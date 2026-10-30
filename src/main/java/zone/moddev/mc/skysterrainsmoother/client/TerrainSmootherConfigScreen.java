package zone.moddev.mc.skysterrainsmoother.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;

/** These generation and migration choices take effect after restarting Minecraft. */
public final class TerrainSmootherConfigScreen extends GuiConfig {
    public TerrainSmootherConfigScreen(GuiScreen parent) {
        super(parent, elements(), "skysterrainsmoother", false, true, "Sky's Terrain Smoother");
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> elements = new ArrayList<>();
        elements.addAll(new ConfigElement(TerrainSmootherConfig.configuration().getCategory("worldgen")).getChildElements());
        elements.addAll(new ConfigElement(TerrainSmootherConfig.configuration().getCategory("compat")).getChildElements());
        return elements;
    }
}
