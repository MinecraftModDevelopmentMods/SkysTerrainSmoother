package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;

/** Checks the texture rows on actual baked faces, not just the source JSON. */
final class FineGrassModelProbe {
    static void run(Minecraft minecraft) {
        int checked = 0;
        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.CORNER }) {
            for (int orientation = 4; orientation < 8; orientation++) {
                for (boolean snowy : new boolean[] { false, true }) {
                    IBlockState state = Pieces.state("minecraft:grass", shape, orientation)
                            .withProperty(BlockGrass.SNOWY, snowy);
                    IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);
                    int sides = 0;
                    for (BakedQuad quad : model.getQuads(state, null, 0)) {
                        if (quad.getFace().getAxis() == net.minecraft.util.EnumFacing.Axis.Y
                                || !quad.getSprite().getIconName().contains("grass_side")) {
                            continue;
                        }
                        int stride = quad.getFormat().getIntegerSize();
                        int textureOffset = quad.getFormat().getUvOffsetById(0) / 4;
                        double firstRow = Double.POSITIVE_INFINITY;
                        double lastRow = Double.NEGATIVE_INFINITY;

                        for (int vertex = 0; vertex < 4; vertex++) {
                            float atlasV = Float.intBitsToFloat(quad.getVertexData()[vertex * stride + textureOffset + 1]);
                            double row = 16 * (atlasV - quad.getSprite().getMinV())
                                    / (quad.getSprite().getMaxV() - quad.getSprite().getMinV());
                            firstRow = Math.min(firstRow, row);
                            lastRow = Math.max(lastRow, row);
                        }
                        RuntimeProbe.require(Math.abs(firstRow) < .1 && Math.abs(lastRow - 8) < .1,
                                "baked grass/snow fringe starts at the piece's top: " + state);
                        RuntimeProbe.require(!snowy || !quad.hasTintIndex(), "snow edges are not biome tinted");
                        sides++;
                    }
                    RuntimeProbe.require(sides > 0, "grass piece has real side faces");
                    checked++;
                }
            }
        }
        System.out.println("TERRAIN_FINE_GRASS_MODELS_PASS states=" + checked);
    }

    private FineGrassModelProbe() {
    }
}
