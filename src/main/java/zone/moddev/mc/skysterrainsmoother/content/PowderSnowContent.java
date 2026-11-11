package zone.moddev.mc.skysterrainsmoother.content;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Natural powder snow needs its own pieces; solid snow would remove the hazard. */
@EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PowderSnowContent {
    public static final RecipeSerializer<PowderSnowCuttingRecipe> CUTTING =
            new SimpleCraftingRecipeSerializer<>(PowderSnowCuttingRecipe::new);
    private static final Map<PowderSnowPieceBlock.Kind, PowderSnowPieceBlock> BLOCKS =
            new EnumMap<>(PowderSnowPieceBlock.Kind.class);

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, registry -> registry.register(
                ResourceLocation.fromNamespaceAndPath(SkysTerrainSmoother.ID, "powder_snow_cutting"), CUTTING));
        event.register(Registries.BLOCK, registry -> {
            for (var kind : PowderSnowPieceBlock.Kind.values()) {
                var block = new PowderSnowPieceBlock(kind);
                BLOCKS.put(kind, block);
                registry.register(id(kind), block);
            }
        });
        event.register(Registries.ITEM, registry -> {
            for (var entry : BLOCKS.entrySet()) {
                registry.register(id(entry.getKey()), new BlockItem(entry.getValue(), new Item.Properties()));
            }
        });
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.BUILDING_BLOCKS)) {
            BLOCKS.values().forEach(event::accept);
        }
    }

    private static ResourceLocation id(PowderSnowPieceBlock.Kind kind) {
        return ResourceLocation.fromNamespaceAndPath(SkysTerrainSmoother.ID,
                "powder_snow_" + kind.getSerializedName());
    }

    public static BlockState state(PowderSnowPieceBlock.Kind kind, Half half, Direction facing) {
        return BLOCKS.get(kind).defaultBlockState().setValue(PowderSnowPieceBlock.HALF, half)
                .setValue(PowderSnowPieceBlock.FACING, facing);
    }

    private PowderSnowContent() {
    }
}
