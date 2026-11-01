package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    public NativeBootstrap() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::started);
    }

    private void setup(FMLCommonSetupEvent event) {
        require(TerrainSmoothingApi.hasMaterial(new ResourceLocation("minecraft:stone")),
                "Built-ins are available from a contributor's native setup callback");
        TerrainSmoothingApi.addBiomeRule((material, biome) -> true);
        setupComplete = true;
    }

    private void started(FMLServerStartedEvent event) {
        ServerWorld world = event.getServer().getWorld(DimensionType.OVERWORLD);
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
            require(state.getBlock() == block && state.get(SandSlabBlock.TYPE)
                    == ((metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM), "Legacy sand orientation");
            require(!state.get(SandSlabBlock.WATERLOGGED), "Legacy sand begins dry");
            require(block.getStateContainer().getValidStates().size() == 4, "No double sand state");
            CompoundNBT oldStack = new CompoundNBT();
            oldStack.putString("id", "skysterrainsmoother:sand_slab");
            oldStack.putByte("Count", (byte) 17);
            oldStack.putShort("Damage", (short) metadata);
            CompoundNBT custom = new CompoundNBT();
            custom.putString("Name", "Kept sand");
            oldStack.put("tag", custom);
            SandLegacyRecovery.recoverItems(oldStack);
            require(oldStack.getString("id").equals(block.getRegistryName().toString())
                    && oldStack.getByte("Count") == 17 && custom.equals(oldStack.getCompound("tag")),
                    "Raw sand stack retains count and custom data");
            CompoundNBT once = oldStack.copy();
            SandLegacyRecovery.recoverItems(oldStack);
            require(once.equals(oldStack), "Sand item recovery is idempotent");
            checks += 5;
            checks += falling(world, state, metadata);
        }
        File root = event.getServer().getActiveAnvilConverter()
                .getFile(event.getServer().getFolderName(), "level.dat").getParentFile();
        Properties receipt = new Properties();
        receipt.setProperty("checks", Integer.toString(checks));
        receipt.setProperty("materials", Integer.toString(MaterialCatalogue.instance().size()));
        try (FileOutputStream output = new FileOutputStream(new File(root, "native-terrain.properties"))) {
            receipt.store(output, "Native Terrain Smoother checks");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot write native check receipt", failure);
        }
        System.out.println("TERRAIN_NATIVE_BOOTSTRAP_PASS checks=" + checks);
        event.getServer().initiateShutdown(false);
    }

    private static int falling(ServerWorld world, BlockState state, int metadata) {
        BlockPos source = new BlockPos(80 + metadata * 4, 220, 80);
        world.getChunk(source);
        for (int height = 215; height <= 223; height++) {
            world.setBlockState(new BlockPos(source.getX(), height, source.getZ()), Blocks.AIR.getDefaultState(), 2);
        }
        world.setBlockState(source.down(5), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(source, state, 2);
        ((SandSlabBlock) state.getBlock()).tick(state, world, source, new java.util.Random(1));
        java.util.List<FallingBlockEntity> entities = world.getEntitiesWithinAABB(FallingBlockEntity.class,
                new AxisAlignedBB(source).grow(1));
        require(entities.size() == 1, "Native falling entity created");
        FallingBlockEntity falling = entities.get(0);
        for (int tick = 0; tick < 100 && falling.isAlive(); tick++) {
            falling.tick();
        }
        require(!falling.isAlive() && world.getBlockState(source.down(4)).equals(state),
                "Falling preserves native sand colour and orientation");
        world.setBlockState(source, state.with(SandSlabBlock.WATERLOGGED, true), 2);
        ((SandSlabBlock) state.getBlock()).tick(world.getBlockState(source), world, source, new java.util.Random(1));
        entities = world.getEntitiesWithinAABB(FallingBlockEntity.class, new AxisAlignedBB(source).grow(1));
        entities.removeIf(entity -> !entity.isAlive());
        require(entities.size() == 1, "Wet sand begins falling");
        FallingBlockEntity wet = entities.get(0);
        require(!wet.getBlockState().get(SandSlabBlock.WATERLOGGED), "Falling sand carries no source water");
        wet.tick();
        require(world.getFluidState(source).getFluid() == Fluids.WATER, "Water remains at the source");
        wet.remove();
        return 5;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
