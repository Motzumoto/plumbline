package plumbline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What has gone wrong so far, kept around for {@code /plumbline report}. Bounded so a
 * badly broken world can't leak memory.
 */
public final class Observations {

    private Observations() {
    }

    private static final int MAX_ENTRIES = 128;

    /** Oversized collision regions caught by the guard, keyed by their extents. */
    private static final Map<String, AtomicLong> GUARD_REGIONS =
        Collections.synchronizedMap(new LinkedHashMap<>());

    /** Sub-levels the healer found with impossible bounds. */
    private static final Map<String, Finding> HEALER_FINDINGS =
        Collections.synchronizedMap(new LinkedHashMap<>());

    private static final AtomicLong GUARD_TOTAL = new AtomicLong();

    public static final class Finding {
        public final String subLevelId;
        public final String dimension;
        public final String before;
        public final String reason;
        public volatile String after = "(not yet repaired)";
        public volatile boolean repaired = false;
        public volatile long seen = 1L;

        Finding(String subLevelId, String dimension, String before, String reason) {
            this.subLevelId = subLevelId;
            this.dimension = dimension;
            this.before = before;
            this.reason = reason;
        }
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

    public static Finding recordFinding(String id, String dim, String before, String reason) {
        synchronized (HEALER_FINDINGS) {
            Finding existing = HEALER_FINDINGS.get(id);
            if (existing != null) {
                existing.seen++;
                return existing;
            }
            Finding f = new Finding(id, dim, before, reason);
            if (HEALER_FINDINGS.size() < MAX_ENTRIES) {
                HEALER_FINDINGS.put(id, f);
            }
            return f;
        }
    }

    public static long guardTotal() {
        return GUARD_TOTAL.get();
    }

    public static Map<String, AtomicLong> guardRegions() {
        synchronized (GUARD_REGIONS) {
            return new LinkedHashMap<>(GUARD_REGIONS);
        }
    }

    public static Map<String, Finding> findings() {
        synchronized (HEALER_FINDINGS) {
            return new LinkedHashMap<>(HEALER_FINDINGS);
        }
    }

    public static boolean isEmpty() {
        return GUARD_TOTAL.get() == 0L && HEALER_FINDINGS.isEmpty();
    }
}
