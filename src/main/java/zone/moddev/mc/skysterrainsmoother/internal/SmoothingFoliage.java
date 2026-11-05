package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Small native plants may give way to a transition in a newly generated chunk. */
final class SmoothingFoliage {
    static boolean isPlant(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.SHORT_GRASS || block == Blocks.FERN || block == Blocks.DEAD_BUSH
                || block == Blocks.DANDELION || block == Blocks.POPPY || block == Blocks.BLUE_ORCHID
                || block == Blocks.ALLIUM || block == Blocks.AZURE_BLUET || block == Blocks.RED_TULIP
                || block == Blocks.ORANGE_TULIP || block == Blocks.WHITE_TULIP || block == Blocks.PINK_TULIP
                || block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER
                || block == Blocks.LILY_OF_THE_VALLEY || block == Blocks.TALL_GRASS
                || block == Blocks.LARGE_FERN || block == Blocks.SUNFLOWER || block == Blocks.LILAC
                || block == Blocks.ROSE_BUSH || block == Blocks.PEONY;
    }

    static boolean isCover(BlockState state) {
        return isPlant(state) || isThinSnow(state);
    }

    private static boolean isThinSnow(BlockState state) {
        return state.getBlock() == Blocks.SNOW && state.getValue(SnowLayerBlock.LAYERS) == 1;
    }

    static int surfaceHeight(ChunkAccess chunk, int localX, int localZ, int startX, int startZ) {
        // Full chunks retain the live heightmap, not the generation-only map.
        Heightmap.Types heightmap = chunk.getPersistedStatus() == ChunkStatus.FULL
                ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        int surfaceY = chunk.getHeight(heightmap, localX, localZ);
        while (surfaceY >= chunk.getMinBuildHeight()) {
            BlockState state = chunk.getBlockState(new BlockPos(startX + localX, surfaceY, startZ + localZ));
            if (!state.isAir() && !isCover(state)) {
                break;
            }
            surfaceY--;
        }
        return surfaceY;
    }

    static boolean canReplace(LevelAccessor world, ChunkAccess owner, BlockPos position) {
        BlockState state = owner.getBlockState(position);
        if (state.hasBlockEntity() || owner.getBlockEntity(position) != null
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isAir() || isThinSnow(state)) {
            return true;
        }
        if (!isPlant(state)) {
            return false;
        }
        if (!(state.getBlock() instanceof DoublePlantBlock)) {
            return true;
        }
        if (position.getY() + 1 >= owner.getMaxBuildHeight() || state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.LOWER) {
            return false;
        }

        BlockState upper = owner.getBlockState(position.above());
        return upper.getBlock() == state.getBlock()
                && upper.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                && owner.getBlockEntity(position.above()) == null;
    }

    private SmoothingFoliage() {
    }
}
