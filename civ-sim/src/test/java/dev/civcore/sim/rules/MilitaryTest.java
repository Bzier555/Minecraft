package dev.civcore.sim.rules;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command.DeclareWar;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.command.Command.MoveArmy;
import dev.civcore.sim.command.Command.ProposePeace;
import dev.civcore.sim.command.Command.QueueProduction;
import dev.civcore.sim.command.Command.SettleWithArmy;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MilitaryTest {
    static final String A = "alice", B = "bob";
    Ruleset rules;
    GameState g;
    Civilization alice, bob;
    City rome, carthage;

    /** Block coordinates of a tile centre on the 2,048 torus. */
    int bx(int tx) {
        return -1024 + tx * 64 + 32;
    }

    @BeforeEach
    void setUp() {
        rules = TestRulesets.shipped();
        g = GameState.newGame(GameSettings.centred("torus", 2048, 3L)); // unanalysed: every tile counts as plains
        ok(new FoundCity(A, "Rome", "Roma", bx(8), 64, bx(16)));
        ok(new FoundCity(B, "Carthage", "Qart", bx(16), 64, bx(16)));
        alice = g.civOfMember(A).orElseThrow();
        bob = g.civOfMember(B).orElseThrow();
        rome = g.cities.get(alice.capitalCityId);
        carthage = g.cities.get(bob.capitalCityId);
    }

    CommandResult ok(dev.civcore.sim.command.Command c) {
        CommandResult r = Simulation.apply(g, rules, c);
        assertTrue(r.ok(), r.message());
        return r;
    }

    Army army(Civilization civ, int tx, int tz, String type, int n) {
        Army a = new Army();
        a.id = g.allocateId();
        a.civId = civ.id;
        a.tileX = tx;
        a.tileZ = tz;
        for (int i = 0; i < n; i++) a.units.add(new Army.Unit(type));
        g.armies.put(a.id, a);
        return a;
    }

    void turns(int n) {
        for (int i = 0; i < n; i++) Simulation.strategicStep(g, rules);
    }

    @Test
    void trainedUnitsFormAnArmyAtTheCity() {
        ok(new QueueProduction(A, rome.id, "civcore:warriors"));
        for (int i = 0; i < 40 && g.armies.isEmpty(); i++) turns(1);
        Army a = g.armies.values().iterator().next();
        assertEquals(alice.id, a.civId);
        assertEquals(rome.tile(), a.tile());
        assertEquals("civcore:warriors", a.units.get(0).type);
    }

    @Test
    void armiesMoveAtTheirSpeed() {
        Army a = army(alice, 8, 10, "civcore:warriors", 2); // warriors: 2 moves
        ok(new MoveArmy(A, a.id, 8, 4));
        turns(1);
        assertEquals(new TilePos(8, 8), a.tile());
        turns(2);
        assertEquals(new TilePos(8, 4), a.tile());
        assertTrue(a.path.isEmpty());
    }

    @Test
    void closedBordersUntilWar() {
        Army a = army(alice, 11, 16, "civcore:spearmen", 3);
        assertFalse(Simulation.apply(g, rules, new MoveArmy(A, a.id, 16, 16)).ok(), "can't enter Carthage's land at peace");
        ok(new DeclareWar(A, bob.id));
        ok(new MoveArmy(A, a.id, 16, 16));
    }

    @Test
    void siegeCaptureAndDominationVictory() {
        ok(new DeclareWar(A, bob.id));
        Army a = army(alice, 14, 16, "civcore:spearmen", 6);
        ok(new MoveArmy(A, a.id, 16, 16));
        for (int i = 0; i < 40 && !g.finished(); i++) turns(1);
        assertEquals(alice.id, carthage.civId, "Carthage should fall");
        assertEquals(new TilePos(16, 16), a.tile(), "army occupies the captured city");
        assertTrue(g.map.owner(new TilePos(17, 16)) == alice.id, "tiles transfer");
        assertEquals(-1, bob.capitalCityId);
        assertTrue(bob.eliminated);
        assertTrue(g.finished());
        assertEquals(alice.id, g.winnerCivId);
        assertEquals("domination", g.victoryType);
        assertFalse(Simulation.apply(g, rules, new QueueProduction(A, rome.id, "civcore:warriors")).ok(), "no commands after the game ends");
    }

    @Test
    void strongerArmyWinsAField() {
        ok(new DeclareWar(B, alice.id));
        Army strong = army(alice, 12, 10, "civcore:spearmen", 5);
        Army weak = army(bob, 13, 10, "civcore:warriors", 2);
        ok(new MoveArmy(A, strong.id, 13, 10));
        for (int i = 0; i < 10 && g.armies.containsKey(weak.id); i++) turns(1);
        assertFalse(g.armies.containsKey(weak.id), "weak army destroyed");
        assertTrue(g.armies.containsKey(strong.id), "strong army survives");
    }

    @Test
    void peaceNeedsBothSides() {
        ok(new DeclareWar(A, bob.id));
        ok(new ProposePeace(A, bob.id));
        assertTrue(g.atWar(alice.id, bob.id));
        CommandResult r = ok(new ProposePeace(B, alice.id));
        assertTrue(r.message().contains("made peace"), r.message());
        assertFalse(g.atWar(alice.id, bob.id));
    }

    @Test
    void settlersFoundANewCity() {
        Army s = army(alice, 8, 16, Military.SETTLERS, 1);
        ok(new MoveArmy(A, s.id, 8, 21));
        turns(3);
        assertEquals(new TilePos(8, 21), s.tile());
        ok(new SettleWithArmy(A, s.id, "Ostia"));
        assertFalse(g.armies.containsKey(s.id), "settlers are used up");
        City ostia = g.cities.values().stream().filter(c -> c.name.equals("Ostia")).findFirst().orElseThrow();
        assertEquals(alice.id, ostia.civId);
        assertFalse(ostia.originalCapital);
        assertEquals(2, alice.cityIds.size());
        Army s2 = army(alice, 8, 11, Military.SETTLERS, 1);
        assertFalse(Simulation.apply(g, rules, new SettleWithArmy(A, s2.id, "ostia")).ok(), "city names are unique");
    }

    @Test
    void scoreVictoryAtTheTurnLimit() {
        g.turnLimit = 5;
        ok(new QueueProduction(A, rome.id, "civcore:monument"));
        turns(5);
        assertTrue(g.finished());
        assertEquals("score", g.victoryType);
    }

    @Test
    void oldSavesGetOriginalCapitalsOnLoad() {
        rome.originalCapital = false; // as written by a build before the field existed
        GameState back = GameStateCodec.fromJson(GameStateCodec.toJson(g));
        assertTrue(back.cities.get(rome.id).originalCapital);
        assertTrue(back.cities.get(carthage.id).originalCapital);
    }

    @Test
    void armiesSurviveSaveRoundTrip() {
        ok(new DeclareWar(A, bob.id));
        Army a = army(alice, 14, 16, "civcore:spearmen", 3);
        ok(new MoveArmy(A, a.id, 16, 16));
        turns(1);
        String json = GameStateCodec.toJson(g);
        GameState back = GameStateCodec.fromJson(json);
        assertEquals(json, GameStateCodec.toJson(back));
        assertTrue(back.atWar(alice.id, bob.id));
        assertEquals(List.of(a.id), List.copyOf(back.armies.keySet()));
    }
}
