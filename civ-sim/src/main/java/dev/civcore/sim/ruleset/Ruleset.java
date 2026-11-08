package dev.civcore.sim.ruleset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable static content for one game: techs, buildings, units (data-driven JSON, docs/D-architecture.md §3). */
public final class Ruleset {

    public record Unlocks(List<String> buildings, List<String> units, List<String> stages) {
        public Unlocks {
            buildings = buildings == null ? List.of() : List.copyOf(buildings);
            units = units == null ? List.of() : List.copyOf(units);
            stages = stages == null ? List.of() : List.copyOf(stages);
        }
    }

    public record TechDef(String id, String era, int cost, List<String> requires, Unlocks unlocks) {
        public TechDef {
            requires = requires == null ? List.of() : List.copyOf(requires);
            unlocks = unlocks == null ? new Unlocks(null, null, null) : unlocks;
        }
    }

    public record BuildingDef(String id, int cost, String requiresTech, Map<String, Integer> yields) {
        public BuildingDef {
            yields = yields == null ? Map.of() : Map.copyOf(yields);
        }
    }

    public record UnitDef(String id, int cost, int strength, int moves, String requiresTech) {
    }

    private final Map<String, TechDef> techs;
    private final Map<String, BuildingDef> buildings;
    private final Map<String, UnitDef> units;
    private final String hash;

    public Ruleset(Map<String, TechDef> techs, Map<String, BuildingDef> buildings, Map<String, UnitDef> units) {
        this.techs = Map.copyOf(new TreeMap<>(techs));
        this.buildings = Map.copyOf(new TreeMap<>(buildings));
        this.units = Map.copyOf(new TreeMap<>(units));
        this.hash = computeHash();
    }

    public static Ruleset empty() {
        return new Ruleset(Map.of(), Map.of(), Map.of());
    }

    public Map<String, TechDef> techs() {
        return techs;
    }

    public Map<String, BuildingDef> buildings() {
        return buildings;
    }

    public Map<String, UnitDef> units() {
        return units;
    }

    /** Stable content hash; stored in saves to detect ruleset changes between sessions. */
    public String hash() {
        return hash;
    }

    private String computeHash() {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(techs).values().forEach(t -> sb.append(t).append('\n'));
        new TreeMap<>(buildings).values().forEach(b -> sb.append(b.id()).append(b.cost()).append(b.requiresTech()).append(new TreeMap<>(b.yields())).append('\n'));
        new TreeMap<>(units).values().forEach(u -> sb.append(u).append('\n'));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
