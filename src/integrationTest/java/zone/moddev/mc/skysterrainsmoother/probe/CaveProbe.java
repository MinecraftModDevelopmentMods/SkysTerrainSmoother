package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;

/** Real cave-floor regressions, never included in production launches or jars. */
final class CaveProbe {
    private static final int[] DX = { 0, 1, 0, -1 }, DZ = { -1, 0, 1, 0 }, CX = { -1, 1, 1, -1 }, CZ = { -1, -1, 1, 1 };

    private CaveProbe() {
    }

    static int run(ServerLevel world) {
        BlockPos floor = new BlockPos(3000, 70, 3000);
        LevelChunk owner = world.getChunkAt(floor);

        clear(world, floor, 69, 255);
        fixture(world, floor, 6);
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isEmptyBlock(floor.above()), "cave floor below a roof receives a matching slab");

        int checks = 1;
        TerrainMaterial stone = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());

        for (int level = 1; level <= 3; level++) {
            for (int cardinal = 0; cardinal < 16; cardinal++) {
                for (int diagonal = 0; diagonal < 16; diagonal++) {
                    fixture(world, floor, 6);

                    for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                        if ((cardinal & (1 << directionIndex)) != 0) {
                            world.setBlock(floor.offset(DX[directionIndex], 1, DZ[directionIndex]),
                                    Blocks.STONE.defaultBlockState(), 2);
                        }
                        if ((diagonal & (1 << directionIndex)) != 0) {
                            world.setBlock(floor.offset(CX[directionIndex], 1, CZ[directionIndex]),
                                    Blocks.STONE.defaultBlockState(), 2);
                        }
                    }

                    BlockState expected = MaterialCatalogue.instance().result(stone,
                            Footprint.mask(cardinal, diagonal, level), level);

                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    RuntimeProbe.require(
                            world.getBlockState(floor.above())
                                    .equals(expected == null ? Blocks.AIR.defaultBlockState() : expected),
                            "roofed footprint " + level + "/" + cardinal + "/" + diagonal);
                    checks++;
                }
            }
        }

        // The same column can contain independent floors at multiple depths.
        fixture(world, floor, 6);
        fixture(world, floor.above(16), 6);
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(floor.above(16).east().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 3);
        RuntimeProbe.require(!world.isEmptyBlock(floor.above()) && !world.isEmptyBlock(floor.above(17)),
                "stacked cave floors are both smoothed");
        checks++;

        for (int air = 1; air <= 4; air++) {
            fixture(world, floor, air + 1);
            world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isEmptyBlock(floor.above()) == (air < 3),
                    "low passage remains walkable: air height " + air);
            checks++;
        }
        for (BlockState obstacle : new BlockState[] { Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.CHEST.defaultBlockState() }) {
            for (int height = 1; height <= 3; height++) {
                fixture(world, floor, 6);
                RuntimeProbe.require(world.isEmptyBlock(floor.above()) && world.isEmptyBlock(floor.above(2))
                        && world.isEmptyBlock(floor.above(3)), "Cave fixture starts with clear headroom");
                world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(floor.above(height), obstacle, 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 1);
                RuntimeProbe.require(
                        height == 1 ? world.getBlockState(floor.above()).equals(obstacle) : world.isEmptyBlock(floor.above()),
                        "fluid, occupied target or blocked headroom is protected: " + obstacle + " / " + height
                                + " / actual=" + world.getBlockState(floor.above()));
                checks++;
            }
        }

        fixture(world, floor, 6);
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(floor.north().above(2), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(world.isEmptyBlock(floor.above()), "solid cave wall is not a floor transition");
        checks++;
        // An open column must still see a higher floor hidden under its neighbour's roof.
        fixture(world, floor, 6);
        ProbeWorld.clear(world, floor.above(6));
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isEmptyBlock(floor.above()), "cave entrance and outdoor floor join consistently");
        checks++;

        for (BlockState source : new BlockState[] { Blocks.COAL_ORE.defaultBlockState(),
                Blocks.GRAVEL.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.INFESTED_STONE.defaultBlockState(),
                Blocks.CHEST.defaultBlockState() }) {
            fixture(world, floor, 6);
            world.setBlock(floor, source, 2);
            world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isEmptyBlock(floor.above()) && world.getBlockState(floor).equals(source),
                    "unregistered provider terrain, ores and tile sources remain intact");
            checks++;
        }

        int mineralogy = 0;

        for (TerrainMaterial material : MaterialCatalogue.instance().materials()) {
            if (material.grass && !GrassSlabsApi.grassGenerationAllowed()
                    || material.mineralogy && !SkysTerrainSmoother.mineralogy) {
                continue;
            }
            for (BlockState source : material.sources) {
                fixture(world, floor, 6);
                world.setBlock(floor, source, 2);
                world.setBlock(floor.north().above(), source, 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 1);
                RuntimeProbe.require(world.getBlockState(floor.above()).equals(material.slab),
                        "cave uses the exact registered material: " + material.id);
                RuntimeProbe
                        .require(
                                world.getBlockState(floor)
                                        .equals(material.supportAfterPlacement == null ? source
                                                : material.supportAfterPlacement),
                                "native support identity is preserved or dirtified");
                RuntimeProbe.require(world.getBlockState(floor.above(6)).getBlock() == Blocks.BRICKS,
                        "cave ceiling remains intact");
                checks += 3;

                if (material.mineralogy) {
                    mineralogy++;
                }
            }
        }

        // Structure bounds and add-on vetoes apply to underground floors as well.
        var previousStarts = new java.util.HashMap<>(owner.getAllStarts());
        BoundingBox protectedBounds = new BoundingBox(floor.getX() - 2, 69, floor.getZ() - 2,
                floor.getX() + 2, 80, floor.getZ() + 2);
        var mineshaft = world.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.MINESHAFT);
        StructureStart protectedStart = protectedStart(owner, mineshaft, protectedBounds);
        owner.setStartForStructure(mineshaft, protectedStart);
        try {
            fixture(world, floor, 6);
            world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isEmptyBlock(floor.above()), "Native mineshaft bounds protect underground floors");
            checks++;
        } finally {
            owner.setAllStarts(previousStarts);
        }

        BlockPos veto = new BlockPos(floor.getX(), 240, floor.getZ());

        fixture(world, veto, 6);
        world.setBlock(veto.north().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(world.isEmptyBlock(veto.above()), "underground add-on area veto");
        checks++;
        checks += lateDecoration(world, floor, owner);
        checks += oreDecoration(world, floor, owner);
        checks += manyFloors(world);
        checks += nativeHeightRange(world);
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CAVE_MATERIALS_PASS mineralogy=" + mineralogy);
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CAVE_PASS checks=" + checks);
        return checks;
    }

    private static int lateDecoration(ServerLevel world, BlockPos floor, LevelChunk owner) {
        int checks = 0;

        for (int kind = 0; kind < 4; kind++) {
            fixture(world, floor, 6);
            world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
            RuntimeProbe.require(!world.isEmptyBlock(floor.above()),
                    "decorated cave cap retained until all later generators finish");
            ProbeWorld.queue(world, owner.getPos().x, owner.getPos().z);

            if (kind == 0) {
                world.setBlock(floor, Blocks.INFESTED_STONE.defaultBlockState(), 2);
            }
            if (kind == 1) {
                world.setBlock(floor, Blocks.SAND.defaultBlockState(), 2);
            }
            if (kind == 2) {
                world.setBlock(floor.above(2), Blocks.BRICKS.defaultBlockState(), 2);
            }
            if (kind == 3) {
                world.setBlock(floor, Blocks.SAND.defaultBlockState(), 2);
                world.setBlock(floor.above(), Blocks.GLASS.defaultBlockState(), 2);
            }

            NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, world));
            RuntimeProbe.require(
                    kind == 1
                            ? world.getBlockState(floor.above())
                                    .getBlock() == zone.moddev.mc.skysterrainsmoother.content.SandContent.slab
                            : kind == 3 ? world.getBlockState(floor.above()).getBlock() == Blocks.GLASS
                                    : world.isEmptyBlock(floor.above()),
                    "late infested stone, sand, low ceiling and provider-owned replacement are handled without excavation");
            checks += 2;
        }

        return checks;
    }

    private static int oreDecoration(ServerLevel world, BlockPos floor, LevelChunk owner) {
        int checks = 0;

        for (BlockState ore : new BlockState[] { Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
                Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState().setValue(RedStoneOreBlock.LIT, true),
                RuntimeProbe.probeOre.defaultBlockState() }) {
            for (int level = 1; level <= 3; level++) {
                fixture(world, floor, 6);
                world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);

                BlockState cap = world.getBlockState(floor.above());

                RuntimeProbe.require(cap.getBlock() != Blocks.AIR, "ore regression starts with a generated piece");
                world.setBlock(floor, ore, 2);
                ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
                ProbeWorld.queue(world, owner.getPos().x, owner.getPos().z);
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, world));
                RuntimeProbe.require(world.getBlockState(floor.above()).equals(cap),
                        "ore under a generated piece stays hidden: " + ore + " / level " + level);
                RuntimeProbe.require(world.getBlockState(floor).equals(ore), "ore support is never overwritten");
                checks += 3;
            }
        }

        fixture(world, floor, 6);
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        world.setBlock(floor, Blocks.COAL_ORE.defaultBlockState(), 2);
        world.setBlock(floor.above(2), Blocks.BRICKS.defaultBlockState(), 2);
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, world));
        RuntimeProbe.require(world.isEmptyBlock(floor.above()) && world.getBlockState(floor).getBlock() == Blocks.COAL_ORE,
                "ore preservation does not override cave headroom protection");
        checks++;
        fixture(world, floor, 6);
        world.setBlock(floor.north().above(), Blocks.COAL_ORE.defaultBlockState(), 2);
        world.setBlock(floor.north().above(2),
                MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState()).slab, 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isEmptyBlock(floor.above()),
                "neighbouring retained ore cap still represents a higher logical floor");
        checks++;
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_ORE_SUPPORT_PASS checks=" + checks);
        return checks;
    }

    private static StructureStart protectedStart(LevelChunk owner,
            net.minecraft.world.level.levelgen.structure.Structure structure, BoundingBox bounds) {
        net.minecraft.world.level.levelgen.structure.StructurePiece piece =
                new net.minecraft.world.level.levelgen.structure.StructurePiece(
                        net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.MINE_SHAFT_ROOM,
                        0, bounds) {
                    @Override
                    protected void addAdditionalSaveData(
                            net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                            net.minecraft.nbt.CompoundTag tag) {
                    }

                    @Override
                    public void postProcess(net.minecraft.world.level.WorldGenLevel world,
                            net.minecraft.world.level.StructureManager structures,
                            net.minecraft.world.level.chunk.ChunkGenerator generator,
                            net.minecraft.util.RandomSource random, BoundingBox clip,
                            net.minecraft.world.level.ChunkPos chunk, BlockPos position) {
                    }
                };
        return new StructureStart(structure,
                owner.getPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(piece)));
    }

    private static int nativeHeightRange(ServerLevel world) {
        RuntimeProbe.require(world.getMinBuildHeight() == -64 && world.getMaxBuildHeight() == 320,
                "The native Overworld exposes the complete 1.18 height range");
        BlockPos centre = new BlockPos(3020, 0, 3020);
        LevelChunk owner = world.getChunkAt(centre);
        TerrainMaterial stone = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());
        int[] floors = { -56, -24, 48, 264, 312 };
        int checks = 0;
        for (int level = 1; level <= 3; level++) {
            clear(world, centre, world.getMinBuildHeight(), world.getMaxBuildHeight() - 1);
            for (int floorY : floors) {
                BlockPos floor = centre.atY(floorY);
                fixture(world, floor, 6);
                world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
            }
            ProbeWorld.rebuildHeights(owner);
            int loadedBefore = world.getChunkSource().getLoadedChunksCount();
            SkysTerrainSmoother.engine.smooth(world, owner, level);
            BlockState expected = MaterialCatalogue.instance().result(stone, Footprint.mask(1, 0, level), level);
            for (int floorY : floors) {
                BlockPos floor = centre.atY(floorY);
                RuntimeProbe.require(world.getBlockState(floor.above()).equals(expected),
                        "Matching cave transition at native height " + floorY + " / level " + level);
                RuntimeProbe.require(world.getBlockState(floor.above(6)).is(Blocks.BRICKS),
                        "Height-range smoothing keeps the cave roof intact");
                checks++;
            }
            RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    "Height-range smoothing does not load neighbouring chunks");
        }
        SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_HEIGHT_RANGE_PASS checks={} min=-64 max=320 levels=3", checks);
        return checks;
    }

    private static int manyFloors(ServerLevel world) {
        LevelChunk owner = world.getChunk(200, 200);
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = owner.getSections();

        for (int section = 0; section < sections.length; section++) {
            sections[section] = ProbeWorld.emptySection(world);
        }
        for (int blockZ = 0; blockZ < 16; blockZ++) {
            for (int blockX = 0; blockX < 16; blockX++) {
                for (int blockY = 32; blockY < 232; blockY += 8) {
                    int floorY = blockY + ((blockX + blockZ) & 1);

                    sections[owner.getSectionIndex(floorY)].setBlockState(blockX, floorY & 15, blockZ, Blocks.STONE.defaultBlockState());
                }

                sections[owner.getSectionIndex(246)].setBlockState(blockX, 246 & 15, blockZ, Blocks.BRICKS.defaultBlockState());

            }
        }

        int countBefore = world.getChunkSource().getLoadedChunksCount();
        ProbeWorld.rebuildHeights(owner);
        int placed = SkysTerrainSmoother.engine.smooth(world, owner, 1);

        RuntimeProbe.require(placed > 256, "decision buffer covers multiple floors across all 256 columns");
        RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == countBefore,
                "cave scan never loads a halo chunk");

        int second = SkysTerrainSmoother.engine.smooth(world, owner, 1);

        RuntimeProbe.require(second == 0, "repeated floor scan never stacks new caps");
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CAVE_BUFFER_PASS placed=" + placed);
        return 3;
    }

    private static void clear(ServerLevel world, BlockPos floor, int minY, int maxY) {
        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    ProbeWorld.clear(world, new BlockPos(floor.getX() + blockX, blockY, floor.getZ() + blockZ));
                }
            }
        }
    }

    private static void fixture(ServerLevel world, BlockPos floor, int roofOffset) {
        clear(world, floor, floor.getY(), floor.getY() + 8);

        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                world.setBlock(floor.offset(blockX, 0, blockZ), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(floor.offset(blockX, roofOffset, blockZ), Blocks.BRICKS.defaultBlockState(), 2);
            }
        }
    }
}
