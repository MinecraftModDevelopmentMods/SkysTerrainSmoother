package zone.moddev.mc.skysterrainsmoother.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Snowy terraces and mixed uphill/downhill edges that still have room for a transition. */
final class OpportunityProbe {
    static int run(WorldServer world) throws IOException {
        BlockPos floor = new BlockPos(3720, 210, 3720);
        Chunk owner = world.getChunk(floor);
        int checks = 0;

        for (IBlockState source : new IBlockState[] { Blocks.STONE.getDefaultState(),
                Blocks.GRASS.getDefaultState(), Blocks.SAND.getDefaultState() }) {
            if (source.getBlock() == Blocks.GRASS && !GrassSlabsApi.grassGenerationAllowed()) {
                continue;
            }
            TerrainMaterial material = MaterialCatalogue.instance().source(source);
            for (int level = 1; level <= 3; level++) {
                for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
                    for (int cover = 1; cover <= 3; cover++) {
                        prepare(world, floor, source);
                        world.setBlockState(floor.offset(uphill).up(), source, 2);
                        if ((cover & 1) != 0) {
                            world.setBlockState(floor.up(), Blocks.SNOW_LAYER.getDefaultState(), 2);
                        }
                        if ((cover & 2) != 0) {
                            world.setBlockState(floor.offset(uphill).up(2), Blocks.SNOW_LAYER.getDefaultState(), 2);
                        }
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(floor.up())) == material,
                                "thin snow must not hide a valid transition: level " + level + " / " + uphill);
                        RuntimeProbe.require((cover & 2) == 0
                                || world.getBlockState(floor.offset(uphill).up(2)).getBlock() == Blocks.SNOW_LAYER,
                                "snow outside the chosen placement is not cleared");
                        RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                                "snow replacement remains idempotent");
                        checks += 3;
                    }

                    // More than one layer is not the thin cover produced by normal snowfall.
                    for (int layers = 2; layers <= 8; layers++) {
                        prepare(world, floor, source);
                        world.setBlockState(floor.offset(uphill).up(), source, 2);
                        IBlockState snow = Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers);
                        world.setBlockState(floor.up(), snow, 2);
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(world.getBlockState(floor.up()).equals(snow),
                                "deep snow remains occupied terrain");
                        checks++;
                    }

                    for (int lowerEdges = 1; lowerEdges <= 2; lowerEdges++) {
                        prepare(world, floor, source);
                        EnumFacing secondRise = uphill.rotateY();
                        world.setBlockState(floor.offset(uphill).up(), source, 2);
                        world.setBlockState(floor.offset(secondRise).up(), source, 2);
                        world.setBlockToAir(floor.offset(uphill.getOpposite()));
                        if (lowerEdges == 2) {
                            world.setBlockToAir(floor.offset(secondRise.getOpposite()));
                        }
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        // Terrain API directions are N/E/S/W, unlike Minecraft's horizontal index.
                        int stepDirection = direction(uphill);
                        int cornerDirection = corner(uphill, secondRise);
                        IBlockState expected = lowerEdges == 1 && level >= 2 ? material.step(stepDirection)
                                : lowerEdges == 2 && level == 3 ? material.corner(cornerDirection) : null;
                        RuntimeProbe.require(world.getBlockState(floor.up())
                                .equals(expected == null ? Blocks.AIR.getDefaultState() : expected),
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
        System.out.println("TERRAIN_SMOOTHER_OPPORTUNITIES_PASS checks=" + checks);
        return checks;
    }

    /** A small block-only sample from the reported mountain; no player or world metadata is included. */
    private static int snowyTerrace(WorldServer world) throws IOException {
        JsonObject fixture;
        InputStream input = OpportunityProbe.class.getResourceAsStream("/fixtures/snowy_stone_terrace.json");
        RuntimeProbe.require(input != null, "the mountain regression fixture is packaged with the probe");
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            fixture = new JsonParser().parse(reader).getAsJsonObject();
        }

        JsonArray savedPalette = fixture.getAsJsonArray("palette");
        IBlockState[] palette = new IBlockState[savedPalette.size()];
        for (int index = 0; index < palette.length; index++) {
            JsonObject entry = savedPalette.get(index).getAsJsonObject();
            Block block = Block.getBlockFromName(entry.get("block").getAsString());
            RuntimeProbe.require(block != null, "unknown block in the mountain regression fixture");
            palette[index] = block.getStateFromMeta(entry.get("meta").getAsInt());
        }

        BlockPos origin = new BlockPos(3768, 210, 3768);
        Chunk owner = world.getChunk(origin);
        int radius = fixture.get("radius").getAsInt();
        int minimumHeight = fixture.get("min_height").getAsInt();
        int maximumHeight = fixture.get("max_height").getAsInt();
        JsonArray blocks = fixture.getAsJsonArray("blocks");
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            prepare(world, origin, Blocks.STONE.getDefaultState());
            int index = 0;
            for (int height = minimumHeight; height <= maximumHeight; height++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                        world.setBlockState(origin.add(offsetX, height, offsetZ),
                                palette[blocks.get(index++).getAsInt()], 2);
                    }
                }
            }
            SkysTerrainSmoother.engine.smooth(world, owner, level);
            RuntimeProbe.require(MaterialCatalogue.instance().piece(world.getBlockState(origin.up()))
                    == MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState()),
                    "the reported snowy stone terrace receives its matching transition at level " + level);
            RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                    "the reported terrace stays stable on a repeated pass");
            checks += 2;
        }
        return checks;
    }

    private static int direction(EnumFacing facing) {
        return facing == EnumFacing.NORTH ? 0 : facing == EnumFacing.EAST ? 1
                : facing == EnumFacing.SOUTH ? 2 : 3;
    }

    private static int corner(EnumFacing first, EnumFacing second) {
        int firstEdge = zone.moddev.mc.skysterrainsmoother.internal.Footprint.EDGES[direction(first)];
        int secondEdge = zone.moddev.mc.skysterrainsmoother.internal.Footprint.EDGES[direction(second)];
        return Integer.numberOfTrailingZeros(firstEdge & secondEdge);
    }

    private static void prepare(WorldServer world, BlockPos floor, IBlockState source) {
        for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                for (int height = -2; height <= 6; height++) {
                    world.setBlockState(floor.add(offsetX, height, offsetZ),
                            height <= 0 ? source : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    private OpportunityProbe() {
    }
}

