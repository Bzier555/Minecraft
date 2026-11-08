package dev.civcore.spike.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Workaround for a server crash: Exotic Worlds 1.3.3 shifts a mob's current Path by the world width when the mob
 * crosses the seam, and dereferences vanilla Path#target unconditionally. Paths whose vanilla target is null
 * (e.g. Villager Recruits' AsyncPath, which keeps its real target in its own field) crash the server with an NPE.
 *
 * Fix: right before the shift, drop such a path. Exotic Worlds already null-checks the navigation's path, and the
 * mob simply re-plans next tick in the new coordinate frame. Remove once fixed upstream.
 */
@Pseudo
@Mixin(targets = "com.exoticworlds.engine.seam.SeamSnap", remap = false)
public abstract class SeamSnapGuardMixin {
    private static final Logger CIVSPIKE_LOG = LoggerFactory.getLogger("civspike");

    @Inject(method = "shiftNavigation", at = @At("HEAD"), remap = false, require = 1)
    private static void civspike$dropUnshiftablePath(Mob mob, int dx, int dz, CallbackInfo ci) {
        Path path = mob.getNavigation().getPath();
        if (path != null && ((PathAccessor) path).civspike$getRawTarget() == null) {
            mob.getNavigation().stop();
            CIVSPIKE_LOG.info("SPIKE seam-guard dropped {} for {} at seam crossing", path.getClass().getSimpleName(), mob.getType().toShortString());
        }
    }
}
