package dev.civcore.sim.rules;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.command.Command.QueueProduction;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.ResourcePlacer;
import dev.civcore.sim.worldplan.ResourceType;
import dev.civcore.sim.worldplan.TerrainType;
import dev.civcore.sim.worldplan.WorldAnalysis;
import dev.civcore.sim.ai.HeadlessGames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ResourcesTest {
    Ruleset rules;
    GameState g;
    Civilization alice;
    City roma;

    @BeforeEach
    void setUp() {
        rules = TestRulesets.shipped();
        g = GameState.newGame(GameSettings.centred("torus", 2048, 11L));
        WorldAnalysis.runAll(g, HeadlessGames.pangaea(11L));
        var start = dev.civcore.sim.worldplan.StartPlacer.place(g, 1).get(0);
        var m = g.settings.mapping();
        assertTrue(Simulation.apply(g, rules, new FoundCity("alice", "Rome", "Roma", m.centerBlockX(start), 64, m.centerBlockZ(start))).ok());
        alice = g.civOfMember("alice").orElseThrow();
        roma = g.cities.get(alice.capitalCityId);
    }

    @Test
    void placementIsDeterministicAndRespectsTerrainAndSeams() {
        assertNotNull(g.map.resource);
        int[] first = g.map.resource.clone();
        ResourcePlacer.place(g);
        assertArrayEquals(first, g.map.resource);
        long placed = Arrays.stream(first).filter(r -> r >= 0).count();
        assertTrue(placed > 20, "placed " + placed);
        for (int i = 0; i < first.length; i++) {
            if (first[i] < 0) continue;
            TilePos p = new TilePos(i % g.map.width, i / g.map.width);
            ResourceType r = ResourceType.values()[first[i]];
            assertTrue(r.terrains.contains(g.map.terrain(p)), r + " on " + g.map.terrain(p));
            assertTrue(g.topology().distanceToSeamOrEdge(p) >= g.settings.seamExclusionTiles());
        }
    }

    @Test
    void resourcesAddYields() {
        TilePos p = new TilePos(16, 16);
        g.map.setTerrain(p, TerrainType.COAST, false);
        g.map.resource[p.z() * g.map.width + p.x()] = ResourceType.FISH.ordinal();
        assertEquals(3, TileYields.of(g, p).food(), "coast 1 + fish 2");
    }

    void put(TilePos p, ResourceType r) {
        g.map.resource[p.z() * g.map.width + p.x()] = r.ordinal();
    }

    @Test
    void swordsmenNeedRevealedIronInsideBorders() {
        alice.knownTechs.add("civcore:agriculture");
        alice.knownTechs.add("civcore:mining");
        alice.knownTechs.add("civcore:bronze_working");
        Arrays.fill(g.map.resource, -1);
        CommandResult none = Simulation.apply(g, rules, new QueueProduction("alice", roma.id, "civcore:swordsmen"));
        assertFalse(none.ok());
        assertTrue(none.message().contains("iron"), none.message());
        TilePos next = new TilePos(roma.tileX + 1, roma.tileZ);
        put(next, ResourceType.IRON);
        assertEquals(1, Resources.available(g, rules, alice, ResourceType.IRON));
        assertTrue(Simulation.apply(g, rules, new QueueProduction("alice", roma.id, "civcore:swordsmen")).ok());
        assertFalse(Simulation.apply(g, rules, new QueueProduction("alice", roma.id, "civcore:swordsmen")).ok(), "one iron, one swordsman");
        alice.knownTechs.remove("civcore:bronze_working");
        assertEquals(0, Resources.owned(g, alice).getOrDefault(ResourceType.IRON, 0), "iron unknown without the tech");
    }

    @Test
    void luxuriesAndShrinesMakePeopleHappyAndUnhappinessSlowsGrowth() {
        Arrays.fill(g.map.resource, -1);
        int base = Resources.happiness(g, rules, alice); // 6 + palace 2 - 2 per city - pop/2
        assertEquals(6 + 2 - 2, base);
        put(new TilePos(roma.tileX + 1, roma.tileZ + 1), ResourceType.WINE);
        put(new TilePos(roma.tileX - 1, roma.tileZ), ResourceType.WINE);
        assertEquals(base + 4, Resources.happiness(g, rules, alice), "a luxury counts once");
        roma.buildings.add("civcore:shrine");
        assertEquals(base + 6, Resources.happiness(g, rules, alice));

        roma.buildings.clear();
        Arrays.fill(g.map.resource, -1);
        roma.population = 40; // very unhappy: 6 + 2 - 2 - 20 = -14
        assertTrue(Resources.happiness(g, rules, alice) <= Resources.VERY_UNHAPPY);
        assertFalse(Simulation.apply(g, rules, new QueueProduction("alice", roma.id, "civcore:settlers")).ok());
    }

    @Test
    void resourcesArePlacedForOldAnalysedSaves() {
        g.map.resource = null;
        GameState back = GameStateCodec.fromJson(GameStateCodec.toJson(g));
        assertNotNull(back.map.resource);
    }
}
