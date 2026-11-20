package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Fixture events wait for their bounded work instead of assuming one tick handles every owner. */
final class ProbeWorld {
    static void post(WorldServer world, Event event) {
        MinecraftForge.EVENT_BUS.post(event);
        if (event instanceof DecorateBiomeEvent.Pre) return;
        int attempts = 0;
        while (SkysTerrainSmoother.engine.queuedRepairs() > 0 && attempts++ < 4096) {
            MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        }
        RuntimeProbe.require(SkysTerrainSmoother.engine.queuedRepairs() == 0, "queued fixture owners finish");
    }

    private ProbeWorld() {
    }
}
