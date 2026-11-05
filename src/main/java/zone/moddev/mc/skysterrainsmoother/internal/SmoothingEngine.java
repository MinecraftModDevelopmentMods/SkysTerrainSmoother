package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Plans dry floor transitions before writing them, using only chunks already loaded. */
public final class SmoothingEngine {
    private static final int[] DX = { 0, 1, 0, -1 }, DZ = { -1, 0, 1, 0 }, CORNER_X = { -1, 1, 1, -1 },
            CORNER_Z = { -1, -1, 1, 1 };
    private final MaterialCatalogue catalogue;
    private volatile ExistingChunkIndex existing;
    private final Set<Long> processed = ConcurrentHashMap.newKeySet();
    private final Set<Long> populated = ConcurrentHashMap.newKeySet(), dirtyOwners = ConcurrentHashMap.newKeySet();
    private final Map<Long, List<FeatureDecorator>> pending = new ConcurrentHashMap<>();
    private final Map<BlockState, Boolean> oreSupports = new ConcurrentHashMap<>();
    private final Map<BlockState, Integer> pieceEdges = new ConcurrentHashMap<>();
    private final ThreadLocal<Decisions> decisions = ThreadLocal.withInitial(Decisions::new);
    public volatile long passes, placed, elapsedNanos, skippedExisting;

    public SmoothingEngine(MaterialCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void start(File worldDirectory) {
        try {
            existing = new ExistingChunkIndex(worldDirectory);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot safely index existing chunks; terrain smoothing stopped", failure);
        }

        processed.clear();
        populated.clear();
        dirtyOwners.clear();
        pending.clear();
        oreSupports.clear();
        pieceEdges.clear();
        passes = placed = elapsedNanos = skippedExisting = 0;
        SkysTerrainSmoother.logger.info("Indexed {} saved Overworld chunks; no smoothing retrogen will run",
                existing.size());
    }

    public void stop() {
        existing = null;
        processed.clear();
        populated.clear();
        dirtyOwners.clear();
        pending.clear();
        oreSupports.clear();
        pieceEdges.clear();
        decisions.remove();
    }

    public int indexedChunks() {
        return existing == null ? 0 : existing.size();
    }

    /** Called by the first vegetation feature for the centre chunk of the decoration region. */
    public boolean generate(LevelAccessor world, int chunkX, int chunkZ) {
        ExistingChunkIndex index = existing;
        if (!isOverworld(world) || !SkysTerrainSmoother.enabled
                || index == null) {
            return false;
        }
        if (index.contains(chunkX, chunkZ)) {
            synchronized (this) {
                skippedExisting++;
            }
            return false;
        }
        ChunkAccess owner = loaded(world, chunkX, chunkZ);
        long key = ExistingChunkIndex.key(chunkX, chunkZ);
        if (owner == null || !processed.add(key)) {
            return false;
        }

        long started = System.nanoTime();
        int count = smooth(world, owner, SkysTerrainSmoother.level);
        synchronized (this) {
            placed += count;
            elapsedNanos += System.nanoTime() - started;
            passes++;
        }
        return count > 0;
    }

    @SubscribeEvent
    public void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level world)) {
            return;
        }
        if (world.isClientSide || !isOverworld(world)) {
            return;
        }
        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        populated.add(ExistingChunkIndex.key(chunkX, chunkZ));

        // Queue repairs on the server thread, after generation has handed over the full chunk.
        // Native decoration can cross any border, even though comparisons retain
        // the established east/south policy.
        for (int ownerZ = chunkZ - 1; ownerZ <= chunkZ + 1; ownerZ++) {
            for (int ownerX = chunkX - 1; ownerX <= chunkX + 1; ownerX++) {
                long key = ExistingChunkIndex.key(ownerX, ownerZ);
                if (pending.containsKey(key)) {
                    dirtyOwners.add(key);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void endWorldTick(LevelTickEvent.Post event) {
        Level world = event.getLevel();
        if (world.isClientSide
                || !isOverworld(world)) {
            return;
        }
        for (long key : new ArrayList<>(dirtyOwners)) {
            if (!dirtyOwners.remove(key)) {
                continue;
            }
            List<FeatureDecorator> placements = pending.get(key);
            if (placements == null) {
                continue;
            }
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            boolean validated;
            synchronized (placements) {
                validated = validate(world, chunkX, chunkZ, placements);
            }
            if (!validated) {
                dirtyOwners.add(key);
                continue;
            }
            if (neighbouringDecorationFinished(chunkX, chunkZ)) {
                pending.remove(key, placements);
            }
        }
    }

    @SubscribeEvent
    public void unload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level world)) {
            return;
        }
        if (world.isClientSide || !isOverworld(world)) {
            return;
        }
        long key = ExistingChunkIndex.key(event.getChunk().getPos().x, event.getChunk().getPos().z);
        dirtyOwners.remove(key);
        pending.remove(key);
    }

    private boolean neighbouringDecorationFinished(int chunkX, int chunkZ) {
        for (int neighbourZ = chunkZ - 1; neighbourZ <= chunkZ + 1; neighbourZ++) {
            for (int neighbourX = chunkX - 1; neighbourX <= chunkX + 1; neighbourX++) {
                if (!populated.contains(ExistingChunkIndex.key(neighbourX, neighbourZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validate(LevelAccessor world, int chunkX, int chunkZ, List<FeatureDecorator> placements) {
        ChunkAccess owner = loaded(world, chunkX, chunkZ);

        if (owner == null) {
            return false;
        }

        // Plan repairs from one snapshot. Replace only our unchanged caps; never restore a decorator's old terrain.
        Map<FeatureDecorator, BlockState> repairs = new LinkedHashMap<>();
        Map<FeatureDecorator, TerrainMaterial> materials = new HashMap<>();
        StructureProtection structures = structures(world, owner);
        if (!structures.canInspectSafely()) {
            return false;
        }

        for (FeatureDecorator placement : placements) {
            BlockState support = owner.getBlockState(placement.pos.below());
            TerrainMaterial material = placement.material;
            boolean blocked = placement.cave && !caveHeadroom(world, owner, placement.pos.below());
            boolean raisesLip = raisesLowerEdge(world, placement.pos.below(), placement.state);
            boolean unsupported = catalogue.source(support) != material && !support.equals(material.supportAfterPlacement)
                    && !oreSupport(support);

            // Keep ore veins hidden beneath existing caps, but never use ore as a new smoothing source.
            if ((blocked || raisesLip || unsupported) && sameState(owner.getBlockState(placement.pos), placement.state)) {
                boolean ourDirt = placement.dirtifiedSupport != null
                        && support.equals(placement.dirtifiedSupport)
                        && catalogue.source(placement.originalSupport) == material;
                TerrainMaterial replacement = ourDirt ? material : catalogue.source(support);
                BlockState result = null;

                if (!blocked && replacement != null && (!replacement.grass || GrassSlabsApi.grassGenerationAllowed())
                        && (!replacement.mineralogy || SkysTerrainSmoother.mineralogy)
                        && support.isCollisionShapeFullBlock(world, placement.pos.below())
                        && !support.hasBlockEntity()
                        && owner.getBlockEntity(placement.pos) == null
                        && !structures.contains(placement.pos)
                        && !structures.contains(placement.pos.below())
                        && catalogue.allows(replacement, world.getBiome(placement.pos).value(), 0, placement.pos)) {
                    int localBlockX = placement.pos.getX() & 15;
                    int localBlockZ = placement.pos.getZ() & 15;
                    int supportY = placement.pos.getY() - 1;
                    int cardinal = 0;
                    int diagonal = 0;

                    for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                        if (higher(world, owner, localBlockX + DX[directionIndex], localBlockZ + DZ[directionIndex],
                                supportY)) {
                            cardinal |= 1 << directionIndex;
                        }
                    }
                    if (placement.level == 3) {
                        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
                            if (higher(world, owner, localBlockX + CORNER_X[directionIndex],
                                    localBlockZ + CORNER_Z[directionIndex], supportY)) {
                                diagonal |= 1 << directionIndex;
                            }
                        }
                    }

                    result = transitionResult(world, placement.pos.below(), replacement,
                            Footprint.mask(cardinal, diagonal, placement.level), placement.level);
                }

                repairs.put(placement, result == null ? Blocks.AIR.defaultBlockState() : result);
                materials.put(placement, replacement);
            }
        }

        boolean changed = false;

        for (Map.Entry<FeatureDecorator, BlockState> repair : repairs.entrySet()) {
            FeatureDecorator placement = repair.getKey();

            if (!sameState(owner.getBlockState(placement.pos), placement.state)) {
                continue;
            }

            BlockState state = repair.getValue();
            BlockState supportBefore = owner.getBlockState(placement.pos.below());
            TerrainMaterial previousMaterial = placement.material;
            boolean ourDirt = placement.dirtifiedSupport != null
                    && supportBefore.equals(placement.dirtifiedSupport)
                    && catalogue.source(placement.originalSupport) == previousMaterial;

            changed |= setGenerationState(world, owner, placement.pos, state);
            placement.state = owner.getBlockState(placement.pos);
            placement.material = materials.get(placement);

            BlockState supportAfter = placement.material != null && state.getBlock() != Blocks.AIR
                    ? supportAfterPlacement(placement.material, state) : null;
            if (supportAfter != null) {
                if (!ourDirt || previousMaterial != placement.material) {
                    placement.originalSupport = supportBefore;
                }
                changed |= setGenerationState(world, owner, placement.pos.below(), supportAfter);
                placement.dirtifiedSupport = placement.material.grass
                        && !supportAfter.equals(placement.originalSupport) ? supportAfter : null;
            } else {
                // Shrinking our own slab exposes its old grass. Never undo a decorator's replacement terrain.
                if (ourDirt && previousMaterial == placement.material && state.getBlock() != Blocks.AIR) {
                    changed |= setGenerationState(world, owner, placement.pos.below(), placement.originalSupport);
                }
                placement.dirtifiedSupport = null;
            }
        }
        if (changed) {
            owner.setUnsaved(true);
        }

        placements.removeIf(placement -> placement.state.getBlock() == Blocks.AIR);
        // Biome decoration can introduce new grass oases as well as beaches. Re-snapshot registered dry floors only in
        // this tracked fresh owner; occupied targets and existing saved chunks remain untouched.
        int additional = smooth(world, owner, SkysTerrainSmoother.level, true);
        synchronized (this) {
            placed += additional;
        }
        return true;
    }

    /** Calculate all placements before writing. Normal generation calls this through the feature. */
    public int smooth(LevelAccessor world, ChunkAccess owner, int level) {
        return smooth(world, owner, level, false);
    }

    private int smooth(LevelAccessor world, ChunkAccess owner, int level, boolean afterDecoration) {
        Footprint.validateLevel(level);

        long key = ExistingChunkIndex.key(owner.getPos().x, owner.getPos().z);

        if (afterDecoration && !pending.containsKey(key)) {
            return 0;
        }

        Decisions buffer = decisions.get();

        buffer.clear();

        int startX = owner.getPos().x << 4;
        int startZ = owner.getPos().z << 4;

        StructureProtection structures = structures(world, owner);
        if (!structures.canInspectSafely()) {
            return 0;
        }
        boolean grassAllowed = GrassSlabsApi.grassGenerationAllowed();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localBlockZ = 0; localBlockZ < 16; ++localBlockZ) {
            for (int localBlockX = 0; localBlockX < 16; ++localBlockX) {
                int surfaceY = SmoothingFoliage.surfaceHeight(owner, localBlockX, localBlockZ, startX, startZ);

                // Visit upward-facing floors at every depth. Finish every decision before the first world write.
                if (surfaceY < owner.getMinBuildHeight()) {
                    continue;
                }

                BlockState previous = owner.getBlockState(
                        cursor.set(startX + localBlockX, Math.min(surfaceY, owner.getMaxBuildHeight() - 2) + 1, startZ + localBlockZ));

                for (int floorY = Math.min(surfaceY, owner.getMaxBuildHeight() - 2); floorY >= owner.getMinBuildHeight(); --floorY) {
                    BlockState lower = owner
                            .getBlockState(cursor.set(startX + localBlockX, floorY, startZ + localBlockZ));
                    BlockState above = previous;

                    previous = lower;

                    // Ignore thin natural cover when finding a floor, but never tunnel through occupied terrain.
                    if (!above.isAir() && !SmoothingFoliage.isCover(above)
                            || lower.isAir()) {
                        continue;
                    }

                    TerrainMaterial material = catalogue.source(lower);

                    if (material == null || material.grass && !grassAllowed
                            || material.mineralogy && !SkysTerrainSmoother.mineralogy) {
                        continue;
                    }

                    BlockPos support = new BlockPos(startX + localBlockX, floorY, startZ + localBlockZ);
                    BlockPos target = support.above();

                    boolean cave = floorY < surfaceY;

                    if (cave && !caveHeadroom(world, owner, support)
                            || !lower.isCollisionShapeFullBlock(world, support)
                            || !SmoothingFoliage.canReplace(world, owner, target)
                            || lower.hasBlockEntity()
                            || owner.getBlockEntity(target) != null
                            || structures.contains(target)
                            || structures.contains(support)
                            || !catalogue.allows(material, world.getBiome(target).value(), 0, target)) {
                        continue;
                    }

                    int cardinal = 0;
                    int diagonal = 0;

                    for (int direction = 0; direction < 4; ++direction) {
                        if (higher(world, owner, localBlockX + DX[direction], localBlockZ + DZ[direction], floorY)) {
                            cardinal |= 1 << direction;
                        }
                    }
                    if (level == 3) {
                        for (int direction = 0; direction < 4; ++direction) {
                            if (higher(world, owner, localBlockX + CORNER_X[direction],
                                    localBlockZ + CORNER_Z[direction], floorY)) {
                                diagonal |= 1 << direction;
                            }
                        }
                    }

                    BlockState result = transitionResult(world, support, material,
                            Footprint.mask(cardinal, diagonal, level), level);

                    if (result != null) {
                        buffer.add(localBlockX | localBlockZ << 4 | (floorY + 1) << 8, result, material, cave, above);
                    }
                }
            }
        }

        int count = 0;
        List<FeatureDecorator> placements = new ArrayList<>();

        for (int index = 0; index < buffer.size; ++index) {
            int position = buffer.positions[index];
            BlockPos target = new BlockPos(startX + (position & 15), position >> 8, startZ + ((position >> 4) & 15));
            BlockState above = owner.getBlockState(target);

            if (!above.equals(buffer.originalTargets[index]) || !SmoothingFoliage.canReplace(world, owner, target)) {
                continue;
            }
            BlockState originalSupport = owner.getBlockState(target.below());
            if (setGenerationState(world, owner, target, buffer.results[index])) {
                if (above.getBlock() instanceof DoublePlantBlock) {
                    setGenerationState(world, owner, target.above(), Blocks.AIR.defaultBlockState());
                }
                if (buffer.supports[index] != null) {
                    setGenerationState(world, owner, target.below(), buffer.supports[index]);
                }

                placements.add(new FeatureDecorator(target, owner.getBlockState(target), buffer.materials[index], level,
                        buffer.caves[index], originalSupport));
                ++count;
            }
        }
        if (count > 0) {
            owner.setUnsaved(true);
        }
        if (afterDecoration) {
            pending.get(key).addAll(placements);
        } else {
            // Decoration can create new floors. Track this owner until its bordering decorators finish.
            pending.put(key, Collections.synchronizedList(placements));
            dirtyOwners.add(key);
        }

        return count;
    }

    private static boolean setGenerationState(LevelAccessor world, ChunkAccess owner, BlockPos position, BlockState state) {
        if (position.getY() < owner.getMinBuildHeight() || position.getY() >= owner.getMaxBuildHeight()
                || position.getX() >> 4 != owner.getPos().x || position.getZ() >> 4 != owner.getPos().z) {
            throw new IllegalArgumentException("Terrain smoothing cannot write outside its owning chunk");
        }
        BlockState before = owner.getBlockState(position);
        if (before.equals(state)) {
            return false;
        }
        owner.setBlockState(position, state, false);
        BlockState actual = owner.getBlockState(position);
        if (world instanceof Level && owner.getStatus() == ChunkStatus.FULL && !before.equals(actual)) {
            // A late repair may follow the chunk's first packet. Notify tracked clients
            // without running neighbour placement logic outside this owner.
            ((Level) world).sendBlockUpdated(position, before, actual, 2);
        }
        return sameState(actual, state);
    }

    private static boolean sameState(BlockState first, BlockState second) {
        // Nearby snow can update the visual property between decoration callbacks.
        // Geometry, material and water state must still match our original cap.
        if (first.hasProperty(SnowyDirtBlock.SNOWY)) {
            first = first.setValue(SnowyDirtBlock.SNOWY, false);
        }
        if (second.hasProperty(SnowyDirtBlock.SNOWY)) {
            second = second.setValue(SnowyDirtBlock.SNOWY, false);
        }
        return first.equals(second);
    }

    private BlockState transitionResult(LevelAccessor world, BlockPos support, TerrainMaterial material, int mask, int level) {
        int lowerEdges = lowerEdges(world, support);
        int safeMask = Footprint.trimDownhillEdges(mask, lowerEdges);
        BlockState result = catalogue.result(material, safeMask, level);

        // Missing finer shapes fall back to slabs. Check the real result, not just the requested footprint.
        return result != null && (touchingEdges(result) & lowerEdges) == 0 ? result : null;
    }

    private static BlockState supportAfterPlacement(TerrainMaterial material, BlockState piece) {
        // A slab hides the whole surface. Finer grass pieces must leave the visible ground grassy.
        return material.grass && !GrassSlabsApi.coversSupport(piece, EmptyAccess.INSTANCE, BlockPos.ZERO)
                ? null : material.supportAfterPlacement;
    }

    private boolean raisesLowerEdge(LevelAccessor world, BlockPos support, BlockState result) {
        return (touchingEdges(result) & lowerEdges(world, support)) != 0;
    }

    private int touchingEdges(BlockState result) {
        Integer edges = pieceEdges.get(result);

        if (edges == null) {
            AABB bounds = result.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO).bounds();
            edges = (bounds.minZ == 0 ? 1 : 0) | (bounds.maxX == 1 ? 2 : 0)
                    | (bounds.maxZ == 1 ? 4 : 0) | (bounds.minX == 0 ? 8 : 0);
            pieceEdges.put(result, edges);
        }

        return edges;
    }

    /**
     * Two caps on consecutive one-block rises recreate a full-block step. Leave the downhill lip alone, but
     * allow the remaining uphill half or quarter where the chosen smoothing level has a matching piece.
     */
    private int lowerEdges(LevelAccessor world, BlockPos support) {
        int edges = 0;

        for (int direction = 0; direction < 4; direction++) {
            BlockPos beside = support.offset(DX[direction], 0, DZ[direction]);
            ChunkAccess neighbour = loaded(world, beside.getX() >> 4, beside.getZ() >> 4);

            // This is a downhill veto, not another source of higher terrain. Read only the loaded halo.
            if (neighbour == null) {
                continue;
            }

            BlockState atLip = neighbour.getBlockState(beside);
            if (catalogue.piece(atLip) != null) {
                edges |= 1 << direction;
                continue;
            }
            if (atLip.isAir() || SmoothingFoliage.isPlant(atLip)
                    || atLip.getBlock() == Blocks.SNOW) {
                BlockPos lowerFloor = beside.below();
                BlockState below = neighbour.getBlockState(lowerFloor);

                if (fullCollision(below) && below.isCollisionShapeFullBlock(world, lowerFloor)
                        && !below.hasBlockEntity()) {
                    edges |= 1 << direction;
                }
            }
        }

        return edges;
    }

    private boolean caveHeadroom(LevelAccessor world, ChunkAccess owner, BlockPos support) {
        // Three clear blocks leave 2.5 blocks above a new cap. A two-block tunnel must stay walkable.
        if (support.getY() + 3 >= owner.getMaxBuildHeight()) {
            return false;
        }
        for (int height = 2; height <= 3; height++) {
            BlockPos pos = support.above(height);
            BlockState state = owner.getBlockState(pos);

            if (!state.isAir()
                    || owner.getBlockEntity(pos) != null) {
                return false;
            }
        }

        return true;
    }

    private boolean oreSupport(BlockState state) {
        Boolean cached = oreSupports.get(state);

        if (cached != null) {
            return cached;
        }

        // The experience-dropping block class also covers non-ores such as sculk.
        boolean isOre = fullCollision(state) && !state.hasBlockEntity()
                && !state.liquid() && state.is(Tags.Blocks.ORES);

        oreSupports.put(state, isOre);
        return isOre;
    }

    private boolean higher(LevelAccessor world, ChunkAccess owner, int localX, int localZ, int lowerY) {
        if (!Footprint.allowedNeighbour(localX, localZ)) {
            return false;
        }

        int worldX = (owner.getPos().x << 4) + localX;
        int worldZ = (owner.getPos().z << 4) + localZ;

        ChunkAccess neighbour = loaded(world, worldX >> 4, worldZ >> 4);

        if (neighbour == null) {
            return false;
        }

        // Compare logical floors at this height, including existing caps over BOP's native soil.
        BlockPos floor = new BlockPos(worldX, lowerY + 1, worldZ);
        BlockState state = neighbour.getBlockState(floor);
        BlockState above = neighbour.getBlockState(floor.above());

        TerrainMaterial source = catalogue.source(state);
        TerrainMaterial cap = catalogue.piece(above);

        if (cap != null) {
            return source != null || catalogue.source(state) == cap
                    || cap.supportAfterPlacement != null && state.equals(cap.supportAfterPlacement)
                    || oreSupport(state);
        }

        return source != null && SmoothingFoliage.canReplace(world, neighbour, floor.above());
    }

    private static boolean isOverworld(LevelAccessor world) {
        if (world instanceof Level) {
            return ((Level) world).dimension() == Level.OVERWORLD;
        }
        return world instanceof WorldGenLevel && ((WorldGenLevel) world).getLevel().dimension() == Level.OVERWORLD;
    }

    /** A missing neighbour is unavailable, never a reason to request or generate another chunk. */
    private static ChunkAccess loaded(LevelAccessor world, int chunkX, int chunkZ) {
        if (world instanceof WorldGenRegion) {
            return world.hasChunk(chunkX, chunkZ) ? world.getChunk(chunkX, chunkZ) : null;
        }
        // getChunk(..., false) can still wait for an existing holder's future.
        // This native lookup uses getNow and returns null while generation is unfinished.
        return world.getChunkSource() instanceof ServerChunkCache
                ? ((ServerChunkCache) world.getChunkSource()).getChunkNow(chunkX, chunkZ) : null;
    }

    private static StructureProtection structures(LevelAccessor world, ChunkAccess owner) {
        return StructureProtection.read(owner, position -> loaded(world, position.x, position.z));
    }

    private static boolean fullCollision(BlockState state) {
        return !Shapes.joinIsNotEmpty(state.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO),
                Shapes.block(), BooleanOp.NOT_SAME);
    }

    private static final class Decisions {
        int size;
        int[] positions = new int[256];
        BlockState[] results = new BlockState[256], supports = new BlockState[256];
        BlockState[] originalTargets = new BlockState[256];
        TerrainMaterial[] materials = new TerrainMaterial[256];
        boolean[] caves = new boolean[256];

        void clear() {
            Arrays.fill(results, 0, size, null);
            Arrays.fill(supports, 0, size, null);
            Arrays.fill(materials, 0, size, null);
            Arrays.fill(originalTargets, 0, size, null);
            size = 0;
        }

        void add(int position, BlockState result, TerrainMaterial material, boolean cave, BlockState originalTarget) {
            if (size == positions.length) {
                int capacity = positions.length * 2;

                positions = Arrays.copyOf(positions, capacity);
                results = Arrays.copyOf(results, capacity);
                supports = Arrays.copyOf(supports, capacity);
                originalTargets = Arrays.copyOf(originalTargets, capacity);
                materials = Arrays.copyOf(materials, capacity);
                caves = Arrays.copyOf(caves, capacity);
            }

            positions[size] = position;
            results[size] = result;
            supports[size] = supportAfterPlacement(material, result);
            materials[size] = material;
            originalTargets[size] = originalTarget;
            caves[size++] = cave;
        }
    }

    private static final class FeatureDecorator {
        final BlockPos pos;
        BlockState state;
        TerrainMaterial material;
        BlockState originalSupport;
        BlockState dirtifiedSupport;
        final int level;
        final boolean cave;

        FeatureDecorator(BlockPos pos, BlockState state, TerrainMaterial material, int level, boolean cave,
                BlockState originalSupport) {
            this.pos = pos;
            this.state = state;
            this.material = material;
            this.level = level;
            this.cave = cave;
            this.originalSupport = originalSupport;
            BlockState dirt = supportAfterPlacement(material, state);
            this.dirtifiedSupport = material.grass && dirt != null && !dirt.equals(originalSupport) ? dirt : null;
        }
    }
}
