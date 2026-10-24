package com.starfinplugins.bedwars.shop;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.economy.InventoryEconomy;
import com.starfinplugins.bedwars.event.MatchLifecycleEvent;
import com.starfinplugins.bedwars.event.ShopPurchaseEvent;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.MatchEquipmentService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.upgrades.TeamUpgradeService;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import com.starfinplugins.bedwars.world.SafeLocationResolver;
import com.starfinplugins.bedwars.shop.npc.ShopNpcFactoryV1_8_R3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.EnumMap;
import java.util.Collections;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ShopService implements ManagedService, Listener {
    private static final String SHOP_MATCH_TAG = "starfinbedwars-shop-match";
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final TeamUpgradeService upgrades;
    private final Map<UUID, List<Entity>> shopEntities = new HashMap<UUID, List<Entity>>();
    private final Map<UUID, Set<String>> spawnedKinds = new HashMap<UUID, Set<String>>();
    private final Map<UUID, Location> npcAnchors = new HashMap<UUID, Location>();
    private final Map<UUID, ArmorStand> npcBodies = new HashMap<UUID, ArmorStand>();
    private ShopSettings settings;
    private ManagedMobGuard mobGuard;
    private MatchEquipmentService equipment;
    private BukkitTask anchorCheck;
    private final Map<UUID, Set<String>> favorites = new HashMap<UUID, Set<String>>();
    private File favoritesFile;

    public void setMobGuard(ManagedMobGuard mobGuard) { this.mobGuard = mobGuard; }
    public void setEquipment(MatchEquipmentService equipment) { this.equipment = equipment; }

    public ShopService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                       TeamUpgradeService upgrades) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.upgrades = upgrades;
    }

    @Override public String getName() { return "shops"; }

    @Override
    public void start() throws Exception {
        settings = ShopSettings.load(plugin);
        favoritesFile = new File(plugin.getDataFolder(), "shop-favorites.yml");
        loadFavorites();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        anchorCheck = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { correctExternalDisplacement(); }
        }, 10L, 10L);
    }

    @Override
    public void stop() {
        if (anchorCheck != null) anchorCheck.cancel();
        anchorCheck = null;
        for (UUID matchId : new ArrayList<UUID>(shopEntities.keySet())) removeShops(matchId);
        ShopNpcFactoryV1_8_R3.unregisterType();
        shopEntities.clear();
        spawnedKinds.clear();
        npcAnchors.clear();
        npcBodies.clear();
        favorites.clear();
    }

    @EventHandler
    public void onMatchLifecycle(MatchLifecycleEvent event) {
        if (event.getType() == MatchLifecycleEvent.Type.STARTED) {
            spawnShops(event.getMatch());
            final GameMatch match = event.getMatch();
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override public void run() { if (match.getState() == MatchState.IN_GAME) spawnShops(match); }
            }, 20L);
        } else if (event.getType() == MatchLifecycleEvent.Type.ENDED
                || event.getType() == MatchLifecycleEvent.Type.INSTANCE_REMOVED) {
            removeShops(event.getMatch().getId());
        }
    }

    private void spawnShops(GameMatch match) {
        World world = matches.getWorld(match.getId());
        if (world == null) {
            plugin.getLogger().severe("Lojas nao iniciadas: mundo da partida ausente para " + match.getId());
            return;
        }
        List<Entity> created = shopEntities.get(match.getId());
        if (created == null) {
            created = new ArrayList<Entity>();
            shopEntities.put(match.getId(), created);
        }
        Set<String> kinds = spawnedKinds.get(match.getId());
        if (kinds == null) {
            kinds = new LinkedHashSet<String>();
            spawnedKinds.put(match.getId(), kinds);
        }
        {
            boolean hasTeamItemShop = false;
            boolean hasTeamUpgradeShop = false;
            boolean missingTeamItemShop = false;
            boolean missingTeamUpgradeShop = false;
            for (TeamDefinition team : match.getArena().getTeamList()) {
                hasTeamItemShop |= team.getItemShop() != null;
                hasTeamUpgradeShop |= team.getUpgradeShop() != null;
                missingTeamItemShop |= team.getItemShop() == null;
                missingTeamUpgradeShop |= team.getUpgradeShop() == null;
            }
            for (TeamDefinition team : match.getArena().getTeamList()) {
                if (team.getItemShop() != null || match.getArena().getItemShop() == null) {
                    trySpawnShop(match, world, team.getItemShop(), fallbackShop(team, true),
                        "Quick Buy", "items:" + team.getId(), created, kinds);
                }
                if (team.getUpgradeShop() != null || match.getArena().getUpgradeShop() == null) {
                    trySpawnShop(match, world, team.getUpgradeShop(), fallbackShop(team, false),
                        "Team Upgrades", "upgrades:" + team.getId(), created, kinds);
                }
            }
            if ((!hasTeamItemShop || missingTeamItemShop)
                    && match.getArena().getItemShop() != null) {
                trySpawnShop(match, world, match.getArena().getItemShop(), null, "Quick Buy",
                    "items", created, kinds);
            }
            if ((!hasTeamUpgradeShop || missingTeamUpgradeShop)
                    && match.getArena().getUpgradeShop() != null) {
                trySpawnShop(match, world, match.getArena().getUpgradeShop(), null, "Team Upgrades",
                    "upgrades", created, kinds);
            }
        }
    }

    private Position fallbackShop(TeamDefinition team, boolean items) {
        if (!plugin.getConfig().getBoolean("shop-npc.fallback-to-team-spawn", true)
                || team.getSpawn() == null) return null;
        Position spawn = team.getSpawn();
        return new Position(spawn.getWorld(), spawn.getX(), spawn.getY(),
            spawn.getZ() + (items ? 3.0D : -3.0D), spawn.getYaw(), spawn.getPitch());
    }

    private void trySpawnShop(GameMatch match, World world, Position position, Position fallback,
                              String name,
                              String kind, List<Entity> created, Set<String> kinds) {
        if (kinds.contains(kind)) return;
        if (position == null) {
            if (fallback == null) {
                plugin.getLogger().warning("NPC sem posicao: arena=" + match.getArena().getId()
                    + " partida=" + match.getId() + " tipo=" + kind + ".");
                return;
            }
            plugin.getLogger().warning("NPC " + kind + " sem posicao em "
                + match.getArena().getId() + "; usando local proximo ao spawn da equipe.");
            position = fallback;
            fallback = null;
        }
        int before = created.size();
        try {
            spawnShop(match, world, position, name, kind, created);
            kinds.add(kind);
        } catch (RuntimeException error) {
            rollbackCreated(created, before);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Falha ao criar NPC de loja: arena=" + match.getArena().getId()
                    + " partida=" + match.getId() + " tipo=" + kind
                    + " posicao=" + position.getX() + "," + position.getY()
                    + "," + position.getZ(), error);
            if (fallback != null) {
                try {
                    spawnShop(match, world, fallback, name, kind, created);
                    kinds.add(kind);
                    plugin.getLogger().warning("NPC " + kind
                        + " criado perto do spawn da equipe apos falha no ponto configurado.");
                } catch (RuntimeException fallbackError) {
                    rollbackCreated(created, before);
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Falha no ponto alternativo do NPC " + kind + " em "
                            + match.getArena().getId() + ".", fallbackError);
                }
            }
        }
    }

    private void rollbackCreated(List<Entity> created, int before) {
        for (int index = created.size() - 1; index >= before; index--) {
            Entity entity = created.remove(index);
            npcAnchors.remove(entity.getUniqueId());
            npcBodies.remove(entity.getUniqueId());
            if (mobGuard != null) mobGuard.forget(entity);
            if (entity.isValid()) entity.remove();
        }
    }

    private void spawnShop(GameMatch match, World world, Position position, String name,
                           String kind, List<Entity> created) {
        if (position == null) return;
        Location location = safeNpcLocation(arenas.toLocation(position, world));
        if (!location.getChunk().isLoaded() && !location.getChunk().load()) {
            throw new IllegalStateException("Chunk do NPC indisponivel.");
        }
        Entity entity = mobGuard == null ? ShopNpcFactoryV1_8_R3.spawn(location)
            : mobGuard.spawnAuthorized(location, EntityType.VILLAGER,
                () -> ShopNpcFactoryV1_8_R3.spawn(location));
        if (!(entity instanceof Villager)) {
            entity.remove();
            throw new IllegalStateException("Bukkit nao criou o NPC de loja.");
        }
        Villager villager = (Villager) entity;
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
        villager.setCanPickupItems(false);
        villager.setRemoveWhenFarAway(false);
        villager.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0,
            true, false));
        villager.setMetadata(SHOP_MATCH_TAG,
            new FixedMetadataValue(plugin, match.getId().toString() + "|" + kind));
        created.add(villager);
        npcAnchors.put(villager.getUniqueId(), location.clone());
        boolean itemShop = kind.startsWith("items");
        ArmorStand body = hologramLine(location.clone(), "");
        body.setCustomNameVisible(false);
        body.setMarker(false);
        body.setArms(true);
        body.setCanPickupItems(false);
        body.setMetadata(SHOP_MATCH_TAG,
            new FixedMetadataValue(plugin, match.getId().toString() + "|" + kind));
        applySkin(body, skinOwner(itemShop));
        created.add(body);
        npcBodies.put(villager.getUniqueId(), body);
        String title = plugin.getConfig().getString(itemShop
            ? "shop-hologram.items-title" : "shop-hologram.upgrades-title",
            itemShop ? "&eLOJA DE ITENS" : "&eMELHORIAS");
        String subtitle = plugin.getConfig().getString(itemShop
            ? "shop-hologram.items-subtitle" : "shop-hologram.upgrades-subtitle",
            itemShop ? "&7Clique para comprar" : "&7Clique para melhorar sua equipe");
        double height = Math.max(1.5D, Math.min(4.0D,
            plugin.getConfig().getDouble("shop-hologram.height", 2.25D)));
        created.add(hologramLine(location.clone().add(0, height, 0), title));
        if (subtitle != null && !subtitle.isEmpty()) {
            double spacing = Math.max(0.2D, Math.min(0.4D,
                plugin.getConfig().getDouble("shop-hologram.line-spacing", 0.24D)));
            created.add(hologramLine(location.clone().add(0, height - spacing, 0), subtitle));
        }
    }

    private Location safeNpcLocation(Location preferred) {
        World world = preferred.getWorld();
        int centerX = preferred.getBlockX();
        int centerY = preferred.getBlockY();
        int centerZ = preferred.getBlockZ();
        for (int radius = 0; radius <= 2; radius++) {
            for (int yOffset : new int[] {0, -1, 1, -2, 2}) {
                int y = centerY + yOffset;
                if (y < 1 || y >= world.getMaxHeight() - 2) continue;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != radius) continue;
                        Location candidate = new Location(world, x + 0.5D, y, z + 0.5D,
                            preferred.getYaw(), preferred.getPitch());
                        if (SafeLocationResolver.hasSafeFloor(candidate)) return candidate;
                    }
                }
            }
        }
        throw new IllegalStateException("Sem espaco seguro perto de " + centerX + ","
            + centerY + "," + centerZ + " para o NPC.");
    }

    private ArmorStand hologramLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) (mobGuard == null
            ? location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND)
            : mobGuard.spawnAuthorized(location, EntityType.ARMOR_STAND));
        if (!stand.isValid()) throw new IllegalStateException("Holograma da loja nao foi criado.");
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
        stand.setCustomNameVisible(true);
        return stand;
    }

    private String skinOwner(boolean itemShop) {
        return plugin.getConfig().getString(itemShop
            ? "shop-npc.items-skin" : "shop-npc.upgrades-skin",
            itemShop ? "AlanLS_" : "Lurreisper");
    }

    private void applySkin(ArmorStand body, String owner) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setOwner(owner);
        head.setItemMeta(skull);
        body.setHelmet(head);
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        chestMeta.setColor(Color.fromRGB(83, 68, 52));
        chest.setItemMeta(chestMeta);
        body.setChestplate(chest);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta legMeta = (LeatherArmorMeta) legs.getItemMeta();
        legMeta.setColor(Color.fromRGB(56, 56, 56));
        legs.setItemMeta(legMeta);
        body.setLeggings(legs);
    }

    public void setSkin(String kind, String owner) {
        if (!"items".equals(kind) && !"upgrades".equals(kind))
            throw new IllegalArgumentException("Use items ou upgrades.");
        if (owner == null || !owner.matches("[A-Za-z0-9_]{3,16}"))
            throw new IllegalArgumentException("Nome de jogador invalido (3 a 16 caracteres).");
        plugin.getConfig().set("shop-npc." + kind + "-skin", owner);
        plugin.saveConfig();
        for (List<Entity> entities : shopEntities.values()) {
            for (Entity entity : entities) {
                if (!(entity instanceof ArmorStand) || !entity.hasMetadata(SHOP_MATCH_TAG)) continue;
                String data = ownedMetadata(entity);
                if (data != null && data.contains("|" + kind + ":")) applySkin((ArmorStand) entity, owner);
            }
        }
    }

    private void removeShops(UUID matchId) {
        spawnedKinds.remove(matchId);
        List<Entity> entities = shopEntities.remove(matchId);
        if (entities == null) return;
        for (Entity entity : entities) {
            if (entity != null) {
                npcAnchors.remove(entity.getUniqueId());
                npcBodies.remove(entity.getUniqueId());
                if (mobGuard != null) mobGuard.forget(entity);
                if (entity.isValid()) entity.remove();
            }
        }
    }

    private void correctExternalDisplacement() {
        for (List<Entity> entities : shopEntities.values()) {
            for (Entity entity : entities) {
                if (!(entity instanceof Villager) || !entity.isValid()) continue;
                Location anchor = npcAnchors.get(entity.getUniqueId());
                if (anchor != null && (entity.getWorld() != anchor.getWorld()
                        || entity.getLocation().distanceSquared(anchor) > 0.25D)) {
                    entity.teleport(anchor);
                }
                Player nearest = null;
                double nearestDistance = 8.0D * 8.0D;
                for (Player viewer : entity.getWorld().getPlayers()) {
                    if (viewer.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                    double distance = viewer.getLocation().distanceSquared(entity.getLocation());
                    if (distance < nearestDistance) {
                        nearest = viewer;
                        nearestDistance = distance;
                    }
                }
                if (nearest != null) {
                    ShopNpcFactoryV1_8_R3.facePlayer((Villager) entity, nearest);
                    ArmorStand body = npcBodies.get(entity.getUniqueId());
                    if (body != null && body.isValid()) {
                        Location look = body.getLocation();
                        Location playerAt = nearest.getLocation();
                        look.setYaw((float) Math.toDegrees(Math.atan2(
                            look.getZ() - playerAt.getZ(), playerAt.getX() - look.getX())) - 90.0F);
                        body.teleport(look);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onShopClick(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        String shopData = ownedMetadata(clicked);
        if (shopData == null) return;
        event.setCancelled(true);
        String[] shopParts = shopData.split("\\|", 2);
        if (shopParts.length != 2) return;
        Player player = event.getPlayer();
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        if (match == null || !match.getId().toString().equals(shopParts[0])
                || match.getState() != MatchState.IN_GAME) {
            player.sendMessage(ChatColor.RED + "Esta loja pertence a uma partida encerrada.");
            return;
        }
        GameParticipant participant = match.getParticipant(player.getUniqueId());
        if (participant == null || participant.getState() != ParticipantState.CONNECTED
                || player.getWorld() != matches.getWorld(match.getId())) {
            player.sendMessage(ChatColor.RED + "Voce nao pode usar esta loja agora.");
            return;
        }
        String[] shopKind = shopParts[1].split(":", 2);
        if (shopKind.length == 2 && !shopKind[1].equals(participant.getTeamId())) {
            player.sendMessage(ChatColor.RED + "Esta loja pertence a outra equipe.");
            return;
        }
        if (shopKind[0].startsWith("upgrades")) {
            upgrades.openShop(player, match, participant.getTeamId());
            return;
        }
        openMenu(player, ClassicShopMenu.Category.FAVORITES, 0, false);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ClassicShopMenu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        ClassicShopMenu menu = (ClassicShopMenu) top.getHolder();
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot <= 8 && menu.getCategoryAt(slot) != null) {
            reopenLater(player, menu.getCategoryAt(slot), 0, menu.isEditing());
            return;
        }
        if (slot == 45 && menu.getPage() > 0) {
            reopenLater(player, menu.getCategory(), menu.getPage() - 1, menu.isEditing());
            return;
        }
        if (slot == 53 && menu.getPage() + 1 < menu.getPageCount()) {
            reopenLater(player, menu.getCategory(), menu.getPage() + 1, menu.isEditing());
            return;
        }
        if (slot == 49) {
            reopenLater(player, menu.getCategory(), menu.getPage(), !menu.isEditing());
            return;
        }
        if (slot == 50) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() { if (player.isOnline()) player.closeInventory(); }
            });
            return;
        }
        ShopItemDefinition definition = menu.getEntry(event.getRawSlot());
        if (definition == null) return;
        if (menu.isEditing()) {
            toggleFavorite(player, definition.getId());
            reopenLater(player, menu.getCategory(), menu.getPage(), true);
            return;
        }
        purchase(player, definition);
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getHolder() == menu) {
            reopenLater(player, menu.getCategory(), menu.getPage(), false);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClassicShopMenu) event.setCancelled(true);
    }

    private void openMenu(Player player, ClassicShopMenu.Category category, int page, boolean editing) {
        ClassicShopMenu menu = new ClassicShopMenu(player, settings.getItems().values(),
            favoritesFor(player.getUniqueId()), category, page, editing,
            categoryOrder(), categoryIcons());
        player.openInventory(menu.getInventory());
    }

    private List<ClassicShopMenu.Category> categoryOrder() {
        List<String> configured = plugin.getConfig().getStringList("shop.category-order");
        List<ClassicShopMenu.Category> order = new ArrayList<ClassicShopMenu.Category>();
        for (String value : configured) {
            try {
                ClassicShopMenu.Category category = ClassicShopMenu.Category.valueOf(
                    value.toUpperCase(Locale.ROOT));
                if (!order.contains(category)) order.add(category);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Categoria de loja invalida: " + value);
            }
        }
        for (ClassicShopMenu.Category category : ClassicShopMenu.Category.values()) {
            if (!order.contains(category)) order.add(category);
        }
        return Collections.unmodifiableList(order);
    }

    private Map<ClassicShopMenu.Category, Material> categoryIcons() {
        Map<ClassicShopMenu.Category, Material> icons =
            new EnumMap<ClassicShopMenu.Category, Material>(ClassicShopMenu.Category.class);
        for (ClassicShopMenu.Category category : ClassicShopMenu.Category.values()) {
            String value = plugin.getConfig().getString("shop.category-icons." + category.name());
            if (value == null) continue;
            try { icons.put(category, Material.valueOf(value.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Icone de loja invalido: " + value);
            }
        }
        return icons;
    }

    private void reopenLater(final Player player, final ClassicShopMenu.Category category,
                             final int page, final boolean editing) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline()) openMenu(player, category, page, editing);
            }
        });
    }

    private Set<String> favoritesFor(UUID playerId) {
        Set<String> saved = favorites.get(playerId);
        if (saved != null) return saved;
        Set<String> defaults = new LinkedHashSet<String>();
        for (ShopItemDefinition item : settings.getQuickBuyItems()) defaults.add(item.getId());
        return defaults;
    }

    private void toggleFavorite(Player player, String itemId) {
        Set<String> chosen = favorites.get(player.getUniqueId());
        if (chosen == null) chosen = new LinkedHashSet<String>(favoritesFor(player.getUniqueId()));
        if (!chosen.add(itemId)) chosen.remove(itemId);
        favorites.put(player.getUniqueId(), chosen);
        try { saveFavorites(); }
        catch (IOException error) {
            plugin.getLogger().warning("Falha ao salvar favoritos de loja: " + error.getMessage());
            player.sendMessage(ChatColor.RED + "Favorito alterado apenas nesta sessao.");
        }
    }

    private void loadFavorites() throws IOException {
        favorites.clear();
        if (!favoritesFile.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(favoritesFile); }
        catch (Exception error) { throw new IOException("shop-favorites.yml invalido.", error); }
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                Set<String> ids = new LinkedHashSet<String>();
                for (String itemId : section.getStringList(key)) {
                    if (settings.getItems().containsKey(itemId)) ids.add(itemId);
                }
                favorites.put(UUID.fromString(key), ids);
            } catch (IllegalArgumentException malformed) {
                plugin.getLogger().warning("UUID invalido em shop-favorites.yml: " + key);
            }
        }
    }

    private void saveFavorites() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        for (Map.Entry<UUID, Set<String>> entry : favorites.entrySet()) {
            yaml.set("players." + entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        File temporary = new File(favoritesFile.getParentFile(), favoritesFile.getName() + ".tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), favoritesFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), favoritesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void purchase(Player player, ShopItemDefinition definition) {
        GameMatch match = matches.getMatchForPlayer(player.getUniqueId());
        GameParticipant participant = match == null ? null : match.getParticipant(player.getUniqueId());
        if (match == null || match.getState() != MatchState.IN_GAME || participant == null
                || participant.getState() != ParticipantState.CONNECTED
                || player.getWorld() != matches.getWorld(match.getId())) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "A partida nao esta disponivel para compras.");
            return;
        }
        ItemStack product = createProduct(definition, match, participant);
        boolean permanent = equipment != null && equipment.isPermanent(product.getType());
        if (permanent && !equipment.canImprove(participant, product.getType())) {
            player.sendMessage(ChatColor.YELLOW + "Voce ja possui este nivel de equipamento ou superior.");
            return;
        }
        if (!permanent && !canFit(player, product)) {
            player.sendMessage(ChatColor.RED + "Libere espaco no inventario antes de comprar.");
            return;
        }
        ItemStack[] backup = InventoryEconomy.snapshot(player);
        if (!InventoryEconomy.charge(player, definition.getCostType(), definition.getCost())) {
            player.sendMessage(ChatColor.RED + "Recursos insuficientes.");
            return;
        }
        if (permanent) {
            equipment.purchased(player, match, participant, product.getType());
            Bukkit.getPluginManager().callEvent(new ShopPurchaseEvent(match, player.getUniqueId(), definition));
            player.sendMessage(ChatColor.GREEN + "Comprado: " + definition.getDisplayName() + ".");
            purchaseSound(player);
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(product);
        if (!leftovers.isEmpty()) {
            InventoryEconomy.restore(player, backup);
            player.sendMessage(ChatColor.RED + "Compra cancelada: nao foi possivel entregar o item.");
            return;
        }
        Bukkit.getPluginManager().callEvent(new ShopPurchaseEvent(match, player.getUniqueId(), definition));
        player.sendMessage(ChatColor.GREEN + "Comprado: " + definition.getDisplayName() + ".");
        purchaseSound(player);
    }

    private void purchaseSound(Player player) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfig().getString("purchase-sounds.shop", "NOTE_PLING"));
            player.playSound(player.getLocation(), sound,
                (float) plugin.getConfig().getDouble("purchase-sounds.volume", 0.8D),
                (float) plugin.getConfig().getDouble("purchase-sounds.pitch", 1.25D));
        } catch (IllegalArgumentException invalidSound) {
            plugin.getLogger().warning("Som de compra invalido em purchase-sounds.shop.");
        }
    }

    private ItemStack createProduct(ShopItemDefinition definition, GameMatch match,
                                    GameParticipant participant) {
        short data = definition.getData();
        if ("wool".equals(definition.getId())) {
            data = match.getTeam(participant.getTeamId()).getDefinition().getColorData();
        }
        ItemStack item = new ItemStack(definition.getMaterial(), definition.getAmount(), data);
        upgrades.applyItemUpgrade(match, participant, item);
        return item;
    }

    private boolean canFit(Player player, ItemStack product) {
        ItemStack[] contents = InventoryEconomy.snapshot(player);
        int remaining = product.getAmount();
        for (ItemStack current : contents) {
            if (current == null || !current.isSimilar(product)) continue;
            int space = Math.min(current.getMaxStackSize(), player.getInventory().getMaxStackSize())
                - current.getAmount();
            if (space > 0) remaining -= Math.min(space, remaining);
            if (remaining == 0) return true;
        }
        for (ItemStack current : contents) {
            if (current != null) continue;
            remaining -= Math.min(product.getMaxStackSize(), player.getInventory().getMaxStackSize());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private String ownedMetadata(Entity entity) {
        if (!entity.hasMetadata(SHOP_MATCH_TAG)) return null;
        for (org.bukkit.metadata.MetadataValue value : entity.getMetadata(SHOP_MATCH_TAG)) {
            if (value.getOwningPlugin() == plugin) return value.asString();
        }
        return null;
    }

    @EventHandler
    public void onShopDamage(EntityDamageEvent event) {
        if (ownedMetadata(event.getEntity()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onShopTarget(EntityTargetEvent event) {
        if (ownedMetadata(event.getEntity()) != null) event.setCancelled(true);
    }
}
