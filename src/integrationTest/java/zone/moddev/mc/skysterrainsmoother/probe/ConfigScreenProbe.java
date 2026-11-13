package zone.moddev.mc.skysterrainsmoother.probe;

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
    private static final List<java.nio.file.Path> captures = new ArrayList<>();
    private static final java.util.Set<java.nio.file.Path> savedCaptures = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
    private static java.util.Set<zone.moddev.mc.skysterrainsmoother.api.MaterialGroup> activeGroups;
    private static List<String> original;
    private static String[] keys;

    static void begin(Minecraft minecraft) {
        captures.clear();
        savedCaptures.clear();
        parent = minecraft.gui.screen();
        activeGrass = SkysGrassSlabsConfig.generateGrassSlabs();
        activeTerrain = TerrainSmootherConfig.enabled();
        activeLevel = TerrainSmootherConfig.level();
        cancelPendingPages(minecraft);
        activeGroups = java.util.Arrays.stream(zone.moddev.mc.skysterrainsmoother.api.MaterialGroup.values())
                .filter(TerrainSmootherConfig::groupEnabled).collect(java.util.stream.Collectors.toSet());
        modIndex = 0;
        tooltips = 0;
        stage = 0;
    }

    static boolean tick(Minecraft minecraft) {
        if (modIndex == 4) {
            if (!savedCaptures.containsAll(captures)) {
                RuntimeProbe.require(System.nanoTime() < deadline, "Native config screenshots finished saving");
                return false;
            }
            RuntimeProbe.require(captures.size() == 4, "Every native config page was captured");
            minecraft.gui.setScreen(parent);
            RuntimeProbe.require(tooltips == 12, "All twelve settings and both child pages were exercised");
            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("SKY_CONFIG_SCREENS_PASS factories=2 pages=4 widgets=12 screenshots=4 restart_safe=true");
            return true;
        }
        String modId = modIndex == 0 ? "skysgrassslabs" : "skysterrainsmoother";
        if (stage == 0) {
            screen = openPage(minecraft, modId);
            keys = modIndex == 0 ? new String[] { "generateGrassSlabs", "forceReplaceBuildingBricksSlabs",
                        "forceReplaceGrassSlabsModContent" }
                    : modIndex == 1 ? new String[] { "enabled", "smoothingLevel" }
                    : modIndex == 2 ? new String[] { "materials.soil", "materials.sandAndGravel", "materials.stone",
                            "materials.snow", "materials.ice", "materials.other" }
                    : new String[] { "enableMineralogy" };
            RuntimeProbe.require(buttons(screen).size() == (modIndex == 1 ? 5 : keys.length + 1), "Each setting has its own control");
            original = new ArrayList<>();
            for (int index = 0; index < keys.length; index++) {
                original.add(buttons(screen).get(index).getMessage().getString());
            }
            minecraft.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            screen.setFocused(buttons(screen).get(0));
            choiceIndex = 0;
            stage = 1;
        } else if (stage == 1) {
            if (choiceIndex == 0) capturePage(minecraft);
            Button button = buttons(screen).get(choiceIndex);
            String key = "config." + modId + "." + keys[choiceIndex];
            RuntimeProbe.require(!I18n.get(key).equals(key) && !I18n.get(key + ".tooltip").equals(key + ".tooltip"),
                    "Readable label and tooltip for " + key);
            var holder = (net.minecraft.client.gui.components.WidgetTooltipHolder) ModMenuProbe.field(
                    net.minecraft.client.gui.components.AbstractWidget.class, button, "tooltip");
            var tooltip = holder.get();
            RuntimeProbe.require(tooltip != null, "The button exposes its tooltip: " + key);
            var message = (net.minecraft.network.chat.Component) ModMenuProbe.field(
                    net.minecraft.client.gui.components.Tooltip.class, tooltip, "message");
            RuntimeProbe.require(message.getString().equals(I18n.get(key + ".tooltip")),
                    "The button uses the correct translated tooltip: " + key);
            button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
            RuntimeProbe.require(!button.getMessage().getString().equals(original.get(choiceIndex)), "The pending choice changes");
            tooltips++;
            if (++choiceIndex == keys.length) {
                saveChoices();
                deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                settleTicks = 0;
                stage = 2;
            }
        } else if (stage == 2) {
            // Native config saves and file watches are asynchronous. Reopening on
            // a later frame also follows the same path a player uses.
            RuntimeProbe.require(System.nanoTime() < deadline, "Saved config choices became readable for " + modId);
            Screen pending = newConfigScreen(modId);
            minecraft.gui.setScreen(pending);
            boolean changed = true;
            for (int index = 0; index < keys.length; index++) {
                changed &= !buttons(pending).get(index).getMessage().getString().equals(original.get(index));
            }
            if (!changed || !configuredValuesMatchDisk(modId)) {
                settleTicks = 0;
                return false;
            }
            if (++settleTicks < 20) return false;
            screen = openPage(minecraft, modId);
            for (int index = 0; index < keys.length; index++) {
                Button button = buttons(screen).get(index);
                RuntimeProbe.require(!button.getMessage().getString().equals(original.get(index)), "Saved choices are reloaded: " + keys[index]);
                for (int attempt = 0; attempt < 3 && !button.getMessage().getString().equals(original.get(index)); attempt++) {
                    button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
                }
                RuntimeProbe.require(button.getMessage().getString().equals(original.get(index)), "Restore the disposable configuration");
            }
            saveChoices();
            settleTicks = 0;
            stage = 3;
        } else {
            RuntimeProbe.require(System.nanoTime() < deadline, "Original config choices restored for " + modId);
            Screen restored = newConfigScreen(modId);
            minecraft.gui.setScreen(restored);
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
            RuntimeProbe.require(activeGroups.equals(java.util.Arrays.stream(
                    zone.moddev.mc.skysterrainsmoother.api.MaterialGroup.values())
                    .filter(TerrainSmootherConfig::groupEnabled).collect(java.util.stream.Collectors.toSet())),
                    "Saving material choices leaves running generation unchanged");
            modIndex++;
            stage = 0;
        }
        return false;
    }

    private static void cancelPendingPages(Minecraft minecraft) {
        var originalGroups = TerrainSmootherConfig.configuredGroups();
        var draft = new zone.moddev.mc.skysterrainsmoother.client.TerrainSmootherConfigScreen(parent);
        minecraft.gui.setScreen(draft);
        buttons(draft).get(2).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        String originalSoil = buttons(draft).get(0).getMessage().getString();
        buttons(draft).get(0).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        String pendingSoil = buttons(draft).get(0).getMessage().getString();
        draft.resize(320, 240);
        RuntimeProbe.require(buttons(draft).get(0).getMessage().getString().equals(pendingSoil),
                "Resizing a material page preserves pending choices");
        var controls = buttons(draft);
        for (Button control : controls) {
            RuntimeProbe.require(control.getX() >= 0 && control.getY() >= 0
                            && control.getX() + control.getWidth() <= 320 && control.getY() + control.getHeight() <= 240,
                    "Material controls fit the minimum supported UI size");
            for (Button other : controls) {
                if (other == control) continue;
                RuntimeProbe.require(control.getX() + control.getWidth() <= other.getX()
                                || other.getX() + other.getWidth() <= control.getX()
                                || control.getY() + control.getHeight() <= other.getY()
                                || other.getY() + other.getHeight() <= control.getY(),
                        "Material controls do not overlap");
            }
        }
        draft.onClose();
        buttons(draft).get(3).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        buttons(draft).get(0).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        draft.onClose();
        buttons(draft).get(2).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        RuntimeProbe.require(buttons(draft).get(0).getMessage().getString().equals(pendingSoil),
                "Page navigation retains pending material choices");
        draft.onClose();
        draft.onClose();
        RuntimeProbe.require(TerrainSmootherConfig.configuredGroups().equals(originalGroups),
                "Escape from the main page discards every pending edit");
        var reopened = new zone.moddev.mc.skysterrainsmoother.client.TerrainSmootherConfigScreen(parent);
        minecraft.gui.setScreen(reopened);
        buttons(reopened).get(2).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        RuntimeProbe.require(buttons(reopened).get(0).getMessage().getString().equals(originalSoil),
                "Cancelled choices are not saved");
        reopened.onClose();
        reopened.onClose();
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info(
                "TERRAIN_CONFIG_PAGES_PASS navigation=true resize=true cancel=true");
    }

    private static Screen newConfigScreen(String modId) {
        Screen result = modId.equals("skysgrassslabs")
                ? new zone.moddev.mc.skysgrassslabs.client.GrassSlabsConfigScreen(parent)
                : new zone.moddev.mc.skysterrainsmoother.client.TerrainSmootherConfigScreen(parent);
        Minecraft.getInstance().gui.setScreen(result);
        selectPage(result);
        return result;
    }

    private static Screen openPage(Minecraft minecraft, String modId) {
        Screen result = ModMenuProbe.open(minecraft, modId, parent);
        selectPage(result);
        return result;
    }

    private static void selectPage(Screen result) {
        if (modIndex >= 2) {
            buttons(result).get(modIndex == 2 ? 2 : 3).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        }
    }

    private static void saveChoices() {
        if (modIndex >= 2) {
            buttons(screen).get(buttons(screen).size() - 1).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
        }
        buttons(screen).get(buttons(screen).size() - 1).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.sdl.SDLKeycode.SDLK_RETURN, 0, 0));
    }

    private static boolean configuredValuesMatchDisk(String modId) {
        String[] values = modId.equals("skysgrassslabs")
                ? new String[] {
                    Boolean.toString(SkysGrassSlabsConfig.configuredGrassGeneration()),
                    Boolean.toString(SkysGrassSlabsConfig.configuredLegacyReplacement()),
                    Boolean.toString(SkysGrassSlabsConfig.configuredGrassSlabsReplacement())
                }
                : modIndex == 1 ? new String[] {
                    Boolean.toString(TerrainSmootherConfig.configuredEnabled()),
                    Integer.toString(TerrainSmootherConfig.configuredLevel())
                } : modIndex == 2 ? java.util.Arrays.stream(zone.moddev.mc.skysterrainsmoother.api.MaterialGroup.values())
                        .map(group -> Boolean.toString(TerrainSmootherConfig.configuredGroups().contains(group)))
                        .toArray(String[]::new)
                : new String[] {Boolean.toString(TerrainSmootherConfig.configuredMineralogy())};
        try {
            String contents = java.nio.file.Files.readString(java.nio.file.Paths.get(
                    "config", modId + "-common.toml"), java.nio.charset.StandardCharsets.UTF_8);
            for (int index = 0; index < keys.length; index++) {
                java.util.regex.Matcher value = java.util.regex.Pattern.compile(
                        "(?m)^\\h*" + java.util.regex.Pattern.quote(keys[index].replace("materials.", ""))
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

    private static List<Button> buttons(Screen screen) {
        List<Button> result = new ArrayList<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof Button) {
                result.add((Button) child);
            }
        }
        return result;
    }

    private static void capturePage(Minecraft minecraft) {
        String phase = System.getProperty("skysterrainsmoother.integrationPhase", "client");
        String filename = phase + "-config-page-" + modIndex + ".png";
        java.nio.file.Path path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(filename);
        captures.add(path);
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, filename,
                minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
                    if (java.nio.file.Files.isRegularFile(path)) savedCaptures.add(path);
                });
    }

    private ConfigScreenProbe() {
    }
}
