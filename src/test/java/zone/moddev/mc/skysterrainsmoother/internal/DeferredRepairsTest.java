package zone.moddev.mc.skysterrainsmoother.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class DeferredRepairsTest {
    @Test
    void coalescesEventsAndOnlyAdmitsOneOwnerPerTick() {
        DeferredRepairs work = new DeferredRepairs(() -> 0);
        work.add(12);
        work.add(12);
        work.add(34);
        List<Long> repaired = new ArrayList<>();
        work.tick(
                key -> {
                    repaired.add(key);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertEquals(java.util.Arrays.asList(12L), repaired);
        assertFalse(work.contains(12));
        assertTrue(work.contains(34));
        work.tick(
                key -> {
                    repaired.add(key);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertEquals(java.util.Arrays.asList(12L, 34L), repaired);
        assertEquals(0, work.size());
    }

    @Test
    void missingNeighboursDoNotStarveReadyOwnersAndRemainQueued() {
        DeferredRepairs work = new DeferredRepairs(() -> 0);
        work.add(1);
        work.add(2);
        work.add(3);
        List<Long> checked = new ArrayList<>();
        work.tick(
                key -> {
                    checked.add(key);
                    return key == 2
                            ? DeferredRepairs.Result.COMPLETE
                            : DeferredRepairs.Result.RETRY;
                });
        assertEquals(java.util.Arrays.asList(1L, 2L), checked);
        assertTrue(work.contains(1));
        assertFalse(work.contains(2));
        assertTrue(work.contains(3));
        checked.clear();
        work.tick(
                key -> {
                    checked.add(key);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertEquals(java.util.Arrays.asList(3L), checked);
        work.tick(key -> DeferredRepairs.Result.COMPLETE);
        assertEquals(0, work.size());
    }

    @Test
    void retryChecksRespectTheTimeAndCountBudgetsWithoutDroppingWork() {
        AtomicLong now = new AtomicLong();
        DeferredRepairs work = new DeferredRepairs(now::get);
        for (long key = 0; key < 40; key++) work.add(key);
        List<Long> checked = new ArrayList<>();
        work.tick(
                key -> {
                    checked.add(key);
                    now.addAndGet(1_100_000);
                    return DeferredRepairs.Result.RETRY;
                });
        assertEquals(java.util.Arrays.asList(0L, 1L), checked);
        assertEquals(40, work.size());
        checked.clear();
        DeferredRepairs countBound = new DeferredRepairs(() -> 0);
        for (long key = 0; key < 40; key++) countBound.add(key);
        countBound.tick(
                key -> {
                    checked.add(key);
                    return DeferredRepairs.Result.RETRY;
                });
        assertEquals(16, checked.size());
        assertEquals(40, countBound.size());
    }

    @Test
    void aSlowRepairLeavesTheNextOwnerForAnotherTick() {
        AtomicLong now = new AtomicLong();
        DeferredRepairs work = new DeferredRepairs(now::get);
        work.add(1);
        work.add(2);
        work.tick(
                key -> {
                    now.addAndGet(30_000_000);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertTrue(work.contains(2));
        assertEquals(1, work.size());
    }

    @Test
    void unloadingAndLifecycleResetCancelStaleWork() {
        DeferredRepairs work = new DeferredRepairs(() -> 0);
        work.add(1);
        work.add(2);
        work.remove(1);
        List<Long> checked = new ArrayList<>();
        work.tick(
                key -> {
                    checked.add(key);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertEquals(java.util.Arrays.asList(2L), checked);
        work.add(3);
        work.clear();
        work.tick(
                key -> {
                    fail("cancelled work must not run");
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertEquals(0, work.size());
    }

    @Test
    void eventsRaisedDuringARepairAreNotLost() {
        DeferredRepairs work = new DeferredRepairs(() -> 0);
        work.add(1);
        work.tick(
                key -> {
                    work.add(key);
                    return DeferredRepairs.Result.COMPLETE;
                });
        assertTrue(work.contains(1));
        work.tick(key -> DeferredRepairs.Result.COMPLETE);
        assertEquals(0, work.size());
    }
}
