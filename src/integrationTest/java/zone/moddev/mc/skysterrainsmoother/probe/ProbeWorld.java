package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.event.level.ChunkEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Native events and controlled fixture writes, used only by the runtime checks. */
final class ProbeWorld {
    static void clear(Level world, BlockPos position) {
        // Fixture resets remove fluids too; World.removeBlock deliberately preserves them.
        world.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
    }

    static boolean generate(Level world, BlockPos origin) {
        return SkysTerrainSmoother.engine.generate(world, origin.getX() >> 4, origin.getZ() >> 4);
    }

    static void queue(Level world, int chunkX, int chunkZ) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(world.getChunk(chunkX, chunkZ), false));
    }

    static void repair(Level world, BlockPos origin) {
        queue(world, origin.getX() >> 4, origin.getZ() >> 4);
        MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent.Post(LogicalSide.SERVER, world, () -> true));
    }

    static void light(Level world, LightLayer type, BlockPos position, int value) {
        DataLayer data = new DataLayer();
        java.util.Arrays.fill(data.getData(), (byte) (value | value << 4));
        world.getChunkSource().getLightEngine().queueSectionData(type, SectionPos.of(position), data);
        net.minecraft.server.level.ServerLevel serverWorld = (net.minecraft.server.level.ServerLevel) world;
        net.minecraft.server.level.ThreadedLevelLightEngine light = serverWorld.getChunkSource().getLightEngine();
        java.util.concurrent.CompletableFuture<?> barrier = light.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        serverWorld.getServer().managedBlock(() -> {
            serverWorld.getChunkSource().pollTask();
            light.tryScheduleUpdate();
            return barrier.isDone() || System.nanoTime() >= deadline;
        });
        RuntimeProbe.require(barrier.isDone(), "Native lighting completed the fixture update");
        barrier.join();
    }

    static void rebuildHeights(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                java.util.EnumSet.allOf(net.minecraft.world.level.levelgen.Heightmap.Types.class));
    }

    static boolean oreSupport(BlockState state) {
        if (state.hasBlockEntity() || state.liquid()) {
            return false;
        }
        return state.is(net.minecraftforge.common.Tags.Blocks.ORES);
    }

    static net.minecraft.world.level.chunk.LevelChunkSection emptySection(Level world) {
        return new net.minecraft.world.level.chunk.LevelChunkSection(
                world.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
    }

    private ProbeWorld() {
    }
}
