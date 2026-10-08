package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Two-pass loaded-only floor decoration; it never obtains or generates a chunk. */
public final class SmoothingEngine {
    private static final int[] DX={0,1,0,-1}, DZ={-1,0,1,0}, CORNER_X={-1,1,1,-1}, CORNER_Z={-1,-1,1,1};
    private final MaterialCatalogue catalogue;
    private ExistingChunkIndex existing;
    private final Set<Long> processed = new HashSet<>();
    private final Set<Long> populated = new HashSet<>(), dirtyOwners = new HashSet<>();
    private final Map<Long,List<Placement>> pending = new HashMap<>();
    private final Map<IBlockState,Boolean> oreSupports = new HashMap<>();
    private final ThreadLocal<Decisions> decisions=ThreadLocal.withInitial(Decisions::new);
    public long passes, placed, elapsedNanos, skippedExisting;
    public SmoothingEngine(MaterialCatalogue catalogue) { this.catalogue=catalogue; }
    public void start(File worldDirectory) {
        try { existing=new ExistingChunkIndex(worldDirectory); }
        catch(IOException failure){throw new IllegalStateException("Cannot safely index existing chunks; terrain smoothing stopped",failure);}
        processed.clear();populated.clear();dirtyOwners.clear();pending.clear();oreSupports.clear();passes=placed=elapsedNanos=skippedExisting=0;
        SkysTerrainSmoother.logger.info("Indexed {} saved Overworld chunks; no smoothing retrogen will run",existing.size());
    }
    public void stop() { existing=null;processed.clear();populated.clear();dirtyOwners.clear();pending.clear();oreSupports.clear();decisions.remove(); }
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

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void afterDecoration(DecorateBiomeEvent.Post event) {
        World world=event.getWorld();
        if(world.isRemote||world.provider.getDimension()!=0)return;
        int chunkX=event.getPos().getX()>>4,chunkZ=event.getPos().getZ()>>4;
        checkAdjacent(world,chunkX,chunkZ);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void afterPopulation(PopulateChunkEvent.Post event) {
        World world=event.getWorld();
        if(world.isRemote||world.provider.getDimension()!=0)return;
        populated.add(ExistingChunkIndex.key(event.getChunkX(),event.getChunkZ()));
        checkAdjacent(world,event.getChunkX(),event.getChunkZ());
    }
    private void checkAdjacent(World world,int chunkX,int chunkZ) {
        // Legacy decoration uses an eight-block offset and can touch the next
        // chunk east/south. Each repair is restricted to its recorded owner.
        for(int z=chunkZ;z<=chunkZ+1;z++)for(int x=chunkX;x<=chunkX+1;x++) {
            long key=ExistingChunkIndex.key(x,z);List<Placement> placements=pending.get(key);
            if(placements==null)continue;
            validate(world,x,z,placements);
            dirtyOwners.add(key);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void endWorldTick(TickEvent.WorldTickEvent event) {
        World world=event.world;
        if(event.phase!=TickEvent.Phase.END||world.isRemote||world.provider.getDimension()!=0)return;
        // Biome-specific ores and Forge world generators can run after Decorate
        // Post. Validate only freshly touched owners after the full call returns.
        for(long key:new ArrayList<>(dirtyOwners)) {
            List<Placement> placements=pending.get(key);if(placements==null)continue;
            int x=(int)(key>>32),z=(int)key;
            validate(world,x,z,placements);
            if(populated.contains(key)&&populated.contains(ExistingChunkIndex.key(x-1,z))&&
                    populated.contains(ExistingChunkIndex.key(x,z-1))&&populated.contains(ExistingChunkIndex.key(x-1,z-1)))pending.remove(key);
        }
        dirtyOwners.clear();
    }
    @SubscribeEvent public void unload(net.minecraftforge.event.world.ChunkEvent.Unload event) {
        Chunk owner=event.getChunk();World world=event.getWorld();
        if(world.isRemote||world.provider.getDimension()!=0)return;
        long key=ExistingChunkIndex.key(owner.xPosition,owner.zPosition);
        List<Placement> placements=pending.remove(key);dirtyOwners.remove(key);
        if(placements!=null)validate(world,owner.xPosition,owner.zPosition,placements);
    }
    private void validate(World world,int chunkX,int chunkZ,List<Placement> placements) {
        Chunk owner=loaded(world,chunkX,chunkZ);
        if(owner==null)return;
        // Decide the repairs from one snapshot. Only replace our unchanged cap,
        // and never restore terrain overwritten by the biome decorator.
        Map<Placement,IBlockState> repairs=new LinkedHashMap<>();
        Map<Placement,TerrainMaterial> materials=new HashMap<>();
        List<StructureBoundingBox> structures=StructureProtection.bounds(world,chunkX<<4,chunkZ<<4);
        for(Placement placement:placements) {
            IBlockState support=owner.getBlockState(placement.pos.down());
            TerrainMaterial material=placement.material;
            boolean blocked=placement.cave&&!caveHeadroom(world,owner,placement.pos.down());
            // Late ore veins may hide beneath a cap. Keep both blocks intact;
            // this exception does not make ores eligible smoothing sources.
            if((blocked||!material.sources.contains(support)&&!support.equals(material.supportAfterPlacement)&&!oreSupport(support))&&
                    owner.getBlockState(placement.pos).equals(placement.state)) {
                TerrainMaterial replacement=catalogue.source(support);
                IBlockState result=null;
                if(!blocked&&replacement!=null&&(!replacement.grass||GrassSlabsApi.grassGenerationAllowed())&&
                        (!replacement.mineralogy||SkysTerrainSmoother.mineralogy)&&
                        support.isSideSolid(world,placement.pos.down(),EnumFacing.UP)&&
                        !support.getBlock().hasTileEntity(support)&&owner.getTileEntity(placement.pos,Chunk.EnumCreateEntityType.CHECK)==null&&
                        !StructureProtection.contains(structures,placement.pos)&&!StructureProtection.contains(structures,placement.pos.down())&&
                        catalogue.allows(replacement,world.getBiome(placement.pos),0,placement.pos)) {
                    int x=placement.pos.getX()&15,z=placement.pos.getZ()&15,y=placement.pos.getY()-1,cardinal=0,diagonal=0;
                    for(int d=0;d<4;d++)if(higher(world,owner,x+DX[d],z+DZ[d],y))cardinal|=1<<d;
                    if(placement.level==3)for(int d=0;d<4;d++)if(higher(world,owner,x+CORNER_X[d],z+CORNER_Z[d],y))diagonal|=1<<d;
                    result=catalogue.result(replacement,Footprint.mask(cardinal,diagonal,placement.level),placement.level);
                }
                repairs.put(placement,result==null?Blocks.AIR.getDefaultState():result);
                materials.put(placement,replacement);
            }
        }
        for(Map.Entry<Placement,IBlockState> repair:repairs.entrySet()) {
            Placement placement=repair.getKey();
            if(!owner.getBlockState(placement.pos).equals(placement.state))continue;
            IBlockState state=repair.getValue();
            owner.setBlockState(placement.pos,state);
            placement.state=state;placement.material=materials.get(placement);
            if(placement.material!=null&&state.getBlock()!=Blocks.AIR&&placement.material.supportAfterPlacement!=null)
                owner.setBlockState(placement.pos.down(),placement.material.supportAfterPlacement);
        }
        placements.removeIf(p->p.state.getBlock()==Blocks.AIR);
        // Biome decoration can introduce new grass oases as well as beaches.
        // Re-snapshot registered dry floors only in this tracked fresh owner;
        // occupied targets and existing saved chunks remain untouched.
        placed+=smooth(world,owner,SkysTerrainSmoother.level,true);
    }

    /** Calculate all placements before writing. Normal generation calls this through the event. */
    public int smooth(World world,Chunk owner,int level) {
        return smooth(world,owner,level,false);
    }
    private int smooth(World world,Chunk owner,int level,boolean afterDecoration) {
        Footprint.validateLevel(level);
        long key=ExistingChunkIndex.key(owner.xPosition,owner.zPosition);
        if(afterDecoration&&!pending.containsKey(key))return 0;
        Decisions buffer=decisions.get();buffer.clear();
        int startX=owner.xPosition<<4,startZ=owner.zPosition<<4;
        List<StructureBoundingBox> structures=StructureProtection.bounds(world,startX,startZ);
        boolean grassAllowed=GrassSlabsApi.grassGenerationAllowed();
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        for(int z=0;z<16;++z)for(int x=0;x<16;++x) {
            int surfaceY=owner.getHeightValue(x,z)-1;
            // Examine upward-facing floors at every depth, not cave ceilings or
            // solid walls. All decisions for all floors precede the first write.
            if(surfaceY<0)continue;
            IBlockState previous=owner.getBlockState(cursor.setPos(startX+x,Math.min(surfaceY,254)+1,startZ+z));
            for(int y=Math.min(surfaceY,254);y>=0;--y) {
                IBlockState lower=owner.getBlockState(cursor.setPos(startX+x,y,startZ+z));
                IBlockState above=previous;previous=lower;
                // Look up mappings only at full-block/air boundaries, not for
                // every block in solid rock or every cell in an empty chamber.
                if(above.getMaterial()!=Material.AIR||lower.getMaterial()==Material.AIR)continue;
                TerrainMaterial material=catalogue.source(lower);
                if(material==null||material.grass&&!grassAllowed||material.mineralogy&&!SkysTerrainSmoother.mineralogy)continue;
                BlockPos support=new BlockPos(startX+x,y,startZ+z),target=support.up();
                boolean cave=y<surfaceY;
                if(cave&&!caveHeadroom(world,owner,support)||!lower.isSideSolid(world,support,EnumFacing.UP)||
                        !above.getBlock().isAir(above,world,target)||lower.getBlock().hasTileEntity(lower)||
                        owner.getTileEntity(target,Chunk.EnumCreateEntityType.CHECK)!=null||
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
                if(result!=null)buffer.add(x|z<<4|(y+1)<<8,result,material,cave);
            }
        }
        int count=0;
        List<Placement> placements=new ArrayList<>();
        for(int index=0;index<buffer.size;++index) {
            int position=buffer.positions[index];
            BlockPos target=new BlockPos(startX+(position&15),position>>8,startZ+((position>>4)&15));
            IBlockState above=owner.getBlockState(target);
            if(!above.getBlock().isAir(above,world,target))continue;
            if(owner.setBlockState(target,buffer.results[index])!=null) {
                if(buffer.supports[index]!=null)owner.setBlockState(target.down(),buffer.supports[index]);
                placements.add(new Placement(target,buffer.results[index],buffer.materials[index],level,buffer.caves[index]));
                ++count;
            }
        }
        if(afterDecoration)pending.get(key).addAll(placements);
        // Even an initially flat or unsupported surface may gain eligible floors
        // during decoration. Track it until its bordering decorators finish.
        else {pending.put(key,placements);dirtyOwners.add(key);}
        return count;
    }
    private boolean caveHeadroom(World world,Chunk owner,BlockPos support) {
        // Three air blocks before adding a half-height cap leave 2.5 blocks of
        // headroom. Never turn an otherwise walkable two-block tunnel into 1.5.
        if(support.getY()>252)return false;
        for(int height=2;height<=3;height++) {
            BlockPos pos=support.up(height);IBlockState state=owner.getBlockState(pos);
            if(state.getMaterial()!=Material.AIR||!state.getBlock().isAir(state,world,pos)||
                    owner.getTileEntity(pos,Chunk.EnumCreateEntityType.CHECK)!=null)return false;
        }
        return true;
    }
    private boolean oreSupport(IBlockState state) {
        Boolean cached=oreSupports.get(state);if(cached!=null)return cached;
        boolean ore=false;
        if(state.isFullCube()&&!state.getBlock().hasTileEntity(state)&&!state.getMaterial().isLiquid()) {
            ore=state.getBlock() instanceof BlockOre||state.getBlock() instanceof BlockRedstoneOre;
            if(!ore) {
                ItemStack stack=new ItemStack(state.getBlock(),1,state.getBlock().getMetaFromState(state));
                if(stack.getItem()!=null)for(int id:OreDictionary.getOreIDs(stack)) {
                    if(OreDictionary.getOreName(id).startsWith("ore")){ore=true;break;}
                }
            }
        }
        oreSupports.put(state,ore);return ore;
    }
    private boolean higher(World world,Chunk owner,int localX,int localZ,int lowerY) {
        if(!Footprint.allowedNeighbour(localX,localZ))return false;
        int worldX=(owner.xPosition<<4)+localX,worldZ=(owner.zPosition<<4)+localZ;
        Chunk neighbour=loaded(world,worldX>>4,worldZ>>4);
        if(neighbour==null)return false;
        // Compare this elevation both inside caves and across cave entrances.
        // Ignore roofs/walls and normalize previously placed caps, including
        // transparent BOP caps over their unregistered native dirt counterparts.
        BlockPos floor=new BlockPos(worldX,lowerY+1,worldZ);
        IBlockState state=neighbour.getBlockState(floor),above=neighbour.getBlockState(floor.up());
        TerrainMaterial source=catalogue.source(state),cap=catalogue.piece(above);
        if(cap!=null)return source!=null||cap.sources.contains(state)||cap.supportAfterPlacement!=null&&state.equals(cap.supportAfterPlacement)||oreSupport(state);
        return source!=null&&above.getMaterial()==Material.AIR&&above.getBlock().isAir(above,world,floor.up());
    }
    private static Chunk loaded(World world,int x,int z) {
        return world.getChunkProvider() instanceof ChunkProviderServer?
                ((ChunkProviderServer)world.getChunkProvider()).getLoadedChunk(x,z):null;
    }
    private static final class Decisions {
        int size;
        int[] positions=new int[256];
        IBlockState[] results=new IBlockState[256],supports=new IBlockState[256];
        TerrainMaterial[] materials=new TerrainMaterial[256];
        boolean[] caves=new boolean[256];
        void clear() {
            Arrays.fill(results,0,size,null);Arrays.fill(supports,0,size,null);Arrays.fill(materials,0,size,null);size=0;
        }
        void add(int position,IBlockState result,TerrainMaterial material,boolean cave) {
            if(size==positions.length) {
                int capacity=positions.length*2;
                positions=Arrays.copyOf(positions,capacity);results=Arrays.copyOf(results,capacity);
                supports=Arrays.copyOf(supports,capacity);materials=Arrays.copyOf(materials,capacity);caves=Arrays.copyOf(caves,capacity);
            }
            positions[size]=position;results[size]=result;supports[size]=material.supportAfterPlacement;materials[size]=material;caves[size++]=cave;
        }
    }
    private static final class Placement {
        final BlockPos pos;
        IBlockState state;
        TerrainMaterial material;
        final int level;
        final boolean cave;
        Placement(BlockPos pos,IBlockState state,TerrainMaterial material,int level,boolean cave){this.pos=pos;this.state=state;this.material=material;this.level=level;this.cave=cave;}
    }
}
