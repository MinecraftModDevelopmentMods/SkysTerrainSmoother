package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.*;
import java.security.MessageDigest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Decoration-created grass and hardened-clay regressions, never shipped. */
final class LateTerrainProbe {
    private static final int[] DX = { 0, 1, 0, -1 }, DZ = { -1, 0, 1, 0 }, CX = { -1, 1, 1, -1 }, CZ = { -1, -1, 1, 1 };

    private LateTerrainProbe() {
    }

    static int run(ServerLevel world) {
        MaterialCatalogue catalogue = MaterialCatalogue.instance();
        BlockState clay = Blocks.TERRACOTTA.defaultBlockState();
        TerrainMaterial material = catalogue.source(clay);
        TerrainMaterial grass = catalogue.source(Blocks.GRASS_BLOCK.defaultBlockState());

        RuntimeProbe.require(material != null, "hardened clay is a smoothing source");
        RuntimeProbe.require(
                material.slab.equals(BuildingPiecesApi.bottomPiece("minecraft:hardened_clay",
                        BuildingPiecesApi.TerrainShape.SLAB, Direction.NORTH)),
                "reuse existing clay slab without new registry IDs");

        int checks = 2;
        int previousLevel = SkysTerrainSmoother.level;

        BlockPos floor = new BlockPos(30008, 220, 30008);
        LevelChunk owner = world.getChunkAt(floor);

        try {
            for (int level = 1; level <= 3; level++) {
                for (int direction = 0; direction < 4; direction++) {
                    SkysTerrainSmoother.level = level;

                    for (boolean diagonal : new boolean[] { false, true }) {
                        fixture(world, floor, clay);

                        BlockPos higher = floor.offset(diagonal ? CX[direction] : DX[direction], 1,
                                diagonal ? CZ[direction] : DZ[direction]);

                        world.setBlock(higher, clay, 2);

                        int mask = Footprint.mask(diagonal ? 0 : 1 << direction, diagonal ? 1 << direction : 0, level);
                        BlockState expected = catalogue.result(material, mask, level);

                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(
                                world.getBlockState(floor.above())
                                        .equals(expected == null ? Blocks.AIR.defaultBlockState() : expected),
                                "clay footprint level=" + level + " direction=" + direction + " diagonal=" + diagonal);
                        RuntimeProbe.require(world.getBlockState(floor).equals(clay),
                                "clay support is never converted to grass or dirt");
                        checks += 2;
                    }

                    // A flat clay surface has no cap in Pre. BOP subsequently adds oasis grass and a higher
                    // neighbouring clay surface.
                    fixture(world, floor, clay);
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    RuntimeProbe.require(world.isEmptyBlock(floor.above()), "no initial cap on a flat clay surface");
                    checks++;
                    world.setBlock(floor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    world.setBlock(floor.offset(DX[direction], 1, DZ[direction]), clay, 2);

                    int loaded = world.getChunkSource().getLoadedChunksCount();

                    post(world, owner);

                    BlockState expected = GrassSlabsApi.grassGenerationAllowed()
                            ? catalogue.result(grass, Footprint.EDGES[direction], level)
                            : Blocks.AIR.defaultBlockState();

                    RuntimeProbe.require(world.getBlockState(floor.above()).equals(expected),
                            "late oasis grass gets its own grass cap beside clay");
                    RuntimeProbe.require(
                            world.getBlockState(floor)
                                    .equals(GrassSlabsApi.grassGenerationAllowed() && level == 1 ? Blocks.DIRT.defaultBlockState()
                                            : Blocks.GRASS_BLOCK.defaultBlockState()),
                            "late support conversion respects the grass switch and exposed footprint");
                    post(world, owner);
                    RuntimeProbe.require(
                            world.getBlockState(floor.above()).equals(expected) && world.isEmptyBlock(floor.above(2)),
                            "repeated late pass is idempotent and does not stack caps");
                    RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loaded,
                            "late pass never loads a neighbour");
                    checks += 4;

                    // Small grass may give way in this tracked fresh owner; trees and other occupied blocks may not.
                    for (BlockState occupied : new BlockState[] { Blocks.SHORT_GRASS.defaultBlockState(),
                            Blocks.OAK_SAPLING.defaultBlockState(),
                            Blocks.WATER.defaultBlockState(), Blocks.GLASS.defaultBlockState() }) {
                        fixture(world, floor, Blocks.GRAVEL.defaultBlockState());
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        world.setBlock(floor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        world.setBlock(floor.offset(DX[direction], 1, DZ[direction]), clay, 2);
                        world.setBlock(floor.above(), occupied, 2);
                        post(world, owner);
                        boolean replacePlant = occupied.getBlock() == Blocks.SHORT_GRASS
                                && GrassSlabsApi.grassGenerationAllowed();
                        RuntimeProbe.require(world.getBlockState(floor.above()).equals(replacePlant ? expected : occupied),
                                "late pass only replaces small grass at a valid transition: " + occupied);
                        RuntimeProbe.require(world.getBlockState(floor).getBlock()
                                == (replacePlant && level == 1 ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                                "support dirtification follows successful placement only");
                        checks += 2;
                    }
                }
            }
        } finally {
            SkysTerrainSmoother.level = previousLevel;
        }

        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLAY_OASIS_PASS checks=" + checks);
        return checks;
    }

    private static void fixture(ServerLevel world, BlockPos floor, BlockState source) {
        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                for (int blockY = floor.getY() + 1; blockY < 256; blockY++) {
                    ProbeWorld.clear(world, new BlockPos(floor.getX() + blockX, blockY, floor.getZ() + blockZ));
                }

                world.setBlock(floor.offset(blockX, 0, blockZ), source, 2);
            }
        }
    }

    private static void post(ServerLevel world, LevelChunk owner) {
        ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
    }

    static int generationOrder(ServerLevel world) throws Exception {
        int previousLevel = SkysTerrainSmoother.level;

        try {
            for (int level = 1; level <= 3; level++) {
                SkysTerrainSmoother.level = level;

                byte[][] hashes = new byte[2][];

                for (int order = 0; order < 2; order++) {
                    int origin = order == 0 ? 128 : 256;
                    ProbeWorld.coldBiome(world, origin, 128, 9);
                    List<LevelChunk> chunks = new ArrayList<>();

                    for (int blockZ = 0; blockZ < 9; blockZ++) {
                        for (int blockX = 0; blockX < 9; blockX++) {
                            chunks.add(world.getChunk(origin + blockX, 128 + blockZ));
                        }
                    }
                    for (int blockZ = 0; blockZ < 144; blockZ++) {
                        for (int blockX = 0; blockX < 144; blockX++) {
                            int height = 200
                                    + Math.floorMod(blockX * 31 + blockZ * 17 + (blockX >> 3) * (blockZ >> 3), 3);
                            LevelChunk chunk = chunks.get((blockX >> 4) + (blockZ >> 4) * 9);

                            for (int blockY = 170; blockY < 256; blockY++) {
                                net.minecraft.world.level.chunk.LevelChunkSection section = chunk
                                        .getSections()[chunk.getSectionIndex(blockY)];

                                if (section == null) {
                                    chunk.getSections()[chunk.getSectionIndex(blockY)] = section = ProbeWorld.emptySection(world);
                                }

                                section.setBlockState(blockX & 15, blockY & 15, blockZ & 15, Blocks.AIR.defaultBlockState());
                            }

                            chunk.getSections()[chunk.getSectionIndex(height)].setBlockState(blockX & 15, height & 15, blockZ & 15,
                                    Blocks.TERRACOTTA.defaultBlockState());

                        }
                    }
                    for (LevelChunk chunk : chunks) {
                    ProbeWorld.rebuildHeights(chunk);
                }
                if (order == 1) {
                        Collections.reverse(chunks);
                    }
                    for (LevelChunk chunk : chunks) {
                        SkysTerrainSmoother.engine.smooth(world, chunk, level);
                    }
                    // Introduce grass after the initial pass, including beneath clay caps, to model decoration changing
                    // the full surface.
                    for (int blockZ = 0; blockZ < 144; blockZ++) {
                        for (int blockX = 0; blockX < 144; blockX++) {
                            if ((blockX + blockZ) % 5 == 0) {
                                int height = 200
                                        + Math.floorMod(blockX * 31 + blockZ * 17 + (blockX >> 3) * (blockZ >> 3), 3);
                                // Resolve by coordinate, not traversal order.
                                LevelChunk chunk = world.getChunkSource().getChunkNow(origin + (blockX >> 4),
                                        128 + (blockZ >> 4));

                                chunk.setBlockState(new BlockPos((origin << 4) + blockX, height, (128 << 4) + blockZ),
                                        Blocks.GRASS_BLOCK.defaultBlockState(), false);
                            }
                        }
                    }
                    for (LevelChunk chunk : chunks) {
                        post(world, chunk);
                    }

                    MessageDigest digest = MessageDigest.getInstance("SHA-256");

                    for (int blockZ = 0; blockZ < 144; blockZ++) {
                        for (int blockX = 0; blockX < 144; blockX++) {
                            for (int blockY = 200; blockY < 205; blockY++) {
                                BlockState state = world.getBlockState(
                                        new BlockPos((origin << 4) + blockX, blockY, (128 << 4) + blockZ));

                                digest.update((net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "/"
                                        + state.toString() + "\n").getBytes("UTF-8"));
                            }
                        }
                    }

                    hashes[order] = digest.digest();
                }

                RuntimeProbe.require(Arrays.equals(hashes[0], hashes[1]),
                        "late clay/oasis 9x9 order differs at level " + level);

                StringBuilder hexDigest = new StringBuilder();

                for (byte value : hashes[0]) {
                    hexDigest.append(String.format("%02X", value & 255));
                }

                zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_LATE_GENERATION_ORDER_PASS level=" + level + " chunks=81 sha256="
                        + hexDigest);
            }
        } finally {
            SkysTerrainSmoother.level = previousLevel;
        }

        return 486;
    }
}
