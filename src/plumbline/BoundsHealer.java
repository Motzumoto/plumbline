package plumbline;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Checks every sub-level's bounding box on a timer and repairs the impossible ones.
 * <p>
 * The test is world height. Blocks can't exist outside the build limits, so a box
 * reaching well past them is wrong regardless of how big the build is, and there's
 * nothing to tune. Repair is {@code forceUpdateGlobalBounds()}, which just makes Sable
 * work the bounds out again; running it on a healthy sub-level does nothing, so getting
 * it wrong costs a log line.
 * <p>
 * Nothing is deleted. If a sub-level won't repair it gets recorded and an operator is
 * told, and that's all.
 */
public final class BoundsHealer {

    private long lastRunTick = 0L;
    private long lastNotifyMs = 0L;

    /** Counts healer passes, so a failing sub-level can be scheduled a few passes out. */
    private long pass = 0L;

    /**
     * Clear per-server state when a server starts.
     * <p>
     * This object is created once in the mod constructor and lives as long as the JVM, but
     * {@code getTickCount()} belongs to the server and restarts at zero. In singleplayer,
     * exiting to the title screen and opening a world again builds a fresh server, so
     * without this reset {@code tick - lastRunTick} stays negative for as long as the
     * previous session ran and the healer quietly stops doing passes.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        pass = 0L;
        lastRunTick = 0L;
        lastNotifyMs = 0L;
        Observations.reset();
    }

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
        pass++;

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

            // Backing off after earlier failures. Skipping is silent by design: a sub-level
            // that cannot be fixed should not cost a log line and a full bounds recompute
            // every pass for the rest of the session.
            if (pass < finding.nextAttemptPass) {
                continue;
            }

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
                finding.failedAttempts = 0L;
                finding.nextAttemptPass = 0L;
                Plumbline.LOG.info(
                    "[Plumbline] repaired sub-level {} in {} ({}) -- bounds {} -> {}",
                    id, dim, reason, before, finding.after);
            } else {
                finding.failedAttempts++;
                int wait = backoffPasses(finding.failedAttempts);
                finding.nextAttemptPass = pass + wait;
                Plumbline.LOG.warn(
                    "[Plumbline] sub-level {} in {} still has impossible bounds after a repair"
                    + " attempt ({}): {}. Failure {}, retrying in {} pass(es)."
                    + " See /plumbline report.",
                    id, dim, reason, finding.after, finding.failedAttempts, wait);
                notifyOps(server, id);
            }
        }
    }

    /** Longest gap between retries, regardless of how the healer interval is configured. */
    private static final long MAX_BACKOFF_SECONDS = 1800L;

    /**
     * How long to wait before retrying a sub-level whose repair just failed.
     * <p>
     * Doubles each time and then levels off. A repair can fail for reasons that go away on
     * their own, an unloaded chunk being the obvious one, so this never stops retrying. It
     * just gets bored. By the fifth or sixth failure the retries are far enough apart that
     * a sub-level nobody can fix costs about one log line every half hour.
     * <p>
     * The ceiling is in seconds rather than passes because {@code healer.intervalSeconds}
     * ranges from 5 to 3600. A fixed pass count would mean half an hour at the default and
     * days at the top of that range.
     *
     * @param failures consecutive failed attempts, 1 on the first failure
     * @return healer passes to skip before trying again, at least 1
     */
    private static int backoffPasses(long failures) {
        long interval = Math.max(1L, PlumblineRuntime.healerIntervalSeconds);
        long ceiling = Math.max(1L, MAX_BACKOFF_SECONDS / interval);
        long doublings = Math.min(Math.max(0L, failures - 1L), 20L);   // 20 keeps the shift sane
        return (int) Math.min(1L << doublings, ceiling);
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
