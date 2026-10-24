package com.starfinplugins.bedwars.domain.arena;

public final class AutomaticArenaBounds {
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int width;
    private final int depth;
    private final int upHeight;
    private final int downDepth;
    private final ArenaRegion region;

    public AutomaticArenaBounds(String world, int centerX, int centerY, int centerZ,
                                int width, int depth, int upHeight, int downDepth,
                                int maximumDimension) {
        if (world == null || width < 1 || depth < 1 || upHeight < 0 || downDepth < 0
                || maximumDimension < 1 || width > maximumDimension || depth > maximumDimension) {
            throw new IllegalArgumentException("Dimensoes automaticas invalidas.");
        }
        long minimumX = (long) centerX - width / 2L;
        long maximumX = minimumX + width - 1L;
        long minimumZ = (long) centerZ - depth / 2L;
        long maximumZ = minimumZ + depth - 1L;
        long minimumY = (long) centerY - downDepth;
        long maximumY = (long) centerY + upHeight;
        if (minimumX < -30000000L || maximumX > 30000000L
                || minimumZ < -30000000L || maximumZ > 30000000L) {
            throw new IllegalArgumentException("Limites horizontais ultrapassam o limite do mundo.");
        }
        if (centerY < 0 || centerY > 255 || minimumY < 0L || maximumY > 255L) {
            throw new IllegalArgumentException("POS1/POS2 verticais calculados seriam Y=" + minimumY
                + " e Y=" + maximumY + "; ajuste uplow para no maximo " + centerY
                + " e upheight para no maximo " + (255 - centerY) + " com este centro.");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.width = width;
        this.depth = depth;
        this.upHeight = upHeight;
        this.downDepth = downDepth;
        this.region = new ArenaRegion(world, (int) minimumX, (int) minimumY, (int) minimumZ,
            (int) maximumX, (int) maximumY, (int) maximumZ);
    }

    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public int getCenterZ() { return centerZ; }
    public int getWidth() { return width; }
    public int getDepth() { return depth; }
    public int getUpHeight() { return upHeight; }
    public int getDownDepth() { return downDepth; }
    public ArenaRegion getRegion() { return region; }
    public long getVolume() { return region.getVolume(); }

    public Position getPos1() {
        return new Position(region.getWorld(), region.getMinimumX(), region.getMinimumY(),
            region.getMinimumZ(), 0.0F, 0.0F);
    }

    public Position getPos2() {
        return new Position(region.getWorld(), region.getMaximumX(), region.getMaximumY(),
            region.getMaximumZ(), 0.0F, 0.0F);
    }
}
