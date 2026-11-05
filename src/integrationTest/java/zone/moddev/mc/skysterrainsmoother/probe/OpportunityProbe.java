package zone.moddev.mc.skysterrainsmoother.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Snowy terraces and mixed uphill/downhill edges that still have room for a transition. */
final class OpportunityProbe {
    static int run(ServerLevel world) throws IOException {
        BlockPos floor = new BlockPos(3720, 210, 3720);
        LevelChunk owner = world.getChunkAt(floor);
        int checks = 0;

        for (BlockState source : new BlockState[] { Blocks.STONE.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.SAND.defaultBlockState() }) {
            if (source.getBlock() == Blocks.GRASS_BLOCK && !GrassSlabsApi.grassGenerationAllowed()) {
                continue;
            }
            TerrainMaterial material = MaterialCatalogue.instance().source(source);
            for (int level = 1; level <= 3; level++) {
                for (Direction uphill : Direction.Plane.HORIZONTAL) {
                    for (int cover = 1; cover <= 3; cover++) {
                        prepare(world, floor, source);
                        world.setBlock(floor.relative(uphill).above(), source, 2);
                        if ((cover & 1) != 0) {
                            world.setBlock(floor.above(), Blocks.SNOW.defaultBlockState(), 2);
                        }
                        if ((cover & 2) != 0) {
                            world.setBlock(floor.relative(uphill).above(2), Blocks.SNOW.defaultBlockState(), 2);
                        }
                        RuntimeProbe.require(MaterialCatalogue.instance().source(world.getBlockState(floor)) == material,
                                "Resetting the fixture restores its intended source material");
                        BlockState beforeSupport = world.getBlockState(floor);
                        BlockState beforeTarget = world.getBlockState(floor.above());
                        int placed = SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(floor.above())) == material,
                                "thin snow must not hide a valid transition: level " + level + " / " + uphill
                                        + " / cover=" + cover + " / source=" + source + " / before=" + beforeSupport
                                        + " / target=" + beforeTarget + " / after=" + world.getBlockState(floor)
                                        + " / cap=" + world.getBlockState(floor.above()) + " / placed=" + placed);
                        RuntimeProbe.require((cover & 2) == 0
                                || world.getBlockState(floor.relative(uphill).above(2)).getBlock() == Blocks.SNOW,
                                "snow outside the chosen placement is not cleared");
                        RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                                "snow replacement remains idempotent");
                        checks += 3;
                    }

                    // More than one layer is not the thin cover produced by normal snowfall.
                    for (int layers = 2; layers <= 8; layers++) {
                        prepare(world, floor, source);
                        world.setBlock(floor.relative(uphill).above(), source, 2);
                        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
                        world.setBlock(floor.above(), snow, 2);
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(world.getBlockState(floor.above()).equals(snow),
                                "deep snow remains occupied terrain");
                        checks++;
                    }

                    for (int lowerEdges = 1; lowerEdges <= 2; lowerEdges++) {
                        prepare(world, floor, source);
                        Direction secondRise = uphill.getClockWise();
                        world.setBlock(floor.relative(uphill).above(), source, 2);
                        world.setBlock(floor.relative(secondRise).above(), source, 2);
                        ProbeWorld.clear(world, floor.relative(uphill.getOpposite()));
                        if (lowerEdges == 2) {
                            ProbeWorld.clear(world, floor.relative(secondRise.getOpposite()));
                        }
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        // Terrain API directions are N/E/S/W, unlike Minecraft's horizontal index.
                        int stepDirection = direction(uphill);
                        int cornerDirection = corner(uphill, secondRise);
                        BlockState expected = lowerEdges == 1 && level >= 2 ? material.step(stepDirection)
                                : lowerEdges == 2 && level == 3 ? material.corner(cornerDirection) : null;
                        RuntimeProbe.require(world.getBlockState(floor.above())
                                .equals(expected == null ? Blocks.AIR.defaultBlockState() : expected),
                                "use a fitting piece instead of rejecting a mixed terrace, level " + level
                                        + " / " + uphill + " / lower edges " + lowerEdges);
                        RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                                "a clipped footprint does not grow on a repeated pass");
                        checks += 2;
                    }
                }
            }
        }
        checks += snowyTerrace(world);
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_OPPORTUNITIES_PASS checks=" + checks);
        return checks;
    }

    /** A small block-only sample from the reported mountain; no player or world metadata is included. */
    private static int snowyTerrace(ServerLevel world) throws IOException {
        JsonObject fixture;
        InputStream input = OpportunityProbe.class.getResourceAsStream("/fixtures/snowy_stone_terrace.json");
        RuntimeProbe.require(input != null, "the mountain regression fixture is packaged with the probe");
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        JsonArray savedPalette = fixture.getAsJsonArray("palette");
        BlockState[] palette = new BlockState[savedPalette.size()];
        for (int index = 0; index < palette.length; index++) {
            JsonObject entry = savedPalette.get(index).getAsJsonObject();
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(entry.get("block").getAsString()));
            RuntimeProbe.require(block != null, "unknown block in the mountain regression fixture");
            RuntimeProbe.require(entry.get("meta").getAsInt() == 0, "Only default native states occur in this terrace fixture");
            palette[index] = block.defaultBlockState();
        }

        BlockPos origin = new BlockPos(3768, 210, 3768);
        LevelChunk owner = world.getChunkAt(origin);
        int radius = fixture.get("radius").getAsInt();
        int minimumHeight = fixture.get("min_height").getAsInt();
        int maximumHeight = fixture.get("max_height").getAsInt();
        JsonArray blocks = fixture.getAsJsonArray("blocks");
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            prepare(world, origin, Blocks.STONE.defaultBlockState());
            int index = 0;
            for (int height = minimumHeight; height <= maximumHeight; height++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                        world.setBlock(origin.offset(offsetX, height, offsetZ),
                                palette[blocks.get(index++).getAsInt()], 2);
                    }
                }
            }
            SkysTerrainSmoother.engine.smooth(world, owner, level);
            RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(origin.above()))
                    == MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState()),
                    "the reported snowy stone terrace receives its matching transition at level " + level);
            RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                    "the reported terrace stays stable on a repeated pass");
            checks += 2;
        }
        return checks;
    }

    private static int direction(Direction facing) {
        return facing == Direction.NORTH ? 0 : facing == Direction.EAST ? 1
                : facing == Direction.SOUTH ? 2 : 3;
    }

    private static int corner(Direction first, Direction second) {
        int firstEdge = zone.moddev.mc.skysterrainsmoother.internal.Footprint.EDGES[direction(first)];
        int secondEdge = zone.moddev.mc.skysterrainsmoother.internal.Footprint.EDGES[direction(second)];
        return Integer.numberOfTrailingZeros(firstEdge & secondEdge);
    }

    private static void prepare(ServerLevel world, BlockPos floor, BlockState source) {
        for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                for (int height = 6; height >= -2; height--) {
                    world.setBlock(floor.offset(offsetX, height, offsetZ),
                            height <= 0 ? source : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private OpportunityProbe() {
    }
}

