package dev.civcore.sim.worldplan;

import dev.civcore.sim.model.GameState;
import dev.civcore.sim.rules.TileYields;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks balanced, well-separated starting tiles: good land (yields within reach of a capital), away from seams,
 * spread out by farthest-point selection on the topology.
 */
public final class StartPlacer {
    /** A start must score at least this fraction of the best start on the map. */
    public static final double MIN_QUALITY = 0.6;

    private StartPlacer() {
    }

    /** Sum of the best 6 tile yields within work radius (food weighted); -1 if the centre is not settleable. */
    public static int score(GameState s, TilePos p) {
        TerrainType t = s.map.terrain(p);
        if (t == null || !t.settleable()) return -1;
        if (s.topology().distanceToSeamOrEdge(p) < s.settings.seamExclusionTiles()) return -1;
        List<Integer> values = new ArrayList<>();
        for (TilePos n : s.topology().within(p, TileYields.WORK_RADIUS)) {
            if (n.equals(p)) continue;
            TileYields.Yield y = TileYields.of(s, n);
            TerrainType nt = s.map.terrain(n);
            if (nt != null && !nt.passable) continue;
            values.add(y.food() * 3 + y.production() * 2 + y.gold());
        }
        values.sort(Comparator.reverseOrder());
        int sum = 0;
        for (int i = 0; i < Math.min(6, values.size()); i++) sum += values.get(i);
        return sum;
    }

    /** Up to {@code count} starts. Requires the map to be analysed. */
    public static List<TilePos> place(GameState s, int count) {
        if (!s.map.analysed) throw new IllegalStateException("world analysis has not finished");
        Topology topo = s.topology();
        List<TilePos> candidates = new ArrayList<>();
        int best = 0;
        int[] scores = new int[s.map.width * s.map.height];
        for (int z = 0; z < s.map.height; z++) {
            for (int x = 0; x < s.map.width; x++) {
                int sc = score(s, new TilePos(x, z));
                scores[z * s.map.width + x] = sc;
                best = Math.max(best, sc);
            }
        }
        int threshold = (int) Math.ceil(best * MIN_QUALITY);
        for (int z = 0; z < s.map.height; z++) {
            for (int x = 0; x < s.map.width; x++) {
                if (scores[z * s.map.width + x] >= threshold && best > 0) candidates.add(new TilePos(x, z));
            }
        }
        List<TilePos> starts = new ArrayList<>();
        if (candidates.isEmpty()) return starts;
        // first start: best score (ties: lowest z, x)
        candidates.sort(Comparator.<TilePos>comparingInt(p -> -scores[p.z() * s.map.width + p.x()])
            .thenComparingInt(TilePos::z).thenComparingInt(TilePos::x));
        starts.add(candidates.get(0));
        while (starts.size() < count) {
            TilePos far = null;
            double farDist = -1;
            for (TilePos c : candidates) {
                double d = Double.MAX_VALUE;
                for (TilePos st : starts) d = Math.min(d, topo.distance(c, st));
                if (d > farDist) {
                    farDist = d;
                    far = c;
                }
            }
            if (far == null || farDist < s.settings.minCitySpacing()) break;
            starts.add(far);
        }
        return starts;
    }
}
