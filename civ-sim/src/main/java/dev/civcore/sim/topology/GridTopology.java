package dev.civcore.sim.topology;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Square-tile grid that may wrap on either axis. */
public record GridTopology(int width, int height, boolean wrapsX, boolean wrapsZ) implements Topology {

    public GridTopology {
        if (width < 1 || height < 1) throw new IllegalArgumentException("map must be at least 1x1, got " + width + "x" + height);
    }

    @Override
    public boolean contains(int x, int z) {
        return (wrapsX || (x >= 0 && x < width)) && (wrapsZ || (z >= 0 && z < height));
    }

    @Override
    public TilePos normalizeOrNull(int x, int z) {
        if (!contains(x, z)) return null;
        return new TilePos(wrapsX ? Math.floorMod(x, width) : x, wrapsZ ? Math.floorMod(z, height) : z);
    }

    @Override
    public int dx(int fromX, int toX) {
        return wrapsX ? wrappedDelta(fromX, toX, width) : toX - fromX;
    }

    @Override
    public int dz(int fromZ, int toZ) {
        return wrapsZ ? wrappedDelta(fromZ, toZ, height) : toZ - fromZ;
    }

    private static int wrappedDelta(int from, int to, int period) {
        int d = Math.floorMod(to - from, period);
        return d > period / 2 ? d - period : d;
    }

    @Override
    public double distance(TilePos a, TilePos b) {
        int ax = Math.abs(dx(a.x(), b.x()));
        int az = Math.abs(dz(a.z(), b.z()));
        return Math.max(ax, az) + 0.5 * Math.min(ax, az);
    }

    @Override
    public int chebyshev(TilePos a, TilePos b) {
        return Math.max(Math.abs(dx(a.x(), b.x())), Math.abs(dz(a.z(), b.z())));
    }

    @Override
    public List<TilePos> neighbors(TilePos p) {
        Set<TilePos> out = new LinkedHashSet<>();
        for (int ddx = -1; ddx <= 1; ddx++) {
            for (int ddz = -1; ddz <= 1; ddz++) {
                if (ddx == 0 && ddz == 0) continue;
                TilePos n = normalizeOrNull(p.x() + ddx, p.z() + ddz);
                if (n != null && !n.equals(p)) out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    public List<TilePos> within(TilePos center, int radius) {
        Set<TilePos> out = new LinkedHashSet<>();
        for (int ddx = -radius; ddx <= radius; ddx++) {
            for (int ddz = -radius; ddz <= radius; ddz++) {
                TilePos n = normalizeOrNull(center.x() + ddx, center.z() + ddz);
                if (n != null) out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    public int distanceToSeamOrEdge(TilePos p) {
        // Wrapped axis: the seam lies between tile (size-1) and tile 0. Bounded axis: the map edge.
        // Both are treated alike: tile 0 and tile size-1 are at distance 0.
        int toX = Math.min(p.x(), width - 1 - p.x());
        int toZ = Math.min(p.z(), height - 1 - p.z());
        return Math.min(toX, toZ);
    }
}
