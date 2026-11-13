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

/** Horizontal falling pieces retain their material and orientation in both gravity paths. */
public abstract class FallingSlabBlock extends BlockFalling {
    protected FallingSlabBlock(String name, float hardness, SoundType sound) {
        super(Material.SAND);
        setRegistryName("skysterrainsmoother", name);
        setTranslationKey("skysterrainsmoother." + name);
        setHardness(hardness);
        setSoundType(sound);
        setLightOpacity(0);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        useNeighborBrightness = true;
    }

    public abstract IBlockState fullState(IBlockState state);

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float hitX, float hitY,
            float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return getStateFromMeta(meta).withProperty(BlockSlab.HALF,
                face == EnumFacing.DOWN || face != EnumFacing.UP && hitY > .5 ? BlockSlab.EnumBlockHalf.TOP
                        : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == (state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP ? EnumFacing.UP
                : EnumFacing.DOWN);
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return isSideSolid(state, world, pos, side);
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP ? new AxisAlignedBB(0, .5, 0, 1, 1, 1)
                : new AxisAlignedBB(0, 0, 0, 1, .5, 1);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public String getHarvestTool(IBlockState state) {
        return "shovel";
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this, 1, damageDropped(state));
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote || !world.getBlockState(pos).equals(state) || pos.getY() <= 0
                || !canFallThrough(world.getBlockState(pos.down()))) {
            return;
        }
        if (!fallInstantly && world.isAreaLoaded(pos.add(-32, -32, -32), pos.add(32, 32, 32))) {
            world.spawnEntity(new EntityFallingBlock(world, pos.getX() + .5, pos.getY(), pos.getZ() + .5, state));
        } else {
            // Vanilla's instant path uses a default state. Keep red sand and the half here.
            world.setBlockToAir(pos);

            BlockPos landing = pos.down();

            while (landing.getY() > 0 && canFallThrough(world.getBlockState(landing))) {
                landing = landing.down();
            }
            if (landing.getY() > 0) {
                world.setBlockState(landing.up(), state, 3);
            }
        }
    }
}
