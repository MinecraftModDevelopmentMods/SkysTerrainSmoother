package zone.moddev.mc.skysterrainsmoother;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.internal.BuiltInMaterials;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine;
import zone.moddev.mc.skysterrainsmoother.internal.TerrainSmoothingFeature;

@Mod(SkysTerrainSmoother.ID)
public final class SkysTerrainSmoother {
    public static final String ID = "skysterrainsmoother";
    public static final String NAME = "Sky's Terrain Smoother";
    public static final String VERSION = "0.1.0.118021";
    public static final Logger logger = LogManager.getLogger();
    public static final SmoothingEngine engine = new SmoothingEngine(MaterialCatalogue.instance());
    public static boolean enabled = true;
    public static boolean mineralogy = true;
    public static int level = 3;

    public SkysTerrainSmoother() {
        TerrainSmootherConfig.register();
        GrassSlabsApi.claimSmoothing(ID, TerrainSmootherConfig::enabled);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::complete);
        MinecraftForge.EVENT_BUS.register(engine);
        MinecraftForge.EVENT_BUS.addListener(TerrainSmoothingFeature::onBiomeLoading);
        MinecraftForge.EVENT_BUS.addListener(this::beforeServer);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
        DistExecutor.runWhenOn(Dist.CLIENT,
                () -> zone.moddev.mc.skysterrainsmoother.client.ClientProxy::register);
    }

    private void setup(FMLCommonSetupEvent event) {
        TerrainSmootherConfig.activateSettingsForThisRun();
        enabled = TerrainSmootherConfig.enabled();
        level = TerrainSmootherConfig.level();
        mineralogy = TerrainSmootherConfig.mineralogy();
        BuiltInMaterials.ensureRegistered();
    }

    private void complete(FMLLoadCompleteEvent event) {
        MaterialCatalogue.instance().freeze();
        // Configured features share a vanilla registry. Update it on the loading
        // thread after the parallel mod callbacks have finished.
        event.enqueueWork(TerrainSmoothingFeature::install);
        logger.info("Terrain smoothing level {}: {} registered materials; two sand slab colours",
                level, MaterialCatalogue.instance().size());
    }

    private void beforeServer(ServerAboutToStartEvent event) {
        engine.start(event.getServer().getWorldPath(LevelResource.ROOT).toFile());
    }

    private void stopped(ServerStoppedEvent event) {
        logger.info("Terrain smoothing completed {} passes and placed {} pieces; {} existing-chunk events skipped",
                engine.passes, engine.placed, engine.skippedExisting);
        engine.stop();
    }
}
