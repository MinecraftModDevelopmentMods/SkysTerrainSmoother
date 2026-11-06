package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean integrated;
    private boolean confirmedWorld;
    private boolean resourcesChecked;
    private int settledWorldTicks;
    private boolean requestedRecipes;
    private volatile java.util.Set<net.minecraft.world.item.crafting.display.RecipeDisplayId> recipeDisplays;

    @Override
    public void init() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::tick);
    }

    public void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().handle());

        if (ran) {
            if (integrated && !confirmedWorld) confirmDisposableWorld(minecraft);
            if (integrated && ClientStatus.complete && minecraft.level != null && minecraft.player != null
                    && minecraft.getConnection() != null && minecraft.getSingleplayerServer() != null) {
                if (!requestedRecipes) {
                    requestedRecipes = true;
                    var server = minecraft.getSingleplayerServer();
                    server.execute(() -> recipeDisplays = RecipePreviewModels.unlock(server));
                }
                if (recipeDisplays == null || !RecipePreviewModels.ready(minecraft, recipeDisplays)) {
                    RuntimeProbe.require(++settledWorldTicks < 400, "Native client recipe displays arrived");
                    return;
                }
                RecipePreviewModels.check(minecraft, recipeDisplays);
                checkBiomeTints(minecraft);
                zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");
                minecraft.stop();
                integrated = false;
            }

            return;
        }
        if (minecraft.gui.screen() == null || minecraft.gui.overlay() != null) {
            return;
        }

        try {
            if (!resourcesChecked) {
                MineralogyProbe.run();
                FineGrassModelProbe.run(minecraft);
                checkModel(minecraft, RuntimeProbe.probeOre.defaultBlockState());

                for (net.minecraft.world.level.block.Block block : new net.minecraft.world.level.block.Block[] {
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.slab,
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.redSlab }) {
                    for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                        checkModel(minecraft, state);
                    }
                }
                for (net.minecraft.world.level.block.state.BlockState state : zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB.get()
                        .getStateDefinition().getPossibleStates()) {
                    checkModel(minecraft, state);
                }
                for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block : zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS
                        .values()) {
                    for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                        checkModel(minecraft, state);
                    }
                }
                resourcesChecked = true;
                ConfigScreenProbe.begin(minecraft);
                return;
            }
            if (!ConfigScreenProbe.tick(minecraft)) return;
            ran = true;
            try (Writer writer = new FileWriter(new File(minecraft.gameDirectory, "terrain-smoother-client-pass.txt"))) {
                writer.write("TERRAIN_SMOOTHER_CLIENT_PASS\n");
            }

            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLIENT_PASS");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        if (System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            integrated = true;
            if (new File(minecraft.gameDirectory, "saves/terrain-smoother-world/level.dat").isFile()) {
                minecraft.createWorldOpenFlows().openWorld("terrain-smoother-world",
                        () -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
            } else {
                net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                        "Terrain Smoother Test", net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("terrain-smoother-world", settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(4815162342L, false, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, minecraft.gui.screen());
            }
        } else {
            minecraft.stop();
        }
    }

    private void confirmDisposableWorld(Minecraft client) {
        if (!new File(client.gameDirectory, "terrain-smoother-client-pass.txt").isFile()
                || !System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            return;
        }
        net.minecraft.client.gui.screens.Screen screen = client.gui.screen();
        if (screen == null || !(screen.getTitle().getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents title)
                || !title.getKey().equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.network.chat.Component proceed;
        if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            proceed = net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton");
        } else if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
            proceed = net.minecraft.network.chat.CommonComponents.GUI_PROCEED;
        } else {
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button) {
                net.minecraft.client.gui.components.Button button =
                        (net.minecraft.client.gui.components.Button) child;
                if (button.getMessage().equals(proceed)) {
                    // Only the marked probe's disposable world reaches this confirmation.
                    // The prompt stays visible while the asynchronous load starts.
                    confirmedWorld = true;
                    zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_NATIVE_EXPERIMENTAL_WORLD_CONFIRMATION");
                    button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                    return;
                }
            }
        }
        throw new AssertionError("The native experimental-world confirmation has no proceed action");
    }

    private static void checkBiomeTints(Minecraft client) {
        net.minecraft.core.BlockPos sample = client.player.blockPosition();
        int expected = tint(client, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), sample);
        int count = 0;
        for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block :
                zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                RuntimeProbe.require(tint(client, block.defaultBlockState(), sample)
                                == expected, "Fine grass uses the actual client biome tint");
                count++;
            }
        }
        RuntimeProbe.require(count > 0, "Grass pieces reached the colour checks");
        RuntimeProbe.require(tint(client, zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB.get().defaultBlockState(), sample)
                        == expected, "Grass slabs retain the actual client biome tint");
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLIENT_TINT_PASS palettes=" + count);
    }

    private static int tint(Minecraft client, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.core.BlockPos position) {
        var source = client.getBlockColors().getTintSource(state, 0);
        RuntimeProbe.require(source != null, "Missing block tint: " + state);
        return source.colorInWorld(state, client.level, position);
    }

    private static void checkModel(Minecraft minecraft, net.minecraft.world.level.block.state.BlockState state) {
        var models = minecraft.getModelManager().getBlockStateModelSet();
        var model = models.get(state);
        RuntimeProbe.require(model != models.missingModel(), "missing model " + state);
        var parts = new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(net.minecraft.util.RandomSource.create(0), parts);

        for (net.minecraft.core.Direction face : new net.minecraft.core.Direction[] { null,
                net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST }) {
            for (net.minecraft.client.resources.model.geometry.BakedQuad quad : parts.stream()
                    .flatMap(part -> part.getQuads(face).stream()).toList()) {
                RuntimeProbe.require(!quad.materialInfo().sprite().contents().name().toString().contains("missingno"), "missing texture " + state);
            }
        }
    }
}
