package plumbline;

/**
 * Plain static settings holder.
 * <p>
 * The collision guard runs inside a mixin on Sable's hot path, potentially before
 * NeoForge config loading has happened. Reading a {@code ModConfigSpec} value before
 * its config is loaded throws, so the guard never touches the config directly: it
 * reads these fields, which carry safe defaults and are refreshed by
 * {@link PlumblineConfig#sync()} once the config actually loads.
 */
public final class PlumblineRuntime {

    private PlumblineRuntime() {
    }

    /** Master switch. */
    public static volatile boolean enabled = true;

    /**
     * Largest block region the collision guard will let Sable walk. 64^3.
     * A real entity sweep is a handful of blocks, so this is enormously generous.
     */
    public static volatile long guardMaxVolume = 262_144L;

    /** Whether the healer validates and repairs sub-level bounds. */
    public static volatile boolean healerEnabled = true;

    /** Seconds between healer passes. */
    public static volatile int healerIntervalSeconds = 30;

    /**
     * How far outside the world's build limits a bounding box may reach before it is
     * considered impossible. Blocks cannot exist outside those limits, so any slack
     * here is pure tolerance for sub-levels that legitimately fly a little high.
     */
    public static volatile int worldHeightSlack = 128;

    /**
     * Secondary, heuristic check: flag bounds enclosing more than this many blocks.
     * Off by default -- unlike the height test this can false-positive on a genuinely
     * enormous build, and being wrong here is not free of confusion even though the
     * repair itself is harmless.
     */
    public static volatile boolean volumeCheckEnabled = false;

    public static volatile long volumeCheckMax = 64_000_000L;

    /** Log each distinct oversized region the guard catches. */
    public static volatile boolean logRegions = true;

    /** Tell online operators when a sub-level could not be repaired. */
    public static volatile boolean notifyOps = true;
}
