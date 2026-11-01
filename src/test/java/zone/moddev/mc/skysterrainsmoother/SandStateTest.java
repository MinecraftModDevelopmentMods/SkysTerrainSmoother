package zone.moddev.mc.skysterrainsmoother;

import java.util.Collections;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandLegacyRecovery;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;
import static org.junit.jupiter.api.Assertions.*;

class SandStateTest {
    @BeforeAll
    static void boot() {
        MinecraftTestBootstrap.registerVanilla();
        SandContent.register();
    }

    @Test
    void everyOldMetadataValueRetainsColourAndHalf() {
        for (int metadata = 0; metadata < 4; metadata++) {
            BlockState state = SandContent.legacyState(metadata);
            assertSame((metadata & 2) == 0 ? SandContent.slab : SandContent.redSlab, state.getBlock());
            assertEquals((metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM, state.get(SandSlabBlock.TYPE));
            assertFalse(state.get(SandSlabBlock.WATERLOGGED));
            VoxelShape collision = state.getCollisionShape(null, BlockPos.ZERO);
            assertEquals((metadata & 1) == 0 ? .5 : 0, collision.getBoundingBox().minY);
            assertEquals((metadata & 1) == 0 ? 1 : .5, collision.getBoundingBox().maxY);
            assertFalse(SandSlabBlock.TYPE.getAllowedValues().contains(SlabType.DOUBLE));
            assertEquals(4, state.getBlock().getStateContainer().getValidStates().size());
        }
    }

    @Test
    void nestedStacksKeepSlotsCountsAndUnrelatedData() {
        CompoundNBT root = new CompoundNBT();
        ListNBT contents = new ListNBT();
        for (int metadata = 0; metadata < 4; metadata++) {
            CompoundNBT stack = stack(metadata);
            stack.putByte("Slot", (byte) metadata);
            contents.add(stack);
        }
        root.put("Items", contents);
        root.putString("Unrelated", "keep");
        SandLegacyRecovery.recoverItems(root);
        for (int index = 0; index < 4; index++) {
            CompoundNBT stack = contents.getCompound(index);
            assertEquals(index < 2 ? "skysterrainsmoother:sand_slab" : "skysterrainsmoother:red_sand_slab", stack.getString("id"));
            assertEquals(19, stack.getByte("Count"));
            assertEquals(index, stack.getByte("Slot"));
            assertEquals("Named sand", stack.getCompound("tag").getString("Name"));
            assertFalse(stack.contains("Damage"));
        }
        assertEquals("keep", root.getString("Unrelated"));
        CompoundNBT once = root.copy();
        SandLegacyRecovery.recoverItems(root);
        assertEquals(once, root);
    }

    @Test
    void invalidNestedStackAbortsBeforeChangingAnyStack() {
        for (int invalid : new int[] { -1, 4, 300 }) {
            CompoundNBT root = new CompoundNBT();
            ListNBT contents = new ListNBT();
            contents.add(stack(2));
            contents.add(stack(invalid));
            root.put("Items", contents);
            CompoundNBT original = root.copy();
            assertThrows(zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.RecoveryAbort.class,
                    () -> SandLegacyRecovery.recoverItems(root));
            assertEquals(original, root);
        }
    }

    @Test
    void unrelatedStacksAreUntouched() {
        CompoundNBT stack = stack(7);
        stack.putString("id", "anothermod:sand_slab");
        CompoundNBT original = stack.copy();
        SandLegacyRecovery.recoverItems(stack);
        assertEquals(original, stack);
    }

    private static CompoundNBT stack(int metadata) {
        CompoundNBT stack = new CompoundNBT();
        stack.putString("id", "skysterrainsmoother:sand_slab");
        stack.putByte("Count", (byte) 19);
        stack.putShort("Damage", (short) metadata);
        CompoundNBT custom = new CompoundNBT();
        custom.putString("Name", "Named sand");
        stack.put("tag", custom);
        return stack;
    }
}
