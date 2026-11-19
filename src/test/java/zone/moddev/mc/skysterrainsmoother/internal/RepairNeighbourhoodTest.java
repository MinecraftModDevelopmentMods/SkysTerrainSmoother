package zone.moddev.mc.skysterrainsmoother.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class RepairNeighbourhoodTest {
    @Test
    void requiresEveryFullNeighbourBeforeAllowingPhysicsNotifications() {
        for (int ownerX : new int[] {-4, 0, 4})
            for (int ownerZ : new int[] {-4, 0, 4}) {
                Set<Long> full = new HashSet<>();
                for (int x = ownerX - 1; x <= ownerX + 1; x++)
                    for (int z = ownerZ - 1; z <= ownerZ + 1; z++) full.add(key(x, z));
                assertTrue(
                        RepairNeighbourhood.ready(
                                ownerX, ownerZ, (x, z) -> full.contains(key(x, z))));
                for (long absent : new HashSet<>(full)) {
                    full.remove(absent);
                    assertFalse(
                            RepairNeighbourhood.ready(
                                    ownerX, ownerZ, (x, z) -> full.contains(key(x, z))));
                    full.add(absent);
                }
            }
    }

    @Test
    void theGuardDoesNotInspectOutsideItsDeclaredHalo() {
        assertTrue(
                RepairNeighbourhood.ready(
                        -1,
                        2,
                        (x, z) -> {
                            assertTrue(x >= -2 && x <= 0 && z >= 1 && z <= 3);
                            return true;
                        }));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
