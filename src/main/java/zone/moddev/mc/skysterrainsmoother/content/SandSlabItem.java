package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Normal block-item placement, with matching horizontal halves joined before placement. */
public final class SandSlabItem extends BlockItem {
    private final SandSlabBlock slab;

    public SandSlabItem(SandSlabBlock slab, Identifier id) {
        super(slab, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)));
        this.slab = slab;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState existing = world.getBlockState(clicked);
        Direction face = context.getClickedFace();
        boolean exposed = existing.getBlock() == slab
                && (face == Direction.UP && existing.getValue(SandSlabBlock.TYPE) == SlabType.BOTTOM
                        || face == Direction.DOWN && existing.getValue(SandSlabBlock.TYPE) == SlabType.TOP);
        BlockPos target = exposed ? clicked : clicked.relative(face);
        existing = world.getBlockState(target);

        if (existing.getBlock() != slab) {
            return super.useOn(context);
        }

        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        BlockState full = slab.fullState();
        if (player == null || held.isEmpty() || !player.mayUseItemAt(target, face, held)
                || !world.isUnobstructed(null, full.getCollisionShape(world, target)
                        .move(target.getX(), target.getY(), target.getZ()))) {
            return InteractionResult.FAIL;
        }
        if (!world.setBlock(target, full, 11)) {
            return InteractionResult.FAIL;
        }

        world.playSound(player, target, full.getSoundType(world, target, player).getPlaceSound(),
                SoundSource.BLOCKS, 1, .8f);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
