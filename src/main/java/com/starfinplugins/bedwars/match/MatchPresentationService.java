package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.GameTeam;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.economy.generator.ResourceGeneratorService;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.ReconnectLifecycleEvent;
import com.starfinplugins.bedwars.service.ManagedService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.text.SimpleDateFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class MatchPresentationService implements ManagedService, Listener {
    private final Plugin plugin;
    private final MatchEngineService matches;
    private final ResourceGeneratorService generators;
    private final TabSkinService skins;
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<UUID, Scoreboard>();
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<UUID, Scoreboard>();
    private final Map<UUID, String> tabContents = new HashMap<UUID, String>();
    private boolean tabAvailable = true;
    private BukkitTask refreshTask;

    public MatchPresentationService(Plugin plugin, MatchEngineService matches,
                                   ResourceGeneratorService generators) {
        this.plugin = plugin;
        this.matches = matches;
        this.generators = generators;
        this.skins = new TabSkinService(plugin, matches);
    }

    @Override
    public String getName() { return "match-presentation"; }

    @Override
    public void start() {
        skins.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
            }
        }, 1L, 20L);
    }

    @Override
    public void stop() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) restoreBoard(player);
        skins.stop();
        previousBoards.clear();
        activeBoards.clear();
        tabContents.clear();
    }

    @EventHandler
    public void onLifecycle(MatchLifecycleEvent event) {
        GameParticipant participant = event.getParticipant();
        Player actor = participant == null ? null : Bukkit.getPlayer(participant.getPlayerId());
        switch (event.getType()) {
            case PLAYER_JOINED:
                notifyMatch(event.getMatch(), message("messages.player-joined",
                    "&7{player} &eentrou (&b{on}&e/&b{max}&e)!",
                    "player", name(actor),
                    "on", Integer.toString(event.getMatch().getConnectedCount()),
                    "max", Integer.toString(event.getMatch().getArena().getMaximumPlayers())));
                if (actor != null) actor.sendMessage(message("messages.you-joined",
                    "&aVoce entrou no mapa &e{arena}&a.",
                    "arena", event.getMatch().getArena().getDisplayName()));
                break;
            case PLAYER_LEFT:
                notifyMatch(event.getMatch(), message("messages.player-left",
                    "&7{player} &csaiu da partida.",
                    "player", name(actor)));
                break;
            case PLAYER_RECONNECTED:
                if (actor != null) actor.sendMessage(message("messages.reconnected",
                    "&aVoce retornou a partida!"));
                break;
            case COUNTDOWN_STARTED:
                notifyMatch(event.getMatch(), message("messages.lobby-ready",
                    "&aJogadores suficientes! &ePreparando inicio da partida."));
                break;
            case COUNTDOWN_CANCELLED:
                notifyMatch(event.getMatch(), message("messages.countdown-cancelled",
                    "&cContagem cancelada: aguardando mais jogadores."));
                break;
            case STARTED:
                // The engine owns the single start announcement. Presentation provides
                // only the configurable onboarding tutorial, once per match start.
                java.util.List<String> tutorial = plugin.getConfig()
                    .getStringList("messages.start-tutorial");
                if (tutorial.isEmpty()) {
                    tutorial = java.util.Arrays.asList(
                        "&8&m--------------------------------",
                        "&e&lBED WARS &7- &fProteja sua cama e destrua as inimigas.",
                        "&7Colete recursos, compre itens e melhore sua equipe.",
                        "&8&m--------------------------------");
                }
                for (GameParticipant member : event.getMatch().getParticipantList()) {
                    Player viewer = Bukkit.getPlayer(member.getPlayerId());
                    if (viewer == null || !viewer.isOnline()
                            || viewer.getWorld() != matches.getWorld(event.getMatch().getId())) continue;
                    for (String line : tutorial) {
                        viewer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            MatchMessageTemplates.format(line,
                                "arena", event.getMatch().getArena().getDisplayName())));
                    }
                }
                break;
            case BED_DESTROYED:
                notifyBedDestroyed(event);
                break;
            case PLAYER_ELIMINATED:
                notifyMatch(event.getMatch(), ChatColor.RED + name(actor) + " foi eliminado.");
                break;
            case ENDED:
                String winner = event.getMatch().getWinnerTeamId();
                notifyMatch(event.getMatch(), winner == null ? ChatColor.YELLOW + "Partida encerrada."
                    : ChatColor.GOLD + "Equipe vencedora: " + teamName(event.getMatch(), winner) + ".");
                break;
            default:
                break;
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
            }
        });
    }

    @EventHandler public void onReconnectLifecycle(ReconnectLifecycleEvent event) {
        GameMatch match = matches.getMatchForPlayer(event.getPlayerId());
        if (match == null || !match.getId().equals(event.getMatchId())) return;
        GameParticipant participant = match.getParticipant(event.getPlayerId());
        if (participant == null) return;
        String message;
        switch (event.getType()) {
            case CREATED:
                message = ChatColor.YELLOW + "Um integrante perdeu a conexao. Prazo de retorno ativo.";
                break;
            case COMPLETED:
                message = ChatColor.GREEN + "Um integrante retornou a partida.";
                break;
            case EXPIRED:
                message = ChatColor.RED + "O prazo de reconexao de um integrante terminou.";
                break;
            default: return;
        }
        for (GameParticipant teammate : match.getParticipantList()) {
            if (!teammate.getTeamId().equals(participant.getTeamId())
                    || teammate.getPlayerId().equals(event.getPlayerId())) continue;
            Player player = Bukkit.getPlayer(teammate.getPlayerId());
            if (player != null) player.sendMessage(ChatColor.AQUA + "[BedWars] " + message);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        skins.onQuit(event.getPlayer());
        UUID id = event.getPlayer().getUniqueId();
        Scoreboard previous = previousBoards.remove(id);
        activeBoards.remove(id);
        tabContents.remove(id);
        if (previous != null) event.getPlayer().setScoreboard(previous);
    }

    private void refresh(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null) match = matches.getSpectatedMatch(player.getUniqueId());
        if (match == null) {
            if (activeBoards.containsKey(player.getUniqueId())) {
                boolean lobbyTab = plugin.getConfig().getBoolean("tab.enabled", true)
                    && plugin.getConfig().getBoolean("tab.lobby.enabled", true);
                restoreBoard(player, !lobbyTab);
            }
            skins.restore(player);
            updateLobbyTab(player);
            return;
        }
        Scoreboard board = activeBoards.get(player.getUniqueId());
        if (board == null) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) return;
            previousBoards.put(player.getUniqueId(), player.getScoreboard());
            board = manager.getNewScoreboard();
            Objective objective = board.registerNewObjective("sbw", "dummy");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.setDisplayName(ChatColor.AQUA + "Starfin BedWars");
            activeBoards.put(player.getUniqueId(), board);
            player.setScoreboard(board);
        }
        Objective objective = board.getObjective("sbw");
        if (objective != null) {
            DisplaySlot desired = plugin.getConfig().getBoolean("scoreboard.enabled", true)
                ? DisplaySlot.SIDEBAR : null;
            if (objective.getDisplaySlot() != desired) objective.setDisplaySlot(desired);
        }
        boolean spectating = matches.getSpectatedMatch(player.getUniqueId()) == match;
        GameParticipant own = match.getParticipant(player.getUniqueId());
        if (match.getState() == MatchState.ENDING || match.getState() == MatchState.RESETTING) {
            renderEnding(board, match, player);
        } else if (match.getState() == MatchState.IN_GAME) {
            renderInGame(board, match, player, spectating || own != null
                && own.getState() == ParticipantState.ELIMINATED);
        } else {
            renderLobby(board, match);
        }
        syncNameTags(board, match);
        skins.refresh(player, match);
        updateTab(player, match);
    }

    private void updateTab(Player player, GameMatch match) {
        if (!tabAvailable || !plugin.getConfig().getBoolean("tab.enabled", true)) return;
        String header = plugin.getConfig().getString("tab.header",
            "&b&lSTARFIN BEDWARS\n{footer}");
        String footer = plugin.getConfig().getString("tab.footer",
            "&eStarfinPlugins");
        Map<String, String> tokens = commonTokens(match);
        tokens.put("mode", gameMode(match));
        tokens.put("players", Integer.toString(onlineCount(match)));
        tokens.put("max", Integer.toString(match.getArena().getMaximumPlayers()));
        header = formatTabText(header, tokens);
        footer = formatTabText(footer, tokens);
        String key = header + "\u0000" + footer;
        if (key.equals(tabContents.get(player.getUniqueId()))) return;
        try {
            TabHeaderV1_8_R3.send(player, header, footer);
            tabContents.put(player.getUniqueId(), key);
        } catch (RuntimeException error) {
            tabAvailable = false;
            plugin.getLogger().warning("Cabecalho TAB desativado: " + error.getMessage());
        }
    }

    private void updateLobbyTab(Player player) {
        if (!tabAvailable || !plugin.getConfig().getBoolean("tab.enabled", true)
                || !plugin.getConfig().getBoolean("tab.lobby.enabled", true)) return;
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("footer", brandFooter());
        String header = formatTabText(plugin.getConfig().getString("tab.lobby.header",
            "&b&lSTARFIN BEDWARS\\n{footer}"), tokens);
        String footer = formatTabText(plugin.getConfig().getString("tab.lobby.footer",
            "{footer}"), tokens);
        String key = header + "\u0000" + footer;
        if (key.equals(tabContents.get(player.getUniqueId()))) return;
        try {
            TabHeaderV1_8_R3.send(player, header, footer);
            tabContents.put(player.getUniqueId(), key);
        } catch (RuntimeException error) {
            tabAvailable = false;
            plugin.getLogger().warning("Cabecalho TAB do lobby desativado: " + error.getMessage());
        }
    }

    private void syncNameTags(Scoreboard board, GameMatch match) {
        Set<String> wanted = new HashSet<String>();
        int index = 0;
        for (GameTeam gameTeam : match.getTeams().values()) {
            String id = String.format(Locale.ROOT, "bw%03d", index++);
            wanted.add(id);
            org.bukkit.scoreboard.Team tag = board.getTeam(id);
            if (tag == null) tag = board.registerNewTeam(id);
            ChatColor color = ChatColor.getByChar(gameTeam.getDefinition().getColor());
            if (color == null) color = ChatColor.WHITE;
            String initial = gameTeam.getDefinition().getDisplayName().substring(0, 1)
                .toUpperCase(Locale.ROOT);
            String prefix = plugin.getConfig().getString("tab.team-prefix", "[{initial}] ")
                .replace("{initial}", initial)
                .replace("{team}", gameTeam.getDefinition().getDisplayName());
            String effectivePrefix = plugin.getConfig().getBoolean("tab.team-prefixes", true)
                ? trim(color + prefix, 16) : "";
            if (!effectivePrefix.equals(tag.getPrefix())) tag.setPrefix(effectivePrefix);
            Set<String> members = new HashSet<String>();
            for (UUID uuid : gameTeam.getMembers()) {
                Player member = Bukkit.getPlayer(uuid);
                GameParticipant participant = match.getParticipant(uuid);
                if (member != null && member.isOnline() && participant != null
                        && participant.getState() != ParticipantState.ELIMINATED
                        && participant.getState() != ParticipantState.ABANDONED) {
                    members.add(member.getName());
                }
            }
            for (String old : new HashSet<String>(tag.getEntries())) {
                if (!members.contains(old)) tag.removeEntry(old);
            }
            for (String member : members) {
                if (!tag.hasEntry(member)) tag.addEntry(member);
            }
        }
        wanted.add("bwzzz");
        org.bukkit.scoreboard.Team spectators = board.getTeam("bwzzz");
        if (spectators == null) spectators = board.registerNewTeam("bwzzz");
        String spectatorPrefix = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("tab.spectator-prefix", "&7[S] "));
        if (!spectatorPrefix.equals(spectators.getPrefix())) spectators.setPrefix(spectatorPrefix);
        Set<String> viewing = new HashSet<String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            GameParticipant participant = match.getParticipant(online.getUniqueId());
            if (plugin.getConfig().getBoolean("tab.show-spectators", true)
                    && (matches.getSpectatedMatch(online.getUniqueId()) == match
                    || participant != null && participant.getState() == ParticipantState.ELIMINATED)) {
                viewing.add(online.getName());
            }
        }
        for (String old : new HashSet<String>(spectators.getEntries())) {
            if (!viewing.contains(old)) spectators.removeEntry(old);
        }
        for (String name : viewing) if (!spectators.hasEntry(name)) spectators.addEntry(name);
        for (org.bukkit.scoreboard.Team tag : new HashSet<org.bukkit.scoreboard.Team>(board.getTeams())) {
            if (tag.getName().startsWith("bw") && !wanted.contains(tag.getName())) tag.unregister();
        }
    }

    private void renderMain(Scoreboard board) {
        List<String> lines = new ArrayList<String>();
        lines.add(dateLine(null));
        lines.add("");
        lines.add(ChatColor.WHITE + "Nivel: " + ChatColor.YELLOW + "0");
        lines.add(ChatColor.WHITE + "Progresso: " + ChatColor.AQUA + "0/5k");
        lines.add(ChatColor.DARK_GRAY + "[----------]");
        lines.add("");
        lines.add(ChatColor.WHITE + "Tokens: " + ChatColor.GREEN + "0");
        lines.add(ChatColor.WHITE + "Abates: " + ChatColor.GREEN + "0");
        lines.add(ChatColor.WHITE + "Vitorias: " + ChatColor.GREEN + "0");
        lines.add("");
        lines.add(brandFooter());
        renderLines(board, boardTitle(), lines);
    }

    private void renderLobby(Scoreboard board, GameMatch match) {
        if ("CLASSIC".equalsIgnoreCase(plugin.getConfig().getString("scoreboard.preset", "CLASSIC"))) {
            renderConfiguredLobby(board, match);
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(dateLine(match.getArena().getId()));
        lines.add(ChatColor.GRAY + "Mapa: " + ChatColor.GREEN
            + trim(match.getArena().getDisplayName(), 18));
        lines.add(ChatColor.GRAY + "Jogadores: " + ChatColor.GREEN + onlineCount(match)
            + ChatColor.GRAY + "/" + match.getArena().getMaximumPlayers());
        lines.add("");
        Integer lobbyCountdown = matches.getLobbyCountdown(match);
        Integer spawnCountdown = matches.getSpawnOpeningCountdown(match);
        if (match.getState() == MatchState.STARTING && spawnCountdown != null) {
            lines.add(ChatColor.YELLOW + "Spawns abrem em...");
            lines.add(ChatColor.GREEN + Integer.toString(spawnCountdown) + "s");
        } else if (lobbyCountdown != null) {
            lines.add(ChatColor.YELLOW + "Comeca em...");
            lines.add(ChatColor.GREEN + Integer.toString(lobbyCountdown) + "s");
        } else {
            lines.add(ChatColor.YELLOW + "Aguardando...");
        }
        lines.add("");
        lines.add(ChatColor.GRAY + "Modo: " + ChatColor.GREEN + gameMode(match));
        lines.add(ChatColor.GRAY + "Versao: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        lines.add("");
        lines.add(brandFooter());
        renderLines(board, boardTitle(), lines);
    }

    private void renderInGame(Scoreboard board, GameMatch match, Player viewer,
                              boolean spectating) {
        if ("CLASSIC".equalsIgnoreCase(plugin.getConfig().getString("scoreboard.preset", "CLASSIC"))) {
            renderClassicInGame(board, match, viewer, spectating);
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(dateLine(match.getArena().getId()));
        if (spectating) lines.add(ChatColor.GRAY + "Espectando partida");
        lines.add("");
        String diamond = generators.getCentralGeneratorStatus(match.getId(), ResourceType.DIAMOND);
        int countdownPart = diamond == null ? -1 : diamond.lastIndexOf(" em ");
        lines.add(countdownPart < 0 ? ChatColor.GRAY + "Geradores indisponiveis"
            : ChatColor.WHITE + diamond.substring(0, countdownPart)
                + ChatColor.GREEN + diamond.substring(countdownPart));
        lines.add("");
        int maximumTeams = Math.min(match.getTeams().size(), spectating ? 7 : 8);
        int shown = 0;
        for (GameTeam team : match.getTeams().values()) {
            if (shown++ >= maximumTeams) break;
            ChatColor color = ChatColor.getByChar(team.getDefinition().getColor());
            if (color == null) color = ChatColor.WHITE;
            String status = teamStatus(match, team);
            String ownTeam = team.getMembers().contains(viewer.getUniqueId())
                ? ChatColor.GRAY + " VOCE" : "";
            String initial = team.getDefinition().getDisplayName().substring(0, 1).toUpperCase(Locale.ROOT);
            lines.add(color + initial + " " + trim(team.getDefinition().getDisplayName(), 7)
                + ChatColor.WHITE + ": " + status + ownTeam);
        }
        if (match.getTeams().size() > maximumTeams) {
            lines.add(ChatColor.GRAY + "+" + (match.getTeams().size() - maximumTeams) + " equipes");
        }
        lines.add("");
        lines.add(brandFooter());
        renderLines(board, boardTitle(), lines);
    }

    private void renderClassicInGame(Scoreboard board, GameMatch match, Player viewer,
                                     boolean spectating) {
        Map<String, String> tokens = commonTokens(match);
        ResourceGeneratorService.NextUpgrade next = generators.getNextUpgrade(match.getId());
        if (next == null) {
            tokens.put("next_upgrade_title", ChatColor.GRAY + "Geradores no maximo");
            tokens.put("next_upgrade", "");
        } else {
            String resource = next.getResource() == ResourceType.DIAMOND ? "Diamantes" : "Esmeraldas";
            long remaining = next.getSecondsRemaining();
            tokens.put("next_upgrade_title", ChatColor.WHITE + "Prox. Aprimoramento:");
            tokens.put("next_upgrade", ChatColor.GREEN + "G. de " + resource + " "
                + roman(next.getLevel()) + " "
                + String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60));
        }
        List<String> teamLines = new ArrayList<String>();
        int limit = Math.min(match.getTeams().size(), 8);
        int shown = 0;
        for (GameTeam team : match.getTeams().values()) {
            if (shown++ >= limit) break;
            boolean eliminated = !team.isEligible(match);
            String eliminatedDisplay = plugin.getConfig().getString("scoreboard.eliminated-display", "ZERO");
            if (eliminated && "HIDE".equalsIgnoreCase(eliminatedDisplay)) continue;
            ChatColor color = ChatColor.getByChar(team.getDefinition().getColor());
            if (color == null) color = ChatColor.WHITE;
            String initial = team.getDefinition().getDisplayName().substring(0, 1).toUpperCase(Locale.ROOT);
            String status = eliminated && "TEXT".equalsIgnoreCase(eliminatedDisplay)
                ? ChatColor.RED + "ELIM." : ChatColor.GRAY + "(" + team.getActiveCount(match) + ")";
            String own = team.getMembers().contains(viewer.getUniqueId())
                ? plugin.getConfig().getString("scoreboard.own-marker", "&e *") : "";
            Map<String, String> teamTokens = new HashMap<String, String>();
            teamTokens.put("team_color", color.toString());
            teamTokens.put("team_initial", initial);
            teamTokens.put("team_name", trim(team.getDefinition().getDisplayName(), 10));
            teamTokens.put("alive", Integer.toString(team.getActiveCount(match)));
            teamTokens.put("status", status);
            teamTokens.put("own_marker", own);
            teamLines.add(formatLine(plugin.getConfig().getString("scoreboard.team-line",
                "{team_color}&l{team_initial} &f{team_name} {status}{own_marker}"), teamTokens));
        }
        tokens.put("spectating", spectating ? ChatColor.GRAY + "Espectando" : "");
        List<String> lines = configuredLines("scoreboard.layouts.in-game",
            Arrays.asList("{date}  &8{arena_id}", "", "{next_upgrade_title}",
                "{next_upgrade}", "", "{teams}", "{spectating}", "", "{footer}"),
            tokens, teamLines);
        renderLines(board, boardTitle(), lines);
    }

    private void renderConfiguredLobby(Scoreboard board, GameMatch match) {
        Map<String, String> tokens = commonTokens(match);
        tokens.put("players", Integer.toString(onlineCount(match)));
        tokens.put("max", Integer.toString(match.getArena().getMaximumPlayers()));
        tokens.put("mode", gameMode(match));
        Integer lobbyCountdown = matches.getLobbyCountdown(match);
        Integer opening = matches.getSpawnOpeningCountdown(match);
        tokens.put("countdown_title", opening != null ? "Spawns abrem em..."
            : lobbyCountdown != null ? "Comeca em..." : "Aguardando...");
        tokens.put("countdown", opening != null ? opening + "s"
            : lobbyCountdown != null ? lobbyCountdown + "s" : "");
        List<String> lines = configuredLines("scoreboard.layouts.lobby",
            Arrays.asList("{date}  &8{arena_id}", "&7Mapa: &a{arena}",
                "&7Jogadores: &a{players}&7/{max}", "", "&e{countdown_title}",
                "&a{countdown}", "", "&7Modo: &a{mode}", "", "{footer}"),
            tokens, null);
        renderLines(board, boardTitle(), lines);
    }

    private String roman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return Integer.toString(level);
        }
    }

    private String teamStatus(GameMatch match, GameTeam team) {
        String display = plugin.getConfig().getString("scoreboard.team-status", "BED");
        if ("ALIVE".equalsIgnoreCase(display)) {
            return ChatColor.GREEN + Integer.toString(team.getActiveCount(match));
        }
        if ("TEXT".equalsIgnoreCase(display)) {
            return !team.isEligible(match) ? ChatColor.RED + "ELIM."
                : team.isBedAlive() ? ChatColor.GREEN + "VIVA" : ChatColor.YELLOW + "SEM CAMA";
        }
        if ("COMPACT".equalsIgnoreCase(display)) {
            return !team.isEligible(match) ? ChatColor.RED + "-"
                : team.isBedAlive() ? ChatColor.GREEN + "V" : ChatColor.RED + "X";
        }
        return !team.isEligible(match) ? ChatColor.RED + "ELIM."
            : team.isBedAlive() ? ChatColor.GREEN + "V" : ChatColor.RED + "X";
    }

    private void renderEnding(Scoreboard board, GameMatch match, Player viewer) {
        if ("CLASSIC".equalsIgnoreCase(plugin.getConfig().getString("scoreboard.preset", "CLASSIC"))) {
            Map<String, String> tokens = commonTokens(match);
            String winner = match.getWinnerTeamId();
            GameTeam team = winner == null ? null : match.getTeam(winner);
            tokens.put("winner", winner == null ? "Sem vencedores"
                : trim(team == null ? winner : team.getDefinition().getDisplayName(), 16));
            tokens.put("result", winner == null ? "" : winner.equals(teamOf(match, viewer))
                ? ChatColor.GREEN + "Vitoria" : ChatColor.RED + "Derrota");
            renderLines(board, boardTitle(), configuredLines("scoreboard.layouts.ending",
                Arrays.asList("{date}  &8{arena_id}", "", "&ePartida encerrada",
                    "&fVencedor: &a{winner}", "{result}", "", "{footer}"), tokens, null));
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(dateLine(match.getArena().getId()));
        lines.add("");
        lines.add(ChatColor.YELLOW + "Partida encerrada");
        String winner = match.getWinnerTeamId();
        if (winner == null) lines.add(ChatColor.GRAY + "Sem vencedores");
        else {
            GameTeam team = match.getTeam(winner);
            lines.add(ChatColor.WHITE + "Vencedor: " + ChatColor.GREEN
                + trim(team == null ? winner : team.getDefinition().getDisplayName(), 16));
            lines.add(winner.equals(teamOf(match, viewer))
                ? ChatColor.GREEN + "Vitoria" : ChatColor.RED + "Derrota");
        }
        lines.add("");
        lines.add(brandFooter());
        renderLines(board, boardTitle(), lines);
    }

    private Map<String, String> commonTokens(GameMatch match) {
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("date", dateLine(null));
        tokens.put("arena_id", trim(match.getArena().getId().toUpperCase(Locale.ROOT), 8));
        tokens.put("arena", match.getArena().getDisplayName());
        tokens.put("footer", brandFooter());
        return tokens;
    }

    private List<String> configuredLines(String path, List<String> defaults,
                                          Map<String, String> tokens, List<String> teamLines) {
        List<String> templates = plugin.getConfig().getStringList(path);
        if (templates.isEmpty()) templates = defaults;
        List<String> lines = new ArrayList<String>();
        for (String template : templates) {
            if ("{teams}".equals(template)) {
                if (teamLines != null) lines.addAll(teamLines);
                continue;
            }
            String line = formatLine(template, tokens);
            if (!template.isEmpty() && line.isEmpty()) continue;
            lines.add(line);
        }
        return lines;
    }

    static String formatTabText(String template, Map<String, String> tokens) {
        return formatLine(template.replace("\\n", "\n"), tokens);
    }

    private static String formatLine(String template, Map<String, String> tokens) {
        String result = ChatColor.translateAlternateColorCodes('&', template);
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}",
                ChatColor.translateAlternateColorCodes('&', entry.getValue()));
        }
        return result;
    }

    private String teamOf(GameMatch match, Player player) {
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        return participant == null ? null : participant.getTeamId();
    }

    private void renderLines(Scoreboard board, String title, List<String> lines) {
        Objective objective = board.getObjective("sbw");
        if (objective == null) return;
        if (!title.equals(objective.getDisplayName())) objective.setDisplayName(title);
        int index = 0;
        for (String original : lines) {
            if (index >= 15) break;
            String line = original;
            String entry = ChatColor.values()[index % ChatColor.values().length].toString() + ChatColor.RESET;
            String teamName = "line" + index;
            org.bukkit.scoreboard.Team lineTeam = board.getTeam(teamName);
            if (lineTeam == null) lineTeam = board.registerNewTeam(teamName);
            for (String oldEntry : new ArrayList<String>(lineTeam.getEntries())) {
                if (!oldEntry.equals(entry)) lineTeam.removeEntry(oldEntry);
            }
            if (!lineTeam.hasEntry(entry)) lineTeam.addEntry(entry);
            String[] parts = splitSidebarLine(line);
            String prefix = parts[0];
            String suffix = parts[1];
            if (!prefix.equals(lineTeam.getPrefix())) lineTeam.setPrefix(prefix);
            if (!suffix.equals(lineTeam.getSuffix())) lineTeam.setSuffix(suffix);
            objective.getScore(entry).setScore(lines.size() - index);
            index++;
        }
        for (int stale = index; stale < 15; stale++) {
            org.bukkit.scoreboard.Team staleTeam = board.getTeam("line" + stale);
            if (staleTeam == null) continue;
            for (String entry : new ArrayList<String>(staleTeam.getEntries())) board.resetScores(entry);
            staleTeam.unregister();
        }
    }

    static String[] splitSidebarLine(String line) {
        String text = line == null ? "" : line.substring(0, Math.min(line.length(), 32));
        if (text.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) text = text.substring(0, text.length() - 1);
        int split = Math.min(16, text.length());
        if (split > 0 && split < text.length() && text.charAt(split - 1) == ChatColor.COLOR_CHAR) split--;
        String prefix = text.substring(0, split);
        String suffix = text.substring(split);
        if (!suffix.isEmpty() && suffix.charAt(0) != ChatColor.COLOR_CHAR) {
            suffix = ChatColor.getLastColors(prefix) + suffix;
        }
        if (suffix.length() > 16) suffix = suffix.substring(0, 16);
        if (suffix.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
            suffix = suffix.substring(0, suffix.length() - 1);
        }
        return new String[] {prefix, suffix};
    }

    private String brandFooter() {
        return ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("scoreboard.footer", "&estarfinplugins&f.com.br"));
    }

    private String boardTitle() {
        return ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("scoreboard.title", "&e&lBED WARS"));
    }

    private String dateLine(String arenaId) {
        String date = new SimpleDateFormat("dd/MM/yy", Locale.ROOT).format(new Date());
        return ChatColor.GRAY + date + (arenaId == null ? "" : "  " + ChatColor.DARK_GRAY
            + trim(arenaId.toUpperCase(Locale.ROOT), 8));
    }

    private String gameMode(GameMatch match) {
        int capacity = -1;
        for (GameTeam team : match.getTeams().values()) {
            int teamCapacity = team.getDefinition().getCapacity();
            if (capacity < 0) capacity = teamCapacity;
            else if (capacity != teamCapacity) return "Personalizado";
        }
        switch (capacity) {
            case 1: return "Solo";
            case 2: return "Duplas";
            case 3: return "Trios";
            case 4: return "Quartetos";
            default: return "Personalizado";
        }
    }

    private void restoreBoard(Player player) {
        restoreBoard(player, true);
    }

    private void restoreBoard(Player player, boolean clearTabHeader) {
        skins.restore(player);
        UUID id = player.getUniqueId();
        if (tabContents.remove(id) != null && tabAvailable && clearTabHeader) {
            try { TabHeaderV1_8_R3.send(player, "", ""); }
            catch (RuntimeException error) { tabAvailable = false; }
        }
        if (!activeBoards.containsKey(id)) return;
        Scoreboard previous = previousBoards.remove(id);
        activeBoards.remove(id);
        if (previous != null) player.setScoreboard(previous);
    }

    private String message(String path, String fallback, String... tokens) {
        return ChatColor.translateAlternateColorCodes('&',
            MatchMessageTemplates.format(plugin.getConfig().getString(path, fallback), tokens));
    }

    private void notifyMatch(GameMatch match, String message) {
        notifyMatch(match, message, null);
    }

    private void notifyBedDestroyed(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        GameTeam team = match.getTeam(event.getTeamId());
        if (team == null) return;
        Player destroyer = event.getParticipant() == null ? null
            : Bukkit.getPlayer(event.getParticipant().getPlayerId());
        ChatColor color = ChatColor.getByChar(team.getDefinition().getColor());
        if (color == null) color = ChatColor.WHITE;
        String configured = destroyer == null
            ? plugin.getConfig().getString("bed-alert.broadcast-unknown",
                "&cA cama da equipe {team} foi destruida!")
            : plugin.getConfig().getString("bed-alert.broadcast",
                "&cA cama da equipe {team} foi destruida por {player}!");
        String message = ChatColor.translateAlternateColorCodes('&', configured)
            .replace("{team}", color + team.getDefinition().getDisplayName() + ChatColor.RED)
            .replace("{player}", destroyer == null ? "" : destroyer.getName());
        notifyMatch(match, message);
        Sound sound;
        try {
            sound = Sound.valueOf(plugin.getConfig().getString("bed-alert.sound", "WITHER_DEATH"));
        } catch (IllegalArgumentException invalidSound) {
            sound = Sound.WITHER_DEATH;
        }
        float volume = (float) Math.max(0.0D, Math.min(2.0D,
            plugin.getConfig().getDouble("bed-alert.volume", 0.7D)));
        float pitch = (float) Math.max(0.5D, Math.min(2.0D,
            plugin.getConfig().getDouble("bed-alert.pitch", 1.0D)));
        for (GameParticipant participant : match.getParticipantList()) {
            if (!team.getDefinition().getId().equals(participant.getTeamId())) continue;
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player == null || !player.isOnline()) continue;
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void notifyMatch(GameMatch match, String message, UUID excluded) {
        for (GameParticipant participant : match.getParticipantList()) {
            if (excluded != null && excluded.equals(participant.getPlayerId())) continue;
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null) player.sendMessage(ChatColor.AQUA + "[BedWars] " + message);
        }
    }

    private int onlineCount(GameMatch match) {
        int count = 0;
        for (GameParticipant participant : match.getParticipantList()) {
            Player player = Bukkit.getPlayer(participant.getPlayerId());
            if (player != null && player.isOnline()) count++;
        }
        return count;
    }

    private String name(Player player) {
        return player == null ? "Jogador" : player.getName();
    }

    private String teamName(GameMatch match, String teamId) {
        GameTeam team = teamId == null ? null : match.getTeam(teamId);
        return team == null ? "desconhecida" : team.getDefinition().getDisplayName();
    }

    private String trim(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
