package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;

/** A chunk saves only its outstanding placements, never permission to smooth the chunk again. */
public final class PendingRepairData {
    public static final String KEY = "skysterrainsmoother:pending_repairs";
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 65_536;

    private PendingRepairData() {}

    public static final class Entry {
        private final BlockPos position;
        private final IBlockState cap;
        private final ResourceLocation material;
        private final int level;
        private final boolean cave;
        private final IBlockState originalSupport;
        private final IBlockState dirtifiedSupport;
        private final IBlockState expectedSupport;

        public Entry(BlockPos position, IBlockState cap, ResourceLocation material, int level, boolean cave,
                IBlockState originalSupport, IBlockState dirtifiedSupport, IBlockState expectedSupport) {
            this.position = position;
            this.cap = cap;
            this.material = material;
            this.level = level;
            this.cave = cave;
            this.originalSupport = originalSupport;
            this.dirtifiedSupport = dirtifiedSupport;
            this.expectedSupport = expectedSupport;
        }

        public BlockPos position() {
            return position;
        }

        public IBlockState cap() {
            return cap;
        }

        public ResourceLocation material() {
            return material;
        }

        public int level() {
            return level;
        }

        public boolean cave() {
            return cave;
        }

        public IBlockState originalSupport() {
            return originalSupport;
        }

        public IBlockState dirtifiedSupport() {
            return dirtifiedSupport;
        }

        public IBlockState expectedSupport() {
            return expectedSupport;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return position.equals(entry.position) && cap.equals(entry.cap) && material.equals(entry.material)
                    && level == entry.level && cave == entry.cave && originalSupport.equals(entry.originalSupport)
                    && java.util.Objects.equals(dirtifiedSupport, entry.dirtifiedSupport)
                    && expectedSupport.equals(entry.expectedSupport);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(position, cap, material, level, cave,
                    originalSupport, dirtifiedSupport, expectedSupport);
        }
    }

    public static NBTTagCompound write(List<Entry> entries) {
        NBTTagCompound result = new NBTTagCompound();
        result.setInteger("version", VERSION);
        result.setString("dimension", "minecraft:overworld");
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound value = new NBTTagCompound();
            value.setLong("position", entry.position.toLong());
            value.setTag("cap", writeState(entry.cap));
            value.setString("material", entry.material.toString());
            value.setInteger("level", entry.level);
            value.setBoolean("cave", entry.cave);
            value.setTag("original_support", writeState(entry.originalSupport));
            value.setTag("expected_support", writeState(entry.expectedSupport));
            if (entry.dirtifiedSupport != null) {
                value.setTag("dirtified_support", writeState(entry.dirtifiedSupport));
            }
            list.appendTag(value);
        }
        result.setTag("placements", list);
        return result;
    }

    public static List<Entry> read(NBTTagCompound data, int chunkX, int chunkZ, int minY, int maxY) {
        if (!(data.getTag("version") instanceof net.minecraft.nbt.NBTTagInt) || data.getInteger("version") != VERSION
                || !data.getString("dimension").equals("minecraft:overworld")
                || !(data.getTag("placements") instanceof NBTTagList)) {
            throw new IllegalArgumentException("Unrecognised pending-repair format");
        }
        NBTTagList list = data.getTagList("placements", 10);
        if (list.tagCount() != ((NBTTagList) data.getTag("placements")).tagCount() || list.tagCount() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid pending-repair list");
        }
        Set<BlockPos> positions = new HashSet<>();
        List<Entry> result = new ArrayList<>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound value = list.getCompoundTagAt(index);
            BlockPos position = BlockPos.fromLong(value.getLong("position"));
            ResourceLocation material = parseId(value.getString("material"));
            int level = value.getInteger("level");
            if (!(value.getTag("position") instanceof net.minecraft.nbt.NBTTagLong) || material == null || level < 1 || level > 3
                    || position.getX() >> 4 != chunkX || position.getZ() >> 4 != chunkZ
                    || position.getY() <= minY || position.getY() >= maxY || !positions.add(position)) {
                throw new IllegalArgumentException("Invalid or repeated pending-repair position");
            }
            result.add(new Entry(position, state(value, "cap"), material, level, value.getBoolean("cave"),
                    state(value, "original_support"),
                    value.hasKey("dirtified_support") ? state(value, "dirtified_support") : null,
                    state(value, "expected_support")));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private static NBTTagCompound writeState(IBlockState state) {
        // Keep our saved format stable when Minecraft changes its own state NBT.
        NBTTagCompound saved = new NBTTagCompound();
        saved.setString("Name", Block.REGISTRY.getNameForObject(state.getBlock()).toString());
        NBTTagCompound properties = new NBTTagCompound();
        for (IProperty<?> property : state.getPropertyKeys()) {
            writeProperty(properties, state, property);
        }
        if (!properties.isEmpty()) {
            saved.setTag("Properties", properties);
        }
        return saved;
    }

    private static <T extends Comparable<T>> void writeProperty(NBTTagCompound properties, IBlockState state,
            IProperty<T> property) {
        properties.setString(property.getName(), property.getName(state.getValue(property)));
    }

    private static IBlockState state(NBTTagCompound value, String key) {
        if (!(value.getTag(key) instanceof NBTTagCompound)) {
            throw new IllegalArgumentException("Missing repair state: " + key);
        }
        NBTTagCompound saved = value.getCompoundTag(key);
        ResourceLocation block = parseId(saved.getString("Name"));
        if (block == null || !Block.REGISTRY.containsKey(block)) {
            throw new IllegalArgumentException("Unavailable repair block: " + saved.getString("Name"));
        }
        IBlockState state = Block.REGISTRY.getObject(block).getDefaultState();
        NBTTagCompound properties = saved.getCompoundTag("Properties");
        for (String name : properties.getKeySet()) {
            IProperty<?> property = state.getBlock().getBlockState().getProperty(name);
            if (property == null || !(properties.getTag(name) instanceof net.minecraft.nbt.NBTTagString)) {
                throw new IllegalArgumentException("Invalid repair property: " + name);
            }
            state = withProperty(state, property, properties.getString(name));
        }
        // Minecraft otherwise accepts unknown properties and substitutes defaults. A repair must match exactly.
        if (!writeState(state).equals(saved) || state.getBlock().hasTileEntity(state)) {
            throw new IllegalArgumentException("Invalid repair state: " + saved);
        }
        return state;
    }

    private static <T extends Comparable<T>> IBlockState withProperty(IBlockState state,
            IProperty<T> property, String value) {
        com.google.common.base.Optional<T> parsed = property.parseValue(value);
        if (!parsed.isPresent()) {
            throw new IllegalArgumentException("Invalid repair property: " + property.getName());
        }
        return state.withProperty(property, parsed.get());
    }

    private static ResourceLocation parseId(String value) {
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return null;
        return new ResourceLocation(value);
    }
}
