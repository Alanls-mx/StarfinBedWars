package com.starfinplugins.bedwars.upgrades;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.TeamTrapDefinition;
import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.economy.generator.ResourceGeneratorService;
import com.starfinplugins.bedwars.economy.InventoryEconomy;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.TeamUpgradePurchasedEvent;
import com.starfinplugins.bedwars.event.TeamTrapTriggeredEvent;
import com.starfinplugins.bedwars.shop.special.SpecialItemService;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

public final class TeamUpgradeService implements ManagedService, Listener {
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final ResourceGeneratorService generators;
    private final Map<UUID, GameMatch> liveMatches = new HashMap<UUID, GameMatch>();
    private final Map<UUID, Set<String>> teamsNearPlayer = new HashMap<UUID, Set<String>>();
    private TeamUpgradeSettings settings;
    private org.bukkit.scheduler.BukkitTask healTask;

    public TeamUpgradeService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                              ResourceGeneratorService generators) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.generators = generators;
    }

    @Override public String getName() { return "team-upgrades-and-traps"; }

    @Override
    public void start() throws Exception {
        settings = TeamUpgradeSettings.load(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        healTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { applyHealPools(); }
        }, 20L, 20L);
    }

    @Override
    public void stop() {
        if (healTask != null) healTask.cancel();
        healTask = null;
        liveMatches.clear();
        teamsNearPlayer.clear();
    }

    @EventHandler
    public void onMatchLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            liveMatches.put(match.getId(), match);
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_RECONNECTED
                && event.getParticipant() != null
                && (event.getParticipant().getState() == ParticipantState.CONNECTED
                    || event.getParticipant().getState() == ParticipantState.RESPAWNING)) {
            Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
            if (player != null && player.getWorld() == matches.getWorld(match.getId())) {
                applyEnchantmentsToPlayer(match, event.getParticipant(), player);
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            liveMatches.remove(match.getId());
            for (GameParticipant participant : match.getParticipantList()) {
                teamsNearPlayer.remove(participant.getPlayerId());
            }
        } else if (event.getType() == MatchLifecycleEvent.Type.PLAYER_LEFT
                && event.getParticipant() != null) {
            teamsNearPlayer.remove(event.getParticipant().getPlayerId());
        }
    }

    public void openShop(Player player, GameMatch match, String teamId) {
        openShop(player, match, teamId, UpgradeMenu.Category.UPGRADES);
    }

    private void openShop(Player player, GameMatch match, String teamId,
                          UpgradeMenu.Category category) {
        if (!isActiveParticipant(player, match, teamId)) {
            player.sendMessage(ChatColor.RED + "Voce nao pode usar esta loja agora.");
            return;
        }
        GameTeam team = match.getTeam(teamId);
        Map<TeamUpgradeType, Integer> levels = new EnumMap<TeamUpgradeType, Integer>(
            TeamUpgradeType.class);
        Map<TeamUpgradeType, Integer> nextCosts = new EnumMap<TeamUpgradeType, Integer>(
            TeamUpgradeType.class);
        for (TeamUpgradeType type : TeamUpgradeType.values()) {
            int level = team.getUpgradeLevel(type);
            levels.put(type, level);
            List<Integer> costs = settings.getCosts(type);
            if (level < costs.size()
                    && (type != TeamUpgradeType.FORGE || generators.canUpgradeTeam(match.getId(), teamId))) {
                nextCosts.put(type, costs.get(level));
            }
        }
        Map<TeamTrapType, Integer> trapCosts = new EnumMap<TeamTrapType, Integer>(TeamTrapType.class);
        for (TeamTrapType type : TeamTrapType.values()) {
            trapCosts.put(type, settings.getTrap(type).getCost());
        }
        UpgradeMenu menu = new UpgradeMenu(levels, nextCosts, trapCosts, team.getTraps().size(),
            settings.getMaximumQueuedTraps(), category);
        player.openInventory(menu.getInventory());
    }

    public void applyItemUpgrade(GameMatch match, GameParticipant participant, ItemStack item) {
        if (match == null || participant == null || item == null) return;
        GameTeam team = match.getTeam(participant.getTeamId());
        if (team == null) return;
        if (team.getUpgradeLevel(TeamUpgradeType.SHARPNESS) > 0 && isSword(item.getType())) {
            item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL,
                team.getUpgradeLevel(TeamUpgradeType.SHARPNESS));
        }
        if (team.getUpgradeLevel(TeamUpgradeType.PROTECTION) > 0 && isArmor(item.getType())) {
            item.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL,
                team.getUpgradeLevel(TeamUpgradeType.PROTECTION));
        }
    }

    @EventHandler
    public void onUpgradeClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof UpgradeMenu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        final UpgradeMenu menu = (UpgradeMenu) top.getHolder();
        Player player = (Player) event.getWhoClicked();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (!isActiveParticipant(player, match, participant == null ? null : participant.getTeamId())) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "A partida nao esta disponivel.");
            return;
        }
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 0 || event.getRawSlot() == 1) {
            final UpgradeMenu.Category category = event.getRawSlot() == 0
                ? UpgradeMenu.Category.UPGRADES : UpgradeMenu.Category.TRAPS;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    if (player.isOnline()) openShop(player, match, participant.getTeamId(), category);
                }
            });
            return;
        }
        UpgradeMenu.Action action = menu.getAction(event.getRawSlot());
        if (action == null) return;
        if (action.getUpgrade() != null) {
            buyUpgrade(player, match, participant.getTeamId(), action.getUpgrade());
        } else {
            buyTrap(player, match, participant.getTeamId(), action.getTrap());
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu) {
                    openShop(player, match, participant.getTeamId(), menu.getCategory());
                }
            }
        });
    }

    @EventHandler
    public void onUpgradeDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof UpgradeMenu) event.setCancelled(true);
    }

    private void buyUpgrade(Player player, GameMatch match, String teamId, TeamUpgradeType type) {
        GameTeam team = match.getTeam(teamId);
        List<Integer> costs = settings.getCosts(type);
        int current = team.getUpgradeLevel(type);
        if (current >= costs.size()) {
            player.sendMessage(ChatColor.RED + "Esta melhoria ja esta no nivel maximo.");
            return;
        }
        if (type == TeamUpgradeType.FORGE && !generators.canUpgradeTeam(match.getId(), teamId)) {
            player.sendMessage(ChatColor.RED + "Os geradores da equipe ja estao no nivel maximo.");
            return;
        }
        ItemStack[] inventoryBefore = InventoryEconomy.snapshot(player);
        if (!InventoryEconomy.charge(player, ResourceType.DIAMOND, costs.get(current))) {
            player.sendMessage(ChatColor.RED + "Diamantes insuficientes.");
            return;
        }
        if (type == TeamUpgradeType.FORGE && generators.upgradeTeam(match.getId(), teamId, 1) == 0) {
            InventoryEconomy.restore(player, inventoryBefore);
            player.sendMessage(ChatColor.RED + "Nao foi possivel melhorar os geradores.");
            return;
        }
        if (!team.upgrade(type, costs.size())) {
            InventoryEconomy.restore(player, inventoryBefore);
            player.sendMessage(ChatColor.RED + "Esta melhoria ja foi comprada.");
            return;
        }
        if (type == TeamUpgradeType.SHARPNESS || type == TeamUpgradeType.PROTECTION) {
            applyTeamEnchantments(match, team);
        }
        Bukkit.getPluginManager().callEvent(new TeamUpgradePurchasedEvent(match, player.getUniqueId(),
            teamId, type, null, team.getUpgradeLevel(type)));
        player.sendMessage(ChatColor.GREEN + "Melhoria de equipe comprada: "
            + type.name().replace('_', ' ') + " " + team.getUpgradeLevel(type) + ".");
        purchaseSound(player);
    }

    private void buyTrap(Player player, GameMatch match, String teamId, TeamTrapType type) {
        GameTeam team = match.getTeam(teamId);
        if (team.getTraps().size() >= settings.getMaximumQueuedTraps()) {
            player.sendMessage(ChatColor.RED + "A fila de armadilhas da equipe esta cheia.");
            return;
        }
        TeamTrapDefinition definition = settings.getTrap(type);
        ItemStack[] inventoryBefore = InventoryEconomy.snapshot(player);
        if (!InventoryEconomy.charge(player, ResourceType.DIAMOND, definition.getCost())) {
            player.sendMessage(ChatColor.RED + "Diamantes insuficientes.");
            return;
        }
        if (!team.addTrap(type, settings.getMaximumQueuedTraps())) {
            InventoryEconomy.restore(player, inventoryBefore);
            player.sendMessage(ChatColor.RED + "Nao foi possivel adicionar a armadilha.");
            return;
        }
        Bukkit.getPluginManager().callEvent(new TeamUpgradePurchasedEvent(match, player.getUniqueId(),
            teamId, null, type, team.getTraps().size()));
        player.sendMessage(ChatColor.GREEN + "Armadilha adicionada a fila da equipe.");
        purchaseSound(player);
    }

    private void purchaseSound(Player player) {
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(plugin.getConfig().getString(
                "purchase-sounds.upgrade", "LEVEL_UP"));
            player.playSound(player.getLocation(), sound,
                (float) plugin.getConfig().getDouble("purchase-sounds.volume", 0.8D),
                (float) plugin.getConfig().getDouble("purchase-sounds.pitch", 1.25D));
        } catch (IllegalArgumentException invalidSound) {
            plugin.getLogger().warning("Som de compra invalido em purchase-sounds.upgrade.");
        }
    }

    private void applyTeamEnchantments(GameMatch match, GameTeam team) {
        for (UUID playerId : team.getMembers()) {
            Player player = Bukkit.getPlayer(playerId);
            GameParticipant participant = match.getParticipant(playerId);
            if (player == null || participant == null
                    || participant.getState() != ParticipantState.CONNECTED
                    || player.getWorld() != matches.getWorld(match.getId())) continue;
            applyEnchantmentsToPlayer(match, participant, player);
        }
    }

    private void applyEnchantmentsToPlayer(GameMatch match, GameParticipant participant, Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;
            ItemStack upgraded = item.clone();
            applyItemUpgrade(match, participant, upgraded);
            inventory.setItem(slot, upgraded);
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            if (armor[slot] == null) continue;
            armor[slot] = armor[slot].clone();
            applyItemUpgrade(match, participant, armor[slot]);
        }
        inventory.setArmorContents(armor);
        player.updateInventory();
    }

    private boolean isActiveParticipant(Player player, GameMatch match, String teamId) {
        if (player == null || match == null || teamId == null || match.getState() != MatchState.IN_GAME
                || matches.getWorld(match.getId()) != player.getWorld()) return false;
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        return participant != null && participant.getState() == ParticipantState.CONNECTED
            && teamId.equals(participant.getTeamId());
    }

    private void applyHealPools() {
        for (GameMatch match : liveMatches.values()) {
            if (match.getState() != MatchState.IN_GAME) continue;
            World world = matches.getWorld(match.getId());
            if (world == null) continue;
            for (GameTeam team : match.getTeams().values()) {
                if (team.getUpgradeLevel(TeamUpgradeType.HEAL_POOL) == 0) continue;
                Position bed = team.getDefinition().getBed();
                if (bed == null) continue;
                Location center = arenas.toLocation(bed, world);
                for (UUID playerId : team.getMembers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    GameParticipant participant = match.getParticipant(playerId);
                    if (player == null || participant == null || participant.getState() != ParticipantState.CONNECTED
                            || player.getWorld() != world || player.getLocation().distanceSquared(center) > 64.0) {
                        continue;
                    }
                    player.addPotionEffect(new PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION, 40, 0), true);
                }
            }
        }
    }

    private SpecialItemService specialItems;
    public void setSpecialItems(SpecialItemService specialItems) { this.specialItems = specialItems; }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED
                || matches.getWorld(match.getId()) != event.getTo().getWorld()) {
            teamsNearPlayer.remove(player.getUniqueId());
            return;
        }
        Set<String> previous = teamsNearPlayer.get(player.getUniqueId());
        if (previous == null) previous = new HashSet<String>();
        Set<String> current = new HashSet<String>();
        for (GameTeam team : match.getTeams().values()) {
            if (team.getDefinition().getId().equals(participant.getTeamId())) continue;
            Position bed = team.getDefinition().getBed();
            if (bed == null) continue;
            Location center = arenas.toLocation(bed, event.getTo().getWorld());
            if (event.getTo().distanceSquared(center) <= 64.0) {
                current.add(team.getDefinition().getId());
            }
        }
        for (String teamId : current) {
            if (previous.contains(teamId)) continue;
            // Magic Milk suppresses the trap without consuming it.
            if (specialItems != null && specialItems.isTrapImmune(player, match)) continue;
            GameTeam team = match.getTeam(teamId);
            if (team.getTraps().isEmpty()) continue;
            TeamTrapType type = team.consumeTrap();
            if (type != null) triggerTrap(match, team, player, type);
        }
        if (current.isEmpty()) teamsNearPlayer.remove(player.getUniqueId());
        else teamsNearPlayer.put(player.getUniqueId(), current);
    }

    private void triggerTrap(GameMatch match, GameTeam team, Player intruder, TeamTrapType type) {
        TeamTrapDefinition definition = settings.getTrap(type);
        if (definition.getEffect() != null) {
            intruder.addPotionEffect(new PotionEffect(definition.getEffect(),
                definition.getDurationTicks(), definition.getAmplifier()), true);
        }
        for (UUID memberId : team.getMembers()) {
            Player teammate = Bukkit.getPlayer(memberId);
            if (teammate != null) teammate.sendMessage(ChatColor.RED + "Armadilha da equipe acionada!");
        }
        Bukkit.getPluginManager().callEvent(new TeamTrapTriggeredEvent(match,
            team.getDefinition().getId(), intruder.getUniqueId(), type));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamsNearPlayer.remove(event.getPlayer().getUniqueId());
    }

    private boolean isSword(Material material) {
        return material == Material.WOOD_SWORD || material == Material.STONE_SWORD
            || material == Material.IRON_SWORD || material == Material.GOLD_SWORD
            || material == Material.DIAMOND_SWORD;
    }

    private boolean isArmor(Material material) {
        switch (material) {
            case LEATHER_HELMET: case LEATHER_CHESTPLATE: case LEATHER_LEGGINGS: case LEATHER_BOOTS:
            case CHAINMAIL_HELMET: case CHAINMAIL_CHESTPLATE: case CHAINMAIL_LEGGINGS: case CHAINMAIL_BOOTS:
            case IRON_HELMET: case IRON_CHESTPLATE: case IRON_LEGGINGS: case IRON_BOOTS:
            case GOLD_HELMET: case GOLD_CHESTPLATE: case GOLD_LEGGINGS: case GOLD_BOOTS:
            case DIAMOND_HELMET: case DIAMOND_CHESTPLATE: case DIAMOND_LEGGINGS: case DIAMOND_BOOTS:
                return true;
            default: return false;
        }
    }
}
