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
 * collide() builds its candidate set with {@code BlockPos.betweenClosed(min, max)} and
 * iterates it in a four pass loop without checking the size. If the sub-level's bounds
 * are wrong that region can cover tens of millions of positions, so one entity move
 * never finishes. Seen on a real world: 155x985x379, about 57.9 million blocks, ticks
 * stuck for 207 seconds, six entities involved.
 * <p>
 * Over the cap this returns an empty iterable. The loop finds nothing, no sub-level
 * collision is applied on that pass, and the entity moves normally. Nothing else in
 * collide() is touched.
 * <p>
 * Stops the freeze only. {@link plumbline.BoundsHealer} is what fixes the bounds.
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
                "[Plumbline] skipped an oversized collision scan: {}x{}x{} = {} blocks, region [{}]."
                + " The sub-level's bounds are wrong. The healer will try to recalculate them."
                + " /plumbline report has the details.",
                dx, dy, dz, volume, region);
        }
        return Collections.emptyList();
    }
}
