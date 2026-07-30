package plumbline;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Periodically validates every sub-level's bounding box and repairs impossible ones.
 * <p>
 * The primary test is an invariant rather than a heuristic: blocks cannot exist outside
 * the world's build limits, so a bounding box reaching well past them is wrong no matter
 * how large the build is. Repair asks Sable to recompute the bounds
 * ({@code forceUpdateGlobalBounds}), which is idempotent and harmless on a healthy
 * sub-level -- so a false positive costs nothing but a log line.
 * <p>
 * Nothing is ever deleted. If a sub-level cannot be repaired, Plumbline records it and
 * tells an operator; it does not act further.
 */
public final class BoundsHealer {

    private long lastRunTick = 0L;
    private long lastNotifyMs = 0L;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!PlumblineRuntime.enabled || !PlumblineRuntime.healerEnabled) {
            return;
        }
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        long interval = Math.max(1L, PlumblineRuntime.healerIntervalSeconds * 20L);
        if (tick - lastRunTick < interval) {
            return;
        }
        lastRunTick = tick;

        try {
            for (ServerLevel level : server.getAllLevels()) {
                sweep(server, level);
            }
        } catch (Throwable t) {
            // never let a diagnostic take down the server tick
            Plumbline.LOG.error("[Plumbline] healer pass failed", t);
        }
    }

    private void sweep(MinecraftServer server, ServerLevel level) {
        ServerSubLevelContainer container;
        try {
            container = SubLevelContainer.getContainer(level);
        } catch (Throwable t) {
            return;
        }
        if (container == null) {
            return;
        }

        List<ServerSubLevel> all = container.getAllSubLevels();
        if (all == null || all.isEmpty()) {
            return;
        }

        int slack = PlumblineRuntime.worldHeightSlack;
        double floor = level.getMinBuildHeight() - slack;
        double ceiling = level.getMaxBuildHeight() + slack;
        String dim = level.dimension().location().toString();

        for (ServerSubLevel sub : all) {
            if (sub == null || sub.isRemoved()) {
                continue;
            }
            BoundingBox3dc bb;
            try {
                bb = sub.boundingBox();
            } catch (Throwable t) {
                continue;
            }
            if (bb == null) {
                continue;
            }

            String reason = diagnose(bb, floor, ceiling);
            if (reason == null) {
                continue;
            }

            String id = String.valueOf(sub.getUniqueId());
            String before = describe(bb);
            Observations.Finding finding = Observations.recordFinding(id, dim, before, reason);

            // Repair: ask Sable to recompute. Harmless on a healthy sub-level.
            try {
                sub.forceUpdateGlobalBounds();
                sub.updateBoundingBox();
            } catch (Throwable t) {
                Plumbline.LOG.warn("[Plumbline] repair threw for sub-level {}: {}", id, t.toString());
            }

            BoundingBox3dc after;
            try {
                after = sub.boundingBox();
            } catch (Throwable t) {
                continue;
            }
            if (after == null) {
                continue;
            }

            finding.after = describe(after);
            boolean stillBad = diagnose(after, floor, ceiling) != null;
            finding.repaired = !stillBad;

            if (!stillBad) {
                Plumbline.LOG.info(
                    "[Plumbline] repaired sub-level {} in {} ({}) -- bounds {} -> {}",
                    id, dim, reason, before, finding.after);
            } else {
                Plumbline.LOG.warn(
                    "[Plumbline] sub-level {} in {} still has impossible bounds after repair ({}): {}"
                    + " -- run '/plumbline report' and file it upstream",
                    id, dim, reason, finding.after);
                notifyOps(server, id);
            }
        }
    }

    /** @return a human-readable reason, or null when the box is fine. */
    private static String diagnose(BoundingBox3dc bb, double floor, double ceiling) {
        if (!finite(bb)) {
            return "non-finite bounds";
        }
        if (bb.minY() < floor || bb.maxY() > ceiling) {
            return "bounds outside world height (blocks cannot exist there)";
        }
        if (PlumblineRuntime.volumeCheckEnabled) {
            double vx = Math.abs(bb.maxX() - bb.minX());
            double vy = Math.abs(bb.maxY() - bb.minY());
            double vz = Math.abs(bb.maxZ() - bb.minZ());
            double volume = vx * vy * vz;
            if (volume > PlumblineRuntime.volumeCheckMax) {
                return "volume " + (long) volume + " over configured maximum";
            }
        }
        return null;
    }

    private static boolean finite(BoundingBox3dc bb) {
        return Double.isFinite(bb.minX()) && Double.isFinite(bb.minY()) && Double.isFinite(bb.minZ())
            && Double.isFinite(bb.maxX()) && Double.isFinite(bb.maxY()) && Double.isFinite(bb.maxZ());
    }

    static String describe(BoundingBox3dc bb) {
        return String.format("[%.1f,%.1f,%.1f -> %.1f,%.1f,%.1f]",
            bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
    }

    private void notifyOps(MinecraftServer server, String id) {
        if (!PlumblineRuntime.notifyOps) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNotifyMs < 300_000L) {
            return;
        }
        lastNotifyMs = now;
        Component msg = Component.literal(
            "[Plumbline] A sub-level has impossible bounds and could not be repaired ("
            + id + "). Run /plumbline report for details. Nothing has been deleted.");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) {
                p.sendSystemMessage(msg);
            }
        }
    }
}
