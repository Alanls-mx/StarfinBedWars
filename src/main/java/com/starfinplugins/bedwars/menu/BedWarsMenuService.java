package com.starfinplugins.bedwars.menu;

import com.starfinplugins.bedwars.arena.ArenaEditingService;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.setup.ArenaSetupWizardService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class BedWarsMenuService implements ManagedService, Listener {
    private static final int PAGE_SIZE = 45;
    private final Plugin plugin;
    private final ArenaService arenas;
    private final MatchEngineService matches;
    private final ArenaEditingService editing;
    private ArenaSetupWizardService wizard;

    public BedWarsMenuService(Plugin plugin, ArenaService arenas, MatchEngineService matches,
                              ArenaEditingService editing) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.matches = matches;
        this.editing = editing;
    }

    public void setSetupWizard(ArenaSetupWizardService wizard) {
        this.wizard = wizard;
    }

    @Override
    public String getName() { return "menus"; }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public void openPlayerMenu(Player player, int page) {
        List<ArenaDefinition> available = new ArrayList<ArenaDefinition>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (arena.isEnabled()) available.add(arena);
        }
        open(player, page, false, available);
    }

    public void openAdminMenu(Player player, int page) {
        open(player, page, true, new ArrayList<ArenaDefinition>(arenas.getArenas()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ArenaMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClickedInventory() != top) return;
        Player player = (Player) event.getWhoClicked();
        ArenaMenuHolder holder = (ArenaMenuHolder) top.getHolder();
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 45) {
            render(player, holder.page - 1, holder.admin);
            return;
        }
        if (event.getRawSlot() == 53) {
            render(player, holder.page + 1, holder.admin);
            return;
        }
        ArenaDefinition arena = holder.entries.get(event.getRawSlot());
        if (arena == null) return;
        player.closeInventory();
        if (holder.admin) {
            if (!player.hasPermission("starfinbedwars.admin")
                    && !player.hasPermission("starfinbedwars.command.arena")) {
                player.sendMessage(ChatColor.RED + "Voce nao tem permissao administrativa.");
                return;
            }
            if (matches.getMatchForPlayer(player.getUniqueId()) != null
                    || matches.isSpectating(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Saia da partida antes de iniciar a edicao.");
                return;
            }
            try {
                editing.start(player, arena, new ArenaEditingService.Completion() {
                    @Override
                    public void complete(Exception error) {
                        if (error != null) {
                            player.sendMessage(ChatColor.RED + "Falha ao iniciar edicao: " + error.getMessage());
                            return;
                        }
                        player.sendMessage(ChatColor.GREEN + "Sessao de edicao pronta.");
                        if (wizard != null) {
                            try {
                                wizard.continueSetup(player, arena);
                            } catch (java.io.IOException | RuntimeException setupError) {
                                player.sendMessage(ChatColor.RED + "Nao foi possivel continuar o setup: "
                                    + setupError.getMessage());
                                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                    "Falha ao continuar setup da arena " + arena.getId() + ".", setupError);
                            }
                        }
                    }
                });
            } catch (RuntimeException error) {
                player.sendMessage(ChatColor.RED + "Falha ao iniciar edicao: " + error.getMessage());
            }
            return;
        }
        try {
            matches.join(player, arena.getId());
        } catch (RuntimeException error) {
            player.sendMessage(ChatColor.RED + "Nao foi possivel entrar: " + error.getMessage());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArenaMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void open(Player player, int page, boolean admin, List<ArenaDefinition> available) {
        int lastPage = Math.max(0, (available.size() - 1) / PAGE_SIZE);
        render(player, Math.max(0, Math.min(page, lastPage)), admin, available);
    }

    private void render(Player player, int page, boolean admin) {
        List<ArenaDefinition> available = new ArrayList<ArenaDefinition>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (admin || arena.isEnabled()) available.add(arena);
        }
        open(player, page, admin, available);
    }

    private void render(Player player, int page, boolean admin, List<ArenaDefinition> available) {
        ArenaMenuHolder holder = new ArenaMenuHolder(page, admin);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            admin ? ChatColor.DARK_RED + "Administrar arenas" : ChatColor.BLUE + "Arenas BedWars");
        holder.inventory = inventory;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, available.size());
        for (int index = start; index < end; index++) {
            ArenaDefinition arena = available.get(index);
            int slot = index - start;
            holder.entries.put(slot, arena);
            Material material = admin
                ? (arena.isEnabled() ? Material.DIAMOND : Material.REDSTONE_BLOCK)
                : Material.BED;
            String status = arena.isEnabled()
                ? (matches.isArenaActive(arena.getId()) ? ChatColor.YELLOW + "Partida em andamento"
                    : ChatColor.GREEN + "Disponivel")
                : ChatColor.RED + "Desabilitada";
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Mundo: " + arena.getSourceWorld());
            lore.add(ChatColor.GRAY + "Equipes: " + arena.getTeams().size()
                + " | capacidade: " + arena.getMaximumPlayers());
            lore.add(status);
            lore.add(admin ? ChatColor.YELLOW + "Clique para iniciar/retomar a edicao."
                : ChatColor.YELLOW + "Clique para entrar na fila.");
            if (admin && wizard != null) {
                lore.add(ChatColor.GRAY + wizard.getMenuSetupHint(player, arena));
            }
            inventory.setItem(slot, item(material, ChatColor.AQUA + arena.getDisplayName(), lore));
        }
        if (page > 0) inventory.setItem(45,
            item(Material.ARROW, ChatColor.YELLOW + "Pagina anterior", new ArrayList<String>()));
        inventory.setItem(49, item(Material.BARRIER, ChatColor.RED + "Fechar",
            new ArrayList<String>()));
        if (end < available.size()) inventory.setItem(53,
            item(Material.ARROW, ChatColor.YELLOW + "Proxima pagina", new ArrayList<String>()));
        if (available.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, ChatColor.GRAY + "Nenhuma arena disponivel",
                new ArrayList<String>()));
        }
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String title, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class ArenaMenuHolder implements InventoryHolder {
        private final int page;
        private final boolean admin;
        private final Map<Integer, ArenaDefinition> entries = new HashMap<Integer, ArenaDefinition>();
        private Inventory inventory;

        private ArenaMenuHolder(int page, boolean admin) {
            this.page = page;
            this.admin = admin;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
