package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Saves real pending work with an incomplete Sable halo, then resumes it in a later process or chunk load. */
final class PersistentRepairProbe {
    private static final int CHUNK_X = -2050;
    private static final int CHUNK_Z = -2049;
    private static final BlockPos TARGET = new BlockPos((CHUNK_X << 4) + 15, 200, (CHUNK_Z << 4) + 15);
    private static final BlockPos EDITED_CAP = TARGET.offset(-2, 0, 0);
    private static final BlockPos EDITED_SUPPORT = TARGET.offset(-4, 0, 0);
    private static final BlockPos UNSMOOTHED = TARGET.offset(-7, 0, -7);
    private static int stage;
    private static int waited;
    private static int initialFull;
    private static volatile boolean requested;
    private static volatile Throwable failure;
    private static boolean returnedFromUnload;
    private static boolean unloaded;

    static void unloaded(ChunkEvent.Unload event) {
        if (event.getChunk().getPos().x() == CHUNK_X && event.getChunk().getPos().z() == CHUNK_Z) {
            unloaded = true;
        }
    }

    static boolean tick(ServerLevel world, String phase) throws Exception {
        RuntimeProbe.require(++waited < 2400, "pending repair fixture finishes within its time bound");
        if (failure != null) {
            throw new IllegalStateException("Fixture chunk request failed", failure);
        }
        boolean reload = !phase.equals("fresh");
        boolean unload = Boolean.getBoolean("skysterrainsmoother.repairUnload");
        boolean playerEdit = Boolean.getBoolean("skysterrainsmoother.repairPlayerEdit");
        TerrainMaterial grass = MaterialCatalogue.instance().source(Blocks.GRASS_BLOCK.defaultBlockState());
        BlockState cap = grass.slab;

        if (stage == 0) {
            Field guard = SkysTerrainSmoother.engine.getClass().getDeclaredField("physicsNeighbourGuard");
            guard.setAccessible(true);
            guard.setBoolean(SkysTerrainSmoother.engine, true);
            request(world, false);
            stage = 1;
            return false;
        }
        if (stage == 1 && requested) {
            LevelChunk owner = owner(world);
            if (owner == null) {
                return false;
            }
            initialFull = full(world, 1);
            if (phase.equals("final")) {
                RuntimeProbe.require(!queued(), "completed or cancelled work is not replayed on a second restart");
                checkResults(world, cap, playerEdit);
                return complete(phase);
            }
            RuntimeProbe.require(initialFull < 9, "only the owner is loaded before the fixture requests neighbours");
            if (reload || returnedFromUnload) {
                if (reload) {
                    RuntimeProbe.require(!SkysTerrainSmoother.engine.generate(world, CHUNK_X, CHUNK_Z),
                            "saved owner remains excluded from initial generation");
                }
                RuntimeProbe.require(queued(), "unfinished repair survives restart or chunk unload");
                RuntimeProbe.require(owner.getBlockState(TARGET).equals(cap), "deferred cap remains before neighbours arrive");
                if (phase.equals("paused")) {
                    SkysTerrainSmoother.enabled = false;
                    stage = 5;
                    waited = 0;
                    return false;
                }
                if (playerEdit) {
                    // A real player break event also retires an identical break-and-replace operation.
                    ServerPlayer player = new ServerPlayer(world.getServer(), world,
                            new GameProfile(java.util.UUID.randomUUID(), "RepairTest"),
                            net.minecraft.server.level.ClientInformation.createDefault());
                    net.neoforged.neoforge.event.level.block.BreakBlockEvent cancelled = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(world, TARGET, cap, player);
                    cancelled.setCanceled(true);
                    NeoForge.EVENT_BUS.post(cancelled);
                    RuntimeProbe.require(queued(), "a cancelled break does not retire the repair");
                    String action = System.getProperty("skysterrainsmoother.repairEditAction", "break");
                    switch (action) {
                        case "break" -> NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(world, TARGET, cap, player));
                        case "place" -> NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                                net.neoforged.neoforge.common.util.BlockSnapshot.create(world.dimension(), world, TARGET),
                                cap, player));
                        case "tool", "bonemeal" -> {
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                                    new net.minecraft.world.item.ItemStack(action.equals("tool")
                                            ? net.minecraft.world.item.Items.IRON_SHOVEL : net.minecraft.world.item.Items.BONE_MEAL));
                            BlockPos interacted = action.equals("tool") ? TARGET : UNSMOOTHED.below();
                            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                                    player, net.minecraft.world.InteractionHand.MAIN_HAND, interacted,
                                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(interacted),
                                            net.minecraft.core.Direction.UP, interacted, false)));
                        }
                        default -> throw new IllegalArgumentException("Unknown fixture action: " + action);
                    }
                    RuntimeProbe.require(!queued(), "player edits retire nearby pending work");
                    java.nio.file.Files.writeString(world.getServer().getWorldPath(
                            net.minecraft.world.level.storage.LevelResource.ROOT).resolve("repair-fixture-player-cap.txt"),
                            owner.getBlockState(TARGET).toString());
                } else {
                    // Simulate changes made by another mod or an offline world editor, without a player event.
                    raw(owner, EDITED_CAP, Blocks.GOLD_BLOCK.defaultBlockState());
                    raw(owner, EDITED_SUPPORT.below(), Blocks.GOLD_BLOCK.defaultBlockState());
                    owner.markUnsaved();
                }
                request(world, true);
                stage = 3;
                waited = 0;
                return false;
            }

            List<Object> placements = new ArrayList<>();
            for (BlockPos position : List.of(TARGET, EDITED_CAP, EDITED_SUPPORT)) {
                raw(owner, position.below(), Blocks.AIR.defaultBlockState());
                raw(owner, position, cap);
                placements.add(placement(position, cap, grass));
            }
            // A valid new floor in this saved owner would get smoothed if a resumed repair ran the general scan.
            raw(owner, UNSMOOTHED.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            raw(owner, UNSMOOTHED, Blocks.AIR.defaultBlockState());
            raw(owner, UNSMOOTHED.north(), Blocks.STONE.defaultBlockState());
            Heightmap.primeHeightmaps(owner, EnumSet.allOf(Heightmap.Types.class));
            owner.markUnsaved();
            Field field = SkysTerrainSmoother.engine.getClass().getDeclaredField("pending");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Long, List<Object>> pending = (Map<Long, List<Object>>) field.get(SkysTerrainSmoother.engine);
            pending.put(key(), Collections.synchronizedList(placements));
            SkysTerrainSmoother.engine.loaded(new ChunkEvent.Load(owner, false));
            stage = 2;
            waited = 0;
            return false;
        }
        if (stage == 2 && waited >= 10) {
            RuntimeProbe.require(queued() && owner(world).getBlockState(TARGET).equals(cap), "repair waits for a loaded halo");
            RuntimeProbe.require(full(world, 1) == initialFull, "waiting does not force neighbours to load");
            if (unload) {
                ChunkPos position = new ChunkPos(CHUNK_X, CHUNK_Z);
                world.getChunkSource().removeTicketWithRadius(TicketType.FORCED, position, 0);
                stage = 4;
                waited = 0;
                return false;
            }
            return complete(phase);
        }
        if (stage == 5 && waited >= 10) {
            RuntimeProbe.require(queued() && owner(world).getBlockState(TARGET).equals(cap),
                    "the master switch pauses outstanding work without losing it");
            RuntimeProbe.require(full(world, 1) == initialFull, "paused work loads no neighbours");
            return complete(phase);
        }
        if (stage == 4 && unloaded && owner(world) == null) {
            RuntimeProbe.require(!queued(), "unloaded work is not run without its owner");
            returnedFromUnload = true;
            request(world, false);
            stage = 1;
            waited = 0;
            return false;
        }
        if (stage == 3 && requested && !queued()) {
            RuntimeProbe.require(full(world, 1) == 9 && full(world, 2) == 9, "repair uses only the requested FULL halo");
            checkResults(world, cap, playerEdit);
            return complete(phase);
        }
        return false;
    }

    private static void checkResults(ServerLevel world, BlockState cap, boolean playerEdit) throws java.io.IOException {
        LevelChunk chunk = owner(world);
        String playerCap = playerEdit ? java.nio.file.Files.readString(world.getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT).resolve("repair-fixture-player-cap.txt")) : "";
        RuntimeProbe.require(playerEdit ? chunk.getBlockState(TARGET).toString().equals(playerCap) : chunk.getBlockState(TARGET).isAir(),
                "only an unchanged, uncancelled recorded cap is repaired");
        RuntimeProbe.require(chunk.getBlockState(EDITED_CAP).equals(playerEdit ? cap : Blocks.GOLD_BLOCK.defaultBlockState()),
                "changed target is retained");
        RuntimeProbe.require(chunk.getBlockState(EDITED_SUPPORT).equals(cap), "cap with changed support is retained");
        RuntimeProbe.require(chunk.getBlockState(EDITED_SUPPORT.below()).equals(
                playerEdit ? Blocks.AIR.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState()), "changed support is retained");
        RuntimeProbe.require(chunk.getBlockState(UNSMOOTHED).isAir(), "resume does not rescan or smooth an unrecorded saved floor");
        RuntimeProbe.require(SkysTerrainSmoother.engine.passes == 0 && SkysTerrainSmoother.engine.placed == 0,
                "only recorded repairs run, without a new generation pass or extra placements");
    }

    private static Object placement(BlockPos position, BlockState cap, TerrainMaterial material) throws Exception {
        Class<?> type = Class.forName("zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine$FeatureDecorator");
        Constructor<?> constructor = type.getDeclaredConstructor(BlockPos.class, BlockState.class,
                TerrainMaterial.class, int.class, boolean.class, BlockState.class);
        constructor.setAccessible(true);
        return constructor.newInstance(position, cap, material, 3, false, Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private static boolean complete(String phase) {
        SkysTerrainSmoother.logger.info("TERRAIN_PERSISTENT_REPAIR_PASS phase=" + phase);
        return true;
    }

    private static long key() {
        return ((long) CHUNK_X << 32) | (CHUNK_Z & 0xffffffffL);
    }

    private static LevelChunk owner(ServerLevel world) {
        return world.getChunkSource().getChunkNow(CHUNK_X, CHUNK_Z);
    }

    private static boolean queued() {
        return SkysTerrainSmoother.engine.repairQueued(CHUNK_X, CHUNK_Z);
    }

    private static int full(ServerLevel world, int radius) {
        int count = 0;
        for (int chunkZ = CHUNK_Z - radius; chunkZ <= CHUNK_Z + radius; chunkZ++) {
            for (int chunkX = CHUNK_X - radius; chunkX <= CHUNK_X + radius; chunkX++) {
                if (world.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void request(ServerLevel world, boolean halo) {
        requested = false;
        Thread worker = new Thread(() -> {
            try {
                for (int chunkZ = CHUNK_Z - (halo ? 1 : 0); chunkZ <= CHUNK_Z + (halo ? 1 : 0); chunkZ++) {
                    for (int chunkX = CHUNK_X - (halo ? 1 : 0); chunkX <= CHUNK_X + (halo ? 1 : 0); chunkX++) {
                        ChunkPos position = new ChunkPos(chunkX, chunkZ);
                        CompletableFuture<Void> acknowledged = new CompletableFuture<>();
                        world.getServer().execute(() -> {
                            world.getChunkSource().addTicketWithRadius(TicketType.FORCED, position, 0);
                            acknowledged.complete(null);
                        });
                        acknowledged.get(30, TimeUnit.SECONDS);
                        var result = world.getChunkSource().getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true)
                                .get(120, TimeUnit.SECONDS);
                        if (!result.isSuccess()) {
                            throw new IllegalStateException(result.getError());
                        }
                    }
                }
                requested = true;
            } catch (Throwable problem) {
                failure = problem;
            }
        }, "terrain-persistence-fixture");
        worker.setDaemon(true);
        worker.start();
    }

    private static void raw(LevelChunk chunk, BlockPos position, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(position.getY())).setBlockState(
                position.getX() & 15, position.getY() & 15, position.getZ() & 15, state, false);
    }
}
