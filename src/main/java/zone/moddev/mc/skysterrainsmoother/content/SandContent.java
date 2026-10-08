package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

public final class SandContent {
    public static SandSlabBlock slab;
    private SandContent() { }
    public static void register() {
        slab=new SandSlabBlock(); GameRegistry.register(slab);
        GameRegistry.register(new SandSlabItem(slab).setRegistryName(slab.getRegistryName()));
    }
    public static void recipes() {
        for(int variant=0;variant<2;variant++) {
            GameRegistry.addShapedRecipe(new ItemStack(slab,6,variant*2),"SSS",'S',new ItemStack(Blocks.SAND,1,variant));
            GameRegistry.addShapedRecipe(new ItemStack(Blocks.SAND,1,variant),"SS",'S',new ItemStack(slab,1,variant*2));
        }
    }
}
