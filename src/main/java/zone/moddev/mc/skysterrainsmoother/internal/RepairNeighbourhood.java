package zone.moddev.mc.skysterrainsmoother.internal;

/** Readiness only: the supplied lookup must never request a chunk. */
final class RepairNeighbourhood {
    @FunctionalInterface
    interface FullChunks {
        boolean present(int chunkX, int chunkZ);
    }
    private RepairNeighbourhood() {
    }

    static boolean ready(int ownerX, int ownerZ, FullChunks fullChunkPresent) {
        // Sable 2 updates six neighbours, whose voxel classification reads their
        // neighbours again. A full one-chunk halo covers this two-block footprint,
        // including negative coordinates, diagonals and every owner border.
        for (int z = ownerZ - 1; z <= ownerZ + 1; z++) {
            for (int x = ownerX - 1; x <= ownerX + 1; x++) {
                if (!fullChunkPresent.present(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
