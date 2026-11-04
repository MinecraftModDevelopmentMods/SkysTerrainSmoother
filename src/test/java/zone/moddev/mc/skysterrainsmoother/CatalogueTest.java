package zone.moddev.mc.skysterrainsmoother;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.*;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import org.junit.jupiter.api.*;
import zone.moddev.mc.skysterrainsmoother.api.*;
import zone.moddev.mc.skysterrainsmoother.internal.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueTest {
    @BeforeAll
    static void boot() {
        MinecraftTestBootstrap.registerVanilla();
    }

    private static BlockState piece(int mask) {
        double x0 = 1;
        double z0 = 1;
        double x1 = 0;
        double z1 = 0;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            if ((mask & (1 << directionIndex)) != 0) {
                double vertexX = directionIndex == 0 || directionIndex == 3 ? 0 : .5;
                double vertexZ = directionIndex < 2 ? 0 : .5;

                x0 = Math.min(x0, vertexX);
                z0 = Math.min(z0, vertexZ);
                x1 = Math.max(x1, vertexX + .5);
                z1 = Math.max(z1, vertexZ + .5);
            }
        }

        final AABB boundingBox = new AABB(x0, 0, z0, x1, .5, z1);

        return new Block(Block.Properties.of(Material.STONE)) {
            @Override
            public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter world,
                    BlockPos position, net.minecraft.world.phys.shapes.CollisionContext context) {
                return net.minecraft.world.phys.shapes.Shapes.create(boundingBox);
            }

            @Override
            public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter world,
                    BlockPos position, net.minecraft.world.phys.shapes.CollisionContext context) {
                return getShape(state, world, position, context);
            }
        }.defaultBlockState();
    }

    private static TerrainMaterial material(String name, BlockState full, BlockState slab, BlockState[] steps,
            BlockState[] corners) {
        return new TerrainMaterial(new ResourceLocation("test", name), Collections.singleton(full), slab, steps,
                corners, false, null);
    }

    @Test
    void missingFormsFallbackAndAdditiveExtensionDoesNotReplaceSlab() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        BlockState slab = piece(15);
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.defaultBlockState(), slab, null, null);

        catalogue.register(terrainMaterial);

        for (int level = 1; level <= 3; level++) {
            for (int mask = 1; mask < 16; mask++) {
                assertSame(slab, catalogue.result(terrainMaterial, mask, level));
            }
        }

        BlockState[] steps = new BlockState[4];
        BlockState[] corners = new BlockState[4];

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            steps[directionIndex] = piece(Footprint.EDGES[directionIndex]);
            corners[directionIndex] = piece(1 << directionIndex);
        }

        catalogue.addShapes(terrainMaterial.id, steps, corners);

        TerrainMaterial extended = catalogue.source(Blocks.STONE.defaultBlockState());

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            assertSame(steps[directionIndex], catalogue.result(extended, Footprint.EDGES[directionIndex], 2));
            assertSame(corners[directionIndex], catalogue.result(extended, 1 << directionIndex, 3));
        }

        steps[0] = slab;
        assertNotSame(slab, extended.step(0));
        assertSame(slab, extended.slab);
        assertThrows(IllegalArgumentException.class, () -> catalogue.addShapes(terrainMaterial.id, new BlockState[4], null));
        catalogue.freeze();
        assertThrows(IllegalStateException.class, () -> catalogue.addBiomeRule((id, b) -> true));
    }

    @Test
    void conflictsAreAtomicAndInvalidGeometryIsRejected() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        BlockState slab = piece(15);
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.defaultBlockState(), slab, null, null);

        catalogue.register(terrainMaterial);
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("dirt", Blocks.DIRT.defaultBlockState(), slab, null, null)));
        assertEquals(1, catalogue.size());
        assertSame(terrainMaterial, catalogue.piece(slab));
        assertNull(catalogue.source(Blocks.DIRT.defaultBlockState()));
        assertThrows(IllegalArgumentException.class, () -> catalogue
                .register(material("full", Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("tile", Blocks.CHEST.defaultBlockState(), piece(15), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("wrong", Blocks.DIRT.defaultBlockState(), piece(3), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("dupe", Blocks.STONE.defaultBlockState(), piece(15), null, null)));
    }

    @Test
    void biomeRestrictionsAndProtectedAreasAreOnlyVetoes() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.defaultBlockState(), piece(15), null, null);
        var biomes = VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME);
        var plains = biomes.getOrThrow(Biomes.PLAINS).value();
        var desert = biomes.getOrThrow(Biomes.DESERT).value();

        catalogue.register(terrainMaterial);
        catalogue.addBiomeRule((id, biome) -> biome == plains);
        catalogue.addAreaProtection((dimension, pos, biome) -> pos.getX() < 0);
        assertTrue(catalogue.allows(terrainMaterial, plains, 0, BlockPos.ZERO));
        assertFalse(catalogue.allows(terrainMaterial, desert, 0, BlockPos.ZERO));
        assertFalse(catalogue.allows(terrainMaterial, plains, 0, new BlockPos(-1, 1, 1)));
    }
    @Test
    void wetOutputsAndHollowGeometryAreRejected() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock sand =
                new zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock(false);
        BlockState wet = sand.defaultBlockState().setValue(
                zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock.WATERLOGGED, true);
        // The native registry fills this cache for registered block states.
        wet.initCache();
        assertFalse(wet.getFluidState().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("wet", Blocks.STONE.defaultBlockState(), wet, null, null)));
        Block hollow = new Block(Block.Properties.of(Material.STONE)) {
            @Override
            public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter world,
                    BlockPos position, net.minecraft.world.phys.shapes.CollisionContext context) {
                return net.minecraft.world.phys.shapes.Shapes.join(
                        net.minecraft.world.phys.shapes.Shapes.create(new AABB(0, 0, 0, 1, .5, 1)),
                        net.minecraft.world.phys.shapes.Shapes.create(new AABB(.25, .125, .25, .75, .375, .75)),
                        net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("hollow", Blocks.DIRT.defaultBlockState(), hollow.defaultBlockState(), null, null)));
        assertEquals(0, catalogue.size());
    }

    @Test
    void snowyGrassKeepsItsLogicalMaterialButWetPiecesDoNotAliasDryOnes() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        BlockState dry = new zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock(false).defaultBlockState();
        TerrainMaterial grass = material("grass", Blocks.GRASS_BLOCK.defaultBlockState(), dry, null, null);
        catalogue.register(grass);
        assertSame(grass, catalogue.source(Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, true)));
        assertNull(catalogue.piece(dry.setValue(zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock.WATERLOGGED, true)));
    }

    @Test
    void parallelSetupContributionsRetainAllMappings() throws Exception {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        Block[] sources = { Blocks.STONE, Blocks.DIRT, Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
                Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.OBSIDIAN };
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
            for (int index = 0; index < sources.length; index++) {
                final int materialIndex = index;
                results.add(workers.submit(() -> catalogue.register(material("parallel_" + materialIndex,
                        sources[materialIndex].defaultBlockState(), piece(15), null, null))));
            }
            for (java.util.concurrent.Future<?> result : results) {
                result.get();
            }
        } finally {
            workers.shutdownNow();
        }
        catalogue.freeze();
        assertEquals(sources.length, catalogue.size());
        for (Block source : sources) {
            assertNotNull(catalogue.source(source.defaultBlockState()));
        }
        assertThrows(IllegalStateException.class,
                () -> catalogue.register(material("late", Blocks.COBBLESTONE.defaultBlockState(), piece(15), null, null)));
    }

}
