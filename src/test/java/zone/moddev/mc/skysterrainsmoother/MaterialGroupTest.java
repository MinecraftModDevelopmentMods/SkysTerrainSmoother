package zone.moddev.mc.skysterrainsmoother;

import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import static org.junit.jupiter.api.Assertions.*;

class MaterialGroupTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void originalConstructorClassifiesKnownSourcesRatherThanTheMaterialName() {
        for (Block source : new Block[] {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL}) {
            assertEquals(MaterialGroup.SOIL, mapping(source).group);
        }
        for (Block source : new Block[] {Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL}) {
            assertEquals(MaterialGroup.SAND_AND_GRAVEL, mapping(source).group);
        }
        for (Block source : new Block[] {Blocks.STONE, Blocks.SANDSTONE, Blocks.TERRACOTTA}) {
            assertEquals(MaterialGroup.STONE, mapping(source).group);
        }
        assertEquals(MaterialGroup.SNOW, mapping(Blocks.SNOW_BLOCK).group);
        assertEquals(MaterialGroup.SNOW, mapping(Blocks.POWDER_SNOW).group);
        assertEquals(MaterialGroup.ICE, mapping(Blocks.PACKED_ICE).group);
        assertEquals(MaterialGroup.OTHER, mapping(Blocks.OAK_PLANKS).group);
    }

    @Test
    void extensionsKeepTheirExplicitGroupAndCannotMutateTheMapping() {
        for (MaterialGroup group : MaterialGroup.values()) {
            var material = new TerrainMaterial(Identifier.parse("test:material"),
                    Set.of(Blocks.DIRT.defaultBlockState()), Blocks.STONE_SLAB.defaultBlockState(),
                    null, null, false, null, group);
            assertEquals(group, material.withAdditionalShapes(null, null).group);
        }
        assertThrows(NullPointerException.class, () -> new TerrainMaterial(Identifier.parse("test:material"),
                Set.of(Blocks.DIRT.defaultBlockState()), Blocks.STONE_SLAB.defaultBlockState(),
                null, null, false, null, null));
    }

    private static TerrainMaterial mapping(Block block) {
        return new TerrainMaterial(Identifier.parse("example:unrelated_name"),
                Set.of(block.defaultBlockState()), Blocks.STONE_SLAB.defaultBlockState(), null, null, false, null);
    }
}
