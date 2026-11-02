package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Empty neighbours for validating immutable piece collision shapes. */
final class EmptyAccess implements BlockGetter {
    static final EmptyAccess INSTANCE = new EmptyAccess();

    @Override
    public int getHeight() {
        return 256;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return Fluids.EMPTY.defaultFluidState();
    }
}
