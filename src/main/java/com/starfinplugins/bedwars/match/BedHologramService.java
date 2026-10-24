package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.ConnectionState;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** One logical bed label per team; packets are sent only to eligible viewers. */
public final class BedHologramService implements ManagedService, Listener {
    enum Visibility { OWN_TEAM, ALL_TEAMS, DISABLED }
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final Map<UUID, MatchLabels> active = new HashMap<UUID, MatchLabels>();
    private Visibility visibility;
    private boolean spectators, intactEnabled, destroyedEnabled;
    private int destroyedTicks;
    private double height;
    private BukkitTask refreshTask;
    private long tick;

    public BedHologramService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                              ManagedMobGuard ignoredMobGuard) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
    }

    @Override public String getName() { return "bed-holograms"; }

    @Override public void start() {
        try {
            visibility = Visibility.valueOf(plugin.getConfig().getString("bed-hologram.visibility", "OWN_TEAM")
                .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("bed-hologram.visibility invalido; use OWN_TEAM, ALL_TEAMS ou DISABLED.");
        }
        if (!plugin.getConfig().getBoolean("bed-hologram.enabled", true)) visibility = Visibility.DISABLED;
        spectators = plugin.getConfig().getBoolean("bed-hologram.show-to-spectators", false);
        intactEnabled = plugin.getConfig().getBoolean("bed-hologram.show-when-intact", true);
        destroyedEnabled = plugin.getConfig().getBoolean("bed-hologram.show-when-destroyed", true);
        destroyedTicks = plugin.getConfig().getInt("bed-hologram.destroyed-duration-seconds", 5) * 20;
        height = plugin.getConfig().getDouble("bed-hologram.height", 1.2D);
        if (destroyedTicks < 0 || destroyedTicks > 12000 || !Double.isFinite(height)
                || height < 0.5D || height > 3.0D)
            throw new IllegalArgumentException("bed-hologram: altura ou duracao invalida.");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { refresh(); }
        }, 20L, 20L);
    }

    @Override public void stop() {
        HandlerList.unregisterAll(this);
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        for (UUID id : new ArrayList<UUID>(active.keySet())) remove(id);
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        final GameMatch match = event.getMatch();
        switch (event.getType()) {
            case STARTED: spawn(match); break;
            case BED_DESTROYED: destroyed(match, event.getTeamId()); break;
            case PLAYER_RECONNECTED:
            case PLAYER_JOINED:
            case SPECTATOR_JOINED:
            case PLAYER_LEFT:
            case SPECTATOR_LEFT:
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override public void run() { refreshMatch(match.getId()); }
                });
                break;
            case ENDED:
            case INSTANCE_REMOVED: remove(match.getId()); break;
            default: break;
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID viewer = event.getPlayer().getUniqueId();
        for (MatchLabels labels : active.values())
            for (BedLabel label : labels.beds.values()) label.viewers.remove(viewer);
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if ((event.getFrom().getBlockX() >> 4) == (event.getTo().getBlockX() >> 4)
                && (event.getFrom().getBlockZ() >> 4) == (event.getTo().getBlockZ() >> 4)
                && event.getFrom().getWorld() == event.getTo().getWorld()) return;
        final Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null) match = matches.getSpectatedMatch(event.getPlayer().getUniqueId());
        if (match != null) {
            final UUID id = match.getId();
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() {
                    if (player.isOnline()) refreshViewer(id, player, true);
                }
            }, 2L);
        }
    }

    private void spawn(GameMatch match) {
        if (visibility == Visibility.DISABLED || active.containsKey(match.getId())) return;
        World world = matches.getWorld(match.getId());
        if (world == null || match.getState() != MatchState.IN_GAME) return;
        MatchLabels labels = new MatchLabels(match, world);
        active.put(match.getId(), labels);
        for (GameTeam team : match.getTeams().values()) {
            TeamDefinition definition = team.getDefinition();
            if (definition.getBed() == null) continue;
            Location at = bedCenter(arenas.toLocation(definition.getBed(), world));
            at.add(0.0D, height, 0.0D);
            BedLabel label = new BedLabel(definition.getId(), at,
                new VirtualBedLabelV1_8_R3(at, label(definition, !team.isBedAlive())));
            label.destroyed = !team.isBedAlive();
            labels.beds.put(definition.getId(), label);
        }
        refreshMatch(match.getId());
    }

    private Location bedCenter(Location configured) {
        Block block = configured.getBlock();
        Location center = block.getLocation().add(0.5D, 0.0D, 0.5D);
        if (block.getType() != Material.BED_BLOCK) return center;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            Block other = block.getRelative(side[0], 0, side[1]);
            if (other.getType() == Material.BED_BLOCK) {
                center.add(side[0] * 0.5D, 0.0D, side[1] * 0.5D);
                break;
            }
        }
        return center;
    }

    private void destroyed(GameMatch match, String teamId) {
        MatchLabels labels = active.get(match.getId());
        BedLabel bed = labels == null ? null : labels.beds.get(teamId);
        GameTeam team = match.getTeam(teamId);
        if (bed == null || team == null || bed.destroyed) return;
        bed.destroyed = true;
        bed.expiresAt = tick + destroyedTicks;
        String message = label(team.getDefinition(), true);
        for (UUID id : new HashSet<UUID>(bed.viewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) bed.packet.rename(player, message);
        }
        refreshMatch(match.getId());
    }

    private void refresh() {
        tick += 20;
        for (UUID id : new ArrayList<UUID>(active.keySet())) refreshMatch(id);
    }

    private void refreshMatch(UUID id) {
        MatchLabels labels = active.get(id);
        if (labels == null) return;
        if (labels.match.getState() != MatchState.IN_GAME || Bukkit.getWorld(labels.world.getName()) != labels.world) {
            remove(id);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) refreshViewer(id, player, false);
        for (BedLabel bed : labels.beds.values()) {
            for (UUID viewer : new HashSet<UUID>(bed.viewers)) {
                Player player = Bukkit.getPlayer(viewer);
                if (player == null || !player.isOnline()) bed.viewers.remove(viewer);
            }
        }
    }

    private void refreshViewer(UUID id, Player player, boolean chunkChanged) {
        MatchLabels labels = active.get(id);
        if (labels == null) return;
        for (BedLabel bed : labels.beds.values()) {
            boolean visible = shouldSee(labels, bed, player);
            boolean seen = bed.viewers.contains(player.getUniqueId());
            if (seen && (!visible || chunkChanged)) {
                bed.packet.hide(player);
                bed.viewers.remove(player.getUniqueId());
            }
            if (visible && (!seen || chunkChanged)) {
                bed.packet.show(player);
                bed.viewers.add(player.getUniqueId());
            }
        }
    }

    private boolean shouldSee(MatchLabels labels, BedLabel bed, Player player) {
        if (player.getWorld() != labels.world || player.getLocation().distanceSquared(bed.at) > 48D * 48D)
            return false;
        if (bed.destroyed ? !destroyedEnabled || tick >= bed.expiresAt : !intactEnabled) return false;
        GameParticipant participant = labels.match.getParticipant(player.getUniqueId());
        if (participant != null && matches.getMatchForPlayer(player.getUniqueId()) == labels.match
                && participant.getConnectionState() == ConnectionState.CONNECTED
                && participant.getState() != ParticipantState.ELIMINATED
                && participant.getState() != ParticipantState.ABANDONED)
            return permitted(visibility, participant.getTeamId(), bed.teamId, false, spectators);
        return permitted(visibility, null, bed.teamId,
            matches.getSpectatedMatch(player.getUniqueId()) == labels.match, spectators);
    }

    static boolean permitted(Visibility visibility, String viewerTeam, String bedTeam,
                             boolean spectatingMatch, boolean showSpectators) {
        if (visibility == Visibility.DISABLED) return false;
        if (viewerTeam != null)
            return visibility == Visibility.ALL_TEAMS || viewerTeam.equals(bedTeam);
        return spectatingMatch && showSpectators;
    }

    private String label(TeamDefinition team, boolean destroyed) {
        String template = plugin.getConfig().getString(destroyed
            ? "bed-hologram.destroyed" : "bed-hologram.intact",
            destroyed ? "&c&lSUA CAMA FOI DESTRUIDA!" : "&c&lDEFENDA SUA CAMA!");
        return ChatColor.translateAlternateColorCodes('&', template
            .replace("{color}", "&" + team.getColor()).replace("{team}", team.getDisplayName()));
    }

    private void remove(UUID id) {
        MatchLabels labels = active.remove(id);
        if (labels == null) return;
        for (BedLabel bed : labels.beds.values()) {
            for (UUID viewer : bed.viewers) {
                Player player = Bukkit.getPlayer(viewer);
                if (player != null && player.isOnline()) bed.packet.hide(player);
            }
            bed.viewers.clear();
        }
        labels.beds.clear();
    }

    private static final class MatchLabels {
        final GameMatch match;
        final World world;
        final Map<String, BedLabel> beds = new HashMap<String, BedLabel>();
        MatchLabels(GameMatch match, World world) { this.match = match; this.world = world; }
    }

    private static final class BedLabel {
        final String teamId;
        final Location at;
        final VirtualBedLabelV1_8_R3 packet;
        final Set<UUID> viewers = new HashSet<UUID>();
        boolean destroyed;
        long expiresAt = Long.MAX_VALUE;
        BedLabel(String teamId, Location at, VirtualBedLabelV1_8_R3 packet) {
            this.teamId = teamId; this.at = at; this.packet = packet;
        }
    }
}
