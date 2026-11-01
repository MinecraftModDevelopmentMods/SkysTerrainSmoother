package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean integrated;
    private boolean resourcesChecked;
    private int settledWorldTicks;

    @Override
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getMainWindow().getHandle());

        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ran) {
            if (integrated && ClientStatus.complete && minecraft.world != null && minecraft.player != null
                    && minecraft.getConnection() != null
                    && !minecraft.getConnection().getRecipeManager().getRecipes().isEmpty()) {
                if (++settledWorldTicks < 20) return;
                RecipePreviewModels.check(minecraft);
                checkBiomeTints(minecraft);
                System.out.println("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");
                minecraft.shutdown();
                integrated = false;
            }

            return;
        }
        if (minecraft.currentScreen == null || minecraft.getLoadingGui() != null) {
            return;
        }

        try {
            if (!resourcesChecked) {
                MineralogyProbe.run();
                FineGrassModelProbe.run(minecraft);
                checkModel(minecraft, RuntimeProbe.probeOre.getDefaultState());

                for (net.minecraft.block.Block block : new net.minecraft.block.Block[] {
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.slab,
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.redSlab }) {
                    for (net.minecraft.block.BlockState state : block.getStateContainer().getValidStates()) {
                        checkModel(minecraft, state);
                    }
                }
                for (net.minecraft.block.BlockState state : zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB
                        .getStateContainer().getValidStates()) {
                    checkModel(minecraft, state);
                }
                for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block : zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS
                        .values()) {
                    for (net.minecraft.block.BlockState state : block.getStateContainer().getValidStates()) {
                        checkModel(minecraft, state);
                    }
                }
                resourcesChecked = true;
                ConfigScreenProbe.begin(minecraft);
                return;
            }
            if (!ConfigScreenProbe.tick(minecraft)) return;
            ran = true;
            try (Writer writer = new FileWriter(new File(minecraft.gameDir, "terrain-smoother-client-pass.txt"))) {
                writer.write("TERRAIN_SMOOTHER_CLIENT_PASS\n");
            }

            System.out.println("TERRAIN_SMOOTHER_CLIENT_PASS");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        if (System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            integrated = true;
            minecraft.launchIntegratedServer("terrain-smoother-world", "Terrain Smoother Test",
                    new net.minecraft.world.WorldSettings(4815162342L, net.minecraft.world.GameType.CREATIVE, false,
                            false, net.minecraft.world.WorldType.DEFAULT));
        } else {
            minecraft.shutdown();
        }
    }

    private static void checkBiomeTints(Minecraft client) {
        net.minecraft.util.math.BlockPos sample = new net.minecraft.util.math.BlockPos(client.player);
        int expected = client.getBlockColors().getColor(net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState(),
                client.world, sample, 0);
        int count = 0;
        for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block :
                zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                RuntimeProbe.require(client.getBlockColors().getColor(block.getDefaultState(), client.world, sample, 0)
                                == expected, "Fine grass uses the actual client biome tint");
                count++;
            }
        }
        RuntimeProbe.require(count > 0, "Grass pieces reached the colour checks");
        RuntimeProbe.require(client.getBlockColors().getColor(
                        zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB.getDefaultState(), client.world, sample, 0)
                        == expected, "Grass slabs retain the actual client biome tint");
        System.out.println("TERRAIN_SMOOTHER_CLIENT_TINT_PASS palettes=" + count);
    }

    private static void checkModel(Minecraft minecraft, net.minecraft.block.BlockState state) {
        net.minecraft.client.renderer.model.IBakedModel model = minecraft.getBlockRendererDispatcher()
                .getModelForState(state);

        RuntimeProbe.require(model != minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                .getMissingModel(), "missing model " + state);

        for (net.minecraft.util.Direction face : new net.minecraft.util.Direction[] { null,
                net.minecraft.util.Direction.DOWN, net.minecraft.util.Direction.UP,
                net.minecraft.util.Direction.NORTH, net.minecraft.util.Direction.SOUTH,
                net.minecraft.util.Direction.WEST, net.minecraft.util.Direction.EAST }) {
            for (net.minecraft.client.renderer.model.BakedQuad quad : model.getQuads(state, face, new java.util.Random(0))) {
                RuntimeProbe.require(!quad.func_187508_a().getName().toString().contains("missingno"), "missing texture " + state);
            }
        }
    }
}
