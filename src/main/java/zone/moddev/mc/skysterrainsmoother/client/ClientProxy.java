package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        TerrainSmootherGuiFactory.register(context);
    }
}
