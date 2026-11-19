package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.TerrainGeometry;

/** Frozen surfaces need explicit mappings; their similar appearance is not a substitute. */
final class FrozenTerrainProbe {
    static int run(ServerLevel world) {
        RuntimeProbe.require(MaterialCatalogue.instance().source(Blocks.PACKED_ICE.defaultBlockState()) != null,
                "Packed ice has its own smoothing material");
        int checks = 1 + PowderSnowProbe.run(world);
        BlockPos floor = new BlockPos(5076, world.getMaxY() - 10, 5076);
        var owner = world.getChunkAt(floor);
        int loaded = world.getChunkSource().getLoadedChunksCount();
        for (Block source : new Block[] { Blocks.PACKED_ICE, Blocks.POWDER_SNOW }) {
            var material = MaterialCatalogue.instance().source(source.defaultBlockState());
            RuntimeProbe.require(material != null, "Every frozen surface has a distinct material");
            for (int level = 1; level <= 3; level++) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                        for (int offsetX = -3; offsetX <= 3; offsetX++) {
                            BlockPos column = floor.offset(offsetX, 0, offsetZ);
                            world.setBlock(column, source.defaultBlockState(), 2);
                            world.setBlock(column.below(), source.defaultBlockState(), 2);
                            for (int height = 1; height <= 8; height++) {
                                ProbeWorld.clear(world, column.above(height));
                            }
                        }
                    }
                    BlockPos higher = level == 3 ? floor.relative(direction).relative(direction.getClockWise()).above()
                            : floor.relative(direction).above();
                    world.setBlock(higher, source.defaultBlockState(), 2);
                    ProbeWorld.rebuildHeights(owner);
                    var support = world.getBlockState(floor);
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    var piece = world.getBlockState(floor.above());
                    RuntimeProbe.require(MaterialCatalogue.instance().piece(piece) == material,
                            "Frozen terrain receives its own pieces at every level and rotation: " + source + " / " + level);
                    RuntimeProbe.require(world.getBlockState(floor).equals(support),
                            "Frozen terrain smoothing preserves the original full support");
                    RuntimeProbe.require(TerrainGeometry.shape(piece).bounds().maxY == .5,
                            "Every frozen transition stays half a block tall");
                    if (source == Blocks.POWDER_SNOW) {
                        RuntimeProbe.require(piece.getCollisionShape(world, floor.above()).isEmpty(),
                                "Generated powder pieces stay non-solid");
                    }
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    RuntimeProbe.require(world.getBlockState(floor.above()).equals(piece),
                            "Repeating a frozen generation pass is idempotent");
                    checks += 5;
                }
            }
        }
        RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loaded,
                "Frozen terrain reads do not force neighbouring chunks");
        SkysTerrainSmoother.logger.info("TERRAIN_FROZEN_SURFACES_PASS checks={} materials=2 levels=3 rotations=4", checks);
        return checks;
    }
}
