package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.IEventBus;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainSmoothingApi;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandLegacyRecovery;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Small native lifecycle and falling-state checks, isolated from production launches. */
@Mod("terrainbootstrap")
public final class NativeBootstrap {
    private boolean setupComplete;

    public NativeBootstrap(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void setup(FMLCommonSetupEvent event) {
        require(TerrainSmoothingApi.hasMaterial(ResourceLocation.parse("minecraft:stone")),
                "Built-ins are available from a contributor's native setup callback");
        TerrainSmoothingApi.addBiomeRule((material, biome) -> true);
        setupComplete = true;
    }

    private void started(ServerStartedEvent event) {
        ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
        require(setupComplete && MaterialCatalogue.instance().frozen(), "Native setup completed and froze");
        require(GrassSlabsApi.hasSmoothingOwner(), "Grass generation ownership was claimed");
        boolean lateRejected = false;
        try {
            TerrainSmoothingApi.addBiomeRule((material, biome) -> true);
        } catch (IllegalStateException expected) {
            lateRejected = true;
        }
        require(lateRejected, "Late registration rejected");
        int checks = 3;
        for (int metadata = 0; metadata < 4; metadata++) {
            BlockState state = SandContent.legacyState(metadata);
            Block block = (metadata & 2) == 0 ? SandContent.slab : SandContent.redSlab;
            require(state.getBlock() == block && state.getValue(SandSlabBlock.TYPE)
                    == ((metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM), "Legacy sand orientation");
            require(!state.getValue(SandSlabBlock.WATERLOGGED), "Legacy sand begins dry");
            require(block.getStateDefinition().getPossibleStates().size() == 4, "No double sand state");
            CompoundTag oldStack = new CompoundTag();
            oldStack.putString("id", "skysterrainsmoother:sand_slab");
            oldStack.putByte("Count", (byte) 17);
            oldStack.putShort("Damage", (short) metadata);
            CompoundTag custom = new CompoundTag();
            custom.putString("Name", "Kept sand");
            oldStack.put("tag", custom);
            SandLegacyRecovery.recoverItems(oldStack);
            require(oldStack.getString("id").equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString())
                    && oldStack.getByte("Count") == 17 && custom.equals(oldStack.getCompound("tag")),
                    "Raw sand stack retains count and custom data");
            CompoundTag once = oldStack.copy();
            SandLegacyRecovery.recoverItems(oldStack);
            require(once.equals(oldStack), "Sand item recovery is idempotent");
            checks += 5;
            checks += falling(world, state, metadata, 220);
            checks += falling(world, state, metadata, -56);
        }
        File root = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        Properties receipt = new Properties();
        receipt.setProperty("checks", Integer.toString(checks));
        receipt.setProperty("materials", Integer.toString(MaterialCatalogue.instance().size()));
        try (FileOutputStream output = new FileOutputStream(new File(root, "native-terrain.properties"))) {
            receipt.store(output, "Native Terrain Smoother checks");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot write native check receipt", failure);
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_NATIVE_BOOTSTRAP_PASS checks=" + checks);
        event.getServer().halt(false);
    }

    private static int falling(ServerLevel world, BlockState state, int metadata, int sourceY) {
        BlockPos source = new BlockPos(80 + metadata * 4, sourceY, 80);
        world.getChunk(source);
        for (int height = sourceY - 5; height <= sourceY + 3; height++) {
            world.setBlock(new BlockPos(source.getX(), height, source.getZ()), Blocks.AIR.defaultBlockState(), 2);
        }
        world.setBlock(source.below(5), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(source, state, 2);
        ((SandSlabBlock) state.getBlock()).tick(state, world, source, net.minecraft.util.RandomSource.create(1));
        java.util.List<FallingBlockEntity> entities = world.getEntitiesOfClass(FallingBlockEntity.class,
                new AABB(source).inflate(1));
        require(entities.size() == 1, "Native falling entity created");
        FallingBlockEntity falling = entities.get(0);
        for (int tick = 0; tick < 100 && falling.isAlive(); tick++) {
            falling.tick();
        }
        require(!falling.isAlive() && world.getBlockState(source.below(4)).equals(state),
                "Falling preserves native sand colour and orientation");
        world.setBlock(source, state.setValue(SandSlabBlock.WATERLOGGED, true), 2);
        ((SandSlabBlock) state.getBlock()).tick(world.getBlockState(source), world, source, net.minecraft.util.RandomSource.create(1));
        entities = world.getEntitiesOfClass(FallingBlockEntity.class, new AABB(source).inflate(1));
        entities.removeIf(entity -> !entity.isAlive());
        require(entities.size() == 1, "Wet sand begins falling");
        FallingBlockEntity wet = entities.get(0);
        require(!wet.getBlockState().getValue(SandSlabBlock.WATERLOGGED), "Falling sand carries no source water");
        wet.tick();
        require(world.getFluidState(source).getType() == Fluids.WATER, "Water remains at the source");
        wet.discard();
        return 5;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
