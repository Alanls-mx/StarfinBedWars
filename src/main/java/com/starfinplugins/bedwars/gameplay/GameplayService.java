package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.event.MatchCombatDeathEvent;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.MatchEquipmentService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchMode;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.stats.PreferencesOutbox;
import com.starfinplugins.bedwars.stats.StatsProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Selection inventory and match-scoped kit/ability lifecycle on the Bukkit thread. */
public final class GameplayService implements ManagedService, Listener {
    public static final int KIT_HOTBAR_SLOT = 3;
    public static final int ABILITY_HOTBAR_SLOT = 5;
    private static final String KIT_MARKER = ChatColor.BLACK + "SBW:KIT-SELECTOR";
    private static final String ABILITY_MARKER = ChatColor.BLACK + "SBW:ABILITY-SELECTOR";
    private final StarfinBedWarsPlugin plugin;
    private final MatchEngineService matches;
    private final MatchStatisticsService stats;
    private final MatchEquipmentService equipment;
    private final Map<UUID, MatchChoices> active = new HashMap<UUID, MatchChoices>();
    private final Map<UUID, TimedEffect> ownedEffects = new HashMap<UUID, TimedEffect>();
    private GameplaySettings settings;

    public GameplayService(StarfinBedWarsPlugin plugin, MatchEngineService matches,
                           MatchStatisticsService stats, MatchEquipmentService equipment) {
        this.plugin = plugin; this.matches = matches; this.stats = stats; this.equipment = equipment;
    }
    @Override public String getName() { return "kits e habilidades"; }
    @Override public void start() {
        settings = GameplaySettings.load(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    @Override public void stop() {
        HandlerList.unregisterAll(this);
        for (UUID id : new ArrayList<UUID>(ownedEffects.keySet())) clearEffect(id);
        active.clear();
    }
    public GameplaySettings getSettings() { return settings; }
    public void reload() { settings = GameplaySettings.load(plugin); }
    public String selectedKit(UUID player) { return stats.getSelectedKit(player); }
    public String selectedAbility(UUID player) { return stats.getSelectedAbility(player); }
    public long cooldownRemaining(UUID player) {
        GameMatch match = matches.getMatchForPlayer(player);
        MatchChoices choices = match == null ? null : active.get(match.getId());
        AbilityState state = choices == null ? null : choices.abilities.get(player);
        return state == null ? 0 : Math.max(0, (state.nextUseMillis - System.currentTimeMillis() + 999) / 1000);
    }

    @EventHandler(priority = EventPriority.HIGH) public void onLifecycle(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case PLAYER_JOINED:
            case SPAWN_OPENING:
            case COUNTDOWN_CANCELLED:
                for (GameParticipant participant : match.getParticipantList()) {
                    if (event.getType() == MatchLifecycleEvent.Type.PLAYER_JOINED
                            && event.getParticipant() != participant) continue;
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null && participant.getState() == ParticipantState.CONNECTED)
                        installSelectors(player);
                }
                break;
            case STARTED:
                MatchChoices choices = new MatchChoices();
                active.put(match.getId(), choices);
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player == null || participant.getState() != ParticipantState.CONNECTED) continue;
                    removeSelectors(player);
                    GameplaySettings.Kit kit = resolvedKit(player, match);
                    GameplaySettings.Ability ability = resolvedAbility(player, match);
                    choices.kits.put(player.getUniqueId(), kit.id);
                    choices.abilities.put(player.getUniqueId(), new AbilityState(
                        ability == null ? "none" : ability.id));
                    applyKit(player, match, participant, kit, false);
                }
                break;
            case PLAYER_RESPAWNED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    MatchChoices resumed = active.get(match.getId());
                    if (player != null && resumed != null) {
                        GameplaySettings.Kit kit = settings.kits.get(resumed.kits.get(player.getUniqueId()));
                        if (kit != null) applyKit(player, match, event.getParticipant(), kit, true);
                        AbilityState state = resumed.abilities.get(player.getUniqueId());
                        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
                        if (ability != null && ability.effect == GameplaySettings.AbilityEffect.RESISTANCE_ON_RESPAWN)
                            activate(player, match, ability, state);
                    }
                }
                break;
            case PLAYER_RECONNECTED:
                if (event.getParticipant() != null) {
                    Player player = Bukkit.getPlayer(event.getParticipant().getPlayerId());
                    if (player != null && event.getParticipant().getState() == ParticipantState.CONNECTED) {
                        removeSelectors(player);
                        MatchChoices resumed = active.get(match.getId());
                        if (resumed != null && !resumed.kits.containsKey(player.getUniqueId())) {
                            GameplaySettings.Kit kit = resolvedKit(player, match);
                            GameplaySettings.Ability ability = resolvedAbility(player, match);
                            resumed.kits.put(player.getUniqueId(), kit.id);
                            resumed.abilities.put(player.getUniqueId(), new AbilityState(
                                ability == null ? "none" : ability.id));
                            applyKit(player, match, event.getParticipant(), kit, false);
                        }
                        reconcileEffect(player);
                    }
                }
                break;
            default: break;
        }
    }

    /** Cleanup precedes inventory restoration, including the player's original potion effects. */
    @EventHandler(priority = EventPriority.LOWEST) public void onLifecycleCleanup(MatchLifecycleEvent event) {
        GameMatch match = event.getMatch();
        switch (event.getType()) {
            case PLAYER_DIED:
            case PLAYER_ELIMINATED:
                if (event.getParticipant() != null) clearEffect(event.getParticipant().getPlayerId());
                break;
            case PLAYER_LEFT:
                if (event.getParticipant() != null) {
                    UUID id = event.getParticipant().getPlayerId();
                    clearEffect(id);
                    MatchChoices inMatch = active.get(match.getId());
                    if (inMatch != null) { inMatch.kits.remove(id); inMatch.abilities.remove(id); }
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) removeSelectors(player);
                }
                break;
            case ENDED:
            case INSTANCE_REMOVED:
                MatchChoices ended = active.remove(match.getId());
                if (ended != null) for (UUID id : ended.abilities.keySet()) clearEffect(id);
                for (GameParticipant participant : match.getParticipantList()) {
                    Player player = Bukkit.getPlayer(participant.getPlayerId());
                    if (player != null) removeSelectors(player);
                }
                break;
            default: break;
        }
    }

    @EventHandler public void onCombatDeath(MatchCombatDeathEvent event) {
        UUID killer = event.getKillerId();
        if (killer == null) return;
        GameMatch match = matches.getMatchForPlayer(killer);
        if (match == null || !match.getId().equals(event.getMatchId())) return;
        AbilityState state = state(match, killer);
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (ability == null || ability.effect != GameplaySettings.AbilityEffect.SPEED_ON_KILL) return;
        Player player = Bukkit.getPlayer(killer);
        if (player != null) activate(player, match, ability, state);
    }

    @EventHandler(ignoreCancelled = true) public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        Player player = (Player) event.getEntity();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!eligibleInGame(player, match)) return;
        AbilityState state = state(match, player.getUniqueId());
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (ability != null && ability.effect == GameplaySettings.AbilityEffect.FALL_REDUCTION)
            event.setDamage(event.getDamage() * (1.0 - ability.potency));
    }

    public void useAbility(Player player) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!eligibleInGame(player, match)) { say(player, "Habilidade disponivel apenas em partida.",
            "Ability available only in a match.", "Habilidad solo disponible en partida."); return; }
        AbilityState state = state(match, player.getUniqueId());
        GameplaySettings.Ability ability = state == null ? null : settings.abilities.get(state.id);
        if (ability == null || ability.type != GameplaySettings.AbilityType.ACTIVE) {
            say(player, "Nenhuma habilidade ativa selecionada.", "No active ability selected.",
                "No hay habilidad activa seleccionada."); return;
        }
        activate(player, match, ability, state);
    }

    private void activate(Player player, GameMatch match, GameplaySettings.Ability ability,
                          AbilityState state) {
        if (!eligibleInGame(player, match) || state == null) return;
        long now = System.currentTimeMillis();
        if (state.nextUseMillis > now) {
            if (ability.type == GameplaySettings.AbilityType.ACTIVE)
                say(player, "Aguarde " + cooldownRemaining(player.getUniqueId()) + "s para usar novamente.",
                    "Wait " + cooldownRemaining(player.getUniqueId()) + "s before using again.",
                    "Espera " + cooldownRemaining(player.getUniqueId()) + "s para usarla otra vez.");
            return;
        }
        PotionEffectType type = ability.effect == GameplaySettings.AbilityEffect.RESISTANCE_ON_RESPAWN
            ? PotionEffectType.DAMAGE_RESISTANCE : PotionEffectType.SPEED;
        int ticks = ability.durationSeconds * 20;
        PotionEffect previous = player.getActivePotionEffects().stream()
            .filter(effect -> effect.getType().equals(type)).findFirst().orElse(null);
        if (previous != null && previous.getAmplifier() >= 0 && previous.getDuration() >= ticks) return;
        player.addPotionEffect(new PotionEffect(type, ticks, 0), true);
        ownedEffects.put(player.getUniqueId(), new TimedEffect(type, now + ability.durationSeconds * 1000L));
        state.nextUseMillis = now + ability.cooldownSeconds * 1000L;
        if (ability.type == GameplaySettings.AbilityType.ACTIVE)
            say(player, "Habilidade ativada!", "Ability activated!", "Habilidad activada!");
    }
    private void clearEffect(UUID id) {
        TimedEffect owned = ownedEffects.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (owned == null || player == null) return;
        for (PotionEffect activeEffect : player.getActivePotionEffects()) {
            if (activeEffect.getType().equals(owned.type) && activeEffect.getAmplifier() == 0
                    && System.currentTimeMillis() < owned.expiresAt + 2000L)
                player.removePotionEffect(owned.type);
        }
    }
    private void reconcileEffect(Player player) {
        TimedEffect owned = ownedEffects.get(player.getUniqueId());
        if (owned == null) return;
        long remaining = owned.expiresAt - System.currentTimeMillis();
        player.removePotionEffect(owned.type);
        if (remaining <= 0) { ownedEffects.remove(player.getUniqueId()); return; }
        player.addPotionEffect(new PotionEffect(owned.type,
            (int) Math.max(1, Math.min(600, (remaining + 49L) / 50L)), 0), true);
    }
    private boolean eligibleInGame(Player player, GameMatch match) {
        if (match == null || match.getState() != MatchState.IN_GAME
                || matches.isSpectating(player.getUniqueId())) return false;
        GameParticipant part = match.getParticipant(player.getUniqueId());
        return part != null && part.getState() == ParticipantState.CONNECTED;
    }
    private AbilityState state(GameMatch match, UUID id) {
        MatchChoices matchState = active.get(match.getId());
        return matchState == null ? null : matchState.abilities.get(id);
    }

    private void applyKit(Player player, GameMatch match, GameParticipant participant,
                          GameplaySettings.Kit kit, boolean respawn) {
        if (participant.getState() != ParticipantState.CONNECTED) return;
        if (kit.permanentTool != null && equipment.canImprove(participant, kit.permanentTool))
            equipment.purchased(player, match, participant, kit.permanentTool);
        List<GameplaySettings.Grant> grants = respawn ? kit.respawnItems : kit.startItems;
        for (GameplaySettings.Grant grant : grants) {
            ItemStack stack = new ItemStack(grant.material, grant.amount,
                grant.material == Material.WOOL && grant.data == 0
                    ? match.getTeam(participant.getTeamId()).getDefinition().getColorData() : grant.data);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) if (leftover != null)
                player.sendMessage(ChatColor.RED + "Sem espaco para item do kit: " + leftover.getType());
        }
        player.updateInventory();
    }

    private GameplaySettings.Kit resolvedKit(Player player, GameMatch match) {
        GameplaySettings.Kit kit = settings.kits.get(stats.getSelectedKit(player.getUniqueId()));
        return available(player, match, kit) ? kit : settings.kits.get("default");
    }
    private GameplaySettings.Ability resolvedAbility(Player player, GameMatch match) {
        GameplaySettings.Ability ability = settings.abilities.get(stats.getSelectedAbility(player.getUniqueId()));
        return available(player, match, ability) ? ability : null;
    }
    private boolean available(Player player, GameMatch match, GameplaySettings.Choice choice) {
        if (choice == null || !choice.enabled || !choice.modes.contains(MatchMode.of(match.getArena()))) return false;
        if (!choice.permission.isEmpty() && !player.hasPermission(choice.permission)) return false;
        if (choice instanceof GameplaySettings.Kit && !match.getArena().getGameplayRules().allowsKit(choice.id)) return false;
        if (choice instanceof GameplaySettings.Ability && !match.getArena().getGameplayRules().allowsAbility(choice.id)) return false;
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        return choice.level <= stats.getProgress(profile == null ? 0 : profile.totalXp).level;
    }

    public void openKits(Player player) { open(player, false); }
    public void openAbilities(Player player) { open(player, true); }
    private void open(Player player, boolean abilities) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null || (match.getState() != MatchState.WAITING
                && match.getState() != MatchState.STARTING)) return;
        String language = stats.getLanguage(player.getUniqueId());
        ChoiceView holder = new ChoiceView(abilities, match.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
            ChatColor.DARK_GRAY + (abilities ? label(language, "Habilidades", "Abilities", "Habilidades")
                : label(language, "Selecao de Kits", "Kit Selection", "Seleccion de Kits")));
        holder.inventory = inventory;
        int slot = 0;
        if (abilities) {
            inventory.setItem(slot, menuItem(Material.BARRIER,
                ChatColor.GRAY + label(language, "Nenhuma habilidade", "No ability", "Sin habilidad"),
                Arrays.asList(ChatColor.YELLOW + ("none".equals(selectedAbility(player.getUniqueId()))
                    ? label(language, "Selecionada", "Selected", "Seleccionada") : ""))));
            holder.ids.put(slot++, "none");
        }
        Map<String, ? extends GameplaySettings.Choice> entries = abilities ? settings.abilities : settings.kits;
        for (GameplaySettings.Choice choice : entries.values()) {
            if (slot >= 27) break;
            boolean selected = choice.id.equals(abilities ? selectedAbility(player.getUniqueId())
                : selectedKit(player.getUniqueId()));
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + choice.description(language));
            lore.add(ChatColor.GRAY + label(language, "Nivel", "Level", "Nivel") + ": " + choice.level);
            if (!choice.permission.isEmpty())
                lore.add(ChatColor.GRAY + label(language, "Permissao", "Permission", "Permiso")
                    + ": " + choice.permission);
            lore.add(ChatColor.GRAY + label(language, "Modos", "Modes", "Modos")
                + ": " + String.join(", ", choice.modes));
            if (choice instanceof GameplaySettings.Ability) {
                GameplaySettings.Ability ability = (GameplaySettings.Ability) choice;
                lore.add(ChatColor.GRAY + "Cooldown: " + ability.cooldownSeconds + "s");
            }
            lore.add((selected ? ChatColor.GOLD : available(player, match, choice) ? ChatColor.GREEN : ChatColor.RED)
                + (selected ? label(language, "Selecionado", "Selected", "Seleccionado")
                    : available(player, match, choice) ? label(language, "Disponivel", "Available", "Disponible")
                    : !choice.enabled ? label(language, "Desativado", "Disabled", "Desactivado")
                    : label(language, "Bloqueado", "Locked", "Bloqueado")));
            inventory.setItem(slot, menuItem(choice.icon, ChatColor.AQUA + choice.name(language), lore));
            holder.ids.put(slot++, choice.id);
        }
        player.openInventory(inventory);
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        ItemStack held = event.getItem();
        boolean kit = isSelector(held, false), ability = isSelector(held, true);
        if (!kit && !ability) return;
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (kit) openKits(player); else openAbilities(player);
        }
    }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        GameMatch match = matches.getMatchForPlayer(event.getPlayer().getUniqueId());
        if (waiting(match) && isAnySelector(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }
    @EventHandler public void onProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownExpBottle)
                || !(event.getEntity().getShooter() instanceof Player)) return;
        Player player = (Player) event.getEntity().getShooter();
        if (waiting(matches.getMatchForPlayer(player.getUniqueId()))) event.setCancelled(true);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ChoiceView) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            ChoiceView holder = (ChoiceView) top.getHolder();
            String id = holder.ids.get(event.getRawSlot());
            if (id == null) return;
            GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
            if (!waiting(match) || !match.getId().equals(holder.matchId)) return;
            if (holder.abilities && "none".equals(id)) { select(player, match, true, id); return; }
            GameplaySettings.Choice choice = holder.abilities ? settings.abilities.get(id) : settings.kits.get(id);
            if (choice == null || !available(player, match, choice)) {
                say(player, "Opcao bloqueada ou indisponivel.", "Option locked or unavailable.",
                    "Opcion bloqueada o no disponible."); return;
            }
            select(player, match, holder.abilities, id);
            return;
        }
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (!waiting(match)) return;
        if (isAnySelector(event.getCurrentItem()) || isAnySelector(event.getCursor())
                || (event.getHotbarButton() >= 0 && isAnySelector(
                    player.getInventory().getItem(event.getHotbarButton())))
                || event.getClickedInventory() == player.getInventory()
                    && (event.getSlot() == KIT_HOTBAR_SLOT || event.getSlot() == ABILITY_HOTBAR_SLOT))
            event.setCancelled(true);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof ChoiceView) {
            event.setCancelled(true); return;
        }
        Player player = (Player) event.getWhoClicked();
        if (waiting(matches.getMatchForPlayer(player.getUniqueId()))) event.setCancelled(true);
    }
    private void select(Player player, GameMatch match, boolean ability, String id) {
        try {
            stats.setGameplaySelection(player, ability ? PreferencesOutbox.Kind.ABILITY
                : PreferencesOutbox.Kind.KIT, id);
            say(player, "Selecao salva: " + id, "Selection saved: " + id,
                "Seleccion guardada: " + id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && waiting(matches.getMatchForPlayer(player.getUniqueId())))
                    open(player, ability);
            });
        } catch (IOException | IllegalStateException invalid) {
            say(player, "Nao foi possivel salvar a selecao.", "Could not save the selection.",
                "No se pudo guardar la seleccion.");
        }
    }
    private void installSelectors(Player player) {
        removeSelectors(player);
        if (!waiting(matches.getMatchForPlayer(player.getUniqueId()))) return;
        String lang = stats.getLanguage(player.getUniqueId());
        player.getInventory().setItem(KIT_HOTBAR_SLOT, selector(Material.NETHER_STAR,
            ChatColor.GREEN + label(lang, "Selecionar Kit", "Select Kit", "Elegir Kit"), KIT_MARKER,
            label(lang, "Clique para escolher o kit", "Click to choose a kit", "Clic para elegir un kit")));
        player.getInventory().setItem(ABILITY_HOTBAR_SLOT, selector(Material.EXP_BOTTLE,
            ChatColor.AQUA + label(lang, "Selecionar Habilidade", "Select Ability", "Elegir Habilidad"),
            ABILITY_MARKER, label(lang, "Clique para escolher sua habilidade",
                "Click to choose an ability", "Clic para elegir una habilidad")));
        player.updateInventory();
    }
    private void removeSelectors(Player player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder() instanceof ChoiceView)
            player.closeInventory();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++)
            if (isAnySelector(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        player.updateInventory();
    }
    private static ItemStack selector(Material material, String name, String marker, String description) {
        return menuItem(material, name, Arrays.asList(ChatColor.GRAY + description, marker));
    }
    private static ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    private static boolean isAnySelector(ItemStack item) {
        return isSelector(item, false) || isSelector(item, true);
    }
    private static boolean isSelector(ItemStack item, boolean ability) {
        if (item == null || item.getType() != (ability ? Material.EXP_BOTTLE : Material.NETHER_STAR)
                || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().contains(ability ? ABILITY_MARKER : KIT_MARKER);
    }
    private static boolean waiting(GameMatch match) {
        return match != null && (match.getState() == MatchState.WAITING
            || match.getState() == MatchState.STARTING);
    }
    private void say(Player player, String pt, String en, String es) {
        player.sendMessage(ChatColor.AQUA + "[BedWars] " + ChatColor.RESET
            + label(stats.getLanguage(player.getUniqueId()), pt, en, es));
    }
    private static String label(String language, String pt, String en, String es) {
        return "en_US".equals(language) ? en : "es_ES".equals(language) ? es : pt;
    }

    private static final class MatchChoices {
        final Map<UUID, String> kits = new HashMap<UUID, String>();
        final Map<UUID, AbilityState> abilities = new HashMap<UUID, AbilityState>();
    }
    private static final class AbilityState {
        final String id;
        long nextUseMillis;
        AbilityState(String id) { this.id = id; }
    }
    private static final class TimedEffect {
        final PotionEffectType type;
        final long expiresAt;
        TimedEffect(PotionEffectType type, long expiresAt) { this.type = type; this.expiresAt = expiresAt; }
    }
    private static final class ChoiceView implements InventoryHolder {
        final boolean abilities;
        final UUID matchId;
        final Map<Integer, String> ids = new HashMap<Integer, String>();
        Inventory inventory;
        ChoiceView(boolean abilities, UUID matchId) { this.abilities = abilities; this.matchId = matchId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
