package com.starfinplugins.bedwars.menu;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Requeues only players who explicitly choose to play again. */
public final class NextMatchService implements ManagedService, Listener {
    private static final long QUEUE_LIMIT_MILLIS = 120000L;
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final BedWarsMenuService menus;
    private final Map<UUID, Set<UUID>> resultViewers = new HashMap<UUID, Set<UUID>>();
    private final Map<UUID, String> endedArenas = new HashMap<UUID, String>();
    private final Map<UUID, QueueEntry> queue = new HashMap<UUID, QueueEntry>();
    private BukkitTask queueTask;

    public NextMatchService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                            BedWarsMenuService menus) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.menus = menus;
    }

    @Override public String getName() { return "next-match-menu"; }

    @Override public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        queueTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { retryQueue(); }
        }, 20L, 20L);
    }

    @Override public void stop() {
        if (queueTask != null) queueTask.cancel();
        queueTask = null;
        org.bukkit.event.HandlerList.unregisterAll(this);
        resultViewers.clear();
        endedArenas.clear();
        queue.clear();
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (event.getType() == MatchLifecycleEvent.Type.ENDED) {
            if (!plugin.getConfig().getBoolean("next-match.enabled", true)) return;
            Set<UUID> viewers = new HashSet<UUID>();
            for (GameParticipant participant : match.getParticipantList()) {
                if (participant.getState() == ParticipantState.ABANDONED) continue;
                Player player = Bukkit.getPlayer(participant.getPlayerId());
                if (player != null && player.isOnline()
                        && player.getWorld() == matches.getWorld(match.getId())) {
                    viewers.add(player.getUniqueId());
                }
            }
            resultViewers.put(match.getId(), viewers);
            endedArenas.put(match.getId(), match.getArena().getId());
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_LEFT
                && event.getParticipant() != null) {
            Set<UUID> viewers = resultViewers.get(match.getId());
            if (viewers != null) viewers.remove(event.getParticipant().getPlayerId());
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_JOINED
                && event.getParticipant() != null) {
            queue.remove(event.getParticipant().getPlayerId());
        } else if (event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            final Set<UUID> viewers = resultViewers.remove(match.getId());
            final String arenaId = endedArenas.remove(match.getId());
            if (viewers == null || arenaId == null) return;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    for (UUID id : viewers) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null && player.isOnline() && !matches.hasPlayer(id)) {
                            player.openInventory(new NextMatchMenu(arenaId).getInventory());
                        }
                    }
                }
            });
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof NextMatchMenu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        Player player = (Player) event.getWhoClicked();
        NextMatchMenu menu = (NextMatchMenu) top.getHolder();
        final int slot = event.getRawSlot();
        if (slot != NextMatchMenu.PLAY_AGAIN && slot != NextMatchMenu.CHOOSE_ARENA) return;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu
                        || matches.hasPlayer(player.getUniqueId())) return;
                player.closeInventory();
                if (slot == NextMatchMenu.CHOOSE_ARENA) {
                    queue.remove(player.getUniqueId());
                    menus.openPlayerMenu(player, 0);
                } else {
                    queue.put(player.getUniqueId(), new QueueEntry(menu.getSourceArenaId()));
                    player.sendMessage(ChatColor.GREEN + "Procurando proxima partida do mesmo modo...");
                    retryQueue();
                }
            }
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NextMatchMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        queue.remove(id);
        for (Set<UUID> viewers : resultViewers.values()) viewers.remove(id);
    }

    private void retryQueue() {
        for (Map.Entry<UUID, QueueEntry> entry : new ArrayList<Map.Entry<UUID, QueueEntry>>(queue.entrySet())) {
            UUID id = entry.getKey();
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || matches.hasPlayer(id)) {
                queue.remove(id);
                continue;
            }
            if (System.currentTimeMillis() - entry.getValue().since > QUEUE_LIMIT_MILLIS) {
                queue.remove(id);
                player.sendMessage(ChatColor.RED + "Nenhuma partida disponivel agora. Use /sbw menu.");
                continue;
            }
            QueueEntry request = entry.getValue();
            if (request.requestedArenaId != null) {
                if (matches.isPreparingArena(request.requestedArenaId)) continue;
                // The asynchronous world preparation ended without admitting this
                // player. Do not retry a broken template indefinitely.
                request.failedArenas.add(request.requestedArenaId);
                request.requestedArenaId = null;
            }
            for (ArenaDefinition arena : candidates(request.sourceArenaId)) {
                if (request.failedArenas.contains(arena.getId())) continue;
                try {
                    matches.join(player, arena.getId());
                    request.requestedArenaId = arena.getId();
                    break;
                } catch (RuntimeException unavailable) {
                    // Another join or world copy may have taken this arena.
                    request.failedArenas.add(arena.getId());
                }
            }
        }
    }

    private List<ArenaDefinition> candidates(String sourceId) {
        ArenaDefinition source = arenas.getArena(sourceId);
        if (source == null) return Collections.emptyList();
        List<ArenaDefinition> available = new ArrayList<ArenaDefinition>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (!arena.isEnabled() || !sameMode(source, arena)) continue;
            GameMatch active = matches.getMatchForArena(arena.getId());
            if (active != null && (active.getState() != MatchState.WAITING
                    || active.getConnectedCount() >= arena.getMaximumPlayers())) continue;
            available.add(arena);
        }
        Collections.sort(available, new Comparator<ArenaDefinition>() {
            @Override public int compare(ArenaDefinition left, ArenaDefinition right) {
                boolean leftIsSource = left.getId().equals(sourceId);
                boolean rightIsSource = right.getId().equals(sourceId);
                if (leftIsSource != rightIsSource) return leftIsSource ? 1 : -1;
                return left.getId().compareTo(right.getId());
            }
        });
        return available;
    }

    static boolean sameMode(ArenaDefinition left, ArenaDefinition right) {
        if (left.getTeams().size() != right.getTeams().size()) return false;
        int capacity = -1;
        for (TeamDefinition team : left.getTeamList()) {
            if (capacity < 0) capacity = team.getCapacity();
            else if (team.getCapacity() != capacity) return false;
        }
        for (TeamDefinition team : right.getTeamList()) {
            if (team.getCapacity() != capacity) return false;
        }
        return true;
    }

    private static final class QueueEntry {
        final String sourceArenaId;
        final long since = System.currentTimeMillis();
        String requestedArenaId;
        final Set<String> failedArenas = new HashSet<String>();
        QueueEntry(String sourceArenaId) { this.sourceArenaId = sourceArenaId; }
    }
}
