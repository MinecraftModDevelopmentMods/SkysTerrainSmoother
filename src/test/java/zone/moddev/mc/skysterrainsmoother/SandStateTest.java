package zone.moddev.mc.skysterrainsmoother;

import java.util.Collections;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandLegacyRecovery;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;
import static org.junit.jupiter.api.Assertions.*;

class SandStateTest {
    @Test
    void gravelKeepsItsNativeMaterialAndBothHorizontalHalves() {
        SandSlabBlock gravel = new SandSlabBlock(net.minecraft.world.level.block.Blocks.GRAVEL,
                Identifier.parse("skysterrainsmoother:test_gravel_slab"));
        assertSame(net.minecraft.world.level.block.Blocks.GRAVEL, gravel.fullState().getBlock());
        assertEquals(4, gravel.getStateDefinition().getPossibleStates().size());
        assertFalse(SandSlabBlock.TYPE.getPossibleValues().contains(SlabType.DOUBLE));
        for (SlabType half : new SlabType[] {SlabType.BOTTOM, SlabType.TOP}) {
            BlockState state = gravel.defaultBlockState().setValue(SandSlabBlock.TYPE, half);
            assertEquals(.5, state.getShape(null, BlockPos.ZERO).bounds().getYsize());
            assertFalse(state.hasBlockEntity());
        }
    }

    @BeforeAll
    static void boot() {
        MinecraftTestBootstrap.registerVanilla();
        SandContent.slab = new SandSlabBlock(false, Identifier.parse("skysterrainsmoother:sand_slab"));
        SandContent.redSlab = new SandSlabBlock(true, Identifier.parse("skysterrainsmoother:red_sand_slab"));
    }

    @Test
    void everyOldMetadataValueRetainsColourAndHalf() {
        for (int metadata = 0; metadata < 4; metadata++) {
            BlockState state = SandContent.legacyState(metadata);
            assertSame((metadata & 2) == 0 ? SandContent.slab : SandContent.redSlab, state.getBlock());
            assertEquals((metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM, state.getValue(SandSlabBlock.TYPE));
            assertFalse(state.getValue(SandSlabBlock.WATERLOGGED));
            VoxelShape collision = state.getCollisionShape(null, BlockPos.ZERO);
            assertEquals((metadata & 1) == 0 ? .5 : 0, collision.bounds().minY);
            assertEquals((metadata & 1) == 0 ? 1 : .5, collision.bounds().maxY);
            assertFalse(SandSlabBlock.TYPE.getPossibleValues().contains(SlabType.DOUBLE));
            assertEquals(4, state.getBlock().getStateDefinition().getPossibleStates().size());
        }
    }

    @Test
    void nestedStacksKeepSlotsCountsAndUnrelatedData() {
        CompoundTag root = new CompoundTag();
        ListTag contents = new ListTag();
        for (int metadata = 0; metadata < 4; metadata++) {
            CompoundTag stack = stack(metadata);
            stack.putByte("Slot", (byte) metadata);
            contents.add(stack);
        }
        root.put("Items", contents);
        root.putString("Unrelated", "keep");
        SandLegacyRecovery.recoverItems(root);
        for (int index = 0; index < 4; index++) {
            CompoundTag stack = contents.getCompound(index).orElseThrow();
            assertEquals(index < 2 ? "skysterrainsmoother:sand_slab" : "skysterrainsmoother:red_sand_slab", stack.getString("id").orElseThrow());
            assertEquals(19, stack.getInt("Count").orElseThrow().intValue());
            assertEquals(index, stack.getInt("Slot").orElseThrow().intValue());
            assertEquals("Named sand", stack.getCompound("tag").orElseThrow().getString("Name").orElseThrow());
            assertFalse(stack.contains("Damage"));
        }
        assertEquals("keep", root.getString("Unrelated").orElseThrow());
        CompoundTag once = root.copy();
        SandLegacyRecovery.recoverItems(root);
        assertEquals(once, root);
    }

    @Test
    void invalidNestedStackAbortsBeforeChangingAnyStack() {
        for (int invalid : new int[] { -1, 4, 300 }) {
            CompoundTag root = new CompoundTag();
            ListTag contents = new ListTag();
            contents.add(stack(2));
            contents.add(stack(invalid));
            root.put("Items", contents);
            CompoundTag original = root.copy();
            assertThrows(zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.RecoveryAbort.class,
                    () -> SandLegacyRecovery.recoverItems(root));
            assertEquals(original, root);
        }
    }

    @Test
    void unrelatedStacksAreUntouched() {
        CompoundTag stack = stack(7);
        stack.putString("id", "anothermod:sand_slab");
        CompoundTag original = stack.copy();
        SandLegacyRecovery.recoverItems(stack);
        assertEquals(original, stack);
    }

    private static CompoundTag stack(int metadata) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "skysterrainsmoother:sand_slab");
        stack.putByte("Count", (byte) 19);
        stack.putShort("Damage", (short) metadata);
        CompoundTag custom = new CompoundTag();
        custom.putString("Name", "Named sand");
        stack.put("tag", custom);
        return stack;
    }
}
