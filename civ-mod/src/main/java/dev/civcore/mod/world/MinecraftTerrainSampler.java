package dev.civcore.mod.world;

import dev.civcore.sim.worldplan.TerrainSampler;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.Tags;

/**
 * Samples the overworld's chunk generator directly (no chunk loading). Under Exotic Worlds this is the wrapped
 * generator, so samples match the generated world; /civ admin probe compares them with real chunks.
 */
public final class MinecraftTerrainSampler implements TerrainSampler {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState random;

    public MinecraftTerrainSampler(ServerLevel level) {
        this.level = level;
        this.generator = level.getChunkSource().getGenerator();
        this.random = level.getChunkSource().randomState();
    }

    @Override
    public Sample sample(int x, int z) {
        int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
        int y = Math.max(floor, level.getSeaLevel());
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
        return new Sample(floor, surface, classify(biome));
    }

    public static BiomeClass classify(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN)) return BiomeClass.OCEAN;
        if (b.is(BiomeTags.IS_RIVER)) return BiomeClass.RIVER;
        if (b.is(BiomeTags.IS_BEACH)) return BiomeClass.BEACH;
        if (b.is(BiomeTags.IS_BADLANDS)) return BiomeClass.BADLANDS;
        if (b.is(Tags.Biomes.IS_DESERT)) return BiomeClass.DESERT;
        if (b.is(BiomeTags.IS_JUNGLE)) return BiomeClass.JUNGLE;
        if (b.is(Tags.Biomes.IS_SWAMP)) return BiomeClass.SWAMP;
        if (b.is(Tags.Biomes.IS_SNOWY)) return BiomeClass.SNOWY;
        if (b.is(BiomeTags.IS_TAIGA)) return BiomeClass.TAIGA;
        if (b.is(BiomeTags.IS_FOREST)) return BiomeClass.FOREST;
        if (b.is(BiomeTags.IS_SAVANNA)) return BiomeClass.SAVANNA;
        // Mountain biome tags include fairly low windswept/meadow biomes; let height decide true mountains.
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL)) return BiomeClass.HILL;
        if (b.is(Tags.Biomes.IS_COLD_OVERWORLD)) return BiomeClass.COLD;
        if (b.is(Tags.Biomes.IS_PLAINS)) return BiomeClass.PLAINS;
        return BiomeClass.OTHER;
    }
}
