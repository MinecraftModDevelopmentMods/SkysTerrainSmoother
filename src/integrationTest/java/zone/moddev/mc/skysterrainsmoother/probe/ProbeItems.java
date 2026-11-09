package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

final class ProbeItems {
    private ProbeItems() {
    }

    static ServerPlayer player(ServerLevel world, GameProfile profile) {
        return new ServerPlayer(world.getServer(), world, profile, ClientInformation.createDefault());
    }

    static CompoundTag custom(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
