package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/** Lobby team choice; a Party is moved as one unit and Solo requires permission. */
public final class TeamSelectorService implements ManagedService, Listener {
    public static final int HOTBAR_SLOT = 8;
    private static final String MARKER = ChatColor.BLACK + "SBW:TEAM-SELECTOR";
    private static final String SOLO_PERMISSION = "starfinbedwars.teamselector.solo";
    private final Plugin plugin;
    private final MatchEngineService matches;

    public TeamSelectorService(Plugin plugin, MatchEngineService matches) {
        this.plugin = plugin;
        this.matches = matches;
    }

    @Override public String getName() { return "seletor de times"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) remove(player);
    }

    private boolean allowed(Player player, GameMatch match) {
        if (player == null || match == null || matches.getSpawnOpeningCountdown(match) != null
                || (match.getState() != MatchState.WAITING && match.getState() != MatchState.STARTING))
            return false;
        boolean solo = true;
        for (TeamDefinition team : match.getArena().getTeamList())
            if (team.getCapacity() > 1) { solo = false; break; }
        return !solo || player.hasPermission(SOLO_PERMISSION);
    }

    @EventHandler public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case PLAYER_JOINED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null) install(player, match);
                }
                break;
            case COUNTDOWN_CANCELLED:
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null) install(player, match);
                }
                break;
            case SPAWN_OPENING:
            case STARTED:
            case ENDED:
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null) remove(player);
                }
                break;
            case PLAYER_LEFT:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null) remove(player);
                }
                break;
            default: break;
        }
    }

    private void install(Player player, GameMatch match) {
        remove(player);
        if (!allowed(player, match)) return;
        player.getInventory().setItem(HOTBAR_SLOT, item(Material.COMPASS, (byte) 0,
            ChatColor.GOLD + "Selecionar Time", Arrays.asList(
                ChatColor.GRAY + "Clique para escolher sua equipe.", MARKER)));
        player.updateInventory();
    }

    private void remove(Player player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder() instanceof TeamView)
            player.closeInventory();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
            if (marked(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        player.updateInventory();
    }

    private static boolean marked(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
            && item.getItemMeta().hasLore() && item.getItemMeta().getLore().contains(MARKER);
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (!marked(event.getItem())) return;
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (allowed(player, match)) open(player, match);
    }

    private void open(Player player, GameMatch match) {
        int count = match.getArena().getTeamList().size();
        TeamView view = new TeamView(match.getId());
        Inventory inventory = Bukkit.createInventory(view, Math.max(9, ((count + 8) / 9) * 9),
            "Selecionar Time");
        view.inventory = inventory;
        String current = match.getParticipant(player.getUniqueId()).getTeamId();
        int slot = 0;
        for (TeamDefinition team : match.getArena().getTeamList()) {
            String id = team.getId();
            ChatColor color = ChatColor.getByChar(team.getColor().charAt(0));
            if (color == null) color = ChatColor.WHITE;
            int occupied = match.getTeam(id).getMembers().size();
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Jogadores: " + occupied + "/" + team.getCapacity());
            lore.add(ChatColor.GRAY + "Vagas livres: " + match.availableTeamSlots(id));
            lore.add(id.equals(current) ? ChatColor.GREEN + "Seu time atual"
                : ChatColor.YELLOW + "Clique para entrar");
            inventory.setItem(slot, item(Material.WOOL, team.getColorData(),
                color + team.getDisplayName(), lore));
            view.teams.put(slot, id);
            slot++;
        }
        player.openInventory(inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof TeamView) {
            event.setCancelled(true);
            TeamView view = (TeamView) event.getView().getTopInventory().getHolder();
            if (event.getRawSlot() < 0 || event.getRawSlot() >= view.inventory.getSize()) return;
            String team = view.teams.get(event.getRawSlot());
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (team == null || match == null || !match.getId().equals(view.matchId)
                    || !allowed(player, match)) return;
            GameMatch.TeamChoice result = matches.chooseTeam(player, team);
            switch (result) {
                case MOVED:
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Equipe alterada para "
                        + match.getTeam(team).getDefinition().getDisplayName() + ".");
                    break;
                case ALREADY:
                    player.sendMessage(ChatColor.YELLOW + "Voce ja esta nessa equipe.");
                    break;
                case FULL:
                    player.sendMessage(ChatColor.RED + "Essa equipe nao tem vagas suficientes.");
                    break;
                case WAIT_FOR_PARTY:
                    player.sendMessage(ChatColor.YELLOW
                        + "Aguarde todos os membros da Party chegarem antes de trocar de time.");
                    break;
                case LEADER_ONLY:
                    player.sendMessage(ChatColor.YELLOW
                        + "Somente o lider pode mover a Party para outra equipe.");
                    break;
                default:
                    player.closeInventory();
                    player.sendMessage(ChatColor.RED + "A escolha de equipe nao esta disponivel.");
            }
            return;
        }
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (allowed(player, match) && (marked(event.getCurrentItem())
                || marked(event.getCursor()) || event.getClickedInventory() == player.getInventory()
                    && event.getSlot() == HOTBAR_SLOT
                || event.getHotbarButton() == HOTBAR_SLOT)) event.setCancelled(true);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TeamView)
            event.setCancelled(true);
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (marked(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    private static ItemStack item(Material material, byte data, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, 1, data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class TeamView implements InventoryHolder {
        final UUID matchId;
        final Map<Integer, String> teams = new LinkedHashMap<Integer, String>();
        Inventory inventory;
        TeamView(UUID matchId) { this.matchId = matchId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
