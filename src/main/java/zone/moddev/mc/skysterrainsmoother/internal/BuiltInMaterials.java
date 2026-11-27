package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.CoarseDirtSlab;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowContent;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;
import net.minecraft.world.level.block.state.properties.Half;

/** Exact natural surfaces; ores and decorative variants are never inferred from tags. */
public final class BuiltInMaterials {
    private static final String[] ROCKS = {
            "basalt", "rhyolite", "basaltic_glass", "scoria", "tuff", "pumice",
            "pegmatite", "diabase", "gabbro", "peridotite", "shale", "conglomerate",
            "dolomite", "limestone", "siltstone", "rock_salt", "chert", "gypsum",
            "chalk", "marble", "slate", "schist", "gneiss", "phyllite", "amphibolite",
            "hornfels", "quartzite", "novaculite"
    };
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static boolean registered;

    private BuiltInMaterials() {
    }

    /** Install the built-in mappings before the ordered add-on setup callbacks. */
    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        TerrainSmootherConfig.activateSettingsForThisRun();
        vanilla("grass", Blocks.GRASS_BLOCK.defaultBlockState(), true,
                ModBlocks.GRASS_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        vanilla("dirt", Blocks.DIRT.defaultBlockState(), false,
                ModBlocks.DIRT_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        vanilla("dirt_podzol", Blocks.PODZOL.defaultBlockState(), false, null);
        vanilla("dirt_coarse", Blocks.COARSE_DIRT.defaultBlockState(), false, CoarseDirtSlab.registeredBlock()
                .defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        vanilla("dirt_mycelium", Blocks.MYCELIUM.defaultBlockState(), false, null);
        vanilla("rooted_dirt", Blocks.ROOTED_DIRT.defaultBlockState(), false, null);
        vanilla("mud", Blocks.MUD.defaultBlockState(), false, null);
        vanilla("clay", Blocks.CLAY.defaultBlockState(), false, null);
        vanilla("deepslate", Blocks.DEEPSLATE.defaultBlockState(), false, null);
        vanilla("tuff", Blocks.TUFF.defaultBlockState(), false, null);
        vanilla("calcite", Blocks.CALCITE.defaultBlockState(), false, null);
        vanilla("dripstone_block", Blocks.DRIPSTONE_BLOCK.defaultBlockState(), false, null);
        vanilla("stone", Blocks.STONE.defaultBlockState(), false, null);
        vanilla("granite", Blocks.GRANITE.defaultBlockState(), false, null);
        vanilla("diorite", Blocks.DIORITE.defaultBlockState(), false, null);
        vanilla("andesite", Blocks.ANDESITE.defaultBlockState(), false, null);
        vanilla("sandstone", Blocks.SANDSTONE.defaultBlockState(), false, null);
        vanilla("red_sandstone", Blocks.RED_SANDSTONE.defaultBlockState(), false, null);
        vanilla("hardened_clay", Blocks.TERRACOTTA.defaultBlockState(), false, null);
        for (DyeColor colour : DyeColor.values()) {
            String name = colour.getName();
            Block terracotta = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name + "_terracotta"));
            String pieceColour = colour == DyeColor.LIGHT_GRAY ? "silver" : name;
            vanilla("stained_hardened_clay_" + pieceColour, terracotta.defaultBlockState(), false, null);
        }
        vanilla("snow", Blocks.SNOW_BLOCK.defaultBlockState(), false, null);
        vanilla("packed_ice", Blocks.PACKED_ICE.defaultBlockState(), false, null);
        vanilla("ice", Blocks.ICE.defaultBlockState(), false, null);
        vanilla("blue_ice", Blocks.BLUE_ICE.defaultBlockState(), false, null);
        BlockState[] powderSteps = new BlockState[4];
        BlockState[] powderCorners = new BlockState[4];
        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
            powderSteps[direction] = PowderSnowContent.state(PowderSnowPieceBlock.Kind.STEP,
                    Half.BOTTOM, DIRECTIONS[direction]);
            powderCorners[direction] = PowderSnowContent.state(PowderSnowPieceBlock.Kind.CORNER,
                    Half.BOTTOM, DIRECTIONS[direction]);
        }
        MaterialCatalogue.instance().register(new TerrainMaterial(ResourceLocation.withDefaultNamespace("powder_snow"),
                Collections.singleton(Blocks.POWDER_SNOW.defaultBlockState()),
                PowderSnowContent.state(PowderSnowPieceBlock.Kind.SLAB, Half.BOTTOM, Direction.NORTH),
                powderSteps, powderCorners, false, null));
        for (int variant = 0; variant < 2; variant++) {
            Block sand = variant == 0 ? Blocks.SAND : Blocks.RED_SAND;
            MaterialCatalogue.instance().register(new TerrainMaterial(
                    ResourceLocation.fromNamespaceAndPath("minecraft", variant == 0 ? "sand" : "red_sand"),
                    Collections.singleton(sand.defaultBlockState()),
                    SandContent.legacyState(variant * 2 + 1), null, null, false, null));
        }
        MaterialCatalogue.instance().register(new TerrainMaterial(ResourceLocation.withDefaultNamespace("gravel"),
                Collections.singleton(Blocks.GRAVEL.defaultBlockState()),
                SandContent.gravelSlab.defaultBlockState(), null, null, false, null));
        registerMineralogy();
        registered = true;
    }

    private static void registerMineralogy() {
        if (!TerrainSmootherConfig.mineralogy() || !ModList.get().isLoaded("mineralogy")) {
            return;
        }
        int available = 0;
        int missing = 0;
        for (String rock : ROCKS) {
            ResourceLocation fullId = ResourceLocation.fromNamespaceAndPath("mineralogy", rock);
            Block full = BuiltInRegistries.BLOCK.get(fullId);
            Block slab = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mineralogy", rock + "_slab"));
            if (full == null || full == Blocks.AIR || slab == null || slab == Blocks.AIR) {
                missing++;
                continue;
            }
            MaterialCatalogue.instance().register(new TerrainMaterial(fullId,
                    Collections.singleton(full.defaultBlockState()), bottom(slab.defaultBlockState()),
                    mineralogyShapes(fullId.toString(), BuildingPiecesApi.TerrainShape.STEP),
                    mineralogyShapes(fullId.toString(), BuildingPiecesApi.TerrainShape.CORNER), false, null));
            available++;
        }
        SkysTerrainSmoother.logger.info(
                "Mineralogy smoothing: {} natural rocks available, {} unavailable slab forms skipped",
                available, missing);
    }

    private static void vanilla(String name, BlockState source, boolean grass, BlockState slab) {
        String material = "minecraft:" + name;
        if (slab == null) {
            slab = BuildingPiecesApi.bottomPiece(material, BuildingPiecesApi.TerrainShape.SLAB, Direction.NORTH);
        }
        if (slab == null) {
            throw new IllegalStateException("Missing Building Pieces slab for " + material);
        }
        BlockState[] steps = new BlockState[4];
        BlockState[] corners = new BlockState[4];
        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
            steps[direction] = BuildingPiecesApi.bottomPiece(material,
                    BuildingPiecesApi.TerrainShape.STEP, DIRECTIONS[direction]);
            corners[direction] = BuildingPiecesApi.bottomPiece(material,
                    BuildingPiecesApi.TerrainShape.CORNER, DIRECTIONS[direction]);
        }
        // Deepslate generates along each axis. All three states represent the same natural rock.
        var sources = source.is(Blocks.DEEPSLATE) ? new LinkedHashSet<>(Blocks.DEEPSLATE.getStateDefinition().getPossibleStates())
                : Collections.singleton(source);
        ResourceLocation identifier = source.is(Blocks.COARSE_DIRT) ? ResourceLocation.withDefaultNamespace("coarse_dirt")
                : ResourceLocation.parse(material);
        MaterialCatalogue.instance().register(new TerrainMaterial(identifier,
                sources, slab, steps, corners, grass,
                grass ? Blocks.DIRT.defaultBlockState() : null));
    }

    private static BlockState[] mineralogyShapes(String material, BuildingPiecesApi.TerrainShape shape) {
        BlockState[] pieces = new BlockState[DIRECTIONS.length];
        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
            pieces[direction] = BuildingPiecesApi.bottomPiece(material, shape, DIRECTIONS[direction]);
            if (pieces[direction] == null) {
                return null;
            }
        }
        return pieces;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState bottom(BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE)) {
            return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        for (Property property : state.getProperties()) {
            if (property.getName().equals("facing") && property.getPossibleValues().contains(Direction.UP)) {
                return state.setValue(property, Direction.UP);
            }
        }
        throw new IllegalStateException("Unsupported Mineralogy slab orientation: " + state);
    }
}
