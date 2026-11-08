package dev.civcore.sim.engine;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.command.Command.QueueProduction;
import dev.civcore.sim.command.Command.SetResearch;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.io.GameStateCodec;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationTest {
    static final String ALICE = "11111111-1111-1111-1111-111111111111";
    static final String BOB = "22222222-2222-2222-2222-222222222222";

    Ruleset rules;
    GameState game;

    @BeforeEach
    void setUp() {
        rules = TestRulesets.shipped();
        // the spike world: 2,048-block torus, 32x32 tiles of 64 blocks
        game = GameState.newGame(GameSettings.centred("torus", 2048, 42L));
    }

    CommandResult found(String who, String city, int x, int z) {
        return Simulation.apply(game, rules, new FoundCity(who, who.equals(ALICE) ? "Rome" : "Carthage", city, x, 64, z));
    }

    @Test
    void firstFoundingCreatesCivCapitalAndBorders() {
        CommandResult r = found(ALICE, "Roma", 0, 0);
        assertTrue(r.ok(), r.message());
        Civilization rome = game.civOfMember(ALICE).orElseThrow();
        City capital = game.cities.get(rome.capitalCityId);
        assertEquals("Roma", capital.name);
        assertTrue(capital.buildings.contains("civcore:palace"));
        assertEquals(9, game.map.countOwnedBy(rome.id), "capital claims a 3x3 tile area");
    }

    @Test
    void citiesMustBeSpacedApart() {
        assertTrue(found(ALICE, "Roma", 0, 0).ok());
        CommandResult tooClose = found(ALICE, "Ostia", 64 * 2, 0);
        assertFalse(tooClose.ok());
        assertTrue(tooClose.message().contains("Too close to Roma"), tooClose.message());
        assertTrue(found(ALICE, "Ostia", 64 * 3, 0).ok());
    }

    @Test
    void citiesCannotBeFoundedNearTheSeam() {
        // x = 1000 is tile 31: touching the east/west seam (the spike's builder-pathing finding)
        CommandResult r = found(ALICE, "Edge", 1000, 0);
        assertFalse(r.ok());
        assertTrue(r.message().contains("edge of the world"), r.message());
        // spacing is measured across the seam too: tiles 3 and 28 are 7 apart going west
        assertTrue(found(ALICE, "West", -1024 + 64 * 3, 0).ok());
        assertTrue(found(BOB, "East", -1024 + 64 * 28, 0).ok());
    }

    @Test
    void cannotFoundOnAnotherCivsLand() {
        assertTrue(found(ALICE, "Roma", 0, 0).ok());
        // tile next to Roma (inside its claim) but far enough? spacing fails first, so check ownership with spacing 1
        game.settings = new GameSettings("torus", 32, 32, 64, -1024, -1024, 42L, 60, 1, 3);
        CommandResult r = found(BOB, "Spy", 64, 0);
        assertFalse(r.ok());
        assertTrue(r.message().contains("belongs to Rome"), r.message());
    }

    @Test
    void researchCompletesAndUnlocksProduction() {
        assertTrue(found(ALICE, "Roma", 0, 0).ok());
        Civilization rome = game.civOfMember(ALICE).orElseThrow();
        long roma = rome.capitalCityId;
        assertFalse(Simulation.apply(game, rules, new QueueProduction(ALICE, roma, "civcore:granary")).ok(), "granary needs pottery");
        assertFalse(Simulation.apply(game, rules, new SetResearch(ALICE, "civcore:pottery")).ok(), "pottery needs agriculture");
        assertTrue(Simulation.apply(game, rules, new SetResearch(ALICE, "civcore:agriculture")).ok());
        int turns = 0;
        while (!rome.knownTechs.contains("civcore:agriculture") && turns++ < 100) Simulation.strategicStep(game, rules);
        assertTrue(rome.knownTechs.contains("civcore:agriculture"), "agriculture should finish");
        assertTrue(turns < 15, "agriculture took " + turns + " turns");
        assertTrue(Simulation.apply(game, rules, new SetResearch(ALICE, "civcore:pottery")).ok());
        while (!rome.knownTechs.contains("civcore:pottery") && turns++ < 200) Simulation.strategicStep(game, rules);
        assertTrue(Simulation.apply(game, rules, new QueueProduction(ALICE, roma, "civcore:granary")).ok());
        while (!game.cities.get(roma).buildings.contains("civcore:granary") && turns++ < 300) Simulation.strategicStep(game, rules);
        assertTrue(game.cities.get(roma).buildings.contains("civcore:granary"));
        assertTrue(game.cities.get(roma).population > 1, "city should have grown");
    }

    @Test
    void onlyOwnersCanQueueInTheirCities() {
        assertTrue(found(ALICE, "Roma", 0, 0).ok());
        long roma = game.civOfMember(ALICE).orElseThrow().capitalCityId;
        assertFalse(Simulation.apply(game, rules, new QueueProduction(BOB, roma, "civcore:warriors")).ok());
        assertTrue(Simulation.apply(game, rules, new QueueProduction(ALICE, roma, "civcore:warriors")).ok());
    }

    @Test
    void saveRoundTripIsLossless() {
        assertTrue(found(ALICE, "Roma", 0, 0).ok());
        assertTrue(found(BOB, "Qart", 64 * 8, 64 * 8).ok());
        Simulation.apply(game, rules, new SetResearch(ALICE, "civcore:agriculture"));
        for (int i = 0; i < 7; i++) Simulation.strategicStep(game, rules);
        String json = GameStateCodec.toJson(game);
        GameState back = GameStateCodec.fromJson(json);
        assertEquals(json, GameStateCodec.toJson(back));
        assertEquals(game.turn, back.turn);
        assertEquals(2, back.civs.size());
        assertEquals(game.topology(), back.topology());
    }

    @Test
    void deterministicGivenSameCommands() {
        GameState a = GameState.newGame(GameSettings.centred("torus", 2048, 7L));
        GameState b = GameState.newGame(GameSettings.centred("torus", 2048, 7L));
        for (GameState g : new GameState[]{a, b}) {
            Simulation.apply(g, rules, new FoundCity(ALICE, "Rome", "Roma", 0, 64, 0));
            Simulation.apply(g, rules, new SetResearch(ALICE, "civcore:agriculture"));
            for (int i = 0; i < 50; i++) Simulation.strategicStep(g, rules);
        }
        assertEquals(GameStateCodec.toJson(a), GameStateCodec.toJson(b));
    }
}
