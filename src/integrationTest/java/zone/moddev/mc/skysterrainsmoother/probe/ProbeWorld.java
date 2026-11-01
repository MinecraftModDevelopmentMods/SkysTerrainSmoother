package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.LogicalSide;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;

/** Native events and controlled fixture writes, used only by the runtime checks. */
final class ProbeWorld {
    static void clear(World world, BlockPos position) {
        // Fixture resets remove fluids too; World.removeBlock deliberately preserves them.
        world.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
    }

    static boolean generate(World world, BlockPos origin) {
        return SkysTerrainSmoother.engine.generate(world, origin.getX() >> 4, origin.getZ() >> 4);
    }

    static void queue(World world, int chunkX, int chunkZ) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(world.getChunk(chunkX, chunkZ)));
    }

    static void repair(World world, BlockPos origin) {
        queue(world, origin.getX() >> 4, origin.getZ() >> 4);
        MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, world));
    }

    static void light(World world, LightType type, BlockPos position, int value) {
        NibbleArray data = new NibbleArray();
        java.util.Arrays.fill(data.getData(), (byte) (value | value << 4));
        world.getChunkSource().getLightEngine().queueSectionData(type, SectionPos.of(position), data, true);
        net.minecraft.world.server.ServerWorld serverWorld = (net.minecraft.world.server.ServerWorld) world;
        net.minecraft.world.server.ServerWorldLightManager light = serverWorld.getChunkSource().getLightEngine();
        java.util.concurrent.CompletableFuture<?> barrier = light.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        serverWorld.getServer().managedBlock(() -> {
            light.tryScheduleUpdate();
            return barrier.isDone() || System.nanoTime() >= deadline;
        });
        RuntimeProbe.require(barrier.isDone(), "Native lighting completed the fixture update");
        barrier.join();
    }

    static void rebuildHeights(net.minecraft.world.chunk.IChunk chunk) {
        net.minecraft.world.gen.Heightmap.primeHeightmaps(chunk,
                java.util.EnumSet.allOf(net.minecraft.world.gen.Heightmap.Type.class));
    }

    static boolean oreSupport(BlockState state) {
        if (state.hasTileEntity() || state.getMaterial().isLiquid()) {
            return false;
        }
        ITag<net.minecraft.block.Block> ores = BlockTags.getAllTags().getTag(new ResourceLocation("forge", "ores"));
        return state.getBlock() instanceof OreBlock || state.getBlock() instanceof RedstoneOreBlock
                || ores != null && ores.contains(state.getBlock());
    }

    private ProbeWorld() {
    }
}
