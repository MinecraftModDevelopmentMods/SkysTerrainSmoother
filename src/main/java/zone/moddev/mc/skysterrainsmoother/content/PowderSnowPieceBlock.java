package zone.moddev.mc.skysterrainsmoother.content;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native powder-snow behaviour confined to a slab, step or corner's actual volume. */
public final class PowderSnowPieceBlock extends PowderSnowBlock {
    public enum Kind implements StringRepresentable {
        SLAB, STEP, CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int[] EDGES = { 3, 6, 12, 9 };
    private final Kind kind;
    private final VoxelShape[] outlines = new VoxelShape[8];
    private final VoxelShape[] landings = new VoxelShape[8];

    public PowderSnowPieceBlock(Kind kind) {
        this(kind, Properties.of().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BLOCK, PowderSnowContent.id(kind))).mapColor(MapColor.SNOW).strength(.25F).sound(SoundType.POWDER_SNOW)
                .noOcclusion().dynamicShape().forceSolidOff());
    }

    private PowderSnowPieceBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(HALF, Half.BOTTOM).setValue(FACING, Direction.NORTH));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            int index = index(state);
            outlines[index] = shape(state, false);
            landings[index] = shape(state, true);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    public Kind kind() {
        return kind;
    }

    public int footprint(BlockState state) {
        int facing = switch (state.getValue(FACING)) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
        return switch (kind) {
            case SLAB -> 15;
            case STEP -> EDGES[facing];
            case CORNER -> 1 << facing;
        };
    }

    public int mask(BlockState state) {
        return footprint(state) << (state.getValue(HALF) == Half.TOP ? 4 : 0);
    }

    private static int index(BlockState state) {
        return state.getValue(FACING).get2DDataValue() + (state.getValue(HALF) == Half.TOP ? 4 : 0);
    }

    private VoxelShape shape(BlockState state, boolean landing) {
        double bottom = state.getValue(HALF) == Half.TOP ? .5 : 0;
        double top = bottom + (landing ? .45 : .5);
        VoxelShape result = Shapes.empty();
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            if ((footprint(state) & 1 << quadrant) != 0) {
                double left = quadrant == 0 || quadrant == 3 ? 0 : .5;
                double front = quadrant < 2 ? 0 : .5;
                result = Shapes.or(result, Shapes.box(left, bottom, front, left + .5, top, front + .5));
            }
        }
        return result.optimize();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos position, CollisionContext context) {
        return outlines[index(state)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos position, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity != null) {
                if (entity.fallDistance > 2.5F) {
                    return landings[index(state)];
                }
                if (entity instanceof FallingBlockEntity || canEntityWalkOnPowderSnow(entity)
                        && context.isAbove(outlines[index(state)], position, false) && !context.isDescending()) {
                    return outlines[index(state)];
                }
            }
        }
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos position, Entity entity,
            net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean moved) {
        boolean inside = outlines[index(state)].bounds().move(position).intersects(entity.getBoundingBox());
        if (inside) {
            // The superclass sets slowdown, freezing, particles and fire behaviour.
            super.entityInside(state, world, position, entity, effects, moved);
        }
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter world, BlockPos position, Entity entity) {
        return outlines[index(state)];
    }

    @Override
    public ItemStack pickupBlock(net.minecraft.world.entity.LivingEntity player, LevelAccessor world, BlockPos position, BlockState state) {
        // A fraction of a block cannot fill a whole bucket. Rebuild the pieces first.
        return ItemStack.EMPTY;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.empty();
    }

    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos position, Mob mob) {
        return PathType.POWDER_SNOW;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction face) {
        return false;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return Block.byItem(context.getItemInHand().getItem()) instanceof PowderSnowPieceBlock incoming
                && joined(state, incoming, context) != null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.getBlock() instanceof PowderSnowPieceBlock) {
            return joined(existing, this, context);
        }
        if (!context.getLevel().getFluidState(context.getClickedPos()).isEmpty()) {
            return null;
        }
        Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));
        Half half = context.getClickedFace() == Direction.DOWN
                || context.getClickedFace().getAxis() != Direction.Axis.Y && hit.y > .5 ? Half.TOP : Half.BOTTOM;
        Direction facing = kind == Kind.CORNER
                ? (hit.z < .5 ? (hit.x < .5 ? Direction.NORTH : Direction.EAST)
                    : (hit.x < .5 ? Direction.WEST : Direction.SOUTH))
                : (Math.abs(hit.x - .5) > Math.abs(hit.z - .5)
                    ? (hit.x < .5 ? Direction.WEST : Direction.EAST)
                    : (hit.z < .5 ? Direction.NORTH : Direction.SOUTH));
        return defaultBlockState().setValue(HALF, half).setValue(FACING, kind == Kind.SLAB ? Direction.NORTH : facing);
    }

    private static BlockState joined(BlockState existing, PowderSnowPieceBlock incoming, BlockPlaceContext context) {
        if (!(existing.getBlock() instanceof PowderSnowPieceBlock present)) {
            return null;
        }
        Direction face = context.getClickedFace();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 inside = context.getClickLocation().subtract(normal.scale(.0001));
        BlockPos cell = BlockPos.containing(inside);
        Vec3 next = context.getClickLocation().add(normal.scale(.0001)).subtract(Vec3.atLowerCornerOf(cell));
        if (next.x < 0 || next.x > 1 || next.y < 0 || next.y > 1 || next.z < 0 || next.z > 1) {
            return null;
        }
        int quadrant = next.z < .5 ? (next.x < .5 ? 0 : 1) : (next.x < .5 ? 3 : 2);
        int selected = 1 << (quadrant + (next.y >= .5 ? 4 : 0));
        int occupied = present.mask(existing);
        for (BlockState candidate : incoming.stateDefinition.getPossibleStates()) {
            int added = incoming.mask(candidate);
            if ((added & occupied) != 0 || (added & selected) == 0) {
                continue;
            }
            int union = added | occupied;
            if (union == 255) {
                return Blocks.POWDER_SNOW.defaultBlockState();
            }
            for (Kind kind : Kind.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (Half half : Half.values()) {
                        BlockState result = PowderSnowContent.state(kind, half, facing);
                        if (((PowderSnowPieceBlock) result.getBlock()).mask(result) == union) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }
}
