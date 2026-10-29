package com.starfinplugins.bedwars.command;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.RankedService;
import com.starfinplugins.bedwars.ranked.RankedSeason;
import com.starfinplugins.bedwars.ranked.RankedSeasonSettings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class RankedSeasonCommand {
    private RankedSeasonCommand() { }

    static void execute(StarfinBedWarsPlugin plugin, CommandSender sender, String[] args) {
        RankedService service = plugin.getRanked();
        if (service == null || service.getSeasonSettings() == null) {
            sender.sendMessage("§cConfiguracao de temporadas indisponivel."); return;
        }
        if (args.length < 4 || !"season".equalsIgnoreCase(args[2])) {
            sender.sendMessage("§eUse /sbw ranked admin season <list|status|info|create|schedule|activate|close|finalize|cancel>.");
            return;
        }
        String action = args[3].toLowerCase(java.util.Locale.ROOT);
        String permission = "starfinbedwars.ranked.admin.season." + action;
        if (!sender.hasPermission(permission) && !sender.hasPermission("starfinbedwars.ranked.admin")) {
            sender.sendMessage("§cSem permissao: " + permission); return;
        }
        if ("status".equals(action)) {
            deliver(plugin, sender, service.seasonQuery(repo -> repo.diagnostics(
                service.getSeasonSettings().closingGraceMinutes)),
                lines -> { for (String line : lines) sender.sendMessage("§7" + line); });
            return;
        }
        if ("list".equals(action)) {
            deliver(plugin, sender, service.getSeasons(20, 1), seasons -> {
                if (seasons.isEmpty()) sender.sendMessage("§eNenhuma temporada criada.");
                for (RankedSeason season : seasons)
                    sender.sendMessage("§b" + season.id + " §7" + season.state
                        + " §f" + season.name);
            });
            return;
        }
        if (args.length < 5) { sender.sendMessage("§eInforme o ID da temporada."); return; }
        String id = args[4];
        if (!id.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage("§cID invalido."); return; }
        if ("info".equals(action)) {
            deliver(plugin, sender, service.getSeason(id), season -> {
                if (season == null) { sender.sendMessage("§cTemporada inexistente."); return; }
                sender.sendMessage("§b" + season.name + " §7[" + season.id + "] §f" + season.state);
                java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter
                    .ofPattern("dd/MM/yyyy HH:mm z").withZone(service.getSeasonSettings().displayZone);
                sender.sendMessage("§7Inicio: " + format.format(java.time.Instant.ofEpochMilli(season.startsAt))
                    + " | Fim: " + format.format(java.time.Instant.ofEpochMilli(season.endsAt))
                    + " | Politica: " + season.version);
                sender.sendMessage("§7Modos: " + season.modes + " | Reset: " + season.resetType);
            });
            return;
        }
        if (!service.getSeasonSettings().enabled) {
            sender.sendMessage("§cTemporadas desativadas em seasons.yml."); return;
        }
        if ("create".equals(action)) {
            RankedSeasonSettings proposal = service.getSeasonSettings();
            if (!id.equals(proposal.id)) {
                sender.sendMessage("§eConfigure current.id=" + id + " em seasons.yml e reinicie antes de criar.");
                return;
            }
            for (RankedSeasonSettings.Title title : proposal.titles)
                if (plugin.getStatistics().getRewards().title(title.id) != null) {
                    sender.sendMessage("§cID de titulo ja usado no XP: " + title.id); return;
                }
            deliver(plugin, sender, service.seasonQuery(repo ->
                repo.create(proposal, service.getSettings(), sender.getName())),
                season -> sender.sendMessage("§aTemporada criada em DRAFT: " + season.id));
            return;
        }
        RankedSeason.State target;
        switch (action) {
            case "schedule": target = RankedSeason.State.SCHEDULED; break;
            case "activate": target = RankedSeason.State.ACTIVE; break;
            case "close": target = RankedSeason.State.CLOSING; break;
            case "finalize": target = RankedSeason.State.FINALIZING; break;
            case "cancel": target = RankedSeason.State.CANCELLED; break;
            default: sender.sendMessage("§eAcao sazonal desconhecida."); return;
        }
        if (!"schedule".equals(action) && (args.length < 6 || !"CONFIRM".equals(args[5]))) {
            sender.sendMessage("§eConfirme com /sbw ranked admin season " + action + " " + id + " CONFIRM");
            return;
        }
        deliver(plugin, sender, service.seasonQuery(repo -> {
            RankedSeason current = repo.find(id);
            if (current != null && current.state == RankedSeason.State.FINALIZING
                    && target == RankedSeason.State.FINALIZING)
                return repo.finalizeSeason(id, sender.getName());
            return repo.transition(id, target, sender.getName());
        }), season -> {
            sender.sendMessage("§a" + season.id + " → " + season.state);
            if (season.state == RankedSeason.State.FINALIZING)
                sender.sendMessage("§eAguarde os backends sincronizarem; repita finalize com CONFIRM para publicar.");
            service.syncNow();
        });
    }

    private static <T> void deliver(StarfinBedWarsPlugin plugin, CommandSender sender,
            CompletableFuture<T> result, java.util.function.Consumer<T> success) {
        sender.sendMessage("§7Consultando MySQL...");
        result.whenComplete((value, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player && !((Player) sender).isOnline()) return;
                if (error != null) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    sender.sendMessage(ChatColor.RED + "Operacao recusada: " + cause.getMessage());
                } else success.accept(value);
            });
        });
    }
}
