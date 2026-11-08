package dev.civcore.spike.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads vanilla Path's private target field (the one Exotic Worlds shifts), not any subclass override. */
@Mixin(Path.class)
public interface PathAccessor {
    @Accessor("target")
    BlockPos civspike$getRawTarget();
}
