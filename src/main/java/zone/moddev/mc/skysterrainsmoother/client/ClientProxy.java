package zone.moddev.mc.skysterrainsmoother.client;

import net.neoforged.fml.ModContainer;

public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register(ModContainer container) {
        TerrainSmootherGuiFactory.register(container);
    }
}
