package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.nbt.CompoundTag;

/** Internal bridge carried by Minecraft's serialized chunk snapshot. */
public interface PendingChunkData {
    void skysterrainsmoother$setPending(CompoundTag data);
}
