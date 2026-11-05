package zone.moddev.mc.skysterrainsmoother.internal;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Runs once in the owning chunk, before plants can occupy its transition cells. */
public final class TerrainSmoothingFeature extends Feature<NoneFeatureConfiguration> {
    private static final String ID = "terrain_smoothing";
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, SkysTerrainSmoother.ID);
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, SkysTerrainSmoother.ID);
    private static final RegistryObject<TerrainSmoothingFeature> SMOOTHING =
            FEATURES.register(ID, TerrainSmoothingFeature::new);
    private static Holder<PlacedFeature> placed;

    static {
        BIOME_MODIFIERS.register(ID, () -> TerrainSmoothingBiomeModifier.CODEC);
    }

    private TerrainSmoothingFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    public static void register(BusGroup modBus) {
        FEATURES.register(modBus);
        BIOME_MODIFIERS.register(modBus);
    }

    public static synchronized void install() {
        if (placed != null) {
            return;
        }
        Holder<ConfiguredFeature<?, ?>> configured = Holder.direct(
                new ConfiguredFeature<>(SMOOTHING.get(), NoneFeatureConfiguration.INSTANCE));
        placed = Holder.direct(new PlacedFeature(configured, List.of()));
    }

    static Holder<PlacedFeature> placedFeature() {
        return placed;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        BlockPos origin = context.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (world instanceof WorldGenRegion) {
            WorldGenRegion region = (WorldGenRegion) world;
            chunkX = region.getCenter().x;
            chunkZ = region.getCenter().z;
        }
        return SkysTerrainSmoother.engine.generate(world, chunkX, chunkZ);
    }
}
