package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.event.level.ChunkEvent;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Native Sable boundary regression, runnable against released and candidate jars. */
final class DeferredRepairProbe {
    private static final int X = 2048, Z = 2049;
    private static final BlockPos TARGET = new BlockPos((X << 4) + 15, 200, (Z << 4) + 8);
    private static int stage, waited, initialFull;
    private static volatile boolean requested;
    private static volatile Throwable failure;
    private static BlockState cap;

    static boolean tick(ServerLevel world, String phase) throws Exception {
        RuntimeProbe.require(SkysTerrainSmoother.enabled && SkysTerrainSmoother.level == 3,
                "deferred repairs retain enabled level-three smoothing");
        RuntimeProbe.require(net.neoforged.fml.ModList.get().isLoaded("sable"), "native Sable is installed");
        if (failure != null) throw new IllegalStateException("Native chunk request failed", failure);
        RuntimeProbe.require(++waited < 2400, "deferred repair fixture completes within its time bound");
        boolean reload = phase.contains("reload");
        if (stage == 0) {
            request(world, false);stage = 1;return false;
        }
        if (stage == 1 && requested) {
            LevelChunk owner = world.getChunkSource().getChunkNow(X, Z);
            if (owner == null) return false;
            if (reload) {
                RuntimeProbe.require(SkysTerrainSmoother.engine.indexedChunks() > 0,
                        "saved chunks remain protected from retrogen");
                RuntimeProbe.require(owner.getBlockState(TARGET).isAir(), "completed repair survives restart");
                RuntimeProbe.require(!queued(), "saved owners do not acquire new smoothing repairs");
                return complete(world, phase);
            }
            initialFull = full(world, 1);
            RuntimeProbe.require(initialFull < 9, "fixture starts without a full physics halo");
            TerrainMaterial grass = MaterialCatalogue.instance().source(Blocks.GRASS_BLOCK.defaultBlockState());
            cap = grass.slab;
            // Only fixture setup uses raw section writes. Production repairs below
            // still go through LevelChunk.setBlockState and Sable's real hooks.
            raw(owner, TARGET.below(), Blocks.AIR.defaultBlockState());raw(owner, TARGET, cap);
            Heightmap.primeHeightmaps(owner, EnumSet.allOf(Heightmap.Types.class));owner.setUnsaved(true);
            Class<?> entry = Class.forName("zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine$FeatureDecorator");
            Constructor<?> constructor = entry.getDeclaredConstructor(BlockPos.class, BlockState.class,
                    TerrainMaterial.class, int.class, boolean.class, BlockState.class);
            constructor.setAccessible(true);
            Object placement = constructor.newInstance(TARGET, cap, grass, 3, false, Blocks.GRASS_BLOCK.defaultBlockState());
            Field field = SkysTerrainSmoother.engine.getClass().getDeclaredField("pending");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Long,List<Object>> pending = (Map<Long,List<Object>>)field.get(SkysTerrainSmoother.engine);
            pending.put(ownerKey(), Collections.synchronizedList(new ArrayList<>(List.of(placement))));
            SkysTerrainSmoother.engine.loaded(new ChunkEvent.Load(owner, false));
            waited = 0;stage = 2;return false;
        }
        if (stage == 2) {
            if (waited < 10) return false;
            RuntimeProbe.require(world.getChunkSource().getChunkNow(X,Z).getBlockState(TARGET).equals(cap),
                    "late repair must wait while physics neighbours are absent");
            RuntimeProbe.require(queued(), "waiting repair is retained rather than dropped");
            RuntimeProbe.require(full(world,1) == initialFull, "waiting repair must not load physics neighbours");
            SkysTerrainSmoother.logger.info("TERRAIN_REPAIR_DEFERRED_PASS full_halo="+initialFull);
            request(world, true);stage = 3;waited = 0;return false;
        }
        if (stage == 3 && requested) {
            if (queued()) return false;
            RuntimeProbe.require(full(world,1)==9, "requested neighbours have arrived");
            RuntimeProbe.require(world.getChunkSource().getChunkNow(X,Z).getBlockState(TARGET).isAir(),
                    "late repair completes after the normal neighbour load");
            RuntimeProbe.require(full(world,2)==9, "physics callbacks do not load another full ring");
            return complete(world, phase);
        }
        return false;
    }

    private static boolean complete(ServerLevel world,String phase) throws Exception {
        Path marker=world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("deferred-repair-"+phase+".txt");
        Files.writeString(marker,"complete=true\nsmoothing_enabled=true\nnormal_physics_notifications=true\n");
        SkysTerrainSmoother.logger.info("TERRAIN_DEFERRED_REPAIR_PASS phase="+phase);return true;
    }

    private static boolean queued() throws Exception {
        // The released jar has a Set here; reflection keeps the same native
        // regression runnable as a genuine red test against 0.1.2.
        Object engine=SkysTerrainSmoother.engine;
        try{return (boolean)engine.getClass().getMethod("repairQueued",int.class,int.class).invoke(engine,X,Z);}
        catch(NoSuchMethodException absent){Field f=engine.getClass().getDeclaredField("dirtyOwners");f.setAccessible(true);return ((Set<?>)f.get(engine)).contains(ownerKey());}
    }

    private static long ownerKey() { return ((long)X << 32) | (Z & 0xffffffffL); }

    private static void request(ServerLevel world,boolean halo) {
        requested=false;
        Thread worker=new Thread(()->{
            try{
                for(int z=Z-(halo?1:0);z<=Z+(halo?1:0);z++)for(int x=X-(halo?1:0);x<=X+(halo?1:0);x++){
                    if(halo && x==X && z==Z)continue;
                    ChunkPos pos=new ChunkPos(x,z);CompletableFuture<Void> ack=new CompletableFuture<>();
                    world.getServer().execute(()->{world.getChunkSource().addRegionTicket(TicketType.FORCED,pos,0,pos);ack.complete(null);});
                    ack.get(30,TimeUnit.SECONDS);
                    var result=world.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true).get(120,TimeUnit.SECONDS);
                    if(!result.isSuccess())throw new IllegalStateException(result.getError());
                }
                requested=true;
            }catch(Throwable ex){failure=ex;}
        },"terrain-repair-fixture-requests");worker.setDaemon(true);worker.start();
    }

    private static int full(ServerLevel world,int radius) {
        int count=0;for(int z=Z-radius;z<=Z+radius;z++)for(int x=X-radius;x<=X+radius;x++)if(world.getChunkSource().getChunkNow(x,z)!=null)count++;return count;
    }

    private static void raw(LevelChunk chunk,BlockPos pos,BlockState state) {
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX()&15,pos.getY()&15,pos.getZ()&15,state,false);
    }
}
