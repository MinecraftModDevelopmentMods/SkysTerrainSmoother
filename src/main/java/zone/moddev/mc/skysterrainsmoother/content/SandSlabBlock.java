package zone.moddev.mc.skysterrainsmoother.content;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.IWaterLoggable;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;

/** A horizontal sand half. Joining it creates ordinary sand, so there is no double state. */
public final class SandSlabBlock extends FallingBlock implements IWaterLoggable {
    public static final EnumProperty<SlabType> TYPE = EnumProperty.create("type", SlabType.class,
            SlabType.TOP, SlabType.BOTTOM);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape LOWER_HALF = Block.makeCuboidShape(0, 0, 0, 16, 8, 16);
    private static final VoxelShape UPPER_HALF = Block.makeCuboidShape(0, 8, 0, 16, 16, 16);
    private final boolean red;

    public SandSlabBlock(boolean red) {
        super(Block.Properties.from(red ? Blocks.RED_SAND : Blocks.SAND));
        this.red = red;
        setDefaultState(stateContainer.getBaseState().with(TYPE, SlabType.BOTTOM).with(WATERLOGGED, false));
    }

    public BlockState fullState() {
        return (red ? Blocks.RED_SAND : Blocks.SAND).getDefaultState();
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(TYPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction face = context.getFace();
        boolean upper = face == Direction.DOWN || face != Direction.UP
                && context.getHitVec().y - context.getPos().getY() > .5;
        boolean water = context.getWorld().getFluidState(context.getPos()).getFluid() == Fluids.WATER;
        return getDefaultState().with(TYPE, upper ? SlabType.TOP : SlabType.BOTTOM).with(WATERLOGGED, water);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos position, ISelectionContext context) {
        return state.get(TYPE) == SlabType.TOP ? UPPER_HALF : LOWER_HALF;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos position,
            ISelectionContext context) {
        return getShape(state, world, position, context);
    }

    @Override
    public IFluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updatePostPlacement(BlockState state, Direction side, BlockState neighbour, IWorld world,
            BlockPos position, BlockPos neighbourPosition) {
        if (state.get(WATERLOGGED)) {
            world.getPendingFluidTicks().scheduleTick(position, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.updatePostPlacement(state, side, neighbour, world, position, neighbourPosition);
    }

    @Override
    public ToolType getHarvestTool(BlockState state) {
        return ToolType.SHOVEL;
    }

    @Override
    public boolean isNormalCube(BlockState state, IBlockReader world, BlockPos position) {
        return false;
    }

    @Override
    public boolean isSolid(BlockState state) {
        return false;
    }

    @Override
    public int getOpacity(BlockState state, IBlockReader world, BlockPos position) {
        return 0;
    }

    @Override
    public void tick(BlockState state, World world, BlockPos position, Random random) {
        if (world.isRemote || !world.getBlockState(position).equals(state) || position.getY() < 0
                || !canFallThrough(world.getBlockState(position.down()))) {
            return;
        }

        // World.removeBlock leaves water at the source. Only the sand travels;
        // vanilla waterlogs it again if the landing cell contains water.
        BlockState fallingState = state.with(WATERLOGGED, false);
        FallingBlockEntity entity = new FallingBlockEntity(world, position.getX() + .5, position.getY(),
                position.getZ() + .5, fallingState);
        onStartFalling(entity);
        world.addEntity(entity);
    }
}
