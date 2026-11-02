package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.Collections;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;

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

    /** An add-on's parallel setup callback may arrive before this mod's own callback. */
    public static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        TerrainSmootherConfig.activateSettingsForThisRun();
        vanilla("grass", Blocks.GRASS_BLOCK.defaultBlockState(), true,
                ModBlocks.GRASS_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        vanilla("dirt", Blocks.DIRT.defaultBlockState(), false,
                ModBlocks.DIRT_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        vanilla("stone", Blocks.STONE.defaultBlockState(), false, null);
        vanilla("granite", Blocks.GRANITE.defaultBlockState(), false, null);
        vanilla("diorite", Blocks.DIORITE.defaultBlockState(), false, null);
        vanilla("andesite", Blocks.ANDESITE.defaultBlockState(), false, null);
        vanilla("sandstone", Blocks.SANDSTONE.defaultBlockState(), false, null);
        vanilla("red_sandstone", Blocks.RED_SANDSTONE.defaultBlockState(), false, null);
        vanilla("hardened_clay", Blocks.TERRACOTTA.defaultBlockState(), false, null);
        for (int variant = 0; variant < 2; variant++) {
            Block sand = variant == 0 ? Blocks.SAND : Blocks.RED_SAND;
            MaterialCatalogue.instance().register(new TerrainMaterial(
                    new ResourceLocation("minecraft", variant == 0 ? "sand" : "red_sand"),
                    Collections.singleton(sand.defaultBlockState()),
                    SandContent.legacyState(variant * 2 + 1), null, null, false, null));
        }
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
            ResourceLocation fullId = new ResourceLocation("mineralogy", rock);
            Block full = ForgeRegistries.BLOCKS.getValue(fullId);
            Block slab = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mineralogy", rock + "_slab"));
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
        BlockState[] steps = new BlockState[4];
        BlockState[] corners = new BlockState[4];
        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
            steps[direction] = BuildingPiecesApi.bottomPiece(material,
                    BuildingPiecesApi.TerrainShape.STEP, DIRECTIONS[direction]);
            corners[direction] = BuildingPiecesApi.bottomPiece(material,
                    BuildingPiecesApi.TerrainShape.CORNER, DIRECTIONS[direction]);
        }
        MaterialCatalogue.instance().register(new TerrainMaterial(new ResourceLocation(material),
                Collections.singleton(source), slab, steps, corners, grass,
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
