package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Normal block-item placement, with matching horizontal halves joined before placement. */
public final class SandSlabItem extends BlockItem {
    private final SandSlabBlock slab;

    public SandSlabItem(SandSlabBlock slab) {
        super(slab, new Item.Properties().group(ItemGroup.BUILDING_BLOCKS));
        this.slab = slab;
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        World world = context.getWorld();
        BlockPos clicked = context.getPos();
        BlockState existing = world.getBlockState(clicked);
        Direction face = context.getFace();
        boolean exposed = existing.getBlock() == slab
                && (face == Direction.UP && existing.get(SandSlabBlock.TYPE) == SlabType.BOTTOM
                        || face == Direction.DOWN && existing.get(SandSlabBlock.TYPE) == SlabType.TOP);
        BlockPos target = exposed ? clicked : clicked.offset(face);
        existing = world.getBlockState(target);

        if (existing.getBlock() != slab) {
            return super.onItemUse(context);
        }

        PlayerEntity player = context.getPlayer();
        ItemStack held = context.getItem();
        BlockState full = slab.fullState();
        if (player == null || held.isEmpty() || !player.canPlayerEdit(target, face, held)
                || !world.checkNoEntityCollision(null, full.getCollisionShape(world, target)
                        .withOffset(target.getX(), target.getY(), target.getZ()))) {
            return ActionResultType.FAIL;
        }
        if (!world.setBlockState(target, full, 11)) {
            return ActionResultType.FAIL;
        }

        world.playSound(player, target, full.getSoundType(world, target, player).getPlaceSound(),
                SoundCategory.BLOCKS, 1, .8f);
        if (!player.abilities.isCreativeMode) {
            held.shrink(1);
        }
        return ActionResultType.SUCCESS;
    }
}
