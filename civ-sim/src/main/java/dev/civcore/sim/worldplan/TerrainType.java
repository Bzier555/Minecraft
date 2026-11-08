package dev.civcore.sim.worldplan;

/**
 * Strategic terrain of a tile, with Civ-style base yields. Placeholder numbers until a data-driven terrain table
 * exists (backlog F1.3 follow-up).
 */
public enum TerrainType {
    OCEAN(1, 0, 1, false, true),
    COAST(1, 0, 1, false, true),
    GRASSLAND(2, 0, 0, true, true),
    PLAINS(1, 1, 0, true, true),
    DESERT(0, 0, 0, true, true),
    TUNDRA(1, 0, 0, true, true),
    SNOW(0, 0, 0, true, true),
    FOREST(1, 1, 0, true, true),
    JUNGLE(1, 0, 0, true, true),
    MARSH(1, 0, 0, true, true),
    HILLS(0, 2, 0, true, true),
    MOUNTAIN(0, 0, 0, true, false);

    public final int food;
    public final int production;
    public final int gold;
    public final boolean land;
    public final boolean passable;

    TerrainType(int food, int production, int gold, boolean land, boolean passable) {
        this.food = food;
        this.production = production;
        this.gold = gold;
        this.land = land;
        this.passable = passable;
    }

    /** A city centre can be founded here. */
    public boolean settleable() {
        return land && passable;
    }
}
