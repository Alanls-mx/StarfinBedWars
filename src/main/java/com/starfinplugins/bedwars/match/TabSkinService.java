package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Viewer-specific TAB skins for offline mode, without changing entity profiles. */
final class TabSkinService {
    private static final long POSITIVE_MILLIS = TimeUnit.HOURS.toMillis(6);
    private static final long NEGATIVE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final Map<String, Cached> cache = new HashMap<String, Cached>();
    private final Set<String> pending = new HashSet<String>();
    private final Map<UUID, Map<UUID, String>> sent = new HashMap<UUID, Map<UUID, String>>();
    private final Map<UUID, Set<UUID>> hidden = new HashMap<UUID, Set<UUID>>();
    private ThreadPoolExecutor executor;
    private boolean available = true;

    TabSkinService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    void start() {
        executor = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(64), runnable -> {
                Thread thread = new Thread(runnable, "StarfinBedWars-tab-skins");
                thread.setDaemon(true);
                return thread;
            });
    }

    void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
        sent.clear();
        hidden.clear();
        pending.clear();
        cache.clear();
    }

    void onQuit(Player player) {
        sent.remove(player.getUniqueId());
        hidden.remove(player.getUniqueId());
        for (Map<UUID, String> entries : sent.values()) entries.remove(player.getUniqueId());
        for (Set<UUID> entries : hidden.values()) entries.remove(player.getUniqueId());
    }

    void restore(Player viewer) {
        Map<UUID, String> entries = sent.remove(viewer.getUniqueId());
        Set<UUID> restore = hidden.remove(viewer.getUniqueId());
        if (restore == null) restore = new HashSet<UUID>();
        if (entries != null) restore.addAll(entries.keySet());
        for (UUID id : restore) {
            Player subject = Bukkit.getPlayer(id);
            if (subject != null && subject.isOnline()) {
                try { TabSkinPacketsV1_8_R3.send(viewer, subject, null); }
                catch (RuntimeException ignored) { available = false; }
            }
        }
    }

    void refreshLobby(Player viewer) { refresh(viewer, null); }

    void refresh(Player viewer, GameMatch match) {
        if (!available || executor == null || !plugin.getConfig().getBoolean("tab.enabled", true)
                || !plugin.getConfig().getBoolean("tab.isolate-matches", true)) {
            restore(viewer);
            return;
        }
        boolean useSkins = plugin.getConfig().getBoolean("tab.skins.enabled", true);
        Set<UUID> visible = new HashSet<UUID>();
        Set<UUID> hiddenForViewer = hidden.get(viewer.getUniqueId());
        if (hiddenForViewer == null) {
            hiddenForViewer = new HashSet<UUID>();
            hidden.put(viewer.getUniqueId(), hiddenForViewer);
        }
        for (Player subject : Bukkit.getOnlinePlayers()) {
            GameMatch subjectMatch = matches.getMatchForPlayer(subject.getUniqueId());
            if (subjectMatch == null) subjectMatch = matches.getSpectatedMatch(subject.getUniqueId());
            if (!shouldShow(match, subjectMatch)) {
                if (hiddenForViewer.add(subject.getUniqueId())) {
                    try { TabSkinPacketsV1_8_R3.hide(viewer, subject); }
                    catch (RuntimeException error) { disable(error); return; }
                }
                Map<UUID, String> known = sent.get(viewer.getUniqueId());
                if (known != null) known.remove(subject.getUniqueId());
                continue;
            }
            visible.add(subject.getUniqueId());
            boolean needsShow = hiddenForViewer.remove(subject.getUniqueId());
            if (needsShow) {
                try { TabSkinPacketsV1_8_R3.send(viewer, subject, null); }
                catch (RuntimeException error) { disable(error); return; }
            }
            if (!useSkins) continue;
            TabSkinProfileLookup.Texture live = TabSkinPacketsV1_8_R3.liveTexture(subject);
            if (live != null) {
                // Vanilla already sends signed textures from the live GameProfile.
                Map<UUID, String> entries = sent.get(viewer.getUniqueId());
                if (entries != null && entries.containsKey(subject.getUniqueId())) {
                    sendIfChanged(viewer, subject, live);
                }
                continue;
            }
            String source = sourceName(subject.getName());
            if (source.isEmpty()) continue;
            Cached cached = cache.get(source.toLowerCase(Locale.ROOT));
            if (cached == null || cached.expires < System.currentTimeMillis()) {
                lookup(source);
                continue;
            }
            if (cached.texture == null && source.equalsIgnoreCase(subject.getName())) {
                String fallback = plugin.getConfig().getString("tab.skins.fallback-player", "").trim();
                if (!fallback.isEmpty() && !fallback.equalsIgnoreCase(source)) {
                    Cached replacement = cache.get(fallback.toLowerCase(Locale.ROOT));
                    if (replacement == null || replacement.expires < System.currentTimeMillis()) {
                        lookup(fallback);
                        continue;
                    }
                    cached = replacement;
                }
            }
            if (cached.texture != null) sendIfChanged(viewer, subject, cached.texture);
        }
        Map<UUID, String> entries = sent.get(viewer.getUniqueId());
        if (entries != null) {
            for (UUID id : new HashSet<UUID>(entries.keySet())) {
                if (!visible.contains(id)) entries.remove(id);
            }
        }
    }

    static boolean shouldShow(GameMatch viewerMatch, GameMatch subjectMatch) {
        return viewerMatch == subjectMatch;
    }

    private void disable(RuntimeException error) {
        available = false;
        plugin.getLogger().warning("TAB individual desativado: " + error.getMessage());
    }

    private String sourceName(String playerName) {
        String alias = "";
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tab.skins.aliases");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.equalsIgnoreCase(playerName)) {
                    alias = section.getString(key, "").trim();
                    break;
                }
            }
        }
        if (!alias.isEmpty()) return alias;
        return playerName;
    }

    private void lookup(final String source) {
        final String key = source.toLowerCase(Locale.ROOT);
        if (!pending.add(key)) return;
        try {
            executor.execute(new Runnable() {
                @Override public void run() {
                    TabSkinProfileLookup.Texture texture = null;
                    try { texture = TabSkinProfileLookup.fetch(source); }
                    catch (Exception error) {
                        // Offline/no premium profile and temporary Mojang failures use
                        // the client's normal default skin until the retry window ends.
                        plugin.getLogger().fine("Skin do TAB indisponivel para " + source
                            + ": " + error.getMessage());
                    }
                    final TabSkinProfileLookup.Texture resolved = texture;
                    if (!plugin.isEnabled()) return;
                    try {
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override public void run() {
                                pending.remove(key);
                                cache.put(key, new Cached(resolved,
                                    System.currentTimeMillis() + (resolved == null
                                        ? NEGATIVE_MILLIS : POSITIVE_MILLIS)));
                            }
                        });
                    } catch (RuntimeException stopped) {
                        // Server or plugin shutdown raced with the HTTP response.
                    }
                }
            });
        } catch (RuntimeException busy) {
            pending.remove(key);
        }
    }

    private void sendIfChanged(Player viewer, Player subject,
                               TabSkinProfileLookup.Texture texture) {
        Map<UUID, String> entries = sent.get(viewer.getUniqueId());
        if (entries == null) {
            entries = new HashMap<UUID, String>();
            sent.put(viewer.getUniqueId(), entries);
        }
        String fingerprint = texture.value + ':' + texture.signature;
        if (fingerprint.equals(entries.get(subject.getUniqueId()))) return;
        try {
            TabSkinPacketsV1_8_R3.send(viewer, subject, texture);
            entries.put(subject.getUniqueId(), fingerprint);
        } catch (RuntimeException error) {
            available = false;
            plugin.getLogger().warning("Icones de skin no TAB desativados: " + error.getMessage());
        }
    }

    private static final class Cached {
        final TabSkinProfileLookup.Texture texture;
        final long expires;
        Cached(TabSkinProfileLookup.Texture texture, long expires) {
            this.texture = texture;
            this.expires = expires;
        }
    }
}
