package dev.civcore.sim.view;

import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.topology.TilePos;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-civ fog of war (docs/F-game-design.md §8). Tiles a civ has ever seen are "explored" and persist; tiles seen
 * right now are "visible" and computed on demand. Vision: cities 3 tiles, armies 2, members walking in the world 2.
 */
public final class Visibility {
    public static final int CITY_VISION = 3;
    public static final int ARMY_VISION = 2;
    public static final int PLAYER_VISION = 2;

    private Visibility() {
    }

    /** Tiles the civ sees right now. {@code playerTiles} are where its members stand (may be empty). */
    public static Set<TilePos> visibleNow(GameState s, int civId, List<TilePos> playerTiles) {
        Set<TilePos> out = new HashSet<>();
        for (City c : s.cities.values()) if (c.civId == civId) out.addAll(s.topology().within(c.tile(), CITY_VISION));
        for (Army a : s.armies.values()) if (a.civId == civId) out.addAll(s.topology().within(a.tile(), ARMY_VISION));
        for (TilePos p : playerTiles) out.addAll(s.topology().within(p, PLAYER_VISION));
        return out;
    }

    /** Adds everything visible now to each civ's explored map. Call every strategic tick and periodically. */
    public static void update(GameState s, Map<Integer, List<TilePos>> playerTilesByCiv) {
        for (var civ : s.civs.values()) {
            boolean[] explored = s.explored.computeIfAbsent(civ.id, k -> new boolean[s.map.width * s.map.height]);
            for (TilePos p : visibleNow(s, civ.id, playerTilesByCiv.getOrDefault(civ.id, List.of()))) {
                explored[p.z() * s.map.width + p.x()] = true;
            }
        }
    }

    public static boolean explored(GameState s, int civId, TilePos p) {
        boolean[] e = s.explored.get(civId);
        return e != null && e[p.z() * s.map.width + p.x()];
    }
}
