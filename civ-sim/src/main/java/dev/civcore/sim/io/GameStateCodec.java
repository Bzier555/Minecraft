package dev.civcore.sim.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.civcore.sim.model.GameState;

/**
 * JSON form of {@link GameState}. Used for persistence (human-readable, diffable saves while the schema is young),
 * tests and the headless runner.
 */
public final class GameStateCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GameStateCodec() {
    }

    public static String toJson(GameState state) {
        return GSON.toJson(state);
    }

    public static GameState fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (version > GameState.SCHEMA_VERSION) {
            throw new IllegalStateException("save schema " + version + " is newer than this build (" + GameState.SCHEMA_VERSION + ")");
        }
        // Future: SchemaMigrations.migrate(root, version) before binding.
        GameState state = GSON.fromJson(root, GameState.class);
        if (state.settings == null || state.map == null) throw new IllegalStateException("save is missing settings or map");
        if (state.map.owner == null || state.map.owner.length != state.map.width * state.map.height) {
            throw new IllegalStateException("save map size does not match its owner array");
        }
        state.map.ensureTerrainArrays();
        markMissingOriginalCapitals(state);
        return state;
    }

    /**
     * Saves from before {@code City.originalCapital} existed: mark each civ's earliest-founded city as its original
     * capital, so domination victory still counts it. Harmless on newer saves (a civ that has one is skipped).
     */
    static void markMissingOriginalCapitals(GameState state) {
        java.util.Map<Integer, dev.civcore.sim.model.City> first = new java.util.HashMap<>();
        java.util.Set<Integer> hasOne = new java.util.HashSet<>();
        for (dev.civcore.sim.model.City c : state.cities.values()) {
            if (c.originalCapital) hasOne.add(c.originalCivId);
            first.merge(c.originalCivId, c, (x, y) -> x.id <= y.id ? x : y);
        }
        first.forEach((civ, city) -> {
            if (!hasOne.contains(civ)) city.originalCapital = true;
        });
    }
}
