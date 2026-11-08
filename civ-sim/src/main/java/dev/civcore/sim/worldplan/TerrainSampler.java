package dev.civcore.sim.worldplan;

/**
 * Reads the world at a block column without needing Minecraft types. The mod implements it with the chunk
 * generator's noise; tests and the headless runner use synthetic implementations.
 */
@FunctionalInterface
public interface TerrainSampler {

    enum BiomeClass { OCEAN, RIVER, BEACH, DESERT, BADLANDS, JUNGLE, FOREST, TAIGA, SNOWY, COLD, SAVANNA, PLAINS, SWAMP, MOUNTAIN, HILL, OTHER }

    /**
     * @param floorHeight   highest solid block (ocean floor under water)
     * @param surfaceHeight highest block including water; greater than floorHeight over water
     */
    record Sample(int floorHeight, int surfaceHeight, BiomeClass biome) {
        /** Water is decided from heights only: biome lookups can disagree with terrain (see probe results). */
        public boolean water() {
            return surfaceHeight > floorHeight + 1;
        }
    }

    Sample sample(int blockX, int blockZ);
}
