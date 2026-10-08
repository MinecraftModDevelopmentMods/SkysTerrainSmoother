package zone.moddev.mc.skysterrainsmoother;

import java.io.File;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.api.TerrainSmoothingApi;
import zone.moddev.mc.skysterrainsmoother.internal.*;

@Mod(modid=SkysTerrainSmoother.ID,name="Sky's Terrain Smoother",version=SkysTerrainSmoother.VERSION,
        acceptedMinecraftVersions="[1.10.2]",
        dependencies="required-before:skysgrassslabs@[1.1.0.110021,2);required-before:skysbuildingpieces@[0.3.0.110021,0.4)")
public final class SkysTerrainSmoother {
    public static final String ID="skysterrainsmoother",VERSION="0.1.0.110021";
    public static boolean enabled=true,mineralogy=true;
    public static int level=1;
    public static Logger logger;
    public static SmoothingEngine engine;
    @SidedProxy(clientSide="zone.moddev.mc.skysterrainsmoother.client.ClientProxy",serverSide="zone.moddev.mc.skysterrainsmoother.client.CommonProxy")
    public static zone.moddev.mc.skysterrainsmoother.client.CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        logger=event.getModLog();Configuration config=new Configuration(event.getSuggestedConfigurationFile());config.load();
        enabled=config.getBoolean("enabled","worldgen",true,"Smooth newly generated dry Overworld slopes and cave floors. Requires restart.");
        level=config.getInt("smoothingLevel","worldgen",1,1,3,"1: slabs; 2: slabs and steps; 3: slabs, steps and corners. Missing shapes use slabs. Requires restart.");
        mineralogy=config.getBoolean("enableMineralogy","compat",true,"Reuse available natural Mineralogy rock slabs. No additional blocks are registered. Requires restart.");
        if(config.hasChanged())config.save();GrassSlabsApi.claimSmoothing(ID,enabled);
        zone.moddev.mc.skysterrainsmoother.content.SandContent.register();proxy.preInit();
        engine=new SmoothingEngine(MaterialCatalogue.instance());MinecraftForge.EVENT_BUS.register(engine);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) { BuiltInMaterials.register();zone.moddev.mc.skysterrainsmoother.content.SandContent.recipes(); }
    @Mod.EventHandler public void complete(FMLLoadCompleteEvent event) {
        MaterialCatalogue.instance().freeze();logger.info("Terrain smoothing level {}: {} registered materials; one sand slab palette",level,MaterialCatalogue.instance().size());
    }
    @Mod.EventHandler public void beforeServer(FMLServerAboutToStartEvent event) {
        MinecraftServer server=event.getServer();
        engine.start(server.isDedicatedServer()?server.getFile(server.getFolderName()):new File(server.getDataDirectory(),"saves/"+server.getFolderName()));
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) {
        logger.info("Terrain smoothing completed {} passes and placed {} pieces; {} existing-chunk events skipped",engine.passes,engine.placed,engine.skippedExisting);engine.stop();
    }
}
