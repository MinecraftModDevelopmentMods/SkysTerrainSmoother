package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.PendingRepairData;

/** Registry-backed persistence contracts, run after the actual content mods have registered. */
final class PendingRepairChecks {
    static int run() {
        int checks = 0;
        for (TerrainMaterial material : MaterialCatalogue.instance().materials()) {
            List<BlockState> shapes = new ArrayList<>(List.of(material.slab));
            for (int direction = 0; direction < 4; direction++) {
                if (material.step(direction) != null) {
                    shapes.add(material.step(direction));
                }
                if (material.corner(direction) != null) {
                    shapes.add(material.corner(direction));
                }
            }
            for (BlockState shape : shapes) {
                PendingRepairData.Entry entry = new PendingRepairData.Entry(new BlockPos(-17, -63, -1), shape,
                        material.id, 3, true, material.sources.iterator().next(), material.supportAfterPlacement,
                        Blocks.AIR.defaultBlockState());
                CompoundTag encoded = PendingRepairData.write(List.of(entry));
                require(PendingRepairData.read(encoded, -2, -1, -64, 320).equals(List.of(entry)),
                        "Every registered orientation survives a pending-repair round trip");
                require(PendingRepairData.write(PendingRepairData.read(encoded, -2, -1, -64, 320)).equals(encoded),
                        "Repair encoding is stable");
                checks += 2;
            }
        }

        TerrainMaterial material = MaterialCatalogue.instance().source(Blocks.STONE.defaultBlockState());
        CompoundTag valid = PendingRepairData.write(List.of(new PendingRepairData.Entry(new BlockPos(1, 64, 1),
                material.slab, material.id, 3, false, Blocks.STONE.defaultBlockState(), null,
                Blocks.STONE.defaultBlockState())));
        invalid(valid, data -> data.putInt("version", 99));
        invalid(valid, data -> data.putString("dimension", "minecraft:the_nether"));
        invalid(valid, data -> data.remove("placements"));
        invalid(valid, data -> first(data).putInt("level", 4));
        invalid(valid, data -> first(data).putLong("position", new BlockPos(16, 64, 1).asLong()));
        invalid(valid, data -> first(data).putLong("position", new BlockPos(1, -64, 1).asLong()));
        invalid(valid, data -> first(data).putLong("position", new BlockPos(1, 320, 1).asLong()));
        invalid(valid, data -> first(data).remove("expected_support"));
        invalid(valid, data -> first(data).getCompound("cap").putString("Name", "missing:slab"));
        invalid(valid, data -> first(data).getCompound("cap").getCompound("Properties").putString("type", "bad"));
        invalid(valid, data -> first(data).getCompound("cap").putString("Name", "minecraft:chest"));
        invalid(valid, data -> data.getList("placements", 10).add(first(data).copy()));
        invalid(valid, data -> {
            ListTag bad = new ListTag();
            bad.add(StringTag.valueOf("not a placement"));
            data.put("placements", bad);
        });
        require(PendingRepairData.read(PendingRepairData.write(List.of()), 0, 0, -64, 320).isEmpty(),
                "An empty repair list is harmless");
        return checks + 14;
    }

    private static CompoundTag first(CompoundTag data) {
        return data.getList("placements", 10).getCompound(0);
    }

    private static void invalid(CompoundTag valid, Consumer<CompoundTag> change) {
        CompoundTag changed = valid.copy();
        change.accept(changed);
        try {
            PendingRepairData.read(changed, 0, 0, -64, 320);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid saved repair was accepted: " + changed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
