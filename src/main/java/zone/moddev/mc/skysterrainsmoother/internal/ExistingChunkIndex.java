package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Indexes saved Anvil entries without loading chunks, including entries not yet populated. */
public final class ExistingChunkIndex {
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final Map<Long, BitSet> regions = new HashMap<>();

    public ExistingChunkIndex(File worldDirectory) throws IOException {
        // Minecraft 26.1 moved the Overworld's region files under its dimension directory.
        // Keep older saves protected too, including any regions still in the old location.
        readRegions(worldDirectory.toPath().resolve("dimensions").resolve("minecraft")
                .resolve("overworld").resolve("region").toFile());
        readRegions(new File(worldDirectory, "region"));
    }

    private void readRegions(File folder) throws IOException {

        if (!folder.exists()) {
            return;
        }

        File[] files = folder.listFiles();

        if (files == null) {
            throw new IOException("Cannot index existing world region headers");
        }
        for (File file : files) {
            Matcher matcher = REGION.matcher(file.getName());

            if (!matcher.matches()) {
                continue;
            }
            // Forge may open a neighbouring region without ever saving a chunk
            // into it. That leaves an empty file, with no saved entries to index.
            if (file.length() == 0) {
                continue;
            }

            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));

            BitSet entries = new BitSet(1024);

            try (DataInputStream header = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                for (int slot = 0; slot < 1024; ++slot) {
                    if (header.readInt() != 0) {
                        entries.set(slot);
                    }
                }
            }

            regions.computeIfAbsent(key(regionX, regionZ), ignored -> new BitSet(1024)).or(entries);
        }
    }

    public boolean contains(int chunkX, int chunkZ) {
        BitSet region = regions.get(key(chunkX >> 5, chunkZ >> 5));

        return region != null && region.get((chunkX & 31) | ((chunkZ & 31) << 5));
    }

    public int size() {
        int count = 0;

        for (BitSet region : regions.values()) {
            count += region.cardinality();
        }

        return count;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
