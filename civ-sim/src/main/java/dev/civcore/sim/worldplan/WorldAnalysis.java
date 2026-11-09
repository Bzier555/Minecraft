package dev.civcore.sim.worldplan;

import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.topology.TileMapping;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;
import dev.civcore.sim.worldplan.TerrainSampler.Sample;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills {@link StrategicMap#terrain} by sampling the world. Incremental: the server calls {@link #step} with a small
 * tile budget each tick so analysis never stalls the game. Deterministic for a given sampler.
 */
public final class WorldAnalysis {
    /** Samples per tile edge (4 → 16 samples per 64-block tile, one every 16 blocks). */
    public static final int SAMPLES_PER_EDGE = 4;

    private final GameState state;
    private final TerrainSampler sampler;
    private int next; // next tile index to analyse
    private boolean done;

    public WorldAnalysis(GameState state, TerrainSampler sampler) {
        this.state = state;
        this.sampler = sampler;
        state.map.ensureTerrainArrays();
    }

    public boolean done() {
        return done;
    }

    public int analysedTiles() {
        return next;
    }

    public int totalTiles() {
        return state.map.width * state.map.height;
    }

    /** Analyses up to {@code tileBudget} tiles; returns true when the whole map (including coasts) is finished. */
    public boolean step(int tileBudget) {
        if (done) return true;
        StrategicMap map = state.map;
        TileMapping m = state.settings.mapping();
        int total = totalTiles();
        int end = Math.min(total, next + tileBudget);
        for (; next < end; next++) {
            TilePos p = new TilePos(next % map.width, next / map.width);
            TerrainClassifier.TileTerrain t = classifyTile(m, sampler, p);
            map.setTerrain(p, t.type(), t.river());
        }
        if (next >= total) {
            finish(state);
            done = true;
        }
        return done;
    }

    /** Samples and classifies one tile. Thread-safe if the sampler is; used by the mod's parallel analysis. */
    public static TerrainClassifier.TileTerrain classifyTile(TileMapping m, TerrainSampler sampler, TilePos p) {
        int stride = m.tileSize() / SAMPLES_PER_EDGE;
        int x0 = m.minBlockX() + p.x() * m.tileSize() + stride / 2;
        int z0 = m.minBlockZ() + p.z() * m.tileSize() + stride / 2;
        List<Sample> samples = new ArrayList<>(SAMPLES_PER_EDGE * SAMPLES_PER_EDGE);
        for (int i = 0; i < SAMPLES_PER_EDGE; i++) {
            for (int j = 0; j < SAMPLES_PER_EDGE; j++) {
                samples.add(sampler.sample(x0 + i * stride, z0 + j * stride));
            }
        }
        return TerrainClassifier.classify(samples);
    }

    /** Marks coasts and flags the map analysed, once every tile has terrain. */
    public static void finish(GameState state) {
        markCoasts(state.topology(), state.map);
        state.map.analysed = true;
        ResourcePlacer.place(state);
    }

    /** Water tiles next to land become COAST (shallow, workable, where ships will be built). */
    static void markCoasts(Topology topo, StrategicMap map) {
        List<TilePos> coasts = new ArrayList<>();
        for (int z = 0; z < map.height; z++) {
            for (int x = 0; x < map.width; x++) {
                TilePos p = new TilePos(x, z);
                if (map.terrain(p) != TerrainType.OCEAN) continue;
                for (TilePos n : topo.neighbors(p)) {
                    TerrainType nt = map.terrain(n);
                    if (nt != null && nt.land) {
                        coasts.add(p);
                        break;
                    }
                }
            }
        }
        for (TilePos p : coasts) map.setTerrain(p, TerrainType.COAST, false);
    }

    /** Runs the whole analysis at once (tests, headless runner). */
    public static void runAll(GameState state, TerrainSampler sampler) {
        WorldAnalysis a = new WorldAnalysis(state, sampler);
        while (!a.step(Integer.MAX_VALUE)) {
            // loop until done
        }
    }
}
