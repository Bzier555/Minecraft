package dev.civcore.sim.worldplan;

import dev.civcore.sim.model.GameState;
import dev.civcore.sim.topology.TilePos;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Scatters resources over the analysed map, deterministically from the game seed. At most one resource per tile;
 * none within the seam exclusion band (no cities can work those tiles anyway).
 */
public final class ResourcePlacer {
    public static final int NONE = -1;

    private ResourcePlacer() {
    }

    public static void place(GameState s) {
        int n = s.map.width * s.map.height;
        int[] res = new int[n];
        Arrays.fill(res, NONE);
        SplittableRandom rng = new SplittableRandom(s.settings.seed() * 0x9E3779B97F4A7C15L + 17);
        for (int i = 0; i < n; i++) {
            TilePos p = new TilePos(i % s.map.width, i / s.map.width);
            TerrainType t = s.map.terrain(p);
            double roll = rng.nextDouble();
            if (t == null || s.topology().distanceToSeamOrEdge(p) < s.settings.seamExclusionTiles()) continue;
            double acc = 0;
            for (ResourceType r : ResourceType.values()) {
                if (!r.terrains.contains(t)) continue;
                acc += r.density;
                if (roll < acc) {
                    res[i] = r.ordinal();
                    break;
                }
            }
        }
        s.map.resource = res;
    }
}
