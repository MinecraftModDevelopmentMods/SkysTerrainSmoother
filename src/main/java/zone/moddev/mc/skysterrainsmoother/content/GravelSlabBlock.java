package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Gravel has one material and two horizontal orientations; it never uses the sand palette. */
public final class GravelSlabBlock extends FallingSlabBlock {
    public GravelSlabBlock() {
        super("gravel_slab", .6f, SoundType.GROUND);
        setDefaultState(blockState.getBaseState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BlockSlab.HALF);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockSlab.HALF,
                (meta & 1) == 0 ? BlockSlab.EnumBlockHalf.TOP : BlockSlab.EnumBlockHalf.BOTTOM);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM ? 1 : 0;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState fullState(IBlockState state) {
        return Blocks.GRAVEL.getDefaultState();
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        items.add(new ItemStack(this));
    }
}
