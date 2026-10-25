package com.starfinplugins.bedwars.command;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.arena.ArenaEditingService;
import com.starfinplugins.bedwars.arena.ArenaRegionSelectionService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaGameplayRules;
import com.starfinplugins.bedwars.domain.arena.ArenaBoundsMode;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.AutomaticArenaBounds;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.MatchPointService;
import com.starfinplugins.bedwars.storage.SqlMigrationWriter;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.world.WorldManagementService;
import com.starfinplugins.bedwars.world.SafeLocationResolver;
import com.starfinplugins.bedwars.integration.worldedit.WorldEditSelectionAdapter;
import com.starfinplugins.bedwars.menu.BedWarsMenuService;
import com.starfinplugins.bedwars.stats.RankingSpec;
import com.starfinplugins.bedwars.setup.ArenaSetupWizardService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BedWarsCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String PREFIX = ChatColor.AQUA + "[StarfinBedWars] " + ChatColor.RESET;
    private final StarfinBedWarsPlugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final WorldManagementService worlds;
    private final ArenaEditingService editing;
    private final ArenaRegionSelectionService regionSelection;
    private final WorldEditSelectionAdapter worldEdit;
    private final BedWarsMenuService menus;
    private final ArenaSetupWizardService wizard;
    private final Map<UUID, BoundsDraft> boundsDrafts = new HashMap<UUID, BoundsDraft>();

    public BedWarsCommand(StarfinBedWarsPlugin plugin,
                          ArenaService arenas, MatchEngineService matches, WorldManagementService worlds,
                          ArenaEditingService editing, ArenaRegionSelectionService regionSelection) {
        this(plugin, arenas, matches, worlds, editing, regionSelection, null);
    }
    public BedWarsCommand(StarfinBedWarsPlugin plugin,
                          ArenaService arenas, MatchEngineService matches, WorldManagementService worlds,
                          ArenaEditingService editing, ArenaRegionSelectionService regionSelection,
                          BedWarsMenuService menus) {
        this(plugin, arenas, matches, worlds, editing, regionSelection, menus, null);
    }
    public BedWarsCommand(StarfinBedWarsPlugin plugin,
                          ArenaService arenas, MatchEngineService matches, WorldManagementService worlds,
                          ArenaEditingService editing, ArenaRegionSelectionService regionSelection,
                          BedWarsMenuService menus, ArenaSetupWizardService wizard) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.worlds = worlds;
        this.editing = editing;
        this.regionSelection = regionSelection;
        this.menus = menus;
        this.wizard = wizard;
        this.worldEdit = new WorldEditSelectionAdapter(plugin.getServer().getPluginManager());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reconnect")) {
            if (!(sender instanceof Player)) sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador.");
            else plugin.getReconnect().reconnect((Player) sender);
            return true;
        }
        if (args.length > 0 && "reconnect".equalsIgnoreCase(args[0])) {
            if (args.length >= 2 && ("sessions".equalsIgnoreCase(args[1])
                    || "inspect".equalsIgnoreCase(args[1])
                    || "invalidate".equalsIgnoreCase(args[1]))) {
                if (!sender.hasPermission("starfinbedwars.command.reconnect.admin")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Sem permissao.");
                } else if ("sessions".equalsIgnoreCase(args[1]) && args.length == 2) {
                    sender.sendMessage(PREFIX + "Sessoes pendentes: " + plugin.getReconnect().sessionCount());
                } else if (args.length >= 3) {
                    UUID target;
                    try { target = UUID.fromString(args[2]); }
                    catch (IllegalArgumentException error) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Informe o UUID do jogador.");
                        return true;
                    }
                    if ("inspect".equalsIgnoreCase(args[1]) && args.length == 3) {
                        sender.sendMessage(PREFIX + plugin.getReconnect().status(target));
                    } else if ("invalidate".equalsIgnoreCase(args[1]) && args.length == 4
                            && "confirm".equalsIgnoreCase(args[3])) {
                        sender.sendMessage(PREFIX + (plugin.getReconnect().invalidate(target)
                            ? "Sessao invalidada e auditada." : "Sessao nao encontrada."));
                    } else sender.sendMessage(PREFIX + ChatColor.RED
                        + "Use /sbw reconnect invalidate <uuid> confirm.");
                } else sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /sbw reconnect <sessions|inspect|invalidate>.");
            } else if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador.");
            } else if (args.length == 1) {
                plugin.getReconnect().reconnect((Player) sender);
            } else if (args.length == 2 && "status".equalsIgnoreCase(args[1])) {
                sender.sendMessage(PREFIX + plugin.getReconnect().status(((Player) sender).getUniqueId()));
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED + "Use /sbw reconnect [status].");
            }
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender, label, args.length > 1 ? args[1] : null);
            return true;
        }
        if ("migrate".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("starfinbedwars.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Migracao SQL exige starfinbedwars.admin.");
                return true;
            }
            if (args.length < 3 || args.length > 4
                    || !("preview".equalsIgnoreCase(args[1]) || "apply".equalsIgnoreCase(args[1]))
                    || ("apply".equalsIgnoreCase(args[1])
                        && (args.length != 4 || !"confirm".equalsIgnoreCase(args[3])))
                    || ("preview".equalsIgnoreCase(args[1]) && args.length != 3)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Uso: /sbw migrate <preview|apply> <mysql|sqlite> [confirm]");
                return true;
            }
            SqlMigrationWriter.Target target;
            try {
                target = SqlMigrationWriter.Target.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Destino valido: mysql ou sqlite.");
                return true;
            }
            plugin.getStorageMigration().execute(sender, "apply".equalsIgnoreCase(args[1]), target);
            return true;
        }
        if ("stats".equalsIgnoreCase(args[0]) || "perfil".equalsIgnoreCase(args[0])
                || "nivel".equalsIgnoreCase(args[0])) {
            if (args.length == 1 && sender instanceof Player
                    && ("perfil".equalsIgnoreCase(args[0]) || "nivel".equalsIgnoreCase(args[0]))) {
                if ("perfil".equalsIgnoreCase(args[0])) plugin.getProgressionMenus().openProfile((Player) sender);
                else plugin.getProgressionMenus().openProgress((Player) sender);
                return true;
            }
            if (args.length > 2 || (args.length == 1 && !(sender instanceof Player))) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /sbw stats [jogador|UUID].");
            } else {
                String target = args.length == 2 ? args[1] : ((Player) sender).getUniqueId().toString();
                plugin.getStatistics().showProfile(sender, target);
            }
            return true;
        }
        if ("profile".equalsIgnoreCase(args[0]) || "progress".equalsIgnoreCase(args[0])
                || "rewards".equalsIgnoreCase(args[0]) || "recompensas".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player) || args.length != 1) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /sbw <profile|progress|rewards>.");
                return true;
            }
            if ("profile".equalsIgnoreCase(args[0])) plugin.getProgressionMenus().openProfile((Player) sender);
            else if ("progress".equalsIgnoreCase(args[0])) plugin.getProgressionMenus().openProgress((Player) sender);
            else plugin.getProgressionMenus().openRewards((Player) sender);
            return true;
        }
        if ("top".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player) || args.length > 5) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /sbw top [metrica] [modalidade] [periodo] [pagina].");
                return true;
            }
            try {
                RankingSpec.Metric metric = args.length > 1 ? RankingSpec.Metric.parse(args[1])
                    : RankingSpec.Metric.WINS;
                String mode = args.length > 2 && !"all".equalsIgnoreCase(args[2]) ? args[2] : null;
                RankingSpec.Period period = args.length > 3 ? RankingSpec.Period.parse(args[3])
                    : RankingSpec.Period.ALL;
                int page = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                plugin.getProgressionMenus().openRanking((Player) sender,
                    plugin.getProgressionMenus().spec(metric, period, mode, page));
            } catch (IllegalArgumentException invalid) {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Filtro invalido. Use: /sbw top [wins|games|kills|final_kills|deaths|final_deaths|beds|xp|level|win_rate|kd] [all|solo|duo|trio|squad|custom] [all|weekly|monthly] [pagina].");
            }
            return true;
        }
        if ("idioma".equalsIgnoreCase(args[0]) || "language".equalsIgnoreCase(args[0])
                || "lang".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player) || args.length > 2) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /sbw idioma [pt_BR|en_US|es_ES].");
                return true;
            }
            Player player = (Player) sender;
            if (args.length == 1) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Idioma atual: " + ChatColor.WHITE
                    + plugin.getStatistics().getLanguage(player.getUniqueId()));
                return true;
            }
            try {
                plugin.getStatistics().setLanguage(player, args[1]);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Idioma registrado para sincronizacao na rede: "
                    + com.starfinplugins.bedwars.stats.PlayerPreferences.languageOrDefault(args[1]));
            } catch (java.io.IOException | IllegalStateException | IllegalArgumentException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Nao foi possivel salvar idioma: "
                    + error.getMessage());
            }
            return true;
        }
        if ("chat".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player) || args.length != 2) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Use /sbw chat <global|team|spectator>.");
            } else {
                sender.sendMessage(PREFIX + plugin.getMatchChat().selectChannel((Player) sender, args[1]));
            }
            return true;
        }
        if ("kit".equalsIgnoreCase(args[0]) || "ability".equalsIgnoreCase(args[0])) {
            boolean ability = "ability".equalsIgnoreCase(args[0]);
            if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
                sender.sendMessage(PREFIX + (ability ? "Habilidades: "
                    + join(new ArrayList<String>(plugin.getGameplay().getAbilityCatalog().abilities.keySet()))
                    : "Kits: " + join(new ArrayList<String>(plugin.getGameplay().getSettings().kits.keySet()))));
            } else if (ability && args.length == 5 && "grant".equalsIgnoreCase(args[1])) {
                if (!requireAdmin(sender)) return true;
                Player target = plugin.getServer().getPlayerExact(args[2]);
                com.starfinplugins.bedwars.gameplay.AbilityCatalog.Definition definition =
                    plugin.getGameplay().getAbilityCatalog().abilities.get(args[3]);
                int level;
                try { level = Integer.parseInt(args[4]); }
                catch (NumberFormatException invalid) { level = 0; }
                if (target == null || definition == null || level < 1 || level > definition.maxLevel) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Jogador online, ID e nivel validos obrigatorios.");
                    return true;
                }
                final String grantedId = definition.id;
                plugin.getStatistics().grantAbility(target.getUniqueId(), grantedId, level,
                    confirmed -> sender.sendMessage(PREFIX + (confirmed == 0 ? ChatColor.RED
                        + "MySQL indisponivel; nenhum desbloqueio concedido."
                        : ChatColor.GREEN + "Desbloqueio confirmado: " + target.getName()
                            + " / " + grantedId + " nivel " + confirmed)));
            } else if (ability && args.length == 2 && "use".equalsIgnoreCase(args[1])
                    && sender instanceof Player) {
                plugin.getGameplay().useAbility((Player) sender);
            } else if (args.length == 1 && sender instanceof Player) {
                if (ability) plugin.getGameplay().openAbilities((Player) sender);
                else plugin.getGameplay().openKits((Player) sender);
            } else sender.sendMessage(PREFIX + ChatColor.YELLOW
                + (ability ? "/sbw ability [list|use|grant <jogador> <id> <nivel>]"
                    : "/sbw kit [list]"));
            return true;
        }
        if ("gameplay".equalsIgnoreCase(args[0])) {
            handleGameplay(sender, args);
            return true;
        }
        if ("always".equalsIgnoreCase(args[0])) {
            handleAlways(sender, args);
            return true;
        }
        if ("version".equalsIgnoreCase(args[0]) && args.length == 1) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Versao " + plugin.getDescription().getVersion());
            return true;
        }
        if ("npc".equalsIgnoreCase(args[0])) {
            if (!requireAdmin(sender)) return true;
            if (args.length != 4 || !"skin".equalsIgnoreCase(args[1])) {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Use /sbw npc skin <items|upgrades> <nome-do-jogador>.");
                return true;
            }
            try {
                plugin.getShopService().setSkin(args[2].toLowerCase(Locale.ROOT), args[3]);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Skin da loja " + args[2]
                    + " definida como " + args[3] + ".");
            } catch (IllegalArgumentException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            }
            return true;
        }
        if ("matchpoint".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("starfinbedwars.command.matchpoint")
                    && !sender.hasPermission("starfinbedwars.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Sem permissao para forcar Match Point.");
                return true;
            }
            if (args.length > 2) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Uso: /sbw matchpoint [id-da-arena].");
                return true;
            }
            GameMatch target = null;
            if (args.length == 2) {
                target = matches.getMatchForArena(args[1].toLowerCase(Locale.ROOT));
            } else if (sender instanceof Player) {
                Player player = (Player) sender;
                target = matches.getMatchForPlayer(player.getUniqueId());
                if (target == null) target = matches.getSpectatedMatch(player.getUniqueId());
            } else {
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "No console use /sbw matchpoint <id-da-arena>.");
                return true;
            }
            if (target == null || target.getState() != MatchState.IN_GAME) {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Nenhuma partida em andamento encontrada. Informe uma arena ativa.");
                return true;
            }
            MatchPointService service = plugin.getMatchPoint();
            if (service == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Sistema Match Point indisponivel.");
                return true;
            }
            MatchPointService.ForceResult result = service.forceMatchPoint(target);
            if (result == MatchPointService.ForceResult.ACTIVATED) {
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Match Point iniciado agora em "
                    + target.getArena().getId() + "! Todas as camas foram quebradas.");
                plugin.getLogger().info("Match Point FORCADO por " + sender.getName()
                    + " na arena " + target.getArena().getId()
                    + " partida " + target.getId());
            } else if (result == MatchPointService.ForceResult.ALREADY_ACTIVE) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "O Match Point ja esta ativo nesta partida.");
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Nao foi possivel forcar Match Point (" + result + ").");
            }
            return true;
        }
        if ("status".equalsIgnoreCase(args[0]) && args.length == 1) {
            if (!sender.hasPermission("starfinbedwars.command.status")
                    && !sender.hasPermission("starfinbedwars.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Voce nao tem permissao para consultar o status.");
                return true;
            }
            sendStatus(sender);
            return true;
        }
        if ("menu".equalsIgnoreCase(args[0]) && args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "O menu exige um jogador.");
                return true;
            }
            if (menus == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Menus indisponiveis.");
                return true;
            }
            menus.openPlayerMenu((Player) sender, 0);
            return true;
        }
        if ("create".equalsIgnoreCase(args[0]) && args.length >= 2
                && "teams".equalsIgnoreCase(args[1])) {
            if (!requireAdmin(sender)) return true;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador em edicao.");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Configure e confirme os limites da arena antes de /sbw setup mode e "
                    + "/sbw create teams <quantidade>.");
                return true;
            }
            if (wizard == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Assistente indisponivel.");
                return true;
            }
            Player player = (Player) sender;
            ArenaDefinition arena = editedArena(player);
            if (arena == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Entre na copia de edicao de uma arena primeiro.");
                return true;
            }
            try {
                wizard.begin(player, arena, Integer.parseInt(args[2]));
            } catch (RuntimeException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            } catch (java.io.IOException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao iniciar o assistente.", error);
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar o rascunho do assistente.");
            }
            return true;
        }
        if ("setup".equalsIgnoreCase(args[0])) {
            handleSetup(sender, args);
            return true;
        }
        if ("setarena".equalsIgnoreCase(args[0])) {
            handleSetArena(sender, args);
            return true;
        }
        if ("next".equalsIgnoreCase(args[0]) && args.length == 1) {
            if (!requireAdmin(sender)) return true;
            if (!(sender instanceof Player) || wizard == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "A proxima etapa exige um jogador no assistente.");
                return true;
            }
            wizard.sendNext((Player) sender);
            return true;
        }
        if ("arena".equalsIgnoreCase(args[0])) {
            handleArena(sender, args);
            return true;
        }

        if ("world".equalsIgnoreCase(args[0])) {
            handleWorld(sender, args);
            return true;
        }
        if ("join".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador.");
                return true;
            }
            try {
                String requested = args[1].toLowerCase(Locale.ROOT);
                if (requested.startsWith("group:")) requested = arenaForGroup(requested.substring(6));
                matches.join((Player) sender, requested);
            } catch (RuntimeException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            }
            return true;
        }
        if ("leave".equalsIgnoreCase(args[0]) && args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador.");
                return true;
            }
            if (matches.getMatchForPlayer(((Player) sender).getUniqueId()) == null
                    && !matches.isSpectating(((Player) sender).getUniqueId())) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Voce nao esta em uma partida.");
            } else {
                matches.leave((Player) sender);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Voce saiu da partida.");
            }
            return true;
        }
        if ("spectate".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Este comando exige um jogador.");
                return true;
            }
            try {
                matches.spectate((Player) sender, args[1].toLowerCase(Locale.ROOT));
            } catch (RuntimeException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            }
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.RED + "Subcomando invalido. Use /" + label + " help.");
        return true;
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (!(sender instanceof Player) || wizard == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Assistente indisponivel ou comando exige jogador.");
            return;
        }
        Player player = (Player) sender;
        try {
            if (args.length == 3 && "color".equalsIgnoreCase(args[1])) {
                wizard.selectTeamColor(player, args[2]);
            } else if (args.length == 3 && "mode".equalsIgnoreCase(args[1])) {
                ArenaDefinition arena = editedArena(player);
                if (arena == null) throw new IllegalStateException("Entre na copia de edicao de uma arena primeiro.");
                wizard.selectGameMode(player, arena, args[2]);
            } else if (args.length == 3 && "team-size".equalsIgnoreCase(args[1])) {
                ArenaDefinition arena = editedArena(player);
                if (arena == null) throw new IllegalStateException("Entre na copia de edicao de uma arena primeiro.");
                wizard.selectTeamSize(player, arena, Integer.parseInt(args[2]));
            } else if (args.length == 2 && "undo".equalsIgnoreCase(args[1])) {
                wizard.undo(player);
            } else if (args.length == 2 && "status".equalsIgnoreCase(args[1])) {
                wizard.sendStatus(player);
            } else if (args.length == 2 && "pause".equalsIgnoreCase(args[1])) {
                wizard.pause(player);
            } else if (args.length == 3 && "resume".equalsIgnoreCase(args[1])) {
                ArenaDefinition arena = getArena(sender, args[2]);
                if (arena != null) wizard.resume(player, arena);
            } else if (args.length == 3 && "cancel".equalsIgnoreCase(args[1])) {
                ArenaDefinition arena = getArena(sender, args[2]);
                if (arena != null) wizard.cancel(player, arena);
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Uso: /sbw setup mode <solo|equipe> | team-size <2..maximo> | status | undo | pause | resume <arena> | cancel <arena>.");
            }
        } catch (RuntimeException error) {
            sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
        } catch (java.io.IOException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao atualizar assistente.", error);
            sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar o rascunho.");
        }
    }

    private ArenaDefinition editedArena(Player player) {
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (editing.isEditWorld(arena, player.getWorld()) && editing.isEditOwner(player, arena)) return arena;
        }
        return null;
    }

    private void handleSetArena(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Delimitar uma arena exige um jogador.");
            return;
        }
        Player player = (Player) sender;
        ArenaDefinition arena = editedArena(player);
        if (arena == null) {
            sender.sendMessage(PREFIX + ChatColor.RED
                + "Entre na copia de edicao da arena antes de configurar os limites.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW
                + "Uso: /sbw setarena <center here|pos1|pos2|upoversize|upheight|uplow|tp|preview|confirm|cancel>");
            return;
        }
        BoundsDraft draft = boundsDraft(player, arena);
        String action = args[1].toLowerCase(Locale.ROOT);
        try {
            if (!"preview".equals(action)) clearBoundsPreview(player, draft);
            if ("center".equals(action) && args.length == 3 && "here".equalsIgnoreCase(args[2])) {
                Position position = editing.canonicalPosition(arena, player.getLocation());
                Position previousCenter = draft.center;
                int previousCenterX = draft.centerX;
                int previousCenterY = draft.centerY;
                int previousCenterZ = draft.centerZ;
                draft.centerX = floor(position.getX());
                draft.centerY = floor(position.getY());
                draft.centerZ = floor(position.getZ());
                draft.center = new Position(position.getWorld(), draft.centerX, draft.centerY, draft.centerZ,
                    position.getYaw(), position.getPitch());
                boolean previousManual = draft.manual;
                Position previousFirst = draft.first;
                Position previousSecond = draft.second;
                draft.manual = false;
                try {
                    updateAutomaticPoints(draft, arena);
                } catch (RuntimeException error) {
                    draft.center = previousCenter;
                    draft.centerX = previousCenterX;
                    draft.centerY = previousCenterY;
                    draft.centerZ = previousCenterZ;
                    draft.manual = previousManual;
                    draft.first = previousFirst;
                    draft.second = previousSecond;
                    throw error;
                }
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Centro pendente marcado em "
                    + draft.centerX + ", " + draft.centerY + ", " + draft.centerZ + ".");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "POS1 e POS2 calculados automaticamente: "
                    + formatPosition(draft.first) + " -> " + formatPosition(draft.second) + ".");
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Proximo comando: /sbw setarena preview; depois confirme com /sbw setarena confirm.");
            } else if ("upoversize".equals(action) && args.length == 3) {
                String[] dimensions = args[2].toLowerCase(Locale.ROOT).split("x", -1);
                if (dimensions.length != 2) {
                    throw new IllegalArgumentException("Use largura x comprimento, por exemplo 280x280.");
                }
                int width = Integer.parseInt(dimensions[0]);
                int depth = Integer.parseInt(dimensions[1]);
                int maximum = plugin.getSettings().getAutomaticBoundsMaximumDimension();
                if (width < 1 || width > maximum || depth < 1 || depth > maximum) {
                    throw new IllegalArgumentException("Cada dimensao deve estar entre 1 e " + maximum + ".");
                }
                int previousWidth = draft.width;
                int previousDepth = draft.depth;
                boolean previousManual = draft.manual;
                draft.width = width;
                draft.depth = depth;
                draft.manual = false;
                try {
                    updateAutomaticPoints(draft, arena);
                } catch (RuntimeException error) {
                    draft.width = previousWidth;
                    draft.depth = previousDepth;
                    draft.manual = previousManual;
                    throw error;
                }
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Dimensoes pendentes: "
                    + width + "x" + depth + ".");
                sendBoundsNextInstruction(sender, draft, "Revise os novos limites.");
            } else if ("upheight".equals(action) && args.length == 3) {
                int previous = draft.upHeight;
                boolean previousManual = draft.manual;
                draft.upHeight = parseHeight(args[2], "altura superior");
                draft.manual = false;
                try {
                    updateAutomaticPoints(draft, arena);
                } catch (RuntimeException error) {
                    draft.upHeight = previous;
                    draft.manual = previousManual;
                    throw error;
                }
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Altura superior pendente: " + draft.upHeight + ".");
                sendBoundsNextInstruction(sender, draft, "Revise os novos limites.");
            } else if ("uplow".equals(action) && args.length == 3) {
                int previous = draft.downDepth;
                boolean previousManual = draft.manual;
                draft.downDepth = parseHeight(args[2], "profundidade inferior");
                draft.manual = false;
                try {
                    updateAutomaticPoints(draft, arena);
                } catch (RuntimeException error) {
                    draft.downDepth = previous;
                    draft.manual = previousManual;
                    throw error;
                }
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Profundidade inferior pendente: "
                    + draft.downDepth + ".");
                sendBoundsNextInstruction(sender, draft, "Revise os novos limites.");
            } else if ("pos1".equals(action) && args.length == 2) {
                if (!draft.manual) {
                    draft.first = null;
                    draft.second = null;
                }
                draft.first = editing.canonicalPosition(arena, player.getLocation());
                draft.manual = true;
                sender.sendMessage(PREFIX + ChatColor.GREEN + "POS1 pendente marcado.");
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Proximo comando: va ao canto oposto e use /sbw setarena pos2.");
            } else if ("pos2".equals(action) && args.length == 2) {
                if (!draft.manual) {
                    draft.first = null;
                    draft.second = null;
                }
                draft.second = editing.canonicalPosition(arena, player.getLocation());
                draft.manual = true;
                sender.sendMessage(PREFIX + ChatColor.GREEN + "POS2 pendente marcado.");
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Proximo comando: /sbw setarena preview; depois confirme com /sbw setarena confirm.");
            } else if ("preview".equals(action) && args.length == 2) {
                showBoundsPreview(player, draft, arena);
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Preview virtual exibido apenas para voce; use /sbw setarena confirm para salvar.");
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Proxima etapa: /sbw setarena confirm.");
            } else if ("tp".equals(action) && args.length == 2) {
                int x;
                int y;
                int z;
                if (draft.manual) {
                    if (draft.first == null || draft.second == null) {
                        throw new IllegalStateException("Marque POS1 e POS2 antes do teleporte.");
                    }
                    x = floor((draft.first.getX() + draft.second.getX()) / 2.0D);
                    y = floor((draft.first.getY() + draft.second.getY()) / 2.0D);
                    z = floor((draft.first.getZ() + draft.second.getZ()) / 2.0D);
                } else {
                    AutomaticArenaBounds automatic = automaticBounds(draft, arena);
                    x = automatic.getCenterX();
                    y = automatic.getCenterY();
                    z = automatic.getCenterZ();
                }
                Location target = new Location(player.getWorld(), x + 0.5D,
                    Math.min(254.0D, y + 1.0D), z + 0.5D, player.getLocation().getYaw(),
                    player.getLocation().getPitch());
                if (!player.teleport(target)) throw new IllegalStateException("Teleporte recusado pelo servidor.");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Teleportado ao centro dos limites pendentes.");
                sendBoundsNextInstruction(sender, draft, "Confira a area e os marcadores.");
            } else if ("confirm".equals(action) && args.length == 2) {
                confirmBounds(player, draft, arena);
            } else if ("cancel".equals(action) && args.length == 2) {
                clearBoundsPreview(player, draft);
                boundsDrafts.remove(player.getUniqueId());
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Alteracoes pendentes descartadas.");
                sender.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Proximo comando: /sbw setarena center here ou marque /sbw setarena pos1 e pos2.");
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Uso: /sbw setarena <center here|pos1|pos2|upoversize <L>x<C>|upheight <n>|uplow <n>|tp|preview|confirm|cancel>");
                return;
            }
        } catch (RuntimeException error) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Limites nao atualizados: " + error.getMessage());
        } catch (java.io.IOException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Falha ao confirmar limites da arena " + arena.getId() + ".", error);
            sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao persistir os limites; alteracao revertida.");
        }
    }

    private int parseHeight(String value, String name) {
        int height = Integer.parseInt(value);
        if (height < 0 || height > 255) throw new IllegalArgumentException(name + " deve estar entre 0 e 255.");
        return height;
    }

    private BoundsDraft boundsDraft(Player player, ArenaDefinition arena) {
        BoundsDraft existing = boundsDrafts.get(player.getUniqueId());
        if (existing != null && existing.arenaId.equals(arena.getId())) return existing;
        if (existing != null) clearBoundsPreview(player, existing);
        BoundsDraft draft = new BoundsDraft(arena.getId(),
            plugin.getSettings().getAutomaticBoundsWidth(),
            plugin.getSettings().getAutomaticBoundsDepth(),
            plugin.getSettings().getAutomaticBoundsUpHeight(),
            plugin.getSettings().getAutomaticBoundsDownDepth());
        if (arena.getBoundsMode() == ArenaBoundsMode.AUTOMATIC && arena.getBoundsCenter() != null) {
            draft.center = arena.getBoundsCenter();
            draft.centerX = floor(draft.center.getX());
            draft.centerY = floor(draft.center.getY());
            draft.centerZ = floor(draft.center.getZ());
            draft.width = arena.getBoundsWidth();
            draft.depth = arena.getBoundsDepth();
            draft.upHeight = arena.getBoundsUpHeight();
            draft.downDepth = arena.getBoundsDownDepth();
        }
        boundsDrafts.put(player.getUniqueId(), draft);
        return draft;
    }

    private AutomaticArenaBounds automaticBounds(BoundsDraft draft, ArenaDefinition arena) {
        if (draft.center == null || draft.manual) {
            throw new IllegalStateException("Marque um centro com /sbw setarena center here.");
        }
        AutomaticArenaBounds bounds = new AutomaticArenaBounds(arena.getSourceWorld(),
            draft.centerX, draft.centerY, draft.centerZ, draft.width, draft.depth,
            draft.upHeight, draft.downDepth,
            plugin.getSettings().getAutomaticBoundsMaximumDimension());
        if (bounds.getVolume() > arenas.getMaximumRegionBlocks()) {
            throw new IllegalArgumentException("O volume excede o limite configurado de "
                + arenas.getMaximumRegionBlocks() + " blocos.");
        }
        return bounds;
    }

    private void updateAutomaticPoints(BoundsDraft draft, ArenaDefinition arena) {
        if (draft.center == null || draft.manual) return;
        AutomaticArenaBounds bounds = automaticBounds(draft, arena);
        draft.first = bounds.getPos1();
        draft.second = bounds.getPos2();
    }

    private void showBoundsPreview(Player player, BoundsDraft draft, ArenaDefinition arena) {
        clearBoundsPreview(player, draft);
        ArenaRegion region;
        int centerX;
        int centerY;
        int centerZ;
        if (draft.manual) {
            if (draft.first == null || draft.second == null) {
                throw new IllegalStateException("Marque POS1 e POS2 antes do preview.");
            }
            region = new ArenaRegion(arena.getSourceWorld(),
                floor(draft.first.getX()), floor(draft.first.getY()), floor(draft.first.getZ()),
                floor(draft.second.getX()), floor(draft.second.getY()), floor(draft.second.getZ()));
            centerX = (region.getMinimumX() + region.getMaximumX()) / 2;
            centerY = (region.getMinimumY() + region.getMaximumY()) / 2;
            centerZ = (region.getMinimumZ() + region.getMaximumZ()) / 2;
        } else {
            AutomaticArenaBounds automatic = automaticBounds(draft, arena);
            region = automatic.getRegion();
            centerX = automatic.getCenterX();
            centerY = automatic.getCenterY();
            centerZ = automatic.getCenterZ();
        }
        showVirtualBlock(player, draft, centerX, centerY, centerZ, Material.BEACON);
        Position pos1 = new Position(region.getWorld(), region.getMinimumX(), region.getMinimumY(),
            region.getMinimumZ(), 0.0F, 0.0F);
        Position pos2 = new Position(region.getWorld(), region.getMaximumX(), region.getMaximumY(),
            region.getMaximumZ(), 0.0F, 0.0F);
        showVirtualBlock(player, draft, floor(pos1.getX()), floor(pos1.getY()), floor(pos1.getZ()),
            Material.STAINED_GLASS, (byte) 11);
        showVirtualBlock(player, draft, floor(pos2.getX()), floor(pos2.getY()), floor(pos2.getZ()),
            Material.STAINED_GLASS, (byte) 14);
        player.sendMessage(PREFIX + ChatColor.BLUE + "POS1 (inferior, azul): " + formatPosition(pos1));
        player.sendMessage(PREFIX + ChatColor.RED + "POS2 (superior, vermelho): " + formatPosition(pos2));
    }

    private void showVirtualBlock(Player player, BoundsDraft draft, int x, int y, int z, Material material) {
        showVirtualBlock(player, draft, x, y, z, material, (byte) 0);
    }

    private void showVirtualBlock(Player player, BoundsDraft draft, int x, int y, int z,
                                  Material material, byte data) {
        Location location = new Location(player.getWorld(), x, y, z);
        Block block = location.getBlock();
        draft.markers.add(location);
        player.sendBlockChange(location, material, data);
    }

    private String formatPosition(Position position) {
        return floor(position.getX()) + ", " + floor(position.getY()) + ", " + floor(position.getZ());
    }

    private void clearBoundsPreview(Player player, BoundsDraft draft) {
        for (Location location : draft.markers) {
            Block block = location.getBlock();
            player.sendBlockChange(location, block.getType(), block.getData());
        }
        draft.markers.clear();
    }

    private void confirmBounds(Player player, BoundsDraft draft, ArenaDefinition arena) throws java.io.IOException {
        ArenaRegion main;
        ArenaRegion protection;
        Position center;
        ArenaBoundsMode mode;
        int width = 0;
        int depth = 0;
        int up = 0;
        int down = 0;
        int ceiling;
        int voidHeight;
        if (draft.manual) {
            if (draft.first == null || draft.second == null) {
                throw new IllegalStateException("Marque POS1 e POS2 antes de confirmar.");
            }
            main = new ArenaRegion(arena.getSourceWorld(),
                floor(draft.first.getX()), floor(draft.first.getY()), floor(draft.first.getZ()),
                floor(draft.second.getX()), floor(draft.second.getY()), floor(draft.second.getZ()));
            protection = new ArenaRegion(arena.getSourceWorld(), main.getMinimumX(), 0,
                main.getMinimumZ(), main.getMaximumX(), 255, main.getMaximumZ());
            center = null;
            mode = ArenaBoundsMode.MANUAL;
            ceiling = arena.getCeilingHeight();
            voidHeight = arena.getVoidHeight();
        } else {
            AutomaticArenaBounds automatic = automaticBounds(draft, arena);
            main = automatic.getRegion();
            protection = new ArenaRegion(arena.getSourceWorld(), main.getMinimumX(), 0,
                main.getMinimumZ(), main.getMaximumX(), 255, main.getMaximumZ());
            center = draft.center;
            mode = ArenaBoundsMode.AUTOMATIC;
            width = draft.width;
            depth = draft.depth;
            up = draft.upHeight;
            down = draft.downDepth;
            ceiling = main.getMaximumY();
            voidHeight = main.getMinimumY();
        }
        arenas.configureBounds(arena, main, protection, center, mode, width, depth, up, down,
            ceiling, voidHeight);
        clearBoundsPreview(player, draft);
        boundsDrafts.remove(player.getUniqueId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Limites confirmados e salvos atomicamente.");
        sendNextSetupInstruction(player, arena);
    }

    private void sendBoundsNextInstruction(CommandSender sender, BoundsDraft draft, String detail) {
        String next;
        if (draft.manual && draft.first == null) {
            next = "/sbw setarena pos1";
        } else if (draft.manual && draft.second == null) {
            next = "/sbw setarena pos2";
        } else if (!draft.manual && draft.center == null) {
            next = "/sbw setarena center here";
        } else {
            next = "/sbw setarena preview; depois /sbw setarena confirm";
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + detail + " Proximo comando: " + next + ".");
    }

    @EventHandler
    public void onBoundsPreviewQuit(PlayerQuitEvent event) {
        BoundsDraft draft = boundsDrafts.get(event.getPlayer().getUniqueId());
        if (draft != null) clearBoundsPreview(event.getPlayer(), draft);
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    private static final class BoundsDraft {
        private final String arenaId;
        private final List<Location> markers = new ArrayList<Location>();
        private int centerX;
        private int centerY;
        private int centerZ;
        private int width;
        private int depth;
        private int upHeight;
        private int downDepth;
        private Position center;
        private Position first;
        private Position second;
        private boolean manual;

        private BoundsDraft(String arenaId, int width, int depth, int upHeight, int downDepth) {
            this.arenaId = arenaId;
            this.width = width;
            this.depth = depth;
            this.upHeight = upHeight;
            this.downDepth = downDepth;
        }
    }

    private void handleArena(final CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender, "sbw");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("rule".equals(action) && args.length == 5) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                ArenaGameplayRules old = arena.getGameplayRules();
                String key = args[3].toLowerCase(Locale.ROOT);
                String value = args[4].toLowerCase(Locale.ROOT);
                Boolean split = old.generatorSplit, afk = old.antiAfk;
                Integer respawn = old.respawnSeconds;
                Set<String> kits = old.kits, abilities = old.abilities;
                if ("split".equals(key)) split = parseInheritedBoolean(value);
                else if ("afk".equals(key)) afk = parseInheritedBoolean(value);
                else if ("respawn".equals(key)) respawn = "inherit".equals(value)
                    ? null : Integer.valueOf(value);
                else if ("kits".equals(key)) kits = parseChoiceList(value,
                    plugin.getGameplay().getSettings().kits.keySet(), true);
                else if ("abilities".equals(key)) abilities = parseChoiceList(value,
                    plugin.getGameplay().getSettings().abilities.keySet(), false);
                else throw new IllegalArgumentException("Regra: split, afk, respawn, kits ou abilities.");
                arenas.setGameplayRules(arena,
                    new ArenaGameplayRules(split, afk, respawn, kits, abilities));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Regra salva para " + arena.getId()
                    + "; novas partidas usarao o valor atualizado.");
            } catch (Exception invalid) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Regra nao salva: " + invalid.getMessage());
            }
            return;
        }
        if ("menu".equals(action) && args.length == 2) {
            if (!requireAdmin(sender)) return;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "O menu administrativo exige um jogador.");
                return;
            }
            if (menus == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Menus indisponiveis.");
                return;
            }
            menus.openAdminMenu((Player) sender, 0);
            return;
        }
        if ("list".equals(action) && args.length == 2) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Arenas: "
                + (arenas.getArenas().isEmpty() ? "nenhuma" : names()));
            return;
        }
        if ("group".equals(action) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                arenas.setGroup(arena, args[3].toLowerCase(Locale.ROOT));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Arena " + arena.getId()
                    + " adicionada ao grupo '" + arena.getGroup() + "'.");
            } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao alterar grupo: " + error.getMessage());
            }
            return;
        }
        if (("import".equals(action) || "create".equals(action)) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            final boolean startEditing = "create".equals(action);
            try {
                arenas.createFromWorld(args[2].toLowerCase(Locale.ROOT), args[3], new ArenaService.ResultCallback() {
                    @Override
                    public void complete(ArenaDefinition arena, Exception error) {
                        if (error != null) {
                            sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao "
                                + (startEditing ? "criar" : "importar") + ": " + error.getMessage());
                            return;
                        }
                        if (startEditing && sender instanceof Player) {
                            final Player player = (Player) sender;
                            if (matches.getMatchForPlayer(player.getUniqueId()) != null
                                    || matches.isSpectating(player.getUniqueId())) {
                                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Arena criada. Saia da partida "
                                    + "e use /sbw arena edit " + arena.getId() + ".");
                                return;
                            }
                            try {
                                editing.start(player, arena, new ArenaEditingService.Completion() {
                                    @Override
                                    public void complete(Exception editError) {
                                        if (editError != null) {
                                            sender.sendMessage(PREFIX + ChatColor.RED
                                                + "Arena criada, mas a sessao de edicao nao iniciou: "
                                                + editError.getMessage());
                                        } else {
                                            sender.sendMessage(PREFIX + ChatColor.GREEN
                                                + "Arena criada; voce foi enviado para a copia de edicao.");
                                            sendNextSetupInstruction(player, arena);
                                        }
                                    }
                                });
                            } catch (RuntimeException editError) {
                                sender.sendMessage(PREFIX + ChatColor.RED
                                    + "Arena criada, mas a sessao de edicao nao iniciou: "
                                    + editError.getMessage());
                            }
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Arena " + arena.getId()
                                + (startEditing ? " criada." : " importada.")
                                + " Configure pontos/equipes e valide antes de habilitar.");
                        }
                    }
                });
                sender.sendMessage(PREFIX + ChatColor.YELLOW + (startEditing
                    ? "Criacao iniciada; voce sera teleportado quando a copia estiver pronta."
                    : "Importacao iniciada em segundo plano."));
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            }
            return;
        }
        if ("loadsource".equals(action) && args.length == 3) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                if (arenas.loadSourceWorld(arena) == null) {
                    throw new IllegalStateException("Bukkit nao conseguiu carregar o mundo-fonte.");
                }
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Mundo-fonte carregado para configurar pontos.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao carregar mundo-fonte: " + error.getMessage());
            }
            return;
        }
        if ("tp".equals(action) && args.length == 3) {
            if (!requireAdmin(sender)) return;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Teleporte exige um jogador.");
                return;
            }
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            Player player = (Player) sender;
            if (matches.getMatchForPlayer(player.getUniqueId()) != null
                    || matches.isSpectating(player.getUniqueId())) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Saia da partida antes de visitar uma arena.");
                return;
            }
            try {
                World source = arenas.loadSourceWorld(arena);
                if (source == null) throw new IllegalStateException("Bukkit nao carregou o mundo-fonte.");
                Position preferred = arena.getEditSpawn() != null ? arena.getEditSpawn() : arena.getLobby();
                Location target;
                WorldManagementService.WorldType type = worlds.getType(arena.getSourceWorld());
                if (preferred == null && (type == WorldManagementService.WorldType.VOID_EMPTY
                        || type == WorldManagementService.WorldType.VOID_PLATFORM)) {
                    target = worlds.getSafeLocation(source, type);
                } else {
                    if (preferred == null) preferred = arenas.position(source.getSpawnLocation());
                    target = arenas.toLocation(preferred, source);
                    if (type != WorldManagementService.WorldType.VOID_EMPTY
                            && type != WorldManagementService.WorldType.VOID_PLATFORM) {
                        target = SafeLocationResolver.findSurface(target, 8);
                        if (target == null) {
                            throw new IllegalStateException("Nao foi encontrado piso seguro perto do ponto configurado.");
                        }
                    } else if (!SafeLocationResolver.hasSafeFloor(target)) {
                        throw new IllegalStateException("Configure editspawn sobre uma plataforma segura.");
                    }
                }
                if (!player.teleport(target)) throw new IllegalStateException("Teleporte recusado pelo servidor.");
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Teleportado ao mundo-fonte da arena "
                    + arena.getId() + ".");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha no teleporte: " + error.getMessage());
            }
            return;
        }
        if ("exit".equals(action) && args.length == 2) {
            if (!requireAdmin(sender)) return;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Sair da edicao exige um jogador.");
                return;
            }
            try {
                editing.exit((Player) sender);
                regionSelection.clearSelection((Player) sender);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Sessoes de edicao encerradas; alteracoes nao publicadas descartadas.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao sair da edicao: " + error.getMessage());
            }
            return;
        }
        if ("edit".equals(action)) {
            handleEdit(sender, args);
            return;
        }
        if ("region".equals(action)) {
            handleRegion(sender, args);
            return;
        }
        if ("team".equals(action) && args.length == 7) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                arenas.configureTeam(arena, args[3], args[4], args[5], Integer.parseInt(args[6]));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuracao da equipe salva.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao configurar equipe: " + error.getMessage());
            }
            return;
        }
        if ("removeteam".equals(action) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                arenas.removeTeam(arena, args[3].toLowerCase(Locale.ROOT));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Equipe removida.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao remover equipe: " + error.getMessage());
            }
            return;
        }
        if ("set".equals(action) && (args.length == 4 || args.length == 5)) {
            if (!requireAdmin(sender)) return;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Definir localizacoes exige um jogador.");
                return;
            }
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            Player player = (Player) sender;
            String selector = args[3].toLowerCase(Locale.ROOT);
            String teamId = args.length == 5 ? args[4].toLowerCase(Locale.ROOT) : null;
            if (("lobby".equals(selector) || "spectator".equals(selector)
                    || "editspawn".equals(selector)) != (args.length == 4)) {
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "lobby/spectator/editspawn nao usam equipe; spawn/respawn/bed exigem equipe.");
                return;
            }
            if (!editing.isEditWorld(arena, player.getWorld())) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Inicie o modo de edicao isolado: /sbw arena edit start "
                    + arena.getId() + ".");
                return;
            }
            try {
                if ("respawn".equals(selector)
                        && !SafeLocationResolver.hasSafeFloor(player.getLocation())) {
                    throw new IllegalArgumentException("Respawn exige piso solido e dois blocos livres acima.");
                }
                Position position = "bed".equals(selector)
                    ? canonicalBedPosition(arena, player) : editing.canonicalPosition(arena, player.getLocation());
                if ("spawn".equals(selector) || "respawn".equals(selector)) {
                    position = new Position(position.getWorld(), position.getX(), position.getY(),
                        position.getZ(), position.getYaw(), 0.0F);
                }
                if ("editspawn".equals(selector)) arenas.setEditSpawn(arena, position);
                else arenas.setPosition(arena, selector, teamId, position);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Localizacao " + selector + " salva.");
                if ("lobby".equals(selector)) {
                    sendNextSetupInstruction(player, arena);
                }
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar localizacao: " + error.getMessage());
            }
            return;
        }
        if ("min".equals(action) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                arenas.setMinimumPlayers(arena, Integer.parseInt(args[3]));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Minimo de jogadores atualizado.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar minimo: " + error.getMessage());
            }
            return;
        }
        if ("countdown".equals(action) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                arenas.setCountdown(arena, Integer.parseInt(args[3]));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Countdown atualizado.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar countdown: " + error.getMessage());
            }
            return;
        }
        if ("generator".equals(action)) {
            handleGenerator(sender, args);
            return;
        }
        if ("shop".equals(action) && args.length == 4) {
            if (!requireAdmin(sender)) return;
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Definir lojas exige um jogador.");
                return;
            }
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            String shop = args[3].toLowerCase(Locale.ROOT);
            if (!"items".equals(shop) && !"upgrades".equals(shop)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Loja deve ser items ou upgrades.");
                return;
            }
            Player player = (Player) sender;
            if (!editing.isEditWorld(arena, player.getWorld())) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Inicie o modo de edicao isolado: /sbw arena edit start "
                    + arena.getId() + ".");
                return;
            }
            try {
                arenas.setShopPosition(arena, "upgrades".equals(shop),
                    editing.canonicalPosition(arena, player.getLocation()));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Local da loja " + shop + " salvo.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao salvar loja: " + error.getMessage());
            }
            return;
        }
        if (("enable".equals(action) || "disable".equals(action) || "validate".equals(action)
                || "validar".equals(action) || "validade".equals(action)) && args.length == 3) {
            if (!requireAdmin(sender)) return;
            ArenaDefinition arena = getArena(sender, args[2]);
            if (arena == null) return;
            try {
                if ("validate".equals(action) || "validar".equals(action) || "validade".equals(action)) {
                    List<String> errors = arenas.validate(arena);
                    sender.sendMessage(PREFIX + (errors.isEmpty() ? ChatColor.GREEN + "Arena valida."
                        : ChatColor.RED + "Arena invalida: " + arenas.join(errors)));
                } else if ("enable".equals(action) && arenas.isEditing(arena.getId())) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "A arena esta em edicao. Execute "
                        + "/sbw arena validate " + arena.getId() + " e /sbw arena edit publish "
                        + arena.getId() + "; depois use /sbw arena enable " + arena.getId() + ".");
                } else {
                    arenas.setEnabled(arena, "enable".equals(action));
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Arena "
                        + ("enable".equals(action) ? "habilitada." : "desabilitada."));
                }
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha na operacao: " + error.getMessage());
            }
            return;
        }
            sender.sendMessage(PREFIX + ChatColor.RED
            + "Uso: /sbw arena list|group|menu|create|import|tp|exit|team|removeteam|edit|region|set|min|countdown|generator|shop|validate|enable|disable");
    }

    private void handleEdit(final CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Uso: /sbw arena edit <arena> ou "
                + "/sbw arena edit <publish|cancel> <arena>");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "O modo de edicao exige um jogador.");
            return;
        }
        final Player player = (Player) sender;
        final String action = args.length == 3 ? "start" : args[2].toLowerCase(Locale.ROOT);
        final ArenaDefinition arena = getArena(sender, args.length == 3 ? args[2] : args[3]);
        if (arena == null) return;
        try {
            if ("start".equals(action)) {
                if (matches.getMatchForPlayer(player.getUniqueId()) != null
                        || matches.isSpectating(player.getUniqueId())) {
                    throw new IllegalStateException("Saia da partida antes de iniciar a edicao.");
                }
                editing.start(player, arena, new ArenaEditingService.Completion() {
                    @Override
                    public void complete(Exception error) {
                        if (error != null) {
                            sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao iniciar edicao: "
                                + error.getMessage());
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Copia isolada carregada. Edite este mundo; "
                                + "publicar substituira o template da arena.");
                            sendNextSetupInstruction(player, arena);
                        }
                    }
                });
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Preparando copia isolada da arena...");
            } else if ("publish".equals(action)) {
                if (wizard != null) wizard.clearEditorHolograms(arena.getId());
                editing.publish(player, arena, new ArenaEditingService.Completion() {
                    @Override
                    public void complete(Exception error) {
                        if (error == null) regionSelection.clearSelection(player);
                        sender.sendMessage(PREFIX + (error == null ? ChatColor.GREEN + "Template publicado."
                            : ChatColor.RED + "Publicacao falhou e o template anterior foi preservado: "
                                + error.getMessage()));
                    }
                });
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Publicacao iniciada.");
            } else if ("cancel".equals(action)) {
                editing.cancel(player, arena);
                regionSelection.clearSelection(player);
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Edicao cancelada; alteracoes descartadas.");
            } else {
                sender.sendMessage(PREFIX + ChatColor.RED + "Acao deve ser start, publish ou cancel.");
            }

        } catch (Exception error) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Falha na edicao: " + error.getMessage());
        }
    }

    private void sendNextSetupInstruction(Player player, ArenaDefinition arena) {
        if (wizard == null) {
            player.sendMessage(PREFIX + ChatColor.YELLOW
                + "Proximo passo: configure os limites com /sbw setarena center here e confirme "
                + "com /sbw setarena confirm.");
            return;
        }
        try {
            wizard.continueSetup(player, arena);
        } catch (java.io.IOException | RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Nao foi possivel exibir a proxima etapa do setup da arena " + arena.getId() + ".", error);
            player.sendMessage(PREFIX + ChatColor.RED
                + "Copia de edicao iniciada, mas nao foi possivel carregar a instrucao. "
                + "Use /sbw setup status para consultar a proxima etapa.");
        }
    }

    private void handleRegion(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length != 5) {
            sender.sendMessage(PREFIX + ChatColor.RED
                + "Uso: /sbw arena region <arena> <main|protection|waiting> <wand|worldedit|clear>");
            return;
        }
        ArenaDefinition arena = getArena(sender, args[2]);
        if (arena == null) return;
        ArenaRegionType type;
        try {
            type = ArenaRegionType.parse(args[3]);
        } catch (IllegalArgumentException error) {
            sender.sendMessage(PREFIX + ChatColor.RED + error.getMessage());
            return;
        }
        String method = args[4].toLowerCase(Locale.ROOT);
        try {
            if ("clear".equals(method)) {
                arenas.clearRegion(arena, type);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Regiao removida.");
                return;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Selecionar regioes exige um jogador.");
                return;
            }
            Player player = (Player) sender;
            if (!editing.isEditWorld(arena, player.getWorld())) {
                throw new IllegalStateException("Inicie o modo de edicao isolado antes de selecionar.");
            }
            if ("wand".equals(method)) {
                regionSelection.giveWand(player, arena, type);
                return;
            }
            if ("worldedit".equals(method)) {
                ArenaRegion region = worldEdit.getSelection(player, player.getWorld().getName());
                regionSelection.setRegion(arena, type, region.getMinimumX(), region.getMinimumY(),
                    region.getMinimumZ(), region.getMaximumX(), region.getMaximumY(), region.getMaximumZ());
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Regiao selecionada no WorldEdit e salva.");
                return;
            }
            sender.sendMessage(PREFIX + ChatColor.RED + "Metodo deve ser wand, worldedit ou clear.");
        } catch (Exception error) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao configurar regiao: " + error.getMessage());
        }
    }

    private void handleGenerator(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 4) {
            sender.sendMessage(PREFIX + ChatColor.RED
                + "Uso: /sbw arena generator <add|remove|list|setteam> <arena> ...");
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        ArenaDefinition arena = getArena(sender, args[3]);
        if (arena == null) return;
        if ("list".equals(operation) && args.length == 4) {
            if (arena.getGeneratorList().isEmpty()) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Arena sem geradores configurados.");
            } else {
                for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
                    sender.sendMessage(PREFIX + ChatColor.GRAY + generator.getId() + ": "
                        + generator.getResource() + ", equipe="
                        + (generator.getTeamId() == null ? "nenhuma" : generator.getTeamId())
                        + ", nivel=" + generator.getStartingLevel());
                }
            }
            return;
        }
        if ("remove".equals(operation) && args.length == 5) {
            try {
                arenas.removeGenerator(arena, args[4].toLowerCase(Locale.ROOT));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Gerador removido.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao remover gerador: " + error.getMessage());
            }
            return;
        }
        if ("setteam".equals(operation) && args.length == 6) {
            try {
                arenas.setGeneratorTeam(arena, args[4].toLowerCase(Locale.ROOT),
                    args[5].toLowerCase(Locale.ROOT));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Equipe do gerador atualizada.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao atualizar equipe: " + error.getMessage());
            }
            return;
        }
        if ("add".equals(operation) && (args.length == 5 || args.length == 6)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Adicionar gerador exige um jogador.");
                return;
            }
            Player player = (Player) sender;
            if (!editing.isEditWorld(arena, player.getWorld())) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Inicie o modo de edicao isolado: /sbw arena edit start "
                    + arena.getId() + ".");
                return;
            }
            try {
                ResourceType resource = ResourceType.valueOf(args[4].toUpperCase(Locale.ROOT));
                String teamId = args.length == 6 ? args[5].toLowerCase(Locale.ROOT) : null;
                ResourceGeneratorDefinition generator = arenas.addGenerator(
                    arena, resource, teamId, editing.canonicalPosition(arena, player.getLocation()));
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Gerador " + generator.getId() + " salvo.");
            } catch (Exception error) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao adicionar gerador: " + error.getMessage());
            }
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.RED
            + "Uso: /sbw arena generator add <arena> <iron|gold|diamond|emerald> [equipe]");
    }

    private void handleWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED
                        + "Uso: /sbw world <list|create|import|info|tp|delete|confirm-delete> ...");
                    return;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("list".equals(action) && args.length == 2) {
                    if (!requireWorldAdmin(sender)) return;
                    if (worlds.getManagedWorlds().isEmpty()) {
                        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Nenhum mundo gerenciado/importado.");
                        return;
                    }
                    for (java.util.Map.Entry<String, WorldManagementService.WorldType> entry
                            : worlds.getManagedWorlds().entrySet()) {
                        sender.sendMessage(PREFIX + ChatColor.GRAY + entry.getKey() + " - " + entry.getValue());
                    }
                    return;
                }
                if ("create".equals(action) && args.length == 4) {
                    if (!requireWorldAdmin(sender)) return;
                    try {
                        WorldManagementService.WorldType type;
                        if ("empty".equalsIgnoreCase(args[3])) {
                            type = WorldManagementService.WorldType.VOID_EMPTY;
                        } else if ("platform".equalsIgnoreCase(args[3])) {
                            type = WorldManagementService.WorldType.VOID_PLATFORM;
                        } else {
                            throw new IllegalArgumentException("Tipo deve ser empty ou platform.");
                        }
                        worlds.createVoidWorld(args[2], type);
                        sender.sendMessage(PREFIX + ChatColor.GREEN + "Mundo " + args[2] + " criado como " + type + ".");
                    } catch (Exception error) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao criar mundo: " + error.getMessage());
                    }
                    return;
                }
                if ("import".equals(action) && args.length == 3) {
                    if (!requireWorldAdmin(sender)) return;
                    try {
                        worlds.importWorld(args[2]);
                        sender.sendMessage(PREFIX + ChatColor.GREEN + "Mundo externo registrado sem alteracao ou permissao de exclusao.");
                    } catch (Exception error) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao importar mundo: " + error.getMessage());
                    }
                    return;
                }
                if ("info".equals(action) && args.length == 3) {
                    if (!requireWorldAdmin(sender)) return;
                    WorldManagementService.WorldType type = worlds.getType(args[2]);
                    if (type == null) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Mundo nao registrado.");
                        return;
                    }
                    World loaded = plugin.getServer().getWorld(args[2]);
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + args[2] + ": " + type
                        + ", carregado=" + (loaded != null));
                    return;
                }
                if ("tp".equals(action) && args.length == 3) {
                    if (!requireWorldAdmin(sender)) return;
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Teleportar para um mundo exige um jogador.");
                        return;
                    }
                    try {
                        worlds.teleport((Player) sender, args[2]);
                        sender.sendMessage(PREFIX + ChatColor.GREEN + "Teleportado para " + args[2] + ".");
                    } catch (Exception error) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao teleportar: " + error.getMessage());
                    }
                    return;
                }
                if (("delete".equals(action) || "confirm-delete".equals(action)) && args.length == 3) {
                    if (!requireWorldAdmin(sender)) return;
                    try {
                        boolean deleted = worlds.confirmDeletion(
                            new WorldManagementService.CommandSenderIdentity(sender.getName()), args[2],
                            "confirm-delete".equals(action));
                        if (deleted) {
                            sender.sendMessage(PREFIX + ChatColor.GREEN + "Mundo gerenciado excluido.");
                        } else {
                            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Para confirmar em ate 30 segundos, use "
                                + "/sbw world confirm-delete " + args[2] + ".");
                        }
                    } catch (Exception error) {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Falha ao excluir mundo: " + error.getMessage());
                    }
                    return;
                }
                sender.sendMessage(PREFIX + ChatColor.RED
                    + "Uso: /sbw world <list|create|import|info|tp|delete|confirm-delete> ...");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("starfinbedwars.admin")
                || sender.hasPermission("starfinbedwars.command.arena")) return true;
        sender.sendMessage(PREFIX + ChatColor.RED + "Voce nao tem permissao administrativa.");
        return false;
    }

    private boolean requireWorldAdmin(CommandSender sender) {
        if (sender.hasPermission("starfinbedwars.admin")
                || sender.hasPermission("starfinbedwars.command.world")) return true;
        sender.sendMessage(PREFIX + ChatColor.RED + "Voce nao tem permissao para gerenciar mundos.");
        return false;
    }

    private ArenaDefinition getArena(CommandSender sender, String id) {
        ArenaDefinition arena = arenas.getArena(id);
        if (arena == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Arena nao encontrada: " + id
                + ". Use /sbw arena list e informe o ID exato (sem < >).");
        }
        return arena;
    }

    private Position canonicalBedPosition(ArenaDefinition arena, Player player) {
        Position position = arenas.bedPosition(player, player.getWorld());
        return new Position(arena.getSourceWorld(), position.getX(), position.getY(), position.getZ(),
            position.getYaw(), position.getPitch());
    }

    private String names() {
        List<String> values = new ArrayList<String>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            values.add(arena.getId() + (arena.isEnabled() ? " (ativa)" : " (desativada)"));
        }
        return join(values);
    }

    private void handleGameplay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starfinbedwars.command.gameplay.admin")
                && !sender.hasPermission("starfinbedwars.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Sem permissao."); return;
        }
        if (args.length == 2 && "reload".equalsIgnoreCase(args[1])) {
            try {
                plugin.getGameplay().reload();
                com.starfinplugins.bedwars.gameplay.GameplaySettings settings =
                    plugin.getGameplay().getSettings();
                arenas.setGameplayLimits(settings.maximumTeamSize, settings.maximumTotalPlayers);
                sender.sendMessage(PREFIX + ChatColor.GREEN
                    + "gameplay.yml e abilities.yml validados e recarregados.");
            } catch (RuntimeException invalid) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Configuracao mantida: " + invalid.getMessage());
            }
            return;
        }
        if (args.length == 2 && "diagnose".equalsIgnoreCase(args[1])) {
            com.starfinplugins.bedwars.gameplay.GameplaySettings config = plugin.getGameplay().getSettings();
            sender.sendMessage(PREFIX + "Kits=" + config.kits.size() + ", habilidades evolutivas="
                + plugin.getGameplay().getAbilityCatalog().abilities.size()
                + ", habilidades legadas=" + config.abilities.size() + ", Split=" + config.splitEnabled
                + ", anti-AFK=" + config.antiAfkEnabled + ".");
            for (ArenaDefinition arena : arenas.getArenas()) {
                ArenaGameplayRules rules = arena.getGameplayRules();
                if (rules.kits != null) for (String id : rules.kits)
                    if (!config.kits.containsKey(id)) sender.sendMessage(ChatColor.RED
                        + arena.getId() + ": kit desconhecido " + id);
                if (rules.abilities != null) for (String id : rules.abilities)
                    if (!config.abilities.containsKey(id)
                            && !plugin.getGameplay().getAbilityCatalog().abilities.containsKey(id))
                        sender.sendMessage(ChatColor.RED
                        + arena.getId() + ": habilidade desconhecida " + id);
            }
            return;
        }
        if (args.length == 3 && ("inspect".equalsIgnoreCase(args[1])
                || "cooldowns".equalsIgnoreCase(args[1]))) {
            Player target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Jogador offline."); return; }
            if ("inspect".equalsIgnoreCase(args[1])) sender.sendMessage(PREFIX + target.getName()
                + ": kit=" + plugin.getGameplay().selectedKit(target.getUniqueId())
                + ", habilidade=" + plugin.getGameplay().selectedAbility(target.getUniqueId()));
            else sender.sendMessage(PREFIX + target.getName() + ": cooldown restante="
                + plugin.getGameplay().cooldownRemaining(target.getUniqueId()) + "s.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW
            + "Use /sbw gameplay <reload|diagnose|inspect <jogador>|cooldowns <jogador>>.");
    }

    private void handleAlways(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starfinbedwars.command.always")
                && !sender.hasPermission("starfinbedwars.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Sem permissao.");
            return;
        }
        if (args.length == 2 && "reload".equalsIgnoreCase(args[1])) {
            try {
                plugin.getAlways().reload();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "always.yml validado e recarregado.");
            } catch (IOException invalid) {
                sender.sendMessage(PREFIX + ChatColor.RED + invalid.getMessage());
            }
            return;
        }
        if (args.length >= 2 && args.length <= 3 && "status".equalsIgnoreCase(args[1])) {
            if (args.length == 2 && sender instanceof Player) {
                sender.sendMessage(PREFIX + plugin.getAlways().describe(((Player) sender).getWorld()));
            } else if (args.length == 3 && args[2].toLowerCase(Locale.ROOT).startsWith("arena:")) {
                sender.sendMessage(PREFIX + plugin.getAlways().describeArena(args[2].substring(6)));
            } else if (args.length == 3) {
                World world = plugin.getServer().getWorld(args[2]);
                sender.sendMessage(PREFIX + plugin.getAlways().describe(world));
            } else sender.sendMessage(PREFIX + ChatColor.YELLOW
                + "Use /sbw always status <mundo|arena:id> no console.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Use /sbw always <status [mundo|arena:id]|reload>.");
    }

    private static Boolean parseInheritedBoolean(String value) {
        if ("inherit".equals(value)) return null;
        if ("on".equals(value) || "true".equals(value)) return true;
        if ("off".equals(value) || "false".equals(value)) return false;
        throw new IllegalArgumentException("Use on, off ou inherit.");
    }

    private static Set<String> parseChoiceList(String value, Set<String> configured, boolean kits) {
        if ("inherit".equals(value)) return null;
        Set<String> parsed = new LinkedHashSet<String>();
        if (!"none".equals(value)) for (String id : value.split(",")) {
            if (!configured.contains(id)) throw new IllegalArgumentException("ID desconhecido: " + id);
            parsed.add(id);
        }
        if (kits && !parsed.contains("default"))
            throw new IllegalArgumentException("Kits permitidos devem incluir default.");
        return parsed;
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelp(sender, label, null);
    }

    private void sendHelp(CommandSender sender, String label, String category) {
        if (category != null) {
            String normalized = category.toLowerCase(Locale.ROOT);
            if ("setup".equals(normalized)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Setup de arena");
                sender.sendMessage(ChatColor.GRAY + "1. Configure POS1/POS2 com /sbw setarena e confirme.");
                sender.sendMessage(ChatColor.GRAY + "2. /sbw setup mode <solo|equipe>");
                sender.sendMessage(ChatColor.GRAY + "3. /sbw setup team-size <2..maximo> (somente equipe)");
                sender.sendMessage(ChatColor.GRAY + "4. /sbw create teams <quantidade>");
                sender.sendMessage(ChatColor.GRAY + "5. /sbw setup status mostra o proximo passo real.");
                sender.sendMessage(ChatColor.GRAY + "/sbw setup <status|undo|pause|resume <arena>|cancel <arena>>");
                sender.sendMessage(ChatColor.GRAY + "/sbw next - repetir a instrucao da etapa atual");
                sender.sendMessage(ChatColor.GRAY + "/sbw setarena <center here|pos1|pos2|upoversize|upheight|uplow|tp|preview|confirm|cancel>");
                return;
            }
            if ("arena".equals(normalized)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Arenas");
                sender.sendMessage(ChatColor.GRAY + "/sbw arena <list|create|import|edit|publish|cancel|exit>");
                sender.sendMessage(ChatColor.GRAY + "/sbw arena group <arena> <grupo> - selecao na rede");
                sender.sendMessage(ChatColor.GRAY + "/sbw arena rule <arena> <split|afk|respawn|kits|abilities> <valor>");
                sender.sendMessage(ChatColor.GRAY + "/sbw arena region <arena> <main|protection|waiting> <wand|worldedit|clear>");
                sender.sendMessage(ChatColor.GRAY + "/sbw arena set <arena> <lobby|spectator|editspawn|spawn|respawn|bed> [equipe]");
                sender.sendMessage(ChatColor.GRAY + "/sbw setarena ... - limites automaticos ou selecao manual");
                return;
            }
            if ("world".equals(normalized)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Mundos");
                sender.sendMessage(ChatColor.GRAY + "/sbw world <list|create|import|info|tp|delete|confirm-delete>");
                sender.sendMessage(ChatColor.GRAY + "/sbw always <status [mundo|arena:id]|reload>");
                return;
            }
            if ("player".equals(normalized) || "jogador".equals(normalized)) {
                sender.sendMessage(PREFIX + ChatColor.YELLOW + "Partidas");
                sender.sendMessage(ChatColor.GRAY + "/sbw join <arena|group:grupo> | /sbw spectate <arena> | /sbw leave");
                sender.sendMessage(ChatColor.GRAY + "/sbw chat <global|team|spectator>");
                sender.sendMessage(ChatColor.GRAY + "/sbw stats [jogador] - perfil, XP e nivel da rede");
                sender.sendMessage(ChatColor.GRAY + "/sbw profile | progress | rewards - menus de progressao");
                sender.sendMessage(ChatColor.GRAY + "/sbw top [metrica] [modo] [periodo] [pagina]");
                sender.sendMessage(ChatColor.GRAY + "/sbw idioma [pt_BR|en_US|es_ES] - idioma salvo no MySQL");
                sender.sendMessage(ChatColor.GRAY + "/sbw kit | /sbw ability | /sbw ability use");
                return;
            }
            sender.sendMessage(PREFIX + ChatColor.RED + "Categoria invalida. Use setup, arena, world ou player.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label
            + " <help [setup|arena|world|player]|version|status|stats|menu|arena|setarena|world|always|join|spectate|leave|chat|next|npc|matchpoint>");
        sender.sendMessage(ChatColor.GRAY + "/sbw menu | /sbw arena menu");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena create <id> <mundo-descarregado> | import <id> <mundo-descarregado>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena tp <arena> | /sbw arena exit");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena loadsource <arena>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena team <arena> <id> <nome> <cor-hex> <capacidade>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena removeteam <arena> <id>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena set <arena> <lobby|spectator|editspawn|spawn|respawn|bed> [equipe]");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena edit <arena> | /sbw arena edit <publish|cancel> <arena>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena region <arena> <main|protection|waiting> <wand|worldedit|clear>");
        sender.sendMessage(ChatColor.GRAY + "/sbw world <list|create|import|info|tp|delete|confirm-delete>");
        if (sender.hasPermission("starfinbedwars.command.always")
                || sender.hasPermission("starfinbedwars.admin"))
            sender.sendMessage(ChatColor.GRAY + "/sbw always <status [mundo|arena:id]|reload>");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena min|countdown|validate|enable|disable <arena> ...");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena generator <add|remove|list|setteam> <arena> ...");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena shop <arena> <items|upgrades>");
        sender.sendMessage(ChatColor.GRAY + "/sbw npc skin <items|upgrades> <nome-do-jogador>");
        sender.sendMessage(ChatColor.GRAY + "/sbw setup mode <solo|equipe> | team-size <2..maximo>");
        sender.sendMessage(ChatColor.GRAY + "/sbw create teams <quantidade>");
        sender.sendMessage(ChatColor.GRAY + "/sbw setup <status|undo|pause|resume <arena>|cancel <arena>>");
        sender.sendMessage(ChatColor.GRAY + "/sbw setarena <center here|pos1|pos2|upoversize <L>x<C>|upheight <n>|uplow <n>|tp|preview|confirm|cancel>");
        sender.sendMessage(ChatColor.GRAY + "/sbw join <arena|group:grupo> | /sbw spectate <arena> | /sbw leave");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena group <arena> <grupo> | /sbw idioma [pt_BR|en_US|es_ES]");
        sender.sendMessage(ChatColor.GRAY + "/sbw arena rule <arena> <split|afk|respawn|kits|abilities> <valor>");
        sender.sendMessage(ChatColor.GRAY + "/sbw kit [list] | /sbw ability [list|use]");
        if (sender.hasPermission("starfinbedwars.command.gameplay.admin")
                || sender.hasPermission("starfinbedwars.admin"))
            sender.sendMessage(ChatColor.GRAY + "/sbw gameplay <reload|diagnose|inspect|cooldowns>");
        sender.sendMessage(ChatColor.GRAY + "/sbw stats [jogador|UUID] | /sbw perfil [jogador]");
        sender.sendMessage(ChatColor.GRAY + "/sbw profile | progress | rewards | top [metrica] [modo] [periodo] [pagina]");
        if (sender.hasPermission("starfinbedwars.admin")) {
            sender.sendMessage(ChatColor.GRAY
                + "/sbw migrate preview <mysql|sqlite> | apply <mysql|sqlite> confirm");
        }
        if (sender.hasPermission("starfinbedwars.command.matchpoint")
                || sender.hasPermission("starfinbedwars.admin")) {
            sender.sendMessage(ChatColor.GRAY
                + "/sbw matchpoint [arena] - forcar Match Point para teste (OP)");
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Status do StarfinBedWars");
        sender.sendMessage(ChatColor.GRAY + "Plugin: "
            + (plugin.isPluginReady() ? "inicializado" : "nao inicializado"));
        sender.sendMessage(ChatColor.GRAY + "Servidor: " + plugin.getServer().getBukkitVersion());
        sender.sendMessage(ChatColor.GRAY + "StarfinMinigames: API compartilhada ainda nao disponivel.");
        List<String> services = plugin.getServiceNames();
        sender.sendMessage(ChatColor.GRAY + "Servicos: "
            + (services.isEmpty() ? "nenhum" : join(services)));
        sender.sendMessage(ChatColor.GRAY + "Arenas configuradas: " + arenas.getArenas().size());
        sender.sendMessage(ChatColor.GRAY + "Mundos gerenciados: " + worlds.getManagedWorlds().size());
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<String>(Arrays.asList(
                "help", "version", "menu", "arena", "join", "spectate", "leave", "chat",
                "stats", "perfil", "nivel", "profile", "progress", "rewards", "recompensas",
                "top", "idioma", "language", "lang", "kit", "ability"));
            if (sender.hasPermission("starfinbedwars.command.always")
                    || sender.hasPermission("starfinbedwars.admin")) choices.add("always");
            if (sender.hasPermission("starfinbedwars.command.gameplay.admin")
                    || sender.hasPermission("starfinbedwars.admin")) choices.add("gameplay");
            if (sender.hasPermission("starfinbedwars.command.status")
                    || sender.hasPermission("starfinbedwars.admin")) choices.add("status");
            if (hasArenaAdmin(sender)) {
                choices.addAll(Arrays.asList("setarena", "create", "setup", "next"));
                if (sender.hasPermission("starfinbedwars.admin")) choices.add("migrate");
            }
            if (sender.hasPermission("starfinbedwars.command.matchpoint")
                    || sender.hasPermission("starfinbedwars.admin")) choices.add("matchpoint");
            if (sender.hasPermission("starfinbedwars.command.world")
                    || sender.hasPermission("starfinbedwars.admin")) choices.add("world");
            return filter(choices, args[0]);
        }
        if ("migrate".equalsIgnoreCase(args[0]) && sender.hasPermission("starfinbedwars.admin")) {
            if (args.length == 2) return filter(Arrays.asList("preview", "apply"), args[1]);
            if (args.length == 3) return filter(Arrays.asList("mysql", "sqlite"), args[2]);
            if (args.length == 4 && "apply".equalsIgnoreCase(args[1])) {
                return filter(Collections.singletonList("confirm"), args[3]);
            }
        }
        if ("gameplay".equalsIgnoreCase(args[0]) && args.length == 2)
            return filter(Arrays.asList("reload", "diagnose", "inspect", "cooldowns"), args[1]);
        if ("always".equalsIgnoreCase(args[0]) && args.length == 2)
            return filter(Arrays.asList("status", "reload"), args[1]);
        if ("always".equalsIgnoreCase(args[0]) && args.length == 3
                && "status".equalsIgnoreCase(args[1])) {
            List<String> targets = new ArrayList<String>();
            for (World world : plugin.getServer().getWorlds()) targets.add(world.getName());
            for (ArenaDefinition arena : arenas.getArenas()) targets.add("arena:" + arena.getId());
            return filter(targets, args[2]);
        }
        if ("kit".equalsIgnoreCase(args[0]) && args.length == 2)
            return filter(Collections.singletonList("list"), args[1]);
        if ("ability".equalsIgnoreCase(args[0]) && args.length == 2)
            return filter(sender.hasPermission("starfinbedwars.admin")
                ? Arrays.asList("list", "use", "grant") : Arrays.asList("list", "use"), args[1]);
        if (args.length == 2 && ("idioma".equalsIgnoreCase(args[0])
                || "language".equalsIgnoreCase(args[0]) || "lang".equalsIgnoreCase(args[0]))) {
            return filter(Arrays.asList("pt_BR", "en_US", "es_ES"), args[1]);
        }
        if ("top".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return filter(Arrays.asList("wins", "games", "kills", "final_kills",
                "deaths", "final_deaths", "beds", "xp", "level", "win_rate", "kd"), args[1]);
            if (args.length == 3) return filter(Arrays.asList("all", "solo", "duo", "trio", "squad", "custom"), args[2]);
            if (args.length == 4) return filter(Arrays.asList("all", "weekly", "monthly"), args[3]);
        }
        if ("matchpoint".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!sender.hasPermission("starfinbedwars.command.matchpoint")
                    && !sender.hasPermission("starfinbedwars.admin")) {
                return Collections.emptyList();
            }
            List<String> active = new ArrayList<String>();
            for (ArenaDefinition arena : arenas.getArenas()) {
                GameMatch match = matches.getMatchForArena(arena.getId());
                if (match != null && match.getState() == MatchState.IN_GAME) {
                    active.add(arena.getId());
                }
            }
            return filter(active, args[1]);
        }
        if ("help".equalsIgnoreCase(args[0]) && args.length == 2) {
            return filter(Arrays.asList("setup", "arena", "world", "player"), args[1]);
        }
        if ("chat".equalsIgnoreCase(args[0]) && args.length == 2) {
            return filter(Arrays.asList("global", "team", "spectator"), args[1]);
        }
        if ("create".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Collections.singletonList("teams"), args[1]);
        }
        if ("create".equalsIgnoreCase(args[0]) && "teams".equalsIgnoreCase(args[1])
                && args.length == 3) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return Collections.emptyList();
        }
        if ("setup".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Arrays.asList("mode", "team-size", "status", "undo", "pause", "resume", "cancel"), args[1]);
        }
        if ("setup".equalsIgnoreCase(args[0]) && args.length == 3 && "mode".equalsIgnoreCase(args[1])) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Arrays.asList("solo", "equipe"), args[2]);
        }
        if ("setup".equalsIgnoreCase(args[0]) && args.length == 3
                && "team-size".equalsIgnoreCase(args[1])) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            List<String> sizes = new ArrayList<String>();
            for (int size = 2; size <= plugin.getGameplay().getSettings().maximumTeamSize; size++)
                sizes.add(Integer.toString(size));
            return filter(sizes, args[2]);
        }
        if ("setup".equalsIgnoreCase(args[0]) && args.length == 3
                && "color".equalsIgnoreCase(args[1])) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Arrays.asList("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW",
                "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
                "BROWN", "GREEN", "RED", "BLACK"), args[2]);
        }
        if ("setup".equalsIgnoreCase(args[0]) && args.length == 3
                && ("resume".equalsIgnoreCase(args[1]) || "cancel".equalsIgnoreCase(args[1]))) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(arenaIds(), args[2]);
        }
        if ("setarena".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Arrays.asList("center", "pos1", "pos2", "upoversize", "upheight",
                "uplow", "tp", "preview", "confirm", "cancel"), args[1]);
        }
        if ("setarena".equalsIgnoreCase(args[0]) && args.length == 3
                && "center".equalsIgnoreCase(args[1])) {
            if (!hasArenaAdmin(sender)) return Collections.emptyList();
            return filter(Collections.singletonList("here"), args[2]);
        }
        if ("world".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!sender.hasPermission("starfinbedwars.command.world")
                    && !sender.hasPermission("starfinbedwars.admin")) return Collections.emptyList();
            return filter(Arrays.asList("list", "create", "import", "info", "tp", "delete",
                "confirm-delete"), args[1]);
        }
        if ("world".equalsIgnoreCase(args[0]) && args.length == 4
                && "create".equalsIgnoreCase(args[1])) {
            return filter(Arrays.asList("empty", "platform"), args[3]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && args.length == 4
                && ("create".equalsIgnoreCase(args[1]) || "import".equalsIgnoreCase(args[1]))) {
            return filter(new ArrayList<String>(worlds.getManagedWorlds().keySet()), args[3]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && args.length == 2) {
            List<String> choices = new ArrayList<String>();
            choices.add("list");
            if (hasArenaAdmin(sender)) {
                choices.addAll(Arrays.asList("menu", "create", "import", "loadsource", "tp", "exit",
                    "team", "removeteam", "edit", "region", "set", "min", "countdown",
                    "generator", "shop", "group", "validate", "enable", "disable"));
            }
            return filter(choices, args[1]);
        }
        if ("npc".equalsIgnoreCase(args[0]) && args.length == 2) {
            return filter(Collections.singletonList("skin"), args[1]);
        }
        if ("npc".equalsIgnoreCase(args[0]) && args.length == 3
                && "skin".equalsIgnoreCase(args[1])) {
            return filter(Arrays.asList("items", "upgrades"), args[2]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && "edit".equalsIgnoreCase(args[1]) && args.length == 3) {
            List<String> choices = new ArrayList<String>(arenaIds());
            choices.add("publish");
            choices.add("cancel");
            return filter(choices, args[2]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && "edit".equalsIgnoreCase(args[1]) && args.length == 4) {
            return filter(arenaIds(), args[3]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && "region".equalsIgnoreCase(args[1]) && args.length == 3) {
            return filter(arenaIds(), args[2]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && "region".equalsIgnoreCase(args[1]) && args.length == 4) {
            return filter(Arrays.asList("main", "protection", "waiting"), args[3]);
        }
        if ("arena".equalsIgnoreCase(args[0]) && "region".equalsIgnoreCase(args[1]) && args.length == 5) {
            return filter(Arrays.asList("wand", "worldedit", "clear"), args[4]);
        }
        if (args.length == 3 && "arena".equalsIgnoreCase(args[0])
                && "generator".equalsIgnoreCase(args[1])) {
            return filter(Arrays.asList("add", "remove", "list", "setteam"), args[2]);
        }
        if (args.length == 3 && "arena".equalsIgnoreCase(args[0])
                && !Arrays.asList("create", "import", "list", "generator")
                    .contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(arenaIds(), args[2]);
        }
        if (args.length == 2 && ("join".equalsIgnoreCase(args[0]) || "spectate".equalsIgnoreCase(args[0]))) {
            List<String> candidates = arenaIds();
            if ("join".equalsIgnoreCase(args[0])) {
                for (ArenaDefinition arena : arenas.getArenas()) {
                    String group = "group:" + arena.getGroup();
                    if (!candidates.contains(group)) candidates.add(group);
                }
            }
            return filter(candidates, args[1]);
        }
        if (args.length == 4 && "arena".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            return filter(Arrays.asList("lobby", "spectator", "editspawn", "spawn", "respawn", "bed"), args[3]);
        }
        if (args.length == 5 && "arena".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            ArenaDefinition arena = arenas.getArena(args[2]);
            if (arena != null) {
                List<String> teams = new ArrayList<String>();
                for (String id : arena.getTeams().keySet()) teams.add(id);
                return filter(teams, args[4]);
            }
        }
        if (args.length == 4 && "arena".equalsIgnoreCase(args[0])
                && "generator".equalsIgnoreCase(args[1]) && "add".equalsIgnoreCase(args[2])) {
            return filter(arenaIds(), args[3]);
        }
        if (args.length == 5 && "arena".equalsIgnoreCase(args[0])
                && "generator".equalsIgnoreCase(args[1]) && "add".equalsIgnoreCase(args[2])) {
            return filter(Arrays.asList("iron", "gold", "diamond", "emerald"), args[4]);
        }
        if (args.length == 5 && "arena".equalsIgnoreCase(args[0])
                && "generator".equalsIgnoreCase(args[1])
                && ("remove".equalsIgnoreCase(args[2]) || "setteam".equalsIgnoreCase(args[2]))) {
            ArenaDefinition arena = arenas.getArena(args[3]);
            if (arena != null) {
                return filter(new ArrayList<String>(arena.getGenerators().keySet()), args[4]);
            }
        }
        if (args.length == 6 && "arena".equalsIgnoreCase(args[0])
                && "generator".equalsIgnoreCase(args[1]) && "setteam".equalsIgnoreCase(args[2])) {
            ArenaDefinition arena = arenas.getArena(args[3]);
            if (arena != null) {
                return filter(new ArrayList<String>(arena.getTeams().keySet()), args[5]);
            }
        }
        if (args.length == 4 && "arena".equalsIgnoreCase(args[0])
                && "shop".equalsIgnoreCase(args[1])) {
            return filter(Arrays.asList("items", "upgrades"), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> arenaIds() {
        List<String> ids = new ArrayList<String>();
        for (ArenaDefinition arena : arenas.getArenas()) ids.add(arena.getId());
        return ids;
    }

    /** Prefer a populated WAITING room; avoid splitting players across empty arenas. */
    private String arenaForGroup(String group) {
        if (!group.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Grupo invalido.");
        }
        ArenaDefinition best = null;
        int occupancy = -1;
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (!arena.isEnabled() || !group.equals(arena.getGroup())
                    || arenas.isEditing(arena.getId()) || matches.isPreparingArena(arena.getId())) continue;
            GameMatch match = matches.getMatchForArena(arena.getId());
            int count = match == null ? 0 : match.getConnectedCount();
            if (match != null && (match.getState() != MatchState.WAITING
                    && match.getState() != MatchState.STARTING
                    || count >= arena.getMaximumPlayers())) continue;
            if (best == null || count > occupancy
                    || count == occupancy && arena.getId().compareTo(best.getId()) < 0) {
                best = arena;
                occupancy = count;
            }
        }
        if (best == null) throw new IllegalStateException("Nenhuma arena disponivel no grupo '" + group + "'.");
        return best.getId();
    }

    private boolean hasArenaAdmin(CommandSender sender) {
        return sender.hasPermission("starfinbedwars.admin")
            || sender.hasPermission("starfinbedwars.command.arena");
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> matches = new ArrayList<String>();
        String normalized = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) matches.add(option);
        }
        return matches;
    }
}
