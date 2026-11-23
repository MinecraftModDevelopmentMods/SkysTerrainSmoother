package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Uses only the original public types so the same fixture can test the previous jar. */
final class GentleTerraceProbe {
    static int run(ServerLevel world) {
        BlockPos floor = new BlockPos(9080, 200, 9080);
        var owner = world.getChunkAt(floor);
        var stone = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());
        int checks = 0;
        for (Direction uphill : Direction.Plane.HORIZONTAL) {
            for (int level = 2; level <= 3; level++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    for (int offsetX = -3; offsetX <= 3; offsetX++) {
                        int rise = offsetX * uphill.getStepX() + offsetZ * uphill.getStepZ();
                        BlockPos column = floor.offset(offsetX, 0, offsetZ);
                        for (int height = -1; height <= 5; height++) {
                            world.setBlock(column.above(height), height <= (rise > 0 ? 1 : 0)
                                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                ProbeWorld.rebuildHeights(owner);
                SkysTerrainSmoother.engine.smooth(world, owner, level);
                RuntimeProbe.require(world.getBlockState(floor.above()).equals(stone.slab),
                        "A broad gentle terrace uses a slab, level=" + level + " uphill=" + uphill);
                checks++;
            }
        }
        SkysTerrainSmoother.logger.info("TERRAIN_GENTLE_TERRACE_CONTROL_PASS cases={}", checks);
        return checks;
    }

    private GentleTerraceProbe() {}
}
