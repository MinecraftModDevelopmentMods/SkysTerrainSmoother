package zone.moddev.mc.skysterrainsmoother.client;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;

/** Vanilla recognises only its full powder-snow block when choosing camera fog. */
@EventBusSubscriber(modid = SkysTerrainSmoother.ID, value = Dist.CLIENT)
public final class PowderSnowFog {
    private static boolean inside(Camera camera) {
        var world = Minecraft.getInstance().level;
        if (world == null) {
            return false;
        }
        var position = camera.getBlockPosition();
        var state = world.getBlockState(position);
        return state.getBlock() instanceof PowderSnowPieceBlock
                && state.getShape(world, position).bounds().contains(
                        camera.getPosition().subtract(Vec3.atLowerCornerOf(position)));
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
    public static void distance(ViewportEvent.RenderFog event) {
        if (inside(event.getCamera())) {
            var entity = event.getCamera().getEntity();
            boolean spectator = entity != null && entity.isSpectator();
            event.setNearPlaneDistance(spectator ? -8 : 0);
            event.setFarPlaneDistance(spectator ? event.getFarPlaneDistance() * .5F : 2);
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }
    }

    private PowderSnowFog() {
    }
}
