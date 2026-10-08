package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSand;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Horizontal placement only. Matching halves normalize to the original sand. */
public final class SandSlabItem extends ItemBlock {
    public SandSlabItem(SandSlabBlock block) { super(block); setHasSubtypes(true); setMaxDamage(0); }
    @Override public int getMetadata(int damage) { return damage & 2; }
    @Override public String getUnlocalizedName(ItemStack stack) { return "tile.skysterrainsmoother." + ((stack.getItemDamage() & 2) == 0 ? "sand_slab" : "red_sand_slab"); }
    @Override public boolean canPlaceBlockOnSide(World world,BlockPos pos,EnumFacing side,EntityPlayer player,ItemStack stack) {
        if(world.getBlockState(pos).getBlock()==block||world.getBlockState(pos.offset(side)).getBlock()==block)return player.canPlayerEdit(pos,side,stack);
        return super.canPlaceBlockOnSide(world,pos,side,player,stack);
    }
    @Override public EnumActionResult onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z) {
        IBlockState state=world.getBlockState(pos);
        boolean exposed=state.getBlock()==block && (face==EnumFacing.UP && state.getValue(BlockSlab.HALF)==BlockSlab.EnumBlockHalf.BOTTOM || face==EnumFacing.DOWN && state.getValue(BlockSlab.HALF)==BlockSlab.EnumBlockHalf.TOP);
        BlockPos target=exposed?pos:pos.offset(face); state=world.getBlockState(target);
        if(state.getBlock()==block && ((SandSlabBlock)block).damageDropped(state)==getMetadata(stack.getItemDamage())) {
            if(stack.stackSize<=0||!player.canPlayerEdit(target,face,stack)||!world.checkNoEntityCollision(Block.FULL_BLOCK_AABB.offset(target)))return EnumActionResult.FAIL;
            IBlockState full=Blocks.SAND.getDefaultState().withProperty(BlockSand.VARIANT,state.getValue(BlockSand.VARIANT));
            if(!world.setBlockState(target,full,11))return EnumActionResult.FAIL;
            world.playSound(player,target,SoundEvents.BLOCK_SAND_PLACE,SoundCategory.BLOCKS,1,.8f);
            if(!player.capabilities.isCreativeMode)--stack.stackSize;
            return EnumActionResult.SUCCESS;
        }
        return super.onItemUse(stack,player,world,pos,hand,face,x,y,z);
    }
}
