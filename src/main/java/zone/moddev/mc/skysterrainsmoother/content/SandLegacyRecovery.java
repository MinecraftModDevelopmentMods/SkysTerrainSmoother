package zone.moddev.mc.skysterrainsmoother.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
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

    private static boolean containsLegacyItems(CompoundTag root) {
        List<CompoundTag> stacks = new ArrayList<>();
        collect(root, stacks, 0);
        return !stacks.isEmpty();
    }

    /** Validate the whole tree before changing any stack, including inventories carried by an item. */
    public static void recoverItems(CompoundTag root) {
        List<CompoundTag> stacks = new ArrayList<>();
        collect(root, stacks, 0);
        for (CompoundTag stack : stacks) {
            int metadata = stack.getIntOr("Damage", -1);
            if (metadata < 0 || metadata > 3
                    || stack.getIntOr("Count", 0) < 1) {
                throw new LegacyBridge.RecoveryAbort(
                        "Cannot recover a saved sand slab stack: invalid damage or count. Restore the world backup.",
                        null);
            }
        }
        for (CompoundTag stack : stacks) {
            stack.putString("id", (stack.getIntOr("Damage", -1) & 2) == 0
                    ? LEGACY_ID : SkysTerrainSmoother.ID + ":red_sand_slab");
            stack.remove("Damage");
        }
    }

    private static void collect(Tag value, List<CompoundTag> stacks, int depth) {
        if (depth > 64) {
            throw new LegacyBridge.RecoveryAbort("Saved sand slab nesting exceeds 64 levels", null);
        }
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;
            if (LEGACY_ID.equals(compound.getStringOr("id", "")) && compound.contains("Damage")) {
                stacks.add(compound);
            }
            for (String key : compound.keySet()) {
                collect(compound.get(key), stacks, depth + 1);
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                collect(child, stacks, depth + 1);
            }
        }
    }
}
