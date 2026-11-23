package zone.moddev.mc.skysterrainsmoother.content;

import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;

/** A horizontal half of falling terrain. Joining it creates the native full block. */
public final class SandSlabBlock extends FallingBlock implements SimpleWaterloggedBlock {
    private static final MapCodec<SandSlabBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("full_block").forGetter(block -> block.fullBlock), propertiesCodec())
                    .apply(instance, SandSlabBlock::new));

    public static final EnumProperty<SlabType> TYPE = EnumProperty.create("type", SlabType.class,
            SlabType.TOP, SlabType.BOTTOM);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape LOWER_HALF = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape UPPER_HALF = Block.box(0, 8, 0, 16, 16, 16);
    private final Block fullBlock;

    public SandSlabBlock(boolean red) {
        this(red ? Blocks.RED_SAND : Blocks.SAND);
    }

    public SandSlabBlock(Block fullBlock) {
        this(fullBlock, Block.Properties.ofFullCopy(fullBlock)
                .noOcclusion().isRedstoneConductor((state, world, position) -> false));
    }

    private SandSlabBlock(Block fullBlock, Properties properties) {
        super(properties);
        this.fullBlock = fullBlock;
        registerDefaultState(stateDefinition.any().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<SandSlabBlock> codec() {
        return CODEC;
    }

    public BlockState fullState() {
        return fullBlock.defaultBlockState();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        boolean upper = face == Direction.DOWN || face != Direction.UP
                && context.getClickLocation().y - context.getClickedPos().getY() > .5;
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(TYPE, upper ? SlabType.TOP : SlabType.BOTTOM).setValue(WATERLOGGED, water);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos position, CollisionContext context) {
        return state.getValue(TYPE) == SlabType.TOP ? UPPER_HALF : LOWER_HALF;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos position,
            CollisionContext context) {
        return getShape(state, world, position, context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor world,
            BlockPos position, BlockPos neighbourPosition) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(position, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, side, neighbour, world, position, neighbourPosition);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos position) {
        return 0;
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos position, RandomSource random) {
        if (world.isClientSide || !world.getBlockState(position).equals(state) || position.getY() < world.getMinBuildHeight()
                || !isFree(world.getBlockState(position.below()))) {
            return;
        }

        // The native factory keeps source water in place and dries only the falling entity.
        // Vanilla waterlogs the slab again if its landing cell contains water.
        FallingBlockEntity entity = FallingBlockEntity.fall(world, position, state);
        falling(entity);
    }
}
