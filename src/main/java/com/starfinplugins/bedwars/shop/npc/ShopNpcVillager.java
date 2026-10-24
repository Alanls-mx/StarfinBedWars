package com.starfinplugins.bedwars.shop.npc;

import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityVillager;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import net.minecraft.server.v1_8_R3.World;

/** Silent and stationary server-side villager, advertised as vanilla type 120. */
final class ShopNpcVillager extends EntityVillager {
    ShopNpcVillager(World world) {
        super(world);
        this.goalSelector = new PathfinderGoalSelector(world.methodProfiler);
        this.targetSelector = new PathfinderGoalSelector(world.methodProfiler);
    }

    @Override public void makeSound(String sound, float volume, float pitch) { }

    @Override public void collide(Entity entity) { }

    @Override public boolean a(EntityHuman player) { return true; }
}
