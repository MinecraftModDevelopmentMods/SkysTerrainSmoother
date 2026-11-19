package zone.moddev.mc.skysterrainsmoother.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.internal.PendingRepairData;
import zone.moddev.mc.skysterrainsmoother.internal.PendingChunkData;

@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin implements PendingChunkData {
    @Unique
    private CompoundTag skysterrainsmoother$pendingRepairs;

    @Override
    public void skysterrainsmoother$setPending(CompoundTag data) {
        skysterrainsmoother$pendingRepairs = data;
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void skysterrainsmoother$readMarkers(LevelHeightAccessor height, PalettedContainerFactory palettes,
            CompoundTag root, CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (data != null) {
            CompoundTag markers = root.getCompoundOrEmpty(PendingRepairData.KEY).copy();
            if (!markers.isEmpty()) {
                ((PendingChunkData) (Object) data).skysterrainsmoother$setPending(markers);
            }
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void skysterrainsmoother$loadMarkers(ServerLevel world, PoiManager poi, RegionStorageInfo storage,
            ChunkPos position, CallbackInfoReturnable<ProtoChunk> callback) {
        ProtoChunk chunk = callback.getReturnValue();
        ChunkAccess loaded = chunk instanceof ImposterProtoChunk wrapper ? wrapper.getWrapped() : chunk;
        SkysTerrainSmoother.engine.readRepairs(world, loaded, skysterrainsmoother$pendingRepairs);
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void skysterrainsmoother$copyMarkers(ServerLevel world, ChunkAccess chunk,
            CallbackInfoReturnable<SerializableChunkData> callback) {
        CompoundTag markers = SkysTerrainSmoother.engine.saveRepairs(world, chunk);
        if (markers != null) {
            ((PendingChunkData) (Object) callback.getReturnValue()).skysterrainsmoother$setPending(markers);
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void skysterrainsmoother$writeMarkers(CallbackInfoReturnable<CompoundTag> callback) {
        if (skysterrainsmoother$pendingRepairs != null) {
            callback.getReturnValue().put(PendingRepairData.KEY, skysterrainsmoother$pendingRepairs.copy());
        }
    }
}
