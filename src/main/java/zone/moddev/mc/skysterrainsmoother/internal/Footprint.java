package zone.moddev.mc.skysterrainsmoother.internal;

/** Four lower half-height cells: NW, NE, SE, SW. No arbitrary composites. */
public final class Footprint {
    public static final int[] EDGES = {3, 6, 12, 9};
    private Footprint() { }
    public static int mask(int cardinalHigher, int diagonalHigher, int level) {
        validateLevel(level);
        int mask = 0;
        for (int direction = 0; direction < 4; ++direction)
            if ((cardinalHigher & 1 << direction) != 0) mask |= EDGES[direction];
        if (level == 3) mask |= diagonalHigher & 15;
        return mask;
    }
    /** 0=no piece, 1=slab, 2=step, 3=corner. */
    public static int shape(int mask, int level) {
        validateLevel(level);
        if ((mask & ~15) != 0) throw new IllegalArgumentException("Invalid footprint");
        if (mask == 0) return 0;
        if (level == 3 && Integer.bitCount(mask) == 1) return 3;
        if (level >= 2) for (int edge : EDGES) if (mask == edge) return 2;
        return 1;
    }
    public static int direction(int mask, int shape) {
        if (shape == 3) return Integer.numberOfTrailingZeros(mask);
        if (shape == 2) for (int direction = 0; direction < 4; ++direction) if (EDGES[direction] == mask) return direction;
        return 0;
    }
    public static void validateLevel(int level) {
        if (level < 1 || level > 3) throw new IllegalArgumentException("Smoothing level must be 1, 2 or 3");
    }
    /** Cross-border reads retain the established east/south-only policy. */
    public static boolean allowedNeighbour(int localX, int localZ) {
        return localX >= 0 && localZ >= 0 && localX <= 16 && localZ <= 16;
    }
}
