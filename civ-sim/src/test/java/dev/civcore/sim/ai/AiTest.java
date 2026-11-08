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
        long domination = results.stream().filter(r -> r.victoryType().equals("domination")).count();
        for (HeadlessGames.Result r : results) {
            assertTrue(r.winner() >= 0, "every game ends: " + r);
            assertTrue(r.techsPerCiv().values().stream().mapToInt(Integer::intValue).max().orElse(0) >= 4, "AIs research: " + r);
            assertTrue(r.citiesPerCiv().values().stream().mapToInt(Integer::intValue).sum() >= 3, "AIs expand: " + r);
            assertEquals(0, r.failedCommands(), "AI should only issue legal commands: " + r);
        }
        assertTrue(domination >= 16, "domination in " + domination + "/20 games: " + results);
    }

    @Test
    void fourAiGameRuns() {
        HeadlessGames.Result r = HeadlessGames.play(rules, 3, 4, 400);
        assertTrue(r.winner() >= 0, r.toString());
        assertEquals(4, r.citiesPerCiv().size());
    }
}
