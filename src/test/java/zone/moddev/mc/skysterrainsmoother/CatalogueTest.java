package zone.moddev.mc.skysterrainsmoother;

import java.util.*;
import net.minecraft.init.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.*;
import zone.moddev.mc.skysterrainsmoother.api.*;
import zone.moddev.mc.skysterrainsmoother.internal.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogueTest {
    @BeforeAll
    static void boot() {
        Bootstrap.register();
    }

    private static IBlockState piece(int mask) {
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

        final AxisAlignedBB boundingBox = new AxisAlignedBB(x0, 0, z0, x1, .5, z1);

        return new Block(Material.ROCK) {
            @Override
            public AxisAlignedBB getBoundingBox(IBlockState blockState, IBlockAccess blockAccess, BlockPos position) {
                return boundingBox;
            }

            @Override
            public boolean isFullCube(IBlockState blockState) {
                return false;
            }
        }.getDefaultState();
    }

    private static TerrainMaterial material(String name, IBlockState full, IBlockState slab, IBlockState[] steps,
            IBlockState[] corners) {
        return new TerrainMaterial(new ResourceLocation("test", name), Collections.singleton(full), slab, steps,
                corners, false, null);
    }

    @Test
    void missingFormsFallbackAndAdditiveExtensionDoesNotReplaceSlab() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        IBlockState slab = piece(15);
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.getDefaultState(), slab, null, null);

        catalogue.register(terrainMaterial);

        for (int level = 1; level <= 3; level++) {
            for (int mask = 1; mask < 16; mask++) {
                assertSame(slab, catalogue.result(terrainMaterial, mask, level));
            }
        }

        IBlockState[] steps = new IBlockState[4];
        IBlockState[] corners = new IBlockState[4];

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            steps[directionIndex] = piece(Footprint.EDGES[directionIndex]);
            corners[directionIndex] = piece(1 << directionIndex);
        }

        catalogue.addShapes(terrainMaterial.id, steps, corners);

        TerrainMaterial extended = catalogue.source(Blocks.STONE.getDefaultState());

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            assertSame(steps[directionIndex], catalogue.result(extended, Footprint.EDGES[directionIndex], 2));
            assertSame(corners[directionIndex], catalogue.result(extended, 1 << directionIndex, 3));
        }

        steps[0] = slab;
        assertNotSame(slab, extended.step(0));
        assertSame(slab, extended.slab);
        assertThrows(IllegalArgumentException.class, () -> catalogue.addShapes(terrainMaterial.id, new IBlockState[4], null));
        catalogue.freeze();
        assertThrows(IllegalStateException.class, () -> catalogue.addBiomeRule((id, b) -> true));
    }

    @Test
    void conflictsAreAtomicAndInvalidGeometryIsRejected() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        IBlockState slab = piece(15);
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.getDefaultState(), slab, null, null);

        catalogue.register(terrainMaterial);
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("dirt", Blocks.DIRT.getDefaultState(), slab, null, null)));
        assertEquals(1, catalogue.size());
        assertSame(terrainMaterial, catalogue.piece(slab));
        assertNull(catalogue.source(Blocks.DIRT.getDefaultState()));
        assertThrows(IllegalArgumentException.class, () -> catalogue
                .register(material("full", Blocks.DIRT.getDefaultState(), Blocks.STONE.getDefaultState(), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("tile", Blocks.CHEST.getDefaultState(), piece(15), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("wrong", Blocks.DIRT.getDefaultState(), piece(3), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> catalogue.register(material("dupe", Blocks.STONE.getDefaultState(), piece(15), null, null)));
    }

    @Test
    void biomeRestrictionsAndProtectedAreasAreOnlyVetoes() {
        MaterialCatalogue catalogue = new MaterialCatalogue();
        TerrainMaterial terrainMaterial = material("stone", Blocks.STONE.getDefaultState(), piece(15), null, null);

        catalogue.register(terrainMaterial);
        catalogue.addBiomeRule((id, biome) -> biome == Biomes.PLAINS);
        catalogue.addAreaProtection((dimension, pos, biome) -> pos.getX() < 0);
        assertTrue(catalogue.allows(terrainMaterial, Biomes.PLAINS, 0, BlockPos.ORIGIN));
        assertFalse(catalogue.allows(terrainMaterial, Biomes.DESERT, 0, BlockPos.ORIGIN));
        assertFalse(catalogue.allows(terrainMaterial, Biomes.PLAINS, 0, new BlockPos(-1, 1, 1)));
    }
}
