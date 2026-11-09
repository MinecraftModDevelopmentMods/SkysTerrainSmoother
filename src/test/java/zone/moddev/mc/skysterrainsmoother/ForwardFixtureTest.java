package zone.moddev.mc.skysterrainsmoother;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForwardFixtureTest {
    private static final Path FIXTURE = Paths.get("src/test/resources/fixtures/forward-1.12.2-family");
    private static final String ARCHIVE_SHA256 = "2661A0EA3C4FED54A69338BC03AF55059447E7E84047AE3CCA73943E938CA02C";

    @Test
    void nativeSourceFilesAreSealedAndStayInsideTheWorldDirectory() throws Exception {
        assertEquals(ARCHIVE_SHA256, digest(Files.readAllBytes(FIXTURE.resolve("world.zip"))));
        assertEquals(ARCHIVE_SHA256 + "  world.zip", text("SHA256SUMS").trim());
        JsonObject files = new JsonParser().parse(text("source-file-sha256.json")).getAsJsonObject();
        Set<String> seen = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(FIXTURE.resolve("world.zip")))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                String name = entry.getName();
                assertTrue(name.startsWith("world/") && !name.contains("..") && !entry.isDirectory());
                String relative = name.substring("world/".length());
                assertTrue(seen.add(relative));
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int size; (size = input.read(buffer)) != -1;) bytes.write(buffer, 0, size);
                assertEquals(files.get(relative).getAsString(), digest(bytes.toByteArray()), relative);
            }
        }
        assertEquals(files.size() - 1, seen.size());
        assertFalse(seen.contains("session.lock"));
        assertTrue(seen.contains("playerdata/592308ca-42eb-46f7-929d-d2c6d00a4a01.dat"));
        assertTrue(seen.contains("data/skysgrassslabs_world_state.dat"));
        assertTrue(seen.contains("data/skysbuildingpieces_world_state.dat"));
    }

    @Test
    void manifestIncludesBothSandColoursOrientationsAndEveryInventoryLocation() throws Exception {
        JsonObject source = new JsonParser().parse(text("source-family-fixture.json")).getAsJsonObject();
        assertEquals(3405, source.getAsJsonArray("blocks").size());
        assertEquals(3405, source.getAsJsonArray("stacks").size());
        assertEquals(5, source.getAsJsonArray("entities").size());
        assertEquals("592308ca-42eb-46f7-929d-d2c6d00a4a01", source.get("player").getAsString());
        Set<Integer> sand = new HashSet<>();
        int snowy = 0;
        for (JsonElement element : source.getAsJsonArray("blocks")) {
            JsonObject block = element.getAsJsonObject();
            if (block.get("id").getAsString().equals("skysterrainsmoother:sand_slab")) {
                sand.add(block.get("meta").getAsInt());
            }
            if (block.has("snowy") && block.get("snowy").getAsBoolean()) snowy++;
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList(0, 1, 2, 3)), sand);
        assertTrue(snowy > 0, "Native source snow presentation is represented");
        JsonArray artifacts = new JsonParser().parse(text("source-artifacts.json")).getAsJsonArray();
        assertEquals("6F056EA5E4ADCE363D835955967ECC4ECA93A153645A32140DE3FC7655529D9C",
                artifacts.get(0).getAsJsonObject().get("sha256").getAsString());
        assertEquals("4273DD260D2F259F4FCE56680DA79AA9B2FBF90FD3ABCC1B494E228965FE1316",
                artifacts.get(1).getAsJsonObject().get("sha256").getAsString());
        assertEquals("660F1ECEBA8F78F3132858416BB8C43E77BBF1C3C1D53F63D59E38AD5612F298",
                artifacts.get(2).getAsJsonObject().get("sha256").getAsString());
    }

    private static String text(String name) throws Exception {
        return new String(Files.readAllBytes(FIXTURE.resolve(name)), StandardCharsets.UTF_8);
    }

    private static String digest(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        }
        return result.toString();
    }
}
