package zone.moddev.mc.skysterrainsmoother;

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex;
import static org.junit.jupiter.api.Assertions.*;

class ExistingChunkIndexTest {
    @TempDir Path directory;
    @Test void savedEntriesIncludingNegativeAndUnpopulatedAreExcludedWithoutDecodingChunks() throws Exception {
        Path regions=Files.createDirectories(directory.resolve("region"));
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(regions.resolve("r.-1.2.mca")))) {
            for(int i=0;i<1024;i++)out.writeInt(i==0||i==1023?0x201:0);
        }
        ExistingChunkIndex index=new ExistingChunkIndex(directory.toFile());
        assertEquals(2,index.size());assertTrue(index.contains(-32,64));assertTrue(index.contains(-1,95));
        assertFalse(index.contains(-31,64));assertFalse(index.contains(0,64));
    }
    @Test void damagedHeaderStopsSafely() throws Exception {
        Files.createDirectories(directory.resolve("region"));Files.write(directory.resolve("region/r.0.0.mca"),new byte[10]);
        assertThrows(IOException.class,()->new ExistingChunkIndex(directory.toFile()));
    }
    @Test void newWorldHasNoSavedChunks() throws Exception {assertEquals(0,new ExistingChunkIndex(directory.toFile()).size());}
}
