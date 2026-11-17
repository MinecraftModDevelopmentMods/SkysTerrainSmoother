package zone.moddev.mc.skysterrainsmoother.content;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.RegisterEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Powder snow keeps its own pieces so smoothing does not turn a hazard into solid snow. */
public final class PowderSnowContent {
    private static final Map<PowderSnowPieceBlock.Kind, PowderSnowPieceBlock> BLOCKS =
            new EnumMap<>(PowderSnowPieceBlock.Kind.class);

    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, registry -> registry.register(
                Identifier.fromNamespaceAndPath(SkysTerrainSmoother.ID, "powder_snow_cutting"),
                PowderSnowCuttingRecipe.SERIALIZER));
        event.register(Registries.BLOCK, registry -> {
            for (var kind : PowderSnowPieceBlock.Kind.values()) {
                var block = new PowderSnowPieceBlock(kind);
                BLOCKS.put(kind, block);
                registry.register(id(kind), block);
            }
        });
        event.register(Registries.ITEM, registry -> {
            for (var entry : BLOCKS.entrySet()) {
                Item.Properties properties = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(entry.getKey())));
                registry.register(id(entry.getKey()), new BlockItem(entry.getValue(), properties));
            }
        });
    }

    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.BUILDING_BLOCKS)) {
            BLOCKS.values().forEach(event::accept);
        }
    }

    static Identifier id(PowderSnowPieceBlock.Kind kind) {
        return Identifier.fromNamespaceAndPath(SkysTerrainSmoother.ID, "powder_snow_" + kind.getSerializedName());
    }

    public static BlockState state(PowderSnowPieceBlock.Kind kind, Half half, Direction facing) {
        return BLOCKS.get(kind).defaultBlockState().setValue(PowderSnowPieceBlock.HALF, half)
                .setValue(PowderSnowPieceBlock.FACING, facing);
    }

    private PowderSnowContent() {
    }
}
