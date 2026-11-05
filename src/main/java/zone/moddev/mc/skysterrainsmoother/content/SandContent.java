package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Sand colours have separate identities after flattening, including when a falling slab drops. */
@EventBusSubscriber(modid = SkysTerrainSmoother.ID)
public final class SandContent {
    private static final Identifier SAND_SLAB = Identifier.fromNamespaceAndPath(SkysTerrainSmoother.ID, "sand_slab");
    private static final Identifier RED_SAND_SLAB = Identifier.fromNamespaceAndPath(SkysTerrainSmoother.ID, "red_sand_slab");
    public static SandSlabBlock slab;
    public static SandSlabBlock redSlab;

    private SandContent() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            // Register the recovery contributor here, after the parallel mod constructors.
            SandLegacyRecovery.register();
            slab = new SandSlabBlock(false, SAND_SLAB);
            redSlab = new SandSlabBlock(true, RED_SAND_SLAB);
            registry.register(SAND_SLAB, slab);
            registry.register(RED_SAND_SLAB, redSlab);
        });
        event.register(Registries.ITEM, registry -> {
            registry.register(SAND_SLAB, new SandSlabItem(slab, SAND_SLAB));
            registry.register(RED_SAND_SLAB, new SandSlabItem(redSlab, RED_SAND_SLAB));
        });
    }

    @SubscribeEvent
    public static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.BUILDING_BLOCKS.equals(event.getTabKey())) {
            event.accept(slab);
            event.accept(redSlab);
        }
    }

    /** The old palette used colour in bit one and the lower half in bit zero. */
    public static BlockState legacyState(int metadata) {
        SandSlabBlock block = (metadata & 2) == 0 ? slab : redSlab;
        SlabType half = (metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM;
        return block.defaultBlockState().setValue(SandSlabBlock.TYPE, half);
    }
}
