package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
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
                    BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
                    int sides = 0;
                    java.util.Set<Direction> sideDirections = java.util.EnumSet.noneOf(Direction.class);
                    String sideTexture = snowy ? "minecraft:block/grass_block_snow" : "minecraft:block/grass_block_side";
                    for (BakedQuad quad : allFaces(model, state)) {
                        if (quad.getDirection().getAxis() == net.minecraft.core.Direction.Axis.Y
                                || !quad.getSprite().contents().name().toString().equals(sideTexture)) {
                            continue;
                        }
                        int stride = com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK.getIntegerSize();
                        int textureOffset = primaryTextureOffset();
                        double firstRow = Double.POSITIVE_INFINITY;
                        double lastRow = Double.NEGATIVE_INFINITY;

                        for (int vertex = 0; vertex < 4; vertex++) {
                            float atlasV = Float.intBitsToFloat(quad.getVertices()[vertex * stride + textureOffset + 1]);
                            double row = 16 * (atlasV - quad.getSprite().getV0())
                                    / (quad.getSprite().getV1() - quad.getSprite().getV0());
                            firstRow = Math.min(firstRow, row);
                            lastRow = Math.max(lastRow, row);
                        }
                        RuntimeProbe.require(Math.abs(firstRow) < .1 && Math.abs(lastRow - 8) < .1,
                                "baked grass/snow fringe starts at the piece's top: " + state);
                        RuntimeProbe.require(!quad.isTinted(), "The base grass and snow sides are not tinted");
                        sideDirections.add(quad.getDirection());
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

    private static int primaryTextureOffset() {
        com.mojang.blaze3d.vertex.VertexFormat format =
                com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK;

        for (int index = 0; index < format.getElements().size(); index++) {
            com.mojang.blaze3d.vertex.VertexFormatElement element = format.getElements().get(index);

            if (element.getUsage() == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.UV
                    && element.getType() == com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT
                    && element.getIndex() == 0) {
                return format.getOffset(index) / 4;
            }
        }
        throw new AssertionError("The native block format has no primary texture coordinates");
    }

    private static List<BakedQuad> allFaces(BakedModel model, BlockState state) {
        // Boundary faces live in direction-specific lists; inset faces are unculled.
        List<BakedQuad> faces = new ArrayList<>(model.getQuads(state, null, RandomSource.create(0)));
        for (Direction direction : Direction.values()) {
            faces.addAll(model.getQuads(state, direction, RandomSource.create(0)));
        }
        return faces;
    }

    private FineGrassModelProbe() {
    }
}
