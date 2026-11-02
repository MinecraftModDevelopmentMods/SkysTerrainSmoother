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

        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().getWindow());

        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ran) {
            if (integrated) confirmDisposableWorld(minecraft);
            if (integrated && ClientStatus.complete && minecraft.level != null && minecraft.player != null
                    && minecraft.getConnection() != null
                    && !minecraft.getConnection().getRecipeManager().getRecipes().isEmpty()) {
                if (++settledWorldTicks < 20) return;
                RecipePreviewModels.check(minecraft);
                checkBiomeTints(minecraft);
                zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");
                minecraft.stop();
                integrated = false;
            }

            return;
        }
        if (minecraft.screen == null || minecraft.getOverlay() != null) {
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
                for (net.minecraft.world.level.block.state.BlockState state : zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB
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
                minecraft.loadLevel("terrain-smoother-world");
            } else {
                net.minecraft.core.RegistryAccess.RegistryHolder registries =
                        net.minecraft.core.RegistryAccess.builtin();
                net.minecraft.core.Registry<net.minecraft.world.level.dimension.DimensionType> dimensions =
                        registries.registryOrThrow(net.minecraft.core.Registry.DIMENSION_TYPE_REGISTRY);
                net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes =
                        registries.registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);
                net.minecraft.world.level.levelgen.WorldGenSettings defaults =
                        net.minecraft.world.level.levelgen.WorldGenSettings.makeDefault(dimensions, biomes,
                                registries.registryOrThrow(net.minecraft.core.Registry.NOISE_GENERATOR_SETTINGS_REGISTRY));
                net.minecraft.world.level.levelgen.WorldGenSettings generator =
                        new net.minecraft.world.level.levelgen.WorldGenSettings(4815162342L, false, false,
                                defaults.dimensions());
                net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(
                        "Terrain Smoother Test", net.minecraft.world.level.GameType.CREATIVE, false,
                        net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                        net.minecraft.world.level.DataPackConfig.DEFAULT);
                minecraft.createLevel("terrain-smoother-world", settings, registries, generator);
            }
        } else {
            minecraft.stop();
        }
    }

    private static void confirmDisposableWorld(Minecraft client) {
        if (!new File(client.gameDirectory, "terrain-smoother-client-pass.txt").isFile()
                || !System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            return;
        }
        net.minecraft.client.gui.screens.Screen screen = client.screen;
        if (screen == null || !(screen.getTitle() instanceof net.minecraft.network.chat.TranslatableComponent)
                || !((net.minecraft.network.chat.TranslatableComponent) screen.getTitle()).getKey()
                        .equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.network.chat.Component proceed;
        if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            proceed = new net.minecraft.network.chat.TranslatableComponent("selectWorld.backupJoinSkipButton");
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
                    zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_NATIVE_EXPERIMENTAL_WORLD_CONFIRMATION");
                    button.onPress();
                    return;
                }
            }
        }
        throw new AssertionError("The native experimental-world confirmation has no proceed action");
    }

    private static void checkBiomeTints(Minecraft client) {
        net.minecraft.core.BlockPos sample = client.player.blockPosition();
        int expected = client.getBlockColors().getColor(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),
                client.level, sample, 0);
        int count = 0;
        for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block :
                zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS.values()) {
            if (block.palette.group.equals("grass")) {
                RuntimeProbe.require(client.getBlockColors().getColor(block.defaultBlockState(), client.level, sample, 0)
                                == expected, "Fine grass uses the actual client biome tint");
                count++;
            }
        }
        RuntimeProbe.require(count > 0, "Grass pieces reached the colour checks");
        RuntimeProbe.require(client.getBlockColors().getColor(
                        zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB.defaultBlockState(), client.level, sample, 0)
                        == expected, "Grass slabs retain the actual client biome tint");
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_CLIENT_TINT_PASS palettes=" + count);
    }

    private static void checkModel(Minecraft minecraft, net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.client.resources.model.BakedModel model = minecraft.getBlockRenderer()
                .getBlockModel(state);

        RuntimeProbe.require(model != minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
                .getMissingModel(), "missing model " + state);

        for (net.minecraft.core.Direction face : new net.minecraft.core.Direction[] { null,
                net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST }) {
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(state, face, new java.util.Random(0))) {
                RuntimeProbe.require(!quad.getSprite().getName().toString().contains("missingno"), "missing texture " + state);
            }
        }
    }
}
