package dev.civcore.sim.topology;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopologyTest {

    @Property
    void torusDistanceIsSymmetricAndBounded(@ForAll @IntRange(min = 1, max = 128) int w, @ForAll @IntRange(min = 1, max = 128) int h,
                                            @ForAll int ax, @ForAll int az, @ForAll int bx, @ForAll int bz) {
        Topology t = Topology.torus(w, h);
        TilePos a = t.normalizeOrNull(ax, az);
        TilePos b = t.normalizeOrNull(bx, bz);
        assertEquals(t.distance(a, b), t.distance(b, a), 1e-9);
        assertTrue(Math.abs(t.dx(a.x(), b.x())) <= w / 2);
        assertTrue(Math.abs(t.dz(a.z(), b.z())) <= h / 2);
        assertTrue(t.chebyshev(a, b) <= Math.max(w / 2, h / 2));
    }

    @Property
    void normalizeIsIdempotentAndWrapsByPeriod(@ForAll @IntRange(min = 1, max = 200) int w, @ForAll @IntRange(min = -10_000, max = 10_000) int x,
                                               @ForAll @IntRange(min = -10_000, max = 10_000) int z) {
        Topology t = Topology.torus(w, w);
        TilePos p = t.normalizeOrNull(x, z);
        assertEquals(p, t.normalizeOrNull(p.x(), p.z()));
        assertEquals(p, t.normalizeOrNull(x + w, z - w));
        assertEquals(0.0, t.distance(p, t.normalizeOrNull(x + 3 * w, z)), 1e-9);
    }

    @Property
    void nearestCopyIsWithinHalfAPeriodOfTheObserver(@ForAll @IntRange(min = -5000, max = 5000) int target,
                                                     @ForAll @IntRange(min = -5000, max = 5000) int observer,
                                                     @ForAll @IntRange(min = 1, max = 8192) int period) {
        long copy = Topology.nearestCopy(target, observer, period);
        assertEquals(0, Math.floorMod(copy - target, period), "copy must be target shifted by whole periods");
        assertTrue(Math.abs(copy - observer) <= period / 2 + 1, () -> "copy " + copy + " too far from " + observer);
    }

    @Test
    void seamNeighboursOnTorus() {
        Topology t = Topology.torus(32, 32);
        assertTrue(t.neighbors(new TilePos(31, 0)).contains(new TilePos(0, 31)), "corner wraps diagonally");
        assertEquals(8, t.neighbors(new TilePos(0, 0)).size());
        assertEquals(1, t.dx(31, 0), "east across the seam is one step");
        assertEquals(-1, t.dx(0, 31), "west across the seam is one step");
    }

    @Test
    void boundedEdgesDoNotWrap() {
        Topology t = Topology.bounded(10, 10);
        assertNull(t.normalizeOrNull(-1, 5));
        assertEquals(3, t.neighbors(new TilePos(0, 0)).size());
        assertEquals(9, t.dx(0, 9));
    }

    @Test
    void cylinderWrapsOnlyX() {
        Topology t = Topology.cylinder(10, 10);
        assertNotNull(t.normalizeOrNull(-1, 5));
        assertNull(t.normalizeOrNull(5, -1));
    }

    @Test
    void tinyWrappedMapsDoNotDuplicateNeighbours() {
        Topology t = Topology.torus(2, 2);
        assertEquals(3, t.neighbors(new TilePos(0, 0)).size());
        assertEquals(4, t.within(new TilePos(0, 0), 5).size());
    }

    @Test
    void distanceToSeam() {
        Topology t = Topology.torus(32, 32);
        assertEquals(0, t.distanceToSeamOrEdge(new TilePos(0, 16)));
        assertEquals(0, t.distanceToSeamOrEdge(new TilePos(31, 16)));
        assertEquals(15, t.distanceToSeamOrEdge(new TilePos(15, 16)));
        assertEquals(2, t.distanceToSeamOrEdge(new TilePos(16, 29)));
    }

    @Test
    void tileMappingMatchesExoticWorldsBounds() {
        // 2,048-block torus: blocks -1024..1023 → tiles 0..31 of 64 blocks
        TileMapping m = new TileMapping(-1024, -1024, 64);
        assertEquals(0, m.tileX(-1024));
        assertEquals(31, m.tileX(1023));
        assertEquals(15, m.tileX(-1));
        assertEquals(16, m.tileX(0));
        assertEquals(-1024 + 32, m.centerBlockX(new TilePos(0, 0)));
        // the spike's seam test: 1023 and -1024 are neighbours one tile apart
        Topology t = Topology.torus(32, 32);
        assertEquals(1, t.chebyshev(new TilePos(m.tileX(1023), 5), new TilePos(m.tileX(-1024), 5)));
    }
}
