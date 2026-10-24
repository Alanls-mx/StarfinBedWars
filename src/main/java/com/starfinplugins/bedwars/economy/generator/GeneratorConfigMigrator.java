package com.starfinplugins.bedwars.economy.generator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;

/** Updates only unchanged legacy level-one defaults; custom values stay intact. */
final class GeneratorConfigMigrator {
    private GeneratorConfigMigrator() { }

    static boolean migrate(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (Exception error) { throw new IOException("generators.yml invalido para migracao.", error); }
        if (yaml.getInt("config-version", 0) >= 2) return false;
        boolean changed = false;
        if (yaml.getInt("resources.iron.levels.1.interval-ticks", -1) == 40) {
            yaml.set("resources.iron.levels.1.interval-ticks", 80);
            changed = true;
        }
        if (yaml.getInt("resources.gold.levels.1.interval-ticks", -1) == 200) {
            yaml.set("resources.gold.levels.1.interval-ticks", 300);
            changed = true;
        }
        if (!changed) return false;
        yaml.set("config-version", 2);
        byte[] original = Files.readAllBytes(file.toPath());
        File backup = new File(file.getParentFile(), "generators.yml.pre-slow-lv1.bak");
        if (!backup.exists()) Files.write(backup.toPath(), original);
        try { yaml.save(file); }
        catch (Exception error) {
            Files.write(file.toPath(), original);
            throw new IOException("Falha ao salvar intervalos dos geradores.", error);
        }
        return true;
    }
}
