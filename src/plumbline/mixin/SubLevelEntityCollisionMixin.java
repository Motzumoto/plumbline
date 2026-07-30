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
 * The seatbelt.
 * <p>
 * {@code SubLevelEntityCollision.collide()} chooses the blocks to test with
 * <pre>BlockPos.betweenClosed(min, max)</pre>
 * and walks the result inside a four-pass loop, never checking how large the region is.
 * When a sub-level's bounds are wrong that region can span tens of millions of blocks,
 * so one entity move iterates effectively forever. Measured in the wild: a single region
 * of 155x985x379 (~57.9 million blocks) and server ticks wedged for 207 seconds, caused
 * by six entities.
 * <p>
 * Over the cap we hand back an empty iterable. The loop finds no blocks, no sub-level
 * collision is applied for that pass, and the entity moves normally. Nothing else in
 * {@code collide()} is touched.
 * <p>
 * This only prevents the freeze; {@code BoundsHealer} is what actually repairs the
 * bounds that caused it.
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
                "[Plumbline] skipped an oversized sub-level collision scan: {}x{}x{} = {} blocks, region [{}]."
                + " The sub-level's bounds are wrong; the healer will try to recompute them."
                + " Run '/plumbline report' to get a paste-ready bug report.",
                dx, dy, dz, volume, region);
        }
        return Collections.emptyList();
    }
}
