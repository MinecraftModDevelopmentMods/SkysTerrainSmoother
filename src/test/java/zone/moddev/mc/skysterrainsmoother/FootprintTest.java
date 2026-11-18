package zone.moddev.mc.skysterrainsmoother;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;
import static org.junit.jupiter.api.Assertions.*;

class FootprintTest {
    @Test
    void everyFootprintLevelAndRotation() {
        for (int level = 1; level <= 3; level++) {
            for (int cardinal = 0; cardinal < 16; cardinal++) {
                for (int diagonal = 0; diagonal < 16; diagonal++) {
                    int expected = 0;

                    for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                        if ((cardinal & (1 << directionIndex)) != 0) {
                            expected |= Footprint.EDGES[directionIndex];
                        }
                    }
                    if (level == 3) {
                        expected |= diagonal;
                    }

                    int mask = Footprint.mask(cardinal, diagonal, level);

                    assertEquals(expected, mask);

                    int shape = Footprint.shape(mask, level);

                    if (mask == 0) {
                        assertEquals(0, shape);
                    } else if (level == 3 && Integer.bitCount(mask) == 1) {
                        assertEquals(3, shape);
                    } else if (level >= 2 && (mask == 3 || mask == 6 || mask == 12 || mask == 9)) {
                        assertEquals(2, shape);
                    } else {
                        assertEquals(1, shape);
                    }

                    int rotation = ((mask << 1) & 15) | (mask >> 3);

                    assertEquals(shape, Footprint.shape(rotation, level));

                    if (shape > 1) {
                        assertEquals((Footprint.direction(mask, shape) + 1) & 3, Footprint.direction(rotation, shape));
                    }
                }
            }
        }
    }

    @Test
    void disconnectedAndThreeQuadrantFootprintsAreSlabs() {
        for (int mask : new int[] { 5, 10, 7, 11, 13, 14 }) {
            assertEquals(1, Footprint.shape(mask, 3));
        }
    }

    @Test
    void trimOnlyTheDownhillPartOfMixedTerraces() {
        assertEquals(3, Footprint.trimDownhillEdges(7, 4), "keep the north step above a south drop");
        assertEquals(6, Footprint.trimDownhillEdges(7, 8), "keep the east step above a west drop");
        assertEquals(2, Footprint.trimDownhillEdges(7, 12), "keep the northeast corner above both drops");
        assertEquals(6, Footprint.trimDownhillEdges(6, 8), "an uphill-facing step already fits");
        assertEquals(0, Footprint.trimDownhillEdges(15, 5), "opposite drops leave no safe piece");
        assertThrows(IllegalArgumentException.class, () -> Footprint.trimDownhillEdges(16, 0));
        assertThrows(IllegalArgumentException.class, () -> Footprint.trimDownhillEdges(0, 16));
    }

    @Test
    void everyTrimmedFootprintKeepsOnlyRequestedSafeQuadrants() {
        for (int requested = 0; requested < 16; requested++) {
            for (int lowerEdges = 0; lowerEdges < 16; lowerEdges++) {
                int trimmed = Footprint.trimDownhillEdges(requested, lowerEdges);
                assertEquals(0, trimmed & ~requested, "never add an unrequested quadrant");
                for (int direction = 0; direction < 4; direction++) {
                    if ((lowerEdges & 1 << direction) != 0) {
                        assertEquals(0, trimmed & Footprint.EDGES[direction], "never raise a downhill edge");
                    }
                }
            }
        }
    }

    @Test
    void borderPolicyNeverReadsTheUnownedNorthOrWestBorder() {
        for (int blockX = -2; blockX <= 18; blockX++) {
            for (int blockZ = -2; blockZ <= 18; blockZ++) {
                assertEquals(blockX >= 0 && blockX <= 16 && blockZ >= 0 && blockZ <= 16,
                        Footprint.allowedNeighbour(blockX, blockZ));
            }
        }

        assertThrows(IllegalArgumentException.class, () -> Footprint.mask(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Footprint.shape(16, 3));
        assertThrows(IllegalArgumentException.class, () -> Footprint.shape(0, 4));
    }
}
