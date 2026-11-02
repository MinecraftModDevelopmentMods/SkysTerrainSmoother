package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import zone.moddev.mc.skysgrassslabs.config.SkysGrassSlabsConfig;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;

/** Opens, renders and saves the native settings screens, preserving the running session. */
final class ConfigScreenProbe {
    private static int screenshotNumber;
    private static Screen parent;
    private static Screen screen;
    private static int modIndex;
    private static int choiceIndex;
    private static int stage;
    private static int settleTicks;
    private static int tooltips;
    private static long deadline;
    private static boolean activeGrass;
    private static boolean activeTerrain;
    private static int activeLevel;
    private static List<String> original;
    private static String[] keys;

    static void begin(Minecraft minecraft) {
        parent = minecraft.screen;
        activeGrass = SkysGrassSlabsConfig.generateGrassSlabs();
        activeTerrain = TerrainSmootherConfig.enabled();
        activeLevel = TerrainSmootherConfig.level();
        modIndex = 0;
        tooltips = 0;
        stage = 0;
    }

    static boolean tick(Minecraft minecraft) {
        if (modIndex == 2) {
            minecraft.setScreen(parent);
            RuntimeProbe.require(tooltips == 6, "All six settings were exercised");
            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("SKY_CONFIG_SCREENS_PASS factories=2 widgets=6 restart_safe=true");
            return true;
        }
        String modId = modIndex == 0 ? "skysgrassslabs" : "skysterrainsmoother";
        if (stage == 0) {
            screen = ModMenuProbe.open(minecraft, modId, parent);
            keys = modIndex == 0 ? new String[] { "generateGrassSlabs", "forceReplaceBuildingBricksSlabs",
                        "forceReplaceGrassSlabsModContent" }
                    : new String[] { "enabled", "smoothingLevel", "enableMineralogy" };
            RuntimeProbe.require(buttons(screen).size() == keys.length + 1, "Each setting has its own control");
            original = new ArrayList<>();
            for (int index = 0; index < keys.length; index++) {
                original.add(buttons(screen).get(index).getMessage().getString());
            }
            choiceIndex = 0;
            stage = 1;
        } else if (stage == 1) {
            Button button = buttons(screen).get(choiceIndex);
            String key = "config." + modId + "." + keys[choiceIndex];
            RuntimeProbe.require(!I18n.get(key).equals(key) && !I18n.get(key + ".tooltip").equals(key + ".tooltip"),
                    "Readable label and tooltip for " + key);
            render(minecraft, screen, button.x + 5, button.y + 5);
            screenshot(minecraft, modId + "-" + keys[choiceIndex] + "-tooltip.png");
            button.onPress();
            RuntimeProbe.require(!button.getMessage().getString().equals(original.get(choiceIndex)), "The pending choice changes");
            tooltips++;
            if (++choiceIndex == keys.length) {
                buttons(screen).get(keys.length).onPress();
                deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                settleTicks = 0;
                stage = 2;
            }
        } else if (stage == 2) {
            // Native config saves and file watches are asynchronous. Reopening on
            // a later frame also follows the same path a player uses.
            RuntimeProbe.require(System.nanoTime() < deadline, "Saved config choices became readable for " + modId);
            Screen pending = newConfigScreen(modId);
            minecraft.setScreen(pending);
            boolean changed = true;
            for (int index = 0; index < keys.length; index++) {
                changed &= !buttons(pending).get(index).getMessage().getString().equals(original.get(index));
            }
            if (!changed || !configuredValuesMatchDisk(modId)) {
                settleTicks = 0;
                return false;
            }
            if (++settleTicks < 20) return false;
            screen = ModMenuProbe.open(minecraft, modId, parent);
            for (int index = 0; index < keys.length; index++) {
                Button button = buttons(screen).get(index);
                RuntimeProbe.require(!button.getMessage().getString().equals(original.get(index)), "Saved choices are reloaded: " + keys[index]);
                for (int attempt = 0; attempt < 3 && !button.getMessage().getString().equals(original.get(index)); attempt++) {
                    button.onPress();
                }
                RuntimeProbe.require(button.getMessage().getString().equals(original.get(index)), "Restore the disposable configuration");
            }
            buttons(screen).get(keys.length).onPress();
            settleTicks = 0;
            stage = 3;
        } else {
            RuntimeProbe.require(System.nanoTime() < deadline, "Original config choices restored for " + modId);
            Screen restored = newConfigScreen(modId);
            minecraft.setScreen(restored);
            for (int index = 0; index < keys.length; index++) {
                if (!buttons(restored).get(index).getMessage().getString().equals(original.get(index))) {
                    settleTicks = 0;
                    return false;
                }
            }
            if (!configuredValuesMatchDisk(modId)) {
                settleTicks = 0;
                return false;
            }
            if (++settleTicks < 20) return false;
            RuntimeProbe.require(SkysGrassSlabsConfig.generateGrassSlabs() == activeGrass
                            && TerrainSmootherConfig.enabled() == activeTerrain && TerrainSmootherConfig.level() == activeLevel,
                    "Saving choices leaves this session unchanged");
            modIndex++;
            stage = 0;
        }
        return false;
    }

    private static Screen newConfigScreen(String modId) {
        return modId.equals("skysgrassslabs")
                ? new zone.moddev.mc.skysgrassslabs.client.GrassSlabsConfigScreen(parent)
                : new zone.moddev.mc.skysterrainsmoother.client.TerrainSmootherConfigScreen(parent);
    }

    private static boolean configuredValuesMatchDisk(String modId) {
        String[] values = modId.equals("skysgrassslabs")
                ? new String[] {
                    Boolean.toString(SkysGrassSlabsConfig.configuredGrassGeneration()),
                    Boolean.toString(SkysGrassSlabsConfig.configuredLegacyReplacement()),
                    Boolean.toString(SkysGrassSlabsConfig.configuredGrassSlabsReplacement())
                }
                : new String[] {
                    Boolean.toString(TerrainSmootherConfig.configuredEnabled()),
                    Integer.toString(TerrainSmootherConfig.configuredLevel()),
                    Boolean.toString(TerrainSmootherConfig.configuredMineralogy())
                };
        try {
            String contents = java.nio.file.Files.readString(java.nio.file.Paths.get(
                    "config", modId + "-common.toml"), java.nio.charset.StandardCharsets.UTF_8);
            for (int index = 0; index < keys.length; index++) {
                java.util.regex.Matcher value = java.util.regex.Pattern.compile(
                        "(?m)^\\h*" + java.util.regex.Pattern.quote(keys[index])
                                + "\\h*=\\h*(true|false|[1-3])\\h*$").matcher(contents);
                if (!value.find() || !value.group(1).equals(values[index]) || value.find()) {
                    return false;
                }
            }
            return true;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read the saved test configuration for " + modId, failure);
        }
    }

    static void render(Minecraft minecraft, Screen screen, int mouseX, int mouseY) {
        minecraft.getMainRenderTarget().bindWrite(true);
        com.mojang.blaze3d.platform.Window window = minecraft.getWindow();

        com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX);
        float farPlane = net.minecraftforge.client.ForgeHooksClient.getGuiFarPlane();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
                com.mojang.math.Matrix4f.orthographic(0,
                        (float) (window.getWidth() / window.getGuiScale()), 0,
                        (float) (window.getHeight() / window.getGuiScale()), 1000, farPlane));
        com.mojang.blaze3d.vertex.PoseStack modelView =
                com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.setIdentity();
        modelView.translate(0, 0, 1000 - farPlane);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        screen.render(new com.mojang.blaze3d.vertex.PoseStack(), mouseX, mouseY, 0);
    }

    private static List<Button> buttons(Screen screen) {
        List<Button> result = new ArrayList<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof Button) {
                result.add((Button) child);
            }
        }
        return result;
    }

    static void screenshot(Minecraft minecraft, String filename) {
        String phase = System.getProperty("skysterrainsmoother.integrationPhase", "client");
        String savedName = phase + "-" + screenshotNumber++ + "-" + filename;
        java.util.concurrent.CountDownLatch saved = new java.util.concurrent.CountDownLatch(1);
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, savedName,
                minecraft.getMainRenderTarget(), message -> saved.countDown());
        try {
            RuntimeProbe.require(saved.await(10, java.util.concurrent.TimeUnit.SECONDS), "Screen capture finished");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Screen capture interrupted", failure);
        }
        RuntimeProbe.require(new File(minecraft.gameDirectory, "screenshots/" + savedName).isFile(),
                "Screen capture saved: " + savedName);
    }

    private ConfigScreenProbe() {
    }
}
