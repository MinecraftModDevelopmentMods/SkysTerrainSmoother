package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.OreBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
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
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(world.getChunk(chunkX, chunkZ)));
    }

    static void repair(Level world, BlockPos origin) {
        queue(world, origin.getX() >> 4, origin.getZ() >> 4);
        MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, world, () -> true));
    }

    static void light(Level world, LightLayer type, BlockPos position, int value) {
        DataLayer data = new DataLayer();
        java.util.Arrays.fill(data.getData(), (byte) (value | value << 4));
        world.getChunkSource().getLightEngine().queueSectionData(type, SectionPos.of(position), data, true);
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
        if (state.hasBlockEntity() || state.getMaterial().isLiquid()) {
            return false;
        }
        return state.getBlock() instanceof OreBlock || state.getBlock() instanceof RedStoneOreBlock
                || state.is(TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("forge", "ores")));
    }

    static net.minecraft.world.level.chunk.LevelChunkSection emptySection(Level world,
            net.minecraft.world.level.chunk.LevelChunk chunk, int index) {
        return new net.minecraft.world.level.chunk.LevelChunkSection(chunk.getMinSection() + index,
                world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY));
    }

    private ProbeWorld() {
    }
}
