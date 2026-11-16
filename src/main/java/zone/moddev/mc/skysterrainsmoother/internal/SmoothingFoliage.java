package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/** Small plants and a single snow layer may give way to a transition in a fresh chunk. */
final class SmoothingFoliage {
    static boolean isCover(IBlockState state) {
        return isPlant(state) || isThinSnow(state);
    }

    private static boolean isThinSnow(IBlockState state) {
        return state.getBlock() == Blocks.SNOW_LAYER && state.getValue(BlockSnow.LAYERS) == 1;
    }

    static boolean isPlant(IBlockState state) {
        return state.getBlock() == Blocks.TALLGRASS || state.getBlock() == Blocks.YELLOW_FLOWER
                || state.getBlock() == Blocks.RED_FLOWER || state.getBlock() == Blocks.DEADBUSH
                || state.getBlock() == Blocks.DOUBLE_PLANT;
    }

    static boolean canReplace(World world, Chunk chunk, BlockPos target) {
        IBlockState state = chunk.getBlockState(target);

        if (state.getBlock().hasTileEntity(state)
                || chunk.getTileEntity(target, Chunk.EnumCreateEntityType.CHECK) != null) {
            return false;
        }
        if (state.getBlock().isAir(state, world, target) || isThinSnow(state)) {
            return true;
        }
        if (!isPlant(state)) {
            return false;
        }
        if (state.getBlock() != Blocks.DOUBLE_PLANT) {
            return true;
        }
        if (target.getY() >= 255
                || state.getValue(BlockDoublePlant.HALF) != BlockDoublePlant.EnumBlockHalf.LOWER) {
            return false;
        }

        IBlockState upper = chunk.getBlockState(target.up());

        return upper.getBlock() == Blocks.DOUBLE_PLANT
                && upper.getValue(BlockDoublePlant.HALF) == BlockDoublePlant.EnumBlockHalf.UPPER
                && chunk.getTileEntity(target.up(), Chunk.EnumCreateEntityType.CHECK) == null;
    }

    static int surfaceHeight(Chunk chunk, int localX, int localZ, int startX, int startZ) {
        int surfaceY = chunk.getHeightValue(localX, localZ) - 1;

        // A decorator may already have reached here from a neighbouring chunk.
        while (surfaceY >= 0 && isCover(chunk.getBlockState(
                new BlockPos(startX + localX, surfaceY, startZ + localZ)))) {
            surfaceY--;
        }

        return surfaceY;
    }

    private SmoothingFoliage() {
    }
}
