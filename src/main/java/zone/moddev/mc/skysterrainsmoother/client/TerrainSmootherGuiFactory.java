package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

/** Connects the Mods list's Config button to the restart-only settings screen. */
public final class TerrainSmootherGuiFactory {
    private TerrainSmootherGuiFactory() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parent) -> new TerrainSmootherConfigScreen(parent));
    }
}
