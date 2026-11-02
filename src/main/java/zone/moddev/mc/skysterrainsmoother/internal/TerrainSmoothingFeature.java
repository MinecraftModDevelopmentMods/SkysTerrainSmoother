package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Runs once in the owning chunk, before plants can occupy its transition cells. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TerrainSmoothingFeature extends Feature<NoneFeatureConfiguration> {
    public static final TerrainSmoothingFeature INSTANCE = create();
    private static ConfiguredFeature<?, ?> configured;
    private static Holder<PlacedFeature> placed;

    public TerrainSmoothingFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static TerrainSmoothingFeature create() {
        TerrainSmoothingFeature feature = new TerrainSmoothingFeature();
        feature.setRegistryName(new ResourceLocation(SkysTerrainSmoother.ID, "terrain_smoothing"));
        return feature;
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Feature<?>> event) {
        event.getRegistry().register(INSTANCE);
    }

    public static synchronized void install() {
        if (configured != null) {
            return;
        }
        ResourceLocation id = new ResourceLocation(SkysTerrainSmoother.ID, "terrain_smoothing");
        configured = new ConfiguredFeature<>(INSTANCE, NoneFeatureConfiguration.INSTANCE);
        Holder<ConfiguredFeature<?, ?>> configuration = BuiltinRegistries.register(
                BuiltinRegistries.CONFIGURED_FEATURE, id, configured);
        placed = BuiltinRegistries.register(BuiltinRegistries.PLACED_FEATURE, id,
                new PlacedFeature(configuration, java.util.Collections.emptyList()));
    }

    public static void onBiomeLoading(BiomeLoadingEvent event) {
        if (placed == null || event.getCategory() == Biome.BiomeCategory.NETHER
                || event.getCategory() == Biome.BiomeCategory.THEEND) {
            return;
        }
        List<Holder<PlacedFeature>> vegetation = event.getGeneration().getFeatures(
                GenerationStep.Decoration.VEGETAL_DECORATION);
        for (Holder<PlacedFeature> feature : vegetation) {
            if (feature.value() == placed.value()) {
                return;
            }
        }
        vegetation.add(0, placed);
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
