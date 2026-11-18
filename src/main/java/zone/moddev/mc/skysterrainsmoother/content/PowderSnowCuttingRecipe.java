package zone.moddev.mc.skysterrainsmoother.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/** Cut one bucket of powder snow and return its empty bucket. */
public final class PowderSnowCuttingRecipe extends CustomRecipe {
    private static final PowderSnowCuttingRecipe INSTANCE = new PowderSnowCuttingRecipe();
    public static final RecipeSerializer<PowderSnowCuttingRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.<RegistryFriendlyByteBuf, PowderSnowCuttingRecipe>unit(INSTANCE));

    @Override
    public boolean matches(CraftingInput input, Level world) {
        int buckets = 0;
        for (int slot = 0; slot < input.size(); slot++) {
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
    public ItemStack assemble(CraftingInput input) {
        return result();
    }

    private static ItemStack result() {
        return new ItemStack(PowderSnowContent.state(PowderSnowPieceBlock.Kind.SLAB,
                Half.BOTTOM, Direction.NORTH).getBlock(), 2);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(Items.POWDER_SNOW_BUCKET)) {
                remainders.set(slot, new ItemStack(Items.BUCKET));
            }
        }
        return remainders;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(Ingredient.of(Items.POWDER_SNOW_BUCKET));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapelessCraftingRecipeDisplay(List.of(Ingredient.of(Items.POWDER_SNOW_BUCKET).display()),
                new SlotDisplay.ItemStackSlotDisplay(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result())),
                Ingredient.of(Blocks.CRAFTING_TABLE).display()));
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.BUILDING;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public String group() {
        return "skys/result/skysterrainsmoother/powder_snow_slab";
    }

    @Override
    public RecipeSerializer<PowderSnowCuttingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
