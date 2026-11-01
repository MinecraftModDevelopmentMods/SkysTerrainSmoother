package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
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
        world.setBlockState(position, Blocks.AIR.getDefaultState(), 2);
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
        world.getChunkProvider().getLightManager().setData(type, SectionPos.from(position), data);
        net.minecraft.world.server.ServerWorld serverWorld = (net.minecraft.world.server.ServerWorld) world;
        net.minecraft.world.server.ServerWorldLightManager light = serverWorld.getChunkProvider().getLightManager();
        java.util.concurrent.CompletableFuture<?> barrier = light.lightChunk(world.getChunkAt(position), true);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        serverWorld.getServer().driveUntil(() -> {
            light.func_215588_z_();
            return barrier.isDone() || System.nanoTime() >= deadline;
        });
        RuntimeProbe.require(barrier.isDone(), "Native lighting completed the fixture update");
        barrier.join();
    }

    static void rebuildHeights(net.minecraft.world.chunk.IChunk chunk) {
        net.minecraft.world.gen.Heightmap.updateChunkHeightmaps(chunk,
                java.util.EnumSet.allOf(net.minecraft.world.gen.Heightmap.Type.class));
    }

    static boolean oreSupport(BlockState state) {
        if (state.hasTileEntity() || state.getMaterial().isLiquid()) {
            return false;
        }
        Tag<net.minecraft.block.Block> ores = BlockTags.getCollection().get(new ResourceLocation("forge", "ores"));
        return state.getBlock() instanceof OreBlock || state.getBlock() instanceof RedstoneOreBlock
                || ores != null && ores.contains(state.getBlock());
    }

    private ProbeWorld() {
    }
}
