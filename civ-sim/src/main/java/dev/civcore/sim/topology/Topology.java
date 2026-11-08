package dev.civcore.sim.topology;

import java.util.List;

/**
 * Shape of the strategic map. Every distance, adjacency and "near the edge" rule in Civ Core goes through this
 * interface so bounded, cylinder and torus maps are interchangeable (docs/D-architecture.md §5).
 *
 * <p>Tile coordinates run from 0 to width-1 / height-1. On a wrapped axis the seam lies between the last tile and 0.
 */
public interface Topology {
    int width();

    int height();

    boolean wrapsX();

    boolean wrapsZ();

    /** True if (x, z) is on the map after wrapping (always true on wrapped axes). */
    boolean contains(int x, int z);

    /** Canonical position for (x, z), or null if it falls off a bounded axis. */
    TilePos normalizeOrNull(int x, int z);

    /** Shortest signed delta from fromX to toX (negative = west). */
    int dx(int fromX, int toX);

    /** Shortest signed delta from fromZ to toZ (negative = north). */
    int dz(int fromZ, int toZ);

    /** Movement distance: octile metric (diagonal step = 1.5). */
    double distance(TilePos a, TilePos b);

    /** "Radius" distance: max(|dx|, |dz|). */
    int chebyshev(TilePos a, TilePos b);

    /** Up to 8 canonical neighbours, without duplicates (tiny wrapped maps can alias). */
    List<TilePos> neighbors(TilePos p);

    /** All canonical tiles within the given Chebyshev radius, without duplicates. */
    List<TilePos> within(TilePos center, int radius);

    /**
     * Distance in tiles to the nearest seam or map edge (0 = touching it). Used by the seam exclusion rule:
     * MineColonies and Recruits path with raw coordinates, so cities must stay away from seams
     * (docs/spike-platform.md §5b).
     */
    int distanceToSeamOrEdge(TilePos p);

    /**
     * The copy of {@code target} on a periodic axis (period {@code period}) that is closest to {@code observer}.
     * Use for every coordinate handed to another mod's pathfinding; the result may lie outside the canonical range,
     * which Exotic Worlds resolves.
     */
    static long nearestCopy(long target, long observer, long period) {
        if (period <= 0) return target;
        long k = Math.round((double) (observer - target) / period);
        return target + k * period;
    }

    static Topology bounded(int width, int height) {
        return new GridTopology(width, height, false, false);
    }

    static Topology cylinder(int width, int height) {
        return new GridTopology(width, height, true, false);
    }

    static Topology torus(int width, int height) {
        return new GridTopology(width, height, true, true);
    }
}
