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
        RuntimeProbe.require(MaterialCatalogue.instance().source(Blocks.POWDER_SNOW.defaultBlockState()) == null,
                "powder snow keeps its native behaviour");
        SkysTerrainSmoother.logger.info("TERRAIN_SNOW_TERRAIN_PASS checks={}", checks + 3);
        return checks + 3;
    }

    private SnowTerrainProbe() {
    }
}
