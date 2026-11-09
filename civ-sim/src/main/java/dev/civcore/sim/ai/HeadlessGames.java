package dev.civcore.sim.ai;

import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.RulesetLoader;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.StartPlacer;
import dev.civcore.sim.worldplan.TerrainSampler;
import dev.civcore.sim.worldplan.TerrainSampler.BiomeClass;
import dev.civcore.sim.worldplan.TerrainSampler.Sample;
import dev.civcore.sim.worldplan.WorldAnalysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AI-vs-AI games without Minecraft (docs/I-testing.md §2 "AI decision-making"). Used by tests and as a CLI:
 * {@code gradlew :civ-sim:aiBatch -Pgames=100}.
 */
public final class HeadlessGames {

    public record Result(long seed, int winner, String victoryType, long turns, Map<Integer, Integer> citiesPerCiv,
                         Map<Integer, Integer> techsPerCiv, int failedCommands, int wars, int peaces, int captures, int firstWarTurn, String firstRejection) {
    }

    private HeadlessGames() {
    }

    /** One continent with hills, forests and a few mountains; seed-dependent shape. Seams are ocean. */
    public static TerrainSampler pangaea(long seed) {
        double ox = (seed % 97) * 13.7, oz = (seed % 89) * 17.3;
        return (x, z) -> {
            double r = Math.hypot(x * 1.15, z) / 820.0;
            double wobble = 0.12 * Math.sin((x + ox) / 150.0) * Math.cos((z - oz) / 170.0);
            double land = 1 - r + wobble;
            if (land <= 0.05) return new Sample(40, 63, BiomeClass.OCEAN);
            double n = Math.sin((x + ox) / 90.0) + Math.cos((z + oz) / 110.0);
            int h = 66 + (int) (land * 25 + n * 6);
            if (n > 1.85) h = 140; // scattered peaks
            BiomeClass b = n > 0.8 ? BiomeClass.FOREST : n < -1.2 ? BiomeClass.DESERT : BiomeClass.PLAINS;
            return new Sample(h, h, b);
        };
    }

    public static Result play(Ruleset rules, long seed, int civCount, int maxTurns) {
        GameState g = GameState.newGame(GameSettings.centred("torus", 2048, seed));
        WorldAnalysis.runAll(g, pangaea(seed));
        List<TilePos> starts = StartPlacer.place(g, civCount);
        var m = g.settings.mapping();
        for (int i = 0; i < starts.size(); i++) {
            TilePos t = starts.get(i);
            String issuer = AiController.ISSUER_PREFIX + (char) ('a' + i);
            CommandResult r = Simulation.apply(g, rules, new Command.FoundCity(issuer, "AI " + (char) ('A' + i), "Capital " + (char) ('A' + i),
                m.centerBlockX(t), 64, m.centerBlockZ(t)));
            if (!r.ok()) throw new IllegalStateException("start " + t + ": " + r.message());
        }
        g.turnLimit = maxTurns;
        int failed = 0, wars = 0, peaces = 0, captures = 0, firstWar = -1;
        int warsBefore = 0;
        String firstRejection = "";
        while (!g.finished()) {
            List<String> rejected = AiTurn.run(g, rules);
            if (!rejected.isEmpty() && firstRejection.isEmpty()) firstRejection = "turn " + g.turn + ": " + rejected.get(0);
            failed += rejected.size();
            if (g.wars.size() > warsBefore) {
                wars += g.wars.size() - warsBefore;
                if (firstWar < 0) firstWar = (int) g.turn;
            }
            if (g.wars.size() < warsBefore) peaces += warsBefore - g.wars.size();
            for (String ev : Simulation.strategicStep(g, rules)) if (ev.contains(" captured ")) captures++;
            warsBefore = g.wars.size();
        }
        Map<Integer, Integer> cities = new TreeMap<>(), techs = new TreeMap<>();
        for (Civilization c : g.civs.values()) {
            cities.put(c.id, (int) g.cities.values().stream().filter(x -> x.civId == c.id).count());
            techs.put(c.id, c.knownTechs.size());
        }
        return new Result(seed, g.winnerCivId, g.victoryType, g.turn, cities, techs, failed, wars, peaces, captures, firstWar, firstRejection);
    }

    /** CLI: games civs maxTurns rulesetDir */
    public static void main(String[] args) throws Exception {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int civs = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        int maxTurns = args.length > 2 ? Integer.parseInt(args[2]) : 400;
        Path dir = Path.of(args.length > 3 ? args[3] : "../civ-mod/src/main/resources/data/civcore/civ");
        Ruleset rules = RulesetLoader.fromDirectory(dir).build();
        int domination = 0;
        long turnsSum = 0;
        List<String> lines = new ArrayList<>();
        lines.add("seed,winner,victory,turns,cities,techs,failedCommands,wars,peaces,captures,firstWarTurn");
        for (int i = 1; i <= games; i++) {
            Result r = play(rules, i, civs, maxTurns);
            if (!r.victoryType().equals("score")) domination++;
            turnsSum += r.turns();
            lines.add(r.seed() + "," + r.winner() + "," + r.victoryType() + "," + r.turns() + "," + r.citiesPerCiv().values()
                + "," + r.techsPerCiv().values() + "," + r.failedCommands() + "," + r.wars() + "," + r.peaces() + "," + r.captures() + "," + r.firstWarTurn() + (r.firstRejection().isEmpty() ? "" : ",\"" + r.firstRejection() + "\""));
        }
        lines.forEach(System.out::println);
        java.util.Map<String, Long> byType = new java.util.TreeMap<>();
        for (String l : lines.subList(1, lines.size())) byType.merge(l.split(",")[2], 1L, Long::sum);
        System.out.println("BY TYPE " + byType);
        System.out.printf("SUMMARY games=%d civs=%d decisive=%d (%.0f%%) avgTurns=%.0f%n",
            games, civs, domination, 100.0 * domination / games, (double) turnsSum / games);
    }

    @SuppressWarnings("unused")
    private static int cityCount(GameState g, int civ) {
        int n = 0;
        for (City c : g.cities.values()) if (c.civId == civ) n++;
        return n;
    }
}
