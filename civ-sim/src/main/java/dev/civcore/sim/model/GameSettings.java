package dev.civcore.sim.model;

import dev.civcore.sim.topology.TileMapping;
import dev.civcore.sim.topology.Topology;

/**
 * Fixed per game; chosen at setup and never changed mid-game (tile size and topology shape the whole game,
 * docs/D-architecture.md §14).
 *
 * @param topology            "torus", "cylinder" or "bounded"
 * @param widthTiles          map width in tiles
 * @param heightTiles         map height in tiles
 * @param tileSize            blocks per tile edge (default 64 = 4x4 chunks)
 * @param minBlockX           world minimum X (tile 0 starts here)
 * @param minBlockZ           world minimum Z
 * @param seed                simulation RNG seed
 * @param strategicTickSeconds real seconds per strategic tick at normal speed
 * @param minCitySpacing      minimum Chebyshev distance in tiles between city centres
 * @param seamExclusionTiles  cities may not be founded closer than this to a seam or map edge
 */
public record GameSettings(String topology, int widthTiles, int heightTiles, int tileSize, int minBlockX, int minBlockZ,
                           long seed, int strategicTickSeconds, int minCitySpacing, int seamExclusionTiles) {

    public static final int DEFAULT_TILE_SIZE = 64;

    /** Settings for a square world of {@code sizeBlocks} centred on 0,0 (Exotic Worlds presets are centred). */
    public static GameSettings centred(String topology, int sizeBlocks, long seed) {
        int tiles = Math.max(1, sizeBlocks / DEFAULT_TILE_SIZE);
        int half = sizeBlocks / 2;
        return new GameSettings(topology, tiles, tiles, DEFAULT_TILE_SIZE, -half, -half, seed, 60, 3, 3);
    }

    public Topology topologyShape() {
        return switch (topology) {
            case "torus" -> Topology.torus(widthTiles, heightTiles);
            case "cylinder" -> Topology.cylinder(widthTiles, heightTiles);
            case "bounded" -> Topology.bounded(widthTiles, heightTiles);
            default -> throw new IllegalArgumentException("unknown topology '" + topology + "'");
        };
    }

    public TileMapping mapping() {
        return new TileMapping(minBlockX, minBlockZ, tileSize);
    }
}
