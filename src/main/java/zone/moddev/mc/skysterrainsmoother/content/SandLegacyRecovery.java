package zone.moddev.mc.skysterrainsmoother.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalItems;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Contributes to the existing early loader bridge; it does not install another transformer. */
public final class SandLegacyRecovery {
    private static final String LEGACY_ID = SkysTerrainSmoother.ID + ":sand_slab";

    private SandLegacyRecovery() {
    }

    public static void register() {
        HistoricalFullBlocks.registerStates(LEGACY_ID, LEGACY_ID, "terrain_sand_flattening",
                metadata -> metadata < 4 ? SandContent.legacyState(metadata) : null);
        HistoricalItems.register("terrain_sand_items", SandLegacyRecovery::containsLegacyItems,
                SandLegacyRecovery::recoverItems);
    }

    private static boolean containsLegacyItems(CompoundNBT root) {
        List<CompoundNBT> stacks = new ArrayList<>();
        collect(root, stacks, 0);
        return !stacks.isEmpty();
    }

    /** Validate the whole tree before changing any stack, including inventories carried by an item. */
    public static void recoverItems(CompoundNBT root) {
        List<CompoundNBT> stacks = new ArrayList<>();
        collect(root, stacks, 0);
        for (CompoundNBT stack : stacks) {
            int metadata = stack.getInt("Damage");
            if (!stack.contains("Damage", 99) || metadata < 0 || metadata > 3
                    || !stack.contains("Count", 99) || stack.getInt("Count") < 1) {
                throw new LegacyBridge.RecoveryAbort(
                        "Cannot recover a saved sand slab stack: invalid damage or count. Restore the world backup.",
                        null);
            }
        }
        for (CompoundNBT stack : stacks) {
            stack.putString("id", (stack.getInt("Damage") & 2) == 0
                    ? LEGACY_ID : SkysTerrainSmoother.ID + ":red_sand_slab");
            stack.remove("Damage");
        }
    }

    private static void collect(INBT value, List<CompoundNBT> stacks, int depth) {
        if (depth > 64) {
            throw new LegacyBridge.RecoveryAbort("Saved sand slab nesting exceeds 64 levels", null);
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            if (LEGACY_ID.equals(compound.getString("id")) && compound.contains("Damage")) {
                stacks.add(compound);
            }
            for (String key : compound.keySet()) {
                collect(compound.get(key), stacks, depth + 1);
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                collect(child, stacks, depth + 1);
            }
        }
    }
}
