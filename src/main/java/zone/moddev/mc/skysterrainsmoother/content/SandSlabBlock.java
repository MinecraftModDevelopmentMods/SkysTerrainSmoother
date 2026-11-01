package zone.moddev.mc.skysterrainsmoother.content;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.IWaterLoggable;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.FluidState;
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
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ToolType;

/** A horizontal sand half. Joining it creates ordinary sand, so there is no double state. */
public final class SandSlabBlock extends FallingBlock implements IWaterLoggable {
    public static final EnumProperty<SlabType> TYPE = EnumProperty.create("type", SlabType.class,
            SlabType.TOP, SlabType.BOTTOM);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape LOWER_HALF = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape UPPER_HALF = Block.box(0, 8, 0, 16, 16, 16);
    private final boolean red;

    public SandSlabBlock(boolean red) {
        super(Block.Properties.copy(red ? Blocks.RED_SAND : Blocks.SAND).noOcclusion().isRedstoneConductor((state, world, position) -> false));
        this.red = red;
        registerDefaultState(stateDefinition.any().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, false));
    }

    public BlockState fullState() {
        return (red ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(TYPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction face = context.getClickedFace();
        boolean upper = face == Direction.DOWN || face != Direction.UP
                && context.getClickLocation().y - context.getClickedPos().getY() > .5;
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(TYPE, upper ? SlabType.TOP : SlabType.BOTTOM).setValue(WATERLOGGED, water);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos position, ISelectionContext context) {
        return state.getValue(TYPE) == SlabType.TOP ? UPPER_HALF : LOWER_HALF;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos position,
            ISelectionContext context) {
        return getShape(state, world, position, context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, IWorld world,
            BlockPos position, BlockPos neighbourPosition) {
        if (state.getValue(WATERLOGGED)) {
            world.getLiquidTicks().scheduleTick(position, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, side, neighbour, world, position, neighbourPosition);
    }

    @Override
    public ToolType getHarvestTool(BlockState state) {
        return ToolType.SHOVEL;
    }

    @Override
    public int getLightBlock(BlockState state, IBlockReader world, BlockPos position) {
        return 0;
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos position, Random random) {
        if (world.isClientSide || !world.getBlockState(position).equals(state) || position.getY() < 0
                || !isFree(world.getBlockState(position.below()))) {
            return;
        }

        // World.removeBlock leaves water at the source. Only the sand travels;
        // vanilla waterlogs it again if the landing cell contains water.
        BlockState fallingState = state.setValue(WATERLOGGED, false);
        FallingBlockEntity entity = new FallingBlockEntity(world, position.getX() + .5, position.getY(),
                position.getZ() + .5, fallingState);
        falling(entity);
        world.addFreshEntity(entity);
    }
}
