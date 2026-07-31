package plumbline;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PlumblineConfig {

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
        .comment("Master switch. When false Plumbline changes nothing about how Sable behaves.",
                 "It still counts how often the guard is consulted, so /plumbline status can",
                 "tell you the mixin applied.")
        .define("enabled", true);

    public static final ModConfigSpec.LongValue GUARD_MAX_VOLUME = B
        .comment(
            "Largest block region the guard will let Sable walk in one collision pass.",
            "",
            "Sable already caps this at 125,000,000 and skips the pass when it is exceeded.",
            "That ceiling is too high to help: a region of 57.9 million froze a server tick",
            "for 207 seconds without ever reaching it. This is the same check at a threshold",
            "that catches the problem.",
            "",
            "What is known about the default: across 35,502 observed collision passes with",
            "sub-levels at rest, none came close to 262,144. What is not known is how large a",
            "legitimate pass gets while a sub-level is rotating quickly, because no such",
            "sample has been collected. If entities pass through a fast-spinning sub-level,",
            "raise this and please open an issue with the numbers from /plumbline report.")
        .defineInRange("guardMaxVolume", 262_144L, 4_096L, Long.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue LOG_REGIONS = B
        .comment("Log each distinct oversized region the guard catches.",
                 "Worth leaving on, it is what /plumbline report has to work with.")
        .define("logRegions", true);

    public static final ModConfigSpec SPEC = B.build();

    private PlumblineConfig() {
    }

    /** Copy config values into {@link PlumblineRuntime}, which is what the hot path reads. */
    public static void sync() {
        try {
            PlumblineRuntime.enabled = ENABLED.get();
            PlumblineRuntime.guardMaxVolume = GUARD_MAX_VOLUME.get();
            PlumblineRuntime.logRegions = LOG_REGIONS.get();
        } catch (IllegalStateException ignored) {
            // config not loaded yet, defaults in PlumblineRuntime stay in force
        }
    }
}
