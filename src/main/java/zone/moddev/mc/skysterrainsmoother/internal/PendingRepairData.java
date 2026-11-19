package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** A chunk saves only its outstanding placements, never permission to smooth the chunk again. */
public final class PendingRepairData {
    public static final String KEY = "skysterrainsmoother:pending_repairs";
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 65_536;

    private PendingRepairData() {}

    public record Entry(BlockPos position, BlockState cap, ResourceLocation material, int level, boolean cave,
            BlockState originalSupport, BlockState dirtifiedSupport, BlockState expectedSupport) {}

    public static CompoundTag write(List<Entry> entries) {
        CompoundTag result = new CompoundTag();
        result.putInt("version", VERSION);
        result.putString("dimension", "minecraft:overworld");
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag value = new CompoundTag();
            value.putLong("position", entry.position.asLong());
            value.put("cap", NbtUtils.writeBlockState(entry.cap));
            value.putString("material", entry.material.toString());
            value.putInt("level", entry.level);
            value.putBoolean("cave", entry.cave);
            value.put("original_support", NbtUtils.writeBlockState(entry.originalSupport));
            value.put("expected_support", NbtUtils.writeBlockState(entry.expectedSupport));
            if (entry.dirtifiedSupport != null) {
                value.put("dirtified_support", NbtUtils.writeBlockState(entry.dirtifiedSupport));
            }
            list.add(value);
        }
        result.put("placements", list);
        return result;
    }

    public static List<Entry> read(CompoundTag data, int chunkX, int chunkZ, int minY, int maxY) {
        if (!data.contains("version", Tag.TAG_INT) || data.getInt("version") != VERSION
                || !data.getString("dimension").equals("minecraft:overworld")
                || !data.contains("placements", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Unrecognised pending-repair format");
        }
        ListTag list = data.getList("placements", Tag.TAG_COMPOUND);
        if (list.size() != ((ListTag) data.get("placements")).size() || list.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid pending-repair list");
        }
        Set<BlockPos> positions = new HashSet<>();
        List<Entry> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            BlockPos position = BlockPos.of(value.getLong("position"));
            ResourceLocation material = ResourceLocation.tryParse(value.getString("material"));
            int level = value.getInt("level");
            if (!value.contains("position", Tag.TAG_LONG) || material == null || level < 1 || level > 3
                    || position.getX() >> 4 != chunkX || position.getZ() >> 4 != chunkZ
                    || position.getY() <= minY || position.getY() >= maxY || !positions.add(position)) {
                throw new IllegalArgumentException("Invalid or repeated pending-repair position");
            }
            result.add(new Entry(position, state(value, "cap"), material, level, value.getBoolean("cave"),
                    state(value, "original_support"),
                    value.contains("dirtified_support") ? state(value, "dirtified_support") : null,
                    state(value, "expected_support")));
        }
        return List.copyOf(result);
    }

    private static BlockState state(CompoundTag value, String key) {
        if (!value.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing repair state: " + key);
        }
        CompoundTag saved = value.getCompound(key);
        ResourceLocation block = ResourceLocation.tryParse(saved.getString("Name"));
        if (block == null || !BuiltInRegistries.BLOCK.containsKey(block)) {
            throw new IllegalArgumentException("Unavailable repair block: " + saved.getString("Name"));
        }
        BlockState state = BuiltInRegistries.BLOCK.get(block).defaultBlockState();
        CompoundTag properties = saved.getCompound("Properties");
        for (String name : properties.getAllKeys()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null || !properties.contains(name, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Invalid repair property: " + name);
            }
            state = withProperty(state, property, properties.getString(name));
        }
        // Minecraft otherwise accepts unknown properties and substitutes defaults. A repair must match exactly.
        if (!NbtUtils.writeBlockState(state).equals(saved) || state.hasBlockEntity()) {
            throw new IllegalArgumentException("Invalid repair state: " + saved);
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid repair property: " + property.getName())));
    }
}
