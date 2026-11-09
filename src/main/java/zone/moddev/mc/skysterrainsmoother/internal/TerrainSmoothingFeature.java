package zone.moddev.mc.skysterrainsmoother.internal;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Runs once in the owning chunk, before plants can occupy its transition cells. */
public final class TerrainSmoothingFeature implements Feature {
    private static final String ID = "terrain_smoothing";
    private static final MapCodec<TerrainSmoothingFeature> CODEC = MapCodec.unit(TerrainSmoothingFeature::new);
    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
            DeferredRegister.create(Registries.FEATURE_TYPE, SkysTerrainSmoother.ID);
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, SkysTerrainSmoother.ID);
    private static Holder<PlacedFeature> placed;

    static {
        FEATURE_TYPES.register(ID, () -> CODEC);
        BIOME_MODIFIERS.register(ID, () -> TerrainSmoothingBiomeModifier.CODEC);
    }

    private TerrainSmoothingFeature() {
    }

    public static void register(BusGroup modBus) {
        FEATURE_TYPES.register(modBus);
        BIOME_MODIFIERS.register(modBus);
    }

    public static synchronized void install() {
        if (placed != null) {
            return;
        }
        Holder<Feature> feature = Holder.direct(new TerrainSmoothingFeature());
        placed = Holder.direct(new PlacedFeature(feature, List.of()));
    }

    static Holder<PlacedFeature> placedFeature() {
        return placed;
    }

    @Override
    public MapCodec<TerrainSmoothingFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (world instanceof WorldGenRegion) {
            WorldGenRegion region = (WorldGenRegion) world;
            chunkX = region.getCenter().x();
            chunkZ = region.getCenter().z();
        }
        return SkysTerrainSmoother.engine.generate(world, chunkX, chunkZ);
    }
}
