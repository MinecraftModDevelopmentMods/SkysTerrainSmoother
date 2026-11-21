package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Half;

/** Cut one bucket of powder snow without consuming the bucket itself. */
public final class PowderSnowCuttingRecipe extends CustomRecipe {
    public PowderSnowCuttingRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level world) {
        int buckets = 0;
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty()) {
                if (!stack.is(Items.POWDER_SNOW_BUCKET)) {
                    return false;
                }
                buckets++;
            }
        }
        return buckets == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return getResultItem(registries);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return new ItemStack(PowderSnowContent.state(PowderSnowPieceBlock.Kind.SLAB,
                Half.BOTTOM, Direction.NORTH).getBlock(), 2);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            if (input.getItem(slot).is(Items.POWDER_SNOW_BUCKET)) {
                remainders.set(slot, new ItemStack(Items.BUCKET));
            }
        }
        return remainders;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.POWDER_SNOW_BUCKET));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public String getGroup() {
        return "skys/result/skysterrainsmoother/powder_snow_slab";
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PowderSnowContent.CUTTING;
    }
}
