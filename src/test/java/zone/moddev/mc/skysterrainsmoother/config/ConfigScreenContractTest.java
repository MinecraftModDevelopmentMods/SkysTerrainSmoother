package zone.moddev.mc.skysterrainsmoother.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigScreenContractTest {
    @TempDir
    Path directory;
    private ForgeConfigSpec spec;

    @BeforeEach
    void loadDefaults() throws Exception {
        java.lang.reflect.Field field = TerrainSmootherConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        spec = (ForgeConfigSpec) field.get(null);
        field = TerrainSmootherConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, null);
        CommentedFileConfig config = CommentedFileConfig.builder(directory.resolve("settings.toml")).sync().build();
        config.load();
        spec.setConfig(config);
        field = TerrainSmootherConfig.class.getDeclaredField("loadedValues");
        field.setAccessible(true);
        field.set(null, config);
    }

    @Test
    void defaultsAndRestartOnlyChoicesAreRetained() {
        assertTrue(TerrainSmootherConfig.enabled());
        assertTrue(TerrainSmootherConfig.mineralogy());
        assertEquals(3, TerrainSmootherConfig.level());
        TerrainSmootherConfig.activateSettingsForThisRun();
        TerrainSmootherConfig.saveForNextStart(false, 1, false);
        assertTrue(TerrainSmootherConfig.enabled());
        assertTrue(TerrainSmootherConfig.mineralogy());
        assertEquals(3, TerrainSmootherConfig.level());
        assertFalse(TerrainSmootherConfig.configuredEnabled());
        assertFalse(TerrainSmootherConfig.configuredMineralogy());
        assertEquals(1, TerrainSmootherConfig.configuredLevel());
        assertThrows(IllegalArgumentException.class, () -> TerrainSmootherConfig.saveForNextStart(true, 0, true));
        assertThrows(IllegalArgumentException.class, () -> TerrainSmootherConfig.saveForNextStart(true, 4, true));
    }

    @Test
    void savingChoicesPreservesOtherSettingsAndComments() throws Exception {
        java.lang.reflect.Field field = TerrainSmootherConfig.class.getDeclaredField("loadedValues");
        field.setAccessible(true);
        com.electronwill.nightconfig.core.CommentedConfig values =
                (com.electronwill.nightconfig.core.CommentedConfig) field.get(null);
        values.set("worldgen.extra", "kept");
        values.setComment("worldgen.extra", "A pack setting");
        values.set("other.extra", 42);
        TerrainSmootherConfig.activateSettingsForThisRun();
        for (int level = 1; level <= 3; level++) {
            TerrainSmootherConfig.saveForNextStart(false, level, false);
            assertEquals(level, TerrainSmootherConfig.configuredLevel());
            assertFalse(TerrainSmootherConfig.configuredEnabled());
            assertFalse(TerrainSmootherConfig.configuredMineralogy());
            assertEquals("kept", values.get("worldgen.extra"));
            assertEquals("A pack setting", values.getComment("worldgen.extra"));
            assertEquals(42, (int) values.get("other.extra"));
            assertEquals(3, TerrainSmootherConfig.level());
        }
    }

    @Test
    void legacySettingsMigrateOnceWithoutChangingTheOriginal() throws Exception {
        Path original = directory.resolve("skysterrainsmoother.cfg");
        byte[] bytes = "worldgen {\n B:enabled=false\n I:smoothingLevel=2\n}\ncompat {\n B:enableMineralogy=false\n}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(original, bytes);
        assertTrue(TerrainSmootherConfig.migrateLegacyConfig(directory));
        String migrated = new String(Files.readAllBytes(directory.resolve(TerrainSmootherConfig.FILE_NAME)), StandardCharsets.UTF_8);
        assertTrue(migrated.contains("enabled=false"));
        assertTrue(migrated.contains("smoothingLevel=2"));
        assertTrue(migrated.contains("enableMineralogy=false"));
        assertArrayEquals(bytes, Files.readAllBytes(original));
        assertFalse(TerrainSmootherConfig.migrateLegacyConfig(directory));
        assertEquals(migrated, new String(Files.readAllBytes(directory.resolve(TerrainSmootherConfig.FILE_NAME)), StandardCharsets.UTF_8));
    }

    @Test
    void malformedOrDuplicateSettingsStopWithoutWritingNewConfiguration() throws Exception {
        for (String contents : new String[] { "B:enabled=perhaps", "B:enabled=true\nB:enabled=false", "I:smoothingLevel=unknown" }) {
            Files.write(directory.resolve("skysterrainsmoother.cfg"), contents.getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalStateException.class, () -> TerrainSmootherConfig.migrateLegacyConfig(directory));
            assertFalse(Files.exists(directory.resolve(TerrainSmootherConfig.FILE_NAME)));
        }
    }

    @Test
    void existingTomlTakesPriorityAndLegacyBoundsAreClamped() throws Exception {
        Path current = directory.resolve(TerrainSmootherConfig.FILE_NAME);
        byte[] original = "[worldgen]\nenabled=true\n".getBytes(StandardCharsets.UTF_8);
        Files.write(current, original);
        Files.write(directory.resolve("skysterrainsmoother.cfg"), "B:enabled=false".getBytes(StandardCharsets.UTF_8));
        assertFalse(TerrainSmootherConfig.migrateLegacyConfig(directory));
        assertArrayEquals(original, Files.readAllBytes(current));
        Files.delete(current);
        Files.write(directory.resolve("skysterrainsmoother.cfg"), "I:smoothingLevel=19".getBytes(StandardCharsets.UTF_8));
        assertTrue(TerrainSmootherConfig.migrateLegacyConfig(directory));
        assertTrue(new String(Files.readAllBytes(current), StandardCharsets.UTF_8).contains("smoothingLevel=3"));
    }

    @Test
    void everyLanguageExplainsTheThreeChoices() throws Exception {
        try (java.util.stream.Stream<Path> languages = Files.list(Paths.get("src/main/resources/assets/skysterrainsmoother/lang"))) {
            for (Path language : languages.collect(java.util.stream.Collectors.toList())) {
                JsonObject values = new JsonParser().parse(new String(Files.readAllBytes(language), StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : new String[] { "enabled", "smoothingLevel", "enableMineralogy" }) {
                    assertFalse(values.get("config.skysterrainsmoother." + key + ".tooltip").getAsString().trim().isEmpty(), language.toString());
                }
            }
        }
    }
}
