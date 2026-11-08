package dev.civcore.sim.view;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViewTest {
    Ruleset rules;
    GameState g;
    int alice, bob;

    int bx(int t) {
        return -1024 + t * 64 + 32;
    }

    @BeforeEach
    void setUp() {
        rules = TestRulesets.shipped();
        g = GameState.newGame(GameSettings.centred("torus", 2048, 5L));
        assertTrue(Simulation.apply(g, rules, new FoundCity("alice", "Rome", "Roma", bx(8), 64, bx(8))).ok());
        assertTrue(Simulation.apply(g, rules, new FoundCity("bob", "Carthage", "Qart", bx(24), 64, bx(24))).ok());
        alice = g.civOfMember("alice").orElseThrow().id;
        bob = g.civOfMember("bob").orElseThrow().id;
        Visibility.update(g, Map.of());
    }

    ClientView view(int civ, List<TilePos> players) {
        return ViewBuilder.build(g, rules, civ, players, false, 30, false);
    }

    @Test
    void unexploredTilesAndUnmetCivsAreHidden() {
        ClientView v = view(alice, List.of());
        assertNotEquals(ClientView.UNEXPLORED, v.terrain[v.index(8, 8)], "own city tile is explored");
        assertEquals(ClientView.UNEXPLORED, v.terrain[v.index(24, 24)], "Carthage's tile is unexplored");
        assertEquals(-1, v.owner[v.index(24, 24)], "owner hidden too");
        assertTrue(v.cities.stream().noneMatch(c -> c.civId == bob), "enemy city unknown");
        assertTrue(v.civs.stream().noneMatch(c -> c.id == bob), "unmet civ unknown");
        assertNotNull(v.cities.get(0).available, "own city details present");
        assertFalse(v.cities.get(0).available.isEmpty());
    }

    @Test
    void enemyArmiesOnlyWhileVisibleAndCitiesOnceExplored() {
        Army scout = new Army();
        scout.id = g.allocateId();
        scout.civId = alice;
        scout.tileX = 22;
        scout.tileZ = 24;
        scout.units.add(new Army.Unit("civcore:scout"));
        g.armies.put(scout.id, scout);
        Army enemy = new Army();
        enemy.id = g.allocateId();
        enemy.civId = bob;
        enemy.tileX = 23;
        enemy.tileZ = 23;
        enemy.units.add(new Army.Unit("civcore:warriors"));
        g.armies.put(enemy.id, enemy);
        Visibility.update(g, Map.of());

        ClientView v = view(alice, List.of());
        assertTrue(v.cities.stream().anyMatch(c -> c.civId == bob && !c.mine), "Carthage now explored");
        assertTrue(v.civs.stream().anyMatch(c -> c.id == bob), "Carthage's civ met");
        assertTrue(v.armies.stream().anyMatch(a -> a.id == enemy.id), "enemy army in sight");
        assertTrue(v.cities.stream().filter(c -> c.civId == bob).allMatch(c -> c.available.isEmpty()), "no enemy city details");

        g.armies.remove(scout.id); // scout gone: Carthage stays explored, its army is no longer seen
        ClientView later = view(alice, List.of());
        assertTrue(later.cities.stream().anyMatch(c -> c.civId == bob));
        assertTrue(later.armies.stream().noneMatch(a -> a.id == enemy.id));
    }

    @Test
    void playersInTheWorldExplore() {
        ClientView before = view(alice, List.of());
        assertEquals(ClientView.UNEXPLORED, before.terrain[before.index(15, 15)]);
        Visibility.update(g, Map.of(alice, List.of(new TilePos(15, 15))));
        ClientView after = view(alice, List.of());
        assertNotEquals(ClientView.UNEXPLORED, after.terrain[after.index(15, 15)]);
    }

    @Test
    void viewAndExploredSurviveJsonRoundTrips() {
        ClientView v = view(alice, List.of());
        ClientView back = ViewBuilder.fromJson(ViewBuilder.toJson(v));
        assertArrayEquals(v.terrain, back.terrain);
        assertEquals(v.cities.size(), back.cities.size());
        GameState gs = GameStateCodec.fromJson(GameStateCodec.toJson(g));
        assertArrayEquals(g.explored.get(alice), gs.explored.get(alice));
    }
}
