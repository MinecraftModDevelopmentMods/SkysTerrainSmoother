package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Real placements under the settings loaded at startup, without changing live configuration. */
final class MaterialConfigProbe {
    static int run(ServerLevel world) {
        var catalogue = MaterialCatalogue.instance();
        BlockPos floor = new BlockPos(System.getProperty("skysterrainsmoother.integrationPhase", "fresh").contains("reload")
                ? 6008 : 5000, 210, 5000);
        var owner = world.getChunkAt(floor);
        int checks = 0;
        int mineralogy = 0;
        int bop = 0;
        var seen = java.util.EnumSet.noneOf(MaterialGroup.class);
        for (TerrainMaterial material : catalogue.materials()) {
            seen.add(material.group);
            if (material.mineralogy) mineralogy++;
            if (material.id.getNamespace().equals("biomesoplenty")) bop++;
            BlockState source = material.sources.iterator().next();
            for (int level = 1; level <= 3; level++) {
                for (boolean cave : new boolean[] {false, true}) {
                    fixture(world, floor, source, cave);
                    ProbeWorld.rebuildHeights(owner);
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    boolean allowed = TerrainSmootherConfig.groupEnabled(material.group)
                            && (!material.mineralogy || TerrainSmootherConfig.mineralogy());
                    BlockState cap = world.getBlockState(floor.above());
                    RuntimeProbe.require(allowed ? catalogue.piece(cap) == material : cap.isAir(),
                            "Material switch controls " + material.id + " level=" + level + " cave=" + cave);
                    if (!allowed) {
                        RuntimeProbe.require(world.getBlockState(floor).equals(source),
                                "Disabled material keeps its original support");
                    }
                    checks++;
                }
            }
        }
        RuntimeProbe.require(seen.size() == MaterialGroup.values().length, "Every configurable group has a real fixture");
        // Stone can remain an uphill neighbour when the pack only wants softer ground smoothed.
        fixture(world, floor, Blocks.GRASS_BLOCK.defaultBlockState(), false);
        world.setBlock(floor.north().above(), Blocks.STONE.defaultBlockState(), 2);
        ProbeWorld.rebuildHeights(owner);
        SkysTerrainSmoother.engine.smooth(world, owner, 3);
        RuntimeProbe.require(world.isEmptyBlock(floor.above()) != TerrainSmootherConfig.groupEnabled(MaterialGroup.SOIL),
                "Disabling stone does not hide its height from an adjacent soil transition");
        checks++;

        // Exercise the second pass too: a decorator can replace the original ground after smoothing.
        fixture(world, floor, Blocks.GRASS_BLOCK.defaultBlockState(), false);
        ProbeWorld.rebuildHeights(owner);
        ProbeWorld.generate(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
        world.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
        ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
        BlockState cap = world.getBlockState(floor.above());
        RuntimeProbe.require(TerrainSmootherConfig.groupEnabled(MaterialGroup.STONE)
                        ? catalogue.piece(cap) == catalogue.source(Blocks.STONE.defaultBlockState()) : cap.isAir(),
                "Decoration repair obeys the stone switch");
        checks++;
        SkysTerrainSmoother.logger.info("TERRAIN_MATERIAL_CONFIG_PASS checks={} groups={} mineralogy={} bop={} enabled={}",
                checks, seen.size(), mineralogy, bop, TerrainSmootherConfig.configuredGroups());
        return checks;
    }

    private static void fixture(ServerLevel world, BlockPos floor, BlockState source, boolean cave) {
        for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                BlockPos column = floor.offset(offsetX, 0, offsetZ);
                for (int height = 0; height <= 5; height++) ProbeWorld.clear(world, column.above(height));
                world.setBlock(column.below(), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(column, source, 2);
                if (cave) world.setBlock(column.above(4), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        world.setBlock(floor.north().above(), source, 2);
    }
}
