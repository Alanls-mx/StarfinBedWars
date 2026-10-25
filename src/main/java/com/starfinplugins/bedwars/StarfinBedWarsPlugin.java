package com.starfinplugins.bedwars;

import com.starfinplugins.bedwars.command.BedWarsCommand;
import com.starfinplugins.bedwars.config.BedWarsSettings;
import com.starfinplugins.bedwars.config.PresentationConfigMigrator;
import com.starfinplugins.bedwars.economy.generator.ResourceGeneratorService;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.arena.ArenaEditingService;
import com.starfinplugins.bedwars.arena.ArenaRegionSelectionService;
import com.starfinplugins.bedwars.reconnect.ReconnectService;
import com.starfinplugins.bedwars.reconnect.ReconnectSettings;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.match.TntAutoIgniteService;
import com.starfinplugins.bedwars.match.ParticipantInventoryService;
import com.starfinplugins.bedwars.match.MatchPresentationService;
import com.starfinplugins.bedwars.match.MatchChatService;
import com.starfinplugins.bedwars.match.MatchChestService;
import com.starfinplugins.bedwars.match.MatchEquipmentService;
import com.starfinplugins.bedwars.match.MatchVisualService;
import com.starfinplugins.bedwars.match.MatchPointService;
import com.starfinplugins.bedwars.match.BedHologramService;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.network.NetworkRegistryService;
import com.starfinplugins.bedwars.network.NetworkReservationDeliveryService;
import com.starfinplugins.bedwars.integration.BedWarsMinigameIntegration;
import com.starfinplugins.bedwars.storage.StorageMigrationService;
import com.starfinplugins.bedwars.menu.BedWarsMenuService;
import com.starfinplugins.bedwars.menu.NextMatchService;
import com.starfinplugins.bedwars.setup.ArenaSetupWizardService;
import com.starfinplugins.bedwars.service.ServiceLifecycle;
import com.starfinplugins.bedwars.shop.ShopService;
import com.starfinplugins.bedwars.shop.special.SpecialItemService;
import com.starfinplugins.bedwars.upgrades.TeamUpgradeService;
import com.starfinplugins.bedwars.world.WorldManagementService;
import com.starfinplugins.bedwars.world.WorldSettings;
import com.starfinplugins.bedwars.world.ManagedMobGuard;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarfinBedWarsPlugin extends JavaPlugin {
    private ServiceLifecycle services;
    private BedWarsSettings settings;
    private ArenaService arenaService;
    private MatchEngineService matchEngine;
    private TntAutoIgniteService tntAutoIgnite;
    private ResourceGeneratorService generators;
    private ParticipantInventoryService participantInventory;
    private TeamUpgradeService teamUpgrades;
    private ShopService shops;
    private SpecialItemService specialItems;
    private WorldSettings worldSettings;
    private WorldManagementService worldManagement;
    private ArenaEditingService arenaEditing;
    private ArenaRegionSelectionService regionSelection;
    private MatchPresentationService presentation;
    private BedWarsMenuService menus;
    private NextMatchService nextMatch;
    private ArenaSetupWizardService setupWizard;
    private ReconnectService reconnect;
    private ManagedMobGuard mobGuard;
    private MatchChatService matchChat;
    private MatchChestService chests;
    private MatchEquipmentService equipment;
    private MatchVisualService visuals;
    private MatchPointService matchPoint;
    private BedHologramService bedHolograms;
    private MatchStatisticsService statistics;
    private NetworkRegistryService networkRegistry;
    private NetworkReservationDeliveryService reservationDelivery;
    private BedWarsMinigameIntegration minigameIntegration;
    private StorageMigrationService storageMigration;
    private boolean initialized;

    @Override
    public void onEnable() {
        getLogger().info("Iniciando StarfinBedWars...");
        try {
            getLogger().info("Verificando dependencias...");
            verifyPlatform();
            verifyBundledClasses();

            getLogger().info("Carregando configuracoes...");
            saveDefaultConfig();
            PresentationConfigMigrator.ensure(this);
            settings = BedWarsSettings.load(this);
            worldSettings = WorldSettings.load(this);

            services = new ServiceLifecycle();
            arenaService = new ArenaService(this);
            arenaService.setMaximumRegionBlocks(worldSettings.getMaximumRegionBlocks());
            worldManagement = new WorldManagementService(this, worldSettings, arenaService);
            arenaService.setWorldManagement(worldManagement);
            arenaEditing = new ArenaEditingService(this, arenaService, worldManagement);
            regionSelection = new ArenaRegionSelectionService(this, arenaService, arenaEditing);
            matchEngine = new MatchEngineService(this, arenaService,
                settings.getRespawnSeconds(),
                settings.getSpawnOpeningSeconds());
            tntAutoIgnite = new TntAutoIgniteService(this, matchEngine, settings);
            reconnect = new ReconnectService(this, matchEngine, ReconnectSettings.load(this));
            matchEngine.setReconnect(reconnect);
            menus = new BedWarsMenuService(this, arenaService, matchEngine, arenaEditing);
            nextMatch = new NextMatchService(this, arenaService, matchEngine, menus);
            setupWizard = new ArenaSetupWizardService(this, arenaService, arenaEditing);
            arenaEditing.setSetupWizard(setupWizard);
            mobGuard = new ManagedMobGuard(this, arenaService, arenaEditing,
                matchEngine, worldManagement);
            setupWizard.setMobGuard(mobGuard);
            matchChat = new MatchChatService(this, matchEngine);
            chests = new MatchChestService(this, matchEngine);
            menus.setSetupWizard(setupWizard);
            arenaService.setMatchEngine(matchEngine);
            participantInventory = new ParticipantInventoryService(this, matchEngine);
            matchEngine.setParticipantInventory(participantInventory);
            generators = new ResourceGeneratorService(this, arenaService, matchEngine);
            generators.setMobGuard(mobGuard);
            presentation = new MatchPresentationService(this, matchEngine, generators);
            teamUpgrades = new TeamUpgradeService(this, arenaService, matchEngine, generators);
            specialItems = new SpecialItemService(this, matchEngine);
            specialItems.setMobGuard(mobGuard);
            teamUpgrades.setSpecialItems(specialItems);
            equipment = new MatchEquipmentService(this, teamUpgrades, matchEngine);
            matchEngine.setEquipment(equipment);
            visuals = new MatchVisualService(this, matchEngine);
            matchPoint = new MatchPointService(this, matchEngine, mobGuard);
            bedHolograms = new BedHologramService(this, arenaService, matchEngine, mobGuard);
            statistics = new MatchStatisticsService(this);
            networkRegistry = new NetworkRegistryService(this, arenaService, matchEngine);
            storageMigration = new StorageMigrationService(this, arenaService);
            shops = new ShopService(this, arenaService, matchEngine, teamUpgrades);
            shops.setMobGuard(mobGuard);
            shops.setEquipment(equipment);
            services.add(worldManagement);
            services.add(arenaService);
            services.add(matchEngine);
            services.add(statistics);
            services.add(networkRegistry);
            services.add(storageMigration);
            services.add(tntAutoIgnite);
            services.add(reconnect);
            services.add(arenaEditing);
            services.add(mobGuard);
            services.add(regionSelection);
            services.add(participantInventory);
            services.add(chests);
            services.add(generators);
            services.add(teamUpgrades);
            services.add(equipment);
            services.add(shops);
            services.add(specialItems);
            services.add(matchPoint);
            services.add(matchChat);
            services.add(presentation);
            services.add(visuals);
            services.add(bedHolograms);
            services.add(menus);
            services.add(nextMatch);
            services.add(setupWizard);
            if (getServer().getPluginManager().isPluginEnabled("StarfinMinigames")) {
                try {
                    minigameIntegration = new BedWarsMinigameIntegration(this, arenaService, matchEngine, networkRegistry);
                    services.add(minigameIntegration);
                    reservationDelivery = new NetworkReservationDeliveryService(this, networkRegistry, matchEngine);
                    services.add(reservationDelivery);
                } catch (LinkageError incompatibleApi) {
                    getLogger().warning("StarfinMinigames incompativel; BedWars segue sem API compartilhada: "
                        + incompatibleApi.getClass().getSimpleName());
                }
            }
            services.start();

            PluginCommand command = getCommand("starfinbedwars");
            if (command == null) {
                throw new IllegalStateException("Comando starfinbedwars ausente do plugin.yml");
            }
            BedWarsCommand executor = new BedWarsCommand(this, arenaService, matchEngine,
                worldManagement, arenaEditing, regionSelection, menus, setupWizard);
            getServer().getPluginManager().registerEvents(executor, this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            PluginCommand reconnectCommand = getCommand("reconnect");
            if (reconnectCommand != null) reconnectCommand.setExecutor(executor);

            initialized = true;
            getLogger().info("Inicializacao concluida.");
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Falha ao inicializar StarfinBedWars.", error);
            stopServices();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        initialized = false;
        stopServices();
        getLogger().info("StarfinBedWars desligado.");
    }

    public boolean isPluginReady() {
        return initialized;
    }

    public BedWarsSettings getSettings() {
        return settings;
    }

    public ArenaService getArenaService() {
        return arenaService;
    }

    public MatchEngineService getMatchEngine() {
        return matchEngine;
    }

    public MatchPointService getMatchPoint() { return matchPoint; }
    public ReconnectService getReconnect() { return reconnect; }
    public MatchChatService getMatchChat() { return matchChat; }
    public MatchStatisticsService getStatistics() { return statistics; }
    public NetworkRegistryService getNetworkRegistry() { return networkRegistry; }
    public StorageMigrationService getStorageMigration() { return storageMigration; }

    public WorldManagementService getWorldManagement() { return worldManagement; }

    public ResourceGeneratorService getGeneratorService() {
        return generators;
    }
    public ShopService getShopService() { return shops; }

    public List<String> getServiceNames() {
        return services == null ? Collections.<String>emptyList() : services.getStartedServiceNames();
    }

    private void verifyPlatform() {
        String version = getServer().getBukkitVersion();
        if (version == null || !version.startsWith("1.8.8")) {
            throw new IllegalStateException("Versao Bukkit nao suportada: " + version
                + ". O alvo desta versao e Spigot 1.8.8.");
        }
    }

    /**
     * Fail at startup with an actionable message instead of discovering a
     * missing class when a player opens a Shop or when the server shuts down.
     *
     * Class.forName with initialize=false resolves the actual plugin jar
     * class path, but does not initialize the NMS NPC's static state.
     */
    private void verifyBundledClasses() {
        String[] expected = {
            "com.starfinplugins.bedwars.shop.npc.ShopNpcFactoryV1_8_R3",
            "com.starfinplugins.bedwars.shop.npc.ShopNpcVillager",
            "com.starfinplugins.bedwars.match.MatchPointDragonFlightV1_8_R3",
            "com.starfinplugins.bedwars.match.MatchPointBossBarV1_8_R3"
        };
        for (String name : expected) {
            try {
                Class.forName(name, false, getClass().getClassLoader());
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("JAR StarfinBedWars incompleto: classe "
                    + name + " ausente. Pare o servidor, recompile com mvn -B verify "
                    + "e instale o JAR novo antes de iniciar.", error);
            } catch (LinkageError error) {
                throw new IllegalStateException("JAR StarfinBedWars incompativel "
                    + "ou corrompido ao carregar " + name + ". "
                    + "Confira PaperSpigot 1.8.8 e reinstale com o servidor desligado.", error);
            }
        }
    }

    private void stopServices() {
        if (services == null) {
            return;
        }
        try {
            services.stop();
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "Falha ao encerrar um ou mais servicos.", error);
        } finally {
            services = null;
        }
    }
}
