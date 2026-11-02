package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.Random;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysgrassslabs.block.GrassSpread;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Real generation and ticking must leave the visible part of a grass support green. */
final class FineGrassProbe {
    static int run(ServerLevel world) {
        BlockPos floor = new BlockPos(4200, 210, 4200);
        LevelChunk owner = world.getChunkAt(floor);
        TerrainMaterial material = MaterialCatalogue.instance().source(Blocks.GRASS_BLOCK.defaultBlockState());
        Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            for (int direction = 0; direction < directions.length; direction++) {
                for (boolean snow : new boolean[] { false, true }) {
                    prepare(world, floor);
                    world.setBlock(floor.relative(directions[direction]).above(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    BlockState expected = level == 1 ? material.slab : material.step(direction);
                    RuntimeProbe.require(world.getBlockState(floor.above()).equals(expected),
                            "grass smoothing chooses the requested shape: expected=" + expected
                                    + " actual=" + world.getBlockState(floor.above())
                                    + " level=" + level + " direction=" + directions[direction]);
                    checks += checkSupport(world, floor, expected, level == 1, snow);
                }
            }
        }

        for (int direction = 0; direction < directions.length; direction++) {
            prepare(world, floor);
            BlockPos higher = floor.relative(directions[direction]).relative(directions[direction].getCounterClockWise()).above();
            world.setBlock(higher, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 3);
            BlockState corner = material.corner(direction);
            RuntimeProbe.require(world.getBlockState(floor.above()).equals(corner), "diagonal grass uses a corner");
            checks += checkSupport(world, floor, corner, false, true);
        }
        checks += shrinkingSlab(world, material);
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_FINE_GRASS_SUPPORT_PASS checks=" + checks);
        return checks;
    }

    private static int checkSupport(ServerLevel world, BlockPos floor, BlockState piece,
            boolean fullCover, boolean snow) {
        RuntimeProbe.require(world.getBlockState(floor).getBlock() == (fullCover ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                "only a full footprint may dirtify the supporting grass");
        ProbeWorld.light(world, LightLayer.SKY, floor.above(), 15);
        if (snow) {
            world.setBlock(floor.above().east(), Blocks.SNOW.defaultBlockState(), 2);
            RuntimeProbe.require(piece.getBlock().updateShape(piece, Direction.EAST,
                    world.getBlockState(floor.above().east()), world, floor.above(), floor.above().east()).getValue(GrassBlock.SNOWY),
                    "snow keeps the same grass-support rule");
        }
        GrassSlabsApi.repairSupport(world, floor.above());
        piece.getBlock().tick(piece, world, floor.above(), new Random(1));
        RuntimeProbe.require(world.getBlockState(floor).getBlock() == (fullCover ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                "neighbour and random-tick repair preserve exposed grass");
        world.setBlock(floor, Blocks.DIRT.defaultBlockState(), 2);
        RuntimeProbe.require(GrassSpread.growTarget(world, floor) == !fullCover,
                "exposed dirt can regrow, but dirt covered by a slab cannot");
        return snow ? 5 : 4;
    }

    private static void prepare(ServerLevel world, BlockPos floor) {
        for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                BlockPos column = floor.offset(offsetX, 0, offsetZ);
                for (int height = 1; height <= 4; height++) {
                    ProbeWorld.clear(world, column.above(height));
                }
                world.setBlock(column.below(), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(column, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    private static int shrinkingSlab(ServerLevel world, TerrainMaterial material) {
        BlockPos floor = new BlockPos(4232, 210, 4232);
        LevelChunk owner = world.getChunkAt(floor);
        int previousLevel = SkysTerrainSmoother.level;
        int checks = 0;
        try {
            SkysTerrainSmoother.level = 2;
            for (BlockState decoratedSupport : new BlockState[] { null,
                    Blocks.SAND.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState() }) {
                prepare(world, floor);
                world.setBlock(floor.north().above(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                world.setBlock(floor.east().above(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 2);
                RuntimeProbe.require(world.getBlockState(floor.above()).equals(material.slab)
                        && world.getBlockState(floor).getBlock() == Blocks.DIRT, "initial full-cover slab hides dirt");
                ProbeWorld.clear(world, floor.south());
                if (decoratedSupport != null) {
                    world.setBlock(floor, decoratedSupport, 2);
                }
                ProbeWorld.repair(world, new BlockPos(floor.getX() & ~15, 0, floor.getZ() & ~15));
                if (decoratedSupport == null) {
                    RuntimeProbe.require(world.getBlockState(floor.above()).equals(material.step(0))
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
