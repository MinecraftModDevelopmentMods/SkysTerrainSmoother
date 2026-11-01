package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
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

    static int run(ServerWorld world) {
        BlockPos floor = new BlockPos(3000, 70, 3000);
        Chunk owner = world.getChunkAt(floor);

        clear(world, floor, 69, 255);
        fixture(world, floor, 6);
        world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()), "cave floor below a roof receives a matching slab");

        int checks = 1;
        TerrainMaterial stone = MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState());

        for (int level = 1; level <= 3; level++) {
            for (int cardinal = 0; cardinal < 16; cardinal++) {
                for (int diagonal = 0; diagonal < 16; diagonal++) {
                    fixture(world, floor, 6);

                    for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                        if ((cardinal & (1 << directionIndex)) != 0) {
                            world.setBlockState(floor.add(DX[directionIndex], 1, DZ[directionIndex]),
                                    Blocks.STONE.getDefaultState(), 2);
                        }
                        if ((diagonal & (1 << directionIndex)) != 0) {
                            world.setBlockState(floor.add(CX[directionIndex], 1, CZ[directionIndex]),
                                    Blocks.STONE.getDefaultState(), 2);
                        }
                    }

                    BlockState expected = MaterialCatalogue.instance().result(stone,
                            Footprint.mask(cardinal, diagonal, level), level);

                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    RuntimeProbe.require(
                            world.getBlockState(floor.up())
                                    .equals(expected == null ? Blocks.AIR.getDefaultState() : expected),
                            "roofed footprint " + level + "/" + cardinal + "/" + diagonal);
                    checks++;
                }
            }
        }

        // The same column can contain independent floors at multiple depths.
        fixture(world, floor, 6);
        fixture(world, floor.up(16), 6);
        world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(floor.up(16).east().up(), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 3);
        RuntimeProbe.require(!world.isAirBlock(floor.up()) && !world.isAirBlock(floor.up(17)),
                "stacked cave floors are both smoothed");
        checks++;

        for (int air = 1; air <= 4; air++) {
            fixture(world, floor, air + 1);
            world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isAirBlock(floor.up()) == (air < 3),
                    "low passage remains walkable: air height " + air);
            checks++;
        }
        for (BlockState obstacle : new BlockState[] { Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState(),
                Blocks.GLASS.getDefaultState(), Blocks.CHEST.getDefaultState() }) {
            for (int height = 1; height <= 3; height++) {
                fixture(world, floor, 6);
                RuntimeProbe.require(world.isAirBlock(floor.up()) && world.isAirBlock(floor.up(2))
                        && world.isAirBlock(floor.up(3)), "Cave fixture starts with clear headroom");
                world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(floor.up(height), obstacle, 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 1);
                RuntimeProbe.require(
                        height == 1 ? world.getBlockState(floor.up()).equals(obstacle) : world.isAirBlock(floor.up()),
                        "fluid, occupied target or blocked headroom is protected: " + obstacle + " / " + height
                                + " / actual=" + world.getBlockState(floor.up()));
                checks++;
            }
        }

        fixture(world, floor, 6);
        world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(floor.north().up(2), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(world.isAirBlock(floor.up()), "solid cave wall is not a floor transition");
        checks++;
        // An open column must still see a higher floor hidden under its neighbour's roof.
        fixture(world, floor, 6);
        ProbeWorld.clear(world, floor.up(6));
        world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()), "cave entrance and outdoor floor join consistently");
        checks++;

        for (BlockState source : new BlockState[] { Blocks.COAL_ORE.getDefaultState(),
                Blocks.GRAVEL.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState(), Blocks.INFESTED_STONE.getDefaultState(),
                Blocks.CHEST.getDefaultState() }) {
            fixture(world, floor, 6);
            world.setBlockState(floor, source, 2);
            world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isAirBlock(floor.up()) && world.getBlockState(floor).equals(source),
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
                world.setBlockState(floor, source, 2);
                world.setBlockState(floor.north().up(), source, 2);
                SkysTerrainSmoother.engine.smooth(world, owner, 1);
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.slab),
                        "cave uses the exact registered material: " + material.id);
                RuntimeProbe
                        .require(
                                world.getBlockState(floor)
                                        .equals(material.supportAfterPlacement == null ? source
                                                : material.supportAfterPlacement),
                                "native support identity is preserved or dirtified");
                RuntimeProbe.require(world.getBlockState(floor.up(6)).getBlock() == Blocks.BRICKS,
                        "cave ceiling remains intact");
                checks += 3;

                if (material.mineralogy) {
                    mineralogy++;
                }
            }
        }

        // Structure bounds and add-on vetoes apply to underground floors as well.
        StructureStart previous = owner.getStructureStarts().get("Mineshaft");
        StructureStart protectedStart = new StructureStart(Feature.MINESHAFT, owner.getPos().x, owner.getPos().z,
                new MutableBoundingBox(floor.getX() - 2, 69, floor.getZ() - 2,
                        floor.getX() + 2, 80, floor.getZ() + 2), 0, 1) {
            @Override
            public void init(net.minecraft.world.gen.ChunkGenerator<?> generator,
                    net.minecraft.world.gen.feature.template.TemplateManager templates, int chunkX, int chunkZ,
                    net.minecraft.world.biome.Biome biome) {
            }

            @Override
            public boolean isValid() {
                return true;
            }
        };
        owner.getStructureStarts().put("Mineshaft", protectedStart);
        try {
            fixture(world, floor, 6);
            world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            RuntimeProbe.require(world.isAirBlock(floor.up()), "Native mineshaft bounds protect underground floors");
            checks++;
        } finally {
            if (previous == null) {
                owner.getStructureStarts().remove("Mineshaft");
            } else {
                owner.getStructureStarts().put("Mineshaft", previous);
            }
        }

        BlockPos veto = new BlockPos(floor.getX(), 240, floor.getZ());

        fixture(world, veto, 6);
        world.setBlockState(veto.north().up(), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(world.isAirBlock(veto.up()), "underground add-on area veto");
        checks++;
        checks += lateDecoration(world, floor, owner);
        checks += oreDecoration(world, floor, owner);
        checks += manyFloors(world);
        System.out.println("TERRAIN_SMOOTHER_CAVE_MATERIALS_PASS mineralogy=" + mineralogy);
        System.out.println("TERRAIN_SMOOTHER_CAVE_PASS checks=" + checks);
        return checks;
    }

    private static int lateDecoration(ServerWorld world, BlockPos floor, Chunk owner) {
        int checks = 0;

        for (int kind = 0; kind < 4; kind++) {
            fixture(world, floor, 6);
            world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
            SkysTerrainSmoother.engine.smooth(world, owner, 1);
            ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
            RuntimeProbe.require(!world.isAirBlock(floor.up()),
                    "decorated cave cap retained until all later generators finish");
            ProbeWorld.queue(world, owner.getPos().x, owner.getPos().z);

            if (kind == 0) {
                world.setBlockState(floor, Blocks.INFESTED_STONE.getDefaultState(), 2);
            }
            if (kind == 1) {
                world.setBlockState(floor, Blocks.SAND.getDefaultState(), 2);
            }
            if (kind == 2) {
                world.setBlockState(floor.up(2), Blocks.BRICKS.getDefaultState(), 2);
            }
            if (kind == 3) {
                world.setBlockState(floor, Blocks.SAND.getDefaultState(), 2);
                world.setBlockState(floor.up(), Blocks.GLASS.getDefaultState(), 2);
            }

            MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, world));
            RuntimeProbe.require(
                    kind == 1
                            ? world.getBlockState(floor.up())
                                    .getBlock() == zone.moddev.mc.skysterrainsmoother.content.SandContent.slab
                            : kind == 3 ? world.getBlockState(floor.up()).getBlock() == Blocks.GLASS
                                    : world.isAirBlock(floor.up()),
                    "late infested stone, sand, low ceiling and provider-owned replacement are handled without excavation");
            checks += 2;
        }

        return checks;
    }

    private static int oreDecoration(ServerWorld world, BlockPos floor, Chunk owner) {
        int checks = 0;

        for (BlockState ore : new BlockState[] { Blocks.COAL_ORE.getDefaultState(), Blocks.IRON_ORE.getDefaultState(),
                Blocks.REDSTONE_ORE.getDefaultState(), Blocks.REDSTONE_ORE.getDefaultState().with(RedstoneOreBlock.LIT, true),
                RuntimeProbe.probeOre.getDefaultState() }) {
            for (int level = 1; level <= 3; level++) {
                fixture(world, floor, 6);
                world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);

                BlockState cap = world.getBlockState(floor.up());

                RuntimeProbe.require(cap.getBlock() != Blocks.AIR, "ore regression starts with a generated piece");
                world.setBlockState(floor, ore, 2);
                ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
                ProbeWorld.queue(world, owner.getPos().x, owner.getPos().z);
                MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, world));
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(cap),
                        "ore under a generated piece stays hidden: " + ore + " / level " + level);
                RuntimeProbe.require(world.getBlockState(floor).equals(ore), "ore support is never overwritten");
                checks += 3;
            }
        }

        fixture(world, floor, 6);
        world.setBlockState(floor.north().up(), Blocks.STONE.getDefaultState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        world.setBlockState(floor, Blocks.COAL_ORE.getDefaultState(), 2);
        world.setBlockState(floor.up(2), Blocks.BRICKS.getDefaultState(), 2);
        MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, world));
        RuntimeProbe.require(world.isAirBlock(floor.up()) && world.getBlockState(floor).getBlock() == Blocks.COAL_ORE,
                "ore preservation does not override cave headroom protection");
        checks++;
        fixture(world, floor, 6);
        world.setBlockState(floor.north().up(), Blocks.COAL_ORE.getDefaultState(), 2);
        world.setBlockState(floor.north().up(2),
                MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState()).slab, 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()),
                "neighbouring retained ore cap still represents a higher logical floor");
        checks++;
        System.out.println("TERRAIN_SMOOTHER_ORE_SUPPORT_PASS checks=" + checks);
        return checks;
    }

    private static int manyFloors(ServerWorld world) {
        Chunk owner = world.getChunk(200, 200);
        net.minecraft.world.chunk.ChunkSection[] sections = owner.getSections();

        for (int section = 0; section < 16; section++) {
            sections[section] = new net.minecraft.world.chunk.ChunkSection(section * 16);
        }
        for (int blockZ = 0; blockZ < 16; blockZ++) {
            for (int blockX = 0; blockX < 16; blockX++) {
                for (int blockY = 32; blockY < 232; blockY += 8) {
                    int floorY = blockY + ((blockX + blockZ) & 1);

                    sections[floorY >> 4].setBlockState(blockX, floorY & 15, blockZ, Blocks.STONE.getDefaultState());
                }

                sections[15].setBlockState(blockX, 246 & 15, blockZ, Blocks.BRICKS.getDefaultState());

            }
        }

        int countBefore = world.getChunkProvider().getLoadedChunkCount();
        ProbeWorld.rebuildHeights(owner);
        int placed = SkysTerrainSmoother.engine.smooth(world, owner, 1);

        RuntimeProbe.require(placed > 256, "decision buffer covers multiple floors across all 256 columns");
        RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount() == countBefore,
                "cave scan never loads a halo chunk");

        int second = SkysTerrainSmoother.engine.smooth(world, owner, 1);

        RuntimeProbe.require(second == 0, "repeated floor scan never stacks new caps");
        System.out.println("TERRAIN_SMOOTHER_CAVE_BUFFER_PASS placed=" + placed);
        return 3;
    }

    private static void clear(ServerWorld world, BlockPos floor, int minY, int maxY) {
        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                for (int blockY = minY; blockY <= maxY; blockY++) {
                    ProbeWorld.clear(world, new BlockPos(floor.getX() + blockX, blockY, floor.getZ() + blockZ));
                }
            }
        }
    }

    private static void fixture(ServerWorld world, BlockPos floor, int roofOffset) {
        clear(world, floor, floor.getY(), floor.getY() + 8);

        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                world.setBlockState(floor.add(blockX, 0, blockZ), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(floor.add(blockX, roofOffset, blockZ), Blocks.BRICKS.getDefaultState(), 2);
            }
        }
    }
}
