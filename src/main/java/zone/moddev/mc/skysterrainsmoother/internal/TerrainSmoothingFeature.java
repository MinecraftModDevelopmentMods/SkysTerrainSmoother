package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import java.util.Random;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Runs once in the owning chunk, before plants can occupy its transition cells. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TerrainSmoothingFeature extends Feature<NoFeatureConfig> {
    public static final TerrainSmoothingFeature INSTANCE = create();
    private static ConfiguredFeature<?, ?> configured;

    public TerrainSmoothingFeature() {
        super(NoFeatureConfig.CODEC);
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
        configured = Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, id,
                INSTANCE.configured(NoFeatureConfig.INSTANCE)
                        .decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));
    }

    public static void onBiomeLoading(BiomeLoadingEvent event) {
        if (configured == null || event.getCategory() == Biome.Category.NETHER
                || event.getCategory() == Biome.Category.THEEND) {
            return;
        }
        List<Supplier<ConfiguredFeature<?, ?>>> vegetation = event.getGeneration().getFeatures(
                GenerationStage.Decoration.VEGETAL_DECORATION);
        for (Supplier<ConfiguredFeature<?, ?>> feature : vegetation) {
            if (feature.get() == configured) {
                return;
            }
        }
        vegetation.add(0, () -> configured);
    }

    @Override
    public boolean place(ISeedReader world, ChunkGenerator generator,
            Random random, BlockPos origin, NoFeatureConfig config) {
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (world instanceof WorldGenRegion) {
            WorldGenRegion region = (WorldGenRegion) world;
            chunkX = region.getCenterX();
            chunkZ = region.getCenterZ();
        }
        return SkysTerrainSmoother.engine.generate(world, chunkX, chunkZ);
    }
}
