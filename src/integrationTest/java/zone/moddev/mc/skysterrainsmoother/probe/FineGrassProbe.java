package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.Random;
import net.minecraft.block.GrassBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysgrassslabs.block.GrassSpread;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Real generation and ticking must leave the visible part of a grass support green. */
final class FineGrassProbe {
    static int run(ServerWorld world) {
        BlockPos floor = new BlockPos(4200, 210, 4200);
        Chunk owner = world.getChunkAt(floor);
        TerrainMaterial material = MaterialCatalogue.instance().source(Blocks.GRASS_BLOCK.getDefaultState());
        Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            for (int direction = 0; direction < directions.length; direction++) {
                for (boolean snow : new boolean[] { false, true }) {
                    prepare(world, floor);
                    world.setBlockState(floor.offset(directions[direction]).up(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    BlockState expected = level == 1 ? material.slab : material.step(direction);
                    RuntimeProbe.require(world.getBlockState(floor.up()).equals(expected),
                            "grass smoothing chooses the requested shape: expected=" + expected
                                    + " actual=" + world.getBlockState(floor.up())
                                    + " level=" + level + " direction=" + directions[direction]);
                    checks += checkSupport(world, floor, expected, level == 1, snow);
                }
            }
        }

        for (int direction = 0; direction < directions.length; direction++) {
            prepare(world, floor);
            BlockPos higher = floor.offset(directions[direction]).offset(directions[direction].rotateYCCW()).up();
            world.setBlockState(higher, Blocks.GRASS_BLOCK.getDefaultState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 3);
            BlockState corner = material.corner(direction);
            RuntimeProbe.require(world.getBlockState(floor.up()).equals(corner), "diagonal grass uses a corner");
            checks += checkSupport(world, floor, corner, false, true);
        }
        checks += shrinkingSlab(world, material);
        System.out.println("TERRAIN_FINE_GRASS_SUPPORT_PASS checks=" + checks);
        return checks;
    }

    private static int checkSupport(ServerWorld world, BlockPos floor, BlockState piece,
            boolean fullCover, boolean snow) {
        RuntimeProbe.require(world.getBlockState(floor).getBlock() == (fullCover ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                "only a full footprint may dirtify the supporting grass");
        ProbeWorld.light(world, LightType.SKY, floor.up(), 15);
        if (snow) {
            world.setBlockState(floor.up().east(), Blocks.SNOW.getDefaultState(), 2);
            RuntimeProbe.require(piece.getBlock().updatePostPlacement(piece, Direction.EAST,
                    world.getBlockState(floor.up().east()), world, floor.up(), floor.up().east()).get(GrassBlock.SNOWY),
                    "snow keeps the same grass-support rule");
        }
        GrassSlabsApi.repairSupport(world, floor.up());
        piece.getBlock().tick(piece, world, floor.up(), new Random(1));
        RuntimeProbe.require(world.getBlockState(floor).getBlock() == (fullCover ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                "neighbour and random-tick repair preserve exposed grass");
        world.setBlockState(floor, Blocks.DIRT.getDefaultState(), 2);
        RuntimeProbe.require(GrassSpread.growTarget(world, floor) == !fullCover,
                "exposed dirt can regrow, but dirt covered by a slab cannot");
        return snow ? 5 : 4;
    }

    private static void prepare(ServerWorld world, BlockPos floor) {
        for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                BlockPos column = floor.add(offsetX, 0, offsetZ);
                for (int height = 1; height <= 4; height++) {
                    ProbeWorld.clear(world, column.up(height));
                }
                world.setBlockState(column.down(), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(column, Blocks.GRASS_BLOCK.getDefaultState(), 2);
            }
        }
    }

    private static int shrinkingSlab(ServerWorld world, TerrainMaterial material) {
        BlockPos floor = new BlockPos(4232, 210, 4232);
        Chunk owner = world.getChunkAt(floor);
        int previousLevel = SkysTerrainSmoother.level;
        int checks = 0;
        try {
            SkysTerrainSmoother.level = 2;
            for (BlockState decoratedSupport : new BlockState[] { null,
                    Blocks.SAND.getDefaultState(), Blocks.DIAMOND_ORE.getDefaultState() }) {
                prepare(world, floor);
                world.setBlockState(floor.north().up(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
                world.setBlockState(floor.east().up(), Blocks.GRASS_BLOCK.getDefaultState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 2);
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.slab)
                        && world.getBlockState(floor).getBlock() == Blocks.DIRT, "initial full-cover slab hides dirt");
                ProbeWorld.clear(world, floor.south());
                if (decoratedSupport != null) {
                    world.setBlockState(floor, decoratedSupport, 2);
                }
                ProbeWorld.repair(world, new BlockPos(floor.getX() & ~15, 0, floor.getZ() & ~15));
                if (decoratedSupport == null) {
                    RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.step(0))
                            && world.getBlockState(floor).getBlock() == Blocks.GRASS_BLOCK,
                            "shrinking our slab restores its original exposed grass");
                } else {
                    RuntimeProbe.require(world.getBlockState(floor).equals(decoratedSupport),
                            "support repair never restores grass over a decorator's sand or ore");
                }
                checks += 2;
            }
        } finally {
            SkysTerrainSmoother.level = previousLevel;
        }
        return checks;
    }

    private FineGrassProbe() {
    }
}
