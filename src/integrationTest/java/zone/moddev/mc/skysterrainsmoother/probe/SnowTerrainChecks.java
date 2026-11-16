package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Snow and packed ice must use their own pieces at every smoothing level. */
final class SnowTerrainChecks {
    static int run(WorldServer world) {
        BlockPos origin = new BlockPos(3880, 220, 3880);
        int checks = 0;
        for (IBlockState source : new IBlockState[] {
                Blocks.SNOW.getDefaultState(), Blocks.PACKED_ICE.getDefaultState() }) {
            TerrainMaterial material = MaterialCatalogue.instance().source(source);
            RuntimeProbe.require(material != null, "frozen terrain has an explicit mapping");
            for (int level = 1; level <= 3; level++) {
                for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
                    for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                        for (int offsetX = -3; offsetX <= 3; offsetX++) {
                            BlockPos ground = origin.add(offsetX, 0, offsetZ);
                            world.setBlockState(ground, source, 2);
                            for (int height = 1; height <= 4; height++) {
                                world.setBlockToAir(ground.up(height));
                            }
                        }
                    }
                    world.setBlockState(origin.offset(uphill).up(), source, 2);
                    int loaded = world.getChunkProvider().getLoadedChunkCount();
                    SkysTerrainSmoother.engine.smooth(world, world.getChunkFromBlockCoords(origin), level);
                    RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(origin.up())) == material,
                            "frozen transition keeps its material at level " + level + "/" + uphill);
                    RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount() == loaded,
                            "frozen terrain checks do not load a halo chunk");
                    checks += 2;
                }
            }
        }
        System.out.println("TERRAIN_SMOOTHER_SNOW_TERRAIN_PASS checks=" + checks);
        return checks;
    }

    private SnowTerrainChecks() {
    }
}
