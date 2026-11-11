package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowContent;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;

/** Exercise the actual baked models and viewport events inside and outside each footprint. */
public final class PowderSnowClientProbe {
    public static void run(Minecraft client) {
        BlockPos position = client.player.blockPosition().above(10);
        var original = client.level.getBlockState(position);
        ProbeCamera camera = new ProbeCamera();
        camera.setup(client.level, client.player, false, false, 0);
        int checks = 0;
        try {
            for (var kind : PowderSnowPieceBlock.Kind.values()) {
                for (Half half : Half.values()) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        var state = PowderSnowContent.state(kind, half, facing);
                        client.level.setBlock(position, state, 2);
                        var model = client.getBlockRenderer().getBlockModel(state);
                        RuntimeProbe.require(model != client.getModelManager().getMissingModel(),
                                "Every powder-snow orientation has a baked model");
                        for (Direction face : Direction.values()) {
                            var quads = model.getQuads(state, face, RandomSource.create(0));
                            var unculled = model.getQuads(state, null, RandomSource.create(0));
                            RuntimeProbe.require(java.util.stream.Stream.concat(quads.stream(), unculled.stream())
                                            .allMatch(quad -> !quad.isTinted() && quad.getSprite().contents().name()
                                                    .toString().equals("minecraft:block/powder_snow")),
                                    "Powder-snow models use native untinted powder snow");
                        }
                        var bounds = state.getShape(client.level, position).bounds();
                        Vec3 inside = new Vec3((bounds.minX + bounds.maxX) / 2,
                                (bounds.minY + bounds.maxY) / 2, (bounds.minZ + bounds.maxZ) / 2);
                        camera.point(Vec3.atLowerCornerOf(position).add(inside));
                        checkFog(camera, true);
                        Vec3 outside = new Vec3(inside.x, half == Half.BOTTOM ? .75 : .25, inside.z);
                        camera.point(Vec3.atLowerCornerOf(position).add(outside));
                        checkFog(camera, false);
                        checks += 3;
                    }
                }
            }
            client.level.setBlock(position, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
            camera.point(Vec3.atCenterOf(position));
            checkFog(camera, false);
        } finally {
            client.level.setBlock(position, original, 2);
        }
        SkysTerrainSmoother.logger.info("TERRAIN_POWDER_SNOW_CLIENT_PASS checks={} models=true fog=true empty_space=true", checks + 1);
    }

    private static void checkFog(Camera camera, boolean inside) {
        var color = new ViewportEvent.ComputeFogColor(camera, 0, 1, 1, 1);
        NeoForge.EVENT_BUS.post(color);
        var fog = new ViewportEvent.RenderFog(FogRenderer.FogMode.FOG_TERRAIN, FogType.NONE,
                camera, 0, 0, 128, FogShape.SPHERE);
        NeoForge.EVENT_BUS.post(fog);
        RuntimeProbe.require(inside ? color.getRed() == .623F && color.getGreen() == .734F
                        && color.getBlue() == .785F && fog.isCanceled() && fog.getFarPlaneDistance() == 2
                        : color.getRed() == 1 && color.getGreen() == 1 && color.getBlue() == 1 && !fog.isCanceled(),
                "Native powder fog applies only inside the occupied piece volume");
    }

    private static final class ProbeCamera extends Camera {
        void point(Vec3 position) {
            setPosition(position);
        }
    }

    private PowderSnowClientProbe() {
    }
}
