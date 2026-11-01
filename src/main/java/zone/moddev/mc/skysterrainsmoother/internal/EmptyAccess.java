package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.IFluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

/** Empty neighbours for validating immutable piece collision shapes. */
final class EmptyAccess implements IBlockReader {
    static final EmptyAccess INSTANCE = new EmptyAccess();

    @Override
    public TileEntity getTileEntity(BlockPos position) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public IFluidState getFluidState(BlockPos position) {
        return Fluids.EMPTY.getDefaultState();
    }
}
