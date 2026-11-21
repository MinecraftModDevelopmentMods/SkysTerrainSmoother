package zone.moddev.mc.skysterrainsmoother.probe.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysterrainsmoother.probe.ChunkReadGuard;

/** Build-only observation; it never changes Minecraft's chunk lookup result. */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ChunkRequestMixin {
    @Inject(method = "getChunk", at = @At("HEAD"))
    private void beforeChunkRequest(int chunkX, int chunkZ, ChunkStatus status, boolean create,
            CallbackInfoReturnable<ChunkAccess> result) {
        if (ChunkReadGuard.active()) {
            ChunkReadGuard.request(chunkX, chunkZ, create,
                    ((ServerChunkCache) (Object) this).getChunkNow(chunkX, chunkZ) != null);
        }
    }
}
