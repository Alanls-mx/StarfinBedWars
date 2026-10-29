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
            if (!sender.hasPermission("starfinbedwars.ranked.admin")) {
                sender.sendMessage("§cSem permissao."); return;
            }
            if ("status".equalsIgnoreCase(args[2]) && args.length == 3) {
                send(sender, settings.message("admin-status", "&bCompetitivo: {enabled} | Banco: {database} | Contexto: {context}")
                    .replace("{enabled}", Boolean.toString(settings.enabled))
                    .replace("{database}", Boolean.toString(service.isDatabaseReady()))
                    .replace("{context}", settings.context));
                return;
            }
            if ("inspect".equalsIgnoreCase(args[2]) && args.length == 4) {
                for (String mode : MatchMode.STANDARD) if (settings.modes.contains(mode))
                    profile(plugin, sender, args[3], mode);
                return;
            }
            sender.sendMessage("§eUse /sbw ranked admin <status|inspect jogador>."); return;
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            send(sender, settings.message(settings.enabled ? "loading" : "disabled",
                settings.enabled ? "&7Sistema competitivo configurado." : "&eRanqueadas ainda nao abertas."));
            sender.sendMessage("§7Banco: " + (service.isDatabaseReady() ? "§aonline" : "§cindisponivel")
                + " §7| Contexto: §f" + settings.context);
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
        lookup.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player && !((Player) sender).isOnline()) return;
                if (error != null) {
                    send(sender, settings.message("unavailable", "&cSistema competitivo indisponivel."));
                } else if (value == null) {
                    send(sender, settings.message("no-player", "&cJogador nao encontrado."));
                } else {
                    String player = identifier == null ? sender.getName() : identifier;
                    String division = value.isPlaced(settings.placements)
                        ? settings.division(value.rating).color + settings.division(value.rating).name
                        : "&eEm posicionamento";
                    String line = settings.message("profile", "&b{player} | {mode} | {rating} | {division}")
                        .replace("{player}", player).replace("{mode}", mode)
                        .replace("{rating}", Integer.toString(value.rating))
                        .replace("{division}", division)
                        .replace("{placement}", Math.min(settings.placements, value.placementGames)
                            + "/" + settings.placements)
                        .replace("{wins}", Integer.toString(value.wins))
                        .replace("{losses}", Integer.toString(value.losses))
                        .replace("{draws}", Integer.toString(value.draws));
                    send(sender, line);
                    String date = value.lastMatchAt == 0 ? "Nenhuma"
                        : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value.lastMatchAt));
                    send(sender, settings.message("last-match", "&7Ultima partida: &f{date}")
                        .replace("{date}", date));
                }
            });
        });
    }
    private static void send(CommandSender sender, String value) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
    }
}
