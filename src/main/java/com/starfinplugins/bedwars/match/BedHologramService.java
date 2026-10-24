package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** A single compact label over each bed, updated when that bed is destroyed. */
public final class BedHologramService implements ManagedService, Listener {
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final ManagedMobGuard mobGuard;
    private final Map<UUID, Map<String, ArmorStand>> labels = new HashMap<UUID, Map<String, ArmorStand>>();

    public BedHologramService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                              ManagedMobGuard mobGuard) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.mobGuard = mobGuard;
    }

    @Override public String getName() { return "bed-holograms"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() {
        HandlerList.unregisterAll(this);
        for (UUID id : new java.util.ArrayList<UUID>(labels.keySet())) remove(id);
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            spawn(match);
        } else if (event.getType() == MatchLifecycleEvent.Type.BED_DESTROYED) {
            Map<String, ArmorStand> matchLabels = labels.get(match.getId());
            ArmorStand stand = matchLabels == null ? null : matchLabels.get(event.getTeamId());
            GameTeam team = match.getTeam(event.getTeamId());
            if (stand != null && stand.isValid() && team != null) {
                stand.setCustomName(label(team.getDefinition(), true));
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            remove(match.getId());
        }
    }

    private void spawn(GameMatch match) {
        if (!plugin.getConfig().getBoolean("bed-hologram.enabled", true)) return;
        World world = matches.getWorld(match.getId());
        if (world == null) return;
        Map<String, ArmorStand> created = new HashMap<String, ArmorStand>();
        labels.put(match.getId(), created);
        double height = Math.max(0.6D, Math.min(2.5D,
            plugin.getConfig().getDouble("bed-hologram.height", 1.2D)));
        for (GameTeam team : match.getTeams().values()) {
            TeamDefinition definition = team.getDefinition();
            if (definition.getBed() == null) continue;
            Location at = arenas.toLocation(definition.getBed(), world).add(0, height, 0);
            ArmorStand stand = (ArmorStand) mobGuard.spawnAuthorized(at, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(label(definition, !team.isBedAlive()));
            created.put(definition.getId(), stand);
        }
    }

    private String label(TeamDefinition team, boolean destroyed) {
        String template = plugin.getConfig().getString(destroyed
            ? "bed-hologram.destroyed" : "bed-hologram.intact",
            destroyed ? "{color}&l{team} &cCama destruida" : "{color}&l{team} &fCama");
        return ChatColor.translateAlternateColorCodes('&', template
            .replace("{color}", "&" + team.getColor())
            .replace("{team}", team.getDisplayName()));
    }

    private void remove(UUID id) {
        Map<String, ArmorStand> matchLabels = labels.remove(id);
        if (matchLabels == null) return;
        for (ArmorStand stand : matchLabels.values()) {
            mobGuard.forget(stand);
            if (stand.isValid()) stand.remove();
        }
    }
}
