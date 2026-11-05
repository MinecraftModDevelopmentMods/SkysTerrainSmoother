package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.resources.ResourceLocation;
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
@EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SandContent {
    private static final ResourceLocation SAND_SLAB = new ResourceLocation(SkysTerrainSmoother.ID, "sand_slab");
    private static final ResourceLocation RED_SAND_SLAB = new ResourceLocation(SkysTerrainSmoother.ID, "red_sand_slab");
    public static SandSlabBlock slab;
    public static SandSlabBlock redSlab;

    private SandContent() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            // Register the recovery contributor here, after the parallel mod constructors.
            SandLegacyRecovery.register();
            slab = new SandSlabBlock(false);
            redSlab = new SandSlabBlock(true);
            registry.register(SAND_SLAB, slab);
            registry.register(RED_SAND_SLAB, redSlab);
        });
        event.register(Registries.ITEM, registry -> {
            registry.register(SAND_SLAB, new SandSlabItem(slab));
            registry.register(RED_SAND_SLAB, new SandSlabItem(redSlab));
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
