package dev.civcore.sim.rules;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CivicsEconomyTest {
    Ruleset rules;
    GameState g;
    Civilization alice;
    City roma;

    int bx(int t) {
        return -1024 + t * 64 + 32;
    }

    CommandResult apply(Command c) {
        return Simulation.apply(g, rules, c);
    }

    @BeforeEach
    void setUp() {
        rules = TestRulesets.shipped();
        g = GameState.newGame(GameSettings.centred("torus", 2048, 9L));
        assertTrue(apply(new Command.FoundCity("alice", "Rome", "Roma", bx(8), 64, bx(16))).ok());
        alice = g.civOfMember("alice").orElseThrow();
        roma = g.cities.get(alice.capitalCityId);
    }

    Army army(int tx, int tz, String type, int n) {
        Army a = new Army();
        a.id = g.allocateId();
        a.civId = alice.id;
        a.tileX = tx;
        a.tileZ = tz;
        for (int i = 0; i < n; i++) a.units.add(Military.newUnit(g, type));
        g.armies.put(a.id, a);
        return a;
    }

    @Test
    void governmentsNeedTechAndCauseAnarchy() {
        assertFalse(apply(new Command.ChangeGovernment("alice", "MONARCHY")).ok(), "needs currency");
        alice.knownTechs.add("civcore:currency");
        assertTrue(apply(new Command.ChangeGovernment("alice", "MONARCHY")).ok());
        assertTrue(Civics.modifiers(g, alice).anarchy());
        int goldBefore = alice.gold;
        Simulation.strategicStep(g, rules);
        assertEquals(goldBefore, alice.gold, "no gold during anarchy");
        Simulation.strategicStep(g, rules);
        Simulation.strategicStep(g, rules);
        assertFalse(Civics.modifiers(g, alice).anarchy());
        assertEquals(2, Civics.modifiers(g, alice).happiness());
    }

    @Test
    void policiesCostCultureAndNeedSlots() {
        assertFalse(apply(new Command.AdoptPolicy("alice", "CIVIC_PRIDE")).ok(), "no culture yet");
        alice.culture = 1000;
        assertTrue(apply(new Command.AdoptPolicy("alice", "CIVIC_PRIDE")).ok());
        assertEquals(60, alice.cultureSpent);
        assertFalse(apply(new Command.AdoptPolicy("alice", "SCHOLASTICISM")).ok(), "despotism has one slot");
        int happy = Resources.happiness(g, rules, alice);
        alice.policies.clear();
        assertEquals(happy - 3, Resources.happiness(g, rules, alice));
    }

    @Test
    void upkeepAndBankruptcyDesertion() {
        army(8, 16, "civcore:warriors", 6); // 6 units, 2 free with one city
        assertEquals(4, Economy.upkeep(g, alice, Civics.modifiers(g, alice)));
        alice.gold = 0;
        var events = new ArrayList<String>();
        Simulation.strategicStep(g, rules);
        assertTrue(alice.gold >= 0);
        assertTrue(Economy.militaryUnits(g, alice) < 6, "a unit deserted");
        alice.policies.add("PROFESSIONAL_ARMY");
        assertEquals(Economy.upkeep(g, alice, Civics.modifiers(g, alice)), (Economy.militaryUnits(g, alice) - 2) / 2);
        assertNotNull(events);
    }

    @Test
    void armiesFarFromHomeStarve() {
        Army far = army(24, 16, "civcore:warriors", 1);
        assertFalse(Military.inSupply(g, far));
        Simulation.strategicStep(g, rules);
        assertEquals(Army.FULL_HP - Military.ATTRITION, far.units.get(0).hp);
        Army near = army(10, 16, "civcore:warriors", 1);
        near.units.get(0).hp = 50;
        Simulation.strategicStep(g, rules);
        assertTrue(near.units.get(0).hp > 50, "in supply: heals");
    }

    @Test
    void connectedCitiesPayGold() {
        assertTrue(apply(new Command.FoundCity("alice", "Rome", "Ostia", bx(12), 64, bx(16))).ok());
        assertEquals(Economy.CONNECTION_GOLD, Economy.connectionGold(g, alice));
    }

    @Test
    void scienceProjectsAreNationalOrderedAndWin() {
        for (var t : rules.techs().keySet()) alice.knownTechs.add(t);
        assertFalse(apply(new Command.QueueProduction("alice", roma.id, "civcore:steam_expedition")).ok(), "in order");
        assertTrue(apply(new Command.QueueProduction("alice", roma.id, "civcore:great_survey")).ok());
        assertTrue(apply(new Command.FoundCity("alice", "Rome", "Ostia", bx(12), 64, bx(16))).ok());
        City ostia = g.cities.values().stream().filter(c -> c.name.equals("Ostia")).findFirst().orElseThrow();
        assertFalse(apply(new Command.QueueProduction("alice", ostia.id, "civcore:great_survey")).ok(), "national: once");
        roma.productionQueue.clear();
        roma.buildings.add("civcore:great_survey");
        roma.buildings.add("civcore:steam_expedition");
        assertEquals(2, Victory.scienceProjectsBuilt(g, rules, alice));
        roma.buildings.add("civcore:world_exhibition");
        Simulation.strategicStep(g, rules);
        assertEquals("science", g.victoryType);
    }

    @Test
    void cultureVictory() {
        g.cultureVictoryCulture = 100;
        alice.culture = 99;
        Simulation.strategicStep(g, rules);
        assertEquals("culture", g.victoryType);
    }

    @Test
    void disabledVictoryTypesDoNotEndTheGame() {
        g.victoryTypes.remove("culture");
        g.cultureVictoryCulture = 10;
        alice.culture = 1000;
        Simulation.strategicStep(g, rules);
        assertFalse(g.finished());
    }

    @Test
    void scoutsKeepTheirOwnStack() {
        var a = Military.spawnUnit(g, roma, "civcore:warriors");
        var b = Military.spawnUnit(g, roma, "civcore:scout");
        assertNotEquals(a.id, b.id);
        Simulation.strategicStep(g, rules); // idle merge must not join them
        assertTrue(g.armies.containsKey(a.id) && g.armies.containsKey(b.id));
    }

    @Test
    void civicsSurviveSaveRoundTrip() {
        alice.government = "REPUBLIC";
        alice.policies.add("SCHOLASTICISM");
        GameState back = GameStateCodec.fromJson(GameStateCodec.toJson(g));
        assertEquals("REPUBLIC", back.civs.get(alice.id).government);
        assertEquals(1, back.civs.get(alice.id).policies.size());
    }
}
