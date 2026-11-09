package zone.moddev.mc.skysterrainsmoother;

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex;
import static org.junit.jupiter.api.Assertions.*;

class ExistingChunkIndexTest {
    @TempDir
    Path directory;

    @Test
    void savedEntriesIncludingNegativeAndUnpopulatedAreExcludedWithoutDecodingChunks() throws Exception {
        Path regions = Files.createDirectories(directory.resolve("region"));

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(regions.resolve("r.-1.2.mca")))) {
            for (int chunkEntryIndex = 0; chunkEntryIndex < 1024; chunkEntryIndex++) {
                out.writeInt(chunkEntryIndex == 0 || chunkEntryIndex == 1023 ? 0x201 : 0);
            }
        }

        ExistingChunkIndex index = new ExistingChunkIndex(directory.toFile());

        assertEquals(2, index.size());
        assertTrue(index.contains(-32, 64));
        assertTrue(index.contains(-1, 95));
        assertFalse(index.contains(-31, 64));
        assertFalse(index.contains(0, 64));
    }

    @Test
    void findsModernOverworldRegionsAndCountsCopiesOnlyOnce() throws Exception {
        Path modern = Files.createDirectories(directory.resolve("dimensions/minecraft/overworld/region"));
        Path previous = Files.createDirectories(directory.resolve("region"));
        Path nether = Files.createDirectories(directory.resolve("dimensions/minecraft/the_nether/region"));

        writeHeader(modern.resolve("r.0.-1.mca"), 0, 1023);
        writeHeader(previous.resolve("r.0.-1.mca"), 0, 1);
        writeHeader(nether.resolve("r.0.-1.mca"), 2);

        ExistingChunkIndex index = new ExistingChunkIndex(directory.toFile());
        assertEquals(3, index.size());
        assertTrue(index.contains(0, -32));
        assertTrue(index.contains(1, -32));
        assertTrue(index.contains(31, -1));
        assertFalse(index.contains(2, -32));
    }

    private static void writeHeader(Path file, int... savedSlots) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            for (int slot = 0; slot < 1024; slot++) {
                boolean saved = false;
                for (int savedSlot : savedSlots) {
                    saved |= slot == savedSlot;
                }
                out.writeInt(saved ? 0x201 : 0);
            }
        }
    }

    @Test
    void unusedNativeRegionFilesDoNotExcludeUnsavedChunks() throws Exception {
        Path regions = Files.createDirectories(directory.resolve("region"));
        Files.createFile(regions.resolve("r.-1.0.mca"));
        ExistingChunkIndex index = new ExistingChunkIndex(directory.toFile());
        assertEquals(0, index.size());
        assertFalse(index.contains(-1, 0));
    }

    @Test
    void damagedHeaderStopsSafely() throws Exception {
        Files.createDirectories(directory.resolve("region"));
        Files.write(directory.resolve("region/r.0.0.mca"), new byte[10]);
        assertThrows(IOException.class, () -> new ExistingChunkIndex(directory.toFile()));
    }

    @Test
    void newWorldHasNoSavedChunks() throws Exception {
        assertEquals(0, new ExistingChunkIndex(directory.toFile()).size());
    }
}
