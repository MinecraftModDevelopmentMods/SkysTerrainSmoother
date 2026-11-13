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

/** The two permanent sand slots keep their original material and half bits. */
public final class SandSlabBlock extends FallingSlabBlock {
    public SandSlabBlock() {
        super("sand_slab", .5f, SoundType.SAND);
        setDefaultState(blockState.getBaseState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.SAND)
                .withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM));
    }

    @Override
    public IBlockState fullState(IBlockState state) {
        return Blocks.SAND.getDefaultState().withProperty(BlockSand.VARIANT, state.getValue(BlockSand.VARIANT));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BlockSand.VARIANT, BlockSlab.HALF);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(BlockSand.VARIANT,
                        (meta & 2) == 0 ? BlockSand.EnumType.SAND : BlockSand.EnumType.RED_SAND)
                .withProperty(BlockSlab.HALF,
                        (meta & 1) == 0 ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockSand.VARIANT).getMetadata() * 2
                + (state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM ? 1 : 0);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state) & 2;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        items.add(new ItemStack(this, 1, 0));
        items.add(new ItemStack(this, 1, 2));
    }

}
