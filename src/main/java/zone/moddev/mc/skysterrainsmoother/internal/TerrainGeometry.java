package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Powder snow has a real terrain outline even though most entities sink through it. */
public final class TerrainGeometry {
    public static VoxelShape shape(BlockState state) {
        return state.getBlock() instanceof PowderSnowBlock
                ? state.getShape(EmptyAccess.INSTANCE, BlockPos.ZERO)
                : state.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO);
    }

    public static boolean full(BlockState state) {
        return !Shapes.joinIsNotEmpty(shape(state), Shapes.block(), BooleanOp.NOT_SAME);
    }

    public static boolean full(BlockState state, BlockGetter world, BlockPos position) {
        return state.getBlock() instanceof PowderSnowBlock
                ? full(state) : state.isCollisionShapeFullBlock(world, position);
    }

    private TerrainGeometry() {
    }
}
