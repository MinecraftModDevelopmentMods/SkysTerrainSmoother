package zone.moddev.mc.skysterrainsmoother.internal;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo;

/** Puts the shared smoothing pass ahead of vegetation placement. */
final class TerrainSmoothingBiomeModifier implements BiomeModifier {
    static final MapCodec<TerrainSmoothingBiomeModifier> CODEC = MapCodec.unit(new TerrainSmoothingBiomeModifier());

    private TerrainSmoothingBiomeModifier() {
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, BiomeInfo.Builder builder) {
        Holder<PlacedFeature> smoothing = TerrainSmoothingFeature.placedFeature();
        if (phase != Phase.ADD || smoothing == null || biome.is(BiomeTags.IS_NETHER) || biome.is(BiomeTags.IS_END)) {
            return;
        }
        List<Holder<PlacedFeature>> vegetation = builder.getGenerationSettings()
                .getFeatures(GenerationStep.Decoration.VEGETAL_DECORATION);
        if (vegetation.stream().noneMatch(feature -> feature.value() == smoothing.value())) {
            vegetation.add(0, smoothing);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
