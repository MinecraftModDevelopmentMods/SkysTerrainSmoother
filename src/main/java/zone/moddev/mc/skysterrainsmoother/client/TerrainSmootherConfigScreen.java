package zone.moddev.mc.skysterrainsmoother.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.StringTextComponent;
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
    private Button enabledButton;
    private Button levelButton;
    private Button mineralogyButton;

    public TerrainSmootherConfigScreen(Screen parent) {
        super(new StringTextComponent(SkysTerrainSmoother.NAME));
        this.parent = parent;
        enabled = TerrainSmootherConfig.configuredEnabled();
        smoothingLevel = TerrainSmootherConfig.configuredLevel();
        mineralogy = TerrainSmootherConfig.configuredMineralogy();
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        enabledButton = addButton(new Button(left, 65, 300, 20, toggleLabel(ENABLED_KEY, enabled), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel(ENABLED_KEY, enabled));
        }));
        levelButton = addButton(new Button(left, 95, 300, 20, levelLabel(), button -> {
            smoothingLevel = smoothingLevel % 3 + 1;
            button.setMessage(levelLabel());
        }));
        mineralogyButton = addButton(new Button(left, 125, 300, 20,
                toggleLabel(MINERALOGY_KEY, mineralogy), button -> {
                    mineralogy = !mineralogy;
                    button.setMessage(toggleLabel(MINERALOGY_KEY, mineralogy));
                }));
        addButton(new Button(width / 2 - 100, height - 35, 200, 20, new TranslationTextComponent("gui.done"), button -> {
            TerrainSmootherConfig.saveForNextStart(enabled, smoothingLevel, mineralogy);
            minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(poseStack);
        drawCenteredString(poseStack, font, title, width / 2, 25, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        String hovered = enabledButton.isMouseOver(mouseX, mouseY) ? ENABLED_KEY
                : levelButton.isMouseOver(mouseX, mouseY) ? LEVEL_KEY
                : mineralogyButton.isMouseOver(mouseX, mouseY) ? MINERALOGY_KEY : null;
        if (hovered != null) {
            renderTooltip(poseStack, font.split(new TranslationTextComponent(hovered + ".tooltip"), 280),
                    mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private ITextComponent levelLabel() {
        return new StringTextComponent(I18n.get(LEVEL_KEY) + ": " + smoothingLevel);
    }

    private static ITextComponent toggleLabel(String key, boolean enabled) {
        return new StringTextComponent(I18n.get(key) + ": " + I18n.get(enabled ? "options.on" : "options.off"));
    }
}
