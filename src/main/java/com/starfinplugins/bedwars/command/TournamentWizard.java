package com.starfinplugins.bedwars.command;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.config.MessagePrefix;
import com.starfinplugins.bedwars.tournament.TournamentSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

/** Persisted, resumable 13-step administrative creation assistant. */
final class TournamentWizard {
    private TournamentWizard() { }
    static void execute(StarfinBedWarsPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cO assistente exige um jogador; o console pode usar admin create.");
            return;
        }
        Player player = (Player) sender;
        Path file = plugin.getDataFolder().toPath().resolve("tournament-drafts")
            .resolve(player.getUniqueId() + ".yml");
        try {
            YamlConfiguration draft;
            if (!Files.exists(file)) {
                File template = new File(plugin.getDataFolder(), "tournaments.yml");
                draft = YamlConfiguration.loadConfiguration(template);
                draft.set("wizard.step", 1);
                save(file, draft);
            } else draft = YamlConfiguration.loadConfiguration(file.toFile());
            int step = Math.max(1, Math.min(13, draft.getInt("wizard.step", 1)));
            if (args.length >= 4) {
                String action = args[3].toLowerCase(Locale.ROOT);
                if ("back".equals(action)) { step = Math.max(1, step - 1); draft.set("wizard.step", step); save(file, draft); }
                else if ("next".equals(action)) {
                    step = Math.min(13, step + 1); draft.set("wizard.step", step); save(file, draft);
                } else if ("set".equals(action)) {
                    if (args.length < 5) throw new IllegalArgumentException("Informe o valor desta etapa.");
                    String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    set(draft, step, value);
                    save(file, draft);
                    player.sendMessage("§aEtapa salva no rascunho.");
                } else if ("confirm".equals(action)) {
                    if (step != 13 || args.length != 5 || !"CONFIRM".equals(args[4]))
                        throw new IllegalArgumentException("Revise ate a etapa 13 e use confirm CONFIRM.");
                    TournamentSettings settings = new TournamentSettings(draft);
                    plugin.getTournaments().query(repo -> repo.create(settings, player.getName()))
                        .whenComplete((tournament, error) -> {
                            if (!plugin.isEnabled()) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                if (error != null) {
                                    player.sendMessage("§cNao foi possivel criar o rascunho: "
                                        + error.getCause()); return;
                                }
                                try { Files.deleteIfExists(file); }
                                catch (IOException failure) { plugin.getLogger().warning(
                                    "Rascunho local nao removido apos criacao: " + file); }
                                player.sendMessage("§aTorneio criado como DRAFT: " + tournament.id);
                            });
                        });
                    return;
                } else throw new IllegalArgumentException("Use set, next, back ou confirm.");
            }
            show(plugin, player, draft, step);
        } catch (IOException | IllegalArgumentException error) {
            player.sendMessage("§cAssistente: " + error.getMessage());
        }
    }
    private static void set(YamlConfiguration draft, int step, String input) {
        String[] fields = input.split("\\|", -1);
        switch (step) {
            case 1:
                require(fields.length == 2 && fields[0].trim().matches("[a-z0-9_-]{1,32}")
                    && !fields[1].trim().isEmpty() && fields[1].trim().length() <= 64,
                    "Use <id>|<nome>.");
                draft.set("proposal.id", fields[0].trim());
                draft.set("proposal.name", fields[1].trim()); break;
            case 2:
                require(fields.length == 2, "Use <solo|duo|trio|squad|custom>|<tamanho>.");
                draft.set("proposal.mode", fields[0].trim().toLowerCase(Locale.ROOT));
                draft.set("proposal.team-size", Integer.parseInt(fields[1].trim())); break;
            case 3: throw new IllegalArgumentException("Formato fixo SINGLE_ELIMINATION; use next.");
            case 4:
                require(fields.length == 2, "Use <minimo>|<maximo>.");
                draft.set("proposal.minimum-teams", Integer.parseInt(fields[0].trim()));
                draft.set("proposal.maximum-teams", Integer.parseInt(fields[1].trim())); break;
            case 5:
                require("true".equalsIgnoreCase(input) || "false".equalsIgnoreCase(input),
                    "Use true ou false.");
                draft.set("proposal.allow-substitutions", Boolean.parseBoolean(input)); break;
            case 6:
                require(fields.length == 3, "Use <abre UTC>|<fecha UTC>|<inicia UTC>.");
                for (String date : fields) Instant.parse(date.trim());
                draft.set("proposal.registration-opens", fields[0].trim());
                draft.set("proposal.registration-closes", fields[1].trim());
                draft.set("proposal.starts-at", fields[2].trim()); break;
            case 7:
                require(input.matches("[a-z0-9_-]{1,32}"), "Grupo de arenas invalido.");
                draft.set("proposal.arena-group", input); break;
            case 8:
                require(fields.length == 3, "Use <1|3|5>|<RANDOM|SEEDED>|<seed>.");
                draft.set("proposal.best-of", Integer.parseInt(fields[0].trim()));
                draft.set("proposal.draw", fields[1].trim().toUpperCase(Locale.ROOT));
                draft.set("proposal.random-seed", Long.parseLong(fields[2].trim())); break;
            case 9:
                draft.set("proposal.check-in-grace-minutes", Integer.parseInt(input.trim())); break;
            case 10:
                require(fields.length == 2, "Use <limite>|<publico true/false>.");
                draft.set("proposal.spectator-limit", Integer.parseInt(fields[0].trim()));
                draft.set("proposal.public-spectators", Boolean.parseBoolean(fields[1].trim())); break;
            case 11:
                require(fields.length == 3 && fields[0].length() <= 48
                    && fields[1].length() <= 48 && fields[2].length() <= 48,
                    "Use <campeao>|<finalista>|<participante> (ate 48 caracteres cada).");
                draft.set("proposal.champion-title", fields[0].trim());
                draft.set("proposal.runner-up-title", fields[1].trim());
                draft.set("proposal.participant-title", fields[2].trim()); break;
            default: throw new IllegalArgumentException("Etapa de revisao; use next ou back.");
        }
    }
    private static void show(StarfinBedWarsPlugin plugin, Player player, YamlConfiguration draft, int step) {
        String[] instructions = {
            "", "Identificacao: set <id>|<nome>",
            "Modalidade: set <modo>|<tamanho>",
            "Formato: eliminacao simples (next)",
            "Equipes: set <minimo>|<maximo>",
            "Substituicoes: set <true|false>",
            "Datas UTC: set <abre>|<fecha>|<inicia> (ISO-8601)",
            "Arenas: set <grupo>",
            "Series: set <1|3|5>|<RANDOM|SEEDED>|<seed>",
            "Comparecimento: set <graca-em-minutos>; sem WO automatico",
            "Espectadores: set <limite>|<true|false>",
            "Premios: set <campeao>|<finalista>|<participante>",
            "Revisao: use next apos conferir os valores",
            "Confirmacao: use confirm CONFIRM"
        };
        player.sendMessage(MessagePrefix.value(plugin) + "§bAssistente §f" + step + "/13 §7— " + instructions[step]);
        player.sendMessage("§7ID §f" + draft.getString("proposal.id") + " §7Nome §f"
            + draft.getString("proposal.name") + " §7Modo §f" + draft.getString("proposal.mode")
            + " §7Equipe §f" + draft.getInt("proposal.team-size"));
        player.sendMessage("§7Equipes §f" + draft.getInt("proposal.minimum-teams") + "–"
            + draft.getInt("proposal.maximum-teams") + " §7MD§f" + draft.getInt("proposal.best-of")
            + " §7Arena §f" + draft.getString("proposal.arena-group"));
        player.sendMessage("§7Navegar: /sbw tournament admin wizard <next|back|set ...>");
        if (step == 12 || step == 13) {
            try { new TournamentSettings(draft);
                player.sendMessage("§aConfiguracao completa e valida."); }
            catch (RuntimeException invalid) {
                player.sendMessage("§cConfiguracao incompleta: " + invalid.getMessage());
            }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private static void save(Path file, YamlConfiguration yaml) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temp, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
