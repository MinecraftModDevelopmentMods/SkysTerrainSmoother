package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.*;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.catalogue.Geometry;
import zone.moddev.mc.skysbuildingpieces.content.*;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysgrassslabs.block.GrassSpread;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import zone.moddev.mc.skysterrainsmoother.*;
import zone.moddev.mc.skysterrainsmoother.api.*;
import zone.moddev.mc.skysterrainsmoother.internal.*;

/** Build-only integration contributor and real-world probe, excluded from ordinary launches. */
@Mod("terrainsmootherprobe")
public final class RuntimeProbe {
    static Block probeOre;
    private MinecraftServer server;
    private boolean ran;
    private boolean gameplayComplete;
    private int completedGameplay;
    private ServerPlayer trackedPlayer;
    private long trackingStarted;
    private long generationBefore;
    private int generationCenterX;
    private int generationCenterZ;
    private boolean trackedAreaWasReady;
    private boolean forwardPrepared;
    private int forwardWaitTicks;

    public RuntimeProbe(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(this::registerProbeOre);
        modBus.addListener(this::init);
        NeoForge.EVENT_BUS.register(this);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            new ClientProbe().init();
        }
    }

    public void registerProbeOre(net.neoforged.neoforge.registries.RegisterEvent event) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("terrainsmootherprobe", "ore_support");
        event.register(net.minecraft.core.registries.Registries.BLOCK, registry -> {
            probeOre = new Block(Block.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.STONE));
            registry.register(id, probeOre);
        });
        event.register(net.minecraft.core.registries.Registries.ITEM, registry ->
                registry.register(id, new net.minecraft.world.item.BlockItem(
                        probeOre, new net.minecraft.world.item.Item.Properties())));
    }

    public void init(FMLCommonSetupEvent event) {
        BlockState[] steps = new BlockState[4];
        BlockState[] corners = new BlockState[4];

        Direction[] directions = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            steps[directionIndex] = BuildingPiecesApi.bottomPiece("minecraft:obsidian",
                    BuildingPiecesApi.TerrainShape.STEP, directions[directionIndex]);
            corners[directionIndex] = BuildingPiecesApi.bottomPiece("minecraft:obsidian",
                    BuildingPiecesApi.TerrainShape.CORNER, directions[directionIndex]);
        }

        ResourceLocation registryId = ResourceLocation.parse("probe:obsidian");

        TerrainSmoothingApi.registerMaterial(new TerrainMaterial(registryId,
                Collections.singleton(Blocks.OBSIDIAN.defaultBlockState()), BuildingPiecesApi
                        .bottomPiece("minecraft:obsidian", BuildingPiecesApi.TerrainShape.SLAB, Direction.NORTH),
                null, null, false, null));
        TerrainSmoothingApi.addShapes(registryId, steps, corners);


        TerrainSmoothingApi.addAreaProtection((dimension, pos, biome) -> pos.getY() == 241);
    }

    @SubscribeEvent
    public void started(ServerStartedEvent event) {
        server = event.getServer();
        checkFeatureRegistration();
    }

    private void checkFeatureRegistration() {
        int eligible = 0;
        int excluded = 0;
        var smoothing = net.minecraft.core.registries.BuiltInRegistries.FEATURE.get(
                ResourceLocation.fromNamespaceAndPath("skysterrainsmoother", "terrain_smoothing"));
        require(smoothing instanceof TerrainSmoothingFeature, "The registered terrain smoothing feature is missing");
        int vegetation = net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        var biomes = server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var biome : biomes.holders().toList()) {
            List<net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>> stages =
                    biome.value().getGenerationSettings().features();
            net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature> features =
                    vegetation < stages.size() ? stages.get(vegetation) : net.minecraft.core.HolderSet.direct(Collections.emptyList());
            long occurrences = features.stream().filter(feature -> feature.value().getFeatures()
                    .anyMatch(configured -> configured.feature() == smoothing)).count();
            boolean excludedBiome = biome.is(net.minecraft.tags.BiomeTags.IS_NETHER)
                    || biome.is(net.minecraft.tags.BiomeTags.IS_END);
            if (excludedBiome) {
                require(occurrences == 0, "Nether and End biomes exclude terrain smoothing");
                excluded++;
            } else {
                require(occurrences == 1 && features.get(0).value().getFeatures()
                        .anyMatch(configured -> configured.feature() == smoothing),
                        "Terrain smoothing occurs once, at the start of vegetation decoration");
                eligible++;
            }
        }
        require(eligible > 0 && excluded > 0, "The native biome registry contains both biome groups");
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_FEATURE_REGISTRATION_PASS eligible=" + eligible + " excluded=" + excluded);
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        if (server == null || ran) {
            return;
        }

        try {
            ServerLevel world = server.getLevel(Level.OVERWORLD);
        if (Boolean.getBoolean("skysterrainsmoother.forwardFixture")) {
            try {
                if (!forwardPrepared) {
                    FamilyForwardChecks.prepare(world);
                    forwardPrepared = true;
                }
                if (!FamilyForwardChecks.ready(world)) {
                    require(++forwardWaitTicks <= 1200, "Saved fixture chunks and original entity UUIDs finish loading");
                    return;
                }
                int checked = FamilyForwardChecks.run(world);
                FamilyForwardChecks.release(world);
                ran = true;
                File marker = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "family-forward-" + phase() + ".properties");
                try (Writer output = new OutputStreamWriter(new FileOutputStream(marker), java.nio.charset.StandardCharsets.UTF_8)) {
                    output.write("checks=" + checked + "\ncomplete=true\n");
                }
                zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_RUNTIME_PASS phase=" + phase() + " forward_checks=" + checked);
                if (System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
                    ClientStatus.complete = true;
                } else {
                    server.halt(false);
                }
                return;
            } catch (Exception failure) {
                throw new IllegalStateException("The saved family fixture did not survive its native upgrade", failure);
            }
        }


            if (!gameplayComplete) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule randomTickSpeed 0");
                require(GrassSlabsApi.hasSmoothingOwner(), "ownership claimed before Grass Slabs startup");
                require(SkysTerrainSmoother.engine.indexedChunks() > 0 == phase().equals("reload"),
                        "saved-chunk index lifecycle");

                boolean late = false;

                try {
                    TerrainSmoothingApi.addBiomeRule((id, biome) -> true);
                } catch (IllegalStateException expected) {
                    late = true;
                }

                require(late, "late API registration rejected");

                int gameplay = 0;
                if (Boolean.getBoolean("skysterrainsmoother.generationOnly")) {
                    require(SkysTerrainSmoother.level == Integer.getInteger("skysterrainsmoother.expectedLevel", 3),
                            "The native configuration selected the requested generation level");
                } else {
                    gameplay = FrozenTerrainProbe.run(world) + SnowTerrainProbe.run(world) + MineralogyProbe.run() + FineGrassProbe.run(world) + OpportunityProbe.run(world)
                            + TransitionProbe.run(world) + FoliageProbe.run(world) + persistence(world)
                            + LateTerrainProbe.run(world) + CaveProbe.run(world) + decorationChanges(world)
                            + geometry(world) + grass(world) + sand(world) + generationOrder(world)
                            + LateTerrainProbe.generationOrder(world);
                }
                completedGameplay = gameplay;
                beginTrackedGeneration(world);
                gameplayComplete = true;
                return;
            }
            if (!trackingReady(world)) {
                return;
            }
            int gameplay = completedGameplay;
            int generation = finishTrackedGeneration(world);
            ran = true;
            Properties values = new Properties();
            File marker = new File(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                    "skysterrainsmoother-integration.properties");

            if (marker.isFile()) {
                try (InputStream input = new FileInputStream(marker)) {
                    values.load(input);
                }
            }

            values.setProperty(phase() + "_complete", "true");
            values.setProperty("gameplay_checks", Integer.toString(gameplay));
            values.setProperty("generation_checks", Integer.toString(generation));

            try (OutputStream output = new FileOutputStream(marker)) {
                values.store(output, "Terrain Smoother disposable verification");
            }

            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_RUNTIME_PASS phase=" + phase() + " level=" + SkysTerrainSmoother.level
                    + " gameplay=" + gameplay + " generated=" + generation);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new IllegalStateException("Terrain Smoother probe failed", failure);
        }
        if (System.getProperty("skysterrainsmoother.integrationPhase", "").startsWith("client-")) {
            ClientStatus.complete = true;
        } else {
            server.halt(false);
        }
    }

    private static String phase() {
        return System.getProperty("skysterrainsmoother.integrationPhase", "fresh").replace("client-", "");
    }

    private static int sand(ServerLevel world) {
        return SandProbe.run(world);
    }

    private static int decorationChanges(ServerLevel world) {
        BlockPos position = new BlockPos(1100, 200, 1100);
        LevelChunk owner = world.getChunkAt(position);
        int checks = 0;

        for (int level = 1; level <= 3; level++) {
            for (Block replacement : new Block[] { Blocks.SAND, Blocks.GRAVEL, Blocks.AIR }) {
                for (int blockZ = -2; blockZ <= 2; blockZ++) {
                    for (int blockX = -2; blockX <= 2; blockX++) {
                        for (int blockY = 200; blockY <= 203; blockY++) {
                            ProbeWorld.clear(world, position.offset(blockX, blockY - 200, blockZ));
                        }
                    }
                }

                BlockState source = GrassSlabsApi.grassGenerationAllowed() ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();

                world.setBlock(position, source, 2);
                world.setBlock(position.north().above(), Blocks.STONE.defaultBlockState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);
                require(!world.isEmptyBlock(position.above()), "regression starts with an actual generated piece");
                world.setBlock(position, replacement.defaultBlockState(), 2);
                ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
                require(replacement == Blocks.SAND
                        ? world.getBlockState(position.above())
                                .getBlock() == zone.moddev.mc.skysterrainsmoother.content.SandContent.slab
                        : world.isEmptyBlock(position.above()),
                        "decoration retargets sand and removes unsupported gravel/air caps");
                require(world.getBlockState(position).getBlock() == replacement, "decorator's terrain is not reverted");
                checks += 3;
            }
        }
        for (int level = 1; level <= 3; level++) {
            for (boolean replacePiece : new boolean[] { false, true }) {
                for (int blockZ = -2; blockZ <= 2; blockZ++) {
                    for (int blockX = -2; blockX <= 2; blockX++) {
                        for (int blockY = 200; blockY <= 203; blockY++) {
                            ProbeWorld.clear(world, position.offset(blockX, blockY - 200, blockZ));
                        }
                    }
                }

                world.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(position.north().above(), Blocks.STONE.defaultBlockState(), 2);
                SkysTerrainSmoother.engine.smooth(world, owner, level);

                BlockState piece = world.getBlockState(position.above());

                if (replacePiece) {
                    world.setBlock(position, Blocks.SAND.defaultBlockState(), 2);
                    world.setBlock(position.above(), Blocks.GLASS.defaultBlockState(), 2);
                    piece = Blocks.GLASS.defaultBlockState();
                }

                BlockPos post = new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4);

                ProbeWorld.repair(world, post);
                ProbeWorld.repair(world, new BlockPos(owner.getPos().x << 4, 0, owner.getPos().z << 4));
                require(world.getBlockState(position.above()).equals(piece),
                        "valid support and decorator-owned replacement remain intact; repeated Post is harmless");
                checks++;
            }
        }

        BlockPos late = new BlockPos(28008, 200, 28008);
        LevelChunk lateOwner = world.getChunkAt(late);

        for (int blockY = 200; blockY <= 203; blockY++) {
            ProbeWorld.clear(world, late.above(blockY - 200));
            ProbeWorld.clear(world, late.north().above(blockY - 200));
        }

        world.setBlock(late, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(late.north().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, lateOwner, 1);
        ProbeWorld.repair(world, new BlockPos(lateOwner.getPos().x << 4, 0, lateOwner.getPos().z << 4));
        require(!world.isEmptyBlock(late.above()), "own Post retains supported generated piece");
        world.setBlock(late, Blocks.SAND.defaultBlockState(), 2);
        ProbeWorld.repair(world, new BlockPos((lateOwner.getPos().x - 1) << 4, 0, lateOwner.getPos().z << 4));
        require(world.getBlockState(late.above()).getBlock() == zone.moddev.mc.skysterrainsmoother.content.SandContent.slab
                && world.getBlockState(late).getBlock() == Blocks.SAND,
                "later neighbouring decoration retargets only its affected generated cap");
        checks += 2;

        BlockPos beach = late.offset(32, 0, 32);
        LevelChunk beachOwner = world.getChunkAt(beach);

        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                for (int blockY = 200; blockY <= 203; blockY++) {
                    ProbeWorld.clear(world, beach.offset(blockX, blockY - 200, blockZ));
                }
            }
        }

        world.setBlock(beach, Blocks.GRAVEL.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, beachOwner, 1);
        require(world.isEmptyBlock(beach.above()), "unsupported first pass leaves no piece");
        world.setBlock(beach, Blocks.RED_SAND.defaultBlockState(), 2);
        world.setBlock(beach.north().above(), Blocks.RED_SAND.defaultBlockState(), 2);
        ProbeWorld.repair(world, new BlockPos(beachOwner.getPos().x << 4, 0, beachOwner.getPos().z << 4));
        require(world.getBlockState(beach.above())
                .equals(zone.moddev.mc.skysterrainsmoother.content.SandContent.legacyState(3)),
                "late beach gets a red sand cap without any initial placement");
        checks += 2;
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_DECORATION_REPAIR_PASS checks=" + checks);
        return checks;
    }

    private static int geometry(ServerLevel world) {
        BlockPos position = new BlockPos(1000, 200, 1000);
        LevelChunk owner = world.getChunkAt(position);

        // Reset previous controlled-probe roofs before testing the same fixture on reload.
        for (int blockZ = -2; blockZ <= 2; blockZ++) {
            for (int blockX = -2; blockX <= 2; blockX++) {
                for (int blockY = 203; blockY <= 245; blockY++) {
                    ProbeWorld.clear(world, new BlockPos(position.getX() + blockX, blockY, position.getZ() + blockZ));
                }
            }
        }

        MaterialCatalogue catalogue = MaterialCatalogue.instance();
        TerrainMaterial stone = catalogue.source(Blocks.STONE.defaultBlockState());
        int checks = 0;
        int[] offsetsX = { 0, 1, 0, -1 };
        int[] offsetsZ = { -1, 0, 1, 0 };
        int[] cx = { -1, 1, 1, -1 };
        int[] cz = { -1, -1, 1, 1 };

        for (int level = 1; level <= 3; level++) {
            for (int cardinal = 0; cardinal < 16; cardinal++) {
                for (int diagonal = 0; diagonal < 16; diagonal++) {
                    for (int blockZ = -2; blockZ <= 2; blockZ++) {
                        for (int blockX = -2; blockX <= 2; blockX++) {
                            world.setBlock(position.offset(blockX, 0, blockZ), Blocks.STONE.defaultBlockState(), 2);
                            ProbeWorld.clear(world, position.offset(blockX, 1, blockZ));
                            ProbeWorld.clear(world, position.offset(blockX, 2, blockZ));
                        }
                    }
                    for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                        if ((cardinal & (1 << directionIndex)) != 0) {
                            world.setBlock(position.offset(offsetsX[directionIndex], 1, offsetsZ[directionIndex]),
                                    Blocks.STONE.defaultBlockState(), 2);
                        }
                        if ((diagonal & (1 << directionIndex)) != 0) {
                            world.setBlock(position.offset(cx[directionIndex], 1, cz[directionIndex]),
                                    Blocks.STONE.defaultBlockState(), 2);
                        }
                    }

                    BlockState expected = catalogue.result(stone, Footprint.mask(cardinal, diagonal, level), level);

                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    require(world.getBlockState(position.above())
                            .equals(expected == null ? Blocks.AIR.defaultBlockState() : expected),
                            "snapshot footprint " + level + "/" + cardinal + "/" + diagonal);
                    checks++;
                }
            }
        }

        world.setBlock(position, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        ProbeWorld.clear(world, position.above());
        world.setBlock(position.east().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 1);

        if (GrassSlabsApi.grassGenerationAllowed()) {
            require(world.getBlockState(position).equals(Blocks.DIRT.defaultBlockState()),
                    "support dirtified after grass placement");
        } else {
            require(world.getBlockState(position).getBlock() == Blocks.GRASS_BLOCK && world.isEmptyBlock(position.above()),
                    "grass switch authoritative");
        }

        world.setBlock(position.above(), Blocks.GLASS.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 3);
        require(world.getBlockState(position.above()).getBlock() == Blocks.GLASS, "occupied target protected");

        BlockPos veto = position.above(40);

        world.setBlock(veto, Blocks.STONE.defaultBlockState(), 2);
        ProbeWorld.clear(world, veto.above());
        world.setBlock(veto.east().above(), Blocks.STONE.defaultBlockState(), 2);
        SkysTerrainSmoother.engine.smooth(world, owner, 3);
        require(world.isEmptyBlock(veto.above()), "protected area veto");

        long start = System.nanoTime();

        for (int entryIndex = 0; entryIndex < 256; entryIndex++) {
            SkysTerrainSmoother.engine.smooth(world, owner, 3);
        }

        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_BENCHMARK passes=256 mean_ns=" + (System.nanoTime() - start) / 256);
        return checks + 3;
    }

    private static int persistence(ServerLevel world) {
        int index = 0;

        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.CORNER }) {
            for (int direction = 0; direction < shape.states; direction++) {
                for (String material : new String[] { "minecraft:grass", "minecraft:dirt" }) {
                    BlockPos pos = new BlockPos(1500 + index, 210, 1500);
                    BlockState expected = Pieces.state(material, shape, direction);

                    index++;

                    if (phase().equals("reload")) {
                        require(world.getBlockState(pos).equals(expected),
                                "saved fine piece retains identity and orientation");
                    } else {
                        world.setBlock(pos, expected, 2);
                    }
                }
            }
        }

        BlockPos saved = new BlockPos(1600, 210, 1600);

        world.getChunk(saved);
        world.setBlock(saved, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(saved.east().above(), Blocks.STONE.defaultBlockState(), 2);
        ProbeWorld.clear(world, saved.above());

        if (phase().equals("reload")) {
            long skipped = SkysTerrainSmoother.engine.skippedExisting;

            ProbeWorld.generate(world, new BlockPos(saved.getX() & ~15, 0, saved.getZ() & ~15));
            require(world.isEmptyBlock(saved.above()), "saved unpopulated chunks are never smoothed");

            if (SkysTerrainSmoother.enabled) {
                require(SkysTerrainSmoother.engine.skippedExisting == skipped + 1,
                        "existing entry skips before terrain access");
            }

            world.setBlock(saved, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            world.setBlock(saved.east().above(), Blocks.TERRACOTTA.defaultBlockState(), 2);
            ProbeWorld.repair(world, new BlockPos(saved.getX() & ~15, 0, saved.getZ() & ~15));
            require(world.isEmptyBlock(saved.above()) && world.getBlockState(saved).getBlock() == Blocks.GRASS_BLOCK,
                    "late oasis pass never retrogenerates an indexed saved chunk");
        }

        return index;
    }

    private static int grass(ServerLevel world) {
        int checks = 0;
        BlockPos position = new BlockPos(1020, 210, 1020);

        world.getChunk(position);
        world.getChunk(position.offset(-8, 0, -8));
        world.getChunk(position.offset(8, 0, 8));

        for (Shape shape : new Shape[] { Shape.HORIZONTAL_STEP, Shape.VERTICAL_STEP, Shape.CORNER, Shape.STAIRS }) {
            for (int direction = 0; direction < shape.states; direction++) {
                BlockState dirt = Pieces.state("minecraft:dirt", shape, direction);
                BlockState grass = Pieces.state("minecraft:grass", shape, direction);

                require(GrassSlabsApi.grassFor(dirt).equals(grass) && GrassSlabsApi.dirtFor(grass).equals(dirt),
                        "shared orientation mapping");
                ProbeWorld.clear(world, position.above());
                world.setBlock(position, dirt, 2);
                ProbeWorld.light(world, LightLayer.SKY, position.above(), 15);
                require(GrassSpread.growTarget(world, position) && world.getBlockState(position).equals(grass),
                        "cross-mod grass growth preserves orientation");

                PieceBlock piece = (PieceBlock) grass.getBlock();
                boolean completeTop = Geometry.completeTop(piece.mask(grass));

                require(piece.isValidBonemealTarget(world, position, grass) == completeTop
                        && piece.isBonemealSuccess(world, net.minecraft.util.RandomSource.create(1), position, grass) == completeTop,
                        "bonemeal requires a complete upper supporting face");
                require(!piece.isValidBonemealTarget(world, position, dirt), "dirt forms do not accept grass bonemeal");
                world.setBlock(position.north().below(), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(position.north(), Blocks.SNOW.defaultBlockState(), 2);
                require(world.getBlockState(position.north()).getBlock() == Blocks.SNOW,
                        "Snow fixture has a supporting block");
                require(piece.getActualState(grass, world, position).getValue(net.minecraft.world.level.block.GrassBlock.SNOWY),
                        "fine grass snow visual");
                ProbeWorld.clear(world, position.north());
                require(!piece.getActualState(grass, world, position).getValue(net.minecraft.world.level.block.GrassBlock.SNOWY),
                        "fine grass snow clears");
                world.setBlock(position.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                piece.neighborChanged(grass, world, position, Blocks.GRASS_BLOCK, position.below(), false);
                int cells = piece.mask(grass);
                boolean coversSupport = ((cells | cells >> 4) & 15) == 15;
                require(world.getBlockState(position.below()).getBlock() == (coversSupport ? Blocks.DIRT : Blocks.GRASS_BLOCK),
                        "only full footprints dirtify the grass support");
                world.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), 2);
                require(GrassSpread.growTarget(world, position.below()) == !coversSupport,
                        "visible soil beneath finer pieces can regain grass");
                world.setBlock(position, dirt, 2);
                world.setBlock(position.above(), ModBlocks.GRASS_SLAB.get().defaultBlockState(), 2);
                require(!GrassSpread.growTarget(world, position), "covered dirt piece rejected");
                world.setBlock(position.above(), Pieces.state("minecraft:grass", Shape.HORIZONTAL_STEP, 4), 2);
                require(!GrassSpread.growTarget(world, position), "covered dirt pieces still reject partial grass cover");
                world.setBlock(position, grass, 2);
                world.setBlock(position.above(), Blocks.STONE.defaultBlockState(), 2);
                ProbeWorld.light(world, LightLayer.SKY, position.above(), 0);
                ProbeWorld.light(world, LightLayer.BLOCK, position.above(), 0);
                grass.tick(world, position, net.minecraft.util.RandomSource.create(1));
                require(world.getBlockState(position).equals(dirt), "covered decay keeps orientation");
                checks += 10;
            }
        }

        return checks;
    }

    private void beginTrackedGeneration(ServerLevel world) {
        trackedPlayer = ProbeItems.player(world,
                new GameProfile(UUID.fromString("4f5b04d6-9050-4e39-a06c-7793a3ffbe18"), "TerrainProbe"));
        // Discard network packets while retaining the native player tracking and chunk tickets.
        trackedPlayer.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(world.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), trackedPlayer,
                net.minecraft.server.network.CommonListenerCookie.createInitial(trackedPlayer.getGameProfile(), false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }
        };
        world.getServer().getPlayerList().setViewDistance(6);
        generationCenterX = Integer.getInteger("skysterrainsmoother.probeCenterX", 400)
                + (phase().equals("reload") ? 200 : 0);
        generationCenterZ = Integer.getInteger("skysterrainsmoother.probeCenterZ", 400);
        generationBefore = SkysTerrainSmoother.engine.passes;
        trackingStarted = System.nanoTime();
        trackedPlayer.setPos(generationCenterX * 16 + 8, 90, generationCenterZ * 16 + 8);
        trackedPlayer.revive();
        world.addNewPlayer(trackedPlayer);
    }

    private boolean trackingReady(ServerLevel world) {
        if (allTrackedChunksLoaded(world, generationCenterX, generationCenterZ)) {
            // Give the native world END tick one turn to process the final load notices.
            boolean ready = trackedAreaWasReady;
            trackedAreaWasReady = true;
            return ready;
        }
        trackedAreaWasReady = false;
        require(System.nanoTime() - trackingStarted < java.util.concurrent.TimeUnit.SECONDS.toNanos(60),
                "Normal player tracking completes the full 9x9 area; loaded="
                        + world.getChunkSource().getLoadedChunksCount() + "; generation passes="
                        + (SkysTerrainSmoother.engine.passes - generationBefore));
        return false;
    }

    private int finishTrackedGeneration(ServerLevel world) {
        int centerX = generationCenterX;
        int centerZ = generationCenterZ;
        int found = 0;
        int caves = 0;
        int mineralogy = 0;
        int lushClay = 0;
        int lushGrass = 0;
        int grassPieces = 0;
        int slabs = 0;
        int steps = 0;
        int corners = 0;
        try {
                for (int blockZ = centerZ - 4; blockZ <= centerZ + 4; blockZ++) {
                    for (int blockX = centerX - 4; blockX <= centerX + 4; blockX++) {
                        LevelChunk chunk = world.getChunkSource().getChunkNow(blockX, blockZ);

                        if (chunk == null) {
                            continue;
                        }
                        for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                            LevelChunkSection section = chunk.getSection(sectionIndex);
                            int sectionBottom = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                            if (section != null) {
                                for (int blockY = 0; blockY < 16; blockY++) {
                                    for (int localZ = 0; localZ < 16; localZ++) {
                                        for (int localX = 0; localX < 16; localX++) {
                                            TerrainMaterial terrainMaterial = MaterialCatalogue.instance()
                                                    .piece(section.getBlockState(localX, blockY, localZ));

                                            if (terrainMaterial != null) {
                                                BlockPos piece = new BlockPos((blockX << 4) + localX,
                                                        sectionBottom + blockY, (blockZ << 4) + localZ);
                                                BlockState support = chunk.getBlockState(piece.below());

                                                require(MaterialCatalogue.instance().source(support) == terrainMaterial
                                                        || support.equals(terrainMaterial.supportAfterPlacement)
                                                        || oreSupport(support),
                                                        "normal decoration leaves no piece over unrelated support: "
                                                                + piece + " / " + support);

                                                if (piece.getY() < chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, localX, localZ)
                                                        && !terrainMaterial.grass) {
                                                    caves++;

                                                    if (terrainMaterial.mineralogy) {
                                                        mineralogy++;
                                                    }
                                                }
                                                if (ResourceLocation.parse("biomesoplenty:lush_desert")
                                                        .equals(world.getBiome(piece).unwrapKey().orElseThrow().location())) {
                                                    if (terrainMaterial.id
                                                            .equals(ResourceLocation.parse("minecraft:hardened_clay"))) {
                                                        lushClay++;
                                                    }
                                                    if (terrainMaterial.id
                                                            .equals(ResourceLocation.parse("minecraft:grass"))) {
                                                        lushGrass++;
                                                    }
                                                }

                                                if (terrainMaterial.grass) grassPieces++;
                                                net.minecraft.world.phys.AABB shape = chunk.getBlockState(piece)
                                                        .getShape(world, piece).bounds();
                                                require(shape.minY == 0 && shape.maxY == .5,
                                                        "Generated transitions are bottom pieces");
                                                double volume = (shape.maxX - shape.minX) * (shape.maxY - shape.minY)
                                                        * (shape.maxZ - shape.minZ);
                                                if (volume == .5) slabs++;
                                                else if (volume == .25) steps++;
                                                else if (volume == .125) corners++;
                                                else throw new AssertionError("Unexpected generated geometry: " + shape);
                                                found++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        if (SkysTerrainSmoother.level == 1) {
            require(steps == 0 && corners == 0, "Level 1 generates slabs only");
        } else if (SkysTerrainSmoother.level == 2) {
            require(corners == 0, "Level 2 does not generate corners");
        }
        if (!GrassSlabsApi.grassGenerationAllowed()) {
            require(grassPieces == 0, "Grass settings remain authoritative in normal generation");
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_GENERATED_SHAPES_PASS level=" + SkysTerrainSmoother.level
                + " slabs=" + slabs + " steps=" + steps + " corners=" + corners + " grass=" + grassPieces);
        if (SkysTerrainSmoother.enabled) {
            require(SkysTerrainSmoother.engine.passes > generationBefore && found > 0,
                    "normal player-tracked generation creates pieces");
        } else {
            require(SkysTerrainSmoother.engine.passes == generationBefore && found == 0,
                    "disabled master has no duplicate grass pass");
        }
        if (SkysTerrainSmoother.enabled) {
            require(caves > 0, "normal player-tracked generation also smooths real cave floors");
        }
        if (Boolean.getBoolean("skysterrainsmoother.verifyLushDesert") && phase().equals("fresh")) {
            require(lushClay > 0, "player-tracked Lush Desert has hardened-clay transitions");
            require(lushGrass > 0, "player-tracked Lush Desert has grass oasis transitions");
            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_LUSH_DESERT_GENERATION_PASS clay=" + lushClay + " grass=" + lushGrass);
        }

        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_GENERATED_CAVES_PASS caves=" + caves + " mineralogy=" + mineralogy);
        return found;
        } finally {
            world.removePlayerImmediately(trackedPlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            trackedPlayer = null;
        }
    }

    private static boolean allTrackedChunksLoaded(ServerLevel world, int centerX, int centerZ) {
        for (int chunkZ = centerZ - 4; chunkZ <= centerZ + 4; chunkZ++) {
            for (int chunkX = centerX - 4; chunkX <= centerX + 4; chunkX++) {
                if (world.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int generationOrder(ServerLevel world) throws Exception {
        for (int level = 1; level <= 3; level++) {
            byte[][] hashes = new byte[2][];

            for (int order = 0; order < 2; order++) {
                int origin = order == 0 ? 128 : 256;
                List<LevelChunk> chunks = new ArrayList<>();

                for (int blockZ = 0; blockZ < 9; blockZ++) {
                    for (int blockX = 0; blockX < 9; blockX++) {
                        chunks.add(world.getChunk(origin + blockX, 128 + blockZ));
                    }
                }
                for (int blockZ = 0; blockZ < 144; blockZ++) {
                    for (int blockX = 0; blockX < 144; blockX++) {
                        int height = 200 + Math.floorMod(blockX * 31 + blockZ * 17 + (blockX >> 3) * (blockZ >> 3), 3);
                        LevelChunk chunk = chunks.get((blockX >> 4) + (blockZ >> 4) * 9);

                        // Bulk fixture construction avoids relighting 20,736 artificially raised columns one by one.
                        // The actual smoothing pass still uses real chunk writes.
                        for (int blockY = 170; blockY < 205; blockY++) {
                            LevelChunkSection section = chunk.getSections()[chunk.getSectionIndex(blockY)];

                            if (section == null) {
                                chunk.getSections()[chunk.getSectionIndex(blockY)] = section = ProbeWorld.emptySection(world);
                            }

                            section.setBlockState(blockX & 15, blockY & 15, blockZ & 15, Blocks.AIR.defaultBlockState());
                        }
                        for (int floor : new int[] { 170, 188, 200 }) {
                            int floorY = floor + height - 200;

                            chunk.getSections()[chunk.getSectionIndex(floorY)].setBlockState(blockX & 15, floorY & 15, blockZ & 15,
                                    Blocks.STONE.defaultBlockState());
                        }
                        for (int roof : new int[] { 180, 198 }) {
                            chunk.getSections()[chunk.getSectionIndex(roof)].setBlockState(blockX & 15, roof & 15, blockZ & 15,
                                    Blocks.BRICKS.defaultBlockState());
                        }


                    }
                }
                for (LevelChunk chunk : chunks) {
                    net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                            java.util.EnumSet.allOf(net.minecraft.world.level.levelgen.Heightmap.Types.class));
                }
                if (order == 1) {
                    Collections.reverse(chunks);
                }
                for (LevelChunk chunk : chunks) {
                    SkysTerrainSmoother.engine.smooth(world, chunk, level);
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                for (int blockZ = 0; blockZ < 144; blockZ++) {
                    for (int blockX = 0; blockX < 144; blockX++) {
                        for (int blockY = 170; blockY < 205; blockY++) {
                            BlockState state = world
                                    .getBlockState(new BlockPos((origin << 4) + blockX, blockY, (128 << 4) + blockZ));

                            digest.update((net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()) + "/"
                                    + state.toString() + "\n").getBytes("UTF-8"));
                        }
                    }
                }

                hashes[order] = digest.digest();
            }

            require(Arrays.equals(hashes[0], hashes[1]), "9x9 generation order differs at level " + level);

            StringBuilder hexDigest = new StringBuilder();

            for (byte value : hashes[0]) {
                hexDigest.append(String.format("%02X", value & 255));
            }

            zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info(
                    "TERRAIN_SMOOTHER_GENERATION_ORDER_PASS level=" + level + " chunks=81 sha256=" + hexDigest);
        }

        return 486;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean oreSupport(BlockState state) {
        return ProbeWorld.oreSupport(state);
    }
}
