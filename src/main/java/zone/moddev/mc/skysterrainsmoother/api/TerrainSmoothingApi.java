package zone.moddev.mc.skysterrainsmoother.api;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModLoadingStage;
import zone.moddev.mc.skysterrainsmoother.internal.BuiltInMaterials;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Version 1 API for registration during initialization. Player settings take priority. */
public final class TerrainSmoothingApi {
    public static final int VERSION = 1;
    private static final MaterialCatalogue CATALOGUE = MaterialCatalogue.instance();

    private TerrainSmoothingApi() {
    }

    /** A restrictive rule; it cannot enable a material disabled by the player. */
    public interface BiomeRule {
        boolean allows(Identifier material, Biome biome);
    }

    /** A read-only veto. No world object is exposed and the engine performs all writes. */
    public interface AreaProtection {
        boolean protects(int dimension, BlockPos target, Biome biome);
    }

    private static void requireInitialization() {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        ModLoadingStage stage = container == null ? null : container.getCurrentState();
        if (stage != ModLoadingStage.COMMON_SETUP && stage != ModLoadingStage.SIDED_SETUP
                && stage != ModLoadingStage.ENQUEUE_IMC && stage != ModLoadingStage.PROCESS_IMC) {
            throw new IllegalStateException("Terrain extensions must register during initialization");
        }
        BuiltInMaterials.ensureRegistered();
    }

    /** Register exact full-block states and their pieces during initialization, before the catalogue freezes. */
    public static void registerMaterial(TerrainMaterial material) {
        requireInitialization();
        CATALOGUE.register(material);
    }

    /** Supply missing finer shapes without replacing the material's slab or existing shapes. */
    public static void addShapes(Identifier material, BlockState[] steps, BlockState[] corners) {
        requireInitialization();
        CATALOGUE.addShapes(material, steps, corners);
    }

    /** Restrict eligible biomes; an add-on cannot override a disabled player setting. */
    public static void addBiomeRule(BiomeRule rule) {
        requireInitialization();
        CATALOGUE.addBiomeRule(rule);
    }

    /** Add a read-only veto for protected areas; the engine remains responsible for world writes. */
    public static void addAreaProtection(AreaProtection rule) {
        requireInitialization();
        CATALOGUE.addAreaProtection(rule);
    }

    /** Allows an add-on to skip disabled or unavailable built-in material mappings. */
    public static boolean hasMaterial(Identifier materialId) {
        if (!CATALOGUE.frozen()) {
            requireInitialization();
        }
        return CATALOGUE.material(materialId) != null;
    }
}
