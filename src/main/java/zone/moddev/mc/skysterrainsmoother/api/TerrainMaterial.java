package zone.moddev.mc.skysterrainsmoother.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Immutable exact-state mapping. Direction order is north, east, south, west. */
public final class TerrainMaterial {
    public final Identifier id;
    public final Set<BlockState> sources;
    public final boolean grass;
    public final boolean mineralogy;
    public final MaterialGroup group;
    public final BlockState slab, supportAfterPlacement;
    private final BlockState[] steps, corners;

    public TerrainMaterial(Identifier materialId, Set<BlockState> sources, BlockState slab, BlockState[] steps,
            BlockState[] corners, boolean grass, BlockState supportAfterPlacement) {
        this(materialId, sources, slab, steps, corners, grass, supportAfterPlacement,
                legacyGroup(sources, grass));
    }

    public TerrainMaterial(Identifier materialId, Set<BlockState> sources, BlockState slab, BlockState[] steps,
            BlockState[] corners, boolean grass, BlockState supportAfterPlacement, MaterialGroup group) {
        if (materialId == null || sources == null || sources.isEmpty() || slab == null) {
            throw new IllegalArgumentException("A material requires an identity, exact full states and a slab");
        }

        this.id = materialId;
        this.group = java.util.Objects.requireNonNull(group, "A material needs a configuration group");
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

            Identifier name = ForgeRegistries.BLOCKS.getKey(source.getBlock());

            if (name != null && name.getNamespace().equals("mineralogy")) {
                mineral = true;
            }
        }

        this.mineralogy = mineral;
    }

    /** Keep the original constructor usable by older add-ons without guessing unknown modded materials. */
    private static MaterialGroup legacyGroup(Set<BlockState> sources, boolean grass) {
        if (grass) {
            return MaterialGroup.SOIL;
        }
        MaterialGroup result = null;
        if (sources != null) {
            for (BlockState state : sources) {
                if (state == null) {
                    continue;
                }
                Identifier block = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                MaterialGroup candidate = knownGroup(block);
                if (result != null && result != candidate) {
                    return MaterialGroup.OTHER;
                }
                result = candidate;
            }
        }
        return result == null ? MaterialGroup.OTHER : result;
    }

    private static MaterialGroup knownGroup(Identifier block) {
        if (block == null) {
            return MaterialGroup.OTHER;
        }
        if (block.getNamespace().equals("mineralogy")) {
            return MaterialGroup.STONE;
        }
        if (!block.getNamespace().equals("minecraft")) {
            return MaterialGroup.OTHER;
        }
        return switch (block.getPath()) {
            case "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "mud" -> MaterialGroup.SOIL;
            case "sand", "red_sand", "gravel" -> MaterialGroup.SAND_AND_GRAVEL;
            case "snow_block", "powder_snow" -> MaterialGroup.SNOW;
            case "ice", "packed_ice", "blue_ice", "frosted_ice" -> MaterialGroup.ICE;
            case "stone", "granite", "diorite", "andesite", "sandstone", "red_sandstone", "terracotta",
                    "obsidian", "deepslate", "tuff", "calcite", "basalt", "smooth_basalt", "blackstone",
                    "netherrack", "end_stone", "cobblestone", "mossy_cobblestone" -> MaterialGroup.STONE;
            default -> MaterialGroup.OTHER;
        };
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
                addedCorners == null ? corners : addedCorners, grass, supportAfterPlacement, group);
    }
}
