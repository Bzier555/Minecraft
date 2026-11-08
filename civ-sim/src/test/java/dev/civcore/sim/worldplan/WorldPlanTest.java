package dev.civcore.sim.worldplan;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.rules.TileYields;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.TerrainSampler.BiomeClass;
import dev.civcore.sim.worldplan.TerrainSampler.Sample;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldPlanTest {

    /** Two continents in an ocean, with a mountain range and a desert band; deterministic. */
    static final TerrainSampler CONTINENTS = (x, z) -> {
        double a = Math.hypot(x + 400, z + 300), b = Math.hypot(x - 450, z - 350);
        double land = Math.max(1 - a / 420, 1 - b / 380);
        if (land <= 0) return new Sample(40, 63, BiomeClass.OCEAN);
        int h = 64 + (int) (land * 30);
        if (Math.abs(x + 400) < 40 && z < -250) return new Sample(150, 150, BiomeClass.MOUNTAIN);
        BiomeClass biome = z > 500 ? BiomeClass.DESERT : (x / 64 + z / 64) % 3 == 0 ? BiomeClass.FOREST : BiomeClass.PLAINS;
        return new Sample(h, h, biome);
    };

    static Sample s(int floor, int surface, BiomeClass b) {
        return new Sample(floor, surface, b);
    }

    @Test
    void classifierBasics() {
        assertEquals(TerrainType.OCEAN, TerrainClassifier.classify(Collections.nCopies(16, s(40, 63, BiomeClass.OCEAN))).type());
        assertEquals(TerrainType.MOUNTAIN, TerrainClassifier.classify(Collections.nCopies(16, s(160, 160, BiomeClass.OTHER))).type());
        assertEquals(TerrainType.DESERT, TerrainClassifier.classify(Collections.nCopies(16, s(70, 70, BiomeClass.DESERT))).type());
        assertEquals(TerrainType.FOREST, TerrainClassifier.classify(Collections.nCopies(16, s(70, 70, BiomeClass.TAIGA))).type());
        List<Sample> rough = new java.util.ArrayList<>(Collections.nCopies(8, s(66, 66, BiomeClass.PLAINS)));
        rough.addAll(Collections.nCopies(8, s(90, 90, BiomeClass.PLAINS)));
        assertEquals(TerrainType.HILLS, TerrainClassifier.classify(rough).type());
        List<Sample> riverPlains = new java.util.ArrayList<>(Collections.nCopies(15, s(68, 68, BiomeClass.PLAINS)));
        riverPlains.add(s(62, 63, BiomeClass.RIVER));
        TerrainClassifier.TileTerrain rp = TerrainClassifier.classify(riverPlains);
        assertEquals(TerrainType.PLAINS, rp.type());
        assertTrue(rp.river());
    }

    GameState analysed() {
        GameState g = GameState.newGame(GameSettings.centred("torus", 2048, 1L));
        WorldAnalysis.runAll(g, CONTINENTS);
        return g;
    }

    @Test
    void analysisProducesOceanCoastAndLand() {
        GameState g = analysed();
        assertTrue(g.map.analysed);
        int ocean = 0, coast = 0, land = 0, mountain = 0;
        for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
            TerrainType t = g.map.terrain(new TilePos(x, z));
            assertNotNull(t);
            if (t == TerrainType.OCEAN) ocean++; else if (t == TerrainType.COAST) coast++; else if (t == TerrainType.MOUNTAIN) mountain++; else land++;
        }
        assertTrue(ocean > 0 && coast > 0 && land > 50 && mountain > 0, "ocean=" + ocean + " coast=" + coast + " land=" + land + " mountain=" + mountain);
        // seam tiles are ocean in this map (like spike seed 0)
        assertEquals(TerrainType.OCEAN, g.map.terrain(new TilePos(0, 16)));
    }

    @Test
    void incrementalAnalysisMatchesOneShot() {
        GameState a = analysed();
        GameState b = GameState.newGame(GameSettings.centred("torus", 2048, 1L));
        WorldAnalysis wa = new WorldAnalysis(b, CONTINENTS);
        int steps = 0;
        while (!wa.step(7)) steps++;
        assertTrue(steps > 100);
        assertArrayEquals(a.map.terrain, b.map.terrain);
    }

    @Test
    void startsAreGoodSeparatedAndAwayFromSeams() {
        GameState g = analysed();
        List<TilePos> starts = StartPlacer.place(g, 4);
        assertEquals(4, starts.size());
        for (TilePos p : starts) {
            assertTrue(g.map.terrain(p).settleable(), p + " is " + g.map.terrain(p));
            assertTrue(g.topology().distanceToSeamOrEdge(p) >= g.settings.seamExclusionTiles());
        }
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < starts.size(); i++) for (int j = i + 1; j < starts.size(); j++) {
            minDist = Math.min(minDist, g.topology().distance(starts.get(i), starts.get(j)));
        }
        assertTrue(minDist >= 5, "starts too close: " + minDist + " " + starts);
    }

    @Test
    void cannotFoundOnWaterOrMountains() {
        GameState g = analysed();
        var rules = TestRulesets.shipped();
        CommandResult sea = Simulation.apply(g, rules, new FoundCity("a", "A", "Atlantis", -1024 + 64 * 5, 63, -1024 + 64 * 16));
        assertFalse(sea.ok());
        assertTrue(sea.message().contains("ocean") || sea.message().contains("coast"), sea.message());
        TilePos start = StartPlacer.place(g, 1).get(0);
        var m = g.settings.mapping();
        CommandResult ok = Simulation.apply(g, rules, new FoundCity("a", "A", "Capital", m.centerBlockX(start), 70, m.centerBlockZ(start)));
        assertTrue(ok.ok(), ok.message());
    }

    @Test
    void citiesWorkTheirBestTilesAndGrow() {
        GameState g = analysed();
        var rules = TestRulesets.shipped();
        TilePos start = StartPlacer.place(g, 1).get(0);
        var m = g.settings.mapping();
        assertTrue(Simulation.apply(g, rules, new FoundCity("a", "A", "Capital", m.centerBlockX(start), 70, m.centerBlockZ(start))).ok());
        var city = g.cities.values().iterator().next();
        assertEquals(1, TileYields.workedTiles(g, city).size());
        for (int i = 0; i < 60; i++) Simulation.strategicStep(g, rules);
        assertTrue(city.population >= 2, "capital should grow on good land, pop=" + city.population);
        assertEquals(Math.min(city.population, 8), TileYields.workedTiles(g, city).size(), "works one tile per citizen (only 8 owned)");
    }

    @Test
    void terrainSurvivesSaveRoundTrip() {
        GameState g = analysed();
        GameState back = GameStateCodec.fromJson(GameStateCodec.toJson(g));
        assertArrayEquals(g.map.terrain, back.map.terrain);
        assertTrue(back.map.analysed);
    }
}
