package com.starfinplugins.bedwars.integration.worldedit;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import java.lang.reflect.Method;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class WorldEditSelectionAdapter {
    private final PluginManager pluginManager;

    public WorldEditSelectionAdapter(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public ArenaRegion getSelection(Player player, String expectedWorld) throws Exception {
        Plugin worldEdit = pluginManager.getPlugin("WorldEdit");
        if (worldEdit == null || !worldEdit.isEnabled()) {
            throw new IllegalStateException("WorldEdit 6.x nao esta instalado.");
        }
        String version = worldEdit.getDescription().getVersion();
        if (version == null || !version.startsWith("6.")) {
            throw new IllegalStateException("Esta integracao suporta somente WorldEdit 6.x.");
        }
        Object selection = invoke(worldEdit, "getSelection", new Class<?>[] {Player.class}, player);
        if (selection == null) throw new IllegalStateException("WorldEdit nao possui selecao completa.");
        Object worldValue = invoke(selection, "getWorld", new Class<?>[0]);
        if (!(worldValue instanceof World)) {
            throw new IllegalStateException("WorldEdit retornou um mundo incompativel.");
        }
        World world = (World) worldValue;
        if (!world.getName().equals(expectedWorld)) {
            throw new IllegalArgumentException("Selecione a regiao dentro da instancia de edicao da arena.");
        }
        Object minimum = invoke(selection, "getMinimumPoint", new Class<?>[0]);
        Object maximum = invoke(selection, "getMaximumPoint", new Class<?>[0]);
        return new ArenaRegion(expectedWorld,
            coordinate(minimum, "getBlockX"), coordinate(minimum, "getBlockY"),
            coordinate(minimum, "getBlockZ"), coordinate(maximum, "getBlockX"),
            coordinate(maximum, "getBlockY"), coordinate(maximum, "getBlockZ"));
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, arguments);
    }

    private int coordinate(Object vector, String method) throws Exception {
        if (vector == null) throw new IllegalStateException("Selecao WorldEdit incompleta.");
        return ((Number) invoke(vector, method, new Class<?>[0])).intValue();
    }
}
