package zone.moddev.mc.skysterrainsmoother.probe;
import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean integrated;
    @Override public void init(){MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc=Minecraft.getMinecraft();if(event.phase!=TickEvent.Phase.END)return;
        if(ran){if(integrated&&ClientStatus.complete){System.out.println("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");mc.shutdown();integrated=false;}return;}
        if(mc.currentScreen==null)return;ran=true;
        try {
            for(net.minecraft.block.state.IBlockState state:zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB.getBlockState().getValidStates())checkModel(mc,state);
            for(zone.moddev.mc.skysbuildingpieces.content.PieceBlock block:zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS.values())
                for(net.minecraft.block.state.IBlockState state:block.getBlockState().getValidStates()) {
                    if(state.getValue(zone.moddev.mc.skysbuildingpieces.content.PieceBlock.META)/block.palette.shape.states>=block.palette.materials.size())continue;
                    checkModel(mc,state);
                }
            try(Writer out=new FileWriter(new File(mc.mcDataDir,"terrain-smoother-client-pass.txt"))){out.write("TERRAIN_SMOOTHER_CLIENT_PASS\n");}
            System.out.println("TERRAIN_SMOOTHER_CLIENT_PASS");
        }catch(IOException failure){throw new IllegalStateException(failure);}
        if(System.getProperty("skysterrainsmoother.integrationPhase","").startsWith("client-")) {
            integrated=true;mc.launchIntegratedServer("terrain-smoother-world","Terrain Smoother Test",
                    new net.minecraft.world.WorldSettings(4815162342L,net.minecraft.world.GameType.CREATIVE,true,false,net.minecraft.world.WorldType.DEFAULT));
        }else mc.shutdown();
    }
    private static void checkModel(Minecraft mc,net.minecraft.block.state.IBlockState state) {
        net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
        RuntimeProbe.require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"missing model "+state);
        for(net.minecraft.util.EnumFacing face:new net.minecraft.util.EnumFacing[]{null,net.minecraft.util.EnumFacing.DOWN,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumFacing.NORTH,net.minecraft.util.EnumFacing.SOUTH,net.minecraft.util.EnumFacing.WEST,net.minecraft.util.EnumFacing.EAST})
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state,face,0))
                RuntimeProbe.require(!quad.getSprite().getIconName().contains("missingno"),"missing texture "+state);
    }
}
