package dev.civcore.sim.topology;

/** A strategic tile coordinate. Always canonical (inside the map) when produced by a {@link Topology}. */
public record TilePos(int x, int z) {
    @Override
    public String toString() {
        return "(" + x + "," + z + ")";
    }
}
