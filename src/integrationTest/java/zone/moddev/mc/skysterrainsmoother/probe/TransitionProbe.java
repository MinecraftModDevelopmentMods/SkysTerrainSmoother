package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Mountain ledges must not gain another full-height step between two half-height caps. */
final class TransitionProbe {
    static int run(WorldServer world) {
        BlockPos origin = new BlockPos(3592, 210, 3592);
        Chunk owner = world.getChunk(origin);
        int checks = 0;

        for (IBlockState source : new IBlockState[] { Blocks.STONE.getDefaultState(),
                Blocks.SANDSTONE.getDefaultState(), Blocks.SAND.getDefaultState(), Blocks.GRASS.getDefaultState() }) {
            if (source.getBlock() == Blocks.GRASS && !GrassSlabsApi.grassGenerationAllowed()) {
                continue;
            }

            TerrainMaterial material = MaterialCatalogue.instance().source(source);

            for (int level = 1; level <= 3; level++) {
                for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
                    for (boolean roofed : new boolean[] { false, true }) {
                        prepare(world, origin, uphill, source, false, roofed);
                        int loadedBefore = world.getChunkProvider().getLoadedChunkCount();
                        SkysTerrainSmoother.engine.smooth(world, owner, level);

                        BlockPos lowCap = origin.up();
                        BlockPos upperCap = origin.offset(uphill).up(2);
                        RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(lowCap)) == material,
                                "the foot of a steep slope still receives its matching transition");
                        requireOpenDownhillEdge(world, upperCap, uphill.getOpposite(),
                                "consecutive mountain ledges, level " + level + " / " + uphill);
                        RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount() == loadedBefore,
                                "slope checks never load neighbouring chunks");
                        RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                                "a repeated pass does not add the rejected upper cap");
                        checks += 4;

                        prepare(world, origin, uphill, source, true, roofed);
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(MaterialCatalogue.instance()
                                .piece(world.getBlockState(origin.offset(uphill).up())) == material,
                                "the bottom of a wider terrace is smoothed");
                        RuntimeProbe.require(MaterialCatalogue.instance()
                                .piece(world.getBlockState(origin.offset(uphill, 3).up(2))) == material,
                                "a wider upper terrace retains room for its own transition");
                        checks += 2;
                    }
                }
            }
        }

        // Existing caps, including those retained over an ore vein, must give the same answer as a fresh plan.
        TerrainMaterial stone = MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState());
        for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
            prepare(world, origin, uphill, Blocks.STONE.getDefaultState(), false, false);
            world.setBlockState(origin, Blocks.COAL_ORE.getDefaultState(), 2);
            world.setBlockState(origin.up(), stone.slab, 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            requireOpenDownhillEdge(world, origin.offset(uphill).up(2), uphill.getOpposite(),
                    "a pre-existing lower slab must not receive a parallel upper slab");
            RuntimeProbe.require(world.getBlockState(origin).getBlock() == Blocks.COAL_ORE
                    && world.getBlockState(origin.up()).equals(stone.slab),
                    "lower slabs and their ore supports are not removed");
            checks += 2;
        }

        checks += borders(world) + lateLowerFloor(world, origin);
        System.out.println("TERRAIN_SMOOTHER_TRANSITIONS_PASS checks=" + checks);
        return checks;
    }

    private static int borders(WorldServer world) {
        int checks = 0;
        for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
            BlockPos low = uphill == EnumFacing.EAST ? new BlockPos(3615, 210, 3624)
                    : uphill == EnumFacing.WEST ? new BlockPos(3616, 210, 3624)
                    : uphill == EnumFacing.SOUTH ? new BlockPos(3624, 210, 3615)
                    : new BlockPos(3624, 210, 3616);
            Chunk lowerOwner = world.getChunk(low);
            Chunk upperOwner = world.getChunk(low.offset(uphill));

            for (int level = 1; level <= 3; level++) {
                IBlockState[] forward = null;
                for (boolean reverse : new boolean[] { false, true }) {
                    prepare(world, low, uphill, Blocks.STONE.getDefaultState(), false, false);
                    int loadedBefore = world.getChunkProvider().getLoadedChunkCount();
                    SkysTerrainSmoother.engine.smooth(world, reverse ? upperOwner : lowerOwner, level);
                    SkysTerrainSmoother.engine.smooth(world, reverse ? lowerOwner : upperOwner, level);
                    requireOpenDownhillEdge(world, low.offset(uphill).up(2), uphill.getOpposite(),
                            "a chunk boundary cannot recreate the upper lip, " + uphill + " / level " + level);
                    RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount() == loadedBefore,
                            "downhill boundary checks never generate chunks");
                    IBlockState[] result = { world.getBlockState(low.up()),
                            world.getBlockState(low.offset(uphill).up(2)) };
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

    private static int lateLowerFloor(WorldServer world, BlockPos origin) {
        Chunk owner = world.getChunk(origin);
        int checks = 0;
        for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
            prepare(world, origin, uphill, Blocks.STONE.getDefaultState(), false, false);
            for (int distance = -2; distance <= 0; distance++) {
                for (int width = -2; width <= 2; width++) {
                    world.setBlockState(origin.offset(uphill, distance).offset(uphill.rotateY(), width).up(),
                            Blocks.STONE.getDefaultState(), 2);
                }
            }
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            BlockPos upperCap = origin.offset(uphill).up(2);
            RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(upperCap)) != null,
                    "late-terrace regression begins with a valid upper cap");
            world.setBlockToAir(origin.up());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world, new java.util.Random(1),
                            new BlockPos(owner.x << 4, 0, owner.z << 4)));
            requireOpenDownhillEdge(world, upperCap, uphill.getOpposite(),
                    "late decoration cannot leave an upper cap that raises a newly exposed downhill edge");
            RuntimeProbe.require(SkysTerrainSmoother.level != 1 || world.isAirBlock(upperCap),
                    "slab-only mode leaves the upper lip uncapped");
            checks += 3;
        }
        return checks;
    }

    private static void requireOpenDownhillEdge(WorldServer world, BlockPos target, EnumFacing downhill,
            String message) {
        IBlockState state = world.getBlockState(target);
        if (state.getBlock() == Blocks.AIR) {
            return;
        }

        AxisAlignedBB bounds = state.getBoundingBox(world, target);
        boolean touches = downhill == EnumFacing.NORTH ? bounds.minZ == 0
                : downhill == EnumFacing.SOUTH ? bounds.maxZ == 1
                : downhill == EnumFacing.WEST ? bounds.minX == 0 : bounds.maxX == 1;
        RuntimeProbe.require(!touches, message);
    }

    private static void prepare(WorldServer world, BlockPos origin, EnumFacing uphill, IBlockState source,
            boolean wide, boolean roofed) {
        EnumFacing across = uphill.rotateY();
        for (int distance = -3; distance <= 5; distance++) {
            int rise = Math.max(0, Math.min(2, wide ? distance / 2 : distance));
            for (int width = -3; width <= 3; width++) {
                BlockPos column = origin.offset(uphill, distance).offset(across, width);
                for (int height = -2; height <= 9; height++) {
                    world.setBlockState(column.up(height), height <= rise ? source : Blocks.AIR.getDefaultState(), 2);
                }
                if (roofed) {
                    world.setBlockState(column.up(8), Blocks.BRICK_BLOCK.getDefaultState(), 2);
                }
            }
        }
    }

    private TransitionProbe() {
    }
}

