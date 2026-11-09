package dev.civcore.sim.rules;

import dev.civcore.sim.model.City;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.TerrainType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Tile yields and which tiles a city works (Civ-style: centre tile plus one tile per population). */
public final class TileYields {
    /** Tiles within this Chebyshev radius of the centre can be worked. */
    public static final int WORK_RADIUS = 2;

    public record Yield(int food, int production, int gold) {
        public static final Yield ZERO = new Yield(0, 0, 0);

        public Yield plus(Yield o) {
            return new Yield(food + o.food, production + o.production, gold + o.gold);
        }
    }

    private TileYields() {
    }

    /** Yields of one tile. Unanalysed tiles count as plains so a game works before analysis finishes. */
    public static Yield of(GameState s, TilePos p) {
        TerrainType t = s.map.terrain(p);
        if (t == null) t = TerrainType.PLAINS;
        int food = t.food, prod = t.production, gold = t.gold + (s.map.river(p) ? 1 : 0);
        dev.civcore.sim.worldplan.ResourceType r = s.map.resource(p);
        if (r != null) {
            food += r.food;
            prod += r.production;
            gold += r.gold;
        }
        return new Yield(food, prod, gold);
    }

    /** City centre always yields at least 2 food and 1 production (Civ convention). */
    public static Yield centre(GameState s, City c) {
        Yield y = of(s, c.tile());
        return new Yield(Math.max(2, y.food()), Math.max(1, y.production()), y.gold());
    }

    /** The tiles a city works besides its centre: the best {@code population} owned tiles in range. */
    public static List<TilePos> workedTiles(GameState s, City c) {
        List<TilePos> candidates = new ArrayList<>();
        for (TilePos p : s.topology().within(c.tile(), WORK_RADIUS)) {
            if (p.equals(c.tile()) || s.map.owner(p) != c.civId) continue;
            TerrainType t = s.map.terrain(p);
            if (t != null && !t.passable) continue;
            candidates.add(p);
        }
        // Food first (growth), then production, then gold; ties broken by position for determinism.
        candidates.sort(Comparator.<TilePos>comparingInt(p -> {
            Yield y = of(s, p);
            return -(y.food() * 4 + y.production() * 3 + y.gold() * 2);
        }).thenComparingInt(TilePos::z).thenComparingInt(TilePos::x));
        return candidates.subList(0, Math.min(c.population, candidates.size()));
    }

    /** Total tile yield of a city (centre + worked tiles), before buildings. */
    public static Yield city(GameState s, City c) {
        Yield total = centre(s, c);
        for (TilePos p : workedTiles(s, c)) total = total.plus(of(s, p));
        return total;
    }
}
