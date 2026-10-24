package com.starfinplugins.bedwars.domain.arena;

public final class TeamDefinition {
    private final String id;
    private String displayName;
    private String color;
    private byte colorData;
    private final int capacity;
    private Position spawn;
    private Position respawn;
    private Position bed;
    private Position itemShop;
    private Position upgradeShop;
    private SpawnStructureDefinition spawnStructure;

    public TeamDefinition(String id, String displayName, String color, int capacity) {
        if (id == null || !id.matches("[a-z0-9_-]{1,24}")) {
            throw new IllegalArgumentException("team id invalido");
        }
        if (displayName == null || displayName.trim().isEmpty() || displayName.length() > 32) {
            throw new IllegalArgumentException("team name invalido");
        }
        if (color == null || !color.matches("[0-9a-f]")) {
            throw new IllegalArgumentException("team color invalido");
        }
        if (capacity < 1 || capacity > 100) {
            throw new IllegalArgumentException("team capacity deve estar entre 1 e 100");
        }
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.colorData = dataForChatColor(color);
        this.capacity = capacity;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public byte getColorData() { return colorData; }
    public int getCapacity() { return capacity; }
    public Position getSpawn() { return spawn; }
    public Position getRespawn() { return respawn; }
    public Position getBed() { return bed; }
    public Position getItemShop() { return itemShop; }
    public Position getUpgradeShop() { return upgradeShop; }
    public SpawnStructureDefinition getSpawnStructure() { return spawnStructure; }
    public void setSpawn(Position spawn) { this.spawn = spawn; }
    public void setRespawn(Position respawn) { this.respawn = respawn; }
    public void setBed(Position bed) { this.bed = bed; }
    public void setItemShop(Position itemShop) { this.itemShop = itemShop; }
    public void setUpgradeShop(Position upgradeShop) { this.upgradeShop = upgradeShop; }
    public void setSpawnStructure(SpawnStructureDefinition spawnStructure) { this.spawnStructure = spawnStructure; }
    public void setAppearance(String displayName, String color) {
        if (displayName == null || displayName.trim().isEmpty() || displayName.length() > 32
                || color == null || !color.matches("[0-9a-f]")) {
            throw new IllegalArgumentException("Aparencia de equipe invalida.");
        }
        this.displayName = displayName;
        this.color = color;
        this.colorData = dataForChatColor(color);
    }
    public void setAppearance(String displayName, String color, int colorData) {
        if (colorData < 0 || colorData > 15) throw new IllegalArgumentException("Cor de bloco invalida.");
        setAppearance(displayName, color);
        this.colorData = (byte) colorData;
    }

    private static byte dataForChatColor(String color) {
        switch (color) {
            case "f": return 0;
            case "6": return 1;
            case "d": return 2;
            case "b": return 3;
            case "e": return 4;
            case "a": return 5;
            case "8": return 7;
            case "7": return 8;
            case "3": return 9;
            case "5": return 10;
            case "9": return 11;
            case "2": return 13;
            case "c": return 14;
            case "0": return 15;
            default: return 0;
        }
    }
}
