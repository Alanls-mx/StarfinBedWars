package com.starfinplugins.bedwars.domain.match;

/** Permanent equipment levels for one participant in one match. */
public final class EquipmentProgress {
    private int armor;
    private int sword;
    private int axe = -1;
    private int pickaxe = -1;

    public int getArmor() { return armor; }
    public int getSword() { return sword; }
    public int getAxe() { return axe; }
    public int getPickaxe() { return pickaxe; }

    public boolean improveArmor(int level) {
        if (level <= armor || level > 3) return false;
        armor = level;
        return true;
    }
    public boolean improveSword(int level) {
        if (level <= sword || level > 3) return false;
        sword = level;
        return true;
    }
    public boolean improveAxe(int level) {
        if (level <= axe || level > 3) return false;
        axe = level;
        return true;
    }
    public boolean improvePickaxe(int level) {
        if (level <= pickaxe || level > 3) return false;
        pickaxe = level;
        return true;
    }

    public void downgrade(int swordMinimum, int toolMinimum) {
        if (sword > swordMinimum) sword--;
        if (axe > toolMinimum) axe--;
        if (pickaxe > toolMinimum) pickaxe--;
    }
}
