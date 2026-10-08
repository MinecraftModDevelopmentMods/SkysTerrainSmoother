package zone.moddev.mc.skysterrainsmoother;

import java.util.*;
import net.minecraft.init.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.*;
import zone.moddev.mc.skysterrainsmoother.api.*;
import zone.moddev.mc.skysterrainsmoother.internal.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueTest {
    @BeforeAll static void boot(){Bootstrap.register();}
    private static IBlockState piece(int mask) {
        double x0=1,z0=1,x1=0,z1=0;
        for(int i=0;i<4;i++)if((mask&(1<<i))!=0){double x=i==0||i==3?0:.5,z=i<2?0:.5;x0=Math.min(x0,x);z0=Math.min(z0,z);x1=Math.max(x1,x+.5);z1=Math.max(z1,z+.5);}
        final AxisAlignedBB box=new AxisAlignedBB(x0,0,z0,x1,.5,z1);
        return new Block(Material.ROCK) {
            @Override public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return box;}
            @Override public boolean isFullCube(IBlockState s){return false;}
        }.getDefaultState();
    }
    private static TerrainMaterial material(String name,IBlockState full,IBlockState slab,IBlockState[] steps,IBlockState[] corners){
        return new TerrainMaterial(new ResourceLocation("test",name),Collections.singleton(full),slab,steps,corners,false,null);
    }
    @Test void missingFormsFallbackAndAdditiveExtensionDoesNotReplaceSlab() {
        MaterialCatalogue c=new MaterialCatalogue();IBlockState slab=piece(15);
        TerrainMaterial m=material("stone",Blocks.STONE.getDefaultState(),slab,null,null);c.register(m);
        for(int level=1;level<=3;level++)for(int mask=1;mask<16;mask++)assertSame(slab,c.result(m,mask,level));
        IBlockState[] steps=new IBlockState[4],corners=new IBlockState[4];
        for(int d=0;d<4;d++){steps[d]=piece(Footprint.EDGES[d]);corners[d]=piece(1<<d);}
        c.addShapes(m.id,steps,corners);TerrainMaterial extended=c.source(Blocks.STONE.getDefaultState());
        for(int d=0;d<4;d++){assertSame(steps[d],c.result(extended,Footprint.EDGES[d],2));assertSame(corners[d],c.result(extended,1<<d,3));}
        steps[0]=slab;assertNotSame(slab,extended.step(0));assertSame(slab,extended.slab);
        assertThrows(IllegalArgumentException.class,()->c.addShapes(m.id,new IBlockState[4],null));
        c.freeze();assertThrows(IllegalStateException.class,()->c.addBiomeRule((id,b)->true));
    }
    @Test void conflictsAreAtomicAndInvalidGeometryIsRejected() {
        MaterialCatalogue c=new MaterialCatalogue();IBlockState slab=piece(15);
        TerrainMaterial m=material("stone",Blocks.STONE.getDefaultState(),slab,null,null);c.register(m);
        assertThrows(IllegalArgumentException.class,()->c.register(material("dirt",Blocks.DIRT.getDefaultState(),slab,null,null)));
        assertEquals(1,c.size());assertSame(m,c.piece(slab));assertNull(c.source(Blocks.DIRT.getDefaultState()));
        assertThrows(IllegalArgumentException.class,()->c.register(material("full",Blocks.DIRT.getDefaultState(),Blocks.STONE.getDefaultState(),null,null)));
        assertThrows(IllegalArgumentException.class,()->c.register(material("tile",Blocks.CHEST.getDefaultState(),piece(15),null,null)));
        assertThrows(IllegalArgumentException.class,()->c.register(material("wrong",Blocks.DIRT.getDefaultState(),piece(3),null,null)));
        assertThrows(IllegalArgumentException.class,()->c.register(material("dupe",Blocks.STONE.getDefaultState(),piece(15),null,null)));
    }
    @Test void biomeRestrictionsAndProtectedAreasAreOnlyVetoes() {
        MaterialCatalogue c=new MaterialCatalogue();TerrainMaterial m=material("stone",Blocks.STONE.getDefaultState(),piece(15),null,null);c.register(m);
        c.addBiomeRule((id,biome)->biome==Biomes.PLAINS);c.addAreaProtection((dimension,pos,biome)->pos.getX()<0);
        assertTrue(c.allows(m,Biomes.PLAINS,0,BlockPos.ORIGIN));assertFalse(c.allows(m,Biomes.DESERT,0,BlockPos.ORIGIN));
        assertFalse(c.allows(m,Biomes.PLAINS,0,new BlockPos(-1,1,1)));
    }
}
