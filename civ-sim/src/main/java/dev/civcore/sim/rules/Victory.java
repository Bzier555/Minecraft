package dev.civcore.sim.rules;

import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;

import java.util.Comparator;
import java.util.List;

/** Domination and score victory, plus elimination (docs/F-game-design.md §9, MVP subset). */
public final class Victory {
    private Victory() {
    }

    public static int score(GameState s, Civilization c) {
        int pop = 0, cities = 0;
        for (City city : s.cities.values()) {
            if (city.civId == c.id) {
                pop += city.population;
                cities++;
            }
        }
        return pop * 3 + cities * 10 + c.knownTechs.size() * 5 + s.map.countOwnedBy(c.id);
    }

    /** How many of the three science projects (in order) the civ has completed. */
    public static int scienceProjectsBuilt(GameState s, dev.civcore.sim.ruleset.Ruleset r, Civilization c) {
        java.util.Set<Integer> built = new java.util.HashSet<>();
        for (City city : s.cities.values()) {
            if (city.civId != c.id) continue;
            for (String b : city.buildings) {
                var d = r.buildings().get(b);
                if (d != null && d.yields().getOrDefault("science_project", 0) > 0) built.add(d.yields().get("science_project"));
            }
        }
        int n = 0;
        while (built.contains(n + 1)) n++;
        return n;
    }

    public static final int SCIENCE_PROJECTS = 3;

    /** Culture needed for a culture victory (automatic: 8,000 per 1,024 tiles). */
    public static int cultureTarget(GameState s) {
        return s.cultureVictoryCulture > 0 ? s.cultureVictoryCulture : Math.max(4000, 8000 * s.map.width * s.map.height / 1024);
    }

    /** Ruleset-aware check (science victory needs building data). */
    public static void check(GameState s, dev.civcore.sim.ruleset.Ruleset r, List<String> events) {
        check(s, events);
        if (s.finished()) return;
        for (Civilization c : s.civs.values()) {
            if (c.eliminated) continue;
            if (s.victoryTypes.contains("science") && scienceProjectsBuilt(s, r, c) >= SCIENCE_PROJECTS) {
                declare(s, c.id, "science", events);
                return;
            }
            if (s.victoryTypes.contains("culture") && c.culture >= cultureTarget(s)) {
                declare(s, c.id, "culture", events);
                return;
            }
        }
    }

    /** Updates eliminations and sets the winner if the game is decided. */
    public static void check(GameState s, List<String> events) {
        if (s.finished()) return;
        for (Civilization c : s.civs.values()) {
            if (c.eliminated) continue;
            boolean hasCity = s.cities.values().stream().anyMatch(x -> x.civId == c.id);
            boolean hasArmy = s.armies.values().stream().anyMatch(a -> a.civId == c.id);
            if (!hasCity && !hasArmy && everFounded(s, c)) {
                c.eliminated = true;
                events.add(c.name + " has been eliminated.");
            }
        }
        // Domination: one civ holds every original capital (needs at least two civs to have founded one).
        List<City> capitals = s.cities.values().stream().filter(c -> c.originalCapital).toList();
        if (capitals.size() >= 2 && s.victoryTypes.contains("domination")) {
            int owner = capitals.get(0).civId;
            if (capitals.stream().allMatch(c -> c.civId == owner)) {
                declare(s, owner, "domination", events);
                return;
            }
        }
        if (s.turnLimit > 0 && s.turn >= s.turnLimit && s.victoryTypes.contains("score")) {
            s.civs.values().stream().filter(c -> !c.eliminated)
                .max(Comparator.<Civilization>comparingInt(c -> score(s, c)).thenComparingInt(c -> -c.id))
                .ifPresent(c -> declare(s, c.id, "score", events));
        }
    }

    private static boolean everFounded(GameState s, Civilization c) {
        return s.cities.values().stream().anyMatch(x -> x.originalCivId == c.id);
    }

    private static void declare(GameState s, int civId, String type, List<String> events) {
        s.winnerCivId = civId;
        s.victoryType = type;
        events.add(s.civs.get(civId).name + " has won a " + type + " victory!");
    }
}
