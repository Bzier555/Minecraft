package dev.civcore.sim.topology;

/**
 * Converts between block coordinates and strategic tiles. Tile (0,0) starts at (minBlockX, minBlockZ), which is the
 * world's minimum corner (e.g. -1024,-1024 for a 2,048-block Exotic Worlds torus), so seams fall on tile borders.
 */
public record TileMapping(int minBlockX, int minBlockZ, int tileSize) {

    public TileMapping {
        if (tileSize < 1) throw new IllegalArgumentException("tileSize must be positive");
    }

    /** Raw (not wrapped) tile coordinate containing the block; normalise with a {@link Topology}. */
    public int tileX(int blockX) {
        return Math.floorDiv(blockX - minBlockX, tileSize);
    }

    public int tileZ(int blockZ) {
        return Math.floorDiv(blockZ - minBlockZ, tileSize);
    }

    public int centerBlockX(TilePos p) {
        return minBlockX + p.x() * tileSize + tileSize / 2;
    }

    public int centerBlockZ(TilePos p) {
        return minBlockZ + p.z() * tileSize + tileSize / 2;
    }
}
