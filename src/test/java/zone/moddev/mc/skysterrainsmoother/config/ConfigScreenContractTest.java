package zone.moddev.mc.skysterrainsmoother.config;

import java.io.File;
import java.nio.file.Path;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import static org.junit.jupiter.api.Assertions.*;

class ConfigScreenContractTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void provideMinecraftHome() throws Exception {
        java.lang.reflect.Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, temporaryDirectory.toFile());
    }

    @Test
    void configButtonHasAClientFactory() {
        assertEquals("zone.moddev.mc.skysterrainsmoother.client.TerrainSmootherGuiFactory",
                SkysTerrainSmoother.class.getAnnotation(Mod.class).guiFactory());
    }

    @Test
    void newConfigurationsStartAtLevelThree() {
        TerrainSmootherConfig.load(temporaryDirectory.resolve("new-world.cfg").toFile());
        assertEquals(3, SkysTerrainSmoother.level);
        assertEquals("3", TerrainSmootherConfig.configuration()
                .getCategory("worldgen").get("smoothingLevel").getDefault());
    }

    @Test
    void loadingKeepsTheHelpTextOnEveryProperty() {
        TerrainSmootherConfig.load(temporaryDirectory.resolve("help.cfg").toFile());
        Configuration pending = TerrainSmootherConfig.configuration();
        assertFalse(pending.getCategory("worldgen").get("enabled").getComment().trim().isEmpty());
        assertFalse(pending.getCategory("worldgen").get("smoothingLevel").getComment().trim().isEmpty());
        assertFalse(pending.getCategory("compat").get("enableMineralogy").getComment().trim().isEmpty());
    }

    @Test
    void allLanguagesExplainEachConfigEntry() throws Exception {
        try (java.util.stream.Stream<Path> languages = java.nio.file.Files.list(
                java.nio.file.Paths.get("src/main/resources/assets/skysterrainsmoother/lang"))) {
            for (Path language : languages.collect(java.util.stream.Collectors.toList())) {
                java.util.Properties translations = new java.util.Properties();
                try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(language,
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    translations.load(reader);
                }
                assertFalse(translations.getProperty("config.skysterrainsmoother.enabled.tooltip", "").trim().isEmpty(),
                        language + " needs help for enabled");
                assertFalse(translations.getProperty("config.skysterrainsmoother.smoothingLevel.tooltip", "").trim().isEmpty(),
                        language + " needs help for smoothingLevel");
                assertFalse(translations.getProperty("config.skysterrainsmoother.enableMineralogy.tooltip", "").trim().isEmpty(),
                        language + " needs help for enableMineralogy");
            }
        }
    }

    @Test
    void savingChoicesDoesNotChangeTheRunningSession() {
        File file = temporaryDirectory.resolve("settings.cfg").toFile();
        Configuration existing = new Configuration(file);
        existing.load();
        existing.get("worldgen", "smoothingLevel", 1).set(1);
        existing.save();
        TerrainSmootherConfig.load(file);
        Configuration pending = TerrainSmootherConfig.configuration();

        assertTrue(pending.getCategory("worldgen").requiresMcRestart());
        assertTrue(pending.getCategory("compat").requiresMcRestart());
        pending.get("worldgen", "enabled", true).set(false);
        pending.get("worldgen", "smoothingLevel", 1).set(3);
        pending.get("compat", "enableMineralogy", true).set(false);
        TerrainSmootherConfig.saveChanges();

        // Saving the GUI must not re-arbitrate generation or turn on migration mid-world.
        assertTrue(SkysTerrainSmoother.enabled);
        assertEquals(1, SkysTerrainSmoother.level);
        assertTrue(SkysTerrainSmoother.mineralogy);
        Configuration written = new Configuration(file);
        written.load();
        assertFalse(written.get("worldgen", "enabled", true).getBoolean());
        assertEquals(3, written.get("worldgen", "smoothingLevel", 1).getInt());
        assertFalse(written.get("compat", "enableMineralogy", true).getBoolean());

        TerrainSmootherConfig.load(file);
        assertFalse(SkysTerrainSmoother.enabled);
        assertEquals(3, SkysTerrainSmoother.level);
        assertFalse(SkysTerrainSmoother.mineralogy);
    }

    @Test
    void allThreeLevelsAndTheExistingBoundsArePreserved() {
        File file = temporaryDirectory.resolve("levels.cfg").toFile();

        for (int requested : new int[] { -1, 1, 2, 3, 9 }) {
            Configuration input = new Configuration(file);
            input.load();
            input.get("worldgen", "smoothingLevel", 1).set(requested);
            input.save();
            TerrainSmootherConfig.load(file);
            assertEquals(Math.max(1, Math.min(3, requested)), SkysTerrainSmoother.level);
            assertEquals("1", TerrainSmootherConfig.configuration()
                    .getCategory("worldgen").get("smoothingLevel").getMinValue());
            assertEquals("3", TerrainSmootherConfig.configuration()
                    .getCategory("worldgen").get("smoothingLevel").getMaxValue());
        }
    }

}
