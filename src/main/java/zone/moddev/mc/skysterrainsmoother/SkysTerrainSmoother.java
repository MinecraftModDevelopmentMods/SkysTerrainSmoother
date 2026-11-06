package zone.moddev.mc.skysterrainsmoother;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
    public static final String VERSION = "0.1.0.2602001";
    public static final Logger logger = LogManager.getLogger();
    public static final SmoothingEngine engine = new SmoothingEngine(MaterialCatalogue.instance());
    public static boolean enabled = true;
    public static boolean mineralogy = true;
    public static int level = 3;

    @SuppressWarnings("removal") // Keep client classes off the dedicated server.
    public SkysTerrainSmoother(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        TerrainSmootherConfig.register(context);
        GrassSlabsApi.claimSmoothing(ID, TerrainSmootherConfig::enabled);
        FMLCommonSetupEvent.getBus(modBus).addListener(this::setup);
        FMLLoadCompleteEvent.getBus(modBus).addListener(this::complete);
        TerrainSmoothingFeature.register(modBus);
        RegisterEvent.getBus(modBus).addListener(SandContent::register);
        BuildCreativeModeTabContentsEvent.BUS.addListener(SandContent::buildCreativeTab);
        ChunkEvent.Load.BUS.addListener(engine::loaded);
        ChunkEvent.Unload.BUS.addListener(engine::unload);
        TickEvent.LevelTickEvent.Post.BUS.addListener(Priority.LOWEST, engine::endWorldTick);
        ServerAboutToStartEvent.BUS.addListener(this::beforeServer);
        ServerStoppedEvent.BUS.addListener(this::stopped);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysterrainsmoother.client.ClientProxy.register(context);
        }
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
