package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Use the real outline for terrain that entities sink into. */
public final class TerrainGeometry {
    public static VoxelShape shape(BlockState state) {
        return hasSinkingSurface(state)
                ? state.getShape(EmptyAccess.INSTANCE, BlockPos.ZERO)
                : state.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO);
    }

    public static boolean full(BlockState state) {
        return !Shapes.joinIsNotEmpty(shape(state), Shapes.block(), BooleanOp.NOT_SAME);
    }

    public static boolean full(BlockState state, BlockGetter world, BlockPos position) {
        return hasSinkingSurface(state)
                ? full(state) : state.isCollisionShapeFullBlock(world, position);
    }

    private static boolean hasSinkingSurface(BlockState state) {
        return state.getBlock() instanceof PowderSnowBlock || state.is(Blocks.MUD);
    }

    private TerrainGeometry() {
    }
}
