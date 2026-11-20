package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/** Fair, coalescing work queue for after-decoration repairs on the server thread. */
final class DeferredRepairs {
    enum Result { COMPLETE, RETRY, DROP }

    private static final int MAX_CHECKS_PER_TICK = 16;
    private static final long ADMISSION_BUDGET_NANOS = 2_000_000L;
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final LongSupplier clock;

    DeferredRepairs() {
        this(System::nanoTime);
    }

    DeferredRepairs(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized void add(long key) {
        if (queued.add(key)) {
            queue.add(key);
        }
    }

    synchronized void remove(long key) {
        queued.remove(key);
    }

    synchronized boolean contains(long key) {
        return queued.contains(key);
    }

    synchronized int size() {
        return queued.size();
    }

    synchronized void clear() {
        queued.clear();
        queue.clear();
    }

    /** Admit at most one full owner repair. A running repair is not preempted. */
    void tick(LongFunction<Result> repair) {
        int available;
        synchronized (this) {
            available = Math.min(MAX_CHECKS_PER_TICK, queue.size());
        }
        long start = clock.getAsLong();
        for (int checked = 0; checked < available; checked++) {
            if (checked > 0 && clock.getAsLong() - start >= ADMISSION_BUDGET_NANOS) {
                break;
            }
            Long key;
            synchronized (this) {
                key = queue.poll();
                if (key == null) {
                    break;
                }
                if (!queued.remove(key)) {
                    continue;
                }
            }
            // Never hold the producer lock while reading or repairing terrain.
            Result result = repair.apply(key);
            if (result == Result.RETRY) {
                add(key);
            } else if (result == Result.COMPLETE) {
                break;
            }
        }
    }
}
