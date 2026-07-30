package plumbline;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PlumblineConfig {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
        .comment("Master switch. When false Plumbline does nothing at all.")
        .define("enabled", true);

    public static final ModConfigSpec.LongValue GUARD_MAX_VOLUME = B
        .comment(
            "Largest block region the collision guard will let Sable walk in one pass.",
            "SubLevelEntityCollision.collide() iterates BlockPos.betweenClosed(min,max) without",
            "checking its size, and a sub-level with bad bounds can push that into the tens of",
            "millions, which locks up the server thread. A real entity sweep is a few blocks.",
            "Default is 64 cubed.")
        .defineInRange("guardMaxVolume", 262_144L, 4_096L, Long.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue LOG_REGIONS = B
        .comment("Log each distinct oversized region the guard catches.",
                 "Worth leaving on, it is what /plumbline report has to work with.")
        .define("logRegions", true);

    public static final ModConfigSpec.BooleanValue HEALER_ENABLED = B
        .comment("Periodically validate sub-level bounding boxes and repair impossible ones.",
                 "Repair means asking Sable to recompute the bounds. Nothing is ever deleted.")
        .define("healer.enabled", true);

    public static final ModConfigSpec.IntValue HEALER_INTERVAL = B
        .comment("Seconds between healer passes.")
        .defineInRange("healer.intervalSeconds", 30, 5, 3600);

    public static final ModConfigSpec.IntValue WORLD_HEIGHT_SLACK = B
        .comment(
            "How far past the world build limits a bounding box may reach before it counts as",
            "impossible. Blocks cannot exist out there at all, so this is just slack for",
            "sub-levels that sit a bit above the ceiling. This check is about how the world",
            "works rather than a threshold picked out of the air, so it will not fire on a",
            "build that is merely large.")
        .defineInRange("healer.worldHeightSlack", 128, 0, 4096);

    public static final ModConfigSpec.BooleanValue VOLUME_CHECK = B
        .comment(
            "Second check: also flag bounds enclosing more than volumeCheckMax blocks.",
            "Off by default. Unlike the height check this one can fire on a genuinely enormous",
            "build. The repair does no harm either way, but the log lines would be misleading.")
        .define("healer.volumeCheckEnabled", false);

    public static final ModConfigSpec.LongValue VOLUME_CHECK_MAX = B
        .comment("Volume threshold for the secondary heuristic above.")
        .defineInRange("healer.volumeCheckMax", 64_000_000L, 1_000_000L, Long.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue NOTIFY_OPS = B
        .comment("Message online operators when a sub-level fails to repair.")
        .define("healer.notifyOps", true);

    public static final ModConfigSpec SPEC = B.build();

    private PlumblineConfig() {
    }

    /** Copy config values into {@link PlumblineRuntime}, which is what the hot paths read. */
    public static void sync() {
        try {
            PlumblineRuntime.enabled = ENABLED.get();
            PlumblineRuntime.guardMaxVolume = GUARD_MAX_VOLUME.get();
            PlumblineRuntime.logRegions = LOG_REGIONS.get();
            PlumblineRuntime.healerEnabled = HEALER_ENABLED.get();
            PlumblineRuntime.healerIntervalSeconds = HEALER_INTERVAL.get();
            PlumblineRuntime.worldHeightSlack = WORLD_HEIGHT_SLACK.get();
            PlumblineRuntime.volumeCheckEnabled = VOLUME_CHECK.get();
            PlumblineRuntime.volumeCheckMax = VOLUME_CHECK_MAX.get();
            PlumblineRuntime.notifyOps = NOTIFY_OPS.get();
        } catch (IllegalStateException ignored) {
            // config not loaded yet -- defaults in PlumblineRuntime stay in force
        }
    }
}
