package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysgrassslabs.block.GrassSpread;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.*;
import zone.moddev.mc.skysterrainsmoother.api.*;
import zone.moddev.mc.skysterrainsmoother.internal.*;

/** Build-only integration contributor and real-world probe, excluded from ordinary launches. */
@Mod(modid="terrainsmootherprobe",name="Terrain Smoother Runtime Probe",version="1",dependencies="required-after:skysterrainsmoother;after:skysbuildingpieces;after:mineralogy")
public final class RuntimeProbe {
    @SidedProxy(clientSide="zone.moddev.mc.skysterrainsmoother.probe.ClientProbe",serverSide="zone.moddev.mc.skysterrainsmoother.probe.ProbeProxy")
    public static ProbeProxy proxy;
    private MinecraftServer server;
    private boolean ran;
    @Mod.EventHandler public void pre(FMLPreInitializationEvent event){MinecraftForge.EVENT_BUS.register(this);proxy.init();}
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        IBlockState[] steps=new IBlockState[4],corners=new IBlockState[4];
        EnumFacing[] directions={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
        for(int d=0;d<4;d++) {
            steps[d]=BuildingPiecesApi.bottomPiece("minecraft:obsidian",BuildingPiecesApi.TerrainShape.STEP,directions[d]);
            corners[d]=BuildingPiecesApi.bottomPiece("minecraft:obsidian",BuildingPiecesApi.TerrainShape.CORNER,directions[d]);
        }
        ResourceLocation id=new ResourceLocation("probe:obsidian");
        TerrainSmoothingApi.registerMaterial(new TerrainMaterial(id,Collections.singleton(Blocks.OBSIDIAN.getDefaultState()),
                BuildingPiecesApi.bottomPiece("minecraft:obsidian",BuildingPiecesApi.TerrainShape.SLAB,EnumFacing.NORTH),null,null,false,null));
        TerrainSmoothingApi.addShapes(id,steps,corners);
        // A later Mineralogy add-on can supply finer geometry without replacing its native slab.
        if(Loader.isModLoaded("mineralogy")&&SkysTerrainSmoother.mineralogy&&
                Block.REGISTRY.containsKey(new ResourceLocation("mineralogy:basalt_slab"))) {
            for(int d=0;d<4;d++) {
                steps[d]=BuildingPiecesApi.bottomPiece("minecraft:quartz_block",BuildingPiecesApi.TerrainShape.STEP,directions[d]);
                corners[d]=BuildingPiecesApi.bottomPiece("minecraft:quartz_block",BuildingPiecesApi.TerrainShape.CORNER,directions[d]);
            }
            TerrainSmoothingApi.addShapes(new ResourceLocation("mineralogy:basalt"),steps,corners);
        }
        TerrainSmoothingApi.addAreaProtection((dimension,pos,biome)->pos.getY()==241);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event){server=FMLCommonHandler.instance().getMinecraftServerInstance();}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(server==null||ran||event.phase!=TickEvent.Phase.END)return;ran=true;
        try {
            WorldServer world=server.worldServerForDimension(0);
            world.getGameRules().setOrCreateGameRule("randomTickSpeed","0");
            require(GrassSlabsApi.hasSmoothingOwner(),"ownership claimed before Grass Slabs startup");
            require(SkysTerrainSmoother.engine.indexedChunks()>0==phase().equals("reload"),"saved-chunk index lifecycle");
            boolean late=false;try{TerrainSmoothingApi.addBiomeRule((id,biome)->true);}catch(IllegalStateException expected){late=true;}
            require(late,"late API registration rejected");
            int gameplay=persistence(world)+geometry(world)+grass(world)+generationOrder(world);
            int generation=generate(world);
            Properties values=new Properties();File marker=new File(world.getSaveHandler().getWorldDirectory(),"skysterrainsmoother-integration.properties");
            if(marker.isFile())try(InputStream in=new FileInputStream(marker)){values.load(in);}
            values.setProperty(phase()+"_complete","true");values.setProperty("gameplay_checks",Integer.toString(gameplay));values.setProperty("generation_checks",Integer.toString(generation));
            try(OutputStream out=new FileOutputStream(marker)){values.store(out,"Terrain Smoother disposable verification");}
            System.out.println("TERRAIN_SMOOTHER_RUNTIME_PASS phase="+phase()+" level="+SkysTerrainSmoother.level+" gameplay="+gameplay+" generated="+generation);
        }catch(Throwable failure){failure.printStackTrace();throw new IllegalStateException("Terrain Smoother probe failed",failure);}
        if(System.getProperty("skysterrainsmoother.integrationPhase","").startsWith("client-"))ClientStatus.complete=true;
        else server.initiateShutdown();
    }
    private static String phase(){return System.getProperty("skysterrainsmoother.integrationPhase","fresh").replace("client-","");}
    private static int geometry(WorldServer world) {
        BlockPos p=new BlockPos(1000,200,1000);Chunk owner=world.getChunkFromBlockCoords(p);
        // Reset previous controlled-probe roofs before testing the same fixture on reload.
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)for(int y=203;y<=245;y++)world.setBlockToAir(new BlockPos(p.getX()+x,y,p.getZ()+z));
        MaterialCatalogue catalogue=MaterialCatalogue.instance();TerrainMaterial stone=catalogue.source(Blocks.STONE.getDefaultState());
        int checks=0;int[] dx={0,1,0,-1},dz={-1,0,1,0},cx={-1,1,1,-1},cz={-1,-1,1,1};
        for(int level=1;level<=3;level++)for(int cardinal=0;cardinal<16;cardinal++)for(int diagonal=0;diagonal<16;diagonal++) {
            for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
                world.setBlockState(p.add(x,0,z),Blocks.STONE.getDefaultState(),2);
                world.setBlockToAir(p.add(x,1,z));world.setBlockToAir(p.add(x,2,z));
            }
            for(int d=0;d<4;d++) {
                if((cardinal&(1<<d))!=0)world.setBlockState(p.add(dx[d],1,dz[d]),Blocks.STONE.getDefaultState(),2);
                if((diagonal&(1<<d))!=0)world.setBlockState(p.add(cx[d],1,cz[d]),Blocks.STONE.getDefaultState(),2);
            }
            IBlockState expected=catalogue.result(stone,Footprint.mask(cardinal,diagonal,level),level);
            SkysTerrainSmoother.engine.smooth(world,owner,level);
            require(world.getBlockState(p.up()).equals(expected==null?Blocks.AIR.getDefaultState():expected),"snapshot footprint "+level+"/"+cardinal+"/"+diagonal);checks++;
        }
        world.setBlockState(p,Blocks.GRASS.getDefaultState(),2);world.setBlockToAir(p.up());world.setBlockState(p.east().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        if(GrassSlabsApi.grassGenerationAllowed())require(world.getBlockState(p).equals(Blocks.DIRT.getDefaultState()),"support dirtified after grass placement");
        else require(world.getBlockState(p).getBlock()==Blocks.GRASS&&world.isAirBlock(p.up()),"grass switch authoritative");
        world.setBlockState(p.up(),Blocks.GLASS.getDefaultState(),2);SkysTerrainSmoother.engine.smooth(world,owner,3);
        require(world.getBlockState(p.up()).getBlock()==Blocks.GLASS,"occupied target protected");
        BlockPos veto=p.up(40);world.setBlockState(veto,Blocks.STONE.getDefaultState(),2);world.setBlockToAir(veto.up());world.setBlockState(veto.east().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,3);require(world.isAirBlock(veto.up()),"protected area veto");
        long start=System.nanoTime();for(int i=0;i<256;i++)SkysTerrainSmoother.engine.smooth(world,owner,3);
        System.out.println("TERRAIN_SMOOTHER_BENCHMARK passes=256 mean_ns="+(System.nanoTime()-start)/256);
        return checks+3;
    }
    private static int persistence(WorldServer world) {
        int index=0;
        for(Shape shape:new Shape[]{Shape.HORIZONTAL_STEP,Shape.CORNER})for(int direction=0;direction<shape.states;direction++)for(String material:new String[]{"minecraft:grass","minecraft:dirt"}) {
            BlockPos pos=new BlockPos(1500+index,210,1500);IBlockState expected=Pieces.state(material,shape,direction);index++;
            if(phase().equals("reload"))require(world.getBlockState(pos).equals(expected),"saved fine piece retains identity and orientation");
            else world.setBlockState(pos,expected,2);
        }
        BlockPos saved=new BlockPos(1600,210,1600);
        world.getChunkFromBlockCoords(saved);
        world.setBlockState(saved,Blocks.STONE.getDefaultState(),2);world.setBlockState(saved.east().up(),Blocks.STONE.getDefaultState(),2);world.setBlockToAir(saved.up());
        if(phase().equals("reload")) {
            long skipped=SkysTerrainSmoother.engine.skippedExisting;
            SkysTerrainSmoother.engine.beforeDecoration(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Pre(world,new Random(1),new BlockPos(saved.getX()&~15,0,saved.getZ()&~15)));
            require(world.isAirBlock(saved.up()),"saved unpopulated chunks are never smoothed");
            if(SkysTerrainSmoother.enabled)require(SkysTerrainSmoother.engine.skippedExisting==skipped+1,"existing entry skips before terrain access");
        }
        return index;
    }
    private static int grass(WorldServer world) {
        int checks=0;BlockPos p=new BlockPos(1020,210,1020);world.getChunkFromBlockCoords(p);world.getChunkFromBlockCoords(p.add(-8,0,-8));world.getChunkFromBlockCoords(p.add(8,0,8));
        for(Shape shape:new Shape[]{Shape.HORIZONTAL_STEP,Shape.VERTICAL_STEP,Shape.CORNER,Shape.STAIRS})for(int direction=0;direction<shape.states;direction++) {
            IBlockState dirt=Pieces.state("minecraft:dirt",shape,direction),grass=Pieces.state("minecraft:grass",shape,direction);
            require(GrassSlabsApi.grassFor(dirt).equals(grass)&&GrassSlabsApi.dirtFor(grass).equals(dirt),"shared orientation mapping");
            world.setBlockToAir(p.up());world.setBlockState(p,dirt,2);world.setLightFor(EnumSkyBlock.SKY,p.up(),15);
            require(GrassSpread.growTarget(world,p)&&world.getBlockState(p).equals(grass),"cross-mod grass growth preserves orientation");
            PieceBlock piece=(PieceBlock)grass.getBlock();
            boolean completeTop=Geometry.completeTop(piece.mask(grass));
            require(piece.canGrow(world,p,grass,false)==completeTop&&piece.canUseBonemeal(world,new Random(1),p,grass)==completeTop,"bonemeal requires a complete upper supporting face");
            require(!piece.canGrow(world,p,dirt,false),"dirt forms do not accept grass bonemeal");
            world.setBlockState(p.north(),Blocks.SNOW_LAYER.getDefaultState(),2);
            require(piece.getActualState(grass,world,p).getValue(net.minecraft.block.BlockGrass.SNOWY),"fine grass snow visual");
            world.setBlockToAir(p.north());
            require(!piece.getActualState(grass,world,p).getValue(net.minecraft.block.BlockGrass.SNOWY),"fine grass snow clears");
            world.setBlockState(p.down(),Blocks.GRASS.getDefaultState(),2);grass.getBlock().neighborChanged(grass,world,p,Blocks.GRASS);
            require(world.getBlockState(p.down()).getBlock()==Blocks.DIRT,"piece support dirtification");
            require(!GrassSpread.growTarget(world,p.down()),"no grass beneath grass pieces");
            world.setBlockState(p,dirt,2);world.setBlockState(p.up(),ModBlocks.GRASS_SLAB.getDefaultState(),2);
            require(!GrassSpread.growTarget(world,p),"covered dirt piece rejected");
            world.setBlockState(p,grass,2);world.setBlockState(p.up(),Blocks.STONE.getDefaultState(),2);
            world.setLightFor(EnumSkyBlock.SKY,p.up(),0);world.setLightFor(EnumSkyBlock.BLOCK,p.up(),0);
            grass.getBlock().updateTick(world,p,grass,new Random(1));require(world.getBlockState(p).equals(dirt),"covered decay keeps orientation");checks+=10;
        }
        return checks;
    }
    private static int generate(WorldServer world) {
        FakePlayer player=FakePlayerFactory.get(world,new GameProfile(UUID.fromString("4f5b04d6-9050-4e39-a06c-7793a3ffbe18"),"TerrainProbe"));
        world.getPlayerChunkMap().setPlayerViewRadius(4);
        long before=SkysTerrainSmoother.engine.passes;int found=0;
        for(int attempt=0;attempt<4;attempt++) {
            int centerX=400+attempt*40+(phase().equals("reload")?200:0),centerZ=400;
            player.setPosition(centerX*16+8,90,centerZ*16+8);world.getPlayerChunkMap().addPlayer(player);
            try {
                for(int tick=0;tick<100;tick++){world.getPlayerChunkMap().tick();world.getChunkProvider().tick();}
                for(int z=centerZ-4;z<=centerZ+4;z++)for(int x=centerX-4;x<=centerX+4;x++) {
                    Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
                    for(ExtendedBlockStorage section:chunk.getBlockStorageArray())if(section!=null)
                        for(int y=0;y<16;y++)for(int zz=0;zz<16;zz++)for(int xx=0;xx<16;xx++) {
                            TerrainMaterial m=MaterialCatalogue.instance().piece(section.get(xx,y,zz));if(m!=null)found++;
                        }
                }
            }finally{world.getPlayerChunkMap().removePlayer(player);}
            if(found>0)break;
        }
        if(SkysTerrainSmoother.enabled)require(SkysTerrainSmoother.engine.passes>before&&found>0,"normal player-tracked generation creates pieces");
        else require(SkysTerrainSmoother.engine.passes==before&&found==0,"disabled master has no duplicate grass pass");
        return found;
    }
    private static int generationOrder(WorldServer world) throws Exception {
        for(int level=1;level<=3;level++) {
            byte[][] hashes=new byte[2][];
            for(int order=0;order<2;order++) {
                int origin=order==0?128:256;
                List<Chunk> chunks=new ArrayList<>();
                for(int z=0;z<9;z++)for(int x=0;x<9;x++)chunks.add(world.getChunkFromChunkCoords(origin+x,128+z));
                for(int z=0;z<144;z++)for(int x=0;x<144;x++) {
                    int height=200+Math.floorMod(x*31+z*17+(x>>3)*(z>>3),3);
                    Chunk chunk=chunks.get((x>>4)+(z>>4)*9);
                    // Bulk fixture construction avoids relighting 20,736 artificially raised
                    // columns one by one. The actual smoothing pass still uses real chunk writes.
                    ExtendedBlockStorage section=chunk.getBlockStorageArray()[12];
                    if(section==null)chunk.getBlockStorageArray()[12]=section=new ExtendedBlockStorage(192,true);
                    for(int y=200;y<205;y++)section.set(x&15,y&15,z&15,Blocks.AIR.getDefaultState());
                    section.set(x&15,height&15,z&15,Blocks.STONE.getDefaultState());
                    chunk.getHeightMap()[(x&15)|((z&15)<<4)]=height+1;
                }
                if(order==1)Collections.reverse(chunks);
                for(Chunk chunk:chunks)SkysTerrainSmoother.engine.smooth(world,chunk,level);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                for(int z=0;z<144;z++)for(int x=0;x<144;x++)for(int y=200;y<205;y++) {
                    IBlockState state=world.getBlockState(new BlockPos((origin<<4)+x,y,(128<<4)+z));
                    digest.update((state.getBlock().getRegistryName()+"/"+state.getBlock().getMetaFromState(state)+"\n").getBytes("UTF-8"));
                }
                hashes[order]=digest.digest();
            }
            require(Arrays.equals(hashes[0],hashes[1]),"9x9 generation order differs at level "+level);
            StringBuilder hex=new StringBuilder();for(byte value:hashes[0])hex.append(String.format("%02X",value&255));
            System.out.println("TERRAIN_SMOOTHER_GENERATION_ORDER_PASS level="+level+" chunks=81 sha256="+hex);
        }
        return 486;
    }
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
