package zone.moddev.mc.skysterrainsmoother.content;

import java.util.List;
import java.util.Random;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Two fixed sand slots and two horizontal halves; gravity retains the complete state. */
public final class SandSlabBlock extends BlockFalling {
    public SandSlabBlock() {
        super(Material.SAND);
        setRegistryName("skysterrainsmoother", "sand_slab");
        setUnlocalizedName("skysterrainsmoother.sand_slab");
        setDefaultState(blockState.getBaseState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.SAND)
                .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM));
        setHardness(.5f); setSoundType(SoundType.SAND); setLightOpacity(0);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS); useNeighborBrightness = true;
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BlockSand.VARIANT, BlockSlab.HALF); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockSand.VARIANT, (meta & 2) == 0 ? BlockSand.EnumType.SAND : BlockSand.EnumType.RED_SAND)
                .withProperty(BlockSlab.HALF, (meta & 1) == 0 ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockSand.VARIANT).getMetadata() * 2 + (state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM ? 1 : 0); }
    @Override public int damageDropped(IBlockState state) { return getMetaFromState(state) & 2; }
    @Override public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> items) { items.add(new ItemStack(item,1,0)); items.add(new ItemStack(item,1,2)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float x, float y, float z, int meta, EntityLivingBase placer, ItemStack stack) {
        return getStateFromMeta(meta).withProperty(BlockSlab.HALF, face == EnumFacing.DOWN || face != EnumFacing.UP && y > .5 ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return side == (state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP ? EnumFacing.UP : EnumFacing.DOWN); }
    @Override public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return isSideSolid(state,world,pos,side); }
    @Override public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return true; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP ? new AxisAlignedBB(0,.5,0,1,1,1) : new AxisAlignedBB(0,0,0,1,.5,1); }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, World world, BlockPos pos) { return getBoundingBox(state,world,pos); }
    @Override public String getHarvestTool(IBlockState state) { return "shovel"; }
    @Override protected ItemStack getSilkTouchDrop(IBlockState state) { return new ItemStack(this,1,damageDropped(state)); }
    @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote || !world.getBlockState(pos).equals(state) || pos.getY() <= 0 || !canFallThrough(world.getBlockState(pos.down()))) return;
        if (!fallInstantly && world.isAreaLoaded(pos.add(-32,-32,-32),pos.add(32,32,32))) {
            world.spawnEntity(new EntityFallingBlock(world,pos.getX()+.5,pos.getY(),pos.getZ()+.5,state));
        } else {
            // Vanilla's instant path uses a default state. Keep red sand and the half here.
            world.setBlockToAir(pos); BlockPos landing=pos.down();
            while (landing.getY() > 0 && canFallThrough(world.getBlockState(landing))) landing=landing.down();
            if (landing.getY() > 0) world.setBlockState(landing.up(),state,3);
        }
    }
}
