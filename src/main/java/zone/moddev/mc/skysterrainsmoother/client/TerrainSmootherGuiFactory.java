package zone.moddev.mc.skysterrainsmoother.client;

import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModContainer;

/** Connects the Mods list's Config button to the restart-only settings screen. */
public final class TerrainSmootherGuiFactory {
    private TerrainSmootherGuiFactory() {
    }

    public static void register(ModContainer container) {
        IConfigScreenFactory factory = (minecraft, parent) -> new TerrainSmootherConfigScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }
}
