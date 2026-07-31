package plumbline.mixin;

import java.util.Collections;

import net.minecraft.core.BlockPos;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;

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

    /**
     * Drops the stale half of the sweep instead of losing the whole pass.
     * <p>
     * collide() unions the entity box as seen from the sub-level's previous pose with the
     * same box as seen from its current pose. Only the union is oversized. Either half on
     * its own is about the size of the entity.
     * <p>
     * When the union would blow the cap this returns the current-pose box alone. Collision
     * against where the sub-level actually is still happens, and the only thing given up is
     * the sweep back to where it used to be, which is what protects against tunnelling
     * through a fast rotation. Trading some tunnelling for a frozen tick is a good trade;
     * trading all collision for one, which is what the guard below does, is a worse one.
     * <p>
     * {@code require = 0} on purpose. If Sable restructures this, the mod should fall back
     * to the guard rather than refuse to load. {@code /plumbline report} prints how many
     * times this ran so a silent miss is still visible.
     */
    @Redirect(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3d;expandTo(Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;Ldev/ryanhcode/sable/companion/math/BoundingBox3d;)Ldev/ryanhcode/sable/companion/math/BoundingBox3d;"
        ),
        require = 0,
        remap = false
    )
    private static BoundingBox3d plumbline$narrowSweep(
            BoundingBox3d atLastPose, BoundingBox3dc atCurrentPose, BoundingBox3d dest) {

        Observations.recordNarrowSeen();

        if (!PlumblineRuntime.enabled || !PlumblineRuntime.narrowSweep) {
            return atLastPose.expandTo(atCurrentPose, dest);
        }

        double minX = Math.min(atLastPose.minX(), atCurrentPose.minX());
        double minY = Math.min(atLastPose.minY(), atCurrentPose.minY());
        double minZ = Math.min(atLastPose.minZ(), atCurrentPose.minZ());
        double maxX = Math.max(atLastPose.maxX(), atCurrentPose.maxX());
        double maxY = Math.max(atLastPose.maxY(), atCurrentPose.maxY());
        double maxZ = Math.max(atLastPose.maxZ(), atCurrentPose.maxZ());

        double volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ);

        if (!(volume > (double) PlumblineRuntime.guardMaxVolume)) {
            // NaN lands here too, and is left for the guard to deal with
            return atLastPose.expandTo(atCurrentPose, dest);
        }

        boolean isNew = Observations.recordNarrowed(
            String.format("%.0f,%.0f,%.0f -> %.0f,%.0f,%.0f", minX, minY, minZ, maxX, maxY, maxZ));
        if (isNew && PlumblineRuntime.logRegions) {
            Plumbline.LOG.warn(
                "[Plumbline] narrowed a collision sweep from {} positions to the sub-level's"
                + " current pose only. It moved a long way since its last pose. Collision still"
                + " applies; tunnelling protection across that movement does not.",
                (long) volume);
        }
        return dest.set(atCurrentPose);
    }

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
