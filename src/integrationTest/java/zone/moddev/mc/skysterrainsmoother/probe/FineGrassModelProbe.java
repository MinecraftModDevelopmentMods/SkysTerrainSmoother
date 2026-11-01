package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Direction;
import net.minecraft.block.GrassBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
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
                            .with(GrassBlock.SNOWY, snowy);
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);
                    int sides = 0;
                    java.util.Set<Direction> sideDirections = java.util.EnumSet.noneOf(Direction.class);
                    String sideTexture = snowy ? "minecraft:block/grass_block_snow" : "minecraft:block/grass_block_side";
                    for (BakedQuad quad : allFaces(model, state)) {
                        if (quad.getFace().getAxis() == net.minecraft.util.Direction.Axis.Y
                                || !quad.func_187508_a().getName().toString().equals(sideTexture)) {
                            continue;
                        }
                        int stride = net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getIntegerSize();
                        int textureOffset = primaryTextureOffset();
                        double firstRow = Double.POSITIVE_INFINITY;
                        double lastRow = Double.NEGATIVE_INFINITY;

                        for (int vertex = 0; vertex < 4; vertex++) {
                            float atlasV = Float.intBitsToFloat(quad.getVertexData()[vertex * stride + textureOffset + 1]);
                            double row = 16 * (atlasV - quad.func_187508_a().getMinV())
                                    / (quad.func_187508_a().getMaxV() - quad.func_187508_a().getMinV());
                            firstRow = Math.min(firstRow, row);
                            lastRow = Math.max(lastRow, row);
                        }
                        RuntimeProbe.require(Math.abs(firstRow) < .1 && Math.abs(lastRow - 8) < .1,
                                "baked grass/snow fringe starts at the piece's top: " + state);
                        RuntimeProbe.require(!quad.hasTintIndex(), "The base grass and snow sides are not tinted");
                        sideDirections.add(quad.getFace());
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
        System.out.println("TERRAIN_FINE_GRASS_MODELS_PASS states=" + checked);
    }

    private static int primaryTextureOffset() {
        net.minecraft.client.renderer.vertex.VertexFormat format =
                net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK;

        for (int index = 0; index < format.getElements().size(); index++) {
            net.minecraft.client.renderer.vertex.VertexFormatElement element = format.getElements().get(index);

            if (element.getUsage() == net.minecraft.client.renderer.vertex.VertexFormatElement.Usage.UV
                    && element.getType() == net.minecraft.client.renderer.vertex.VertexFormatElement.Type.FLOAT
                    && element.getIndex() == 0) {
                return format.getOffset(index) / 4;
            }
        }
        throw new AssertionError("The native block format has no primary texture coordinates");
    }

    private static List<BakedQuad> allFaces(IBakedModel model, BlockState state) {
        // Boundary faces live in direction-specific lists; inset faces are unculled.
        List<BakedQuad> faces = new ArrayList<>(model.getQuads(state, null, new Random(0)));
        for (Direction direction : Direction.values()) {
            faces.addAll(model.getQuads(state, direction, new Random(0)));
        }
        return faces;
    }

    private FineGrassModelProbe() {
    }
}
