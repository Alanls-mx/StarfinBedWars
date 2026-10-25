package com.starfinplugins.bedwars.setup;

import com.starfinplugins.bedwars.arena.ArenaEditingService;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.SpawnStructureDefinition;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.SafeLocationResolver;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ArenaSetupWizardService implements ManagedService, Listener {
    private static final String TOOL_NAME = ChatColor.GOLD + "Assistente de Arena";
    private final Plugin plugin;
    private final ArenaService arenas;
    private final ArenaEditingService editing;
    private final Map<String, Draft> drafts = new HashMap<String, Draft>();
    private final Map<UUID, Draft> active = new HashMap<UUID, Draft>();
    private final Map<UUID, SetupSelection> selections = new HashMap<UUID, SetupSelection>();
    private final Map<String, List<ArmorStand>> generatorHolograms =
        new HashMap<String, List<ArmorStand>>();
    private File directory;
    private ManagedMobGuard mobGuard;

    private int maximumTeamSize() {
        return ((com.starfinplugins.bedwars.StarfinBedWarsPlugin) plugin)
            .getGameplay().getSettings().maximumTeamSize;
    }
    private int maximumTotalPlayers() {
        return ((com.starfinplugins.bedwars.StarfinBedWarsPlugin) plugin)
            .getGameplay().getSettings().maximumTotalPlayers;
    }

    public void setMobGuard(ManagedMobGuard mobGuard) { this.mobGuard = mobGuard; }

    public ArenaSetupWizardService(Plugin plugin, ArenaService arenas, ArenaEditingService editing) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.editing = editing;
    }

    @Override public String getName() { return "arena-setup-wizard"; }

    @Override
    public void start() throws Exception {
        directory = new File(plugin.getDataFolder(), "setup-wizard");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Nao foi possivel criar o diretorio de rascunhos do assistente.");
        }
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Nao foi possivel listar rascunhos do assistente.");
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".yml")) continue;
            try {
                Draft draft = readDraft(file);
                drafts.put(draft.arenaId, draft);
            } catch (Exception error) {
                plugin.getLogger().log(Level.SEVERE,
                    "Rascunho do assistente invalido em " + file.getName() + ".", error);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        for (Map.Entry<UUID, Draft> entry : new HashMap<UUID, Draft>(active).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) restoreTool(player, entry.getValue(), true);
        }
        active.clear();
        for (String arenaId : new ArrayList<String>(generatorHolograms.keySet())) {
            clearGeneratorHolograms(arenaId);
        }
    }

    public void selectGameMode(Player player, ArenaDefinition arena, String mode) {
        requireEditOwner(player, arena);
        if (active.containsKey(player.getUniqueId())) {
            Draft activeDraft = active.get(player.getUniqueId());
            throw new IllegalStateException("O setup esta ativo na etapa " + stageDescription(activeDraft.stage)
                + ". Para reiniciar, use /sbw setup pause e depois /sbw setup cancel "
                + activeDraft.arenaId + ".");
        }
        Draft existingDraft = drafts.get(arena.getId());
        if (existingDraft != null) {
            String action = existingDraft.owner.equals(player.getUniqueId())
                ? "/sbw setup resume " + arena.getId() + " ou /sbw setup cancel " + arena.getId()
                : "o proprietario do rascunho deve retoma-lo ou cancela-lo";
            throw new IllegalStateException("Ja existe um setup salvo para esta arena (proxima etapa: "
                + stageDescription(existingDraft.stage) + "). Use " + action + " antes de escolher outro modo.");
        }
        requireArenaBounds(arena);
        if (mode == null) throw new IllegalArgumentException("Modo ausente.");
        String normalized = mode.toLowerCase(Locale.ROOT);
        if (!"solo".equals(normalized) && !"team".equals(normalized) && !"equipe".equals(normalized)) {
            throw new IllegalArgumentException("Modo deve ser solo ou equipe.");
        }
        SetupSelection selection = new SetupSelection(arena.getId(),
            "solo".equals(normalized) ? SetupMode.SOLO : SetupMode.TEAM);
        selections.put(player.getUniqueId(), selection);
        player.sendMessage(ChatColor.GREEN + "Modo escolhido: "
            + (selection.mode == SetupMode.SOLO ? "Solo." : "Equipe."));
        player.sendMessage(ChatColor.YELLOW + (selection.mode == SetupMode.SOLO
            ? "Proxima etapa: /sbw create teams <quantidade>"
            : "Proxima etapa: /sbw setup team-size <2.." + maximumTeamSize() + ">"));
    }

    public void selectTeamSize(Player player, ArenaDefinition arena, int capacity) {
        requireEditOwner(player, arena);
        requireArenaBounds(arena);
        SetupSelection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.arenaId.equals(arena.getId())) {
            throw new IllegalStateException("Escolha primeiro /sbw setup mode equipe.");
        }
        if (selection.mode != SetupMode.TEAM) {
            throw new IllegalStateException("O tamanho da equipe so pode ser definido no modo Equipe.");
        }
        if (capacity < 2 || capacity > maximumTeamSize()) {
            throw new IllegalArgumentException("Tamanho da equipe deve ser de 2 a "
                + maximumTeamSize() + " jogadores.");
        }
        selection.teamCapacity = capacity;
        player.sendMessage(ChatColor.GREEN + "Equipe configurada para " + capacity + " jogadores.");
        player.sendMessage(ChatColor.YELLOW + "Proxima etapa: /sbw create teams <quantidade>");
    }

    public void begin(Player player, ArenaDefinition arena, int teamCount) throws IOException {
        requireEditOwner(player, arena);
        Draft existingDraft = drafts.get(arena.getId());
        if (existingDraft != null) {
            String action = existingDraft.owner.equals(player.getUniqueId())
                ? "/sbw setup resume " + arena.getId() + " ou /sbw setup cancel " + arena.getId()
                : "o proprietario do rascunho deve retoma-lo ou cancela-lo";
            throw new IllegalStateException("Ja existe um setup salvo para esta arena (proxima etapa: "
                + stageDescription(existingDraft.stage) + "). Use " + action + ".");
        }
        SetupSelection selection = selections.get(player.getUniqueId());
        if (selection == null || !selection.arenaId.equals(arena.getId())) {
            throw new IllegalStateException("Escolha primeiro o modo: /sbw setup mode <solo|equipe>.");
        }
        requireArenaBounds(arena);
        if (selection.mode == SetupMode.TEAM && selection.teamCapacity == 0) {
            throw new IllegalStateException("Defina o tamanho antes: /sbw setup team-size <2.."
                + maximumTeamSize() + ">.");
        }
        int capacity = selection.mode == SetupMode.SOLO ? 1 : selection.teamCapacity;
        String mode = selection.mode == SetupMode.SOLO ? "solo" : "team";
        if (teamCount < 2 || teamCount > SetupTeamColor.values().length) {
            throw new IllegalArgumentException("Quantidade de equipes deve estar entre 2 e 16.");
        }
        if ((long) teamCount * capacity > maximumTotalPlayers()) {
            throw new IllegalArgumentException("Capacidade total excede " + maximumTotalPlayers()
                + " jogadores configurados em gameplay.yml.");
        }
        if (active.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Voce ja esta usando o assistente em outra arena.");
        }
        List<TeamDefinition> teams = new ArrayList<TeamDefinition>();
        SetupTeamColor[] colors = SetupTeamColor.values();
        for (int index = 0; index < teamCount; index++) {
            SetupTeamColor color = colors[index];
            TeamDefinition team = new TeamDefinition("team" + (index + 1),
                "Equipe " + (index + 1), color.getChatCode(), capacity);
            team.setAppearance(team.getDisplayName(), color.getChatCode(), color.getBlockData());
            teams.add(team);
        }
        String previousTeams = arenas.snapshot(arena);
        arenas.replaceTeams(arena, teams);
        Draft draft = new Draft(arena.getId(), player.getUniqueId(), mode,
            teamCount, capacity);
        drafts.put(arena.getId(), draft);
        try {
            activate(player, draft);
        } catch (IOException | RuntimeException error) {
            drafts.remove(arena.getId());
            try {
                arenas.restoreSnapshot(arena.getId(), previousTeams);
            } catch (IOException restoreError) {
                error.addSuppressed(restoreError);
            }
            throw error;
        }
        selections.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Times criados para o modo "
            + (selection.mode == SetupMode.SOLO ? "Solo." : "Equipe de " + capacity + "."));
        prompt(player, draft);
    }

    public void resume(Player player, ArenaDefinition arena) throws IOException {
        requireEditOwner(player, arena);
        Draft draft = drafts.get(arena.getId());
        if (draft == null) throw new IllegalStateException("Nao ha rascunho para esta arena.");
        if (!draft.owner.equals(player.getUniqueId())) {
            throw new IllegalStateException("Somente o administrador que iniciou o assistente pode retoma-lo.");
        }
        if (active.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Voce ja esta usando o assistente.");
        }
        activate(player, draft);
        refreshGeneratorHolograms(arena.getId(), player.getWorld(), arena);
        normalizeCompletedCentralGeneratorStage(draft, arena);
        prompt(player, draft);
    }

    public void continueSetup(Player player, ArenaDefinition arena) throws IOException {
        requireEditOwner(player, arena);
        Draft draft = drafts.get(arena.getId());
        if (draft != null) {
            if (!draft.owner.equals(player.getUniqueId())) {
                throw new IllegalStateException("O setup salvo pertence a outro administrador.");
            }
            if (active.get(player.getUniqueId()) == draft) {
                sendStatus(player);
            } else {
                resume(player, arena);
                player.sendMessage(ChatColor.GREEN + "Setup retomado automaticamente.");
                player.sendMessage(ChatColor.YELLOW + "Proximo passo: " + stageDescription(draft.stage) + ".");
            }
            return;
        }
        SetupSelection selection = selections.get(player.getUniqueId());
        if (selection != null && selection.arenaId.equals(arena.getId())) {
            player.sendMessage(ChatColor.YELLOW + "Setup inicial nao concluido.");
            player.sendMessage(ChatColor.YELLOW + "Proximo comando: "
                + (!hasArenaBounds(arena) ? boundsSetupInstruction()
                    : selection.mode == SetupMode.TEAM && selection.teamCapacity == 0
                    ? "/sbw setup team-size <2.." + maximumTeamSize() + ">"
                    : "/sbw create teams <quantidade>"));
            return;
        }
        if (isFreshSetup(arena)) {
            player.sendMessage(ChatColor.YELLOW + "Arena sem configuracao de BedWars detectada: setup do zero.");
            if (hasArenaBounds(arena)) {
                player.sendMessage(ChatColor.YELLOW + "Proximo comando: /sbw setup mode <solo|equipe>.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Proximo comando: " + boundsSetupInstruction());
                player.sendMessage(ChatColor.GRAY + "Depois revise com /sbw setarena preview e confirme com "
                    + "/sbw setarena confirm. So entao escolha o modo e crie as equipes.");
            }
            return;
        }
        sendExistingSetupContinuation(player, arena);
    }

    public String getMenuSetupHint(Player player, ArenaDefinition arena) {
        Draft draft = drafts.get(arena.getId());
        if (draft != null) {
            if (player != null && !draft.owner.equals(player.getUniqueId())) {
                return "Setup existente; somente o proprietario pode continua-lo.";
            }
            return "Continuacao salva: " + stageDescription(draft.stage) + ".";
        }
        SetupSelection selection = player == null ? null : selections.get(player.getUniqueId());
        if (selection != null && selection.arenaId.equals(arena.getId())) {
            if (!hasArenaBounds(arena)) return "Novo setup: proximo " + boundsSetupInstruction() + ".";
            return selection.mode == SetupMode.TEAM && selection.teamCapacity == 0
                ? "Novo setup: proximo /sbw setup team-size <2.." + maximumTeamSize() + ">."
                : "Novo setup: proximo /sbw create teams <quantidade>.";
        }
        if (isFreshSetup(arena)) {
            return hasArenaBounds(arena) ? "Novo setup: proximo /sbw setup mode <solo|equipe>."
                : "Novo setup: proximo " + boundsSetupInstruction() + ".";
        }
        String nextStep = nextExistingSetupStep(arena);
        if (nextStep != null) return "Continuacao: " + nextStep;
        if (arenas.validate(arena).isEmpty()) return "Setup configurado: revisar e publicar.";
        return "Continuacao: execute /sbw arena validate " + arena.getId() + ".";
    }

    private boolean isFreshSetup(ArenaDefinition arena) {
        if (arena.getLobby() != null || arena.getSpectator() != null
                || !arena.getGeneratorList().isEmpty() || arena.getItemShop() != null
                || arena.getUpgradeShop() != null || arena.getTeamList().size() != 2) return false;
        TeamDefinition red = arena.getTeams().get("red");
        TeamDefinition blue = arena.getTeams().get("blue");
        return isDefaultTeam(red, 4) && isDefaultTeam(blue, 4);
    }

    private boolean isDefaultTeam(TeamDefinition team, int capacity) {
        return team != null && team.getCapacity() == capacity && team.getSpawn() == null
            && team.getBed() == null && team.getItemShop() == null
            && team.getUpgradeShop() == null && team.getSpawnStructure() == null;
    }

    private void sendExistingSetupContinuation(Player player, ArenaDefinition arena) {
        player.sendMessage(ChatColor.YELLOW + "Configuracao existente detectada; nenhum time sera recriado.");
        String nextStep = nextExistingSetupStep(arena);
        if (nextStep != null) {
            player.sendMessage(ChatColor.YELLOW + "Proximo passo: " + nextStep);
            return;
        }
        List<String> errors = arenas.validate(arena);
        if (errors.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "Setup concluido. Proximo comando: /sbw arena validate "
                + arena.getId() + "; depois publique com /sbw arena edit publish " + arena.getId() + ".");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Proximo comando: /sbw arena validate "
                + arena.getId() + " para ver os itens restantes.");
            player.sendMessage(ChatColor.GRAY + errors.get(0));
        }
    }

    private String nextExistingSetupStep(ArenaDefinition arena) {
        for (TeamDefinition team : arena.getTeamList()) {
            if (team.getBed() == null) {
                return "/sbw arena set " + arena.getId() + " bed " + team.getId() + ".";
            }
            if (team.getSpawn() == null) {
                return "/sbw arena set " + arena.getId() + " spawn " + team.getId() + ".";
            }
        }
        if (arena.getItemShop() == null) {
            return "/sbw arena shop " + arena.getId() + " items.";
        }
        if (arena.getUpgradeShop() == null) {
            return "/sbw arena shop " + arena.getId() + " upgrades.";
        }
        if (arena.getRegion(ArenaRegionType.MAIN) == null) {
            return "/sbw arena region " + arena.getId() + " main wand (marcar e salvar a regiao principal).";
        }
        if (arena.getBounds() == null) {
            return "/sbw setarena center here e configure os limites; use /sbw help setup.";
        }
        for (TeamDefinition team : arena.getTeamList()) {
            if (!hasGenerator(arena, ResourceType.IRON, team.getId())
                    || !hasGenerator(arena, ResourceType.GOLD, team.getId())
                    || !hasGenerator(arena, ResourceType.EMERALD, team.getId())) {
                return "retome o assistente e marque uma unica regiao POS1/POS2 para Iron, Gold e Esmeralda "
                    + "do time " + team.getDisplayName() + ".";
            }
        }
        if (!hasGenerator(arena, ResourceType.DIAMOND, null)) {
            return "/sbw arena generator add " + arena.getId() + " diamond.";
        }
        if (!hasGenerator(arena, ResourceType.EMERALD, null)) {
            return "/sbw arena generator add " + arena.getId() + " emerald.";
        }
        if (arena.getLobby() == null) return "/sbw arena set " + arena.getId() + " lobby.";
        return null;
    }

    public void pause(Player player) throws IOException {
        Draft draft = active.remove(player.getUniqueId());
        if (draft == null) throw new IllegalStateException("Voce nao esta usando o assistente.");
        restoreTool(player, draft, true);
        save(draft);
        player.sendMessage(ChatColor.YELLOW + "Assistente pausado. O rascunho foi salvo.");
        player.sendMessage(ChatColor.YELLOW + "Proximo comando: /sbw setup resume "
            + draft.arenaId + " para continuar da etapa " + stageDescription(draft.stage) + ".");
    }

    public void cancel(Player player, ArenaDefinition arena) throws IOException {
        Draft draft = drafts.get(arena.getId());
        if (draft == null) throw new IllegalStateException("Nao ha rascunho para esta arena.");
        if (!draft.owner.equals(player.getUniqueId())) {
            throw new IllegalStateException("Somente o administrador que iniciou o assistente pode cancelar.");
        }
        active.remove(player.getUniqueId());
        restoreTool(player, draft, true);
        if (!file(arena.getId()).delete() && file(arena.getId()).exists()) {
            throw new IOException("Nao foi possivel excluir o rascunho do assistente.");
        }
        drafts.remove(arena.getId());
        clearGeneratorHolograms(arena.getId());
        player.sendMessage(ChatColor.YELLOW + "Rascunho removido; a configuracao ja aplicada foi mantida.");
        player.sendMessage(ChatColor.YELLOW + "Proximo passo: "
            + (hasArenaBounds(arena) ? "/sbw setup mode <solo|equipe>."
                : boundsSetupInstruction() + "."));
    }

    @EventHandler
    public void onChat(final AsyncPlayerChatEvent event) {
        final Draft draft = active.get(event.getPlayer().getUniqueId());
        if (draft == null || !(draft.stage == Stage.TEAM_NAME || draft.stage == Stage.TEAM_COLOR
                || draft.stage == Stage.HEIGHTS || draft.stage == Stage.GENERATOR_SPEC
                || (draft.stage == Stage.GENERATOR_POS1 && draft.generatorTeam == null))) return;
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Player player = event.getPlayer();
                if (!player.isOnline() || active.get(player.getUniqueId()) != draft) return;
                try {
                    if (draft.stage == Stage.TEAM_COLOR) {
                        sendColorOptions(player, draft);
                        return;
                    }
                    acceptChat(player, draft, message);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    player.sendMessage(ChatColor.RED + "Entrada invalida ou etapa incompleta: "
                        + error.getMessage());
                    prompt(player, draft);
                } catch (IOException error) {
                    player.sendMessage(ChatColor.RED + "Falha ao persistir a etapa do assistente.");
                    plugin.getLogger().log(Level.SEVERE,
                        "Falha ao salvar o assistente da arena " + draft.arenaId + ".", error);
                }
            }
        });
    }

    public void selectTeamColor(Player player, String colorName) throws IOException {
        Draft draft = active.get(player.getUniqueId());
        if (draft == null) throw new IllegalStateException("Inicie ou retome o assistente antes de escolher a cor.");
        ArenaDefinition arena = arenas.getArena(draft.arenaId);
        requireEditOwner(player, arena);
        if (draft.stage != Stage.TEAM_COLOR) {
            throw new IllegalStateException("A etapa atual nao esta aguardando a cor da equipe.");
        }
        if (colorName == null) throw new IllegalArgumentException("Selecione uma cor.");
        SetupTeamColor color;
        try {
            color = SetupTeamColor.valueOf(colorName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Cor invalida. Clique em uma das opcoes mostradas no chat.");
        }
        acceptChat(player, draft, color.name());
    }

    private void acceptChat(Player player, Draft draft, String message) throws IOException {
        ArenaDefinition arena = arenas.getArena(draft.arenaId);
        if (arena == null) throw new IllegalStateException("Arena do rascunho nao existe.");
        TeamDefinition team = draft.teamIndex < draft.teamCount ? currentTeam(arena, draft) : null;
        UndoRecord undo = null;
        if (draft.stage == Stage.TEAM_NAME) {
            String name = cleanName(message);
            undo = new UndoRecord(player.getWorld().getName(), (byte) 0, arenas.snapshot(arena), draft);
            arenas.setTeamAppearance(arena, team.getId(), name, team.getColor(), team.getColorData());
            draft.stage = Stage.TEAM_COLOR;
        } else if (draft.stage == Stage.TEAM_COLOR) {
            SetupTeamColor color = SetupTeamColor.parse(message);
            undo = new UndoRecord(player.getWorld().getName(), (byte) 0, arenas.snapshot(arena), draft);
            arenas.setTeamAppearance(arena, team.getId(), team.getDisplayName(),
                color.getChatCode(), color.getBlockData());
            draft.stage = Stage.BED;
        } else if (draft.stage == Stage.HEIGHTS) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length != 2) throw new IllegalArgumentException("Use: <teto 1-255> <void 0-255>.");
            int ceiling = Integer.parseInt(parts[0]);
            int voidHeight = Integer.parseInt(parts[1]);
            if (arena.getBounds() == null) throw new IllegalStateException("Defina os limites antes das alturas.");
            undo = new UndoRecord(player.getWorld().getName(), (byte) 0, arenas.snapshot(arena), draft);
            arenas.setBounds(arena, arena.getBounds(), ceiling, voidHeight);
            draft.stage = Stage.GENERATOR_SPEC;
        } else {
            if ("fim".equalsIgnoreCase(message.trim()) || "done".equalsIgnoreCase(message.trim())) {
                String nextGenerator = nextGeneratorInstruction(arena, draft);
                if (!"fim".equals(nextGenerator)) {
                    throw new IllegalStateException("Ainda falta configurar " + nextGenerator + ".");
                }
                if (arena.getLobby() == null) {
                    draft.stage = Stage.GENERATOR_SPEC;
                    draft.generatorResource = null;
                    draft.generatorTeam = null;
                    draft.first = null;
                    save(draft);
                    player.sendMessage(ChatColor.GREEN + "Geradores configurados com sucesso.");
                    player.sendMessage(ChatColor.YELLOW + "Proximo comando: /sbw arena set "
                        + arena.getId() + " lobby. Depois, digite fim novamente para concluir o setup.");
                    return;
                }
                finish(player, draft, arena);
                return;
            }
            String[] parts = message.trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (parts[0].contains(":")) {
                throw new IllegalArgumentException("Nao digite tipos para geradores de equipe. "
                    + "Use a varinha: clique esquerdo para POS1 e direito para POS2.");
            }
            if (draft.teamIndex < draft.teamCount) {
                throw new IllegalStateException("Geradores de equipe nao sao definidos por texto. "
                    + "Use a varinha para marcar POS1 e POS2.");
            }
            if (parts.length < 1 || parts.length > 2
                    || (parts.length == 2 && !"shared".equals(parts[1]))) {
                throw new IllegalArgumentException("Digite diamond/diamante ou emerald/esmeralda "
                    + "para o gerador central.");
            }
            ResourceType resource = parseGeneratorResource(parts[0]);
            if (resource != ResourceType.DIAMOND && resource != ResourceType.EMERALD) {
                throw new IllegalArgumentException("Escolha diamond/diamante ou emerald/esmeralda "
                    + "para geradores centrais.");
            }
            String teamId = null;
            draft.generatorResource = resource;
            draft.generatorTeam = teamId;
            draft.stage = Stage.GENERATOR_POS1;
        }
        if (undo != null) {
            undo.afterSnapshot = arenas.snapshot(arenas.getArena(draft.arenaId));
            pushUndo(draft, undo);
        }
        save(draft);
        prompt(player, draft);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Draft draft = active.get(event.getPlayer().getUniqueId());
        if (draft == null || !isTool(event.getPlayer().getItemInHand(), draft)) return;
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            openWizardMenu(event.getPlayer(), draft);
            return;
        }
        try {
            handleTool(event.getPlayer(), draft, event);
        } catch (IllegalArgumentException | IllegalStateException error) {
            event.getPlayer().sendMessage(ChatColor.RED + "Etapa nao concluida: " + error.getMessage());
        } catch (IOException error) {
            event.getPlayer().sendMessage(ChatColor.RED + "Falha ao salvar a etapa do assistente.");
            plugin.getLogger().log(Level.SEVERE, "Falha na ferramenta do assistente " + draft.arenaId + ".", error);
        }
    }

    private void handleTool(Player player, Draft draft, PlayerInteractEvent event) throws IOException {
        ArenaDefinition arena = arenas.getArena(draft.arenaId);
        requireEditOwner(player, arena);
        String arenaSnapshot = arenas.snapshot(arena);
        UndoRecord undo = new UndoRecord(player.getWorld().getName(), (byte) 0, arenaSnapshot, draft);
        boolean arenaChanged = false;
        ResourceType centerGeneratorHologram = null;
        Location centerGeneratorLocation = null;
        org.bukkit.Location spawnExit = null;
        Action action = event.getAction();
        Block clicked = event.getClickedBlock();
        if (draft.stage == Stage.BED) {
            if (action != Action.LEFT_CLICK_BLOCK || clicked == null
                    || clicked.getType() != Material.BED_BLOCK) {
                throw new IllegalArgumentException("Clique com o botao esquerdo em uma cama.");
            }
            TeamDefinition team = currentTeam(arena, draft);
            arenas.setPosition(arena, "bed", team.getId(),
                editing.canonicalPosition(arena, clicked.getLocation()));
            arenaChanged = true;
            draft.stage = Stage.SPAWN;
        } else if (draft.stage == Stage.SPAWN || draft.stage == Stage.RESPAWN
                || draft.stage == Stage.ITEM_SHOP
                || draft.stage == Stage.UPGRADE_SHOP) {
            if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
            TeamDefinition team = currentTeam(arena, draft);
            if (draft.stage == Stage.SPAWN) {
                int centerX = floor(player.getLocation().getX());
                int centerZ = floor(player.getLocation().getZ());
                SpawnPlatformLayout layout = new SpawnPlatformLayout(centerX,
                    floor(player.getLocation().getY()) - 1, centerZ, team.getCapacity() == 1,
                    player.getLocation().getYaw());
                spawnExit = findSpawnExit(player, layout);
                Position selected = editing.canonicalPosition(arena, player.getLocation());
                Position spawn = new Position(selected.getWorld(), selected.getX(), selected.getY(),
                    selected.getZ(), selected.getYaw(), 0.0F);
                arenas.setPosition(arena, "spawn", team.getId(),
                    spawn);
                undo = createSpawnPlatform(player, team, layout, centerX,
                    floor(player.getLocation().getY()) - 1, centerZ, arenaSnapshot, draft);
                try {
                    arenas.setTeamSpawnStructure(arena, team.getId(), undo.toStructure());
                } catch (IOException | RuntimeException error) {
                    restorePlacedBlocks(player, undo);
                    arenas.restoreSnapshot(draft.arenaId, arenaSnapshot);
                    throw error;
                }
                arenaChanged = true;
                draft.stage = Stage.RESPAWN;
            } else if (draft.stage == Stage.RESPAWN) {
                if (!SafeLocationResolver.hasSafeFloor(player.getLocation())) {
                    throw new IllegalArgumentException("O respawn precisa de piso solido e dois blocos livres acima.");
                }
                Position selected = editing.canonicalPosition(arena, player.getLocation());
                arenas.setPosition(arena, "respawn", team.getId(), new Position(selected.getWorld(),
                    selected.getX(), selected.getY(), selected.getZ(), selected.getYaw(), 0.0F));
                arenaChanged = true;
                draft.stage = Stage.ITEM_SHOP;
            } else if (draft.stage == Stage.ITEM_SHOP) {
                arenas.setTeamShopPosition(arena, team.getId(), false,
                    editing.canonicalPosition(arena, player.getLocation()));
                arenaChanged = true;
                draft.stage = Stage.UPGRADE_SHOP;
            } else {
                arenas.setTeamShopPosition(arena, team.getId(), true,
                    editing.canonicalPosition(arena, player.getLocation()));
                arenaChanged = true;
                draft.stage = Stage.GENERATOR_SPEC;
                advanceGeneratorWorkflow(draft, arena);
            }
        } else if (draft.stage == Stage.GENERATOR_POS1 && draft.generatorTeam == null) {
            if (action != Action.RIGHT_CLICK_BLOCK || clicked == null) {
                throw new IllegalArgumentException("Clique com o botao direito no bloco onde o gerador sera criado.");
            }
            if (draft.generatorResource != ResourceType.DIAMOND
                    && draft.generatorResource != ResourceType.EMERALD) {
                throw new IllegalStateException("Selecione diamond/diamante ou emerald/esmeralda "
                    + "no chat antes de marcar o gerador.");
            }
            Location blockLocation = clicked.getLocation();
            Position generatorPosition = editing.canonicalPosition(arena,
                blockLocation.clone().add(0.5D, 1.0D, 0.5D));
            ArenaRegion dropRegion = new ArenaRegion(arena.getSourceWorld(),
                floor(generatorPosition.getX()), floor(generatorPosition.getY()),
                floor(generatorPosition.getZ()), floor(generatorPosition.getX()),
                floor(generatorPosition.getY()), floor(generatorPosition.getZ()));
            ResourceGeneratorDefinition generator = arenas.addGenerator(arena, draft.generatorResource,
                null, generatorPosition, dropRegion);
            centerGeneratorHologram = generator.getResource();
            centerGeneratorLocation = blockLocation.clone().add(0.5D, 0.0D, 0.5D);
            arenaChanged = true;
        } else if (draft.stage == Stage.BOUNDS_POS1 || draft.stage == Stage.BOUNDS_POS2
                || draft.stage == Stage.GENERATOR_POS1 || draft.stage == Stage.GENERATOR_POS2) {
            if (clicked == null) throw new IllegalArgumentException("Clique em um bloco para selecionar a posicao.");
            if (action == Action.LEFT_CLICK_BLOCK) {
                if (draft.stage == Stage.BOUNDS_POS1) {
                    draft.first = editing.canonicalPosition(arena, clicked.getLocation());
                    draft.stage = Stage.BOUNDS_POS2;
                } else if (draft.stage == Stage.GENERATOR_POS1) {
                    draft.first = editing.canonicalPosition(arena, clicked.getLocation());
                    draft.stage = Stage.GENERATOR_POS2;
                } else {
                    throw new IllegalArgumentException("Use o botao direito para marcar POS2.");
                }
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                if (draft.stage == Stage.BOUNDS_POS2) {
                    PositionPair points = new PositionPair(draft.first,
                        editing.canonicalPosition(arena, clicked.getLocation()));
                    ArenaRegion main = new ArenaRegion(arena.getSourceWorld(),
                        floor(points.first.getX()), floor(points.first.getY()), floor(points.first.getZ()),
                        floor(points.second.getX()), floor(points.second.getY()), floor(points.second.getZ()));
                    ArenaRegion bounds = new ArenaRegion(arena.getSourceWorld(),
                        main.getMinimumX(), 0, main.getMinimumZ(), main.getMaximumX(), 255, main.getMaximumZ());
                    arenas.setRegion(arena, ArenaRegionType.MAIN, main);
                    arenas.setBounds(arena, bounds, arena.getCeilingHeight(), arena.getVoidHeight());
                    arenaChanged = true;
                    draft.first = null;
                    draft.stage = Stage.HEIGHTS;
                } else if (draft.stage == Stage.GENERATOR_POS2) {
                    PositionPair points = new PositionPair(draft.first,
                        editing.canonicalPosition(arena, clicked.getLocation()));
                    ArenaRegion selectedBlocks = new ArenaRegion(arena.getSourceWorld(),
                        floor(points.first.getX()), floor(points.first.getY()), floor(points.first.getZ()),
                        floor(points.second.getX()), floor(points.second.getY()), floor(points.second.getZ()));
                    ArenaRegion region = draft.generatorTeam == null ? selectedBlocks
                        : new ArenaRegion(arena.getSourceWorld(), selectedBlocks.getMinimumX(),
                            selectedBlocks.getMinimumY() + 1, selectedBlocks.getMinimumZ(),
                            selectedBlocks.getMaximumX(), selectedBlocks.getMaximumY() + 1,
                            selectedBlocks.getMaximumZ());
                    double x = (region.getMinimumX() + (double) region.getMaximumX() + 1.0D) / 2.0D;
                    double y = (region.getMinimumY() + (double) region.getMaximumY() + 1.0D) / 2.0D;
                    double z = (region.getMinimumZ() + (double) region.getMaximumZ() + 1.0D) / 2.0D;
                    if (draft.generatorTeam != null) {
                        arenas.addTeamGenerators(arena, draft.generatorTeam,
                            new com.starfinplugins.bedwars.domain.arena.Position(arena.getSourceWorld(),
                                x, y, z, 0.0F, 0.0F), region);
                        arenaChanged = true;
                    } else if (!hasGeneratorRegion(arena, draft.generatorResource, null, region)) {
                        arenas.addGenerator(arena, draft.generatorResource, draft.generatorTeam,
                            new com.starfinplugins.bedwars.domain.arena.Position(arena.getSourceWorld(),
                                x, y, z, 0.0F, 0.0F), region);
                        arenaChanged = true;
                    }
                    draft.first = null;
                    draft.generatorResource = null;
                    draft.generatorTeam = null;
                    draft.teamIndex++;
                    draft.stage = draft.teamIndex < draft.teamCount
                        ? Stage.TEAM_NAME : Stage.GENERATOR_SPEC;
                } else {
                    throw new IllegalArgumentException("Use o botao esquerdo para marcar POS1.");
                }
            }
        } else {
            throw new IllegalStateException("Esta etapa aguarda uma resposta no chat.");
        }
        if (arenaChanged) {
            undo.afterSnapshot = arenas.snapshot(arenas.getArena(draft.arenaId));
            pushUndo(draft, undo);
        }
        save(draft);
        if (centerGeneratorHologram != null && centerGeneratorLocation != null) {
            showGeneratorHologram(draft.arenaId, centerGeneratorHologram, centerGeneratorLocation);
            player.sendMessage(ChatColor.GREEN + "Gerador " + generatorResourceName(centerGeneratorHologram)
                + " #" + countGenerators(arena, centerGeneratorHologram, null) + " definido. "
                + "Clique em outro bloco para adicionar mais ou digite diamond/diamante ou "
                + "emerald/esmeralda para trocar o tipo.");
        }
        if (spawnExit != null && !player.teleport(spawnExit)) {
            player.sendMessage(ChatColor.RED
                + "O spawn foi criado, mas o teleporte para fora foi recusado pelo servidor.");
        } else if (spawnExit != null) {
            player.sendMessage(ChatColor.GREEN + "Spawn criado; voce foi teleportado para fora da estrutura.");
        }
        prompt(player, draft);
    }

    private void finish(Player player, Draft draft, ArenaDefinition arena) throws IOException {
        List<String> errors = new ArrayList<String>();
        if (arena.getLobby() == null) errors.add("configure o lobby com /sbw arena set.");
        for (TeamDefinition team : arena.getTeamList()) {
            if (team.getBed() == null || team.getSpawn() == null
                    || team.getItemShop() == null || team.getUpgradeShop() == null) {
                errors.add("faltam cama, spawn ou lojas para " + team.getId());
            }
            if (!hasGenerator(arena, ResourceType.IRON, team.getId())
                    || !hasGenerator(arena, ResourceType.GOLD, team.getId())) {
                errors.add("faltam geradores iron/gold para " + team.getId());
            }
        }
        if (!hasGenerator(arena, ResourceType.DIAMOND, null)
                || !hasGenerator(arena, ResourceType.EMERALD, null)) {
            errors.add("configure geradores diamond e emerald compartilhados");
        }
        errors.addAll(arenas.validate(arena));
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Configuracao incompleta: " + arenas.join(errors));
        }
        active.remove(player.getUniqueId());
        restoreTool(player, draft, false);
        if (!file(draft.arenaId).delete() && file(draft.arenaId).exists()) {
            throw new IOException("Arena configurada, mas o rascunho nao pode ser removido.");
        }
        drafts.remove(draft.arenaId);
        clearGeneratorHolograms(draft.arenaId);
        player.sendMessage(ChatColor.GREEN + "Configuracao guiada concluida. A arena continua desabilitada; "
            + "salve na ordem: /sbw arena validate " + arena.getId()
            + ", /sbw arena edit publish " + arena.getId()
            + " e, somente depois da publicacao, /sbw arena enable " + arena.getId() + ".");
    }

    private boolean hasGenerator(ArenaDefinition arena, ResourceType resource, String teamId) {
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            if (generator.getResource() == resource
                    && (teamId == null ? generator.getTeamId() == null : teamId.equals(generator.getTeamId()))) {
                return true;
            }
        }
        return false;
    }

    private int countGenerators(ArenaDefinition arena, ResourceType resource, String teamId) {
        int count = 0;
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            if (generator.getResource() == resource
                    && (teamId == null ? generator.getTeamId() == null : teamId.equals(generator.getTeamId()))) {
                count++;
            }
        }
        return count;
    }

    private void showGeneratorHologram(String arenaId, ResourceType resource, Location location) {
        Location raisedLocation = location.clone().add(0.0D, 2.5D, 0.0D);
        Material blockType = resource == ResourceType.DIAMOND
            ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK;
        ArmorStand blockStand = spawnHologramStand(raisedLocation);
        blockStand.setVisible(false);
        blockStand.setGravity(false);
        blockStand.setMarker(true);
        blockStand.setBasePlate(false);
        blockStand.setArms(false);
        blockStand.setHelmet(new ItemStack(blockType));
        List<ArmorStand> stands = generatorHolograms.get(arenaId);
        if (stands == null) {
            stands = new ArrayList<ArmorStand>();
            generatorHolograms.put(arenaId, stands);
        }
        stands.add(blockStand);
        blockStand.setRemoveWhenFarAway(false);
        ChatColor resourceColor = resource == ResourceType.DIAMOND ? ChatColor.AQUA : ChatColor.GREEN;
        addGeneratorHologramLine(arenaId, raisedLocation.clone().add(0.0D, 3.1D, 0.0D),
            ChatColor.YELLOW + "EDITOR");
        addGeneratorHologramLine(arenaId, raisedLocation.clone().add(0.0D, 2.5D, 0.0D),
            resourceColor + (resource == ResourceType.DIAMOND ? "Diamante" : "Esmeralda"));
        addGeneratorHologramLine(arenaId, raisedLocation.clone().add(0.0D, 1.9D, 0.0D),
            ChatColor.GOLD + "Ponto central configurado");
    }

    private void addGeneratorHologramLine(String arenaId, Location location, String text) {
        ArmorStand stand = spawnHologramStand(location);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        List<ArmorStand> stands = generatorHolograms.get(arenaId);
        if (stands == null) {
            stands = new ArrayList<ArmorStand>();
            generatorHolograms.put(arenaId, stands);
        }
        stands.add(stand);
        stand.setRemoveWhenFarAway(false);
    }

    private ArmorStand spawnHologramStand(Location location) {
        ArmorStand stand = (ArmorStand) (mobGuard == null
            ? location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND)
            : mobGuard.spawnAuthorized(location, EntityType.ARMOR_STAND));
        if (!stand.isValid()) throw new IllegalStateException("Holograma do editor nao foi criado.");
        return stand;
    }

    private void refreshGeneratorHolograms(String arenaId, org.bukkit.World world, ArenaDefinition arena) {
        clearGeneratorHolograms(arenaId);
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            if (generator.getTeamId() != null
                    || (generator.getResource() != ResourceType.DIAMOND
                        && generator.getResource() != ResourceType.EMERALD)) continue;
            Location location = arenas.toLocation(generator.getPosition(), world)
                .subtract(0.0D, 1.0D, 0.0D);
            showGeneratorHologram(arenaId, generator.getResource(), location);
        }
    }

    private void clearGeneratorHolograms(String arenaId) {
        List<ArmorStand> stands = generatorHolograms.remove(arenaId);
        if (stands == null) return;
        for (ArmorStand stand : stands) {
            if (mobGuard != null) mobGuard.forget(stand);
            if (stand != null && stand.isValid()) stand.remove();
        }
    }

    public void clearEditorHolograms(String arenaId) {
        clearGeneratorHolograms(arenaId);
    }

    private boolean hasGeneratorRegion(ArenaDefinition arena, ResourceType resource, String teamId,
                                       ArenaRegion region) {
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            ArenaRegion existing = generator.getRegion();
            if (generator.getResource() == resource
                    && (teamId == null ? generator.getTeamId() == null : teamId.equals(generator.getTeamId()))
                    && existing != null && existing.getWorld().equals(region.getWorld())
                    && existing.getMinimumX() == region.getMinimumX()
                    && existing.getMinimumY() == region.getMinimumY()
                    && existing.getMinimumZ() == region.getMinimumZ()
                    && existing.getMaximumX() == region.getMaximumX()
                    && existing.getMaximumY() == region.getMaximumY()
                    && existing.getMaximumZ() == region.getMaximumZ()) return true;
        }
        return false;
    }

    private UndoRecord createSpawnPlatform(Player player, TeamDefinition team, SpawnPlatformLayout layout,
                                           int centerX, int centerY, int centerZ,
                                           String arenaSnapshot, Draft draft) {
        byte color = team.getColorData();
        UndoRecord undo = new UndoRecord(player.getWorld().getName(), color, arenaSnapshot, draft);
        for (SpawnPlatformLayout.BlockCoordinate coordinate : layout.getBlocks()) {
            if (coordinate.getPart() == SpawnPlatformLayout.Part.BASE
                    && coordinate.getX() == centerX && coordinate.getY() == centerY
                    && coordinate.getZ() == centerZ) {
                placeSpawnFloor(player, undo, coordinate.getX(), coordinate.getY(),
                    coordinate.getZ(), color);
            } else {
                placeIfAir(player, undo, coordinate.getX(), coordinate.getY(),
                    coordinate.getZ(), color);
            }
        }
        return undo;
    }

    private org.bukkit.Location findSpawnExit(Player player, SpawnPlatformLayout layout) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int distance = 1; distance <= 8; distance++) {
            for (int[] direction : directions) {
                int x = direction[0] > 0 ? layout.getMaximumX() + distance
                    : direction[0] < 0 ? layout.getMinimumX() - distance
                    : (layout.getMinimumX() + layout.getMaximumX()) / 2;
                int z = direction[1] > 0 ? layout.getMaximumZ() + distance
                    : direction[1] < 0 ? layout.getMinimumZ() - distance
                    : (layout.getMinimumZ() + layout.getMaximumZ()) / 2;
                org.bukkit.Location preferred = new org.bukkit.Location(player.getWorld(),
                    x + 0.5D, player.getLocation().getY(), z + 0.5D,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
                org.bukkit.Location safe = SafeLocationResolver.findSurface(preferred, 0);
                if (safe != null) return safe;
            }
        }
        if (player.getAllowFlight()) {
            return new org.bukkit.Location(player.getWorld(), layout.getMaximumX() + 1.5D,
                player.getLocation().getY(), player.getLocation().getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch());
        }
        throw new IllegalStateException("Nao ha um ponto seguro para sair do spawn; escolha um local com piso ao redor.");
    }

    private void placeSpawnFloor(Player player, UndoRecord undo, int x, int y, int z, byte color) {
        if (y < 0 || y > 255 || Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L) return;
        Block block = player.getWorld().getBlockAt(x, y, z);
        undo.blocks.add(new BlockSnapshot(x, y, z, block.getTypeId(), block.getData()));
        undo.placedBlocks.add(new SpawnStructureDefinition.BlockPosition(x, y, z));
        block.setTypeIdAndData(95, color, false);
    }

    private void placeIfAir(Player player, UndoRecord undo, int x, int y, int z, byte color) {
        if (y < 0 || y > 255 || Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L) return;
        Block block = player.getWorld().getBlockAt(x, y, z);
        if (block.getType() != Material.AIR) return;
        undo.blocks.add(new BlockSnapshot(x, y, z, block.getTypeId(), block.getData()));
        undo.placedBlocks.add(new SpawnStructureDefinition.BlockPosition(x, y, z));
        block.setTypeIdAndData(95, color, false);
    }

    private void restorePlacedBlocks(Player player, UndoRecord undo) {
        for (BlockSnapshot snapshot : undo.blocks) {
            Block block = player.getWorld().getBlockAt(snapshot.x, snapshot.y, snapshot.z);
            if (block.getTypeId() == 95 && block.getData() == undo.placedData) {
                block.setTypeIdAndData(snapshot.typeId, snapshot.data, false);
            }
        }
    }

    private void pushUndo(Draft draft, UndoRecord undo) {
        draft.undo.add(undo);
        if (draft.undo.size() > 64) draft.undo.remove(0);
    }

    public int undo(Player player) throws IOException {
        Draft draft = active.get(player.getUniqueId());
        if (draft == null) throw new IllegalStateException("Inicie ou retome o assistente antes de desfazer.");
        requireEditOwner(player, arenas.getArena(draft.arenaId));
        if (draft.undo.isEmpty()) throw new IllegalStateException("Nao ha acao do assistente para desfazer.");
        int undoIndex = draft.undo.size() - 1;
        UndoRecord record = draft.undo.get(undoIndex);
        ArenaDefinition currentArena = arenas.getArena(draft.arenaId);
        if (currentArena == null) throw new IllegalStateException("A arena do assistente nao existe mais.");
        if (!record.afterSnapshot.equals(arenas.snapshot(currentArena))) {
            draft.undo.remove(undoIndex);
            try {
                save(draft);
            } catch (IOException error) {
                pushUndo(draft, record);
                throw error;
            }
            throw new IllegalStateException("A arena mudou depois desta etapa; o Undo foi cancelado para "
                + "preservar configuracoes posteriores. O registro antigo foi descartado.");
        }
        draft.undo.remove(undoIndex);
        org.bukkit.World world = plugin.getServer().getWorld(record.world);
        int restored = 0;
        int changedSincePlacement = 0;
        if (world != null && world.equals(player.getWorld())) {
            for (BlockSnapshot snapshot : record.blocks) {
                Block block = world.getBlockAt(snapshot.x, snapshot.y, snapshot.z);
                if (block.getTypeId() == 95 && block.getData() == record.placedData) {
                    block.setTypeIdAndData(snapshot.typeId, snapshot.data, false);
                    restored++;
                } else {
                    changedSincePlacement++;
                }
            }
        } else {
            changedSincePlacement = record.blocks.size();
        }
        try {
            arenas.restoreSnapshot(draft.arenaId, record.arenaSnapshot);
        } catch (IOException error) {
            draft.undo.add(record);
            throw error;
        }
        refreshGeneratorHolograms(draft.arenaId, player.getWorld(), currentArena);
        draft.stage = record.beforeStage;
        draft.teamIndex = record.beforeTeamIndex;
        draft.first = record.beforeFirst;
        draft.generatorResource = record.beforeGeneratorResource;
        draft.generatorTeam = record.beforeGeneratorTeam;
        save(draft);
        player.sendMessage(ChatColor.GREEN + "Ultima acao do assistente desfeita"
            + (record.blocks.isEmpty() ? "." : ": " + restored + " blocos restaurados")
            + (changedSincePlacement == 0 ? "." : "; " + changedSincePlacement
                + " blocos preservados porque foram alterados depois."));
        prompt(player, draft);
        return restored;
    }

    public void sendStatus(Player player) {
        Draft draft = active.get(player.getUniqueId());
        if (draft == null) {
            Draft savedDraft = draftForPlayer(player);
            if (savedDraft != null) {
                player.sendMessage(ChatColor.YELLOW + "Setup " + savedDraft.arenaId
                    + " pausado na etapa " + stageDescription(savedDraft.stage) + ".");
                player.sendMessage(ChatColor.YELLOW + "Proximo passo real: /sbw setup resume "
                    + savedDraft.arenaId + " para continuar; para reiniciar, /sbw setup cancel "
                    + savedDraft.arenaId + " e depois escolha o modo.");
                return;
            }
            SetupSelection selection = selections.get(player.getUniqueId());
            if (selection != null) {
                player.sendMessage(ChatColor.YELLOW + "Configuracao inicial: modo "
                    + (selection.mode == SetupMode.SOLO ? "Solo" : "Equipe")
                    + (selection.mode == SetupMode.TEAM && selection.teamCapacity > 0
                        ? ", " + selection.teamCapacity + " jogadores por equipe" : "")
                    + ". Proxima etapa: "
                    + (!hasArenaBounds(arenas.getArena(selection.arenaId)) ? boundsSetupInstruction()
                        : selection.mode == SetupMode.TEAM && selection.teamCapacity == 0
                        ? "/sbw setup team-size <2.." + maximumTeamSize() + ">"
                        : "/sbw create teams <quantidade>") + ".");
                return;
            }
            ArenaDefinition currentArena = editingArena(player);
            player.sendMessage(ChatColor.YELLOW
                + "Nenhum setup salvo nesta copia. Proximo passo: "
                + (currentArena == null ? "entre na copia de edicao da arena"
                    : isFreshSetup(currentArena) && hasArenaBounds(currentArena)
                        ? "/sbw setup mode <solo|equipe>"
                        : isFreshSetup(currentArena) ? boundsSetupInstruction()
                        : "/sbw arena validate " + currentArena.getId()) + ".");
            return;
        }
        ArenaDefinition arena = arenas.getArena(draft.arenaId);
        synchronizeConfiguredBounds(draft, arena, player);
        try {
            normalizeCompletedCentralGeneratorStage(draft, arena);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE,
                "Nao foi possivel atualizar a etapa dos geradores da arena " + draft.arenaId + ".", error);
            player.sendMessage(ChatColor.RED
                + "Geradores completos, mas nao foi possivel salvar a proxima etapa do assistente.");
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Setup " + draft.arenaId + ": equipe "
            + Math.min(draft.teamIndex + 1, draft.teamCount) + "/" + draft.teamCount
            + "; etapa " + stageDescription(draft.stage)
            + "; acoes de Undo: " + draft.undo.size() + ".");
        prompt(player, draft);
    }

    public void sendNext(Player player) {
        Draft draft = active.get(player.getUniqueId());
        if (draft == null) {
            sendStatus(player);
            return;
        }
        synchronizeConfiguredBounds(draft, arenas.getArena(draft.arenaId), player);
        try {
            normalizeCompletedCentralGeneratorStage(draft, arenas.getArena(draft.arenaId));
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE,
                "Nao foi possivel atualizar a etapa dos geradores da arena " + draft.arenaId + ".", error);
            player.sendMessage(ChatColor.RED
                + "Geradores completos, mas nao foi possivel salvar a proxima etapa do assistente.");
            return;
        }
        prompt(player, draft);
    }

    private void normalizeCompletedCentralGeneratorStage(Draft draft, ArenaDefinition arena)
            throws IOException {
        if (draft.stage != Stage.GENERATOR_POS1 || draft.generatorTeam != null
                || draft.teamIndex < draft.teamCount || arena.getLobby() == null
                || !"fim".equals(nextGeneratorInstruction(arena, draft))) return;
        draft.stage = Stage.GENERATOR_SPEC;
        draft.generatorResource = null;
        draft.first = null;
        save(draft);
    }

    private Draft draftForPlayer(Player player) {
        for (Draft draft : drafts.values()) {
            ArenaDefinition arena = arenas.getArena(draft.arenaId);
            if (draft.owner.equals(player.getUniqueId()) && arena != null
                    && editing.isEditWorld(arena, player.getWorld())
                    && editing.isEditOwner(player, arena)) return draft;
        }
        return null;
    }

    private ArenaDefinition editingArena(Player player) {
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (editing.isEditWorld(arena, player.getWorld())
                    && editing.isEditOwner(player, arena)) return arena;
        }
        return null;
    }

    private void synchronizeConfiguredBounds(Draft draft, ArenaDefinition arena, Player player) {
        if (arena == null || arena.getRegion(ArenaRegionType.MAIN) == null || arena.getBounds() == null) return;
        boolean configuredBeforeWizard = draft.stage == Stage.BOUNDS_POS1 || draft.stage == Stage.BOUNDS_POS2;
        boolean automaticBoundsChangedDuringWizard = draft.stage == Stage.HEIGHTS
            && arena.getBoundsMode() == com.starfinplugins.bedwars.domain.arena.ArenaBoundsMode.AUTOMATIC;
        if (configuredBeforeWizard || automaticBoundsChangedDuringWizard) {
            Stage previousStage = draft.stage;
            com.starfinplugins.bedwars.domain.arena.Position previousFirst = draft.first;
            draft.stage = Stage.GENERATOR_SPEC;
            draft.first = null;
            try {
                save(draft);
            } catch (IOException error) {
                draft.stage = previousStage;
                draft.first = previousFirst;
                plugin.getLogger().log(Level.SEVERE,
                    "Nao foi possivel atualizar a etapa do setup " + draft.arenaId + ".", error);
                player.sendMessage(ChatColor.RED
                    + "Os limites foram configurados, mas nao foi possivel salvar o proximo passo do assistente.");
            }
        }
    }

    static ResourceType parseGeneratorResource(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("iron".equals(normalized) || "ferro".equals(normalized)) return ResourceType.IRON;
        if ("gold".equals(normalized) || "ouro".equals(normalized)) return ResourceType.GOLD;
        if ("diamond".equals(normalized) || "diamante".equals(normalized)) return ResourceType.DIAMOND;
        if ("emerald".equals(normalized) || "esmeralda".equals(normalized)) return ResourceType.EMERALD;
        throw new IllegalArgumentException("Recurso invalido: " + value
            + ". Para geradores centrais, use diamond/diamante ou emerald/esmeralda.");
    }

    private static String generatorResourceName(ResourceType resource) {
        if (resource == ResourceType.DIAMOND) return "diamante";
        if (resource == ResourceType.EMERALD) return "esmeralda";
        return resource.name().toLowerCase(Locale.ROOT);
    }

    private void advanceGeneratorWorkflow(Draft draft, ArenaDefinition arena) {
        if (draft.teamIndex >= draft.teamCount) {
            draft.generatorResource = null;
            draft.generatorTeam = null;
            draft.first = null;
            draft.stage = Stage.GENERATOR_SPEC;
            return;
        }
        TeamDefinition team = currentTeam(arena, draft);
        if (!hasGenerator(arena, ResourceType.IRON, team.getId())
                || !hasGenerator(arena, ResourceType.GOLD, team.getId())
                || !hasGenerator(arena, ResourceType.EMERALD, team.getId())) {
            draft.generatorResource = null;
            draft.generatorTeam = team.getId();
            draft.first = null;
            draft.stage = Stage.GENERATOR_POS1;
            return;
        }
        draft.teamIndex++;
        draft.generatorResource = null;
        draft.generatorTeam = null;
        draft.first = null;
        draft.stage = draft.teamIndex < draft.teamCount ? Stage.TEAM_NAME : Stage.GENERATOR_SPEC;
    }

    private String nextGeneratorInstruction(ArenaDefinition arena, Draft draft) {
        for (TeamDefinition team : arena.getTeamList()) {
            if (!hasGenerator(arena, ResourceType.IRON, team.getId())
                    || !hasGenerator(arena, ResourceType.GOLD, team.getId())
                    || !hasGenerator(arena, ResourceType.EMERALD, team.getId())) {
                return "equipe " + team.getId();
            }
        }
        if (!hasGenerator(arena, ResourceType.DIAMOND, null)) return "diamante central";
        if (!hasGenerator(arena, ResourceType.EMERALD, null)) return "esmeralda central";
        return "fim";
    }

    private boolean hasArenaBounds(ArenaDefinition arena) {
        return arena != null && arena.getRegion(ArenaRegionType.MAIN) != null
            && arena.getBounds() != null;
    }

    private void requireArenaBounds(ArenaDefinition arena) {
        if (!hasArenaBounds(arena)) {
            throw new IllegalStateException("Configure e confirme POS1/POS2 da arena antes de prosseguir: "
                + boundsSetupInstruction() + ".");
        }
    }

    private String boundsSetupInstruction() {
        return "configure os limites: automatico com /sbw setarena center here ou manual com "
            + "/sbw setarena pos1 e /sbw setarena pos2; depois use /sbw setarena preview "
            + "e /sbw setarena confirm";
    }

    private String stageDescription(Stage stage) {
        switch (stage) {
            case TEAM_NAME: return "nome da equipe";
            case TEAM_COLOR: return "cor da equipe";
            case BED: return "cama";
            case SPAWN: return "spawn e plataforma";
            case RESPAWN: return "respawn da equipe";
            case ITEM_SHOP: return "loja de itens";
            case UPGRADE_SHOP: return "loja de melhorias";
            case BOUNDS_POS1: return "POS1 da arena";
            case BOUNDS_POS2: return "POS2 da arena";
            case HEIGHTS: return "altura do teto e void";
            case GENERATOR_SPEC: return "configuracao dos geradores";
            case GENERATOR_POS1: return "POS1 do gerador";
            case GENERATOR_POS2: return "POS2 do gerador";
            default: throw new IllegalStateException("Etapa desconhecida.");
        }
    }

    private void activate(Player player, Draft draft) throws IOException {
        ItemStack existing = player.getInventory().getItem(8);
        draft.originalSlot = existing == null ? null : existing.clone();
        draft.originalContents = cloneItems(player.getInventory().getContents());
        draft.originalArmor = cloneItems(player.getInventory().getArmorContents());
        draft.originalHeldSlot = player.getInventory().getHeldItemSlot();
        draft.toolEquipped = true;
        try {
            save(draft);
        } catch (IOException error) {
            draft.toolEquipped = false;
            draft.originalContents = null;
            draft.originalArmor = null;
            throw error;
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItem(4, tool(draft));
        player.getInventory().setHeldItemSlot(4);
        player.updateInventory();
        active.put(player.getUniqueId(), draft);
    }

    private void restoreTool(Player player, Draft draft, boolean persist) {
        if (active.get(player.getUniqueId()) == draft) active.remove(player.getUniqueId());
        if (draft.toolEquipped) {
            restoreInventory(player, draft);
            player.updateInventory();
            draft.originalSlot = null;
            draft.originalContents = null;
            draft.originalArmor = null;
            draft.toolEquipped = false;
        }
        if (persist) {
            try {
                save(draft);
            } catch (IOException error) {
                plugin.getLogger().log(Level.SEVERE, "Nao foi possivel salvar o rascunho " + draft.arenaId, error);
            }
        }
    }

    private void onJoinRestore(Player player, Draft draft) {
        if (!draft.toolEquipped) return;
        restoreInventory(player, draft);
        player.updateInventory();
        draft.originalSlot = null;
        draft.originalContents = null;
        draft.originalArmor = null;
        draft.toolEquipped = false;
        try {
            save(draft);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Nao foi possivel restaurar o inventario de " + player.getName(), error);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Draft draft = active.remove(event.getPlayer().getUniqueId());
        if (draft != null) restoreTool(event.getPlayer(), draft, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Draft draft : drafts.values()) {
            if (draft.owner.equals(event.getPlayer().getUniqueId()) && draft.toolEquipped) {
                onJoinRestore(event.getPlayer(), draft);
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isWizardTool(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (active.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            if (event.getView().getTopInventory().getHolder() instanceof WizardMenu
                    && event.getWhoClicked() instanceof Player) {
                final Player player = (Player) event.getWhoClicked();
                final Draft draft = active.get(player.getUniqueId());
                final int slot = event.getRawSlot();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override public void run() { handleWizardMenu(player, draft, slot); }
                });
            }
            return;
        }
        if (isWizardTool(event.getCurrentItem()) || isWizardTool(event.getCursor())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (active.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (isWizardTool(event.getOldCursor())) event.setCancelled(true);
    }

    private void restoreInventory(Player player, Draft draft) {
        if (draft.originalContents != null) {
            player.getInventory().setContents(cloneItems(draft.originalContents));
            player.getInventory().setArmorContents(cloneItems(draft.originalArmor));
            player.getInventory().setHeldItemSlot(draft.originalHeldSlot);
        } else {
            player.getInventory().setItem(8,
                draft.originalSlot == null ? null : draft.originalSlot.clone());
        }
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    private void openWizardMenu(Player player, Draft draft) {
        WizardMenu menu = new WizardMenu();
        menu.inventory.setItem(11, menuItem(Material.BOOK, "Etapa: " + stageDescription(draft.stage)));
        if (draft.stage == Stage.SPAWN || draft.stage == Stage.RESPAWN
                || draft.stage == Stage.ITEM_SHOP || draft.stage == Stage.UPGRADE_SHOP) {
            menu.inventory.setItem(13, menuItem(Material.EMERALD,
                "Confirmar minha posicao atual"));
        } else {
            menu.inventory.setItem(13, menuItem(Material.COMPASS, "Instrucoes desta etapa"));
        }
        if (!draft.undo.isEmpty()) menu.inventory.setItem(19, menuItem(Material.ARROW, "Desfazer"));
        menu.inventory.setItem(21, menuItem(Material.WATCH, "Pausar setup"));
        menu.inventory.setItem(23, menuItem(Material.BARRIER, "Sair da edicao"));
        player.openInventory(menu.inventory);
    }

    private ItemStack menuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }

    private void handleWizardMenu(Player player, Draft draft, int slot) {
        if (!player.isOnline() || active.get(player.getUniqueId()) != draft) return;
        player.closeInventory();
        try {
            if (slot == 11 || slot == 13 && draft.stage != Stage.SPAWN
                    && draft.stage != Stage.RESPAWN && draft.stage != Stage.ITEM_SHOP
                    && draft.stage != Stage.UPGRADE_SHOP) {
                sendNext(player);
            } else if (slot == 13) {
                handleTool(player, draft, new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                    player.getItemInHand(), null, org.bukkit.block.BlockFace.SELF));
            } else if (slot == 19 && !draft.undo.isEmpty()) {
                undo(player);
                sendNext(player);
            } else if (slot == 21) {
                pause(player);
            } else if (slot == 23) {
                pause(player);
                player.performCommand("sbw arena exit");
            }
        } catch (Exception error) {
            player.sendMessage(ChatColor.RED + "Acao do setup falhou: " + error.getMessage());
            plugin.getLogger().log(Level.WARNING, "Acao do menu do setup falhou.", error);
        }
    }

    private static final class WizardMenu implements InventoryHolder {
        private final Inventory inventory = Bukkit.createInventory(this, 27, "Setup da arena");
        @Override public Inventory getInventory() { return inventory; }
    }

    private boolean isWizardTool(ItemStack item) {
        for (Draft draft : drafts.values()) {
            if (isTool(item, draft)) return true;
        }
        return false;
    }

    private void requireEditOwner(Player player, ArenaDefinition arena) {
        if (player == null || arena == null || !editing.isEditWorld(arena, player.getWorld())
                || !editing.isEditOwner(player, arena)) {
            throw new IllegalStateException("Inicie sua sessao de edicao isolada antes do assistente.");
        }
    }

    private TeamDefinition currentTeam(ArenaDefinition arena, Draft draft) {
        return arena.getTeams().get("team" + (draft.teamIndex + 1));
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.replaceAll("(?i)[&\u00a7][0-9a-fk-or]", "").trim();
        if (name.isEmpty() || name.length() > 32 || name.matches(".*[\\r\\n\\p{Cntrl}].*")) {
            throw new IllegalArgumentException("Nome deve conter entre 1 e 32 caracteres.");
        }
        return name;
    }

    private void prompt(Player player, Draft draft) {
        TeamDefinition team = draft.teamIndex < draft.teamCount
            ? currentTeam(arenas.getArena(draft.arenaId), draft) : null;
        switch (draft.stage) {
            case TEAM_NAME:
                player.sendMessage(ChatColor.YELLOW + "Digite o nome da equipe " + (draft.teamIndex + 1) + ".");
                break;
            case TEAM_COLOR:
                sendColorOptions(player, draft);
                break;
            case BED:
                player.sendMessage(ChatColor.YELLOW + "Clique com o botao esquerdo na cama de " + team.getDisplayName() + ".");
                break;
            case SPAWN:
                player.sendMessage(ChatColor.YELLOW + "Fique no spawn de " + team.getDisplayName()
                    + " e clique com o botao direito. Sera criada a sala de spawn "
                    + (team.getCapacity() == 1 ? "Solo 3x3 (interior 1x1)" : "Equipe 5x5 (interior 3x3)")
                    + ", com paredes de 3 blocos e teto. Ar e o piso central sob voce serao substituidos.");
                break;
            case RESPAWN:
                player.sendMessage(ChatColor.YELLOW + "Fique no ponto de respawn de "
                    + team.getDisplayName() + ", fora da sala inicial, sobre piso solido, e clique com o botao direito.");
                break;
            case ITEM_SHOP:
                player.sendMessage(ChatColor.YELLOW + "Fique no local da loja de itens de "
                    + team.getDisplayName() + " e clique com o botao direito.");
                break;
            case UPGRADE_SHOP:
                player.sendMessage(ChatColor.YELLOW + "Fique no local da loja de melhorias de "
                    + team.getDisplayName() + " e clique com o botao direito.");
                break;
            case BOUNDS_POS1:
                player.sendMessage(ChatColor.YELLOW + "Clique com o botao esquerdo no primeiro canto da arena.");
                break;
            case BOUNDS_POS2:
                player.sendMessage(ChatColor.YELLOW + "Clique com o botao direito no canto oposto da arena.");
                break;
            case HEIGHTS:
                player.sendMessage(ChatColor.YELLOW + "Digite as alturas do teto e void (ex.: 120 0).");
                break;
            case GENERATOR_SPEC:
                String nextGenerator = nextGeneratorInstruction(arenas.getArena(draft.arenaId), draft);
                if (draft.teamIndex < draft.teamCount) {
                    player.sendMessage(ChatColor.YELLOW + "Antes de continuar os times, configure os geradores "
                        + "de " + currentTeam(arenas.getArena(draft.arenaId), draft).getDisplayName()
                        + " usando a varinha.");
                    advanceGeneratorWorkflow(draft, arenas.getArena(draft.arenaId));
                    prompt(player, draft);
                } else if (nextGenerator.contains(" ")) {
                    player.sendMessage(ChatColor.YELLOW + "Gerador central pendente: "
                        + nextGenerator + ".");
                    player.sendMessage(ChatColor.YELLOW + "Digite diamond/diamante ou emerald/esmeralda e "
                        + "clique com o botao direito em cada bloco onde deseja esse gerador. Cada clique cria um ponto "
                        + "e mostra seu holograma; voce pode cadastrar varios do mesmo tipo.");
                } else {
                    if (arenas.getArena(draft.arenaId).getLobby() == null) {
                        player.sendMessage(ChatColor.GREEN + "Geradores configurados.");
                        player.sendMessage(ChatColor.YELLOW + "Proximo comando: /sbw arena set "
                            + draft.arenaId + " lobby; depois digite fim para concluir. Tambem pode "
                            + "digitar diamond/diamante ou emerald/esmeralda para adicionar outros pontos.");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Geradores centrais configurados.");
                        player.sendMessage(ChatColor.YELLOW + "Digite fim para concluir ou diamond/diamante "
                            + "ou emerald/esmeralda "
                            + "para adicionar outros pontos; se houver pendencias, use /sbw arena validate "
                            + draft.arenaId + ".");
                    }
                }
                break;
            case GENERATOR_POS1:
                if (draft.generatorTeam == null) {
                    player.sendMessage(ChatColor.YELLOW + "Gerador central "
                        + generatorResourceName(draft.generatorResource)
                        + ": segure a varinha e clique com o botao direito no bloco desejado.");
                    player.sendMessage(ChatColor.YELLOW + "Clique em varios blocos para criar varios pontos; "
                        + "digite diamond/diamante ou emerald/esmeralda para trocar o tipo. "
                        + "Cada ponto exibe um holograma. "
                        + "Digite fim para concluir.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Gerador de Iron, Gold e Esmeralda do time "
                        + currentTeam(arenas.getArena(draft.arenaId), draft).getDisplayName()
                        + ": segure a varinha do assistente e clique com o botao esquerdo em POS1.");
                    player.sendMessage(ChatColor.YELLOW + "Depois clique com o botao direito em POS2.");
                }
                break;
            case GENERATOR_POS2:
                player.sendMessage(ChatColor.YELLOW + "Segure a varinha do assistente e clique com o botao "
                    + "direito em POS2 da regiao do gerador.");
                break;
            default:
                throw new IllegalStateException("Etapa desconhecida.");
        }
    }

    private void sendColorOptions(Player player, Draft draft) {
        TeamDefinition team = currentTeam(arenas.getArena(draft.arenaId), draft);
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Qual cor voce quer aplicar ao time "
            + team.getDisplayName() + "? Clique em uma opcao:");
        SetupTeamColor[] colors = SetupTeamColor.values();
        for (int row = 0; row < colors.length / 4; row++) {
            TextComponent[] options = new TextComponent[7];
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                SetupTeamColor color = colors[index];
                TextComponent option = new TextComponent("[" + color.getDisplayName() + "]");
                option.setColor(net.md_5.bungee.api.ChatColor.getByChar(color.getChatCode().charAt(0)));
                option.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/sbw setup color " + color.name()));
                option.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("Aplicar cor ")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(color.getDisplayName()).color(net.md_5.bungee.api.ChatColor.getByChar(
                            color.getChatCode().charAt(0))).create()));
                options[column * 2] = option;
                if (column < 3) options[column * 2 + 1] = new TextComponent("  ");
            }
            player.spigot().sendMessage(options);
        }
    }

    private ItemStack tool(Draft draft) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TOOL_NAME);
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Uso exclusivo do assistente de arena");
        lore.add(ChatColor.DARK_GRAY + "setup-session:" + draft.sessionId);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isTool(ItemStack item, Draft draft) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
            && TOOL_NAME.equals(item.getItemMeta().getDisplayName())
            && item.getItemMeta().hasLore()
            && item.getItemMeta().getLore().contains(ChatColor.GRAY + "Uso exclusivo do assistente de arena")
            && item.getItemMeta().getLore().contains(ChatColor.DARK_GRAY + "setup-session:" + draft.sessionId);
    }

    private int floor(double value) { return (int) Math.floor(value); }

    private File file(String arenaId) { return new File(directory, arenaId + ".yml"); }

    private void save(Draft draft) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("arena", draft.arenaId);
        yaml.set("owner", draft.owner.toString());
        yaml.set("session-id", draft.sessionId.toString());
        yaml.set("mode", draft.mode);
        yaml.set("team-count", draft.teamCount);
        yaml.set("team-capacity", draft.teamCapacity);
        yaml.set("team-index", draft.teamIndex);
        yaml.set("stage", draft.stage.name());
        yaml.set("generator-resource", draft.generatorResource == null ? null : draft.generatorResource.name());
        yaml.set("generator-team", draft.generatorTeam);
        yaml.set("first.world", draft.first == null ? null : draft.first.getWorld());
        if (draft.first != null) {
            yaml.set("first.x", draft.first.getX());
            yaml.set("first.y", draft.first.getY());
            yaml.set("first.z", draft.first.getZ());
            yaml.set("first.yaw", draft.first.getYaw());
            yaml.set("first.pitch", draft.first.getPitch());
        }
        yaml.set("inventory.original-slot", draft.originalSlot);
        yaml.set("inventory.contents", draft.originalContents == null ? null
            : java.util.Arrays.asList(draft.originalContents));
        yaml.set("inventory.armor", draft.originalArmor == null ? null
            : java.util.Arrays.asList(draft.originalArmor));
        yaml.set("inventory.held-slot", draft.originalHeldSlot);
        yaml.set("inventory.tool-equipped", draft.toolEquipped);
        yaml.set("undo-count", draft.undo.size());
        for (int action = 0; action < draft.undo.size(); action++) {
            UndoRecord record = draft.undo.get(action);
            String path = "undo." + action + ".";
            yaml.set(path + "world", record.world);
            yaml.set(path + "placed-data", record.placedData);
            yaml.set(path + "arena-snapshot", record.arenaSnapshot);
            yaml.set(path + "after-snapshot", record.afterSnapshot);
            yaml.set(path + "stage", record.beforeStage.name());
            yaml.set(path + "team-index", record.beforeTeamIndex);
            yaml.set(path + "generator-resource", record.beforeGeneratorResource == null
                ? null : record.beforeGeneratorResource.name());
            yaml.set(path + "generator-team", record.beforeGeneratorTeam);
            yaml.set(path + "first.world", record.beforeFirst == null ? null : record.beforeFirst.getWorld());
            if (record.beforeFirst != null) {
                yaml.set(path + "first.x", record.beforeFirst.getX());
                yaml.set(path + "first.y", record.beforeFirst.getY());
                yaml.set(path + "first.z", record.beforeFirst.getZ());
                yaml.set(path + "first.yaw", record.beforeFirst.getYaw());
                yaml.set(path + "first.pitch", record.beforeFirst.getPitch());
            }
            yaml.set(path + "block-count", record.blocks.size());
            for (int index = 0; index < record.blocks.size(); index++) {
                BlockSnapshot block = record.blocks.get(index);
                String blockPath = path + "blocks." + index + ".";
                yaml.set(blockPath + "x", block.x);
                yaml.set(blockPath + "y", block.y);
                yaml.set(blockPath + "z", block.z);
                yaml.set(blockPath + "type", block.typeId);
                yaml.set(blockPath + "data", block.data);
            }
        }
        File target = file(draft.arenaId);
        File temporary = File.createTempFile("wizard-", ".yml.tmp", directory);
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private ItemStack[] readItems(List<?> values, int expectedSize) throws IOException {
        if (values == null) return null;
        if (values.size() != expectedSize) throw new IOException("Snapshot de inventario do setup invalido.");
        ItemStack[] items = new ItemStack[expectedSize];
        for (int index = 0; index < expectedSize; index++) {
            Object value = values.get(index);
            if (value != null && !(value instanceof ItemStack)) {
                throw new IOException("Item invalido no inventario do setup: " + index);
            }
            items[index] = value == null ? null : ((ItemStack) value).clone();
        }
        return items;
    }

    private Draft readDraft(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException("YAML de rascunho invalido", error);
        }
        if (yaml.getInt("version", -1) != 1) throw new IOException("versao de rascunho nao suportada");
        String sessionId = yaml.getString("session-id");
        Draft draft = new Draft(yaml.getString("arena"), UUID.fromString(yaml.getString("owner")),
            yaml.getString("mode"), yaml.getInt("team-count"), yaml.getInt("team-capacity"),
            sessionId == null ? UUID.randomUUID() : UUID.fromString(sessionId));
        ArenaDefinition arena = arenas.getArena(draft.arenaId);
        if (arena == null) throw new IOException("arena do rascunho nao encontrada");
        draft.teamIndex = yaml.getInt("team-index");
        draft.stage = Stage.valueOf(yaml.getString("stage"));
        String resource = yaml.getString("generator-resource");
        draft.generatorResource = resource == null ? null : ResourceType.valueOf(resource);
        draft.generatorTeam = yaml.getString("generator-team");
        if (yaml.isString("first.world")) {
            draft.first = new com.starfinplugins.bedwars.domain.arena.Position(yaml.getString("first.world"),
                yaml.getDouble("first.x"), yaml.getDouble("first.y"), yaml.getDouble("first.z"),
                (float) yaml.getDouble("first.yaw"), (float) yaml.getDouble("first.pitch"));
        }
        Object original = yaml.get("inventory.original-slot");
        draft.originalSlot = original instanceof ItemStack ? ((ItemStack) original).clone() : null;
        draft.originalContents = readItems(yaml.getList("inventory.contents"), 36);
        draft.originalArmor = readItems(yaml.getList("inventory.armor"), 4);
        draft.originalHeldSlot = yaml.getInt("inventory.held-slot", 0);
        draft.toolEquipped = yaml.getBoolean("inventory.tool-equipped", false);
        int undoCount = yaml.getInt("undo-count", 0);
        if (undoCount < 0 || undoCount > 64) throw new IOException("historico de undo invalido");
        for (int action = 0; action < undoCount; action++) {
            String path = "undo." + action + ".";
            String world = yaml.getString(path + "world");
            int blockCount = yaml.getInt(path + "block-count", -1);
            if (world == null || blockCount < 0 || blockCount > 256) {
                throw new IOException("registro de undo invalido");
            }
            UndoRecord record = new UndoRecord(world);
            record.arenaSnapshot = yaml.getString(path + "arena-snapshot");
            if (record.arenaSnapshot == null) throw new IOException("snapshot da acao undo ausente");
            record.afterSnapshot = yaml.getString(path + "after-snapshot");
            if (record.afterSnapshot == null) throw new IOException("snapshot posterior do undo ausente");
            record.beforeStage = Stage.valueOf(yaml.getString(path + "stage"));
            record.beforeTeamIndex = yaml.getInt(path + "team-index", -1);
            String beforeResource = yaml.getString(path + "generator-resource");
            record.beforeGeneratorResource = beforeResource == null ? null : ResourceType.valueOf(beforeResource);
            record.beforeGeneratorTeam = yaml.getString(path + "generator-team");
            if (yaml.isString(path + "first.world")) {
                record.beforeFirst = new com.starfinplugins.bedwars.domain.arena.Position(
                    yaml.getString(path + "first.world"), yaml.getDouble(path + "first.x"),
                    yaml.getDouble(path + "first.y"), yaml.getDouble(path + "first.z"),
                    (float) yaml.getDouble(path + "first.yaw"), (float) yaml.getDouble(path + "first.pitch"));
            }
            if (record.beforeTeamIndex < 0 || record.beforeTeamIndex > draft.teamCount) {
                throw new IOException("estado de equipe do undo invalido");
            }
            int placedData = yaml.getInt(path + "placed-data", -1);
            if (placedData < 0 || placedData > 255) throw new IOException("cor do bloco de undo invalida");
            record.placedData = (byte) placedData;
            for (int index = 0; index < blockCount; index++) {
                String blockPath = path + "blocks." + index + ".";
                int x = yaml.getInt(blockPath + "x", Integer.MIN_VALUE);
                int y = yaml.getInt(blockPath + "y", -1);
                int z = yaml.getInt(blockPath + "z", Integer.MIN_VALUE);
                int type = yaml.getInt(blockPath + "type", -1);
                int data = yaml.getInt(blockPath + "data", -1);
                if (y < 0 || y > 255 || type < 0 || type > 4095 || data < 0 || data > 255) {
                    throw new IOException("bloco de undo invalido");
                }
                record.blocks.add(new BlockSnapshot(x, y, z, type, (byte) data));
                record.placedBlocks.add(new SpawnStructureDefinition.BlockPosition(x, y, z));
            }
            draft.undo.add(record);
        }
        if (draft.teamCount < 2 || draft.teamCount > 16 || draft.teamIndex < 0
                || draft.teamIndex > draft.teamCount) throw new IOException("etapa de equipes invalida");
        if (draft.teamIndex < draft.teamCount && !arena.getTeams().containsKey("team" + (draft.teamIndex + 1))) {
            throw new IOException("equipe atual do rascunho ausente");
        }
        return draft;
    }

    private enum Stage {
        TEAM_NAME, TEAM_COLOR, BED, SPAWN, RESPAWN, ITEM_SHOP, UPGRADE_SHOP,
        BOUNDS_POS1, BOUNDS_POS2, HEIGHTS, GENERATOR_SPEC, GENERATOR_POS1, GENERATOR_POS2
    }

    private static final class Draft {
        private final String arenaId;
        private final UUID owner;
        private final String mode;
        private final UUID sessionId;
        private final int teamCount;
        private final int teamCapacity;
        private int teamIndex;
        private Stage stage = Stage.TEAM_NAME;
        private ResourceType generatorResource;
        private String generatorTeam;
        private com.starfinplugins.bedwars.domain.arena.Position first;
        private ItemStack originalSlot;
        private ItemStack[] originalContents;
        private ItemStack[] originalArmor;
        private int originalHeldSlot;
        private boolean toolEquipped;
        private final List<UndoRecord> undo = new ArrayList<UndoRecord>();

        private Draft(String arenaId, UUID owner, String mode, int teamCount, int teamCapacity) {
            this(arenaId, owner, mode, teamCount, teamCapacity, UUID.randomUUID());
        }

        private Draft(String arenaId, UUID owner, String mode, int teamCount, int teamCapacity,
                      UUID sessionId) {
            this.arenaId = arenaId;
            this.owner = owner;
            this.mode = mode;
            this.teamCount = teamCount;
            this.teamCapacity = teamCapacity;
            this.sessionId = sessionId;
        }
    }

    private enum SetupMode { SOLO, TEAM }

    private static final class SetupSelection {
        private final String arenaId;
        private final SetupMode mode;
        private int teamCapacity;

        private SetupSelection(String arenaId, SetupMode mode) {
            this.arenaId = arenaId;
            this.mode = mode;
        }
    }

    private static final class UndoRecord {
        private final String world;
        private byte placedData;
        private String arenaSnapshot;
        private String afterSnapshot;
        private Stage beforeStage;
        private int beforeTeamIndex;
        private ResourceType beforeGeneratorResource;
        private String beforeGeneratorTeam;
        private com.starfinplugins.bedwars.domain.arena.Position beforeFirst;
        private final List<BlockSnapshot> blocks = new ArrayList<BlockSnapshot>();
        private final List<SpawnStructureDefinition.BlockPosition> placedBlocks =
            new ArrayList<SpawnStructureDefinition.BlockPosition>();
        private UndoRecord(String world, byte placedData, String arenaSnapshot, Draft draft) {
            this.world = world;
            this.placedData = placedData;
            this.arenaSnapshot = arenaSnapshot;
            this.beforeStage = draft.stage;
            this.beforeTeamIndex = draft.teamIndex;
            this.beforeGeneratorResource = draft.generatorResource;
            this.beforeGeneratorTeam = draft.generatorTeam;
            this.beforeFirst = draft.first;
        }
        private UndoRecord(String world) { this.world = world; }

        private SpawnStructureDefinition toStructure() {
            return new SpawnStructureDefinition(95, placedData, placedBlocks);
        }
    }

    private static final class BlockSnapshot {
        private final int x;
        private final int y;
        private final int z;
        private final int typeId;
        private final byte data;
        private BlockSnapshot(int x, int y, int z, int typeId, byte data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.typeId = typeId;
            this.data = data;
        }
    }

    private static final class PositionPair {
        private final com.starfinplugins.bedwars.domain.arena.Position first;
        private final com.starfinplugins.bedwars.domain.arena.Position second;
        private PositionPair(com.starfinplugins.bedwars.domain.arena.Position first,
                             com.starfinplugins.bedwars.domain.arena.Position second) {
            if (first == null || second == null) throw new IllegalArgumentException("Marque POS1 e POS2.");
            this.first = first;
            this.second = second;
        }
    }
}
