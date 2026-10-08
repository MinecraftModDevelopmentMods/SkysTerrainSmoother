package zone.moddev.mc.skysterrainsmoother.probe;

import java.util.*;
import java.security.MessageDigest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.Footprint;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Decoration-created grass and hardened-clay regressions, never shipped. */
final class LateTerrainProbe {
    private static final int[] DX={0,1,0,-1},DZ={-1,0,1,0},CX={-1,1,1,-1},CZ={-1,-1,1,1};
    private LateTerrainProbe() { }
    static int run(WorldServer world) {
        MaterialCatalogue catalogue=MaterialCatalogue.instance();
        IBlockState clay=Blocks.HARDENED_CLAY.getDefaultState();
        TerrainMaterial material=catalogue.source(clay),grass=catalogue.source(Blocks.GRASS.getDefaultState());
        RuntimeProbe.require(material!=null,"hardened clay is a smoothing source");
        RuntimeProbe.require(material.slab.equals(BuildingPiecesApi.bottomPiece("minecraft:hardened_clay",BuildingPiecesApi.TerrainShape.SLAB,EnumFacing.NORTH)),"reuse existing clay slab without new registry IDs");
        int checks=2,previousLevel=SkysTerrainSmoother.level;
        BlockPos floor=new BlockPos(30008,220,30008);Chunk owner=world.getChunkFromBlockCoords(floor);
        try {
            for(int level=1;level<=3;level++)for(int direction=0;direction<4;direction++) {
                SkysTerrainSmoother.level=level;
                for(boolean diagonal:new boolean[]{false,true}) {
                    fixture(world,floor,clay);
                    BlockPos higher=floor.add(diagonal?CX[direction]:DX[direction],1,diagonal?CZ[direction]:DZ[direction]);
                    world.setBlockState(higher,clay,2);
                    int mask=Footprint.mask(diagonal?0:1<<direction,diagonal?1<<direction:0,level);
                    IBlockState expected=catalogue.result(material,mask,level);
                    SkysTerrainSmoother.engine.smooth(world,owner,level);
                    RuntimeProbe.require(world.getBlockState(floor.up()).equals(expected==null?Blocks.AIR.getDefaultState():expected),"clay footprint level="+level+" direction="+direction+" diagonal="+diagonal);
                    RuntimeProbe.require(world.getBlockState(floor).equals(clay),"clay support is never converted to grass or dirt");checks+=2;
                }

                // A flat clay surface has no cap in Pre. BOP subsequently adds
                // oasis grass and a higher neighbouring clay surface.
                fixture(world,floor,clay);
                SkysTerrainSmoother.engine.smooth(world,owner,level);
                RuntimeProbe.require(world.isAirBlock(floor.up()),"no initial cap on a flat clay surface");checks++;
                world.setBlockState(floor,Blocks.GRASS.getDefaultState(),2);
                world.setBlockState(floor.add(DX[direction],1,DZ[direction]),clay,2);
                int loaded=world.getChunkProvider().getLoadedChunkCount();
                post(world,owner);
                IBlockState expected=GrassSlabsApi.grassGenerationAllowed()?catalogue.result(grass,Footprint.EDGES[direction],level):Blocks.AIR.getDefaultState();
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(expected),"late oasis grass gets its own grass cap beside clay");
                RuntimeProbe.require(world.getBlockState(floor).equals(GrassSlabsApi.grassGenerationAllowed()?Blocks.DIRT.getDefaultState():Blocks.GRASS.getDefaultState()),"late grass support dirtification respects grass switch");
                post(world,owner);
                RuntimeProbe.require(world.getBlockState(floor.up()).equals(expected)&&world.isAirBlock(floor.up(2)),"repeated late pass is idempotent and does not stack caps");
                RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount()==loaded,"late pass never loads a neighbour");checks+=4;

                // Decoration owns occupied targets. Do not replace vegetation,
                // fluids or a placed block to obtain the missing transition.
                for(IBlockState occupied:new IBlockState[]{Blocks.TALLGRASS.getDefaultState(),Blocks.WATER.getDefaultState(),Blocks.GLASS.getDefaultState()}) {
                    fixture(world,floor,Blocks.GRAVEL.getDefaultState());
                    SkysTerrainSmoother.engine.smooth(world,owner,level);
                    world.setBlockState(floor,Blocks.GRASS.getDefaultState(),2);
                    world.setBlockState(floor.add(DX[direction],1,DZ[direction]),clay,2);
                    world.setBlockState(floor.up(),occupied,2);post(world,owner);
                    RuntimeProbe.require(world.getBlockState(floor.up()).equals(occupied),"late pass preserves occupied target "+occupied);
                    RuntimeProbe.require(world.getBlockState(floor).getBlock()==Blocks.GRASS,"no support dirtification without successful placement");checks+=2;
                }
            }
        } finally { SkysTerrainSmoother.level=previousLevel; }
        System.out.println("TERRAIN_SMOOTHER_CLAY_OASIS_PASS checks="+checks);
        return checks;
    }
    private static void fixture(WorldServer world,BlockPos floor,IBlockState source) {
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
            for(int y=floor.getY()+1;y<256;y++)world.setBlockToAir(new BlockPos(floor.getX()+x,y,floor.getZ()+z));
            world.setBlockState(floor.add(x,0,z),source,2);
        }
    }
    private static void post(WorldServer world,Chunk owner) {
        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(world,new Random(1),new BlockPos(owner.xPosition<<4,0,owner.zPosition<<4)));
    }
    static int generationOrder(WorldServer world) throws Exception {
        int previousLevel=SkysTerrainSmoother.level;
        try {
            for(int level=1;level<=3;level++) {
                SkysTerrainSmoother.level=level;byte[][] hashes=new byte[2][];
                for(int order=0;order<2;order++) {
                    int origin=order==0?128:256;List<Chunk> chunks=new ArrayList<>();
                    for(int z=0;z<9;z++)for(int x=0;x<9;x++)chunks.add(world.getChunkFromChunkCoords(origin+x,128+z));
                    for(int z=0;z<144;z++)for(int x=0;x<144;x++) {
                        int height=200+Math.floorMod(x*31+z*17+(x>>3)*(z>>3),3);
                        Chunk chunk=chunks.get((x>>4)+(z>>4)*9);
                        for(int y=170;y<256;y++) {
                            net.minecraft.world.chunk.storage.ExtendedBlockStorage section=chunk.getBlockStorageArray()[y>>4];
                            if(section==null)chunk.getBlockStorageArray()[y>>4]=section=new net.minecraft.world.chunk.storage.ExtendedBlockStorage(y&~15,true);
                            section.set(x&15,y&15,z&15,Blocks.AIR.getDefaultState());
                        }
                        chunk.getBlockStorageArray()[height>>4].set(x&15,height&15,z&15,Blocks.HARDENED_CLAY.getDefaultState());
                        chunk.getHeightMap()[(x&15)|((z&15)<<4)]=height+1;
                    }
                    if(order==1)Collections.reverse(chunks);
                    for(Chunk chunk:chunks)SkysTerrainSmoother.engine.smooth(world,chunk,level);
                    // Introduce grass after the initial pass, including beneath
                    // clay caps, to model decoration changing the full surface.
                    for(int z=0;z<144;z++)for(int x=0;x<144;x++)if((x+z)%5==0) {
                        int height=200+Math.floorMod(x*31+z*17+(x>>3)*(z>>3),3);
                        // Resolve by coordinate, not traversal order.
                        Chunk chunk=world.getChunkProvider().getLoadedChunk(origin+(x>>4),128+(z>>4));
                        chunk.setBlockState(new BlockPos((origin<<4)+x,height,(128<<4)+z),Blocks.GRASS.getDefaultState());
                    }
                    for(Chunk chunk:chunks)post(world,chunk);
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    for(int z=0;z<144;z++)for(int x=0;x<144;x++)for(int y=200;y<205;y++) {
                        IBlockState state=world.getBlockState(new BlockPos((origin<<4)+x,y,(128<<4)+z));
                        digest.update((state.getBlock().getRegistryName()+"/"+state.getBlock().getMetaFromState(state)+"\n").getBytes("UTF-8"));
                    }
                    hashes[order]=digest.digest();
                }
                RuntimeProbe.require(Arrays.equals(hashes[0],hashes[1]),"late clay/oasis 9x9 order differs at level "+level);
                StringBuilder hex=new StringBuilder();for(byte value:hashes[0])hex.append(String.format("%02X",value&255));
                System.out.println("TERRAIN_SMOOTHER_LATE_GENERATION_ORDER_PASS level="+level+" chunks=81 sha256="+hex);
            }
        } finally {SkysTerrainSmoother.level=previousLevel;}
        return 486;
    }
}
