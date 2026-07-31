package plumbline;

/**
 * Static settings the hot path reads.
 * <p>
 * The guard runs inside a mixin and can fire before NeoForge has loaded configs, and
 * reading a {@code ModConfigSpec} value that early throws. So it reads these instead.
 * They start at sane defaults and {@link PlumblineConfig#sync()} updates them once the
 * config is actually available.
 */
public final class PlumblineRuntime {

    private PlumblineRuntime() {
    }

    /** Master switch. */
    public static volatile boolean enabled = true;

    /**
     * Largest block region the guard will let Sable walk in one collision pass.
     * <p>
     * Sable caps the same quantity at 125,000,000, which is far too high to prevent the
     * freeze. See {@link PlumblineConfig#GUARD_MAX_VOLUME} for what is and is not known
     * about this number.
     */
    public static volatile long guardMaxVolume = 262_144L;

    /** Log each distinct oversized region the guard catches. */
    public static volatile boolean logRegions = true;
}
