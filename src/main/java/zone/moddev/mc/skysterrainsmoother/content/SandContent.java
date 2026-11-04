package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Sand colours have separate identities after flattening, including when a falling slab drops. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SandContent {
    private static final ResourceLocation SAND_SLAB = new ResourceLocation(SkysTerrainSmoother.ID, "sand_slab");
    private static final ResourceLocation RED_SAND_SLAB = new ResourceLocation(SkysTerrainSmoother.ID, "red_sand_slab");
    public static SandSlabBlock slab;
    public static SandSlabBlock redSlab;

    private SandContent() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, registry -> {
            // Register the recovery contributor here, after the parallel mod constructors.
            SandLegacyRecovery.register();
            slab = new SandSlabBlock(false);
            redSlab = new SandSlabBlock(true);
            registry.register(SAND_SLAB, slab);
            registry.register(RED_SAND_SLAB, redSlab);
        });
        event.register(ForgeRegistries.Keys.ITEMS, registry -> {
            registry.register(SAND_SLAB, new SandSlabItem(slab));
            registry.register(RED_SAND_SLAB, new SandSlabItem(redSlab));
        });
    }

    @SubscribeEvent
    public static void buildCreativeTab(CreativeModeTabEvent.BuildContents event) {
        if (event.getTab() == CreativeModeTabs.BUILDING_BLOCKS) {
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
