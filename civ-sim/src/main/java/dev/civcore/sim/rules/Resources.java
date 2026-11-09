package dev.civcore.sim.rules;

import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.worldplan.ResourceType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Strategic resources and happiness (backlog F2.5, F2.9). A civ "has" a resource for every tile inside its borders
 * that holds it (strategic ones only once the revealing tech is known). Each unit that requires a strategic resource
 * uses one while it exists.
 */
public final class Resources {
    public static final int BASE_HAPPINESS = 6;
    public static final int HAPPINESS_PER_LUXURY = 4;
    public static final int UNHAPPINESS_PER_CITY = 2;
    /** Below this, production also suffers and no settlers can be trained. */
    public static final int VERY_UNHAPPY = -10;

    private Resources() {
    }

    public static boolean knows(Civilization c, ResourceType r) {
        return r.revealTech == null || c.knownTechs.contains(r.revealTech);
    }

    /** Resource tiles inside the civ's borders (strategic ones only if revealed). */
    public static Map<ResourceType, Integer> owned(GameState s, Civilization c) {
        Map<ResourceType, Integer> out = new EnumMap<>(ResourceType.class);
        if (s.map.resource == null) return out;
        for (int i = 0; i < s.map.resource.length; i++) {
            int r = s.map.resource[i];
            if (r < 0 || s.map.owner[i] != c.id) continue;
            ResourceType t = ResourceType.values()[r];
            if (knows(c, t)) out.merge(t, 1, Integer::sum);
        }
        return out;
    }

    /** Strategic resources used by the civ's existing units and by units already queued in its cities. */
    public static Map<ResourceType, Integer> committed(GameState s, Ruleset r, Civilization c) {
        Map<ResourceType, Integer> out = new EnumMap<>(ResourceType.class);
        for (Army a : s.armies.values()) {
            if (a.civId != c.id) continue;
            for (Army.Unit u : a.units) add(out, r, u.type);
        }
        for (City city : s.cities.values()) {
            if (city.civId != c.id) continue;
            for (String q : city.productionQueue) add(out, r, q);
        }
        return out;
    }

    private static void add(Map<ResourceType, Integer> out, Ruleset r, String unitId) {
        Ruleset.UnitDef d = r.units().get(unitId);
        if (d == null || d.requiresResource() == null) return;
        ResourceType t = ResourceType.byId(d.requiresResource());
        if (t != null) out.merge(t, 1, Integer::sum);
    }

    /** Free units of a strategic resource for new production. */
    public static int available(GameState s, Ruleset r, Civilization c, ResourceType t) {
        return owned(s, c).getOrDefault(t, 0) - committed(s, r, c).getOrDefault(t, 0);
    }

    public static int luxuries(GameState s, Civilization c) {
        int n = 0;
        for (var e : owned(s, c).entrySet()) if (e.getKey().kind == ResourceType.Kind.LUXURY && e.getValue() > 0) n++;
        return n;
    }

    /** National happiness: base + luxuries + buildings - cities - half the population. */
    public static int happiness(GameState s, Ruleset r, Civilization c) {
        int cities = 0, pop = 0, fromBuildings = 0;
        for (City city : s.cities.values()) {
            if (city.civId != c.id) continue;
            cities++;
            pop += city.population;
            for (String b : city.buildings) {
                Ruleset.BuildingDef d = r.buildings().get(b);
                if (d != null) fromBuildings += d.yields().getOrDefault("happiness", 0);
            }
        }
        return BASE_HAPPINESS + HAPPINESS_PER_LUXURY * luxuries(s, c) + fromBuildings - UNHAPPINESS_PER_CITY * cities - pop / 2;
    }

    /** Tile index helper for tests and views. */
    public static ResourceType at(GameState s, TilePos p) {
        return s.map.resource(p);
    }
}
