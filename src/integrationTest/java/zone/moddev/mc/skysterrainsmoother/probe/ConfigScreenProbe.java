package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
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
        parent = minecraft.currentScreen;
        activeGrass = SkysGrassSlabsConfig.generateGrassSlabs();
        activeTerrain = TerrainSmootherConfig.enabled();
        activeLevel = TerrainSmootherConfig.level();
        modIndex = 0;
        tooltips = 0;
        stage = 0;
    }

    static boolean tick(Minecraft minecraft) {
        if (modIndex == 2) {
            minecraft.displayGuiScreen(parent);
            RuntimeProbe.require(tooltips == 5, "All five settings were exercised");
            System.out.println("SKY_CONFIG_SCREENS_PASS factories=2 widgets=5 restart_safe=true");
            return true;
        }
        String modId = modIndex == 0 ? "skysgrassslabs" : "skysterrainsmoother";
        if (stage == 0) {
            screen = ModMenuProbe.open(minecraft, modId, parent);
            keys = modIndex == 0 ? new String[] { "generateGrassSlabs", "forceReplaceBuildingBricksSlabs" }
                    : new String[] { "enabled", "smoothingLevel", "enableMineralogy" };
            RuntimeProbe.require(buttons(screen).size() == keys.length + 1, "Each setting has its own control");
            original = new ArrayList<>();
            for (int index = 0; index < keys.length; index++) {
                original.add(buttons(screen).get(index).getMessage());
            }
            choiceIndex = 0;
            stage = 1;
        } else if (stage == 1) {
            Button button = buttons(screen).get(choiceIndex);
            String key = "config." + modId + "." + keys[choiceIndex];
            RuntimeProbe.require(!I18n.format(key).equals(key) && !I18n.format(key + ".tooltip").equals(key + ".tooltip"),
                    "Readable label and tooltip for " + key);
            render(minecraft, screen, button.x + 5, button.y + 5);
            screenshot(minecraft, modId + "-" + keys[choiceIndex] + "-tooltip.png");
            button.onPress();
            RuntimeProbe.require(!button.getMessage().equals(original.get(choiceIndex)), "The pending choice changes");
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
            minecraft.displayGuiScreen(pending);
            boolean changed = true;
            for (int index = 0; index < keys.length; index++) {
                changed &= !buttons(pending).get(index).getMessage().equals(original.get(index));
            }
            if (!changed) {
                settleTicks = 0;
                return false;
            }
            if (++settleTicks < 5) return false;
            screen = ModMenuProbe.open(minecraft, modId, parent);
            for (int index = 0; index < keys.length; index++) {
                Button button = buttons(screen).get(index);
                RuntimeProbe.require(!button.getMessage().equals(original.get(index)), "Saved choices are reloaded: " + keys[index]);
                for (int attempt = 0; attempt < 3 && !button.getMessage().equals(original.get(index)); attempt++) {
                    button.onPress();
                }
                RuntimeProbe.require(button.getMessage().equals(original.get(index)), "Restore the disposable configuration");
            }
            buttons(screen).get(keys.length).onPress();
            settleTicks = 0;
            stage = 3;
        } else {
            RuntimeProbe.require(System.nanoTime() < deadline, "Original config choices restored for " + modId);
            Screen restored = newConfigScreen(modId);
            minecraft.displayGuiScreen(restored);
            for (int index = 0; index < keys.length; index++) {
                if (!buttons(restored).get(index).getMessage().equals(original.get(index))) {
                    settleTicks = 0;
                    return false;
                }
            }
            if (++settleTicks < 5) return false;
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

    static void render(Minecraft minecraft, Screen screen, int mouseX, int mouseY) {
        minecraft.getFramebuffer().bindFramebuffer(true);
        minecraft.mainWindow.loadGUIRenderMatrix(Minecraft.IS_RUNNING_ON_MAC);
        com.mojang.blaze3d.platform.GlStateManager.clear(256, Minecraft.IS_RUNNING_ON_MAC);
        screen.render(mouseX, mouseY, 0);
    }

    private static List<Button> buttons(Screen screen) {
        List<Button> result = new ArrayList<>();
        for (net.minecraft.client.gui.IGuiEventListener child : screen.children()) {
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
        net.minecraft.util.ScreenShotHelper.saveScreenshot(minecraft.gameDir, savedName,
                minecraft.mainWindow.getFramebufferWidth(), minecraft.mainWindow.getFramebufferHeight(),
                minecraft.getFramebuffer(), message -> saved.countDown());
        try {
            RuntimeProbe.require(saved.await(10, java.util.concurrent.TimeUnit.SECONDS), "Screen capture finished");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Screen capture interrupted", failure);
        }
        RuntimeProbe.require(new File(minecraft.gameDir, "screenshots/" + savedName).isFile(),
                "Screen capture saved: " + savedName);
    }

    private ConfigScreenProbe() {
    }
}
