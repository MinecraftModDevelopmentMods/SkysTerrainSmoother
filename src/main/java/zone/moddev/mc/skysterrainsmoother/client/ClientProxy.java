package zone.moddev.mc.skysterrainsmoother.client;

public final class ClientProxy {
    private ClientProxy() {
    }

    public static void register() {
        TerrainSmootherGuiFactory.register();
    }
}
