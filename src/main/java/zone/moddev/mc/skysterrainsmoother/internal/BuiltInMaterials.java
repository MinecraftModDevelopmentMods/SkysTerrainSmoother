package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.*;

public final class BuiltInMaterials {
    private static final String[] ROCKS = {"basalt","rhyolite","basaltic_glass","scoria","tuff","pumice",
            "pegmatite","diabase","gabbro","peridotite","shale","conglomerate","dolomite","limestone",
            "siltstone","rock_salt","chert","gypsum","chalk","marble","slate","schist","gneiss",
            "phyllite","amphibolite","hornfels","quartzite","novaculite"};
    private BuiltInMaterials() { }
    public static void register() {
        vanilla("grass",Blocks.GRASS.getDefaultState(),true,ModBlocks.GRASS_SLAB.getDefaultState());
        vanilla("dirt",Blocks.DIRT.getStateFromMeta(0),false,ModBlocks.DIRT_SLAB.getDefaultState());
        vanilla("stone",Blocks.STONE.getStateFromMeta(0),false,null);
        vanilla("granite",Blocks.STONE.getStateFromMeta(1),false,null);
        vanilla("diorite",Blocks.STONE.getStateFromMeta(3),false,null);
        vanilla("andesite",Blocks.STONE.getStateFromMeta(5),false,null);
        vanilla("sandstone",Blocks.SANDSTONE.getStateFromMeta(0),false,null);
        vanilla("red_sandstone",Blocks.RED_SANDSTONE.getStateFromMeta(0),false,null);
        if (!SkysTerrainSmoother.mineralogy || !Loader.isModLoaded("mineralogy")) return;
        int count=0,missing=0;
        for(String rock:ROCKS) {
            ResourceLocation fullId=new ResourceLocation("mineralogy",rock),slabId=new ResourceLocation("mineralogy",rock+"_slab");
            if(!Block.REGISTRY.containsKey(fullId)||!Block.REGISTRY.containsKey(slabId)){++missing;continue;}
            IBlockState slab=bottom(Block.REGISTRY.getObject(slabId).getDefaultState());
            TerrainSmoothingApi.registerMaterial(new TerrainMaterial(fullId,
                    Collections.singleton(Block.REGISTRY.getObject(fullId).getDefaultState()),slab,null,null,false,null));++count;
        }
        SkysTerrainSmoother.logger.info("Mineralogy smoothing: {} natural rocks available, {} unavailable slab forms skipped",count,missing);
    }
    private static void vanilla(String name,IBlockState source,boolean grass,IBlockState slab) {
        String material="minecraft:"+name;
        if(slab==null)slab=BuildingPiecesApi.bottomPiece(material,BuildingPiecesApi.TerrainShape.SLAB,EnumFacing.NORTH);
        IBlockState[] steps=new IBlockState[4],corners=new IBlockState[4];
        EnumFacing[] directions={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
        for(int i=0;i<4;++i) {
            steps[i]=BuildingPiecesApi.bottomPiece(material,BuildingPiecesApi.TerrainShape.STEP,directions[i]);
            corners[i]=BuildingPiecesApi.bottomPiece(material,BuildingPiecesApi.TerrainShape.CORNER,directions[i]);
        }
        TerrainSmoothingApi.registerMaterial(new TerrainMaterial(new ResourceLocation(material),Collections.singleton(source),
                slab,steps,corners,grass,grass?Blocks.DIRT.getDefaultState():null));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static IBlockState bottom(IBlockState state) {
        for(IProperty property:state.getPropertyKeys())if(property.getName().equals("facing")&&property.getAllowedValues().contains(EnumFacing.UP))
            return state.withProperty(property,EnumFacing.UP);
        throw new IllegalStateException("Unsupported Mineralogy slab orientation property: "+state);
    }
}
