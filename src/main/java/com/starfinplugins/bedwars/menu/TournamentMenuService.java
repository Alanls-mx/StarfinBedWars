package com.starfinplugins.bedwars.menu;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.tournament.Tournament;
import com.starfinplugins.bedwars.tournament.TournamentMatch;
import com.starfinplugins.bedwars.tournament.TournamentReward;
import com.starfinplugins.bedwars.tournament.TournamentTeam;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Paginated, asynchronous tournament inventories for Minecraft 1.8.8. */
public final class TournamentMenuService implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    public TournamentMenuService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Menus de torneio"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() { HandlerList.unregisterAll(this); }

    public void openMain(Player player, int page) {
        if (!available(player)) return;
        int current = Math.max(1, page);
        plugin.getTournaments().query(repo -> repo.list(21, current)).whenComplete((rows, error) ->
            main(() -> {
                if (!online(player)) return;
                if (error != null) { unavailable(player); return; }
                Menu menu = new Menu("Torneios", 27, "main", null, current, 0);
                menu.inventory.setItem(0, item(Material.NETHER_STAR, "§bTorneios",
                    "§7Inscricoes e confrontos"));
                menu.inventory.setItem(1, item(Material.BOOK, "§eMeus torneios",
                    "§7Seu historico de inscricoes"));
                menu.inventory.setItem(2, item(Material.DIAMOND, "§bPremios",
                    "§7Títulos conquistados"));
                int slot = 3;
                for (Tournament row : rows) {
                    if (slot >= 24) break;
                    menu.entries.add(row.id);
                    menu.inventory.setItem(slot++, item(row.state == Tournament.State.REGISTRATION_OPEN
                        ? Material.EMERALD : Material.PAPER, "§b" + row.name,
                        "§7" + row.mode + " §8• §7MD" + row.bestOf,
                        "§7Estado: §f" + row.state, "§eClique para detalhes"));
                }
                if (current > 1) menu.inventory.setItem(24,
                    item(Material.ARROW, "§ePagina anterior"));
                menu.inventory.setItem(26, item(Material.ARROW, "§eProxima pagina"));
                player.openInventory(menu.inventory);
            }));
    }
    public void openDetails(Player player, String id) {
        if (!available(player)) return;
        plugin.getTournaments().query(repo -> repo.find(id)).whenComplete((tournament, error) ->
            main(() -> {
                if (!online(player)) return;
                if (error != null || tournament == null) { unavailable(player); return; }
                Menu menu = new Menu("Torneio: " + tournament.name, 27, "details", id, 1, 0);
                menu.inventory.setItem(4, item(Material.NETHER_STAR,
                    "§b" + tournament.name, "§7" + tournament.description,
                    "§7Estado: §f" + tournament.state,
                    "§7Modo: §f" + tournament.mode + " §8| §7MD" + tournament.bestOf,
                    "§7Equipes: §f" + tournament.minimumTeams + "–" + tournament.maximumTeams,
                    "§7Inicio: §f" + Instant.ofEpochMilli(tournament.startsAt)));
                menu.inventory.setItem(10, item(Material.COMPASS, "§bChave eliminatoria",
                    "§7Rodadas e confrontos"));
                menu.inventory.setItem(12, item(Material.BOOK, "§eMinha equipe",
                    "§7Elenco inscrito"));
                menu.inventory.setItem(14, item(Material.CHEST, "§bPremios",
                    "§7Consultar e resgatar"));
                if (tournament.state == Tournament.State.REGISTRATION_OPEN)
                    menu.inventory.setItem(16, item(Material.EMERALD, "§aInscrever equipe",
                        "§7O lider da Party confirma o elenco",
                        "§7Cada membro confirma sua participacao"));
                menu.inventory.setItem(22, item(Material.ARROW, "§eVoltar"));
                player.openInventory(menu.inventory);
            }));
    }
    public void openTeam(Player player, String id) {
        if (!available(player)) return;
        UUID playerId = player.getUniqueId();
        plugin.getTournaments().query(repo -> repo.teamFor(id, playerId)).whenComplete((team, error) ->
            main(() -> {
                if (!online(player)) return;
                if (error != null) { unavailable(player); return; }
                Menu menu = new Menu("Minha equipe", 27, "team", id, 1, 0);
                if (team == null) menu.inventory.setItem(13, item(Material.BARRIER,
                    "§cVoce nao esta inscrito", "§7Volte aos detalhes para inscrever-se"));
                else {
                    menu.inventory.setItem(4, item(Material.NETHER_STAR, "§b" + team.name,
                        "§7Estado: §f" + team.state, "§7Capitao: §f" + team.captain));
                    for (int i = 0; i < team.members.size() && i < 18; i++)
                        menu.inventory.setItem(9 + i, item(Material.PAPER,
                            "§f" + team.members.get(i), "§7Membro " + (i + 1)));
                }
                menu.inventory.setItem(22, item(Material.ARROW, "§eVoltar"));
                player.openInventory(menu.inventory);
            }));
    }
    public void openBracket(Player player, String id, int round, int page) {
        if (!available(player)) return;
        int currentRound = Math.max(1, round), currentPage = Math.max(1, page);
        plugin.getTournaments().query(repo -> repo.matches(id, currentRound, currentPage))
            .whenComplete((rows, error) -> main(() -> {
                if (!online(player)) return;
                if (error != null) { unavailable(player); return; }
                Menu menu = new Menu("Chave: rodada " + currentRound, 54, "bracket",
                    id, currentPage, currentRound);
                for (int i = 0; i < rows.size() && i < 36; i++) {
                    TournamentMatch match = rows.get(i);
                    menu.inventory.setItem(i, item(Material.PAPER,
                        "§bConfronto " + (match.slot + 1),
                        "§7A: §f" + label(match.teamA) + " §b" + match.winsA,
                        "§7B: §f" + label(match.teamB) + " §b" + match.winsB,
                        "§7Serie: §fMD" + match.bestOf + " §8| §7" + match.state,
                        "§7Vencedor: §f" + label(match.winner)));
                }
                if (rows.isEmpty()) menu.inventory.setItem(22,
                    item(Material.BARRIER, "§7Sem confrontos nesta pagina"));
                menu.inventory.setItem(45, item(Material.ARROW, "§eRodada anterior"));
                menu.inventory.setItem(46, item(Material.ARROW, "§ePagina anterior"));
                menu.inventory.setItem(49, item(Material.CHEST, "§eVoltar"));
                menu.inventory.setItem(52, item(Material.ARROW, "§eProxima pagina"));
                menu.inventory.setItem(53, item(Material.ARROW, "§eProxima rodada"));
                player.openInventory(menu.inventory);
            }));
    }
    public void openRewards(Player player) {
        if (!available(player)) return;
        plugin.getTournaments().query(repo -> repo.rewards(player.getUniqueId()))
            .whenComplete((rows, error) -> main(() -> {
                if (!online(player)) return;
                if (error != null) { unavailable(player); return; }
                Menu menu = new Menu("Premios de torneio", 27, "rewards", null, 1, 0);
                for (int i = 0; i < rows.size() && i < 18; i++) {
                    TournamentReward reward = rows.get(i);
                    menu.inventory.setItem(i, item(Material.NETHER_STAR,
                        "§b" + reward.titleName, "§7Torneio: §f" + reward.tournamentId,
                        "§7Estado: §f" + reward.state));
                }
                menu.inventory.setItem(22, item(Material.ARROW, "§eVoltar"));
                menu.inventory.setItem(24, item(Material.EMERALD, "§aResgatar premios",
                    "§7Resgate idempotente"));
                player.openInventory(menu.inventory);
            }));
    }
    public void openHistory(Player player) {
        if (!available(player)) return;
        plugin.getTournaments().query(repo -> repo.history(player.getUniqueId(), 21))
            .whenComplete((rows, error) -> main(() -> {
                if (!online(player)) return;
                if (error != null) { unavailable(player); return; }
                Menu menu = new Menu("Meus torneios", 27, "history", null, 1, 0);
                for (int i = 0; i < rows.size() && i < 21; i++) {
                    Tournament row = rows.get(i);
                    menu.entries.add(row.id);
                    menu.inventory.setItem(i, item(Material.BOOK, "§b" + row.name,
                        "§7" + row.state, "§eClique para detalhes"));
                }
                menu.inventory.setItem(26, item(Material.ARROW, "§eVoltar"));
                player.openInventory(menu.inventory);
            }));
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || !(event.getView().getTopInventory().getHolder() instanceof Menu)) return;
        Menu menu = (Menu) event.getView().getTopInventory().getHolder();
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()) return;
        Player player = (Player) event.getWhoClicked(); int slot = event.getRawSlot();
        switch (menu.kind) {
            case "main":
                if (slot == 1) openHistory(player);
                else if (slot == 2) openRewards(player);
                else if (slot == 24 && menu.page > 1) openMain(player, menu.page - 1);
                else if (slot == 26) openMain(player, menu.page + 1);
                else if (slot >= 3 && slot - 3 < menu.entries.size())
                    openDetails(player, menu.entries.get(slot - 3));
                break;
            case "history":
                if (slot == 26) openMain(player, 1);
                else if (slot < menu.entries.size()) openDetails(player, menu.entries.get(slot));
                break;
            case "details":
                if (slot == 10) openBracket(player, menu.id, 1, 1);
                else if (slot == 12) openTeam(player, menu.id);
                else if (slot == 14) openRewards(player);
                else if (slot == 16 && menu.inventory.getItem(16) != null)
                    plugin.getTournaments().register(player, menu.id, player.getName());
                else if (slot == 22) openMain(player, 1);
                break;
            case "team": if (slot == 22) openDetails(player, menu.id); break;
            case "bracket":
                if (slot == 45 && menu.round > 1) openBracket(player, menu.id, menu.round - 1, 1);
                else if (slot == 46 && menu.page > 1)
                    openBracket(player, menu.id, menu.round, menu.page - 1);
                else if (slot == 49) openDetails(player, menu.id);
                else if (slot == 52) openBracket(player, menu.id, menu.round, menu.page + 1);
                else if (slot == 53) openBracket(player, menu.id, menu.round + 1, 1);
                break;
            case "rewards":
                if (slot == 22) openMain(player, 1);
                else if (slot == 24) plugin.getTournaments()
                    .query(repo -> repo.claimRewards(player.getUniqueId())).whenComplete((count, error) ->
                        main(() -> {
                            if (!online(player)) return;
                            player.sendMessage(error == null ? "§aPremios resgatados: " + count
                                : "§cFalha ao resgatar premios.");
                            if (error == null) {
                                plugin.getStatistics().requestSeasonTitleRefresh(player.getUniqueId());
                                openRewards(player);
                            }
                        }));
                break;
            default: break;
        }
    }
    private boolean available(Player player) {
        if (plugin.getTournaments() != null && plugin.getTournaments().isOperational()) return true;
        unavailable(player); return false;
    }
    private void unavailable(Player player) { player.sendMessage("§cTorneios indisponiveis."); }
    private boolean online(Player player) { return plugin.isEnabled() && player.isOnline(); }
    private void main(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }
    private static String label(UUID id) { return id == null ? "A definir" : id.toString().substring(0, 8); }
    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(name); meta.setLore(Arrays.asList(lore));
        result.setItemMeta(meta); return result;
    }
    private static final class Menu implements InventoryHolder {
        final Inventory inventory;
        final String kind, id;
        final int page, round;
        final List<String> entries = new ArrayList<String>();
        Menu(String title, int size, String kind, String id, int page, int round) {
            this.kind = kind; this.id = id; this.page = page; this.round = round;
            this.inventory = Bukkit.createInventory(this, size,
                title.length() > 32 ? title.substring(0, 32) : title);
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
