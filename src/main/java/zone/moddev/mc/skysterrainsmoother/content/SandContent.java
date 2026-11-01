package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.state.properties.SlabType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Sand colours have separate identities after flattening, including when a falling slab drops. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SandContent {
    public static SandSlabBlock slab;
    public static SandSlabBlock redSlab;

    private SandContent() {
    }

    public static void register() {
        slab = new SandSlabBlock(false);
        slab.setRegistryName(SkysTerrainSmoother.ID, "sand_slab");
        redSlab = new SandSlabBlock(true);
        redSlab.setRegistryName(SkysTerrainSmoother.ID, "red_sand_slab");
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(slab, redSlab);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(new SandSlabItem(slab).setRegistryName(slab.getRegistryName()),
                new SandSlabItem(redSlab).setRegistryName(redSlab.getRegistryName()));
    }

    /** The old palette used colour in bit one and the lower half in bit zero. */
    public static BlockState legacyState(int metadata) {
        SandSlabBlock block = (metadata & 2) == 0 ? slab : redSlab;
        SlabType half = (metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM;
        return block.getDefaultState().with(SandSlabBlock.TYPE, half);
    }
}
