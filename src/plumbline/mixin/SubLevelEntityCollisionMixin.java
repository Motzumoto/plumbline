package plumbline.mixin;

import java.util.Collections;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import plumbline.Observations;
import plumbline.Plumbline;
import plumbline.PlumblineRuntime;

/**
 * Caps the block region {@code SubLevelEntityCollision.collide()} is allowed to walk.
 * <p>
 * collide() takes the entity's swept bounding box, expands it slightly, and inverse
 * transforms it into the sub-level's local frame twice: once against
 * {@code LevelReusedVectors.lastPose} and once against {@code SubLevel.logicalPose()}.
 * It unions those two boxes and walks every block position inside with
 * {@code BlockPos.betweenClosed(min, max)}. When the sub-level moved or rotated a long
 * way between those two poses the union spans the gap, and one entity move has to walk
 * tens of millions of positions. Seen on a real world: 155x985x379, about 57.9 million
 * positions, a tick stuck for 207 seconds.
 * <p>
 * Sable already guards this. It compares the same union's volume against 125,000,000 and
 * logs "Enormous local sub-level collision bounds, quitting." That ceiling is high enough
 * that the 57.9 million case passed straight through it. This is the same test, lower.
 * <p>
 * Over the cap this returns an empty iterable. The loop finds nothing, no sub-level
 * collision is applied on that pass, and the entity moves normally. Nothing else in
 * collide() is touched.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision", remap = false)
public class SubLevelEntityCollisionMixin {

    @Redirect(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"
        ),
        remap = false
    )
    private static Iterable<BlockPos> plumbline$capCollisionVolume(BlockPos min, BlockPos max) {
        // Counted before the enabled check on purpose. A zero here is the one thing that
        // distinguishes "the mixin never applied" from "nothing was ever oversized", and
        // that answer matters most to somebody who thinks Plumbline is doing nothing.
        Observations.recordGuardSeen();

        if (!PlumblineRuntime.enabled) {
            return BlockPos.betweenClosed(min, max);
        }

        long dx = Math.abs((long) max.getX() - (long) min.getX()) + 1L;
        long dy = Math.abs((long) max.getY() - (long) min.getY()) + 1L;
        long dz = Math.abs((long) max.getZ() - (long) min.getZ()) + 1L;

        long cap = PlumblineRuntime.guardMaxVolume;

        // test each axis first so an absurd input cannot overflow the multiply
        long volume;
        if (dx > cap || dy > cap || dz > cap) {
            volume = Long.MAX_VALUE;
        } else {
            volume = dx * dy * dz;
        }

        if (volume <= cap) {
            return BlockPos.betweenClosed(min, max);
        }

        String region = min.getX() + "," + min.getY() + "," + min.getZ()
                      + " -> " + max.getX() + "," + max.getY() + "," + max.getZ();
        boolean isNew = Observations.recordGuard(region);
        if (isNew && PlumblineRuntime.logRegions) {
            Plumbline.LOG.warn(
                "[Plumbline] skipped an oversized collision pass: {}x{}x{} = {} positions,"
                + " region [{}] in sub-level local space. The sub-level moved a long way"
                + " between its last pose and its current one. /plumbline report has the details.",
                dx, dy, dz, volume, region);
        }
        return Collections.emptyList();
    }
}
