package com.starfinplugins.bedwars.menu;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.RankedService;
import com.starfinplugins.bedwars.ranked.RankedProfile;
import com.starfinplugins.bedwars.ranked.RankedResult;
import com.starfinplugins.bedwars.ranked.RankedSeason;
import com.starfinplugins.bedwars.ranked.RankedSeasonReward;
import com.starfinplugins.bedwars.ranked.RankedStanding;
import com.starfinplugins.bedwars.ranked.RankedSettings;
import com.starfinplugins.bedwars.service.ManagedService;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Fixed-size 1.8.8 inventory views; async callbacks may only update their own open view. */
public final class RankedMenuService implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    private static final String[] MODES = {"solo", "duo", "trio", "squad", "custom"};
    public RankedMenuService(StarfinBedWarsPlugin plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Menus competitivos"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() { HandlerList.unregisterAll(this); }
    private static final class View implements InventoryHolder {
        final String type;
        String mode, seasonId, divisionId;
        int page;
        long requestVersion;
        Inventory inventory;
        final Map<Integer, RankedSeasonReward> rewardSlots = new HashMap<Integer, RankedSeasonReward>();
        final Map<Integer, RankedStanding> standingSlots = new HashMap<Integer, RankedStanding>();
        View(String type, String mode, String seasonId, int page) {
            this.type = type; this.mode = mode; this.seasonId = seasonId; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private RankedService ranked() { return plugin.getRanked(); }
    private String language(Player player) { return plugin.getStatistics().getLanguage(player.getUniqueId()); }
    private String date(long epochMillis) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")
            .withZone(ranked().getSeasonSettings().displayZone)
            .format(Instant.ofEpochMilli(epochMillis));
    }
    private String t(Player player, String pt, String en, String es) {
        String lang = language(player); return "en_US".equals(lang) ? en : "es_ES".equals(lang) ? es : pt;
    }
    private String seasonState(Player player, RankedSeason.State state) {
        switch (state) {
            case DRAFT: return t(player, "Rascunho", "Draft", "Borrador");
            case SCHEDULED: return t(player, "Agendada", "Scheduled", "Programada");
            case ACTIVE: return t(player, "Ativa", "Active", "Activa");
            case CLOSING: return t(player, "Encerrando", "Closing", "Cerrando");
            case FINALIZING: return t(player, "Finalizando", "Finalizing", "Finalizando");
            case FINISHED: return t(player, "Finalizada", "Finished", "Finalizada");
            default: return t(player, "Cancelada", "Cancelled", "Cancelada");
        }
    }
    private String rewardState(Player player, String state) {
        if ("CLAIMED".equals(state)) return t(player, "Resgatada", "Claimed", "Reclamada");
        if ("CLAIMABLE".equals(state)) return t(player, "Disponivel", "Claimable", "Disponible");
        return t(player, "Bloqueada", "Locked", "Bloqueada");
    }
    private View open(Player player, String type, String mode, String seasonId, int page) {
        View view = new View(type, mode, seasonId, page);
        view.inventory = Bukkit.createInventory(view, 54,
            t(player, "Ranqueadas", "Ranked", "Clasificatorias"));
        player.openInventory(view.inventory);
        return view;
    }
    private boolean visible(Player player, View view) {
        return plugin.isEnabled() && player.isOnline()
            && player.getOpenInventory().getTopInventory() == view.inventory;
    }
    private <T> void load(Player player, View view, CompletableFuture<T> request,
            java.util.function.Consumer<T> render) {
        request.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!visible(player, view)) return;
                if (error != null) {
                    view.inventory.setItem(22, icon(Material.BARRIER, ChatColor.RED
                        + t(player, "Dados indisponiveis", "Data unavailable", "Datos no disponibles")));
                } else render.accept(value);
            });
        });
    }
    private void loading(Player player, View view) {
        view.inventory.clear();
        view.inventory.setItem(22, icon(Material.WATCH, ChatColor.YELLOW
            + t(player, "Carregando...", "Loading...", "Cargando...")));
        back(player, view);
    }
    private void back(Player player, View view) {
        view.inventory.setItem(49, icon(Material.ARROW, ChatColor.YELLOW
            + t(player, "Voltar", "Back", "Volver")));
    }
    public void openMain(Player player) {
        View view = open(player, "main", "solo", null, 1);
        Inventory inv = view.inventory;
        inv.setItem(4, icon(Material.NETHER_STAR, ChatColor.AQUA
            + t(player, "Competitivo", "Competitive", "Competitivo"),
            ChatColor.GRAY + t(player, "Classificacao por temporada", "Season rankings", "Ranking por temporada")));
        inv.setItem(10, icon(Material.SKULL_ITEM, ChatColor.GREEN
            + t(player, "Meu perfil", "My profile", "Mi perfil")));
        inv.setItem(12, icon(Material.WATCH, ChatColor.YELLOW
            + t(player, "Temporada", "Season", "Temporada")));
        inv.setItem(14, icon(Material.DIAMOND, ChatColor.AQUA
            + t(player, "Rankings", "Leaderboards", "Clasificaciones")));
        inv.setItem(16, icon(Material.CHEST, ChatColor.GOLD
            + t(player, "Recompensas", "Rewards", "Recompensas")));
        inv.setItem(28, icon(Material.BOOK, ChatColor.LIGHT_PURPLE
            + t(player, "Historico", "History", "Historial")));
        inv.setItem(30, icon(Material.COMPASS, ChatColor.WHITE
            + t(player, "Modalidades", "Modes", "Modos")));
        inv.setItem(32, icon(Material.GOLD_INGOT, ChatColor.YELLOW
            + t(player, "Divisoes", "Divisions", "Divisiones")));
        inv.setItem(49, icon(Material.BARRIER, ChatColor.RED
            + t(player, "Fechar", "Close", "Cerrar")));
    }
    public void openProfile(Player player, String mode) {
        View view = open(player, "profile", mode, null, 1);
        loading(player, view);
        load(player, view, ranked().getCompetitiveProfile(player.getUniqueId(), mode), profile -> {
            Inventory inv = view.inventory; inv.clear();
            RankedSettings rules = ranked().getSettings();
            int required = ranked().placementRequired();
            String division = profile.isPlaced(required)
                ? "..." : t(player, "Posicionamento", "Placement", "Posicionamiento");
            inv.setItem(4, head(player.getName(), ChatColor.AQUA + player.getName(),
                ChatColor.GRAY + t(player, "Modalidade", "Mode", "Modo") + ": §f" + mode));
            inv.setItem(20, icon(Material.GOLD_INGOT, "§e" + division,
                "§7MMR: §f" + profile.rating, "§7Peak: §f" + profile.peakRating,
                "§7" + t(player, "Proxima divisao", "Next division", "Proxima division")
                    + ": §f..."));
            inv.setItem(22, icon(Material.PAPER, "§b" + t(player, "Partidas", "Matches", "Partidas"),
                "§7" + t(player, "Jogos", "Games", "Juegos") + ": §f" + profile.games,
                "§7" + t(player, "Vitorias", "Wins", "Victorias") + ": §f" + profile.wins,
                "§7" + t(player, "Derrotas", "Losses", "Derrotas") + ": §f" + profile.losses,
                "§7" + t(player, "Taxa de vitorias", "Win rate", "Tasa de victorias")
                    + ": §f" + String.format(java.util.Locale.ROOT, "%.1f%%",
                        profile.games == 0 ? 0D : profile.wins * 100D / profile.games),
                "§7" + t(player, "Posicionamento", "Placement", "Posicionamiento")
                    + ": §f" + Math.min(required, profile.placementGames) + "/" + required));
            inv.setItem(24, icon(Material.COMPASS, "§e" + t(player, "Posicao", "Position", "Posicion"),
                "§7" + t(player, "Consultando...", "Checking...", "Consultando...")));
            inv.setItem(31, icon(Material.BOOK, "§b"
                + t(player, "Partidas recentes", "Recent matches", "Partidas recientes")));
            back(player, view);
            load(player, view, ranked().seasonQuery(repo ->
                repo.progress(profile.context, profile.rating, rules)), progress ->
                inv.setItem(20, icon(Material.GOLD_INGOT,
                    "§e" + (profile.isPlaced(required) ? progress.current : division),
                    "§7MMR: §f" + profile.rating, "§7Peak: §f" + profile.peakRating,
                    "§7" + t(player, "Proxima divisao", "Next division", "Proxima division")
                        + ": §f" + progress.next + " (" + progress.pointsToNext + ")")));
            RankedSeason season = ranked().getCachedSeason();
            if (season != null) load(player, view,
                ranked().getRankedPosition(player.getUniqueId(), season.id, mode), standing ->
                    inv.setItem(24, icon(Material.COMPASS, "§e" + t(player, "Posicao", "Position", "Posicion"),
                        standing == null ? "§7-" : "§f#" + standing.position)));
            else inv.setItem(24, icon(Material.COMPASS, "§e" + t(player, "Posicao", "Position", "Posicion"), "§7-"));
        });
    }
    public void openSeason(Player player) {
        View view = open(player, "season", "solo", null, 1); loading(player, view);
        load(player, view, ranked().getActiveSeason(), season -> {
            view.inventory.clear(); back(player, view);
            if (season == null) { view.inventory.setItem(22, icon(Material.BARRIER, "§e"
                + t(player, "Sem temporada ativa", "No active season", "Sin temporada activa"))); return; }
            view.seasonId = season.id;
            view.inventory.setItem(22, icon(Material.NETHER_STAR, "§b" + season.name,
                "§7ID: §f" + season.id, "§7" + t(player, "Estado", "State", "Estado")
                    + ": §f" + seasonState(player, season.state),
                "§7" + t(player, "Modos", "Modes", "Modos") + ": §f" + season.modes,
                "§7" + t(player, "Termina", "Ends", "Finaliza") + ": §f"
                    + date(season.endsAt)));
        });
    }
    public void openTop(Player player) { openTop(player, "solo", null, 1); }
    private void openTop(Player player, String mode, String seasonId, int page) {
        View view = open(player, "top", mode, seasonId, page);
        renderTop(player, view);
    }
    private void renderTop(Player player, View view) {
        long ticket = ++view.requestVersion;
        loading(player, view);
        CompletableFuture<RankedSeason> target = view.seasonId == null
            ? ranked().getActiveSeason() : ranked().getSeason(view.seasonId);
        load(player, view, target, season -> {
            if (ticket != view.requestVersion) return;
            if (season == null) {
                load(player, view, ranked().getSeasons(20, 1), all -> {
                    if (ticket != view.requestVersion) return;
                    RankedSeason finished = null;
                    for (RankedSeason s : all) if (s.state == RankedSeason.State.FINISHED) {
                        finished = s; break;
                    }
                    if (finished == null) {
                        view.inventory.setItem(22, icon(Material.BARRIER, "§e"
                            + t(player, "Sem ranking sazonal", "No season ranking", "Sin ranking")));
                    } else { view.seasonId = finished.id; renderTopRows(player, view, ticket); }
                });
            } else { view.seasonId = season.id; renderTopRows(player, view, ticket); }
        });
    }
    private void renderTopRows(Player player, View view, long ticket) {
        load(player, view, ranked().getLeaderboard(view.seasonId, view.mode,
            view.divisionId, 36, view.page), standings -> {
            if (ticket != view.requestVersion) return;
            Inventory inv = view.inventory; inv.clear(); view.standingSlots.clear();
            int slot = 0;
            for (RankedStanding row : standings) {
                if (slot >= 36) break;
                inv.setItem(slot, head(row.playerName, "§b#" + row.position + " §f" + row.playerName,
                    "§7MMR: §f" + row.rating, "§7Peak: §f" + row.peakRating,
                    "§7" + row.divisionId + " §8| §f" + row.wins + "W / " + row.losses + "L"));
                view.standingSlots.put(slot++, row);
            }
            if (standings.isEmpty()) inv.setItem(22, icon(Material.PAPER, "§e"
                + t(player, "Nenhum classificado", "No ranked players", "Sin clasificados")));
            inv.setItem(45, icon(Material.ARROW, "§e" + t(player, "Anterior", "Previous", "Anterior")));
            inv.setItem(46, icon(Material.COMPASS, "§b" + t(player, "Modo", "Mode", "Modo")
                + ": §f" + view.mode));
            inv.setItem(47, icon(Material.GOLD_INGOT, "§b"
                + t(player, "Divisao", "Division", "Division") + ": §f"
                + (view.divisionId == null
                    ? t(player, "Todas", "All", "Todas") : view.divisionId)));
            inv.setItem(48, icon(Material.WATCH, "§b" + view.seasonId));
            load(player, view, ranked().getRankedPosition(player.getUniqueId(),
                view.seasonId, view.mode), own ->
                {
                    if (ticket == view.requestVersion)
                        inv.setItem(51, icon(Material.SKULL_ITEM, "§b"
                            + t(player, "Sua posicao", "Your position", "Tu posicion"),
                            own == null ? "§7-" : "§f#" + own.position));
                });
            back(player, view);
            inv.setItem(53, icon(Material.ARROW, "§e" + t(player, "Proxima", "Next", "Siguiente")));
        });
    }
    public void openRewards(Player player) {
        View view = open(player, "rewards", "solo", null, 1); loading(player, view);
        load(player, view, ranked().getSeasons(20, 1), seasons -> {
            for (RankedSeason season : seasons) if (season.state == RankedSeason.State.FINISHED) {
                view.seasonId = season.id; renderRewards(player, view); return;
            }
            view.inventory.setItem(22, icon(Material.BARRIER, "§e"
                + t(player, "Aguardando temporada finalizada", "Awaiting final season", "Esperando temporada final")));
        });
    }
    private void renderRewards(Player player, View view) {
        load(player, view, ranked().getSeasonRewards(player.getUniqueId(), view.seasonId), rewards -> {
            Inventory inv = view.inventory; inv.clear(); view.rewardSlots.clear();
            int slot = 10;
            for (RankedSeasonReward reward : rewards) {
                if (slot >= 44) break;
                String state = reward.state;
                inv.setItem(slot, icon("CLAIMED".equals(state) ? Material.NAME_TAG
                        : "CLAIMABLE".equals(state) ? Material.CHEST : Material.BARRIER,
                    ("CLAIMED".equals(state) ? "§a" : "CLAIMABLE".equals(state) ? "§e" : "§7")
                        + reward.name(language(player)),
                    "§7" + t(player, "Estado", "State", "Estado") + ": §f"
                        + rewardState(player, state),
                    "§7MMR: §f" + reward.minimumRating,
                    "§7" + t(player, "Jogos minimos", "Minimum games", "Juegos minimos")
                        + ": §f" + reward.minimumGames));
                view.rewardSlots.put(slot++, reward);
            }
            if (rewards.isEmpty()) inv.setItem(22, icon(Material.PAPER, "§e"
                + t(player, "Sem recompensas", "No rewards", "Sin recompensas")));
            inv.setItem(48, icon(Material.WATCH, "§b"
                + t(player, "Temporada", "Season", "Temporada") + ": §f" + view.seasonId,
                "§7" + t(player, "Clique para alternar", "Click to switch", "Clic para cambiar")));
            back(player, view);
        });
    }
    public void openHistory(Player player) {
        View view = open(player, "history", "solo", null, 1); renderHistory(player, view);
    }
    public void openMatchHistory(Player player) {
        RankedSeason season = ranked().getCachedSeason();
        View view = open(player, "matches", "solo",
            season == null ? ranked().getSettings().context : season.id, 1);
        renderMatches(player, view);
    }
    private void renderMatches(Player player, View view) {
        loading(player, view);
        load(player, view, ranked().getCompetitiveHistory(player.getUniqueId(),
            view.seasonId, view.page), records -> {
            Inventory inv = view.inventory; inv.clear(); int slot = 0;
            for (RankedResult result : records) {
                if (slot >= 36) break;
                String outcome = result.outcome;
                inv.setItem(slot++, icon("WIN".equals(outcome) ? Material.EMERALD
                        : "LOSS".equals(outcome) ? Material.REDSTONE : Material.PAPER,
                    ("WIN".equals(outcome) ? "§a" : "LOSS".equals(outcome) ? "§c" : "§e")
                        + outcome + " §8| §f" + result.matchId.toString().substring(0, 8),
                    "§7MMR: §f" + result.before + " §7→ §f" + result.after,
                    "§7" + t(player, "Variacao", "Change", "Cambio") + ": §f" + result.change,
                    "§7" + date(result.completedAt)));
            }
            if (records.isEmpty()) inv.setItem(22, icon(Material.PAPER, "§e"
                + t(player, "Sem partidas confirmadas", "No confirmed matches", "Sin partidas confirmadas")));
            inv.setItem(45, icon(Material.ARROW, "§e" + t(player, "Anterior", "Previous", "Anterior")));
            back(player, view);
            inv.setItem(53, icon(Material.ARROW, "§e" + t(player, "Proxima", "Next", "Siguiente")));
        });
    }
    private void renderHistory(Player player, View view) {
        loading(player, view);
        load(player, view, ranked().getSeasonHistory(player.getUniqueId(), view.page), records -> {
            Inventory inv = view.inventory; inv.clear(); view.standingSlots.clear();
            int slot = 0;
            for (RankedStanding row : records) {
                if (slot >= 36) break;
                inv.setItem(slot, icon(Material.BOOK, "§b" + row.seasonId + " §f" + row.mode,
                    "§7#" + row.position + " §8| §f" + row.divisionId,
                    "§7MMR: §f" + row.rating + " §7Peak: §f" + row.peakRating,
                    "§7" + row.wins + "W / " + row.losses + "L"));
                view.standingSlots.put(slot++, row);
            }
            if (records.isEmpty()) inv.setItem(22, icon(Material.PAPER, "§e"
                + t(player, "Sem historico finalizado", "No final history", "Sin historial final")));
            inv.setItem(45, icon(Material.ARROW, "§e" + t(player, "Anterior", "Previous", "Anterior")));
            back(player, view);
            inv.setItem(53, icon(Material.ARROW, "§e" + t(player, "Proxima", "Next", "Siguiente")));
        });
    }
    public void openModes(Player player) {
        View view = open(player, "modes", "solo", null, 1);
        for (int i = 0; i < MODES.length; i++) {
            final String mode = MODES[i]; final int slot = 10 + i * 2;
            if (!ranked().getSettings().modes.contains(mode)) continue;
            view.inventory.setItem(slot, icon(Material.WATCH, "§b" + mode));
            load(player, view, ranked().getCompetitiveProfile(player.getUniqueId(), mode), profile ->
                view.inventory.setItem(slot, icon(Material.COMPASS, "§b" + mode,
                    "§7MMR: §f" + profile.rating,
                    "§7" + t(player, "Jogos", "Games", "Juegos") + ": §f" + profile.games,
                    "§7" + t(player, "Clique para ver seu perfil", "Click for your profile",
                        "Clic para ver tu perfil"))));
        }
        back(player, view);
    }
    public void openDivisions(Player player) {
        View view = open(player, "divisions", "solo", null, 1);
        int slot = 10;
        for (RankedSettings.Division division : ranked().getSettings().divisions) {
            if (slot >= 44) break;
            Material icon = Material.matchMaterial(division.icon);
            view.inventory.setItem(slot++, icon(icon == null ? Material.PAPER : icon,
                ChatColor.translateAlternateColorCodes('&', division.color + division.name),
                "§7MMR: §f" + division.minimum));
        }
        back(player, view);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof View)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClickedInventory() != top) return;
        Player player = (Player) event.getWhoClicked();
        View view = (View) top.getHolder(); int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        if (slot == 49 && !"main".equals(view.type)) {
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player)); return;
        }
        if ("main".equals(view.type)) {
            if (slot == 49) { player.closeInventory(); return; }
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (slot) {
                    case 10: openProfile(player, "solo"); break;
                    case 12: openSeason(player); break;
                    case 14: openTop(player); break;
                    case 16: openRewards(player); break;
                    case 28: openHistory(player); break;
                    case 30: openModes(player); break;
                    case 32: openDivisions(player); break;
                    default: break;
                }
            });
        } else if ("modes".equals(view.type)) {
            for (int i = 0; i < MODES.length; i++) if (slot == 10 + i * 2) {
                final String mode = MODES[i];
                if (!ranked().getSettings().modes.contains(mode)) return;
                Bukkit.getScheduler().runTask(plugin, () -> openProfile(player, mode)); return;
            }
        } else if ("profile".equals(view.type) && slot == 31) {
            Bukkit.getScheduler().runTask(plugin, () -> openMatchHistory(player));
        } else if ("top".equals(view.type)) {
            if (slot == 45 && view.page > 1) { view.page--; renderTop(player, view); }
            else if (slot == 53 && !view.standingSlots.isEmpty()) { view.page++; renderTop(player, view); }
            else if (slot == 46) {
                int next = (Arrays.asList(MODES).indexOf(view.mode) + 1) % MODES.length;
                while (!ranked().getSettings().modes.contains(MODES[next]))
                    next = (next + 1) % MODES.length;
                view.mode = MODES[next]; view.page = 1; renderTop(player, view);
            } else if (slot == 47) {
                List<RankedSettings.Division> divisions = ranked().getSettings().divisions;
                int index = -1;
                for (int i = 0; i < divisions.size(); i++)
                    if (divisions.get(i).id.equals(view.divisionId)) index = i;
                view.divisionId = index + 1 >= divisions.size()
                    ? null : divisions.get(index + 1).id;
                view.page = 1; renderTop(player, view);
            } else if (slot == 48) load(player, view, ranked().getSeasons(20, 1), seasons -> {
                List<RankedSeason> publicSeasons = new ArrayList<RankedSeason>();
                for (RankedSeason candidate : seasons)
                    if (candidate.state == RankedSeason.State.ACTIVE
                            || candidate.state == RankedSeason.State.CLOSING
                            || candidate.state == RankedSeason.State.FINALIZING
                            || candidate.state == RankedSeason.State.FINISHED)
                        publicSeasons.add(candidate);
                if (publicSeasons.isEmpty()) return;
                int index = -1;
                for (int i = 0; i < publicSeasons.size(); i++)
                    if (publicSeasons.get(i).id.equals(view.seasonId)) index = i;
                view.seasonId = publicSeasons.get((index + 1) % publicSeasons.size()).id;
                view.page = 1; renderTop(player, view);
            });
        } else if ("history".equals(view.type)) {
            if (slot == 45 && view.page > 1) { view.page--; renderHistory(player, view); }
            else if (slot == 53 && !view.standingSlots.isEmpty()) { view.page++; renderHistory(player, view); }
            else if (view.standingSlots.containsKey(slot)) {
                RankedStanding row = view.standingSlots.get(slot);
                Bukkit.getScheduler().runTask(plugin, () -> openTop(player, row.mode, row.seasonId, 1));
            }
        } else if ("matches".equals(view.type)) {
            if (slot == 45 && view.page > 1) { view.page--; renderMatches(player, view); }
            else if (slot == 53) { view.page++; renderMatches(player, view); }
        } else if ("rewards".equals(view.type) && slot == 48) {
            load(player, view, ranked().getSeasons(100, 1), seasons -> {
                List<RankedSeason> finished = new ArrayList<RankedSeason>();
                for (RankedSeason candidate : seasons)
                    if (candidate.state == RankedSeason.State.FINISHED) finished.add(candidate);
                if (finished.isEmpty()) return;
                int index = -1;
                for (int i = 0; i < finished.size(); i++)
                    if (finished.get(i).id.equals(view.seasonId)) index = i;
                view.seasonId = finished.get((index + 1) % finished.size()).id;
                renderRewards(player, view);
            });
        } else if ("rewards".equals(view.type) && view.rewardSlots.containsKey(slot)) {
            RankedSeasonReward reward = view.rewardSlots.get(slot);
            if (!"CLAIMABLE".equals(reward.state) || reward.mode == null) return;
            load(player, view, ranked().claimSeasonReward(player.getUniqueId(), reward.seasonId,
                reward.mode, reward.id), claimed -> {
                if (claimed) {
                    plugin.getStatistics().requestSeasonTitleRefresh(player.getUniqueId());
                    player.sendMessage("§a" + t(player, "Recompensa resgatada", "Reward claimed", "Recompensa reclamada"));
                }
                renderRewards(player, view);
            });
        }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof View)) return;
        for (int slot : event.getRawSlots()) if (slot < top.getSize()) {
            event.setCancelled(true); return;
        }
    }
    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name);
        meta.setLore(new ArrayList<String>(Arrays.asList(lore))); item.setItemMeta(meta);
        return item;
    }
    private static ItemStack head(String owner, String name, String... lore) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwner(owner); meta.setDisplayName(name);
        meta.setLore(new ArrayList<String>(Arrays.asList(lore))); item.setItemMeta(meta);
        return item;
    }
}
