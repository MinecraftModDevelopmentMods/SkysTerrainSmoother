package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class ProbeItems {
    static ServerPlayer player(ServerLevel world, GameProfile profile) {
        return new ServerPlayer(world.getServer(), world, profile);
    }

    static CompoundTag custom(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
    }

    private ProbeItems() {}
}
