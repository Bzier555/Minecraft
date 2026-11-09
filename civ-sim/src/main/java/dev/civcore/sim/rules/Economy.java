package dev.civcore.sim.rules;

import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;

import java.util.Comparator;
import java.util.List;

/** Upkeep and city connections (backlog F3.6, F2.7 without physical roads). */
public final class Economy {
    /** Free military units per city before upkeep applies. */
    public static final int FREE_UNITS_PER_CITY = 2;
    public static final int CONNECTION_GOLD = 2;
    /** A city within this many tiles of the capital, with a land route, is connected. */
    public static final int CONNECTION_RANGE = 12;

    private Economy() {
    }

    public static int militaryUnits(GameState s, Civilization c) {
        int n = 0;
        for (Army a : s.armies.values()) {
            if (a.civId != c.id) continue;
            for (Army.Unit u : a.units) if (!Military.SETTLERS.equals(u.type)) n++;
        }
        return n;
    }

    /** Gold per turn spent on the army. */
    public static int upkeep(GameState s, Civilization c, Civics.Modifiers mods) {
        long cities = s.cities.values().stream().filter(x -> x.civId == c.id).count();
        int paid = Math.max(0, militaryUnits(s, c) - (int) (FREE_UNITS_PER_CITY * cities));
        return paid * mods.upkeepPct() / 100;
    }

    /** A bankrupt civ loses its weakest unit each turn until it can pay. */
    public static void disbandIfBankrupt(GameState s, Ruleset r, Civilization c, List<String> events) {
        if (c.gold >= 0) return;
        c.gold = 0;
        Army worst = null;
        Army.Unit unit = null;
        for (Army a : s.armies.values()) {
            if (a.civId != c.id) continue;
            for (Army.Unit u : a.units) {
                if (Military.SETTLERS.equals(u.type)) continue;
                if (unit == null || u.hp < unit.hp) {
                    unit = u;
                    worst = a;
                }
            }
        }
        if (worst == null) return;
        worst.units.remove(unit);
        worst.revision++;
        if (worst.units.isEmpty()) s.armies.remove(worst.id);
        events.add(c.name + " cannot pay its army: a unit of " + unit.type.replace("civcore:", "") + " deserted.");
    }

    /** Gold from cities connected to the capital over land (or both having a Harbor). */
    public static int connectionGold(GameState s, Civilization c) {
        City capital = s.cities.get(c.capitalCityId);
        if (capital == null || capital.civId != c.id) return 0;
        boolean capitalHarbor = capital.buildings.contains("civcore:harbor");
        int gold = 0;
        for (City city : s.cities.values()) {
            if (city.civId != c.id || city.id == capital.id) continue;
            boolean connected = (capitalHarbor && city.buildings.contains("civcore:harbor"))
                || (s.topology().chebyshev(city.tile(), capital.tile()) <= CONNECTION_RANGE
                && !Military.findPath(s, c.id, city.tile(), capital.tile()).isEmpty());
            if (connected) gold += CONNECTION_GOLD;
        }
        return gold;
    }

    @SuppressWarnings("unused")
    private static Comparator<Army.Unit> byHp() {
        return Comparator.comparingInt(u -> u.hp);
    }
}
