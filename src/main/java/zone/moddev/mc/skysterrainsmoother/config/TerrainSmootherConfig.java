package zone.moddev.mc.skysterrainsmoother.config;

import java.io.File;
import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import net.minecraftforge.common.config.Configuration;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Keeps saved choices separate from the settings used by the current session. */
public final class TerrainSmootherConfig {
    private static Configuration configuration;
    private static int enabledGroups = (1 << MaterialGroup.values().length) - 1;

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
        enabledGroups = 0;
        for (MaterialGroup group : MaterialGroup.values()) {
            String key = group.configKey();
            if (configuration.getBoolean(key, "materials", true,
                    "Smooth this material family in new slopes and cave floors. Requires restart.")) {
                enabledGroups |= 1 << group.ordinal();
            }
            configuration.getCategory("materials").get(key)
                    .setLanguageKey("config.skysterrainsmoother.materials." + key).setRequiresMcRestart(true);
        }
        configuration.setCategoryRequiresMcRestart("materials", true);
        configuration.getCategory("materials").setLanguageKey("config.skysterrainsmoother.materials")
                .setComment("Choose which material families can be smoothed. Requires restart.");
        configuration.getCategory("compat").setLanguageKey("config.skysterrainsmoother.compatibility")
                .setComment("Choose optional mod support. Requires restart.");
        saveChanges();
    }

    /** The GUI edits saved choices; generation keeps this startup snapshot until restart. */
    public static boolean groupEnabled(MaterialGroup group) {
        return (enabledGroups & 1 << group.ordinal()) != 0;
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
