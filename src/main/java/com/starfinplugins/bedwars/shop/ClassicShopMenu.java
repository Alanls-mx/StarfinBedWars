package com.starfinplugins.bedwars.shop;

import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import com.starfinplugins.bedwars.shop.special.SpecialItemType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Six-row classic shop view. Purchases are still handled by ShopService. */
public final class ClassicShopMenu implements InventoryHolder {
    public enum Category {
        FAVORITES("Favoritos", Material.NETHER_STAR, 0),
        SWORDS("Espadas", Material.IRON_SWORD, 1),
        ARMOR("Armaduras", Material.DIAMOND_CHESTPLATE, 2),
        TOOLS("Ferramentas", Material.IRON_PICKAXE, 3),
        BLOCKS("Blocos", Material.WOOL, 4),
        BOWS("Arcos", Material.BOW, 5),
        POTIONS("Pocoes", Material.POTION, 6),
        UTILITIES("Utilitarios", Material.TNT, 7),
        SPECIAL("Especiais", Material.BLAZE_POWDER, 8);
        private final String title;
        private final Material icon;
        private final int slot;
        Category(String title, Material icon, int slot) {
            this.title = title; this.icon = icon; this.slot = slot;
        }
        public String getTitle() { return title; }
        public int getSlot() { return slot; }
        public Material getIcon() { return icon; }
    }

    private final Inventory inventory;
    private final Map<Integer, ShopItemDefinition> entries =
        new LinkedHashMap<Integer, ShopItemDefinition>();
    private final Map<Integer, Category> categorySlots = new LinkedHashMap<Integer, Category>();
    private Category category;
    private int page;
    private boolean editing;
    private int pageCount;
    private final int descriptionLineLength;

    public ClassicShopMenu(Player player, Iterable<ShopItemDefinition> items, Set<String> favorites,
                           Category category, int requestedPage, boolean editing,
                           List<Category> order, Map<Category, Material> icons) {
        this(player, items, favorites, category, requestedPage, editing, order, icons, 28);
    }

    public ClassicShopMenu(Player player, Iterable<ShopItemDefinition> items, Set<String> favorites,
                           Category category, int requestedPage, boolean editing,
                           List<Category> order, Map<Category, Material> icons, int descriptionLineLength) {
        this.descriptionLineLength = Math.max(18, Math.min(40, descriptionLineLength));
        this.inventory = Bukkit.createInventory(this, 54,
            ChatColor.DARK_AQUA + "Starfin " + ChatColor.DARK_GRAY + "| "
                + ChatColor.YELLOW + "Loja");
        refresh(player, items, favorites, category, requestedPage, editing, order, icons);
    }

    /**
     * Switch views inside the SAME Bukkit inventory. In 1.8.8, repeatedly
     * opening a new window after clicks may leave the client using an obsolete
     * window id and produce Container.getSlot(49) on a 45-slot player view.
     */
    public void refresh(Player player, Iterable<ShopItemDefinition> items, Set<String> favorites,
                        Category category, int requestedPage, boolean editing,
                        List<Category> order, Map<Category, Material> icons) {
        this.category = category;
        this.editing = editing;
        inventory.clear();
        entries.clear();
        categorySlots.clear();
        List<ShopItemDefinition> all = new ArrayList<ShopItemDefinition>();
        for (ShopItemDefinition item : items) all.add(item);
        Collections.sort(all, new java.util.Comparator<ShopItemDefinition>() {
            @Override public int compare(ShopItemDefinition left, ShopItemDefinition right) {
                return Integer.compare(left.getSlot(), right.getSlot());
            }
        });
        List<ShopItemDefinition> filtered = new ArrayList<ShopItemDefinition>();
        for (ShopItemDefinition item : all) {
            if (category == Category.FAVORITES ? favorites.contains(item.getId())
                    : categoryOf(item) == category) filtered.add(item);
        }
        this.pageCount = Math.max(1, (filtered.size() + 26) / 27);
        this.page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int tabSlot = 0;
        int activeSlot = -1;
        for (Category tab : order) {
            if (tabSlot >= 9) break;
            if (tab != Category.FAVORITES && !hasCategory(all, tab)) continue;
            categorySlots.put(tabSlot, tab);
            ItemStack categoryIcon = named(
                icons.containsKey(tab) ? icons.get(tab) : tab.getIcon(),
                tab == category ? ChatColor.GREEN + "" + ChatColor.BOLD + tab.getTitle()
                    : ChatColor.YELLOW + tab.getTitle());
            ItemMeta tabMeta = categoryIcon.getItemMeta();
            List<String> tabLore = new ArrayList<String>();
            tabLore.add(ChatColor.GRAY + "Categoria da loja");
            tabLore.add(tab == category
                ? ChatColor.GREEN + "Selecionada"
                : ChatColor.YELLOW + "Clique para visualizar");
            tabMeta.setLore(tabLore);
            categoryIcon.setItemMeta(tabMeta);
            inventory.setItem(tabSlot, categoryIcon);
            if (tab == category) activeSlot = tabSlot;
            tabSlot++;
        }
        for (int slot = 9; slot <= 17; slot++) {
            inventory.setItem(slot, namedPane(slot == activeSlot + 9 ? (short) 5 : (short) 7));
        }
        int first = page * 27;
        for (int index = first; index < Math.min(filtered.size(), first + 27); index++) {
            ShopItemDefinition definition = filtered.get(index);
            int slot = 18 + index - first;
            ItemStack display = new ItemStack(definition.getMaterial(), definition.getAmount(),
                definition.getData());
            ItemMeta meta = display.getItemMeta();
            boolean affordable = count(player, definition.getCostType().getMaterial()) >= definition.getCost();
            meta.setDisplayName((affordable ? ChatColor.GREEN : ChatColor.RED)
                + definition.getDisplayName());
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.DARK_GRAY + "STARFIN BEDWARS");
            lore.add(" ");
            String description = definition.getDescription().isEmpty()
                ? "Item para sua partida." : definition.getDescription();
            lore.addAll(ShopLoreFormatter.wrapDescription(description, descriptionLineLength));
            lore.add(ChatColor.GRAY + "Quantidade: " + ChatColor.WHITE + definition.getAmount());
            lore.add(ChatColor.GRAY + "Custo: " + ChatColor.GOLD + definition.getCost()
                + " " + currencyLabel(definition.getCostType().name()));
            lore.add(" ");
            lore.add(editing ? ChatColor.AQUA + "Clique para alterar favoritos"
                : affordable ? ChatColor.GREEN + "Clique para comprar"
                    : ChatColor.RED + "Recursos insuficientes");
            if (favorites.contains(definition.getId())) lore.add(ChatColor.YELLOW + "★ Favorito");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inventory.setItem(slot, display);
            entries.put(slot, definition);
        }
        for (int slot = 45; slot <= 53; slot++) {
            inventory.setItem(slot, namedPane((short) 15));
        }
        if (filtered.isEmpty()) {
            inventory.setItem(31, named(Material.BARRIER,
                ChatColor.RED + "Nenhum item nesta categoria"));
        }
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Anterior"));
        inventory.setItem(49, named(Material.BOOK, editing
            ? ChatColor.GREEN + "Concluir favoritos" : ChatColor.AQUA + "Editar favoritos"));
        inventory.setItem(50, named(Material.BARRIER, ChatColor.RED + "Fechar"));
        if (page + 1 < pageCount) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Proxima"));
    }

    private boolean hasCategory(List<ShopItemDefinition> items, Category tab) {
        for (ShopItemDefinition item : items) if (categoryOf(item) == tab) return true;
        return false;
    }

    public static Category categoryOf(ShopItemDefinition item) {
        String name = item.getMaterial().name();
        SpecialItemType special = SpecialItemType.byId(item.getId());
        if (special != null) {
            return special == SpecialItemType.FIREBALL ? Category.UTILITIES : Category.SPECIAL;
        }
        if (name.endsWith("_SWORD")) return Category.SWORDS;
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) return Category.ARMOR;
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || item.getMaterial() == Material.SHEARS)
            return Category.TOOLS;
        if (item.getMaterial() == Material.BOW || item.getMaterial() == Material.ARROW)
            return Category.BOWS;
        if (item.getMaterial() == Material.POTION) return Category.POTIONS;
        if (item.getMaterial() == Material.TNT || item.getMaterial() == Material.GOLDEN_APPLE)
            return Category.UTILITIES;
        if (item.getMaterial().isBlock()) return Category.BLOCKS;
        return Category.SPECIAL;
    }

    private static String currencyLabel(String name) {
        if ("IRON".equals(name)) return "Ferros";
        if ("GOLD".equals(name)) return "Ouros";
        if ("DIAMOND".equals(name)) return "Diamantes";
        if ("EMERALD".equals(name)) return "Esmeraldas";
        return name;
    }

    private static int count(Player player, Material material) {
        int amount = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) amount += stack.getAmount();
        }
        return amount;
    }
    private ItemStack named(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack namedPane(short data) {
        ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1, data);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
    public ShopItemDefinition getEntry(int slot) { return entries.get(slot); }
    public Category getCategoryAt(int slot) { return categorySlots.get(slot); }
    public Map<Integer, ShopItemDefinition> getEntries() { return Collections.unmodifiableMap(entries); }
    public Category getCategory() { return category; }
    public int getPage() { return page; }
    public boolean isEditing() { return editing; }
    public int getPageCount() { return pageCount; }
    @Override public Inventory getInventory() { return inventory; }
}
