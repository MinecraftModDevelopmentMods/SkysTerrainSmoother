package zone.moddev.mc.skysterrainsmoother.client;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;

/** Pages share pending choices; only Done saves them for the next Minecraft start. */
public final class TerrainSmootherConfigScreen extends Screen {
    private static final String PREFIX = "config.skysterrainsmoother.";
    private enum Page { WORLDGEN, MATERIALS, COMPATIBILITY }

    private final Screen parent;
    private final EnumSet<MaterialGroup> groups = EnumSet.noneOf(MaterialGroup.class);
    private final Map<Button, String> tooltips = new LinkedHashMap<>();
    private Page page = Page.WORLDGEN;
    private boolean enabled;
    private int smoothingLevel;
    private boolean mineralogy;

    public TerrainSmootherConfigScreen(Screen parent) {
        super(Component.literal(SkysTerrainSmoother.NAME));
        this.parent = parent;
        enabled = TerrainSmootherConfig.configuredEnabled();
        smoothingLevel = TerrainSmootherConfig.configuredLevel();
        mineralogy = TerrainSmootherConfig.configuredMineralogy();
        groups.addAll(TerrainSmootherConfig.configuredGroups());
    }

    @Override
    protected void init() {
        tooltips.clear();
        int left = width / 2 - 150;
        if (page == Page.WORLDGEN) {
            control("enabled", toggleLabel("enabled", enabled), left, 55, 300, button -> {
                enabled = !enabled;
                button.setMessage(toggleLabel("enabled", enabled));
            });
            control("smoothingLevel", levelLabel(), left, 85, 300, button -> {
                smoothingLevel = smoothingLevel % 3 + 1;
                button.setMessage(levelLabel());
            });
            control("materials", Component.translatable(PREFIX + "materials"), left, 115, 300,
                    button -> show(Page.MATERIALS));
            control("compatibility", Component.translatable(PREFIX + "compatibility"), left, 145, 300,
                    button -> show(Page.COMPATIBILITY));
        } else if (page == Page.MATERIALS) {
            int index = 0;
            for (MaterialGroup group : MaterialGroup.values()) {
                String key = "materials." + group.configKey();
                control(key, toggleLabel(key, groups.contains(group)), left + index % 2 * 154,
                        60 + index / 2 * 32, 146, button -> {
                            if (!groups.remove(group)) {
                                groups.add(group);
                            }
                            button.setMessage(toggleLabel(key, groups.contains(group)));
                        });
                index++;
            }
        } else {
            control("enableMineralogy", toggleLabel("enableMineralogy", mineralogy), left, 65, 300, button -> {
                mineralogy = !mineralogy;
                button.setMessage(toggleLabel("enableMineralogy", mineralogy));
            });
        }
        Component action = Component.translatable(page == Page.WORLDGEN ? "gui.done" : "gui.back");
        addRenderableWidget(Button.builder(action, button -> {
            if (page != Page.WORLDGEN) {
                show(Page.WORLDGEN);
                return;
            }
            TerrainSmootherConfig.saveForNextStart(enabled, smoothingLevel, mineralogy, groups);
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height - 35, 200, 20).build());
    }

    private void control(String key, Component label, int left, int top, int width, Button.OnPress action) {
        Button button = addRenderableWidget(Button.builder(label, action).bounds(left, top, width, 20).build());
        tooltips.put(button, PREFIX + key);
    }

    private void show(Page next) {
        page = next;
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics, mouseX, mouseY, partialTicks);
        Component heading = page == Page.WORLDGEN ? title
                : Component.translatable(PREFIX + (page == Page.MATERIALS ? "materials" : "compatibility"));
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(font, heading, width / 2, 25, 0xFFFFFFFF);
        String tooltip = tooltips.entrySet().stream().filter(entry -> entry.getKey().isMouseOver(mouseX, mouseY))
                .map(Map.Entry::getValue).findFirst().orElseGet(() -> tooltips.entrySet().stream()
                        .filter(entry -> entry.getKey().isFocused()).map(Map.Entry::getValue).findFirst().orElse(null));
        if (tooltip != null) {
            graphics.renderTooltip(font, font.split(Component.translatable(tooltip + ".tooltip"), 280), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        if (page == Page.WORLDGEN) {
            minecraft.setScreen(parent);
        } else {
            show(Page.WORLDGEN);
        }
    }

    private Component levelLabel() {
        return Component.literal(I18n.get(PREFIX + "smoothingLevel") + ": " + smoothingLevel);
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.literal(I18n.get(PREFIX + key) + ": " + I18n.get(enabled ? "options.on" : "options.off"));
    }
}
