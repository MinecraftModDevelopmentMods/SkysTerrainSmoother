package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.PendingRepairData;

/** Registry-backed persistence contracts, run after the actual content mods have registered. */
final class PendingRepairChecks {
    static int run() {
        int checks = 0;
        for (TerrainMaterial material : MaterialCatalogue.instance().materials()) {
            List<IBlockState> shapes = new ArrayList<>(java.util.Arrays.asList(material.slab));
            for (int direction = 0; direction < 4; direction++) {
                if (material.step(direction) != null) {
                    shapes.add(material.step(direction));
                }
                if (material.corner(direction) != null) {
                    shapes.add(material.corner(direction));
                }
            }
            for (IBlockState shape : shapes) {
                PendingRepairData.Entry entry = new PendingRepairData.Entry(new BlockPos(-17, -63, -1), shape,
                        material.id, 3, true, material.sources.iterator().next(), material.supportAfterPlacement,
                        Blocks.AIR.getDefaultState());
                NBTTagCompound encoded = PendingRepairData.write(java.util.Arrays.asList(entry));
                require(PendingRepairData.read(encoded, -2, -1, -64, 320).equals(java.util.Arrays.asList(entry)),
                        "Every registered orientation survives a pending-repair round trip");
                require(PendingRepairData.write(PendingRepairData.read(encoded, -2, -1, -64, 320)).equals(encoded),
                        "Repair encoding is stable");
                checks += 2;
            }
        }

        TerrainMaterial material = MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState());
        NBTTagCompound valid = PendingRepairData.write(java.util.Arrays.asList(new PendingRepairData.Entry(new BlockPos(1, 64, 1),
                material.slab, material.id, 3, false, Blocks.STONE.getDefaultState(), null,
                Blocks.STONE.getDefaultState())));
        invalid(valid, data -> data.setInteger("version", 99));
        invalid(valid, data -> data.setString("dimension", "minecraft:the_nether"));
        invalid(valid, data -> data.removeTag("placements"));
        invalid(valid, data -> first(data).setInteger("level", 4));
        invalid(valid, data -> first(data).setLong("position", new BlockPos(16, 64, 1).toLong()));
        invalid(valid, data -> first(data).setLong("position", new BlockPos(1, -64, 1).toLong()));
        invalid(valid, data -> first(data).setLong("position", new BlockPos(1, 320, 1).toLong()));
        invalid(valid, data -> first(data).removeTag("expected_support"));
        invalid(valid, data -> first(data).getCompoundTag("cap").setString("Name", "missing:slab"));
        invalid(valid, data -> first(data).getCompoundTag("cap").getCompoundTag("Properties").setString("type", "bad"));
        invalid(valid, data -> first(data).getCompoundTag("cap").setString("Name", "minecraft:chest"));
        invalid(valid, data -> data.getTagList("placements", 10).appendTag(first(data).copy()));
        invalid(valid, data -> {
            NBTTagList bad = new NBTTagList();
            bad.appendTag(new NBTTagString("not a placement"));
            data.setTag("placements", bad);
        });
        require(PendingRepairData.read(PendingRepairData.write(java.util.Arrays.asList()), 0, 0, -64, 320).isEmpty(),
                "An empty repair list is harmless");
        return checks + 14;
    }

    private static NBTTagCompound first(NBTTagCompound data) {
        return data.getTagList("placements", 10).getCompoundTagAt(0);
    }

    private static void invalid(NBTTagCompound valid, Consumer<NBTTagCompound> change) {
        NBTTagCompound changed = valid.copy();
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
