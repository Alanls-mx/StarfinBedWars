package com.starfinplugins.bedwars.domain.arena;

public final class ArenaRegion {
    private final String world;
    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int maximumX;
    private final int maximumY;
    private final int maximumZ;

    public ArenaRegion(String world, int firstX, int firstY, int firstZ,
                       int secondX, int secondY, int secondZ) {
        if (world == null || !world.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Mundo da regiao invalido.");
        }
        if (firstY < 0 || firstY > 255 || secondY < 0 || secondY > 255
                || Math.abs((long) firstX) > 30000000L || Math.abs((long) secondX) > 30000000L
                || Math.abs((long) firstZ) > 30000000L || Math.abs((long) secondZ) > 30000000L) {
            throw new IllegalArgumentException("Coordenadas da regiao fora dos limites do Minecraft 1.8.");
        }
        this.world = world;
        this.minimumX = Math.min(firstX, secondX);
        this.minimumY = Math.min(firstY, secondY);
        this.minimumZ = Math.min(firstZ, secondZ);
        this.maximumX = Math.max(firstX, secondX);
        this.maximumY = Math.max(firstY, secondY);
        this.maximumZ = Math.max(firstZ, secondZ);
    }

    public String getWorld() { return world; }
    public int getMinimumX() { return minimumX; }
    public int getMinimumY() { return minimumY; }
    public int getMinimumZ() { return minimumZ; }
    public int getMaximumX() { return maximumX; }
    public int getMaximumY() { return maximumY; }
    public int getMaximumZ() { return maximumZ; }
    public long getVolume() {
        return ((long) maximumX - minimumX + 1L)
            * ((long) maximumY - minimumY + 1L)
            * ((long) maximumZ - minimumZ + 1L);
    }

    public boolean contains(Position position) {
        return position != null && world.equals(position.getWorld())
            && position.getX() >= minimumX && position.getX() < (double) maximumX + 1.0
            && position.getY() >= minimumY && position.getY() < (double) maximumY + 1.0
            && position.getZ() >= minimumZ && position.getZ() < (double) maximumZ + 1.0;
    }
}
