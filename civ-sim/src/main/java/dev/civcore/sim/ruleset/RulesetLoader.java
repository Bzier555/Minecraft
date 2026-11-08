package dev.civcore.sim.ruleset;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import dev.civcore.sim.ruleset.Ruleset.BuildingDef;
import dev.civcore.sim.ruleset.Ruleset.TechDef;
import dev.civcore.sim.ruleset.Ruleset.UnitDef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Builds a {@link Ruleset} from JSON documents. The mod feeds it datapack entries ({@code data/<ns>/civ/<kind>/*.json});
 * tests and the headless runner read the same files from disk.
 */
public final class RulesetLoader {
    private static final Gson GSON = new Gson();

    private final Map<String, TechDef> techs = new LinkedHashMap<>();
    private final Map<String, BuildingDef> buildings = new LinkedHashMap<>();
    private final Map<String, UnitDef> units = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    /** @param kind "techs", "buildings" or "units"; {@code source} is used in error messages */
    public RulesetLoader add(String kind, String source, JsonElement json) {
        try {
            switch (kind) {
                case "techs" -> put(techs, source, GSON.fromJson(json, TechDef.class), TechDef::id);
                case "buildings" -> put(buildings, source, GSON.fromJson(json, BuildingDef.class), BuildingDef::id);
                case "units" -> put(units, source, GSON.fromJson(json, UnitDef.class), UnitDef::id);
                default -> errors.add(source + ": unknown ruleset kind '" + kind + "'");
            }
        } catch (JsonParseException | IllegalArgumentException e) {
            errors.add(source + ": " + e.getMessage());
        }
        return this;
    }

    private <T> void put(Map<String, T> into, String source, T def, java.util.function.Function<T, String> id) {
        if (def == null || id.apply(def) == null || id.apply(def).isBlank()) {
            errors.add(source + ": missing \"id\"");
            return;
        }
        if (into.put(id.apply(def), def) != null) errors.add(source + ": duplicate id " + id.apply(def));
    }

    /** Errors from parsing (validation errors come from {@link RulesetValidator}). */
    public List<String> errors() {
        return List.copyOf(errors);
    }

    public Ruleset build() {
        return new Ruleset(techs, buildings, units);
    }

    /** Reads {@code root/techs/*.json}, {@code root/buildings/*.json}, {@code root/units/*.json}. */
    public static RulesetLoader fromDirectory(Path root) throws IOException {
        RulesetLoader loader = new RulesetLoader();
        for (String kind : List.of("techs", "buildings", "units")) {
            Path dir = root.resolve(kind);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    loader.add(kind, f.getFileName().toString(), com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)));
                }
            }
        }
        return loader;
    }
}
