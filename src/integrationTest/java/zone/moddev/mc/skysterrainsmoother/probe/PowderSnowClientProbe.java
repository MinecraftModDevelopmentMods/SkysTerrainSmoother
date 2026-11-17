package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowContent;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;

/** Inspect the baked models and real viewport buses inside and outside each powder-snow footprint. */
public final class PowderSnowClientProbe {
    public static void run(Minecraft client) {
        BlockPos position = client.player.blockPosition().above(10);
        var original = client.level.getBlockState(position);
        ProbeCamera camera = new ProbeCamera();
        camera.setLevel(client.level);
        camera.setEntity(client.player);
        int checks = 0;
        try {
            for (var kind : PowderSnowPieceBlock.Kind.values()) {
                for (Half half : Half.values()) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        var state = PowderSnowContent.state(kind, half, facing);
                        client.level.setBlock(position, state, 2);
                        var models = client.getModelManager().getBlockStateModelSet();
                        var model = models.get(state);
                        RuntimeProbe.require(model != models.missingModel(), "Every powder orientation has a model");
                        List<BlockStateModelPart> parts = new ArrayList<>();
                        model.collectParts(RandomSource.create(0), parts);
                        int faces = 0;
                        for (var part : parts) {
                            for (Direction face : new Direction[] {null, Direction.UP, Direction.DOWN,
                                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                                for (var quad : part.getQuads(face)) {
                                    RuntimeProbe.require(!quad.materialInfo().isTinted() && quad.materialInfo().sprite()
                                                    .contents().name().toString().equals("minecraft:block/powder_snow"),
                                            "Powder models use native, untinted powder snow");
                                    faces++;
                                }
                            }
                        }
                        RuntimeProbe.require(faces == 6, "A powder piece keeps all six exposed faces");
                        var bounds = state.getShape(client.level, position).bounds();
                        Vec3 inside = new Vec3((bounds.minX + bounds.maxX) / 2,
                                (bounds.minY + bounds.maxY) / 2, (bounds.minZ + bounds.maxZ) / 2);
                        camera.point(Vec3.atLowerCornerOf(position).add(inside));
                        checkFog(camera, true);
                        camera.point(Vec3.atLowerCornerOf(position).add(inside.x, half == Half.BOTTOM ? .75 : .25, inside.z));
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
        ViewportEvent.ComputeFogColor.BUS.post(color);
        FogData distances = new FogData();
        distances.environmentalEnd = 128;
        var fog = new ViewportEvent.RenderFog(FogType.NONE, camera, 0, distances, new org.joml.Vector4f(1));
        boolean cancelled = ViewportEvent.RenderFog.BUS.post(fog);
        RuntimeProbe.require(inside ? color.getRed() == .623F && color.getGreen() == .734F && color.getBlue() == .785F
                        && cancelled && fog.getFarPlaneDistance() == 2
                        : color.getRed() == 1 && color.getGreen() == 1 && color.getBlue() == 1 && !cancelled,
                "Powder fog applies only inside the occupied piece volume");
    }

    private static final class ProbeCamera extends Camera {
        void point(Vec3 position) {
            setPosition(position);
        }
    }

    private PowderSnowClientProbe() {
    }
}
