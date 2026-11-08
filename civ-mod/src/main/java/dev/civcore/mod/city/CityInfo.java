package dev.civcore.mod.city;

import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.rules.TileYields;

/** Text description of a city, shared by the City Center block and /civ status. */
public final class CityInfo {
    private CityInfo() {
    }

    public static String describe(GameState s, City c) {
        Civilization civ = s.civs.get(c.civId);
        TileYields.Yield y = TileYields.city(s, c);
        return c.name + " (" + (civ == null ? "?" : civ.name) + (civ != null && civ.capitalCityId == c.id ? ", capital" : "") + ")"
            + "\n  population " + c.population + ", food " + c.foodStored + "/" + Simulation.growthThreshold(c.population)
            + " (tiles +" + y.food() + ", eats " + Simulation.FOOD_PER_CITIZEN * c.population + ")"
            + "\n  production " + c.productionStored + " (+" + y.production() + " from tiles), gold +" + y.gold()
            + "\n  queue " + c.productionQueue + "\n  built " + c.buildings;
    }
}
