package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Reads Anvil location headers, never chunks. Includes unpopulated saved entries. */
public final class ExistingChunkIndex {
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final Map<Long, BitSet> regions = new HashMap<>();
    public ExistingChunkIndex(File worldDirectory) throws IOException {
        File folder = new File(worldDirectory, "region");
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) throw new IOException("Cannot index existing world region headers");
        for (File file : files) {
            Matcher matcher = REGION.matcher(file.getName());
            if (!matcher.matches()) continue;
            int x = Integer.parseInt(matcher.group(1)), z = Integer.parseInt(matcher.group(2));
            BitSet entries = new BitSet(1024);
            try (DataInputStream header = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                for (int slot = 0; slot < 1024; ++slot) if (header.readInt() != 0) entries.set(slot);
            }
            regions.put(key(x,z), entries);
        }
    }
    public boolean contains(int x,int z) {
        BitSet region = regions.get(key(x>>5,z>>5));
        return region != null && region.get((x&31)|((z&31)<<5));
    }
    public int size() { int count=0; for(BitSet region:regions.values())count+=region.cardinality(); return count; }
    public static long key(int x,int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
}
