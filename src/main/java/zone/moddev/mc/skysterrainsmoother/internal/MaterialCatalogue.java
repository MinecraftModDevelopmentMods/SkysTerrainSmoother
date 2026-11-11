package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.biome.Biome;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Baked lookup tables. No registry scans or reflective lookups in the column pass. */
public final class MaterialCatalogue {
    private static final MaterialCatalogue INSTANCE = new MaterialCatalogue();

    public static MaterialCatalogue instance() {
        return INSTANCE;
    }

    private volatile Map<ResourceLocation, TerrainMaterial> materials = Collections.emptyMap();
    private volatile Map<BlockState, TerrainMaterial> sources = Collections.emptyMap();
    private volatile Map<BlockState, TerrainMaterial> pieces = Collections.emptyMap();
    private volatile List<TerrainSmoothingApi.BiomeRule> biomeRules = Collections.emptyList();
    private volatile List<TerrainSmoothingApi.AreaProtection> protections = Collections.emptyList();
    private volatile boolean frozen;
    private volatile boolean registrationOpen;

    public synchronized void register(TerrainMaterial material) {
        requireOpen();
        validate(material);

        if (materials.containsKey(material.id)) {
            throw new IllegalArgumentException("Duplicate material " + material.id);
        }
        for (BlockState source : material.sources) {
            if (sources.containsKey(canonical(source))) {
                throw new IllegalArgumentException("Conflicting terrain source " + source);
            }
        }

        Map<ResourceLocation, TerrainMaterial> candidate = new LinkedHashMap<>(materials);

        candidate.put(material.id, material);
        commit(candidate);
    }

    public synchronized void addShapes(ResourceLocation materialId, BlockState[] steps, BlockState[] corners) {
        requireOpen();

        TerrainMaterial current = materials.get(materialId);

        if (current == null) {
            throw new IllegalArgumentException("Register the base material first: " + materialId);
        }

        TerrainMaterial updated = current.withAdditionalShapes(steps, corners);

        validate(updated);

        Map<ResourceLocation, TerrainMaterial> candidate = new LinkedHashMap<>(materials);

        candidate.put(materialId, updated);
        commit(candidate);
    }

    public synchronized void addBiomeRule(TerrainSmoothingApi.BiomeRule rule) {
        requireOpen();
        List<TerrainSmoothingApi.BiomeRule> updated = new ArrayList<>(biomeRules);
        updated.add(Objects.requireNonNull(rule));
        biomeRules = Collections.unmodifiableList(updated);
    }

    public synchronized void addAreaProtection(TerrainSmoothingApi.AreaProtection rule) {
        requireOpen();
        List<TerrainSmoothingApi.AreaProtection> updated = new ArrayList<>(protections);
        updated.add(Objects.requireNonNull(rule));
        protections = Collections.unmodifiableList(updated);
    }

    public synchronized void freeze() {
        requireOpen();
        frozen = true;
    }

    public synchronized void openRegistration() {
        requireOpen();
        registrationOpen = true;
    }

    public boolean registrationOpen() {
        return registrationOpen && !frozen;
    }

    public boolean frozen() {
        return frozen;
    }

    public int size() {
        return materials.size();
    }

    public TerrainMaterial material(ResourceLocation materialId) {
        return materials.get(materialId);
    }

    public Collection<TerrainMaterial> materials() {
        return Collections.unmodifiableCollection(materials.values());
    }

    public TerrainMaterial source(BlockState state) {
        return sources.get(canonical(state));
    }

    public TerrainMaterial piece(BlockState state) {
        return pieces.get(canonical(state));
    }

    public boolean allows(TerrainMaterial material, Biome biome, int dimension, BlockPos target) {
        for (TerrainSmoothingApi.BiomeRule rule : biomeRules) {
            if (!rule.allows(material.id, biome)) {
                return false;
            }
        }
        for (TerrainSmoothingApi.AreaProtection rule : protections) {
            if (rule.protects(dimension, target, biome)) {
                return false;
            }
        }

        return true;
    }

    /** Use the requested finer shape when available, otherwise fall back to this material's slab. */
    public BlockState result(TerrainMaterial material, int mask, int level) {
        int shape = Footprint.shape(mask, level);
        int direction = Footprint.direction(mask, shape);

        if (shape == 0) {
            return null;
        }

        BlockState fine = shape == 2 ? material.step(direction) : shape == 3 ? material.corner(direction) : null;

        return fine == null ? material.slab : fine;
    }

    private void requireOpen() {
        if (frozen) {
            throw new IllegalStateException("Terrain catalogue is frozen");
        }
    }

    /** Build replacement indexes first, leaving the active catalogue intact if validation fails. */
    private void commit(Map<ResourceLocation, TerrainMaterial> candidate) {
        Map<BlockState, TerrainMaterial> newSources = new HashMap<>();
        Map<BlockState, TerrainMaterial> newPieces = new HashMap<>();

        for (TerrainMaterial material : candidate.values()) {
            for (BlockState source : material.sources) {
                TerrainMaterial prior = newSources.put(canonical(source), material);

                if (prior != null) {
                    throw new IllegalArgumentException("Conflicting terrain source " + source);
                }
            }

            indexPiece(newPieces, material.slab, material);

            for (int direction = 0; direction < 4; ++direction) {
                indexPiece(newPieces, material.step(direction), material);
                indexPiece(newPieces, material.corner(direction), material);
            }
        }

        // Setup callbacks may run in parallel. Publish validated snapshots so
        // generation never needs to lock a shared map for each column read.
        materials = Collections.unmodifiableMap(new LinkedHashMap<>(candidate));
        sources = Collections.unmodifiableMap(newSources);
        pieces = Collections.unmodifiableMap(newPieces);
    }

    private static void indexPiece(Map<BlockState, TerrainMaterial> outputs, BlockState state,
            TerrainMaterial material) {
        if (state == null) {
            return;
        }

        TerrainMaterial prior = outputs.put(canonical(state), material);

        if (prior != null && prior != material) {
            throw new IllegalArgumentException("Conflicting terrain output " + state);
        }
    }

    private static void validate(TerrainMaterial material) {
        for (BlockState source : material.sources) {
            if (!fullCollision(source) || source.hasBlockEntity() || !source.getFluidState().isEmpty()) {
                throw new IllegalArgumentException("Terrain sources must be full blocks without tile entities");
            }
        }
        if (material.supportAfterPlacement != null && (!fullCollision(material.supportAfterPlacement)
                || material.supportAfterPlacement.hasBlockEntity()
                || !material.supportAfterPlacement.getFluidState().isEmpty())) {
            throw new IllegalArgumentException("Support conversion requires a full non-tile state");
        }

        validatePiece(material.slab, 15);

        for (int direction = 0; direction < 4; ++direction) {
            if (material.step(direction) != null) {
                validatePiece(material.step(direction), Footprint.EDGES[direction]);
            }
            if (material.corner(direction) != null) {
                validatePiece(material.corner(direction), 1 << direction);
            }
        }
    }

    private static void validatePiece(BlockState state, int footprint) {
        if (state.hasBlockEntity() || fullCollision(state) || !state.getFluidState().isEmpty()) {
            throw new IllegalArgumentException("A terrain piece cannot be full or tile-backed");
        }

        VoxelShape shape = TerrainGeometry.shape(state);
        if (shape.isEmpty()) {
            throw new IllegalArgumentException("A terrain piece needs solid collision geometry");
        }
        AABB boundingBox = shape.bounds();
        double minimumX = 1;
        double minimumZ = 1;
        double maximumX = 0;
        double maximumZ = 0;

        for (int cell = 0; cell < 4; ++cell) {
            if ((footprint & 1 << cell) != 0) {
                double cellMinimumX = cell == 0 || cell == 3 ? 0 : .5;
                double cellMinimumZ = cell < 2 ? 0 : .5;

                minimumX = Math.min(minimumX, cellMinimumX);
                minimumZ = Math.min(minimumZ, cellMinimumZ);
                maximumX = Math.max(maximumX, cellMinimumX + .5);
                maximumZ = Math.max(maximumZ, cellMinimumZ + .5);
            }
        }
        if (boundingBox == null || boundingBox.minX != minimumX || boundingBox.maxX != maximumX
                || boundingBox.minZ != minimumZ || boundingBox.maxZ != maximumZ || boundingBox.minY != 0
                || boundingBox.maxY != .5) {
            throw new IllegalArgumentException("Incorrect bottom-piece geometry: " + state);
        }
        VoxelShape expected = Shapes.create(new AABB(minimumX, 0, minimumZ, maximumX, .5, maximumZ));
        if (Shapes.joinIsNotEmpty(shape, expected, BooleanOp.NOT_SAME)) {
            throw new IllegalArgumentException("A terrain piece must fill its declared footprint: " + state);
        }
    }

    private static boolean fullCollision(BlockState state) {
        return TerrainGeometry.full(state);
    }

    private static BlockState canonical(BlockState state) {
        return state.hasProperty(SnowyDirtBlock.SNOWY) ? state.setValue(SnowyDirtBlock.SNOWY, false) : state;
    }
}
