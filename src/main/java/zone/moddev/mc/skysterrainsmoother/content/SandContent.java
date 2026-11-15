package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(
        modid = "skysterrainsmoother")
public final class SandContent {
    public static SandSlabBlock slab;
    public static GravelSlabBlock gravelSlab;

    private SandContent() {
    }

    public static void register() {
        slab = new SandSlabBlock();
        gravelSlab = new GravelSlabBlock();
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(slab);
        event.getRegistry().register(gravelSlab);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new SandSlabItem(slab).setRegistryName(slab.getRegistryName()));
        event.getRegistry().register(new SandSlabItem(gravelSlab).setRegistryName(gravelSlab.getRegistryName()));
    }

    @SubscribeEvent
    public static void recipes(RegistryEvent.Register<IRecipe> event) {
        Ingredient gravel = Ingredient.fromStacks(new ItemStack(Blocks.GRAVEL));
        Ingredient gravelHalf = Ingredient.fromStacks(new ItemStack(gravelSlab));
        event.getRegistry().register(new ShapedRecipes("skysterrainsmoother/gravel_slab", 3, 1,
                NonNullList.from(Ingredient.EMPTY, gravel, gravel, gravel), new ItemStack(gravelSlab, 6))
                .setRegistryName("skysterrainsmoother", "gravel_slab"));
        event.getRegistry().register(new ShapedRecipes("skysterrainsmoother/gravel_full", 2, 1,
                NonNullList.from(Ingredient.EMPTY, gravelHalf, gravelHalf), new ItemStack(Blocks.GRAVEL))
                .setRegistryName("skysterrainsmoother", "gravel_from_slabs"));
        for (int variant = 0; variant < 2; variant++) {
            String name = variant == 0 ? "sand" : "red_sand";
            Ingredient full = Ingredient.fromStacks(new ItemStack(Blocks.SAND, 1, variant));
            Ingredient half = Ingredient.fromStacks(new ItemStack(slab, 1, variant * 2));

            event.getRegistry()
                    .register(new ShapedRecipes("skysterrainsmoother/" + name + "_slab", 3, 1,
                            NonNullList.from(Ingredient.EMPTY, full, full, full), new ItemStack(slab, 6, variant * 2))
                            .setRegistryName("skysterrainsmoother", name + "_slab"));
            event.getRegistry()
                    .register(new ShapedRecipes("skysterrainsmoother/" + name + "_full", 2, 1,
                            NonNullList.from(Ingredient.EMPTY, half, half), new ItemStack(Blocks.SAND, 1, variant))
                            .setRegistryName("skysterrainsmoother", name + "_from_slabs"));
        }
    }
}
