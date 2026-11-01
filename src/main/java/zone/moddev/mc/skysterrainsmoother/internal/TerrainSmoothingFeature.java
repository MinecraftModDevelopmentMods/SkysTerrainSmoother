package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.List;
import java.util.Random;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Runs once in the owning chunk, before plants can occupy its transition cells. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TerrainSmoothingFeature extends Feature<NoFeatureConfig> {
    public static final TerrainSmoothingFeature INSTANCE = create();
    private static ConfiguredFeature<?, ?> configured;

    public TerrainSmoothingFeature() {
        super(NoFeatureConfig::deserialize);
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
        configured = INSTANCE.withConfiguration(IFeatureConfig.NO_FEATURE_CONFIG)
                .withPlacement(Placement.NOPE.configure(new NoPlacementConfig()));
        for (Biome biome : ForgeRegistries.BIOMES.getValues()) {
            if (biome.getCategory() == Biome.Category.NETHER || biome.getCategory() == Biome.Category.THEEND) {
                continue;
            }
            List<ConfiguredFeature<?, ?>> vegetation = biome.getFeatures(GenerationStage.Decoration.VEGETAL_DECORATION);
            vegetation.add(0, configured);
        }
    }

    @Override
    public boolean place(IWorld world, ChunkGenerator<? extends GenerationSettings> generator,
            Random random, BlockPos origin, NoFeatureConfig config) {
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (world instanceof WorldGenRegion) {
            WorldGenRegion region = (WorldGenRegion) world;
            chunkX = region.getMainChunkX();
            chunkZ = region.getMainChunkZ();
        }
        return SkysTerrainSmoother.engine.generate(world, chunkX, chunkZ);
    }
}
