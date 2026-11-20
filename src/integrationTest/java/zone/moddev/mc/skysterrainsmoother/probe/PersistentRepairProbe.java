package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Real chunk-save and unload checks in a deliberately incomplete distant halo. */
final class PersistentRepairProbe {
    private static final int CHUNK_X = -2050;
    private static final int CHUNK_Z = -2049;
    private static final BlockPos TARGET = new BlockPos((CHUNK_X << 4) + 15, 200, (CHUNK_Z << 4) + 15);
    private static final BlockPos EDITED_CAP = TARGET.add(-2, 0, 0);
    private static final BlockPos EDITED_SUPPORT = TARGET.add(-4, 0, 0);
    private static final BlockPos UNSMOOTHED = TARGET.add(-7, 0, -7);
    private static int stage;
    private static int waited;
    private static int initialFull;
    private static long initialPasses;
    private static long initialPlacements;
    private static boolean returnedFromUnload;

    static boolean tick(WorldServer world, String phase) throws Exception {
        RuntimeProbe.require(++waited < 2400, "pending repair fixture finishes within its time bound");
        boolean oldControl = Boolean.getBoolean("skysterrainsmoother.repairLegacyControl");
        boolean unload = Boolean.getBoolean("skysterrainsmoother.repairUnload");
        boolean playerEdit = Boolean.getBoolean("skysterrainsmoother.repairPlayerEdit");
        TerrainMaterial grass = MaterialCatalogue.instance().source(Blocks.GRASS.getDefaultState());
        IBlockState cap = grass.slab;

        if (stage == 0) {
            initialPasses = SkysTerrainSmoother.engine.passes;
            initialPlacements = SkysTerrainSmoother.engine.placed;
            try {
                Field guard = SkysTerrainSmoother.engine.getClass().getDeclaredField("physicsNeighbourGuard");
                guard.setAccessible(true);
                guard.setBoolean(SkysTerrainSmoother.engine, true);
            } catch (NoSuchFieldException missing) {
                if (!oldControl) throw missing;
            }
            request(world, false);
            stage = 1;
            waited = 0;
            return false;
        }
        if (stage == 1) {
            Chunk owner = owner(world);
            RuntimeProbe.require(owner != null, "the native chunk provider loaded the owner");
            initialFull = full(world, 1);
            if (phase.equals("final")) {
                RuntimeProbe.require(!pending().containsKey(key()), "completed work stays retired after restart");
                results(world, cap, playerEdit);
                return complete(phase);
            }
            RuntimeProbe.require(initialFull < 9, "the distant owner starts without a complete halo");
            if (!phase.equals("fresh") || returnedFromUnload) {
                RuntimeProbe.require(pending().containsKey(key()), "unfinished repair survives restart or chunk unload");
                RuntimeProbe.require(SkysTerrainSmoother.engine.repairQueued(CHUNK_X, CHUNK_Z), "restored owner is queued");
                RuntimeProbe.require(owner.getBlockState(TARGET).equals(cap),
                        "the deferred cap survives unchanged: expected=" + cap + "; actual=" + owner.getBlockState(TARGET));
                if (phase.equals("paused")) {
                    SkysTerrainSmoother.enabled = false;
                    stage = 5;
                    waited = 0;
                    return false;
                }
                if (playerEdit) {
                    net.minecraft.entity.player.EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);
                    BlockEvent.BreakEvent cancelled = new BlockEvent.BreakEvent(world, TARGET, cap, player);
                    cancelled.setCanceled(true);
                    MinecraftForge.EVENT_BUS.post(cancelled);
                    RuntimeProbe.require(pending().containsKey(key()), "a cancelled break retains pending work");
                    if (System.getProperty("skysterrainsmoother.repairEditAction", "break").equals("bonemeal")) {
                        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player,
                                EnumHand.MAIN_HAND, UNSMOOTHED.down(), EnumFacing.UP, new Vec3d(UNSMOOTHED)));
                    } else {
                        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, TARGET, cap, player));
                    }
                    RuntimeProbe.require(!pending().containsKey(key()), "player interaction retires nearby work");
                } else {
                    raw(owner, EDITED_CAP, Blocks.GOLD_BLOCK.getDefaultState());
                    raw(owner, EDITED_SUPPORT.down(), Blocks.GOLD_BLOCK.getDefaultState());
                    owner.markDirty();
                }
                request(world, true);
                stage = 3;
                waited = 0;
                return false;
            }

            List<Object> placements = new ArrayList<>();
            for (BlockPos position : new BlockPos[] {TARGET, EDITED_CAP, EDITED_SUPPORT}) {
                raw(owner, position.down(), Blocks.AIR.getDefaultState());
                raw(owner, position, cap);
                placements.add(placement(position, cap, grass));
            }
            raw(owner, UNSMOOTHED.down(), Blocks.GRASS.getDefaultState());
            raw(owner, UNSMOOTHED, Blocks.AIR.getDefaultState());
            raw(owner, UNSMOOTHED.north(), Blocks.STONE.getDefaultState());
            owner.generateSkylightMap();
            owner.markDirty();
            pending().put(key(), placements);
            MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(owner));
            stage = 2;
            waited = 0;
            if (oldControl) return complete(phase);
            return false;
        }
        if ((stage == 2 || stage == 5) && waited >= 10) {
            RuntimeProbe.require(pending().containsKey(key()) && owner(world).getBlockState(TARGET).equals(cap),
                    "waiting or paused work retains the original cap");
            RuntimeProbe.require(full(world, 1) == initialFull, "waiting never requests a neighbouring chunk");
            if (unload && stage == 2) {
                world.getChunkProvider().saveChunks(true);
                world.getChunkProvider().queueUnload(owner(world));
                stage = 4;
                waited = 0;
                return false;
            }
            return complete(phase);
        }
        if (stage == 4 && owner(world) == null) {
            RuntimeProbe.require(!SkysTerrainSmoother.engine.repairQueued(CHUNK_X, CHUNK_Z), "unloaded owners do not run");
            net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
            returnedFromUnload = true;
            request(world, false);
            stage = 1;
            waited = 0;
            return false;
        }
        if (stage == 3 && !SkysTerrainSmoother.engine.repairQueued(CHUNK_X, CHUNK_Z)) {
            results(world, cap, playerEdit);
            return complete(phase);
        }
        return false;
    }

    private static void request(WorldServer world, boolean halo) {
        boolean enabled = SkysTerrainSmoother.enabled;
        SkysTerrainSmoother.enabled = false;
        try {
            for (int chunkZ = CHUNK_Z - (halo ? 1 : 0); chunkZ <= CHUNK_Z + (halo ? 1 : 0); chunkZ++) {
                for (int chunkX = CHUNK_X - (halo ? 1 : 0); chunkX <= CHUNK_X + (halo ? 1 : 0); chunkX++) {
                    world.getChunk(chunkX, chunkZ);
                }
            }
        } finally {
            SkysTerrainSmoother.enabled = enabled;
        }
    }

    private static void results(WorldServer world, IBlockState cap, boolean playerEdit) {
        Chunk owner = owner(world);
        RuntimeProbe.require(owner.getBlockState(TARGET).equals(playerEdit ? cap : Blocks.AIR.getDefaultState()),
                "only unchanged recorded caps are repaired");
        RuntimeProbe.require(owner.getBlockState(EDITED_CAP).equals(playerEdit ? cap : Blocks.GOLD_BLOCK.getDefaultState()),
                "changed targets are preserved");
        RuntimeProbe.require(owner.getBlockState(EDITED_SUPPORT).equals(cap), "changed supports keep their cap");
        RuntimeProbe.require(owner.getBlockState(EDITED_SUPPORT.down()).equals(playerEdit
                ? Blocks.AIR.getDefaultState() : Blocks.GOLD_BLOCK.getDefaultState()), "changed support is preserved");
        RuntimeProbe.require(owner.getBlockState(UNSMOOTHED).getBlock() == Blocks.AIR,
                "resume never scans unrecorded terrain");
        RuntimeProbe.require(SkysTerrainSmoother.engine.passes == initialPasses
                && SkysTerrainSmoother.engine.placed == initialPlacements,
                "recorded repairs do not create a new generation pass or extra placements");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, List<Object>> pending() throws Exception {
        Field field = SkysTerrainSmoother.engine.getClass().getDeclaredField("pending");
        field.setAccessible(true);
        return (Map<Long, List<Object>>) field.get(SkysTerrainSmoother.engine);
    }

    private static Object placement(BlockPos position, IBlockState state, TerrainMaterial material) throws Exception {
        Class<?> type = Class.forName("zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine$Placement");
        Constructor<?> constructor = type.getDeclaredConstructor(BlockPos.class, IBlockState.class,
                TerrainMaterial.class, int.class, boolean.class, IBlockState.class);
        constructor.setAccessible(true);
        return constructor.newInstance(position, state, material, 3, false, Blocks.GRASS.getDefaultState());
    }

    private static long key() {
        return ((long) CHUNK_X << 32) | (CHUNK_Z & 0xffffffffL);
    }

    private static Chunk owner(WorldServer world) {
        return world.getChunkProvider().getLoadedChunk(CHUNK_X, CHUNK_Z);
    }

    private static int full(WorldServer world, int radius) {
        int count = 0;
        for (int chunkZ = CHUNK_Z - radius; chunkZ <= CHUNK_Z + radius; chunkZ++) {
            for (int chunkX = CHUNK_X - radius; chunkX <= CHUNK_X + radius; chunkX++) {
                if (world.getChunkProvider().getLoadedChunk(chunkX, chunkZ) != null) count++;
            }
        }
        return count;
    }

    private static void raw(Chunk owner, BlockPos position, IBlockState state) {
        owner.setBlockState(position, state);
    }

    private static boolean complete(String phase) {
        SkysTerrainSmoother.logger.info("TERRAIN_PERSISTENT_REPAIR_PASS phase=" + phase);
        return true;
    }
}
