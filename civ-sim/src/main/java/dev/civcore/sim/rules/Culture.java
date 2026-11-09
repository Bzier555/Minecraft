package dev.civcore.sim.rules;

import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Culture and border growth (docs/F-game-design.md §4): each city earns culture every turn; at a threshold its
 * borders take one more tile, the best unowned tile touching its territory within {@link #BORDER_RADIUS}.
 */
public final class Culture {
    public static final int BASE_CULTURE = 1;
    public static final int BORDER_RADIUS = 3;

    private Culture() {
    }

    /** Culture needed for a city's next border tile, given how many it already gained this way. */
    public static int threshold(int tilesGained) {
        return 15 + 10 * tilesGained;
    }

    public static int cityCulture(City c, Ruleset r) {
        int culture = BASE_CULTURE;
        for (String b : c.buildings) {
            var def = r.buildings().get(b);
            if (def != null) culture += def.yields().getOrDefault("culture", 0);
        }
        return culture;
    }

    /** Runs one turn of culture for every city. */
    public static void step(GameState s, Ruleset r, List<String> events) {
        for (City c : s.cities.values()) {
            int gained = cityCulture(c, r);
            c.cultureStored += gained;
            Civilization civ = s.civs.get(c.civId);
            if (civ != null) civ.culture += gained;
            if (c.cultureStored >= threshold(c.cultureTiles)) {
                Optional<TilePos> tile = nextBorderTile(s, c);
                if (tile.isPresent()) {
                    s.map.setOwner(tile.get(), c.civId);
                    c.cultureStored -= threshold(c.cultureTiles);
                    c.cultureTiles++;
                } else {
                    c.cultureStored = threshold(c.cultureTiles); // nothing to take: hold at the threshold
                }
            }
        }
    }

    /** Best unowned tile within reach that touches the city's civ territory; better yields first, then nearer. */
    public static Optional<TilePos> nextBorderTile(GameState s, City c) {
        Topology topo = s.topology();
        return topo.within(c.tile(), BORDER_RADIUS).stream()
            .filter(p -> s.map.owner(p) == StrategicMap.NO_OWNER)
            .filter(p -> topo.neighbors(p).stream().anyMatch(n -> s.map.owner(n) == c.civId))
            .max(Comparator.<TilePos>comparingInt(p -> {
                    TileYields.Yield y = TileYields.of(s, p);
                    return y.food() * 3 + y.production() * 2 + y.gold();
                })
                .thenComparingDouble(p -> -topo.distance(p, c.tile()))
                .thenComparingInt(p -> -p.z()).thenComparingInt(p -> -p.x()));
    }

    /**
     * On capture: the old owner's tiles within reach of the captured city that are closer to it than to any of the
     * old owner's remaining cities change hands.
     */
    public static void transferOnCapture(GameState s, City captured, int oldCivId) {
        Topology topo = s.topology();
        List<City> others = s.cities.values().stream().filter(x -> x.civId == oldCivId && x.id != captured.id).toList();
        for (TilePos p : topo.within(captured.tile(), BORDER_RADIUS)) {
            if (s.map.owner(p) != oldCivId) continue;
            double here = topo.distance(p, captured.tile());
            boolean closerToAnother = others.stream().anyMatch(o -> topo.distance(p, o.tile()) < here);
            if (!closerToAnother) s.map.setOwner(p, captured.civId);
        }
    }
}
