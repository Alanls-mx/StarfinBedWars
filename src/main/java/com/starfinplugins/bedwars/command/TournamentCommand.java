package com.starfinplugins.bedwars.command;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.TournamentService;
import com.starfinplugins.bedwars.tournament.Tournament;
import com.starfinplugins.bedwars.tournament.TournamentGame;
import com.starfinplugins.bedwars.tournament.TournamentMatch;
import com.starfinplugins.bedwars.tournament.TournamentReward;
import com.starfinplugins.bedwars.tournament.TournamentTeam;
import com.starfinplugins.bedwars.tournament.TournamentSettings;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

/** Player and administrator command facade; all SQL is run by TournamentService. */
final class TournamentCommand {
    private static final String P = ChatColor.AQUA + "[Torneio] " + ChatColor.RESET;
    private TournamentCommand() { }
    static void execute(StarfinBedWarsPlugin plugin, CommandSender sender, String[] args) {
        TournamentService service = plugin.getTournaments();
        if (service == null || !service.isOperational()) {
            sender.sendMessage(P + ChatColor.RED + "Torneios indisponiveis."); return;
        }
        if (args.length < 2) {
            if (sender instanceof Player && plugin.getTournamentMenus() != null)
                plugin.getTournamentMenus().openMain((Player) sender, 1);
            else help(sender);
            return;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        String permission = ("join".equals(action) || "confirm".equals(action)
            || "checkin".equals(action) || "leave".equals(action)) ? "register"
            : "spectate".equals(action) ? "spectate"
            : "rewards".equals(action) ? "rewards" : "consult";
        if (!"admin".equals(action) && !sender.hasPermission("starfinbedwars.tournament." + permission)) {
            sender.sendMessage(P + "§cSem permissao."); return;
        }
        try {
            switch (action) {
                case "list":
                    int page = args.length > 2 ? positive(args[2]) : 1;
                    show(plugin, sender, service.query(repo -> repo.list(8, page)), rows -> {
                        sender.sendMessage(P + "Torneios — pagina " + page);
                        for (Tournament t : rows) sender.sendMessage("§b" + t.id + " §f" + t.name
                            + " §7[" + t.state + "] " + t.mode);
                        if (rows.isEmpty()) sender.sendMessage("§7Nenhum torneio nesta pagina.");
                    }); return;
                case "info":
                    if (args.length != 3) { help(sender); return; }
                    show(plugin, sender, service.query(repo -> repo.find(args[2])), t -> {
                        if (t == null) { sender.sendMessage(P + "§cTorneio inexistente."); return; }
                        sender.sendMessage(P + "§b" + t.name + " §7(" + t.id + ")");
                        sender.sendMessage("§f" + t.description);
                        sender.sendMessage("§7Estado: §f" + t.state + " §7Modo: §f" + t.mode
                            + " §7Equipes: §f" + t.minimumTeams + "-" + t.maximumTeams
                            + " §7Serie: §fMD" + t.bestOf);
                        sender.sendMessage("§7Inicio: §f" + Instant.ofEpochMilli(t.startsAt));
                    }); return;
                case "join":
                    if (!(sender instanceof Player) || args.length < 3 || args.length > 4) {
                        sender.sendMessage(P + "§eUse /sbw tournament join <id> [nomeEquipe]."); return;
                    }
                    service.register((Player) sender, args[2], args.length == 4
                        ? args[3] : sender.getName()); return;
                case "confirm":
                    if (!(sender instanceof Player) || args.length != 3) { help(sender); return; }
                    UUID confirming = ((Player) sender).getUniqueId();
                    show(plugin, sender, service.query(repo -> repo.confirm(args[2], confirming)),
                        team -> sender.sendMessage(P + "§aConfirmado: §f" + team.name)); return;
                case "checkin":
                    if (!(sender instanceof Player) || args.length != 3) { help(sender); return; }
                    UUID checking = ((Player) sender).getUniqueId();
                    show(plugin, sender, service.query(repo -> repo.checkIn(args[2], checking)),
                        match -> sender.sendMessage(P + "§aPresenca confirmada para " + match));
                    return;
                case "leave":
                    if (!(sender instanceof Player) || args.length != 3) { help(sender); return; }
                    UUID leaving = ((Player) sender).getUniqueId();
                    show(plugin, sender, service.query(repo -> {
                        repo.withdraw(args[2], leaving); return true;
                    }), ignored -> sender.sendMessage(P + "§eInscricao cancelada.")); return;
                case "team":
                    if (!(sender instanceof Player) || args.length != 3) { help(sender); return; }
                    UUID player = ((Player) sender).getUniqueId();
                    show(plugin, sender, service.query(repo -> repo.teamFor(args[2], player)), t -> {
                        if (t == null) { sender.sendMessage(P + "§eVoce nao esta inscrito."); return; }
                        sender.sendMessage(P + "§b" + t.name + " §7" + t.state
                            + " §7| capitao: " + t.captain);
                        for (UUID member : t.members) sender.sendMessage("§7- " + member);
                    }); return;
                case "history":
                    if (!(sender instanceof Player)) { help(sender); return; }
                    UUID historical = ((Player) sender).getUniqueId();
                    show(plugin, sender, service.query(repo -> repo.history(historical, 20)), rows -> {
                        sender.sendMessage(P + "Historico de torneios:");
                        for (Tournament t : rows) sender.sendMessage("§b" + t.id
                            + " §f" + t.name + " §7[" + t.state + "]");
                        if (rows.isEmpty()) sender.sendMessage("§7Nenhum torneio encontrado.");
                    }); return;
                case "matches": case "bracket":
                    if (args.length < 3 || args.length > 5) { help(sender); return; }
                    int round = args.length > 3 ? Integer.parseInt(args[3]) : 0;
                    int matchPage = args.length > 4 ? positive(args[4]) : 1;
                    show(plugin, sender, service.query(repo -> repo.matches(args[2], round, matchPage)),
                        rows -> {
                            sender.sendMessage(P + "Chave " + args[2] + " — pagina " + matchPage);
                            for (TournamentMatch m : rows) sender.sendMessage("§7R" + m.round
                                + "/" + (m.slot + 1) + " §f" + shortId(m.teamA) + " §b"
                                + m.winsA + "§7:§b" + m.winsB + " §f" + shortId(m.teamB)
                                + " §7[" + m.state + "] §8" + m.id);
                            if (rows.isEmpty()) sender.sendMessage("§7Chave ainda nao gerada.");
                        }); return;
                case "rewards":
                    if (!(sender instanceof Player)) { help(sender); return; }
                    UUID rewarded = ((Player) sender).getUniqueId();
                    if (args.length > 2 && "claim".equalsIgnoreCase(args[2])) {
                        show(plugin, sender, service.query(repo -> repo.claimRewards(rewarded)), count -> {
                            sender.sendMessage(P + "§aPremios resgatados: " + count);
                            if (plugin.getStatistics() != null)
                                plugin.getStatistics().requestSeasonTitleRefresh(rewarded);
                        }); return;
                    }
                    show(plugin, sender, service.query(repo -> repo.rewards(rewarded)), rows -> {
                        sender.sendMessage(P + "Premios:");
                        for (TournamentReward reward : rows) sender.sendMessage("§b"
                            + reward.tournamentId + " §f" + reward.titleName
                            + " §7[" + reward.state + "]");
                        if (rows.isEmpty()) sender.sendMessage("§7Nenhum premio.");
                    }); return;
                case "spectate":
                    if (!(sender instanceof Player) || (args.length != 3 && args.length != 4)) {
                        help(sender); return;
                    }
                    UUID gameId = args.length == 4 ? UUID.fromString(args[3]) : null;
                    Player viewer = (Player) sender;
                    show(plugin, sender, service.query(repo -> {
                        Tournament t = repo.find(args[2]);
                        TournamentGame game = gameId == null
                            ? repo.runningGame(args[2]) : repo.game(gameId);
                        if (t == null || game == null || !t.id.equals(game.tournamentId)
                                || !"RUNNING".equals(game.state)) return null;
                        boolean member = repo.teamFor(t.id, viewer.getUniqueId()) != null;
                        if (!t.publicSpectators && !member
                                && !viewer.hasPermission("starfinbedwars.tournament.referee")) return null;
                        return game;
                    }), game -> {
                        if (game == null) { sender.sendMessage(P + "§cEspectador indisponivel."); return; }
                        GameMatch match = plugin.getMatchEngine().getMatchForArena(game.arenaId);
                        if (match == null || match.getTournamentAdmission() == null
                                || !game.id.equals(match.getTournamentAdmission().game.id)) {
                            sender.sendMessage(P + "§cPartida nao esta nesta instancia."); return;
                        }
                        service.query(repo -> repo.find(args[2])).thenAccept(t ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (t == null || !viewer.isOnline()) return;
                                if (plugin.getMatchEngine().getDirectSpectators(match).size()
                                        >= t.spectatorLimit) {
                                    viewer.sendMessage(P + "§cLimite de espectadores atingido."); return;
                                }
                                try { plugin.getMatchEngine().spectate(viewer, game.arenaId); }
                                catch (RuntimeException error) { viewer.sendMessage(P + "§c" + error.getMessage()); }
                            }));
                    }); return;
                case "admin": admin(plugin, sender, args); return;
                default: help(sender);
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(P + "§cArgumento invalido: " + invalid.getMessage());
        }
    }
    private static void admin(StarfinBedWarsPlugin plugin, CommandSender sender, String[] args) {
        if (!sender.hasPermission("starfinbedwars.tournament.admin")) {
            sender.sendMessage(P + "§cSem permissao."); return;
        }
        if (args.length < 3) { adminHelp(sender); return; }
        TournamentService service = plugin.getTournaments();
        String action = args[2].toLowerCase(java.util.Locale.ROOT);
        String permission = ("create".equals(action) || "wizard".equals(action)) ? "create"
            : ("edit".equals(action) || "seed".equals(action)
                || "schedule-match".equals(action) || "schedule".equals(action)
                || "open".equals(action) || "close".equals(action)
                || "generate".equals(action)) ? "configure"
            : "start".equals(action) ? "start"
            : ("pause".equals(action) || "resume".equals(action)) ? "pause"
            : "cancel".equals(action) ? "cancel"
            : ("replay".equals(action) || "substitute".equals(action)
                || "walkover".equals(action)) ? "resolve"
            : "finalize".equals(action) ? "finalize" : "consult";
        if (!sender.hasPermission("starfinbedwars.tournament.admin." + permission)) {
            sender.sendMessage(P + "§cSem permissao para esta operacao."); return;
        }
        String actor = sender.getName();
        if ("wizard".equals(action)) {
            TournamentWizard.execute(plugin, sender, args); return;
        }
        if ("create".equals(action) && args.length == 4 && confirm(args[3])) {
            show(plugin, sender, service.query(repo -> repo.create(service.settings(), actor)),
                t -> sender.sendMessage(P + "§aRascunho criado: " + t.id)); return;
        }
        if ("create".equals(action) && args.length == 5 && confirm(args[4])) {
            YamlConfiguration proposal = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "tournaments.yml"));
            proposal.set("proposal.id", args[3]);
            TournamentSettings settings = new TournamentSettings(proposal);
            show(plugin, sender, service.query(repo -> repo.create(settings, actor)),
                t -> sender.sendMessage(P + "§aRascunho criado: " + t.id)); return;
        }
        if ("audit".equals(action) && args.length == 4) {
            show(plugin, sender, service.query(repo -> repo.auditTrail(args[3], 20)),
                rows -> { for (String row : rows) sender.sendMessage("§7" + row); }); return;
        }
        if ("matches".equals(action) && args.length == 4) {
            execute(plugin, sender, new String[]{"tournament", "matches", args[3]}); return;
        }
        if ("edit".equals(action) && args.length == 7 && confirm(args[6])) {
            show(plugin, sender, service.query(repo -> repo.editDraft(args[3], args[4],
                args[5], actor)), t -> sender.sendMessage(P + "§aRascunho atualizado: " + t.id));
            return;
        }
        if ("status".equals(action) && args.length == 4) {
            execute(plugin, sender, new String[]{"tournament", "info", args[3]}); return;
        }
        if ("seed".equals(action) && args.length == 6) {
            UUID team = UUID.fromString(args[4]); int seed = positive(args[5]);
            show(plugin, sender, service.query(repo -> {
                repo.seed(args[3], team, seed, actor); return true;
            }), ignored -> sender.sendMessage(P + "§aSeed atualizada.")); return;
        }
        if ("schedule-match".equals(action) && args.length == 7 && confirm(args[6])) {
            UUID match = UUID.fromString(args[4]); long when = Instant.parse(args[5]).toEpochMilli();
            show(plugin, sender, service.query(repo -> {
                repo.scheduleMatch(args[3], match, when, actor); return true;
            }), ignored -> sender.sendMessage(P + "§aConfronto programado.")); return;
        }
        if ("replay".equals(action) && args.length == 6 && confirm(args[5])) {
            UUID match = UUID.fromString(args[4]);
            show(plugin, sender, service.query(repo -> {
                repo.replayReviewed(args[3], match, actor); return true;
            }), ignored -> sender.sendMessage(P + "§aNovo jogo solicitado.")); return;
        }
        if ("substitute".equals(action) && args.length >= 9
                && confirm(args[args.length - 1])) {
            UUID team = UUID.fromString(args[4]);
            UUID outgoing = UUID.fromString(args[5]);
            UUID incoming = UUID.fromString(args[6]);
            StringBuilder reason = new StringBuilder();
            for (int n = 7; n < args.length - 1; n++) {
                if (reason.length() > 0) reason.append(' ');
                reason.append(args[n]);
            }
            show(plugin, sender, service.query(repo -> {
                repo.substitute(args[3], team, outgoing, incoming, actor, reason.toString());
                return true;
            }), ignored -> sender.sendMessage(P + "§aSubstituicao pendente de confirmacao do novo jogador."));
            return;
        }
        if ("walkover".equals(action) && args.length >= 8
                && confirm(args[args.length - 1])) {
            UUID match = UUID.fromString(args[4]);
            UUID winner = UUID.fromString(args[5]);
            StringBuilder reason = new StringBuilder();
            for (int n = 6; n < args.length - 1; n++) {
                if (reason.length() > 0) reason.append(' ');
                reason.append(args[n]);
            }
            show(plugin, sender, service.query(repo -> {
                repo.adjudicateWalkover(args[3], match, winner, actor, reason.toString());
                return true;
            }), ignored -> sender.sendMessage(P + "§aDecisao por ausencia registrada na auditoria."));
            return;
        }
        if (args.length == 5 && confirm(args[4])) {
            String id = args[3];
            if ("generate".equals(action)) {
                show(plugin, sender, service.query(repo -> repo.generate(id, actor)),
                    t -> sender.sendMessage(P + "§aChave publicada.")); return;
            }
            if ("resume".equals(action)) {
                show(plugin, sender, service.query(repo -> repo.resume(id, actor)),
                    t -> sender.sendMessage(P + "§aTorneio retomado.")); return;
            }
            if ("finalize".equals(action)) {
                show(plugin, sender, service.query(repo -> repo.finalizeTournament(id, actor)),
                    t -> sender.sendMessage(P + "§aTorneio finalizado. Campeao: " + t.champion)); return;
            }
            Tournament.State state = null;
            switch (action) {
                case "schedule": state = Tournament.State.SCHEDULED; break;
                case "open": state = Tournament.State.REGISTRATION_OPEN; break;
                case "close": state = Tournament.State.REGISTRATION_CLOSED; break;
                case "start": state = Tournament.State.RUNNING; break;
                case "pause": state = Tournament.State.PAUSED; break;
                case "cancel": state = Tournament.State.CANCELLED; break;
                default: break;
            }
            if (state != null) {
                final Tournament.State target = state;
                show(plugin, sender, service.query(repo -> repo.transition(id, target, actor)),
                    t -> sender.sendMessage(P + "§aEstado: " + t.state)); return;
            }
        }
        adminHelp(sender);
    }
    private static String shortId(UUID id) {
        return id == null ? "BYE/?" : id.toString().substring(0, 8);
    }
    private static int positive(String text) {
        int number = Integer.parseInt(text);
        if (number < 1 || number > 1000) throw new IllegalArgumentException("Numero fora do limite");
        return number;
    }
    private static boolean confirm(String value) { return "CONFIRM".equals(value); }
    private static <T> void show(StarfinBedWarsPlugin plugin, CommandSender sender,
            CompletableFuture<T> future, java.util.function.Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    Throwable root = error;
                    while (root.getCause() != null) root = root.getCause();
                    sender.sendMessage(P + "§c" + (root.getMessage() == null
                        ? "Operacao recusada." : root.getMessage()));
                } else onSuccess.accept(value);
            });
        });
    }
    private static void help(CommandSender sender) {
        sender.sendMessage(P + "§e/sbw tournament <list|info|join|confirm|checkin|leave|team|bracket|matches|history|rewards|spectate|admin>");
    }
    private static void adminHelp(CommandSender sender) {
        sender.sendMessage(P + "§e/sbw tournament admin wizard [set|next|back|confirm] ou create <id> CONFIRM");
        sender.sendMessage(P + "§eadmin <schedule|open|close|generate|start|pause|resume|cancel|finalize> <id> CONFIRM");
        sender.sendMessage(P + "§eadmin edit <id> <campo> <valor> CONFIRM | seed <id> <team-uuid> <n> | schedule-match <id> <match-uuid> <ISO-UTC> CONFIRM | replay <id> <match-uuid> CONFIRM | audit <id>");
        sender.sendMessage(P + "§eadmin substitute <id> <team-uuid> <old-uuid> <new-uuid> <justificativa> CONFIRM");
        sender.sendMessage(P + "§eadmin walkover <id> <match-uuid> <winner-team-uuid> <justificativa> CONFIRM");
    }
}
