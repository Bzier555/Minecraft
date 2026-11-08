package dev.civcore.sim.worldplan;

import dev.civcore.sim.worldplan.TerrainSampler.BiomeClass;
import dev.civcore.sim.worldplan.TerrainSampler.Sample;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns the samples of one tile into a strategic terrain type. Coast is decided later from neighbours. */
public final class TerrainClassifier {
    public static final int SEA_LEVEL = 63;
    /** Average land height above which a tile counts as mountains (impassable). */
    public static final int MOUNTAIN_HEIGHT = 130;
    /** Average land height or height spread above which a tile counts as hills. */
    public static final int HILL_HEIGHT = 95;
    public static final int HILL_SPREAD = 18;

    public record TileTerrain(TerrainType type, boolean river) {
    }

    private TerrainClassifier() {
    }

    public static TileTerrain classify(List<Sample> samples) {
        if (samples.isEmpty()) throw new IllegalArgumentException("no samples");
        int water = 0;
        boolean river = false;
        Map<BiomeClass, Integer> votes = new EnumMap<>(BiomeClass.class);
        int landCount = 0, sum = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (Sample s : samples) {
            if (s.biome() == BiomeClass.RIVER) river = true;
            if (s.water() && s.biome() != BiomeClass.RIVER) {
                water++;
                continue;
            }
            landCount++;
            sum += s.floorHeight();
            min = Math.min(min, s.floorHeight());
            max = Math.max(max, s.floorHeight());
            votes.merge(s.biome(), 1, Integer::sum);
        }
        if (water * 2 > samples.size() || landCount == 0) return new TileTerrain(TerrainType.OCEAN, false);

        int avg = sum / landCount;
        int mountainVotes = votes.getOrDefault(BiomeClass.MOUNTAIN, 0);
        if (avg >= MOUNTAIN_HEIGHT || mountainVotes * 2 > landCount) return new TileTerrain(TerrainType.MOUNTAIN, river);
        if (avg >= HILL_HEIGHT || max - min >= HILL_SPREAD || votes.getOrDefault(BiomeClass.HILL, 0) * 2 > landCount) {
            return new TileTerrain(TerrainType.HILLS, river);
        }
        BiomeClass dominant = votes.entrySet().stream()
            .filter(e -> e.getKey() != BiomeClass.RIVER && e.getKey() != BiomeClass.MOUNTAIN && e.getKey() != BiomeClass.HILL)
            .max(Map.Entry.<BiomeClass, Integer>comparingByValue().thenComparing(e -> -e.getKey().ordinal()))
            .map(Map.Entry::getKey).orElse(BiomeClass.PLAINS);
        TerrainType t = switch (dominant) {
            case DESERT, BADLANDS, BEACH -> TerrainType.DESERT;
            case JUNGLE -> TerrainType.JUNGLE;
            case FOREST, TAIGA -> TerrainType.FOREST;
            case SNOWY -> TerrainType.SNOW;
            case COLD -> TerrainType.TUNDRA;
            case SAVANNA, PLAINS -> TerrainType.PLAINS;
            case SWAMP -> TerrainType.MARSH;
            default -> TerrainType.GRASSLAND;
        };
        return new TileTerrain(t, river);
    }
}
