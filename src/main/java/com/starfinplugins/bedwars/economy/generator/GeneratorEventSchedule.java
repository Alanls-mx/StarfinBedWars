package com.starfinplugins.bedwars.economy.generator;

import com.starfinplugins.bedwars.domain.economy.ResourceType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** One immutable schedule, evaluated against each match's own start time. */
public final class GeneratorEventSchedule {
    public enum Type { DIAMOND_UPGRADE, EMERALD_UPGRADE }

    public static final class Entry {
        private final int timeSeconds;
        private final Type type;
        private final int level;
        private Entry(int timeSeconds, Type type, int level) {
            this.timeSeconds = timeSeconds;
            this.type = type;
            this.level = level;
        }
        public int getTimeSeconds() { return timeSeconds; }
        public Type getType() { return type; }
        public int getLevel() { return level; }
        public ResourceType getResource() {
            return type == Type.DIAMOND_UPGRADE ? ResourceType.DIAMOND : ResourceType.EMERALD;
        }
    }

    private final List<Entry> entries;
    private final boolean enabled;
    private final boolean notifyPlayers;

    private GeneratorEventSchedule(List<Entry> entries, boolean enabled, boolean notifyPlayers) {
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.enabled = enabled;
        this.notifyPlayers = notifyPlayers;
    }

    public static GeneratorEventSchedule load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "generator-events.yml");
        if (!file.exists()) plugin.saveResource("generator-events.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (Exception error) { throw new IOException("generator-events.yml invalido.", error); }
        return parse(yaml);
    }

    static GeneratorEventSchedule parse(YamlConfiguration yaml) throws IOException {
        ConfigurationSection schedule = yaml.getConfigurationSection("events.schedule");
        if (schedule == null) throw new IOException("events.schedule ausente.");
        TreeSet<Integer> order = new TreeSet<Integer>();
        for (String key : schedule.getKeys(false)) {
            try {
                if (!order.add(Integer.parseInt(key))) throw new IOException("Evento duplicado: " + key);
            } catch (NumberFormatException error) { throw new IOException("Evento invalido: " + key, error); }
        }
        List<Entry> entries = new ArrayList<Entry>();
        int previousTime = -1;
        for (Integer index : order) {
            ConfigurationSection event = schedule.getConfigurationSection(index.toString());
            if (event == null || !event.isInt("time-seconds") || !event.isInt("level")) {
                throw new IOException("Evento incompleto: " + index);
            }
            int time = event.getInt("time-seconds");
            int level = event.getInt("level");
            Type type;
            try { type = Type.valueOf(event.getString("type", "")); }
            catch (IllegalArgumentException error) { throw new IOException("Tipo de evento invalido: " + index, error); }
            if (time <= previousTime || time < 1 || level < 2 || level > 10) {
                throw new IOException("Tempo ou nivel invalido no evento " + index);
            }
            previousTime = time;
            entries.add(new Entry(time, type, level));
        }
        return new GeneratorEventSchedule(entries,
            yaml.getBoolean("events.enabled", true), yaml.getBoolean("events.notify", true));
    }

    public List<Entry> getEntries() { return entries; }
    public boolean isEnabled() { return enabled; }
    public boolean isNotifyPlayers() { return notifyPlayers; }
}
