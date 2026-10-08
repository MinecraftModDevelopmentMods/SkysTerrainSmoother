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
    static Block probeOre;
    @SidedProxy(clientSide="zone.moddev.mc.skysterrainsmoother.probe.ClientProbe",serverSide="zone.moddev.mc.skysterrainsmoother.probe.ProbeProxy")
    public static ProbeProxy proxy;
    private MinecraftServer server;
    private boolean ran;
    @Mod.EventHandler public void pre(FMLPreInitializationEvent event){
        probeOre=new Block(net.minecraft.block.material.Material.ROCK).setUnlocalizedName("terrainsmootherprobe.ore_support").setRegistryName("terrainsmootherprobe","ore_support");
        net.minecraftforge.fml.common.registry.GameRegistry.register(probeOre);
        net.minecraftforge.fml.common.registry.GameRegistry.register(new net.minecraft.item.ItemBlock(probeOre).setRegistryName(probeOre.getRegistryName()));
        net.minecraftforge.oredict.OreDictionary.registerOre("oreTerrainProbe",probeOre);
        MinecraftForge.EVENT_BUS.register(this);proxy.init();
    }
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
            int gameplay=persistence(world)+LateTerrainProbe.run(world)+CaveProbe.run(world)+decorationChanges(world)+geometry(world)+grass(world)+sand(world)+generationOrder(world)+LateTerrainProbe.generationOrder(world);
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
    private static int sand(WorldServer world) {
        zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock slab=zone.moddev.mc.skysterrainsmoother.content.SandContent.slab;
        BlockPos p=new BlockPos(1200,220,1200);world.getChunkFromBlockCoords(p);int checks=0;
        FakePlayer player=FakePlayerFactory.get(world,new GameProfile(UUID.fromString("14581a55-6ff1-4c98-834f-3f41057e395b"),"SandProbe"));player.capabilities.isCreativeMode=false;player.setPosition(1204,220,1204);
        for(int variant=0;variant<2;variant++) {
            for(int meta=variant*2;meta<variant*2+2;meta++) {
                IBlockState state=slab.getStateFromMeta(meta);require(slab.getMetaFromState(state)==meta,"sand half and material round trip");
                boolean instant=BlockFalling.fallInstantly;try {
                    BlockFalling.fallInstantly=true;
                    for(int y=216;y<=224;y++)world.setBlockToAir(new BlockPos(p.getX(),y,p.getZ()));world.setBlockState(p.down(5),Blocks.STONE.getDefaultState(),2);world.setBlockState(p,state,2);
                    slab.updateTick(world,p,state,new Random(1));require(world.isAirBlock(p)&&world.getBlockState(p.down(4)).equals(state),"instant fall preserves red sand and half");
                }finally{BlockFalling.fallInstantly=instant;}
                require(slab.damageDropped(state)==variant*2,"drop strips orientation but keeps sand type");checks+=3;
                for(int cz=-3;cz<=3;cz++)for(int cx=-3;cx<=3;cx++)world.getChunkFromChunkCoords((p.getX()>>4)+cx,(p.getZ()>>4)+cz);
                for(int y=216;y<=224;y++)world.setBlockToAir(new BlockPos(p.getX(),y,p.getZ()));
                world.setBlockState(p.down(5),Blocks.STONE.getDefaultState(),2);world.setBlockState(p,state,2);
                boolean previous=BlockFalling.fallInstantly;BlockFalling.fallInstantly=false;
                try {
                    slab.updateTick(world,p,state,new Random(1));
                    java.util.List<net.minecraft.entity.item.EntityFallingBlock> falling=world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityFallingBlock.class,new net.minecraft.util.math.AxisAlignedBB(p).expandXyz(1));
                    require(falling.size()==1,"loaded sand uses the native falling entity");
                    net.minecraft.entity.item.EntityFallingBlock entity=falling.get(0);
                    for(int tick=0;tick<80&&!entity.isDead;tick++)entity.onUpdate();
                    require(entity.isDead&&world.isAirBlock(p)&&world.getBlockState(p.down(4)).equals(state),"entity fall preserves sand variant and half");checks+=2;
                }finally{BlockFalling.fallInstantly=previous;}
            }
            world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);world.setBlockToAir(p);world.setBlockToAir(p.up());
            net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(slab,2,variant*2);
            require(stack.onItemUse(player,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f)==EnumActionResult.SUCCESS,"sand bottom placement");
            require(world.getBlockState(p).equals(slab.getStateFromMeta(variant*2+1)),"horizontal bottom sand state");
            require(stack.onItemUse(player,world,p,EnumHand.MAIN_HAND,EnumFacing.UP,.5f,.5f,.5f)==EnumActionResult.SUCCESS&&world.getBlockState(p).equals(Blocks.SAND.getStateFromMeta(variant)),"sand halves normalize to original full variant");
            require(stack.stackSize==0,"placement consumes exactly two slabs");checks+=4;
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},2,2);
            grid.setInventorySlotContents(0,new net.minecraft.item.ItemStack(slab,1,variant*2));grid.setInventorySlotContents(1,new net.minecraft.item.ItemStack(slab,1,variant*2));
            require(net.minecraft.item.ItemStack.areItemStacksEqual(new net.minecraft.item.ItemStack(Blocks.SAND,1,variant),net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(grid,world)),"sand slab crafting recombination");checks++;
        }
        for(net.minecraft.block.Block grass:new net.minecraft.block.Block[]{ModBlocks.DIRT_SLAB,ModBlocks.GRASS_SLAB}){
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},2,2);
            grid.setInventorySlotContents(0,new net.minecraft.item.ItemStack(grass,1,0));grid.setInventorySlotContents(1,new net.minecraft.item.ItemStack(grass,1,1));
            require(net.minecraft.item.ItemStack.areItemStacksEqual(new net.minecraft.item.ItemStack(grass==ModBlocks.DIRT_SLAB?Blocks.DIRT:Blocks.GRASS),net.minecraft.item.crafting.CraftingManager.getInstance().findMatchingRecipe(grid,world)),"both saved grass/dirt slab item orientations recombine");checks++;
        }
        System.out.println("TERRAIN_SMOOTHER_SAND_PASS checks="+checks);return checks;
    }
    private static int decorationChanges(WorldServer world) {
        BlockPos p=new BlockPos(1100,200,1100);Chunk owner=world.getChunkFromBlockCoords(p);int checks=0;
        for(int level=1;level<=3;level++)for(Block replacement:new Block[]{Blocks.SAND,Blocks.GRAVEL,Blocks.AIR}) {
            for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)for(int y=200;y<=203;y++)world.setBlockToAir(p.add(x,y-200,z));
            IBlockState source=GrassSlabsApi.grassGenerationAllowed()?Blocks.GRASS.getDefaultState():Blocks.STONE.getDefaultState();
            world.setBlockState(p,source,2);world.setBlockState(p.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,level);
            require(!world.isAirBlock(p.up()),"regression starts with an actual generated piece");
            world.setBlockState(p,replacement.getDefaultState(),2);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4)));
            require(replacement==Blocks.SAND?world.getBlockState(p.up()).getBlock()==zone.moddev.mc.skysterrainsmoother.content.SandContent.slab:world.isAirBlock(p.up()),"decoration retargets sand and removes unsupported gravel/air caps");
            require(world.getBlockState(p).getBlock()==replacement,"decorator's terrain is not reverted");checks+=3;
        }
        for(int level=1;level<=3;level++)for(boolean replacePiece:new boolean[]{false,true}) {
            for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)for(int y=200;y<=203;y++)world.setBlockToAir(p.add(x,y-200,z));
            world.setBlockState(p,Blocks.STONE.getDefaultState(),2);world.setBlockState(p.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,level);IBlockState piece=world.getBlockState(p.up());
            if(replacePiece){world.setBlockState(p,Blocks.SAND.getDefaultState(),2);world.setBlockState(p.up(),Blocks.GLASS.getDefaultState(),2);piece=Blocks.GLASS.getDefaultState();}
            net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post post=new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4));
            MinecraftForge.EVENT_BUS.post(post);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4)));
            require(world.getBlockState(p.up()).equals(piece),"valid support and decorator-owned replacement remain intact; repeated Post is harmless");checks++;
        }
        BlockPos late=new BlockPos(28008,200,28008);Chunk lateOwner=world.getChunkFromBlockCoords(late);
        for(int y=200;y<=203;y++){world.setBlockToAir(late.up(y-200));world.setBlockToAir(late.north().up(y-200));}
        world.setBlockState(late,Blocks.STONE.getDefaultState(),2);world.setBlockState(late.north().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,lateOwner,1);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(lateOwner.xPosition<<4,0,lateOwner.zPosition<<4)));
        require(!world.isAirBlock(late.up()),"own Post retains supported generated piece");
        world.setBlockState(late,Blocks.SAND.getDefaultState(),2);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos((lateOwner.xPosition-1)<<4,0,lateOwner.zPosition<<4)));
        require(world.getBlockState(late.up()).getBlock()==zone.moddev.mc.skysterrainsmoother.content.SandContent.slab&&world.getBlockState(late).getBlock()==Blocks.SAND,"later neighbouring decoration retargets only its affected generated cap");checks+=2;
        BlockPos beach=late.add(32,0,32);Chunk beachOwner=world.getChunkFromBlockCoords(beach);
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)for(int y=200;y<=203;y++)world.setBlockToAir(beach.add(x,y-200,z));
        world.setBlockState(beach,Blocks.GRAVEL.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,beachOwner,1);
        require(world.isAirBlock(beach.up()),"unsupported first pass leaves no piece");
        world.setBlockState(beach,Blocks.SAND.getStateFromMeta(1),2);world.setBlockState(beach.north().up(),Blocks.SAND.getStateFromMeta(1),2);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(beachOwner.xPosition<<4,0,beachOwner.zPosition<<4)));
        require(world.getBlockState(beach.up()).equals(zone.moddev.mc.skysterrainsmoother.content.SandContent.slab.getStateFromMeta(3)),"late beach gets a red sand cap without any initial placement");checks+=2;
        System.out.println("TERRAIN_SMOOTHER_DECORATION_REPAIR_PASS checks="+checks);
        return checks;
    }
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
            world.setBlockState(saved,Blocks.GRASS.getDefaultState(),2);
            world.setBlockState(saved.east().up(),Blocks.HARDENED_CLAY.getDefaultState(),2);
            SkysTerrainSmoother.engine.afterDecoration(new net.minecraftforge.event.terraingen.DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(saved.getX()&~15,0,saved.getZ()&~15)));
            require(world.isAirBlock(saved.up())&&world.getBlockState(saved).getBlock()==Blocks.GRASS,"late oasis pass never retrogenerates an indexed saved chunk");
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
        long before=SkysTerrainSmoother.engine.passes;int found=0,caves=0,mineralogy=0,lushClay=0,lushGrass=0;
        for(int attempt=0;attempt<4;attempt++) {
            int centerX=Integer.getInteger("skysterrainsmoother.probeCenterX",400)+attempt*40+(phase().equals("reload")?200:0),centerZ=Integer.getInteger("skysterrainsmoother.probeCenterZ",400);
            player.setPosition(centerX*16+8,90,centerZ*16+8);world.getPlayerChunkMap().addPlayer(player);
            try {
                for(int tick=0;tick<100;tick++){world.getPlayerChunkMap().tick();world.getChunkProvider().tick();}
                // This probe drives tracking during ServerTick END. Complete the
                // WorldTick END that ordinary server-driven population receives.
                MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,TickEvent.Phase.END,world));
                for(int z=centerZ-4;z<=centerZ+4;z++)for(int x=centerX-4;x<=centerX+4;x++) {
                    Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
                    for(ExtendedBlockStorage section:chunk.getBlockStorageArray())if(section!=null)
                        for(int y=0;y<16;y++)for(int zz=0;zz<16;zz++)for(int xx=0;xx<16;xx++) {
                            TerrainMaterial m=MaterialCatalogue.instance().piece(section.get(xx,y,zz));if(m!=null) {
                                BlockPos piece=new BlockPos((x<<4)+xx,section.getYLocation()+y,(z<<4)+zz);
                                IBlockState support=chunk.getBlockState(piece.down());
                                require(m.sources.contains(support)||support.equals(m.supportAfterPlacement)||oreSupport(support),"normal decoration leaves no piece over unrelated support: "+piece+" / "+support);
                                if(piece.getY()<chunk.getHeightValue(xx,zz)-1&&!m.grass){caves++;if(m.mineralogy)mineralogy++;}
                                if(new ResourceLocation("biomesoplenty:lush_desert").equals(world.getBiome(piece).getRegistryName())) {
                                    if(m.id.equals(new ResourceLocation("minecraft:hardened_clay")))lushClay++;
                                    if(m.id.equals(new ResourceLocation("minecraft:grass")))lushGrass++;
                                }
                                found++;
                            }
                        }
                }
            }finally{world.getPlayerChunkMap().removePlayer(player);}
            if(found>0)break;
        }
        if(SkysTerrainSmoother.enabled)require(SkysTerrainSmoother.engine.passes>before&&found>0,"normal player-tracked generation creates pieces");
        else require(SkysTerrainSmoother.engine.passes==before&&found==0,"disabled master has no duplicate grass pass");
        if(SkysTerrainSmoother.enabled)require(caves>0,"normal player-tracked generation also smooths real cave floors");
        if(Boolean.getBoolean("skysterrainsmoother.verifyLushDesert")&&phase().equals("fresh")) {
            require(lushClay>0,"player-tracked Lush Desert has hardened-clay transitions");
            require(lushGrass>0,"player-tracked Lush Desert has grass oasis transitions");
            System.out.println("TERRAIN_SMOOTHER_LUSH_DESERT_GENERATION_PASS clay="+lushClay+" grass="+lushGrass);
        }
        System.out.println("TERRAIN_SMOOTHER_GENERATED_CAVES_PASS caves="+caves+" mineralogy="+mineralogy);
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
                    for(int y=170;y<205;y++) {
                        ExtendedBlockStorage section=chunk.getBlockStorageArray()[y>>4];
                        if(section==null)chunk.getBlockStorageArray()[y>>4]=section=new ExtendedBlockStorage(y&~15,true);
                        section.set(x&15,y&15,z&15,Blocks.AIR.getDefaultState());
                    }
                    for(int floor:new int[]{170,188,200}) {
                        int floorY=floor+height-200;
                        chunk.getBlockStorageArray()[floorY>>4].set(x&15,floorY&15,z&15,Blocks.STONE.getDefaultState());
                    }
                    for(int roof:new int[]{180,198})chunk.getBlockStorageArray()[roof>>4].set(x&15,roof&15,z&15,Blocks.BRICK_BLOCK.getDefaultState());
                    chunk.getHeightMap()[(x&15)|((z&15)<<4)]=height+1;
                }
                if(order==1)Collections.reverse(chunks);
                for(Chunk chunk:chunks)SkysTerrainSmoother.engine.smooth(world,chunk,level);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                for(int z=0;z<144;z++)for(int x=0;x<144;x++)for(int y=170;y<205;y++) {
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
    private static boolean oreSupport(IBlockState state) {
        if(!state.isFullCube()||state.getBlock().hasTileEntity(state))return false;
        if(state.getBlock() instanceof BlockOre||state.getBlock() instanceof BlockRedstoneOre)return true;
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(state.getBlock(),1,state.getBlock().getMetaFromState(state));
        for(int id:net.minecraftforge.oredict.OreDictionary.getOreIDs(stack))
            if(net.minecraftforge.oredict.OreDictionary.getOreName(id).startsWith("ore"))return true;
        return false;
    }
}
