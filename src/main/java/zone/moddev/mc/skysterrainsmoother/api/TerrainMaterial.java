package zone.moddev.mc.skysterrainsmoother.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Immutable exact-state mapping. Direction order is north, east, south, west. */
public final class TerrainMaterial {
    public final ResourceLocation id;
    public final Set<BlockState> sources;
    public final boolean grass;
    public final boolean mineralogy;
    public final BlockState slab, supportAfterPlacement;
    private final BlockState[] steps, corners;

    public TerrainMaterial(ResourceLocation materialId, Set<BlockState> sources, BlockState slab, BlockState[] steps,
            BlockState[] corners, boolean grass, BlockState supportAfterPlacement) {
        if (materialId == null || sources == null || sources.isEmpty() || slab == null) {
            throw new IllegalArgumentException("A material requires an identity, exact full states and a slab");
        }

        this.id = materialId;
        this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        this.slab = slab;
        this.steps = copyShapes(steps);
        this.corners = copyShapes(corners);
        this.grass = grass;
        this.supportAfterPlacement = supportAfterPlacement;

        boolean mineral = false;

        for (BlockState source : this.sources) {
            if (source == null) {
                throw new IllegalArgumentException("A source cannot be null");
            }

            ResourceLocation name = BuiltInRegistries.BLOCK.getKey(source.getBlock());

            if (name != null && name.getNamespace().equals("mineralogy")) {
                mineral = true;
            }
        }

        this.mineralogy = mineral;
    }

    /** Copy complete directional sets so callers cannot alter a registered material afterwards. */
    private static BlockState[] copyShapes(BlockState[] shapes) {
        if (shapes == null) {
            return new BlockState[4];
        }
        if (shapes.length != 4) {
            throw new IllegalArgumentException("Provide all four directions");
        }

        int present = 0;

        for (BlockState state : shapes) {
            if (state != null) {
                ++present;
            }
        }
        if (present != 0 && present != 4) {
            throw new IllegalArgumentException("A shape needs every direction or none");
        }

        return shapes.clone();
    }

    public BlockState step(int direction) {
        return steps[direction];
    }

    public BlockState corner(int direction) {
        return corners[direction];
    }

    public TerrainMaterial withAdditionalShapes(BlockState[] addedSteps, BlockState[] addedCorners) {
        if (addedSteps != null && steps[0] != null || addedCorners != null && corners[0] != null) {
            throw new IllegalArgumentException("An add-on may supply missing shapes, not replace existing shapes");
        }

        return new TerrainMaterial(id, sources, slab, addedSteps == null ? steps : addedSteps,
                addedCorners == null ? corners : addedCorners, grass, supportAfterPlacement);
    }
}
