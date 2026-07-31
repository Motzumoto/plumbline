package plumbline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the guard has seen, kept for {@code /plumbline report}. Bounded so a badly behaved
 * world cannot leak memory.
 */
public final class Observations {

    private Observations() {
    }

    private static final int MAX_ENTRIES = 128;

    /** Oversized collision regions the guard skipped, keyed by their extents. */
    private static final Map<String, AtomicLong> GUARD_REGIONS =
        Collections.synchronizedMap(new LinkedHashMap<>());

    private static final AtomicLong GUARD_TOTAL = new AtomicLong();

    /**
     * Every call the guard sees, skipped or not.
     * <p>
     * Without this a zero skip count means either "nothing was ever oversized" or "the
     * mixin never ran", and those need very different follow-up. A non-zero count here
     * with zero skips says the guard is live and the world is fine.
     */
    private static final AtomicLong GUARD_SEEN = new AtomicLong();

    /** Called on every guard invocation, before any size test. */
    public static void recordGuardSeen() {
        GUARD_SEEN.incrementAndGet();
    }

    /** @return true if this exact region is new (caller should log it once). */
    public static boolean recordGuard(String region) {
        GUARD_TOTAL.incrementAndGet();
        synchronized (GUARD_REGIONS) {
            AtomicLong n = GUARD_REGIONS.get(region);
            if (n != null) {
                n.incrementAndGet();
                return false;
            }
            if (GUARD_REGIONS.size() >= MAX_ENTRIES) {
                return false;
            }
            GUARD_REGIONS.put(region, new AtomicLong(1L));
            return true;
        }
    }

    public static long guardTotal() {
        return GUARD_TOTAL.get();
    }

    public static long guardSeen() {
        return GUARD_SEEN.get();
    }

    public static Map<String, AtomicLong> guardRegions() {
        synchronized (GUARD_REGIONS) {
            return new LinkedHashMap<>(GUARD_REGIONS);
        }
    }

    /**
     * Drops everything counted so far. Called when a server starts.
     * <p>
     * These are statics and the mod object outlives any single server, so without this a
     * singleplayer player who leaves one world and opens another carries the first world's
     * numbers into the second one's report.
     */
    public static void reset() {
        synchronized (GUARD_REGIONS) {
            GUARD_REGIONS.clear();
        }
        GUARD_TOTAL.set(0L);
        GUARD_SEEN.set(0L);
    }
}
