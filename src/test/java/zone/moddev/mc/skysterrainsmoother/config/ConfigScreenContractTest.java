package zone.moddev.mc.skysterrainsmoother.config;

import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.MinecraftTestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import java.util.Set;
import java.lang.reflect.Field;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigScreenContractTest {
    @Test
    void everyMaterialCombinationSavesWithoutChangingTheRunningSession() throws Exception {
        var all = EnumSet.allOf(MaterialGroup.class);
        assertEquals(all, TerrainSmootherConfig.configuredGroups());
        TerrainSmootherConfig.activateSettingsForThisRun();
        var groups = MaterialGroup.values();
        var catalogue = new MaterialCatalogue();
        MinecraftTestBootstrap.registerVanilla();
        for (int mask = 0; mask < 1 << groups.length; mask++) {
            var enabled = EnumSet.noneOf(MaterialGroup.class);
            for (var group : groups) {
                if ((mask & 1 << group.ordinal()) != 0) {
                    enabled.add(group);
                }
            }
            TerrainSmootherConfig.saveForNextStart(true, 3, true, enabled);
            assertEquals(enabled, TerrainSmootherConfig.configuredGroups());
            for (var group : groups) {
                assertTrue(TerrainSmootherConfig.groupEnabled(group), "Running settings remain unchanged");
            }
            Field active = TerrainSmootherConfig.class.getDeclaredField("active");
            active.setAccessible(true);
            active.set(null, null);
            TerrainSmootherConfig.activateSettingsForThisRun();
            for (var group : groups) {
                assertEquals(enabled.contains(group), TerrainSmootherConfig.groupEnabled(group));
                var mapping = new TerrainMaterial(
                        ResourceLocation.parse("test:" + group.configKey().toLowerCase()),
                        Set.of(Blocks.DIRT.defaultBlockState()),
                        Blocks.STONE_SLAB.defaultBlockState(), null, null, false, null, group);
                assertEquals(enabled.contains(group), catalogue.allows(mapping, null, 0, BlockPos.ZERO));
            }
            TerrainSmootherConfig.saveForNextStart(true, 3, true, all);
            active.set(null, null);
            TerrainSmootherConfig.activateSettingsForThisRun();
        }
    }

    @Test
    void upgradingThreeKeyTomlKeepsItsChoicesAndEnablesEveryNewGroup() throws Exception {
        container.tracker.unloadConfigs(ModConfig.Type.COMMON);
        Files.writeString(configFile, "[worldgen]\nenabled=false\nsmoothingLevel=2\n[compat]\nenableMineralogy=false\n");
        container.tracker.loadConfigs(ModConfig.Type.COMMON, configFile.getParent());
        assertFalse(TerrainSmootherConfig.configuredEnabled());
        assertEquals(2, TerrainSmootherConfig.configuredLevel());
        assertFalse(TerrainSmootherConfig.configuredMineralogy());
        assertEquals(EnumSet.allOf(MaterialGroup.class),
                TerrainSmootherConfig.configuredGroups());
    }
    @TempDir
    Path directory;
    private TestContainer container;
    private Path configFile;

    @BeforeEach
    void loadDefaults() throws Exception {
        Field field = TerrainSmootherConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, null);
        Path configDirectory = directory.resolve("config-test");
        Files.createDirectories(configDirectory);
        configFile = configDirectory.resolve(TerrainSmootherConfig.FILE_NAME);
        FMLPaths.loadAbsolutePaths(configDirectory);
        container = new TestContainer();
        TerrainSmootherConfig.register(container.getEventBus(), container);
        CommentedConfig defaults = CommentedConfig.inMemory();
        container.config.getSpec().correct(defaults);
        try (var writer = Files.newBufferedWriter(configFile)) {
            new TomlWriter().write(defaults, writer);
        }
        container.tracker.loadConfigs(ModConfig.Type.COMMON, configDirectory);
    }

    @AfterEach
    void unloadConfig() {
        if (container != null) {
            container.tracker.unloadConfigs(ModConfig.Type.COMMON);
        }
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
        CommentedConfig values = container.config.getLoadedConfig().config();
        values.set("worldgen.extra", "kept");
        values.setComment("worldgen.extra", "A pack setting");
        values.set("other", values.createSubConfig());
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
            try (var reader = Files.newBufferedReader(configFile)) {
                CommentedConfig saved = new TomlParser().parse(reader);
                assertEquals(level, (int) saved.get("worldgen.smoothingLevel"));
                assertFalse((boolean) saved.get("worldgen.enabled"));
                assertFalse((boolean) saved.get("compat.enableMineralogy"));
                assertEquals("kept", saved.get("worldgen.extra"));
                assertEquals("A pack setting", saved.getComment("worldgen.extra").trim());
                assertEquals(42, (int) saved.get("other.extra"));
            }
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
    /** Load and save the test config without sharing the game's tracker. */
    private static final class TestContainer extends ModContainer {
        private final ConfigTracker tracker = new ConfigTracker();
        private final IEventBus bus = BusBuilder.builder().build();
        private ModConfig config;

        TestContainer() {
            super((IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(),
                    new Class<?>[] {IModInfo.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getModId")) {
                            return "skysterrainsmoother";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }));
        }

        @Override
        public IEventBus getEventBus() {
            return bus;
        }

        @Override
        public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) {
            config = tracker.registerConfig(type, spec, this, fileName);
        }
    }
}
