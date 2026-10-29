package com.starfinplugins.bedwars.command;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.RankedService;
import com.starfinplugins.bedwars.ranked.RankedProfile;
import com.starfinplugins.bedwars.ranked.RankedSettings;
import com.starfinplugins.bedwars.stats.MatchMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class RankedCommand {
    private RankedCommand() { }

    static void execute(StarfinBedWarsPlugin plugin, CommandSender sender, String[] args) {
        RankedService service = plugin.getRanked();
        RankedSettings settings = service == null ? null : service.getSettings();
        if (settings == null) { sender.sendMessage("§cConfiguracao competitiva invalida."); return; }
        if (args.length >= 3 && "admin".equalsIgnoreCase(args[1])) {
            if ("season".equalsIgnoreCase(args[2])) {
                RankedSeasonCommand.execute(plugin, sender, args); return;
            }
            if ("rewards".equalsIgnoreCase(args[2]) && args.length == 5
                    && "inspect".equalsIgnoreCase(args[3])) {
                if (!sender.hasPermission("starfinbedwars.ranked.admin.rewards.inspect")
                        && !sender.hasPermission("starfinbedwars.ranked.admin")) {
                    sender.sendMessage("§cSem permissao."); return;
                }
                service.seasonQuery(repo -> repo.auditRewards(args[4]))
                    .whenComplete((lines, error) -> {
                        if (!plugin.isEnabled()) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (sender instanceof Player && !((Player) sender).isOnline()) return;
                            if (error != null) sender.sendMessage("§cAuditoria indisponivel.");
                            else for (String line : lines) sender.sendMessage("§7" + line);
                        });
                    });
                return;
            }
            if (!sender.hasPermission("starfinbedwars.ranked.admin")) {
                sender.sendMessage("§cSem permissao."); return;
            }
            if ("status".equalsIgnoreCase(args[2]) && args.length == 3) {
                send(sender, settings.message("admin-status", "&bCompetitivo: {enabled} | Banco: {database} | Contexto: {context}")
                    .replace("{enabled}", Boolean.toString(settings.enabled))
                    .replace("{database}", Boolean.toString(service.isDatabaseReady()))
                    .replace("{context}", service.contextForDisplay()));
                return;
            }
            if ("inspect".equalsIgnoreCase(args[2]) && args.length == 4) {
                for (String mode : MatchMode.STANDARD) if (settings.modes.contains(mode))
                    profile(plugin, sender, args[3], mode);
                return;
            }
            sender.sendMessage("§eUse /sbw ranked admin <status|inspect jogador>."); return;
        }
        if (args.length == 1 && sender instanceof Player) {
            plugin.getRankedMenus().openMain((Player) sender); return;
        }
        if (args.length == 2 && sender instanceof Player) {
            Player player = (Player) sender;
            switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "season": plugin.getRankedMenus().openSeason(player); return;
                case "top": plugin.getRankedMenus().openTop(player); return;
                case "rewards": plugin.getRankedMenus().openRewards(player); return;
                case "history": plugin.getRankedMenus().openHistory(player); return;
                case "matches": plugin.getRankedMenus().openMatchHistory(player); return;
                case "divisions": plugin.getRankedMenus().openDivisions(player); return;
                default: break;
            }
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            send(sender, settings.message(settings.enabled ? "loading" : "disabled",
                settings.enabled ? "&7Sistema competitivo configurado." : "&eRanqueadas ainda nao abertas."));
            sender.sendMessage("§7Banco: " + (service.isDatabaseReady() ? "§aonline" : "§cindisponivel")
                + " §7| Contexto: §f" + service.contextForDisplay());
            if (sender instanceof Player) profile(plugin, sender, null, currentMode(plugin, (Player) sender));
            return;
        }
        if (args.length == 1 || (args.length >= 2 && "profile".equalsIgnoreCase(args[1]))) {
            if (args.length > 4) { sender.sendMessage("§eUse /sbw ranked profile [jogador] [modo]."); return; }
            String identifier = args.length >= 3 ? args[2] : null;
            if (identifier == null && !(sender instanceof Player)) {
                sender.sendMessage("§eInforme um jogador."); return;
            }
            String mode = args.length == 4 ? args[3]
                : sender instanceof Player ? currentMode(plugin, (Player) sender) : "solo";
            try { mode = MatchMode.persisted(mode); }
            catch (IllegalArgumentException invalid) {
                sender.sendMessage("§cModo invalido. Use solo, duo, trio, squad ou custom."); return;
            }
            profile(plugin, sender, identifier, mode); return;
        }
        sender.sendMessage("§eUse /sbw ranked [profile [jogador] [modo]|status|admin status|admin inspect jogador].");
    }

    private static String currentMode(StarfinBedWarsPlugin plugin, Player player) {
        GameMatch match = plugin.getMatchEngine().getMatchForPlayer(player.getUniqueId());
        return match == null ? "solo" : MatchMode.of(match.getArena());
    }
    private static void profile(StarfinBedWarsPlugin plugin, CommandSender sender,
                                String identifier, String mode) {
        RankedService service = plugin.getRanked();
        RankedSettings settings = service.getSettings();
        if (!service.isDatabaseReady()) {
            send(sender, settings.message("unavailable", "&cSistema competitivo indisponivel.")); return;
        }
        send(sender, settings.message("loading", "&7Consultando perfil competitivo..."));
        java.util.concurrent.CompletableFuture<RankedProfile> lookup = identifier == null
            ? service.getCompetitiveProfile(((Player) sender).getUniqueId(), mode)
            : service.getCompetitiveProfile(identifier, mode);
        lookup.thenCompose(value -> value == null
            ? java.util.concurrent.CompletableFuture.completedFuture(null)
            : service.seasonQuery(repo -> repo.division(value.context, value.rating, settings))
                .thenApply(division -> new ProfileDisplay(value, division)))
            .whenComplete((display, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player && !((Player) sender).isOnline()) return;
                if (error != null) {
                    send(sender, settings.message("unavailable", "&cSistema competitivo indisponivel."));
                } else if (display == null) {
                    send(sender, settings.message("no-player", "&cJogador nao encontrado."));
                } else {
                    RankedProfile value = display.profile;
                    String player = identifier == null ? sender.getName() : identifier;
                    int required = service.placementRequired();
                    String division = value.isPlaced(required)
                        ? display.division.color + display.division.name
                        : "&eEm posicionamento";
                    String line = settings.message("profile", "&b{player} | {mode} | {rating} | {division}")
                        .replace("{player}", player).replace("{mode}", mode)
                        .replace("{rating}", Integer.toString(value.rating))
                        .replace("{division}", division)
                        .replace("{placement}", Math.min(required, value.placementGames)
                            + "/" + required)
                        .replace("{wins}", Integer.toString(value.wins))
                        .replace("{losses}", Integer.toString(value.losses))
                        .replace("{draws}", Integer.toString(value.draws));
                    send(sender, line);
                    String date = value.lastMatchAt == 0 ? "Nenhuma"
                        : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                            .withZone(service.getSeasonSettings().displayZone)
                            .format(Instant.ofEpochMilli(value.lastMatchAt));
                    send(sender, settings.message("last-match", "&7Ultima partida: &f{date}")
                        .replace("{date}", date));
                }
            });
        });
    }
    private static final class ProfileDisplay {
        final RankedProfile profile;
        final RankedSettings.Division division;
        ProfileDisplay(RankedProfile profile, RankedSettings.Division division) {
            this.profile = profile; this.division = division;
        }
    }
    private static void send(CommandSender sender, String value) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
    }
}
