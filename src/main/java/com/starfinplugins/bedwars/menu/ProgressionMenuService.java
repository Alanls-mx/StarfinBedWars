package com.starfinplugins.bedwars.menu;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.MatchStatisticsService;
import com.starfinplugins.bedwars.stats.Phase2Text;
import com.starfinplugins.bedwars.stats.RankingPage;
import com.starfinplugins.bedwars.stats.RankingSpec;
import com.starfinplugins.bedwars.stats.RewardsCatalog;
import com.starfinplugins.bedwars.stats.StatsProfile;
import com.starfinplugins.bedwars.stats.XpProgression;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** All inventory access stays on Bukkit's main thread; ranking data arrives by callback. */
public final class ProgressionMenuService implements ManagedService, Listener {
    private final StarfinBedWarsPlugin plugin;
    private final MatchStatisticsService stats;

    public ProgressionMenuService(StarfinBedWarsPlugin plugin, MatchStatisticsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }
    @Override public String getName() { return "menus de progressao"; }
    @Override public void start() { Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public void stop() { HandlerList.unregisterAll(this); }

    public void openProfile(Player player) {
        String lang = stats.getLanguage(player.getUniqueId());
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        if (profile == null) { loading(player, () -> openProfile(player)); return; }
        XpProgression.Progress xp = stats.getProgress(profile.totalXp);
        View holder = new View("profile", null);
        Inventory inv = Bukkit.createInventory(holder, 27, tr(lang, "profile"));
        holder.inventory = inv;
        inv.setItem(4, head(profile.name, ChatColor.AQUA + profile.name,
            ChatColor.GRAY + tr(lang, "level") + ": " + ChatColor.YELLOW + xp.level,
            ChatColor.GRAY + tr(lang, "selected_title") + ": " + ChatColor.WHITE
                + titleOrNone(player, lang)));
        inv.setItem(10, item(Material.EXP_BOTTLE, ChatColor.YELLOW + tr(lang, "progress"), 1,
            ChatColor.GRAY + tr(lang, "total_xp") + ": " + profile.totalXp,
            ChatColor.GRAY + tr(lang, "last_match_xp") + ": +"
                + stats.getCachedLastMatchXp(player.getUniqueId()),
            ChatColor.GRAY + tr(lang, "next_level") + ": " + xp.current + "/" + xp.required,
            ChatColor.GRAY + "XP: " + bar(xp.current, xp.required)));
        inv.setItem(12, item(Material.DIAMOND, ChatColor.GREEN + tr(lang, "wins") + ": " + profile.wins, 1,
            ChatColor.GRAY + tr(lang, "losses") + ": " + profile.losses,
            ChatColor.GRAY + tr(lang, "games") + ": " + profile.games,
            ChatColor.GRAY + tr(lang, "win_rate") + ": " + percent(profile.wins, profile.games)));
        inv.setItem(14, item(Material.IRON_SWORD, ChatColor.RED + tr(lang, "kills") + ": " + profile.kills, 1,
            ChatColor.GRAY + tr(lang, "final_kills") + ": " + profile.finalKills,
            ChatColor.GRAY + tr(lang, "beds") + ": " + profile.beds));
        inv.setItem(19, item(Material.BOOK, ChatColor.AQUA + tr(lang, "progress"), 1));
        inv.setItem(22, item(Material.NETHER_STAR, ChatColor.GOLD + tr(lang, "rewards"), 1));
        inv.setItem(25, item(Material.PAPER, ChatColor.AQUA + tr(lang, "ranking"), 1));
        player.openInventory(inv);
    }

    public void openProgress(Player player) {
        String lang = stats.getLanguage(player.getUniqueId());
        StatsProfile profile = stats.getCachedProfile(player.getUniqueId());
        if (profile == null) { loading(player, () -> openProgress(player)); return; }
        XpProgression.Progress xp = stats.getProgress(profile.totalXp);
        View holder = new View("progress", null);
        Inventory inv = Bukkit.createInventory(holder, 36, tr(lang, "progress"));
        holder.inventory = inv;
        inv.setItem(4, item(Material.EXP_BOTTLE, ChatColor.YELLOW + tr(lang, "level") + " " + xp.level, 1,
            ChatColor.GRAY + tr(lang, "total_xp") + ": " + profile.totalXp,
            ChatColor.GRAY + tr(lang, "last_match_xp") + ": +"
                + stats.getCachedLastMatchXp(player.getUniqueId()),
            ChatColor.GRAY + "XP: " + xp.current + "/" + xp.required,
            ChatColor.GRAY + tr(lang, "next_level") + ": " + Math.max(0, xp.required - xp.current),
            ChatColor.GREEN + bar(xp.current, xp.required)));
        int slot = 10;
        for (RewardsCatalog.TitleReward reward : stats.getRewards().titles()) {
            if (slot >= 26) break;
            boolean unlocked = stats.getCachedUnlocks(player.getUniqueId()).contains(reward.id);
            inv.setItem(slot++, rewardItem(reward, lang, unlocked));
        }
        inv.setItem(31, item(Material.NETHER_STAR, ChatColor.GOLD + tr(lang, "rewards"), 1));
        player.openInventory(inv);
    }

    public void openRewards(Player player) {
        String lang = stats.getLanguage(player.getUniqueId());
        View holder = new View("rewards", null);
        Inventory inv = Bukkit.createInventory(holder, 54, tr(lang, "rewards"));
        holder.inventory = inv;
        int slot = 10;
        for (RewardsCatalog.TitleReward reward : stats.getRewards().titles()) {
            if (slot >= 44) break;
            boolean unlocked = stats.getCachedUnlocks(player.getUniqueId()).contains(reward.id);
            inv.setItem(slot, rewardItem(reward, lang, unlocked));
            holder.rewardSlots[slot++] = reward.id;
        }
        for (java.util.Map.Entry<String, String> title : stats.getSeasonTitles(player.getUniqueId()).entrySet()) {
            if (slot >= 44) break;
            inv.setItem(slot, item(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + title.getValue(), 1,
                ChatColor.AQUA + "Temporada competitiva", ChatColor.GREEN + "Clique para selecionar"));
            holder.rewardSlots[slot++] = title.getKey();
        }
        inv.setItem(49, item(Material.ARROW, ChatColor.GRAY + tr(lang, "close"), 1));
        player.openInventory(inv);
    }

    public void openRanking(Player player, RankingSpec spec) {
        String lang = stats.getLanguage(player.getUniqueId());
        View holder = new View("ranking", spec);
        Inventory inv = Bukkit.createInventory(holder, 54, tr(lang, "ranking"));
        holder.inventory = inv;
        inv.setItem(22, item(Material.WATCH, ChatColor.YELLOW + tr(lang, "loading"), 1));
        player.openInventory(inv);
        stats.requestRanking(player, spec, (page, failure) -> {
            if (!player.isOnline()) return;
            if (failure != null || page == null) {
                inv.setItem(22, item(Material.BARRIER, ChatColor.RED + tr(lang, "unavailable"), 1));
                return;
            }
            renderRanking(player, holder, page);
        });
    }

    private void renderRanking(Player player, View holder, RankingPage page) {
        Inventory inv = holder.inventory;
        inv.clear();
        String lang = stats.getLanguage(player.getUniqueId());
        int slot = 0;
        for (RankingPage.Entry entry : page.entries) {
            if (slot >= 45) break;
            boolean self = player.getUniqueId().equals(entry.uuid);
            inv.setItem(slot++, head(entry.name,
                (self ? ChatColor.GREEN : ChatColor.AQUA) + "#" + entry.position + " " + entry.name,
                ChatColor.GRAY + tr(lang, "metric") + ": " + ChatColor.WHITE
                    + format(entry.value, page.spec.metric),
                ChatColor.GRAY + tr(lang, "level") + ": "
                    + stats.getProgress(entry.globalXp).level));
        }
        RankingSpec spec = holder.spec;
        inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + tr(lang, "previous"), 1));
        inv.setItem(46, item(Material.DIAMOND, ChatColor.AQUA + tr(lang, "metric") + ": "
            + Phase2Text.metric(lang, spec.metric), 1));
        inv.setItem(47, item(Material.COMPASS, ChatColor.AQUA + tr(lang, "mode") + ": "
            + (spec.mode == null ? tr(lang, "all") : spec.mode), 1));
        inv.setItem(48, item(Material.WATCH, ChatColor.AQUA + tr(lang, "period") + ": "
            + Phase2Text.period(lang, spec.period), 1));
        inv.setItem(49, item(Material.PAPER, ChatColor.YELLOW + tr(lang, "your_position") + ": "
            + (page.viewer == null ? "-" : "#" + page.viewer.position), 1,
            page.viewer == null ? "" : format(page.viewer.value, spec.metric),
                ChatColor.GRAY.toString() + spec.page + " / " + Math.max(1, (page.totalPlayers + spec.pageSize - 1) / spec.pageSize)));
        inv.setItem(53, item(Material.ARROW, ChatColor.YELLOW + tr(lang, "next"), 1));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof View)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClickedInventory() != top) return;
        Player player = (Player) event.getWhoClicked();
        View holder = (View) top.getHolder();
        int slot = event.getRawSlot();
        if ("profile".equals(holder.type)) {
            if (slot == 19) Bukkit.getScheduler().runTask(plugin, () -> openProgress(player));
            else if (slot == 22) Bukkit.getScheduler().runTask(plugin, () -> openRewards(player));
            else if (slot == 25) Bukkit.getScheduler().runTask(plugin, () -> openRanking(player,
                spec(RankingSpec.Metric.WINS, RankingSpec.Period.ALL, null, 1)));
        } else if ("progress".equals(holder.type)) {
            if (slot == 31) Bukkit.getScheduler().runTask(plugin, () -> openRewards(player));
        } else if ("rewards".equals(holder.type)) {
            if (slot == 49) Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            else if (slot >= 0 && slot < holder.rewardSlots.length && holder.rewardSlots[slot] != null) {
                String id = holder.rewardSlots[slot];
                if (stats.getCachedUnlocks(player.getUniqueId()).contains(id)
                        || stats.getSeasonTitles(player.getUniqueId()).containsKey(id))
                    stats.selectTitle(player, id);
                else player.sendMessage(ChatColor.RED + tr(stats.getLanguage(player.getUniqueId()), "locked"));
            }
        } else if ("ranking".equals(holder.type)) {
            RankingSpec old = holder.spec;
            RankingSpec.Metric metric = old.metric;
            RankingSpec.Period period = old.period;
            String mode = old.mode;
            int page = old.page;
            if (slot == 45) page = Math.max(1, page - 1);
            else if (slot == 53) page = Math.min(1000, page + 1);
            else if (slot == 46) metric = next(RankingSpec.Metric.values(), metric);
            else if (slot == 47) mode = nextMode(mode);
            else if (slot == 48) period = next(RankingSpec.Period.values(), period);
            else return;
            final RankingSpec nextSpec = spec(metric, period, mode,
                slot == 45 || slot == 53 ? page : 1);
            Bukkit.getScheduler().runTask(plugin, () -> openRanking(player, nextSpec));
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }

    public RankingSpec spec(RankingSpec.Metric metric, RankingSpec.Period period, String mode, int page) {
        return new RankingSpec(metric, period, mode, page,
            Math.min(45, stats.getDefaultRankingPageSize()), stats.getMaximumRankingPageSize(), Instant.now());
    }
    private static <T> T next(T[] values, T current) {
        return values[(Arrays.asList(values).indexOf(current) + 1) % values.length];
    }
    private static String nextMode(String current) {
        List<String> modes = Arrays.asList(null, "solo", "duo", "trio", "squad", "custom");
        return modes.get((modes.indexOf(current) + 1) % modes.size());
    }
    private static ItemStack rewardItem(RewardsCatalog.TitleReward reward, String lang, boolean unlocked) {
        return item(unlocked ? Material.NAME_TAG : Material.PAPER,
            (unlocked ? ChatColor.GREEN : ChatColor.RED) + reward.name(lang), 1,
            ChatColor.GRAY + tr(lang, "required_level") + ": " + reward.level,
            ChatColor.GRAY + (unlocked ? tr(lang, "unlocked") + " - " + tr(lang, "select")
                : tr(lang, "locked")));
    }
    private String titleOrNone(Player player, String lang) {
        String title = stats.getSelectedTitle(player.getUniqueId());
        return title.isEmpty() ? tr(lang, "none") : title;
    }
    private static String tr(String lang, String key) { return Phase2Text.text(lang, key); }
    private void loading(Player player, Runnable retry) {
        String lang = stats.getLanguage(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + tr(lang, "loading"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (stats.getCachedProfile(player.getUniqueId()) == null)
                player.sendMessage(ChatColor.RED + tr(lang, "unavailable"));
            else retry.run();
        }, 40L);
    }
    private static String percent(long wins, long games) {
        return games == 0 ? "0%" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * wins / games);
    }
    private static String format(double value, RankingSpec.Metric metric) {
        if (metric == RankingSpec.Metric.WIN_RATE)
            return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0);
        return value == Math.rint(value) ? Long.toString((long) value)
            : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    private static String bar(long current, long required) {
        int filled = required <= 0 ? 0 : (int) Math.min(10, 10 * current / required);
        return ChatColor.GREEN + String.join("", Collections.nCopies(filled, "|"))
            + ChatColor.DARK_GRAY + String.join("", Collections.nCopies(10 - filled, "|"));
    }
    private static ItemStack item(Material material, String name, int amount, String... lore) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<String>(Arrays.asList(lore)));
        stack.setItemMeta(meta);
        return stack;
    }
    private static ItemStack head(String owner, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwner(owner);
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<String>(Arrays.asList(lore)));
        stack.setItemMeta(meta);
        return stack;
    }
    private static final class View implements InventoryHolder {
        final String type;
        final RankingSpec spec;
        final String[] rewardSlots = new String[54];
        Inventory inventory;
        View(String type, RankingSpec spec) { this.type = type; this.spec = spec; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
