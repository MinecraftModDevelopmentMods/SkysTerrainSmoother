package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;

/** Pending choices take effect on the next Minecraft start. */
public final class TerrainSmootherConfigScreen extends Screen {
    private static final String ENABLED_KEY = "config.skysterrainsmoother.enabled";
    private static final String LEVEL_KEY = "config.skysterrainsmoother.smoothingLevel";
    private static final String MINERALOGY_KEY = "config.skysterrainsmoother.enableMineralogy";
    private final Screen parent;
    private boolean enabled;
    private int smoothingLevel;
    private boolean mineralogy;

    public TerrainSmootherConfigScreen(Screen parent) {
        super(Component.literal(SkysTerrainSmoother.NAME));
        this.parent = parent;
        enabled = TerrainSmootherConfig.configuredEnabled();
        smoothingLevel = TerrainSmootherConfig.configuredLevel();
        mineralogy = TerrainSmootherConfig.configuredMineralogy();
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        addRenderableWidget(Button.builder(toggleLabel(ENABLED_KEY, enabled), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel(ENABLED_KEY, enabled));
        }).bounds(left, 65, 300, 20).tooltip(tooltip(ENABLED_KEY)).build());
        addRenderableWidget(Button.builder(levelLabel(), button -> {
            smoothingLevel = smoothingLevel % 3 + 1;
            button.setMessage(levelLabel());
        }).bounds(left, 95, 300, 20).tooltip(tooltip(LEVEL_KEY)).build());
        addRenderableWidget(Button.builder(toggleLabel(MINERALOGY_KEY, mineralogy), button -> {
            mineralogy = !mineralogy;
            button.setMessage(toggleLabel(MINERALOGY_KEY, mineralogy));
        }).bounds(left, 125, 300, 20).tooltip(tooltip(MINERALOGY_KEY)).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            TerrainSmootherConfig.saveForNextStart(enabled, smoothingLevel, mineralogy);
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 100, height - 35, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.centeredText(font, title, width / 2, 25, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static Tooltip tooltip(String key) {
        return Tooltip.create(Component.translatable(key + ".tooltip"));
    }

    private Component levelLabel() {
        return Component.literal(I18n.get(LEVEL_KEY) + ": " + smoothingLevel);
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.literal(I18n.get(key) + ": " + I18n.get(enabled ? "options.on" : "options.off"));
    }
}
