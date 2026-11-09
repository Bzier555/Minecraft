package dev.civcore.sim.worldplan;

import java.util.Set;

/**
 * Map resources (docs/F-game-design.md §4, backlog F2.5). Bonus resources add yields; strategic resources are needed
 * by some units; luxuries make the civ happier. Strategic resources are revealed by a technology.
 */
public enum ResourceType {
    WHEAT(Kind.BONUS, 1, 0, 0, null, Set.of(TerrainType.PLAINS, TerrainType.GRASSLAND), 0.05),
    CATTLE(Kind.BONUS, 1, 0, 0, null, Set.of(TerrainType.GRASSLAND), 0.05),
    FISH(Kind.BONUS, 2, 0, 0, null, Set.of(TerrainType.COAST), 0.08),
    STONE(Kind.BONUS, 0, 1, 0, null, Set.of(TerrainType.PLAINS, TerrainType.DESERT, TerrainType.TUNDRA, TerrainType.HILLS), 0.04),
    IRON(Kind.STRATEGIC, 0, 1, 0, "civcore:bronze_working", Set.of(TerrainType.HILLS, TerrainType.TUNDRA, TerrainType.DESERT, TerrainType.PLAINS), 0.05),
    HORSES(Kind.STRATEGIC, 0, 1, 0, "civcore:animal_husbandry", Set.of(TerrainType.PLAINS, TerrainType.GRASSLAND, TerrainType.TUNDRA), 0.05),
    GEMS(Kind.LUXURY, 0, 0, 2, null, Set.of(TerrainType.HILLS, TerrainType.JUNGLE, TerrainType.DESERT), 0.025),
    FURS(Kind.LUXURY, 0, 0, 2, null, Set.of(TerrainType.FOREST, TerrainType.TUNDRA, TerrainType.SNOW), 0.025),
    WINE(Kind.LUXURY, 0, 0, 2, null, Set.of(TerrainType.PLAINS, TerrainType.GRASSLAND, TerrainType.HILLS), 0.02);

    public enum Kind { BONUS, STRATEGIC, LUXURY }

    public final Kind kind;
    public final int food, production, gold;
    /** Tech that reveals and enables it, or null (always known). */
    public final String revealTech;
    public final Set<TerrainType> terrains;
    /** Chance per eligible tile. */
    public final double density;

    ResourceType(Kind kind, int food, int production, int gold, String revealTech, Set<TerrainType> terrains, double density) {
        this.kind = kind;
        this.food = food;
        this.production = production;
        this.gold = gold;
        this.revealTech = revealTech;
        this.terrains = terrains;
        this.density = density;
    }

    /** Strategic ids as used in unit definitions ("civcore:iron"). */
    public String id() {
        return "civcore:" + name().toLowerCase();
    }

    public static ResourceType byId(String id) {
        for (ResourceType r : values()) if (r.id().equals(id)) return r;
        return null;
    }
}
