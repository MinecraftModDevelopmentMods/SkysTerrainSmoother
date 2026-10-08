package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;

/** Real cave-floor regressions, never included in production launches or jars. */
final class CaveProbe {
    private static final int[] DX={0,1,0,-1},DZ={-1,0,1,0},CX={-1,1,1,-1},CZ={-1,-1,1,1};
    private CaveProbe() { }
    static int run(WorldServer world) {
        BlockPos floor=new BlockPos(3000,70,3000);
        Chunk owner=world.getChunkFromBlockCoords(floor);
        clear(world,floor,69,255);
        fixture(world,floor,6);
        world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()),"cave floor below a roof receives a matching slab");
        int checks=1;
        TerrainMaterial stone=MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState());
        for(int level=1;level<=3;level++)for(int cardinal=0;cardinal<16;cardinal++)for(int diagonal=0;diagonal<16;diagonal++) {
            fixture(world,floor,6);
            for(int d=0;d<4;d++) {
                if((cardinal&(1<<d))!=0)world.setBlockState(floor.add(DX[d],1,DZ[d]),Blocks.STONE.getDefaultState(),2);
                if((diagonal&(1<<d))!=0)world.setBlockState(floor.add(CX[d],1,CZ[d]),Blocks.STONE.getDefaultState(),2);
            }
            IBlockState expected=MaterialCatalogue.instance().result(stone,Footprint.mask(cardinal,diagonal,level),level);
            SkysTerrainSmoother.engine.smooth(world,owner,level);
            RuntimeProbe.require(world.getBlockState(floor.up()).equals(expected==null?Blocks.AIR.getDefaultState():expected),"roofed footprint "+level+"/"+cardinal+"/"+diagonal);
            checks++;
        }
        // The same column can contain independent floors at multiple depths.
        fixture(world,floor,6);fixture(world,floor.up(16),6);
        world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(floor.up(16).east().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,3);
        RuntimeProbe.require(!world.isAirBlock(floor.up())&&!world.isAirBlock(floor.up(17)),"stacked cave floors are both smoothed");checks++;
        for(int air=1;air<=4;air++) {
            fixture(world,floor,air+1);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,1);
            RuntimeProbe.require(world.isAirBlock(floor.up())==(air<3),"low passage remains walkable: air height "+air);checks++;
        }
        for(IBlockState obstacle:new IBlockState[]{Blocks.WATER.getDefaultState(),Blocks.LAVA.getDefaultState(),Blocks.GLASS.getDefaultState(),Blocks.CHEST.getDefaultState()})for(int height=1;height<=3;height++) {
            fixture(world,floor,6);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
            world.setBlockState(floor.up(height),obstacle,2);
            SkysTerrainSmoother.engine.smooth(world,owner,1);
            RuntimeProbe.require(height==1?world.getBlockState(floor.up()).equals(obstacle):world.isAirBlock(floor.up()),"fluid, occupied target or blocked headroom is protected");checks++;
        }
        fixture(world,floor,6);
        world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
        world.setBlockState(floor.north().up(2),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(world.isAirBlock(floor.up()),"solid cave wall is not a floor transition");checks++;
        // An open column must still see a higher floor hidden under its neighbour's roof.
        fixture(world,floor,6);world.setBlockToAir(floor.up(6));
        world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()),"cave entrance and outdoor floor join consistently");checks++;
        for(IBlockState source:new IBlockState[]{Blocks.COAL_ORE.getDefaultState(),Blocks.GRAVEL.getDefaultState(),Blocks.PLANKS.getDefaultState(),Blocks.MONSTER_EGG.getDefaultState(),Blocks.CHEST.getDefaultState()}) {
            fixture(world,floor,6);world.setBlockState(floor,source,2);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,1);
            RuntimeProbe.require(world.isAirBlock(floor.up())&&world.getBlockState(floor).equals(source),"unregistered provider terrain, ores and tile sources remain intact");checks++;
        }
        int mineralogy=0;
        for(TerrainMaterial material:MaterialCatalogue.instance().materials()) {
            if(material.grass&&!GrassSlabsApi.grassGenerationAllowed()||material.mineralogy&&!SkysTerrainSmoother.mineralogy)continue;
            for(IBlockState source:material.sources) {
                fixture(world,floor,6);world.setBlockState(floor,source,2);world.setBlockState(floor.north().up(),source,2);
                SkysTerrainSmoother.engine.smooth(world,owner,1);
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.slab),"cave uses the exact registered material: "+material.id);
                RuntimeProbe.require(world.getBlockState(floor).equals(material.supportAfterPlacement==null?source:material.supportAfterPlacement),"native support identity is preserved or dirtified");
                RuntimeProbe.require(world.getBlockState(floor.up(6)).getBlock()==Blocks.BRICK_BLOCK,"cave ceiling remains intact");checks+=3;
                if(material.mineralogy)mineralogy++;
            }
        }
        // Structure bounds and add-on vetoes apply to underground floors as well.
        MapGenStructureData data=(MapGenStructureData)world.getPerWorldStorage().getOrLoadData(MapGenStructureData.class,"Mineshaft");
        if(data==null){data=new MapGenStructureData("Mineshaft");world.getPerWorldStorage().setData("Mineshaft",data);}
        String key=MapGenStructureData.formatChunkCoords(owner.xPosition,owner.zPosition);
        NBTTagCompound previous=data.getTagCompound().hasKey(key)?data.getTagCompound().getCompoundTag(key).copy():null;
        try {
            NBTTagCompound bounds=new NBTTagCompound();bounds.setIntArray("BB",new int[]{floor.getX()-2,69,floor.getZ()-2,floor.getX()+2,80,floor.getZ()+2});
            data.writeInstance(bounds,owner.xPosition,owner.zPosition);
            fixture(world,floor,6);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,1);
            RuntimeProbe.require(world.isAirBlock(floor.up()),"mineshaft structure bounds are protected underground");checks++;
        }finally{if(previous==null)data.getTagCompound().removeTag(key);else data.getTagCompound().setTag(key,previous);}
        BlockPos veto=new BlockPos(floor.getX(),240,floor.getZ());fixture(world,veto,6);
        world.setBlockState(veto.north().up(),Blocks.STONE.getDefaultState(),2);SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(world.isAirBlock(veto.up()),"underground add-on area veto");checks++;
        checks+=lateDecoration(world,floor,owner);
        checks+=oreDecoration(world,floor,owner);
        checks+=manyFloors(world);
        System.out.println("TERRAIN_SMOOTHER_CAVE_MATERIALS_PASS mineralogy="+mineralogy);
        System.out.println("TERRAIN_SMOOTHER_CAVE_PASS checks="+checks);
        return checks;
    }
    private static int lateDecoration(WorldServer world,BlockPos floor,Chunk owner) {
        int checks=0;
        for(int kind=0;kind<4;kind++) {
            fixture(world,floor,6);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
            SkysTerrainSmoother.engine.smooth(world,owner,1);
            MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4)));
            RuntimeProbe.require(!world.isAirBlock(floor.up()),"decorated cave cap retained until all later generators finish");
            MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Post(world.getChunkProvider().chunkGenerator,world,new Random(1),owner.xPosition,owner.zPosition,false));
            if(kind==0)world.setBlockState(floor,Blocks.MONSTER_EGG.getDefaultState(),2);
            if(kind==1)world.setBlockState(floor,Blocks.SAND.getDefaultState(),2);
            if(kind==2)world.setBlockState(floor.up(2),Blocks.BRICK_BLOCK.getDefaultState(),2);
            if(kind==3){world.setBlockState(floor,Blocks.SAND.getDefaultState(),2);world.setBlockState(floor.up(),Blocks.GLASS.getDefaultState(),2);}
            MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER,TickEvent.Phase.END,world));
            RuntimeProbe.require(kind==1?world.getBlockState(floor.up()).getBlock()==zone.moddev.mc.skysterrainsmoother.content.SandContent.slab:kind==3?world.getBlockState(floor.up()).getBlock()==Blocks.GLASS:world.isAirBlock(floor.up()),"late infested stone, sand, low ceiling and provider-owned replacement are handled without excavation");checks+=2;
        }
        return checks;
    }
    private static int oreDecoration(WorldServer world,BlockPos floor,Chunk owner) {
        int checks=0;
        for(IBlockState ore:new IBlockState[]{Blocks.COAL_ORE.getDefaultState(),Blocks.IRON_ORE.getDefaultState(),Blocks.REDSTONE_ORE.getDefaultState(),Blocks.LIT_REDSTONE_ORE.getDefaultState(),RuntimeProbe.probeOre.getDefaultState()}) {
            for(int level=1;level<=3;level++) {
                fixture(world,floor,6);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
                SkysTerrainSmoother.engine.smooth(world,owner,level);
                IBlockState cap=world.getBlockState(floor.up());
                RuntimeProbe.require(cap.getBlock()!=Blocks.AIR,"ore regression starts with a generated piece");
                world.setBlockState(floor,ore,2);
                MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4)));
                MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Post(world.getChunkProvider().chunkGenerator,world,new Random(1),owner.xPosition,owner.zPosition,false));
                MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER,TickEvent.Phase.END,world));
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(cap),"ore under a generated piece stays hidden: "+ore+" / level "+level);
                RuntimeProbe.require(world.getBlockState(floor).equals(ore),"ore support is never overwritten");checks+=3;
            }
        }
        fixture(world,floor,6);world.setBlockState(floor.north().up(),Blocks.STONE.getDefaultState(),2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        world.setBlockState(floor,Blocks.COAL_ORE.getDefaultState(),2);world.setBlockState(floor.up(2),Blocks.BRICK_BLOCK.getDefaultState(),2);
        MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER,TickEvent.Phase.END,world));
        RuntimeProbe.require(world.isAirBlock(floor.up())&&world.getBlockState(floor).getBlock()==Blocks.COAL_ORE,"ore preservation does not override cave headroom protection");checks++;
        fixture(world,floor,6);
        world.setBlockState(floor.north().up(),Blocks.COAL_ORE.getDefaultState(),2);
        world.setBlockState(floor.north().up(2),MaterialCatalogue.instance().source(Blocks.STONE.getDefaultState()).slab,2);
        SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(!world.isAirBlock(floor.up()),"neighbouring retained ore cap still represents a higher logical floor");checks++;
        System.out.println("TERRAIN_SMOOTHER_ORE_SUPPORT_PASS checks="+checks);
        return checks;
    }
    private static int manyFloors(WorldServer world) {
        Chunk owner=world.getChunkFromChunkCoords(200,200);
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] sections=owner.getBlockStorageArray();
        for(int section=0;section<16;section++)sections[section]=new net.minecraft.world.chunk.storage.ExtendedBlockStorage(section*16,true);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            for(int y=32;y<232;y+=8) {int floorY=y+((x+z)&1);sections[floorY>>4].set(x,floorY&15,z,Blocks.STONE.getDefaultState());}
            sections[15].set(x,246&15,z,Blocks.BRICK_BLOCK.getDefaultState());owner.getHeightMap()[x|z<<4]=247;
        }
        int countBefore=world.getChunkProvider().getLoadedChunkCount();
        int placed=SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(placed>256,"decision buffer covers multiple floors across all 256 columns");
        RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount()==countBefore,"cave scan never loads a halo chunk");
        int second=SkysTerrainSmoother.engine.smooth(world,owner,1);
        RuntimeProbe.require(second==0,"repeated floor scan never stacks new caps");
        System.out.println("TERRAIN_SMOOTHER_CAVE_BUFFER_PASS placed="+placed);
        return 3;
    }
    private static void clear(WorldServer world,BlockPos floor,int minY,int maxY) {
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)for(int y=minY;y<=maxY;y++)
            world.setBlockToAir(new BlockPos(floor.getX()+x,y,floor.getZ()+z));
    }
    private static void fixture(WorldServer world,BlockPos floor,int roofOffset) {
        clear(world,floor,floor.getY(),floor.getY()+8);
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
            world.setBlockState(floor.add(x,0,z),Blocks.STONE.getDefaultState(),2);
            world.setBlockState(floor.add(x,roofOffset,z),Blocks.BRICK_BLOCK.getDefaultState(),2);
        }
    }
}
