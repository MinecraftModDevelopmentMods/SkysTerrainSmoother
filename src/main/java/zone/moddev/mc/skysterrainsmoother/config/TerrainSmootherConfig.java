package zone.moddev.mc.skysterrainsmoother.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Saves future choices without changing the generation settings of a running session. */
public final class TerrainSmootherConfig {
    public static final String FILE_NAME = "skysterrainsmoother-common.toml";
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue LEVEL;
    private static final ForgeConfigSpec.BooleanValue MINERALOGY;
    private static volatile Settings active;
    private static CommentedConfig loadedValues;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("worldgen");
        ENABLED = builder.comment("Smooth new dry Overworld slopes and cave floors. Requires restart.")
                .worldRestart().define("enabled", true);
        LEVEL = builder.comment("1: slabs; 2: slabs and steps; 3: slabs, steps and corners.",
                "Missing finer shapes use slabs where they fit. Requires restart.")
                .worldRestart().defineInRange("smoothingLevel", 3, 1, 3);
        builder.pop();
        builder.push("compat");
        MINERALOGY = builder.comment("Use available natural Mineralogy rock pieces. Requires restart.")
                .worldRestart().define("enableMineralogy", true);
        builder.pop();
        SPEC = builder.build();
    }

    private TerrainSmootherConfig() {
    }

    public static void register() {
        migrateLegacyConfig(FMLPaths.CONFIGDIR.get());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, FILE_NAME);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                .addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) -> {
                    if (event.getConfig().getSpec() == SPEC) {
                        loadedValues = event.getConfig().getConfigData();
                    }
                });
    }

    public static synchronized void activateSettingsForThisRun() {
        if (active == null) {
            active = new Settings(configuredEnabled(), configuredLevel(), configuredMineralogy());
        }
    }

    public static boolean enabled() {
        Settings settings = active;
        return settings == null ? configuredEnabled() : settings.enabled;
    }

    public static int level() {
        Settings settings = active;
        return settings == null ? configuredLevel() : settings.level;
    }

    public static boolean mineralogy() {
        Settings settings = active;
        return settings == null ? configuredMineralogy() : settings.mineralogy;
    }

    public static boolean configuredEnabled() {
        return ENABLED.get();
    }

    public static int configuredLevel() {
        return LEVEL.get();
    }

    public static boolean configuredMineralogy() {
        return MINERALOGY.get();
    }

    public static void saveForNextStart(boolean enabled, int level, boolean mineralogy) {
        if (level < 1 || level > 3) {
            throw new IllegalArgumentException("Choose a smoothing level from 1 to 3");
        }
        if (loadedValues == null) {
            throw new IllegalStateException("Terrain Smoother settings have not loaded yet");
        }
        // Forge watches this file and autosaves each root edit. Save the choices
        // together so its watcher cannot reload a half-written set of settings.
        CommentedConfig pending = CommentedConfig.copy(loadedValues);
        CommentedConfig worldgen = CommentedConfig.copy(loadedValues.get("worldgen"));
        CommentedConfig compatibility = CommentedConfig.copy(loadedValues.get("compat"));
        worldgen.set("enabled", enabled);
        worldgen.set("smoothingLevel", level);
        compatibility.set("enableMineralogy", mineralogy);
        pending.set("worldgen", worldgen);
        pending.set("compat", compatibility);
        loadedValues.putAll(pending);
        // Refresh Forge's cached choices after the atomic file update. The active
        // session keeps its separate snapshot until Minecraft starts again.
        SPEC.afterReload();
        SPEC.save();
    }

    public static boolean migrateLegacyConfig(Path directory) {
        Path legacy = directory.resolve("skysterrainsmoother.cfg");
        Path current = directory.resolve(FILE_NAME);
        if (Files.exists(current) || !Files.isRegularFile(legacy)) {
            return false;
        }
        try {
            String contents = new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8);
            boolean enabled = Boolean.parseBoolean(legacyValue(contents, "B:enabled", "true"));
            boolean mineralogy = Boolean.parseBoolean(legacyValue(contents, "B:enableMineralogy", "true"));
            int level = Integer.parseInt(legacyValue(contents, "I:smoothingLevel", "3"));
            level = Math.max(1, Math.min(3, level));
            String saved = "[worldgen]\nenabled=" + enabled + "\nsmoothingLevel=" + level
                    + "\n\n[compat]\nenableMineralogy=" + mineralogy + "\n";
            Files.createDirectories(directory);
            Files.write(current, saved.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            SkysTerrainSmoother.logger.info("Copied the existing Terrain Smoother settings to TOML; "
                    + "the original CFG file is unchanged.");
            return true;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot preserve Terrain Smoother settings. "
                    + "Check the original CFG and restore the configuration backup before retrying.", failure);
        }
    }

    private static String legacyValue(String contents, String name, String fallback) {
        Matcher setting = Pattern.compile("(?m)^\\s*" + Pattern.quote(name) + "\\s*=\\s*(\\S+)\\s*$")
                .matcher(contents);
        if (!setting.find()) {
            return fallback;
        }
        String value = setting.group(1);
        if (setting.find() || name.startsWith("B:")
                && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Invalid or duplicate legacy setting: " + name);
        }
        return value;
    }

    private static final class Settings {
        final boolean enabled;
        final int level;
        final boolean mineralogy;

        Settings(boolean enabled, int level, boolean mineralogy) {
            this.enabled = enabled;
            this.level = level;
            this.mineralogy = mineralogy;
        }
    }
}
