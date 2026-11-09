package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Connects the Mods list's Config button to the restart-only settings screen. */
public final class TerrainSmootherGuiFactory {
    private TerrainSmootherGuiFactory() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new TerrainSmootherConfigScreen(parent)));
    }
}
