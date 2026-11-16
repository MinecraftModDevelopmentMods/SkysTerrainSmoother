package zone.moddev.mc.skysterrainsmoother.config;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Keeps saved choices separate from the settings used by the current session. */
public final class TerrainSmootherConfig {
    private static Configuration configuration;

    public static void load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        SkysTerrainSmoother.enabled = configuration.getBoolean("enabled", "worldgen", true,
                "Smooth newly generated dry Overworld slopes and cave floors. Requires restart.");
        SkysTerrainSmoother.level = configuration.getInt("smoothingLevel", "worldgen", 3, 1, 3,
                "1: slabs; 2: slabs and steps; 3: slabs, steps and corners. Missing shapes use slabs. Requires restart.");
        SkysTerrainSmoother.mineralogy = configuration.getBoolean("enableMineralogy", "compat", true,
                "Reuse available natural Mineralogy rock slabs. Requires restart.");

        configuration.setCategoryRequiresMcRestart("worldgen", true);
        configuration.setCategoryRequiresMcRestart("compat", true);
        // Work with the existing properties so Forge retains their help text and bounds.
        configuration.getCategory("worldgen").get("enabled").setLanguageKey("config.skysterrainsmoother.enabled");
        // The generic integer getter resets Forge's bounds, so keep the existing bounded property.
        configuration.getCategory("worldgen").get("smoothingLevel")
                .setLanguageKey("config.skysterrainsmoother.smoothingLevel")
                .set(SkysTerrainSmoother.level);
        configuration.getCategory("compat").get("enableMineralogy")
                .setLanguageKey("config.skysterrainsmoother.enableMineralogy");
        saveChanges();
    }

    public static Configuration configuration() {
        if (configuration == null) {
            throw new IllegalStateException("Terrain Smoother configuration has not loaded");
        }

        return configuration;
    }

    public static void saveChanges() {
        if (configuration().hasChanged()) {
            configuration.save();
        }
    }

    private TerrainSmootherConfig() {
    }
}
