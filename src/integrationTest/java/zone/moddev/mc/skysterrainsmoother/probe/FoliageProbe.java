package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.DoublePlantBlock;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Adjacent decorators can put plants in a fresh owner before its own Pre event. */
final class FoliageProbe {
    static int run(ServerWorld world) {
        BlockPos floor = new BlockPos(3528, 210, 3528);
        Chunk owner = world.getChunkAt(floor);
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            for (BlockState plant : new BlockState[] { Blocks.GRASS.getDefaultState(),
                    Blocks.DANDELION.getDefaultState(), Blocks.POPPY.getDefaultState(),
                    Blocks.DEAD_BUSH.getDefaultState() }) {
                prepare(world, floor);
                world.setBlockState(floor.up(), plant, 2);
                world.setBlockState(floor.east().up(2), Blocks.GRASS.getDefaultState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);
                RuntimeProbe.require(zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue.instance()
                        .piece(world.getBlockState(floor.up())) != null
                        && world.getBlockState(floor).getBlock() == (level == 1 ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                        "foliage must not hide a valid grass transition at level " + level);
                RuntimeProbe.require(world.getBlockState(floor.east().up(2)).getBlock() == Blocks.GRASS,
                        "plants outside the placement remain unchanged");
                checks += 2;
            }
        }

        prepare(world, floor);
        world.setBlockState(floor.up(), Blocks.TALL_GRASS.getDefaultState()
                .with(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
        world.setBlockState(floor.up(2), Blocks.TALL_GRASS.getDefaultState()
                .with(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(world.getBlockState(floor.up()).getBlock() == ModBlocks.GRASS_SLAB
                && world.isAirBlock(floor.up(2)), "both halves of a displaced tall plant are removed together");
        checks++;

        for (BlockState obstacle : new BlockState[] { Blocks.OAK_LEAVES.getDefaultState(),
                Blocks.OAK_LOG.getDefaultState(), Blocks.WHEAT.getDefaultState(), Blocks.OAK_SAPLING.getDefaultState(),
                Blocks.VINE.getDefaultState(), Blocks.CHEST.getDefaultState(), Blocks.WATER.getDefaultState() }) {
            prepare(world, floor);
            world.setBlockState(floor.up(), obstacle, 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.getBlockState(floor.up()).equals(obstacle)
                    && world.getBlockState(floor).getBlock() == Blocks.GRASS_BLOCK,
                    "trees, crops, vines, block entities and fluids are not replaceable foliage");
            checks++;
        }

        System.out.println("TERRAIN_SMOOTHER_FOLIAGE_PASS checks=" + checks);
        return checks;
    }

    private static void prepare(ServerWorld world, BlockPos floor) {
        for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                BlockPos support = floor.add(offsetX, 0, offsetZ);
                for (int height = 1; height <= 6; height++) {
                    world.setBlockState(support.up(height), Blocks.AIR.getDefaultState(), 2);
                }
                world.setBlockState(support, Blocks.GRASS_BLOCK.getDefaultState(), 2);
            }
        }
        world.setBlockState(floor.east().up(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
    }

    private FoliageProbe() {
    }
}
