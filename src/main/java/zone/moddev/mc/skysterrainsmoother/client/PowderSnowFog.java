package zone.moddev.mc.skysterrainsmoother.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ViewportEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;

/** Vanilla recognises only its full powder-snow block when choosing camera fog. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, value = Dist.CLIENT)
public final class PowderSnowFog {
    private static boolean inside(Camera camera) {
        var world = Minecraft.getInstance().level;
        if (world == null) {
            return false;
        }
        var position = camera.blockPosition();
        var state = world.getBlockState(position);
        return state.getBlock() instanceof PowderSnowPieceBlock
                && state.getShape(world, position).bounds().contains(
                        camera.position().subtract(Vec3.atLowerCornerOf(position)));
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor event) {
        if (inside(event.getCamera())) {
            event.setRed(.623F);
            event.setGreen(.734F);
            event.setBlue(.785F);
        }
    }

    @SubscribeEvent
    public static boolean distance(ViewportEvent.RenderFog event) {
        if (inside(event.getCamera())) {
            var entity = event.getCamera().entity();
            boolean spectator = entity != null && entity.isSpectator();
            event.setNearPlaneDistance(spectator ? -8 : 0);
            event.setFarPlaneDistance(spectator ? event.getFarPlaneDistance() * .5F : 2);
            return true;
        }
        return false;
    }

    private PowderSnowFog() {
    }
}
