package dev.civcore.sim.ai;

import dev.civcore.sim.TestRulesets;
import dev.civcore.sim.ruleset.Ruleset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Headless AI-vs-AI games (backlog F1.12 acceptance, reduced to 20 seeds for CI speed). */
class AiTest {
    final Ruleset rules = TestRulesets.shipped();

    @Test
    void aiGamesAreDeterministic() {
        assertEquals(HeadlessGames.play(rules, 7, 2, 200), HeadlessGames.play(rules, 7, 2, 200));
    }

    @Test
    void aisExpandResearchAndUsuallyWinByConquest() {
        List<HeadlessGames.Result> results = new ArrayList<>();
        for (long seed = 1; seed <= 20; seed++) results.add(HeadlessGames.play(rules, seed, 2, 400));
        long decisive = results.stream().filter(r -> !r.victoryType().equals("score")).count();
        for (HeadlessGames.Result r : results) {
            assertTrue(r.winner() >= 0, "every game ends: " + r);
            assertTrue(r.techsPerCiv().values().stream().mapToInt(Integer::intValue).max().orElse(0) >= 4, "AIs research: " + r);
            assertTrue(r.citiesPerCiv().values().stream().mapToInt(Integer::intValue).sum() >= 3, "AIs expand: " + r);
            assertEquals(0, r.failedCommands(), "AI should only issue legal commands: " + r);
        }
        assertTrue(decisive >= 16, "decisive victories in " + decisive + "/20 games: " + results);
    }

    @Test
    void offlineGovernorRunsCitiesButNeverStartsWars() {
        var rules = TestRulesets.shipped();
        var g = dev.civcore.sim.model.GameState.newGame(dev.civcore.sim.model.GameSettings.centred("torus", 2048, 4L));
        dev.civcore.sim.worldplan.WorldAnalysis.runAll(g, HeadlessGames.pangaea(4L));
        var starts = dev.civcore.sim.worldplan.StartPlacer.place(g, 2);
        var m = g.settings.mapping();
        dev.civcore.sim.engine.Simulation.apply(g, rules, new dev.civcore.sim.command.Command.FoundCity("player-uuid", "Humans", "Home",
            m.centerBlockX(starts.get(0)), 64, m.centerBlockZ(starts.get(0))));
        dev.civcore.sim.engine.Simulation.apply(g, rules, new dev.civcore.sim.command.Command.FoundCity("ai:x", "Bots", "Bot City",
            m.centerBlockX(starts.get(1)), 64, m.centerBlockZ(starts.get(1))));
        var human = g.civOfMember("player-uuid").orElseThrow();
        for (int i = 0; i < 80; i++) {
            for (var c : AiController.governor(g, rules, human)) {
                boolean allowed = c instanceof dev.civcore.sim.command.Command.SetResearch
                    || c instanceof dev.civcore.sim.command.Command.QueueProduction
                    || c instanceof dev.civcore.sim.command.Command.SettleWithArmy
                    || (c instanceof dev.civcore.sim.command.Command.MoveArmy mv && g.armies.get(mv.armyId()).hasUnit("civcore:settlers"));
                assertTrue(allowed, "governor issued " + c);
            }
            assertTrue(AiTurn.run(g, rules, java.util.Set.of(human.id)).isEmpty());
            dev.civcore.sim.engine.Simulation.strategicStep(g, rules);
        }
        assertTrue(human.knownTechs.size() >= 3, "governor researches");
        assertTrue(g.cities.values().stream().filter(c -> c.civId == human.id).count() >= 2, "governor settles");
    }

    @Test
    void fourAiGameRuns() {
        HeadlessGames.Result r = HeadlessGames.play(rules, 3, 4, 400);
        assertTrue(r.winner() >= 0, r.toString());
        assertEquals(4, r.citiesPerCiv().size());
    }
}
