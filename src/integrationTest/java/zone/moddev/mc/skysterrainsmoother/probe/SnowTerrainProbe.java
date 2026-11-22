package zone.moddev.mc.skysterrainsmoother.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Covers the raised snow neighbours from the reported world and their normal block updates. */
final class SnowTerrainProbe {
    static int run(ServerLevel world) throws Exception {
        BlockPos target = new BlockPos(4680, 210, 4680);
        try (var input = SnowTerrainProbe.class.getResourceAsStream("/fixtures/reported_snow_gap.json")) {
            if (input == null) {
                throw new IllegalStateException("The reported snow fixture is missing");
            }
            JsonObject fixture = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement entry : fixture.getAsJsonArray("blocks")) {
                JsonObject block = entry.getAsJsonObject();
                var offset = block.getAsJsonArray("offset");
                BlockPos position = target.offset(offset.get(0).getAsInt(), offset.get(1).getAsInt(),
                        offset.get(2).getAsInt());
                JsonObject state = block.getAsJsonObject("state");
                CompoundTag tag = new CompoundTag();
                tag.putString("Name", state.get("Name").getAsString());
                if (state.has("Properties")) {
                    CompoundTag properties = new CompoundTag();
                    state.getAsJsonObject("Properties").entrySet().forEach(property ->
                            properties.putString(property.getKey(), property.getValue().getAsString()));
                    tag.put("Properties", properties);
                }
                world.setBlock(position, NbtUtils.readBlockState(world.holderLookup(Registries.BLOCK), tag), 2);
            }
        }
        ProbeWorld.light(world, LightLayer.SKY, target, 15);
        BlockState slab = world.getBlockState(target);
        int loaded = world.getChunkSource().getLoadedChunksCount();
        int checks = 0;
        for (BlockState dry : new BlockState[] { slab.setValue(SnowyDirtBlock.SNOWY, false),
                BuildingPiecesApi.bottomPiece("minecraft:grass", BuildingPiecesApi.TerrainShape.STEP, Direction.NORTH),
                BuildingPiecesApi.bottomPiece("minecraft:grass", BuildingPiecesApi.TerrainShape.CORNER, Direction.NORTH) }) {
            world.setBlock(target.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            world.setBlock(target, dry, 2);
            BlockState snowy = dry.updateShape(Direction.SOUTH, world.getBlockState(target.south()), world,
                    target, target.south());
            RuntimeProbe.require(snowy.getValue(SnowyDirtBlock.SNOWY),
                    "a raised snow-covered neighbour gives the reported piece its snow cap: " + dry);
            world.getChunkAt(target).setBlockState(target, dry, false);
            dry.randomTick(world, target, RandomSource.create(1));
            RuntimeProbe.require(world.getBlockState(target).getValue(SnowyDirtBlock.SNOWY),
                    "normal random ticks keep the snow cap beside the raised snow: " + dry);
            checks += 2;
        }
        RuntimeProbe.require(world.getChunkSource().getLoadedChunksCount() == loaded,
                "snow appearance does not load chunks");
        RuntimeProbe.require(MaterialCatalogue.instance().source(Blocks.SNOW_BLOCK.defaultBlockState()) != null,
                "full snow blocks have a registered smoothing material");
        RuntimeProbe.require(MaterialCatalogue.instance().source(Blocks.POWDER_SNOW.defaultBlockState()) != null,
                "powder snow has its own non-solid smoothing pieces");
        checks += coldSurfaces(world);
        SkysTerrainSmoother.logger.info("TERRAIN_SNOW_TERRAIN_PASS checks={}", checks + 3);
        return checks + 3;
    }

    private static int coldSurfaces(ServerLevel world) {
        BlockPos floor = new BlockPos(4744, 210, 4744);
        BlockPos target = floor.above();
        var owner = world.getChunkAt(floor);
        var originalBiomes = new java.util.HashMap<BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>();
        int firstQuartX = owner.getPos().x * 4;
        int firstQuartZ = owner.getPos().z * 4;
        for (int quartY = world.getMinBuildHeight() >> 2; quartY < world.getMaxBuildHeight() >> 2; quartY++) {
            for (int quartZ = firstQuartZ; quartZ < firstQuartZ + 4; quartZ++) {
                for (int quartX = firstQuartX; quartX < firstQuartX + 4; quartX++) {
                    originalBiomes.put(new BlockPos(quartX, quartY, quartZ), owner.getNoiseBiome(quartX, quartY, quartZ));
                }
            }
        }
        var sampler = world.getChunkSource().randomState().sampler();
        var registry = world.registryAccess().registryOrThrow(Registries.BIOME);
        var cold = registry.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        var warm = registry.getHolderOrThrow(Biomes.DESERT);
        int checks = 0;
        try {
            owner.fillBiomesFromNoise((quartX, quartY, quartZ, climate) -> cold, sampler);
            for (int level = 1; level <= 3; level++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    for (int offsetX = -3; offsetX <= 3; offsetX++) {
                        BlockPos column = floor.offset(offsetX, 0, offsetZ);
                        for (int height = 1; column.getY() + height < world.getMaxBuildHeight(); height++) {
                            ProbeWorld.clear(world, column.above(height));
                        }
                        world.setBlock(column, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        world.setBlock(column.below(), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                world.setBlock(level == 3 ? target.north().west() : target.north(),
                        Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);
                BlockState piece = world.getBlockState(target);
                RuntimeProbe.require(piece.hasProperty(SnowyDirtBlock.SNOWY)
                                && piece.getValue(SnowyDirtBlock.SNOWY),
                        "exposed cold transitions receive snow without a supported snow layer at level " + level);
                piece.randomTick(world, target, RandomSource.create(1));
                RuntimeProbe.require(world.getBlockState(target).getValue(SnowyDirtBlock.SNOWY),
                        "normal random ticks retain the exposed cold snow cap");

                world.setBlock(target.above(3), Blocks.STONE.defaultBlockState(), 2);
                piece.randomTick(world, target, RandomSource.create(1));
                RuntimeProbe.require(!world.getBlockState(target).getValue(SnowyDirtBlock.SNOWY),
                        "sheltered pieces keep their green appearance");
                ProbeWorld.clear(world, target.above(3));
                owner.fillBiomesFromNoise((quartX, quartY, quartZ, climate) -> warm, sampler);
                world.getBlockState(target).randomTick(world, target, RandomSource.create(1));
                RuntimeProbe.require(!world.getBlockState(target).getValue(SnowyDirtBlock.SNOWY),
                        "exposed warm surfaces keep their green appearance");
                owner.fillBiomesFromNoise((quartX, quartY, quartZ, climate) -> cold, sampler);
                world.setBlock(target.east(), Blocks.GLOWSTONE.defaultBlockState(), 2);
                ProbeWorld.light(world, LightLayer.BLOCK, target, 15);
                RuntimeProbe.require(world.getBrightness(LightLayer.BLOCK, target.above()) >= 10,
                        "the lighting fixture is bright enough to prevent snowfall");
                world.getBlockState(target).randomTick(world, target, RandomSource.create(1));
                RuntimeProbe.require(!world.getBlockState(target).getValue(SnowyDirtBlock.SNOWY),
                        "bright block light keeps the cold surface green");
                ProbeWorld.clear(world, target.east());
                ProbeWorld.light(world, LightLayer.BLOCK, target, 0);
                checks += 6;
            }
        } finally {
            owner.fillBiomesFromNoise((quartX, quartY, quartZ, climate) ->
                    originalBiomes.get(new BlockPos(quartX, quartY, quartZ)), sampler);
        }
        return checks;
    }

    private SnowTerrainProbe() {
    }
}
