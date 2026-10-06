package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Two-pass loaded-only surface decoration; it never obtains or generates a chunk. */
public final class SmoothingEngine {
    private static final int[] DX={0,1,0,-1}, DZ={-1,0,1,0}, CORNER_X={-1,1,1,-1}, CORNER_Z={-1,-1,1,1};
    private final MaterialCatalogue catalogue;
    private ExistingChunkIndex existing;
    private final Set<Long> processed = new HashSet<>();
    private final ThreadLocal<Decisions> decisions=ThreadLocal.withInitial(Decisions::new);
    public long passes, placed, elapsedNanos, skippedExisting;
    public SmoothingEngine(MaterialCatalogue catalogue) { this.catalogue=catalogue; }
    public void start(File worldDirectory) {
        try { existing=new ExistingChunkIndex(worldDirectory); }
        catch(IOException failure){throw new IllegalStateException("Cannot safely index existing chunks; terrain smoothing stopped",failure);}
        processed.clear();passes=placed=elapsedNanos=skippedExisting=0;
        SkysTerrainSmoother.logger.info("Indexed {} saved Overworld chunks; no smoothing retrogen will run",existing.size());
    }
    public void stop() { existing=null;processed.clear();decisions.remove(); }
    public int indexedChunks() { return existing==null?0:existing.size(); }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void beforeDecoration(DecorateBiomeEvent.Pre event) {
        World world=event.getWorld();
        if(world.isRemote||world.provider.getDimension()!=0||!SkysTerrainSmoother.enabled||existing==null)return;
        int chunkX=event.getPos().getX()>>4,chunkZ=event.getPos().getZ()>>4;
        if(existing.contains(chunkX,chunkZ)){++skippedExisting;return;}
        Chunk owner=loaded(world,chunkX,chunkZ);
        if(owner==null||!processed.add(ExistingChunkIndex.key(chunkX,chunkZ)))return;
        long start=System.nanoTime();
        placed+=smooth(world,owner,SkysTerrainSmoother.level);
        elapsedNanos+=System.nanoTime()-start;++passes;
    }

    /** Engine entry point also used by controlled geometry probes; normal generation uses the event. */
    public int smooth(World world,Chunk owner,int level) {
        Footprint.validateLevel(level);
        Decisions buffer=decisions.get();Arrays.fill(buffer.results,null);
        int startX=owner.xPosition<<4,startZ=owner.zPosition<<4;
        List<StructureBoundingBox> structures=StructureProtection.bounds(world,startX,startZ);
        boolean grassAllowed=GrassSlabsApi.grassGenerationAllowed();
        for(int z=0;z<16;++z)for(int x=0;x<16;++x) {
            int index=x|(z<<4),y=owner.getHeightValue(x,z)-1;
            if(y<0||y>=255)continue;
            BlockPos support=new BlockPos(startX+x,y,startZ+z),target=support.up();
            IBlockState lower=owner.getBlockState(support),above=owner.getBlockState(target);
            TerrainMaterial material=catalogue.source(lower);
            if(material==null||material.grass&&!grassAllowed||material.mineralogy&&!SkysTerrainSmoother.mineralogy||!lower.isSideSolid(world,support,EnumFacing.UP)||
                    !above.getBlock().isAir(above,world,target)||above.getMaterial()==Material.WATER||above.getMaterial()==Material.LAVA||
                    lower.getBlock().hasTileEntity(lower)||owner.getTileEntity(target,Chunk.EnumCreateEntityType.CHECK)!=null||
                    StructureProtection.contains(structures,target)||StructureProtection.contains(structures,support)||
                    !catalogue.allows(material,world.getBiome(target),0,target))continue;
            int cardinal=0,diagonal=0;
            for(int direction=0;direction<4;++direction)
                if(higher(world,owner,x+DX[direction],z+DZ[direction],y))cardinal|=1<<direction;
            if(level==3) {
                for(int direction=0;direction<4;++direction)
                    if(higher(world,owner,x+CORNER_X[direction],z+CORNER_Z[direction],y))diagonal|=1<<direction;
            }
            IBlockState result=catalogue.result(material,Footprint.mask(cardinal,diagonal,level),level);
            if(result!=null){buffer.y[index]=y+1;buffer.results[index]=result;buffer.supports[index]=material.supportAfterPlacement;}
        }
        int count=0;
        for(int index=0;index<256;++index)if(buffer.results[index]!=null) {
            BlockPos target=new BlockPos(startX+(index&15),buffer.y[index],startZ+(index>>4));
            IBlockState above=owner.getBlockState(target);
            if(!above.getBlock().isAir(above,world,target))continue;
            if(owner.setBlockState(target,buffer.results[index])!=null) {
                if(buffer.supports[index]!=null)owner.setBlockState(target.down(),buffer.supports[index]);
                ++count;
            }
        }
        return count;
    }
    private boolean higher(World world,Chunk owner,int localX,int localZ,int lowerY) {
        if(!Footprint.allowedNeighbour(localX,localZ))return false;
        int worldX=(owner.xPosition<<4)+localX,worldZ=(owner.zPosition<<4)+localZ;
        Chunk neighbour=loaded(world,worldX>>4,worldZ>>4);
        if(neighbour==null)return false;
        int y=neighbour.getHeightValue(worldX&15,worldZ&15)-1;
        if(y<0)return false;
        BlockPos pos=new BlockPos(worldX,y,worldZ);
        IBlockState state=neighbour.getBlockState(pos);
        TerrainMaterial piece=catalogue.piece(state);
        if(piece!=null) {
            IBlockState support=neighbour.getBlockState(pos.down());
            if(piece.sources.contains(support)||piece.supportAfterPlacement!=null&&support.equals(piece.supportAfterPlacement)) { --y;state=piece.sources.iterator().next(); }
        }
        return y==lowerY+1&&catalogue.source(state)!=null;
    }
    private static Chunk loaded(World world,int x,int z) {
        return world.getChunkProvider() instanceof ChunkProviderServer?
                ((ChunkProviderServer)world.getChunkProvider()).getLoadedChunk(x,z):null;
    }
    private static final class Decisions {
        final int[] y=new int[256];
        final IBlockState[] results=new IBlockState[256],supports=new IBlockState[256];
    }
}
