package dev.civcore.mod.world;

import com.exoticworlds.api.v1.ExoticWorldsApi;
import com.exoticworlds.api.v1.ToroidalShape;
import com.exoticworlds.api.v1.gen.GenerationProbe;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Optional Exotic Worlds integration through its public API ({@code com.exoticworlds.api.v1}).
 * Only {@link #present()} may be called without the mod; every other method touches Exotic Worlds classes.
 */
public final class ExoticWorldsCompat {
    private ExoticWorldsCompat() {
    }

    public static boolean present() {
        return ModList.get().isLoaded("exotic_worlds");
    }

    /** World shape for Civ setup: topology name, X width, Z width, min X, min Z. */
    public record Shape(String topology, int widthX, int widthZ, int minX, int minZ) {
    }

    public static Optional<Shape> shape(ServerLevel level) {
        if (!present()) return Optional.empty();
        return Impl.shape(level);
    }

    /**
     * Runs world sampling with the level's fold bound on this thread. Without it, biome lookups outside the chunk map
     * see an unbounded world and disagree with the generated terrain (measured on the spike server; Exotic Worlds
     * docs/modding/generation.md). Each worker thread must wrap its own work.
     */
    public static void withFold(ServerLevel level, Runnable work) {
        if (present()) Impl.withFold(level, work);
        else work.run();
    }

    /** Holds the Exotic Worlds references so this class loads only when the mod is present. */
    private static final class Impl {
        static Optional<Shape> shape(ServerLevel level) {
            Optional<ToroidalShape> s = ExoticWorldsApi.shapeOf(level);
            if (s.isEmpty()) return Optional.empty();
            ToroidalShape t = s.get();
            boolean x = t.loops(Direction.Axis.X), z = t.loops(Direction.Axis.Z);
            if (!x) return Optional.empty(); // Civ maps wrap east-west first; a Z-only cylinder is not supported yet
            String topo = z ? "torus" : "cylinder";
            int wz = z ? t.widthBlocks(Direction.Axis.Z) : t.widthBlocks(Direction.Axis.X);
            int minZ = z ? t.minBlock(Direction.Axis.Z) : -wz / 2;
            return Optional.of(new Shape(topo, t.widthBlocks(Direction.Axis.X), wz, t.minBlock(Direction.Axis.X), minZ));
        }

        static void withFold(ServerLevel level, Runnable work) {
            if (ExoticWorldsApi.shapeOf(level).isPresent()) GenerationProbe.withFold(level, work);
            else work.run();
        }
    }
}
