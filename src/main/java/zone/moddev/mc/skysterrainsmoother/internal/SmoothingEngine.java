package zone.moddev.mc.skysterrainsmoother.internal;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.OreBlock;
import net.minecraft.block.RedstoneOreBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowyDirtBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
    private final Map<Long, List<Placement>> pending = new ConcurrentHashMap<>();
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

    /** Called by the first vegetation feature; the decoration origin belongs to this chunk in Forge 28. */
    public boolean generate(IWorld world, int chunkX, int chunkZ) {
        ExistingChunkIndex index = existing;
        if (world.getDimension().getType() != DimensionType.OVERWORLD || !SkysTerrainSmoother.enabled
                || index == null) {
            return false;
        }
        if (index.contains(chunkX, chunkZ)) {
            synchronized (this) {
                skippedExisting++;
            }
            return false;
        }
        IChunk owner = loaded(world, chunkX, chunkZ);
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
    public void loaded(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof World)) {
            return;
        }
        World world = (World) event.getWorld();
        if (world.isRemote || world.getDimension().getType() != DimensionType.OVERWORLD) {
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
    public void endWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (event.phase != TickEvent.Phase.END || world.isRemote
                || world.getDimension().getType() != DimensionType.OVERWORLD) {
            return;
        }
        for (long key : new ArrayList<>(dirtyOwners)) {
            if (!dirtyOwners.remove(key)) {
                continue;
            }
            List<Placement> placements = pending.get(key);
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
    public void unload(net.minecraftforge.event.world.ChunkEvent.Unload event) {
        if (!(event.getWorld() instanceof World)) {
            return;
        }
        World world = (World) event.getWorld();
        if (world.isRemote || world.getDimension().getType() != DimensionType.OVERWORLD) {
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

    private boolean validate(IWorld world, int chunkX, int chunkZ, List<Placement> placements) {
        IChunk owner = loaded(world, chunkX, chunkZ);

        if (owner == null) {
            return false;
        }

        // Plan repairs from one snapshot. Replace only our unchanged caps; never restore a decorator's old terrain.
        Map<Placement, BlockState> repairs = new LinkedHashMap<>();
        Map<Placement, TerrainMaterial> materials = new HashMap<>();
        StructureProtection structures = structures(world, owner);
        if (!structures.canInspectSafely()) {
            return false;
        }

        for (Placement placement : placements) {
            BlockState support = owner.getBlockState(placement.pos.down());
            TerrainMaterial material = placement.material;
            boolean blocked = placement.cave && !caveHeadroom(world, owner, placement.pos.down());
            boolean raisesLip = raisesLowerEdge(world, placement.pos.down(), placement.state);
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
                        && support.isCollisionShapeOpaque(world, placement.pos.down())
                        && !support.getBlock().hasTileEntity(support)
                        && owner.getTileEntity(placement.pos) == null
                        && !structures.contains(placement.pos)
                        && !structures.contains(placement.pos.down())
                        && catalogue.allows(replacement, world.getBiome(placement.pos), 0, placement.pos)) {
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

                    result = transitionResult(world, placement.pos.down(), replacement,
                            Footprint.mask(cardinal, diagonal, placement.level), placement.level);
                }

                repairs.put(placement, result == null ? Blocks.AIR.getDefaultState() : result);
                materials.put(placement, replacement);
            }
        }

        boolean changed = false;

        for (Map.Entry<Placement, BlockState> repair : repairs.entrySet()) {
            Placement placement = repair.getKey();

            if (!sameState(owner.getBlockState(placement.pos), placement.state)) {
                continue;
            }

            BlockState state = repair.getValue();
            BlockState supportBefore = owner.getBlockState(placement.pos.down());
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
                changed |= setGenerationState(world, owner, placement.pos.down(), supportAfter);
                placement.dirtifiedSupport = placement.material.grass
                        && !supportAfter.equals(placement.originalSupport) ? supportAfter : null;
            } else {
                // Shrinking our own slab exposes its old grass. Never undo a decorator's replacement terrain.
                if (ourDirt && previousMaterial == placement.material && state.getBlock() != Blocks.AIR) {
                    changed |= setGenerationState(world, owner, placement.pos.down(), placement.originalSupport);
                }
                placement.dirtifiedSupport = null;
            }
        }
        if (changed) {
            owner.setModified(true);
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
    public int smooth(IWorld world, IChunk owner, int level) {
        return smooth(world, owner, level, false);
    }

    private int smooth(IWorld world, IChunk owner, int level, boolean afterDecoration) {
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
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int localBlockZ = 0; localBlockZ < 16; ++localBlockZ) {
            for (int localBlockX = 0; localBlockX < 16; ++localBlockX) {
                int surfaceY = SmoothingFoliage.surfaceHeight(owner, localBlockX, localBlockZ, startX, startZ);

                // Visit upward-facing floors at every depth. Finish every decision before the first world write.
                if (surfaceY < 0) {
                    continue;
                }

                BlockState previous = owner.getBlockState(
                        cursor.setPos(startX + localBlockX, Math.min(surfaceY, 254) + 1, startZ + localBlockZ));

                for (int floorY = Math.min(surfaceY, 254); floorY >= 0; --floorY) {
                    BlockState lower = owner
                            .getBlockState(cursor.setPos(startX + localBlockX, floorY, startZ + localBlockZ));
                    BlockState above = previous;

                    previous = lower;

                    // Ignore thin natural cover when finding a floor, but never tunnel through occupied terrain.
                    if (above.getMaterial() != Material.AIR && !SmoothingFoliage.isCover(above)
                            || lower.getMaterial() == Material.AIR) {
                        continue;
                    }

                    TerrainMaterial material = catalogue.source(lower);

                    if (material == null || material.grass && !grassAllowed
                            || material.mineralogy && !SkysTerrainSmoother.mineralogy) {
                        continue;
                    }

                    BlockPos support = new BlockPos(startX + localBlockX, floorY, startZ + localBlockZ);
                    BlockPos target = support.up();

                    boolean cave = floorY < surfaceY;

                    if (cave && !caveHeadroom(world, owner, support)
                            || !lower.isCollisionShapeOpaque(world, support)
                            || !SmoothingFoliage.canReplace(world, owner, target)
                            || lower.getBlock().hasTileEntity(lower)
                            || owner.getTileEntity(target) != null
                            || structures.contains(target)
                            || structures.contains(support)
                            || !catalogue.allows(material, world.getBiome(target), 0, target)) {
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
        List<Placement> placements = new ArrayList<>();

        for (int index = 0; index < buffer.size; ++index) {
            int position = buffer.positions[index];
            BlockPos target = new BlockPos(startX + (position & 15), position >> 8, startZ + ((position >> 4) & 15));
            BlockState above = owner.getBlockState(target);

            if (!above.equals(buffer.originalTargets[index]) || !SmoothingFoliage.canReplace(world, owner, target)) {
                continue;
            }
            BlockState originalSupport = owner.getBlockState(target.down());
            if (setGenerationState(world, owner, target, buffer.results[index])) {
                if (above.getBlock() instanceof DoublePlantBlock) {
                    setGenerationState(world, owner, target.up(), Blocks.AIR.getDefaultState());
                }
                if (buffer.supports[index] != null) {
                    setGenerationState(world, owner, target.down(), buffer.supports[index]);
                }

                placements.add(new Placement(target, owner.getBlockState(target), buffer.materials[index], level,
                        buffer.caves[index], originalSupport));
                ++count;
            }
        }
        if (count > 0) {
            owner.setModified(true);
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

    private static boolean setGenerationState(IWorld world, IChunk owner, BlockPos position, BlockState state) {
        if (position.getY() < 0 || position.getY() > 255
                || position.getX() >> 4 != owner.getPos().x || position.getZ() >> 4 != owner.getPos().z) {
            throw new IllegalArgumentException("Terrain smoothing cannot write outside its owning chunk");
        }
        BlockState before = owner.getBlockState(position);
        if (before.equals(state)) {
            return false;
        }
        owner.setBlockState(position, state, false);
        BlockState actual = owner.getBlockState(position);
        if (world instanceof World && owner.getStatus() == ChunkStatus.FULL && !before.equals(actual)) {
            // A late repair may follow the chunk's first packet. Notify tracked clients
            // without running neighbour placement logic outside this owner.
            ((World) world).notifyBlockUpdate(position, before, actual, 2);
        }
        return sameState(actual, state);
    }

    private static boolean sameState(BlockState first, BlockState second) {
        // Nearby snow can update the visual property between decoration callbacks.
        // Geometry, material and water state must still match our original cap.
        if (first.has(SnowyDirtBlock.SNOWY)) {
            first = first.with(SnowyDirtBlock.SNOWY, false);
        }
        if (second.has(SnowyDirtBlock.SNOWY)) {
            second = second.with(SnowyDirtBlock.SNOWY, false);
        }
        return first.equals(second);
    }

    private BlockState transitionResult(IWorld world, BlockPos support, TerrainMaterial material, int mask, int level) {
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

    private boolean raisesLowerEdge(IWorld world, BlockPos support, BlockState result) {
        return (touchingEdges(result) & lowerEdges(world, support)) != 0;
    }

    private int touchingEdges(BlockState result) {
        Integer edges = pieceEdges.get(result);

        if (edges == null) {
            AxisAlignedBB bounds = result.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO).getBoundingBox();
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
    private int lowerEdges(IWorld world, BlockPos support) {
        int edges = 0;

        for (int direction = 0; direction < 4; direction++) {
            BlockPos beside = support.add(DX[direction], 0, DZ[direction]);
            IChunk neighbour = loaded(world, beside.getX() >> 4, beside.getZ() >> 4);

            // This is a downhill veto, not another source of higher terrain. Read only the loaded halo.
            if (neighbour == null) {
                continue;
            }

            BlockState atLip = neighbour.getBlockState(beside);
            if (catalogue.piece(atLip) != null) {
                edges |= 1 << direction;
                continue;
            }
            if (atLip.getMaterial() == Material.AIR || SmoothingFoliage.isPlant(atLip)
                    || atLip.getBlock() == Blocks.SNOW) {
                BlockPos lowerFloor = beside.down();
                BlockState below = neighbour.getBlockState(lowerFloor);

                if (fullCollision(below) && below.isCollisionShapeOpaque(world, lowerFloor)
                        && !below.getBlock().hasTileEntity(below)) {
                    edges |= 1 << direction;
                }
            }
        }

        return edges;
    }

    private boolean caveHeadroom(IWorld world, IChunk owner, BlockPos support) {
        // Three clear blocks leave 2.5 blocks above a new cap. A two-block tunnel must stay walkable.
        if (support.getY() > 252) {
            return false;
        }
        for (int height = 2; height <= 3; height++) {
            BlockPos pos = support.up(height);
            BlockState state = owner.getBlockState(pos);

            if (state.getMaterial() != Material.AIR || !state.getBlock().isAir(state, world, pos)
                    || owner.getTileEntity(pos) != null) {
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

        boolean isOre = false;

        if (fullCollision(state) && !state.getBlock().hasTileEntity(state) && !state.getMaterial().isLiquid()) {
            isOre = state.getBlock() instanceof OreBlock || state.getBlock() instanceof RedstoneOreBlock;

            if (!isOre) {
                Tag<net.minecraft.block.Block> ores = BlockTags.getCollection().get(new ResourceLocation("forge", "ores"));
                isOre = ores != null && ores.contains(state.getBlock());
            }
        }

        oreSupports.put(state, isOre);
        return isOre;
    }

    private boolean higher(IWorld world, IChunk owner, int localX, int localZ, int lowerY) {
        if (!Footprint.allowedNeighbour(localX, localZ)) {
            return false;
        }

        int worldX = (owner.getPos().x << 4) + localX;
        int worldZ = (owner.getPos().z << 4) + localZ;

        IChunk neighbour = loaded(world, worldX >> 4, worldZ >> 4);

        if (neighbour == null) {
            return false;
        }

        // Compare logical floors at this height, including existing caps over BOP's native soil.
        BlockPos floor = new BlockPos(worldX, lowerY + 1, worldZ);
        BlockState state = neighbour.getBlockState(floor);
        BlockState above = neighbour.getBlockState(floor.up());

        TerrainMaterial source = catalogue.source(state);
        TerrainMaterial cap = catalogue.piece(above);

        if (cap != null) {
            return source != null || catalogue.source(state) == cap
                    || cap.supportAfterPlacement != null && state.equals(cap.supportAfterPlacement)
                    || oreSupport(state);
        }

        return source != null && SmoothingFoliage.canReplace(world, neighbour, floor.up());
    }

    /** A missing neighbour is unavailable, never a reason to request or generate another chunk. */
    private static IChunk loaded(IWorld world, int chunkX, int chunkZ) {
        if (world instanceof WorldGenRegion) {
            return world.chunkExists(chunkX, chunkZ) ? world.getChunk(chunkX, chunkZ) : null;
        }
        // getChunk(..., false) can still wait for an existing holder's future.
        // This native lookup uses getNow and returns null while generation is unfinished.
        return world.getChunkProvider() instanceof ServerChunkProvider
                ? ((ServerChunkProvider) world.getChunkProvider()).getChunkWithoutLoading(chunkX, chunkZ) : null;
    }

    private static StructureProtection structures(IWorld world, IChunk owner) {
        return StructureProtection.read(owner, position -> loaded(world, position.x, position.z));
    }

    private static boolean fullCollision(BlockState state) {
        return !VoxelShapes.compare(state.getCollisionShape(EmptyAccess.INSTANCE, BlockPos.ZERO),
                VoxelShapes.fullCube(), IBooleanFunction.NOT_SAME);
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

    private static final class Placement {
        final BlockPos pos;
        BlockState state;
        TerrainMaterial material;
        BlockState originalSupport;
        BlockState dirtifiedSupport;
        final int level;
        final boolean cave;

        Placement(BlockPos pos, BlockState state, TerrainMaterial material, int level, boolean cave,
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
