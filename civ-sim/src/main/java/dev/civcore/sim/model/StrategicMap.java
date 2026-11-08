package dev.civcore.sim.model;

import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.TerrainType;

import java.util.Arrays;

/** Mutable per-tile state: ownership and (once the world is analysed) terrain. Row-major: index = z * width + x. */
public final class StrategicMap {
    public static final int NO_OWNER = -1;
    public static final int UNKNOWN_TERRAIN = -1;

    public int width;
    public int height;
    /** Owning civ id per tile, or NO_OWNER. */
    public int[] owner;
    /** TerrainType ordinal per tile, or UNKNOWN_TERRAIN until world analysis has run. */
    public int[] terrain;
    public boolean[] river;
    /** True once every tile has terrain (world analysis finished). */
    public boolean analysed;

    public StrategicMap() {
    }

    public StrategicMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.owner = new int[width * height];
        Arrays.fill(owner, NO_OWNER);
        ensureTerrainArrays();
    }

    /** Fills terrain arrays for saves written before terrain existed. */
    public void ensureTerrainArrays() {
        if (terrain == null || terrain.length != width * height) {
            terrain = new int[width * height];
            Arrays.fill(terrain, UNKNOWN_TERRAIN);
            analysed = false;
        }
        if (river == null || river.length != width * height) river = new boolean[width * height];
    }

    private int idx(TilePos p) {
        return p.z() * width + p.x();
    }

    public int owner(TilePos p) {
        return owner[idx(p)];
    }

    public void setOwner(TilePos p, int civId) {
        owner[idx(p)] = civId;
    }

    public int countOwnedBy(int civId) {
        int n = 0;
        for (int o : owner) if (o == civId) n++;
        return n;
    }

    /** Terrain, or null if not analysed yet. */
    public TerrainType terrain(TilePos p) {
        int t = terrain[idx(p)];
        return t == UNKNOWN_TERRAIN ? null : TerrainType.values()[t];
    }

    public void setTerrain(TilePos p, TerrainType type, boolean hasRiver) {
        terrain[idx(p)] = type.ordinal();
        river[idx(p)] = hasRiver;
    }

    public boolean river(TilePos p) {
        return river[idx(p)];
    }
}
