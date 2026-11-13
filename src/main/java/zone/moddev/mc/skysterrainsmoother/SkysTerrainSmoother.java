package zone.moddev.mc.skysterrainsmoother;

import net.neoforged.api.distmarker.Dist;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.internal.BuiltInMaterials;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;
import zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine;
import zone.moddev.mc.skysterrainsmoother.internal.TerrainSmoothingFeature;

@Mod(SkysTerrainSmoother.ID)
public final class SkysTerrainSmoother {
    public static final String ID = "skysterrainsmoother";
    public static final String NAME = "Sky's Terrain Smoother";
    public static final String VERSION = "0.1.2.121012";
    public static final Logger logger = LogManager.getLogger();
    public static final SmoothingEngine engine = new SmoothingEngine(MaterialCatalogue.instance());
    public static boolean enabled = true;
    public static boolean mineralogy = true;
    public static int level = 3;

    public SkysTerrainSmoother(IEventBus modBus, ModContainer container) {
        TerrainSmootherConfig.register(modBus, container);
        GrassSlabsApi.claimSmoothing(ID, TerrainSmootherConfig::enabled);
        modBus.addListener(this::setup);
        modBus.addListener(this::complete);
        TerrainSmoothingFeature.register(modBus);
        NeoForge.EVENT_BUS.register(engine);
        NeoForge.EVENT_BUS.addListener(this::beforeServer);
        NeoForge.EVENT_BUS.addListener(this::stopped);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysterrainsmoother.client.ClientProxy.register(container);
        }
    }

    private void setup(FMLCommonSetupEvent event) {
        TerrainSmootherConfig.activateSettingsForThisRun();
        enabled = TerrainSmootherConfig.enabled();
        level = TerrainSmootherConfig.level();
        mineralogy = TerrainSmootherConfig.mineralogy();
        BuiltInMaterials.ensureRegistered();
        MaterialCatalogue.instance().openRegistration();
    }

    private void complete(FMLLoadCompleteEvent event) {
        MaterialCatalogue.instance().freeze();
        // Build the placed feature after all material contributors have finished.
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
