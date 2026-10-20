package zone.moddev.mc.skysterrainsmoother.probe;

import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ClientProbe extends ProbeProxy {
    private boolean ran;
    private boolean integrated;

    @Override
    public void init() {
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                net.minecraft.item.Item.getItemFromBlock(RuntimeProbe.probeOre), 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation("minecraft:stone", "inventory"));
        net.minecraftforge.client.model.ModelLoader.setCustomStateMapper(RuntimeProbe.probeOre,
                new net.minecraft.client.renderer.block.statemap.StateMapperBase() {
                    @Override
                    protected net.minecraft.client.renderer.block.model.ModelResourceLocation getModelResourceLocation(
                            net.minecraft.block.state.IBlockState state) {
                        return new net.minecraft.client.renderer.block.model.ModelResourceLocation("minecraft:stone",
                                "normal");
                    }
                });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();

        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ran) {
            if (integrated && ClientStatus.complete) {
                System.out.println("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS");
                minecraft.shutdown();
                integrated = false;
            }

            return;
        }
        if (minecraft.currentScreen == null) {
            return;
        }

        ran = true;

        try {
            checkModel(minecraft, RuntimeProbe.probeOre.getDefaultState());

            for (net.minecraft.block.state.IBlockState state : zone.moddev.mc.skysgrassslabs.init.ModBlocks.GRASS_SLAB
                    .getBlockState().getValidStates()) {
                checkModel(minecraft, state);
            }
            for (zone.moddev.mc.skysbuildingpieces.content.PieceBlock block : zone.moddev.mc.skysbuildingpieces.content.Pieces.BLOCKS
                    .values()) {
                for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates()) {
                    if (state.getValue(zone.moddev.mc.skysbuildingpieces.content.PieceBlock.META)
                            / block.palette.shape.states >= block.palette.materials.size()) {
                        continue;
                    }

                    checkModel(minecraft, state);
                }
            }
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
                    new net.minecraft.world.WorldSettings(4815162342L, net.minecraft.world.GameType.CREATIVE, true,
                            false, net.minecraft.world.WorldType.DEFAULT));
        } else {
            minecraft.shutdown();
        }
    }

    private static void checkModel(Minecraft minecraft, net.minecraft.block.state.IBlockState state) {
        net.minecraft.client.renderer.block.model.IBakedModel model = minecraft.getBlockRendererDispatcher()
                .getModelForState(state);

        RuntimeProbe.require(model != minecraft.getBlockRendererDispatcher().getBlockModelShapes().getModelManager()
                .getMissingModel(), "missing model " + state);

        for (net.minecraft.util.EnumFacing face : new net.minecraft.util.EnumFacing[] { null,
                net.minecraft.util.EnumFacing.DOWN, net.minecraft.util.EnumFacing.UP,
                net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.SOUTH,
                net.minecraft.util.EnumFacing.WEST, net.minecraft.util.EnumFacing.EAST }) {
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(state, face, 0)) {
                RuntimeProbe.require(!quad.getSprite().getIconName().contains("missingno"), "missing texture " + state);
            }
        }
    }
}
