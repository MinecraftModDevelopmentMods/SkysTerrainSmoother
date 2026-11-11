package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Mountain ledges must not gain another full-height step between two half-height caps. */
final class TransitionProbe {
    static int run(ServerLevel world) {
        BlockPos origin = new BlockPos(3592, 210, 3592);
        LevelChunk owner = world.getChunkAt(origin);
        int checks = 0;

        for (BlockState source : new BlockState[] { Blocks.STONE.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.SNOW_BLOCK.defaultBlockState() }) {
            if (source.getBlock() == Blocks.GRASS_BLOCK && !GrassSlabsApi.grassGenerationAllowed()) {
                continue;
            }

            TerrainMaterial material = MaterialCatalogue.instance().source(source);

            for (int level = 1; level <= 3; level++) {
                for (Direction uphill : Direction.Plane.HORIZONTAL) {
                    for (boolean roofed : new boolean[] { false, true }) {
                        prepare(world, origin, uphill, source, false, roofed);
                        int loadedBefore = world.getChunkSource().getLoadedChunksCount();
                        SkysTerrainSmoother.engine.smooth(world, owner, level);

                        BlockPos lowCap = origin.above();
                        BlockPos upperCap = origin.relative(uphill).above(2);
                        RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(lowCap)) == material,
                                "the foot of a steep slope still receives its matching transition");
                        BlockState step = material.step(direction(uphill));
                        if (level >= 2 && step != null) {
                            RuntimeProbe.require(world.getBlockState(lowCap).equals(step),
                                    "the foot of consecutive rises keeps its uphill step");
                            checks++;
                        }
                        requireOpenDownhillEdge(world, upperCap, uphill.getOpposite(),
                                "consecutive mountain ledges, level " + level + " / " + uphill);
                        RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loadedBefore,
                                "slope checks never load neighbouring chunks");
                        RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                                "a repeated pass does not add the rejected upper cap");
                        checks += 4;

                        prepare(world, origin, uphill, source, true, roofed);
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        BlockPos terraceCap = origin.relative(uphill).above();
                        BlockState expected = roofed && level >= 2 && step != null ? step : material.slab;
                        RuntimeProbe.require(world.getBlockState(terraceCap).equals(expected),
                                "gentle surface terraces use a slab and cave terraces keep their existing shape: "
                                        + source + " / " + uphill + " / level " + level + " / roofed " + roofed);
                        RuntimeProbe.require(MaterialCatalogue.instance()
                                .piece(world.getBlockState(origin.relative(uphill, 3).above(2))) == material,
                                "a wider upper terrace retains room for its own transition");
                        checks += 2;
                    }
                }
            }
        }

        // Existing caps, including those retained over an ore vein, must give the same answer as a fresh plan.
        TerrainMaterial stone = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());
        for (Direction uphill : Direction.Plane.HORIZONTAL) {
            prepare(world, origin, uphill, Blocks.STONE.defaultBlockState(), false, false);
            world.setBlock(origin, Blocks.COAL_ORE.defaultBlockState(), 2);
            world.setBlock(origin.above(), stone.slab, 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            requireOpenDownhillEdge(world, origin.relative(uphill).above(2), uphill.getOpposite(),
                    "a pre-existing lower slab must not receive a parallel upper slab");
            RuntimeProbe.require(world.getBlockState(origin).getBlock() == Blocks.COAL_ORE
                    && world.getBlockState(origin.above()).equals(stone.slab),
                    "lower slabs and their ore supports are not removed");
            checks += 2;
        }

        checks += borders(world) + gentleBorders(world) + lateLowerFloor(world, origin);
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_TRANSITIONS_PASS checks=" + checks);
        return checks;
    }

    private static int borders(ServerLevel world) {
        int checks = 0;
        for (Direction uphill : Direction.Plane.HORIZONTAL) {
            BlockPos low = uphill == Direction.EAST ? new BlockPos(3615, 210, 3624)
                    : uphill == Direction.WEST ? new BlockPos(3616, 210, 3624)
                    : uphill == Direction.SOUTH ? new BlockPos(3624, 210, 3615)
                    : new BlockPos(3624, 210, 3616);
            LevelChunk lowerOwner = world.getChunkAt(low);
            LevelChunk upperOwner = world.getChunkAt(low.relative(uphill));

            for (int level = 1; level <= 3; level++) {
                BlockState[] forward = null;
                for (boolean reverse : new boolean[] { false, true }) {
                    prepare(world, low, uphill, Blocks.STONE.defaultBlockState(), false, false);
                    int loadedBefore = world.getChunkSource().getLoadedChunksCount();
                    SkysTerrainSmoother.engine.smooth(world, reverse ? upperOwner : lowerOwner, level);
                    SkysTerrainSmoother.engine.smooth(world, reverse ? lowerOwner : upperOwner, level);
                    requireOpenDownhillEdge(world, low.relative(uphill).above(2), uphill.getOpposite(),
                            "a chunk boundary cannot recreate the upper lip, " + uphill + " / level " + level);
                    RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loadedBefore,
                            "downhill boundary checks never generate chunks");
                    BlockState[] result = { world.getBlockState(low.above()),
                            world.getBlockState(low.relative(uphill).above(2)) };
                    if (forward == null) {
                        forward = result;
                    } else {
                        RuntimeProbe.require(java.util.Arrays.equals(forward, result),
                                "mountain transitions match in both chunk orders");
                        checks++;
                    }
                    checks += 2;
                }
            }
        }
        return checks;
    }

    private static int gentleBorders(ServerLevel world) {
        TerrainMaterial material = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());
        int checks = 0;
        for (Direction uphill : new Direction[] { Direction.EAST, Direction.SOUTH }) {
            BlockPos origin = uphill == Direction.EAST ? new BlockPos(3614, 210, 3656)
                    : new BlockPos(3656, 210, 3614);
            BlockPos low = origin.relative(uphill);
            LevelChunk lowerOwner = world.getChunkAt(low);
            LevelChunk upperOwner = world.getChunkAt(low.relative(uphill));
            for (boolean reverse : new boolean[] { false, true }) {
                prepare(world, origin, uphill, Blocks.STONE.defaultBlockState(), true, false);
                int loadedBefore = world.getChunkSource().getLoadedChunksCount();
                SkysTerrainSmoother.engine.smooth(world, reverse ? upperOwner : lowerOwner, 3);
                SkysTerrainSmoother.engine.smooth(world, reverse ? lowerOwner : upperOwner, 3);
                RuntimeProbe.require(world.getBlockState(low.above()).equals(material.slab),
                        "the same gentle slab fits in both chunk orders: " + uphill);
                RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loadedBefore,
                        "terrace lookahead never loads a chunk");
                checks += 2;
            }
        }
        return checks;
    }

    private static int direction(Direction uphill) {
        return switch (uphill) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Expected horizontal uphill direction");
        };
    }

    private static int lateLowerFloor(ServerLevel world, BlockPos origin) {
        LevelChunk owner = world.getChunkAt(origin);
        int checks = 0;
        for (Direction uphill : Direction.Plane.HORIZONTAL) {
            prepare(world, origin, uphill, Blocks.STONE.defaultBlockState(), false, false);
            for (int distance = -2; distance <= 0; distance++) {
                for (int width = -2; width <= 2; width++) {
                    world.setBlock(origin.relative(uphill, distance).relative(uphill.getClockWise(), width).above(),
                            Blocks.STONE.defaultBlockState(), 2);
                }
            }
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            BlockPos upperCap = origin.relative(uphill).above(2);
            RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(upperCap)) != null,
                    "late-terrace regression begins with a valid upper cap");
            ProbeWorld.clear(world, origin.above());
            ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
            requireOpenDownhillEdge(world, upperCap, uphill.getOpposite(),
                    "late decoration cannot leave an upper cap that raises a newly exposed downhill edge");
            RuntimeProbe.require(SkysTerrainSmoother.level != 1 || world.isEmptyBlock(upperCap),
                    "slab-only mode leaves the upper lip uncapped");
            checks += 3;
        }
        return checks;
    }

    private static void requireOpenDownhillEdge(ServerLevel world, BlockPos target, Direction downhill,
            String message) {
        BlockState state = world.getBlockState(target);
        if (state.getBlock() == Blocks.AIR) {
            return;
        }

        AABB bounds = state.getShape(world, target).bounds();
        boolean touches = downhill == Direction.NORTH ? bounds.minZ == 0
                : downhill == Direction.SOUTH ? bounds.maxZ == 1
                : downhill == Direction.WEST ? bounds.minX == 0 : bounds.maxX == 1;
        RuntimeProbe.require(!touches, message);
    }

    private static void prepare(ServerLevel world, BlockPos origin, Direction uphill, BlockState source,
            boolean wide, boolean roofed) {
        Direction across = uphill.getClockWise();
        for (int distance = -3; distance <= 5; distance++) {
            int rise = Math.max(0, Math.min(2, wide ? distance / 2 : distance));
            for (int width = -3; width <= 3; width++) {
                BlockPos column = origin.relative(uphill, distance).relative(across, width);
                for (int height = 9; height >= -2; height--) {
                    world.setBlock(column.above(height), height <= rise ? source : Blocks.AIR.defaultBlockState(), 2);
                }
                if (roofed) {
                    world.setBlock(column.above(8), Blocks.BRICKS.defaultBlockState(), 2);
                }
            }
        }
    }

    private TransitionProbe() {
    }
}

