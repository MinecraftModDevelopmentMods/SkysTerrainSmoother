package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.model.geom.builders.UVPair;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks the texture rows on actual baked faces, not just the source JSON. */
final class FineGrassModelProbe {
    static void run(Minecraft minecraft) {
        int checked = 0;
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.CORNER }) {
            for (int orientation = 4; orientation < 8; orientation++) {
                for (boolean snowy : new boolean[] { false, true }) {
                    BlockState state = Pieces.state("minecraft:grass", shape, orientation)
                            .setValue(GrassBlock.SNOWY, snowy);
                    BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
                    int sides = 0;
                    java.util.Set<Direction> sideDirections = java.util.EnumSet.noneOf(Direction.class);
                    String sideTexture = snowy ? "minecraft:block/grass_block_snow" : "minecraft:block/grass_block_side";
                    for (BakedQuad quad : allFaces(model)) {
                        if (quad.direction().getAxis() == net.minecraft.core.Direction.Axis.Y
                                || !quad.materialInfo().sprite().contents().name().toString().equals(sideTexture)) {
                            continue;
                        }
                        double firstRow = Double.POSITIVE_INFINITY;
                        double lastRow = Double.NEGATIVE_INFINITY;

                        for (int vertex = 0; vertex < 4; vertex++) {
                            float atlasV = UVPair.unpackV(quad.packedUV(vertex));
                            double row = 16 * (atlasV - quad.materialInfo().sprite().getV0())
                                    / (quad.materialInfo().sprite().getV1() - quad.materialInfo().sprite().getV0());
                            firstRow = Math.min(firstRow, row);
                            lastRow = Math.max(lastRow, row);
                        }
                        RuntimeProbe.require(Math.abs(firstRow) < .1 && Math.abs(lastRow - 8) < .1,
                                "baked grass/snow fringe starts at the piece's top: " + state);
                        RuntimeProbe.require(!quad.materialInfo().isTinted(), "The base grass and snow sides are not tinted");
                        sideDirections.add(quad.direction());
                        sides++;
                    }
                    int expectedFaces = shape == Shape.HORIZONTAL_STEP ? 6 : 4;
                    RuntimeProbe.require(sides == expectedFaces && sideDirections.size() == 4,
                            "Grass piece has every exposed side of its quadrant model: " + state
                                    + " faces=" + sides + " directions=" + sideDirections);
                    checked++;
                }
            }
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_FINE_GRASS_MODELS_PASS states=" + checked);
    }

    private static List<BakedQuad> allFaces(BlockStateModel model) {
        List<BakedQuad> faces = new ArrayList<>();
        var parts = new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(RandomSource.create(0), parts);
        for (var part : parts) {
            faces.addAll(part.getQuads(null));
            for (Direction direction : Direction.values()) {
                faces.addAll(part.getQuads(direction));
            }
        }
        return faces;
    }

    private FineGrassModelProbe() {
    }
}
