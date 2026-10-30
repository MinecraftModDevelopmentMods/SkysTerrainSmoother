package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysgrassslabs.config.SkysGrassSlabsConfig;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;

/** Opens the actual registered screens inside the disposable client, without taking desktop control. */
final class ConfigScreenProbe {
    static void run(Minecraft minecraft) {
        ModMenuProbe.run(minecraft);
        for (String modId : new String[] { "skysgrassslabs", "skysterrainsmoother" }) {
            IModGuiFactory factory = FMLClientHandler.instance()
                    .getGuiFactoryFor(Loader.instance().getIndexedModList().get(modId));
            RuntimeProbe.require(factory != null, "Mods list has a config factory for " + modId);
            RuntimeProbe.require(factory.hasConfigGui(), "Config button is enabled for " + modId);
            GuiScreen screen = factory.createConfigGui(minecraft.currentScreen);
            net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(minecraft);
            screen.setWorldAndResolution(minecraft, resolution.getScaledWidth(), resolution.getScaledHeight());
            GuiConfig configScreen = (GuiConfig) screen;
            int expectedEntries = modId.equals("skysgrassslabs") ? 2 : 3;
            RuntimeProbe.require(configScreen.configElements.size() == expectedEntries
                    && configScreen.entryList.listEntries.size() == expectedEntries
                    && configScreen.allRequireMcRestart, "all settings have widgets and require restart");
            for (net.minecraftforge.fml.client.config.IConfigElement element : configScreen.configElements) {
                RuntimeProbe.require(!I18n.format(element.getLanguageKey()).equals(element.getLanguageKey()),
                        "setting has a readable translated label: " + element.getName());
            }
            minecraft.getFramebuffer().bindFramebuffer(true);
            minecraft.entityRenderer.setupOverlayRendering();
            screen.drawScreen(0, 0, 0);
            net.minecraft.util.ScreenShotHelper.saveScreenshot(minecraft.gameDir, modId + "-config.png",
                    minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
            RuntimeProbe.require(new java.io.File(minecraft.gameDir, "screenshots/" + modId + "-config.png").isFile(),
                    "config screen screenshot saved");
            screen.onGuiClosed();
        }

        Configuration grass = (Configuration) grassConfiguration("configuration");
        Configuration terrain = TerrainSmootherConfig.configuration();
        boolean activeGrass = SkysGrassSlabsConfig.generateGrassSlabs();
        int activeLevel = SkysTerrainSmoother.level;

        grass.get("worldgen", "generateGrassSlabs", true).set(!activeGrass);
        int pendingLevel = activeLevel == 3 ? 1 : 3;
        terrain.getCategory("worldgen").get("smoothingLevel").set(pendingLevel);
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.OnConfigChangedEvent("unrelated", null, false, true));
        RuntimeProbe.require(read(grass).get("worldgen", "generateGrassSlabs", true).getBoolean() == activeGrass
                && read(terrain).get("worldgen", "smoothingLevel", 1).getInt() == activeLevel,
                "other mods cannot save these settings");
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.OnConfigChangedEvent("skysgrassslabs", null, false, true));
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.OnConfigChangedEvent("skysterrainsmoother", null, false, true));
        // Forge's save() does not clear every dirty flag. Read the files back instead.
        RuntimeProbe.require(read(grass).get("worldgen", "generateGrassSlabs", true).getBoolean() != activeGrass
                && read(terrain).get("worldgen", "smoothingLevel", 1).getInt() == pendingLevel,
                "actual GUI event handlers saved both files");
        RuntimeProbe.require(SkysGrassSlabsConfig.generateGrassSlabs() == activeGrass
                && SkysTerrainSmoother.level == activeLevel, "pending GUI choices leave active settings unchanged");

        grass.get("worldgen", "generateGrassSlabs", true).set(activeGrass);
        terrain.getCategory("worldgen").get("smoothingLevel").set(activeLevel);
        grassConfiguration("saveChanges");
        TerrainSmootherConfig.saveChanges();
        System.out.println("SKY_CONFIG_SCREENS_PASS factories=2 widgets=5 restart_safe=true");
    }

    private static Configuration read(Configuration source) {
        Configuration written = new Configuration(source.getConfigFile());
        written.load();
        return written;
    }

    private static Object grassConfiguration(String method) {
        try {
            // This is a build-only check, not an addition to the Grass Slabs extension API.
            return SkysGrassSlabsConfig.class.getMethod(method).invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Use the matching Grass Slabs config-screen candidate", failure);
        }
    }

    private ConfigScreenProbe() {
    }
}
