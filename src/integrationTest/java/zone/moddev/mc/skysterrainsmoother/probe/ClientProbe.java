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
                System.out.println("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");
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

                for (net.minecraft.block.Block block : new net.minecraft.block.Block[] {
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.slab,
                        zone.moddev.mc.skysterrainsmoother.content.SandContent.redSlab }) {
                    for (net.minecraft.block.BlockState state : block.getStateDefinition().getPossibleStates()) {
                        checkModel(minecraft, state);
                    }
                }
                for (net.minecraft.block.BlockState state : zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB
                        .getStateDefinition().getPossibleStates()) {
                    checkModel(minecraft, state);
                }
                for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block : zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS
                        .values()) {
                    for (net.minecraft.block.BlockState state : block.getStateDefinition().getPossibleStates()) {
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

            System.out.println("TERRAIN_SMOOTHER_CLIENT_PASS");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        if (System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            integrated = true;
            if (new File(minecraft.gameDirectory, "saves/terrain-smoother-world/level.dat").isFile()) {
                minecraft.loadLevel("terrain-smoother-world");
            } else {
                net.minecraft.util.registry.DynamicRegistries.Impl registries =
                        net.minecraft.util.registry.DynamicRegistries.builtin();
                net.minecraft.util.registry.Registry<net.minecraft.world.DimensionType> dimensions =
                        registries.registryOrThrow(net.minecraft.util.registry.Registry.DIMENSION_TYPE_REGISTRY);
                net.minecraft.util.registry.Registry<net.minecraft.world.biome.Biome> biomes =
                        registries.registryOrThrow(net.minecraft.util.registry.Registry.BIOME_REGISTRY);
                net.minecraft.world.gen.settings.DimensionGeneratorSettings defaults =
                        net.minecraft.world.gen.settings.DimensionGeneratorSettings.makeDefault(dimensions, biomes,
                                registries.registryOrThrow(net.minecraft.util.registry.Registry.NOISE_GENERATOR_SETTINGS_REGISTRY));
                net.minecraft.world.gen.settings.DimensionGeneratorSettings generator =
                        new net.minecraft.world.gen.settings.DimensionGeneratorSettings(4815162342L, false, false,
                                defaults.dimensions());
                net.minecraft.world.WorldSettings settings = new net.minecraft.world.WorldSettings(
                        "Terrain Smoother Test", net.minecraft.world.GameType.CREATIVE, false,
                        net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(),
                        net.minecraft.util.datafix.codec.DatapackCodec.DEFAULT);
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
        net.minecraft.client.gui.screen.Screen screen = client.screen;
        if (screen == null || !(screen.getTitle() instanceof net.minecraft.util.text.TranslationTextComponent)
                || !((net.minecraft.util.text.TranslationTextComponent) screen.getTitle()).getKey()
                        .equals("selectWorld.backupQuestion.experimental")) {
            return;
        }
        net.minecraft.util.text.ITextComponent proceed;
        if (screen instanceof net.minecraft.client.gui.screen.ConfirmBackupScreen) {
            proceed = new net.minecraft.util.text.TranslationTextComponent("selectWorld.backupJoinSkipButton");
        } else if (screen instanceof net.minecraft.client.gui.screen.ConfirmScreen) {
            proceed = net.minecraft.client.gui.DialogTexts.GUI_PROCEED;
        } else {
            return;
        }
        for (net.minecraft.client.gui.IGuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.widget.button.Button) {
                net.minecraft.client.gui.widget.button.Button button =
                        (net.minecraft.client.gui.widget.button.Button) child;
                if (button.getMessage().equals(proceed)) {
                    // Only the marked probe's disposable world reaches this confirmation.
                    System.out.println("TERRAIN_NATIVE_EXPERIMENTAL_WORLD_CONFIRMATION");
                    button.onPress();
                    return;
                }
            }
        }
        throw new AssertionError("The native experimental-world confirmation has no proceed action");
    }

    private static void checkBiomeTints(Minecraft client) {
        net.minecraft.util.math.BlockPos sample = client.player.blockPosition();
        int expected = client.getBlockColors().getColor(net.minecraft.block.Blocks.GRASS_BLOCK.defaultBlockState(),
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
        System.out.println("TERRAIN_SMOOTHER_CLIENT_TINT_PASS palettes=" + count);
    }

    private static void checkModel(Minecraft minecraft, net.minecraft.block.BlockState state) {
        net.minecraft.client.renderer.model.IBakedModel model = minecraft.getBlockRenderer()
                .getBlockModel(state);

        RuntimeProbe.require(model != minecraft.getBlockRenderer().getBlockModelShaper().getModelManager()
                .getMissingModel(), "missing model " + state);

        for (net.minecraft.util.Direction face : new net.minecraft.util.Direction[] { null,
                net.minecraft.util.Direction.DOWN, net.minecraft.util.Direction.UP,
                net.minecraft.util.Direction.NORTH, net.minecraft.util.Direction.SOUTH,
                net.minecraft.util.Direction.WEST, net.minecraft.util.Direction.EAST }) {
            for (net.minecraft.client.renderer.model.BakedQuad quad : model.getQuads(state, face, new java.util.Random(0))) {
                RuntimeProbe.require(!quad.getSprite().getName().toString().contains("missingno"), "missing texture " + state);
            }
        }
    }
}
