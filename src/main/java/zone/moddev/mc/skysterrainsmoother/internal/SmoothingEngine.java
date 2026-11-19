package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.io.*;
import java.util.*;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import zone.moddev.mc.skysgrassslabs.api.GrassSlabsApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Plans dry floor transitions before writing them, using only chunks already loaded. */
public final class SmoothingEngine {
    private static final int[] DX = { 0, 1, 0, -1 }, DZ = { -1, 0, 1, 0 }, CORNER_X = { -1, 1, 1, -1 },
            CORNER_Z = { -1, -1, 1, 1 };
    private final MaterialCatalogue catalogue;
    private ExistingChunkIndex existing;
    private final Set<Long> processed = new HashSet<>();
    private final Set<Long> populated = new HashSet<>();
    private final DeferredRepairs dirtyOwners = new DeferredRepairs();
    private boolean physicsNeighbourGuard;
    private final Map<Long, List<Placement>> pending = new HashMap<>();
    private final Map<Long, NBTTagCompound> savedPending = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Long> resumedOwners = new HashSet<>();
    private final Map<IBlockState, Boolean> oreSupports = new HashMap<>();
    private final Map<IBlockState, Integer> pieceEdges = new HashMap<>();
    private final ThreadLocal<Decisions> decisions = ThreadLocal.withInitial(Decisions::new);
    public long passes, placed, elapsedNanos, skippedExisting;
    public long completedRepairs, deferredRepairs, repairNanos;

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
        savedPending.clear();
        resumedOwners.clear();
        oreSupports.clear();
        pieceEdges.clear();
        passes = placed = elapsedNanos = skippedExisting = 0;
        completedRepairs = deferredRepairs = repairNanos = 0;
        physicsNeighbourGuard = net.minecraftforge.fml.common.Loader.isModLoaded("sable");
        SkysTerrainSmoother.logger.info("Indexed {} saved Overworld chunks; no smoothing retrogen will run",
                existing.size());
    }

    public void stop() {
        existing = null;
        processed.clear();
        populated.clear();
        dirtyOwners.clear();
        pending.clear();
        savedPending.clear();
        resumedOwners.clear();
        oreSupports.clear();
        pieceEdges.clear();
        decisions.remove();
    }

    public int indexedChunks() {
        return existing == null ? 0 : existing.size();
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public void beforeDecoration(DecorateBiomeEvent.Pre event) {
        World world = event.getWorld();

        if (world.isRemote || world.provider.getDimension() != 0 || !SkysTerrainSmoother.enabled || existing == null) {
            return;
        }

        int chunkX = event.getPos().getX() >> 4;
        int chunkZ = event.getPos().getZ() >> 4;

        if (existing.contains(chunkX, chunkZ)) {
            ++skippedExisting;
            return;
        }

        Chunk owner = loaded(world, chunkX, chunkZ);

        if (owner == null || !processed.add(ExistingChunkIndex.key(chunkX, chunkZ))) {
            return;
        }

        long start = System.nanoTime();

        placed += smooth(world, owner, SkysTerrainSmoother.level);
        elapsedNanos += System.nanoTime() - start;
        ++passes;
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public void afterDecoration(DecorateBiomeEvent.Post event) {
        World world = event.getWorld();

        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }

        int chunkX = event.getPos().getX() >> 4;
        int chunkZ = event.getPos().getZ() >> 4;

        checkAdjacent(world, chunkX, chunkZ);
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public void afterPopulation(PopulateChunkEvent.Post event) {
        World world = event.getWorld();

        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }

        populated.add(ExistingChunkIndex.key(event.getChunkX(), event.getChunkZ()));
        checkAdjacent(world, event.getChunkX(), event.getChunkZ());
    }

    private void checkAdjacent(World world, int chunkX, int chunkZ) {
        // The decoration offset can reach the next chunk east or south. Repairs still belong to their original owner.
        for (int adjacentChunkZ = chunkZ; adjacentChunkZ <= chunkZ + 1; adjacentChunkZ++) {
            for (int adjacentChunkX = chunkX; adjacentChunkX <= chunkX + 1; adjacentChunkX++) {
                long key = ExistingChunkIndex.key(adjacentChunkX, adjacentChunkZ);
                List<Placement> placements = pending.get(key);

                if (placements == null) {
                    continue;
                }

                dirtyOwners.add(key);
            }
        }
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST)
    public void endWorldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (event.phase != TickEvent.Phase.END || world.isRemote || world.provider.getDimension() != 0
                || !SkysTerrainSmoother.enabled) {
            return;
        }
        dirtyOwners.tick(key -> {
            List<Placement> placements = pending.get(key);
            if (placements == null) return DeferredRepairs.Result.DROP;
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            Chunk owner = loaded(world, chunkX, chunkZ);
            if (owner == null) return DeferredRepairs.Result.DROP;
            if (physicsNeighbourGuard && !RepairNeighbourhood.ready(chunkX, chunkZ,
                    (x, z) -> loaded(world, x, z) != null)) {
                deferredRepairs++;
                return DeferredRepairs.Result.RETRY;
            }
            long started = System.nanoTime();
            boolean validated = validate(world, chunkX, chunkZ, placements, resumedOwners.contains(key));
            repairNanos += System.nanoTime() - started;
            if (!validated) {
                deferredRepairs++;
                return DeferredRepairs.Result.RETRY;
            }
            completedRepairs++;
            if (resumedOwners.contains(key) || populated.contains(key)
                    && populated.contains(ExistingChunkIndex.key(chunkX - 1, chunkZ))
                    && populated.contains(ExistingChunkIndex.key(chunkX, chunkZ - 1))
                    && populated.contains(ExistingChunkIndex.key(chunkX - 1, chunkZ - 1))) {
                pending.remove(key);
                resumedOwners.remove(key);
                owner.markDirty();
            }
            return DeferredRepairs.Result.COMPLETE;
        });
    }

    @SubscribeEvent
    public void unload(net.minecraftforge.event.world.ChunkEvent.Unload event) {
        Chunk owner = event.getChunk();
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        long key = ExistingChunkIndex.key(owner.x, owner.z);
        dirtyOwners.remove(key);
        List<Placement> placements = pending.get(key);
        if (placements != null) {
            placements.removeIf(placement -> !sameState(owner.getBlockState(placement.pos), placement.state)
                    || placement.expectedSupport != null && !sameState(
                            owner.getBlockState(placement.pos.down()), placement.expectedSupport));
            for (Placement placement : placements) {
                placement.expectedSupport = owner.getBlockState(placement.pos.down());
            }
            resumedOwners.add(key);
            owner.markDirty();
        }
    }

    /** Internal diagnostics and native fixture synchronization, not an add-on API. */
    public boolean repairQueued(int chunkX, int chunkZ) {
        return dirtyOwners.contains(ExistingChunkIndex.key(chunkX, chunkZ));
    }

    public int queuedRepairs() {
        return dirtyOwners.size();
    }

    @SubscribeEvent
    public void loaded(net.minecraftforge.event.world.ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        Chunk owner = event.getChunk();
        restore(owner);
        for (int chunkZ = owner.z - 1; chunkZ <= owner.z + 1; chunkZ++) {
            for (int chunkX = owner.x - 1; chunkX <= owner.x + 1; chunkX++) {
                long key = ExistingChunkIndex.key(chunkX, chunkZ);
                if (pending.containsKey(key) && loaded(world, chunkX, chunkZ) != null) dirtyOwners.add(key);
            }
        }
    }

    @SubscribeEvent
    public void readRepairs(ChunkDataEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        NBTTagCompound data = event.getData().getCompoundTag("Level").getCompoundTag(PendingRepairData.KEY);
        if (data.getString("dimension").equals("minecraft:overworld")) {
            Chunk chunk = event.getChunk();
            savedPending.put(ExistingChunkIndex.key(chunk.x, chunk.z), data.copy());
        }
    }

    @SubscribeEvent
    public void saveRepairs(ChunkDataEvent.Save event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0) return;
        Chunk chunk = event.getChunk();
        long key = ExistingChunkIndex.key(chunk.x, chunk.z);
        NBTTagCompound level = event.getData().getCompoundTag("Level");
        level.removeTag(PendingRepairData.KEY);
        List<Placement> placements = pending.get(key);
        if (placements == null) {
            NBTTagCompound retained = savedPending.get(key);
            if (retained != null) level.setTag(PendingRepairData.KEY, retained.copy());
            return;
        }
        List<PendingRepairData.Entry> entries = new ArrayList<>();
        for (Placement placement : placements) {
            IBlockState support = chunk.getBlockState(placement.pos.down());
            if (sameState(chunk.getBlockState(placement.pos), placement.state)
                    && !support.getBlock().hasTileEntity(support)
                    && (placement.expectedSupport == null || sameState(support, placement.expectedSupport))) {
                entries.add(new PendingRepairData.Entry(placement.pos, placement.state, placement.material.id,
                        placement.level, placement.cave, placement.originalSupport, placement.dirtifiedSupport, support));
            }
        }
        if (!entries.isEmpty()) level.setTag(PendingRepairData.KEY, PendingRepairData.write(entries));
    }

    private void restore(Chunk chunk) {
        long key = ExistingChunkIndex.key(chunk.x, chunk.z);
        if (pending.containsKey(key)) {
            savedPending.remove(key);
            return;
        }
        NBTTagCompound data = savedPending.get(key);
        if (data == null) return;
        try {
            List<Placement> placements = new ArrayList<>();
            for (PendingRepairData.Entry entry : PendingRepairData.read(data, chunk.x, chunk.z, 0, 256)) {
                TerrainMaterial material = catalogue.material(entry.material());
                if (material == null || catalogue.piece(entry.cap()) != material) {
                    throw new IllegalArgumentException("Unavailable repair material: " + entry.material());
                }
                Placement placement = new Placement(entry.position(), entry.cap(), material,
                        entry.level(), entry.cave(), entry.originalSupport());
                placement.dirtifiedSupport = entry.dirtifiedSupport();
                placement.expectedSupport = entry.expectedSupport();
                placements.add(placement);
            }
            pending.put(key, placements);
            resumedOwners.add(key);
            savedPending.remove(key);
        } catch (IllegalArgumentException failure) {
            // Keep unavailable records intact so restoring an add-on can recover them later.
            SkysTerrainSmoother.logger.warn("Leaving pending terrain repairs unchanged in chunk {}, {}: {}",
                    chunk.x, chunk.z, failure.getMessage());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerBrokeBlock(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) abandonNearPlayerEdit(event.getWorld(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerPlacedBlock(BlockEvent.PlaceEvent event) {
        if (!event.isCanceled()) abandonNearPlayerEdit(event.getWorld(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerInteracted(PlayerInteractEvent.RightClickBlock event) {
        // Buckets, bonemeal and custom tools do not always post a placement event.
        abandonNearPlayerEdit(event.getWorld(), event.getPos());
    }

    private void abandonNearPlayerEdit(World world, BlockPos position) {
        if (world.isRemote || world.provider.getDimension() != 0) return;
        for (int chunkZ = (position.getZ() >> 4) - 1; chunkZ <= (position.getZ() >> 4) + 1; chunkZ++) {
            for (int chunkX = (position.getX() >> 4) - 1; chunkX <= (position.getX() >> 4) + 1; chunkX++) {
                Chunk owner = loaded(world, chunkX, chunkZ);
                if (owner == null) continue;
                long key = ExistingChunkIndex.key(chunkX, chunkZ);
                boolean removed = pending.remove(key) != null;
                removed |= savedPending.remove(key) != null;
                resumedOwners.remove(key);
                dirtyOwners.remove(key);
                if (removed) owner.markDirty();
            }
        }
    }

    private static boolean sameState(IBlockState first, IBlockState second) {
        return first.equals(second);
    }


    private boolean validate(World world, int chunkX, int chunkZ, List<Placement> placements,
            boolean recordedOnly) {
        Chunk owner = loaded(world, chunkX, chunkZ);

        if (owner == null) {
            return false;
        }

        // Plan repairs from one snapshot. Replace only our unchanged caps; never restore a decorator's old terrain.
        Map<Placement, IBlockState> repairs = new LinkedHashMap<>();
        Map<Placement, TerrainMaterial> materials = new HashMap<>();
        List<StructureBoundingBox> structures = StructureProtection.bounds(world, chunkX << 4, chunkZ << 4);

        placements.removeIf(placement -> !sameState(owner.getBlockState(placement.pos), placement.state)
                || placement.expectedSupport != null
                        && !sameState(owner.getBlockState(placement.pos.down()), placement.expectedSupport)
                || placement.material.grass && !GrassSlabsApi.grassGenerationAllowed()
                || placement.material.mineralogy && !SkysTerrainSmoother.mineralogy
                || !TerrainSmootherConfig.groupEnabled(placement.material.group)
                || recordedOnly && (StructureProtection.contains(structures, placement.pos)
                        || StructureProtection.contains(structures, placement.pos.down())
                        || !catalogue.allows(placement.material, world.getBiome(placement.pos), 0, placement.pos)));

        for (Placement placement : placements) {
            IBlockState support = owner.getBlockState(placement.pos.down());
            TerrainMaterial material = placement.material;
            boolean blocked = placement.cave && !caveHeadroom(world, owner, placement.pos.down());
            boolean raisesLip = raisesLowerEdge(world, placement.pos.down(), placement.state);
            boolean unsupported = catalogue.source(support) != material && !support.equals(material.supportAfterPlacement)
                    && !oreSupport(support);

            // Keep ore veins hidden beneath existing caps, but never use ore as a new smoothing source.
            if ((blocked || raisesLip || unsupported) && owner.getBlockState(placement.pos).equals(placement.state)) {
                boolean ourDirt = placement.dirtifiedSupport != null
                        && support.equals(placement.dirtifiedSupport)
                        && material.sources.contains(placement.originalSupport);
                TerrainMaterial replacement = ourDirt ? material : catalogue.source(support);
                IBlockState result = null;

                if (!blocked && replacement != null && TerrainSmootherConfig.groupEnabled(replacement.group) && (!replacement.grass || GrassSlabsApi.grassGenerationAllowed())
                        && (!replacement.mineralogy || SkysTerrainSmoother.mineralogy)
                        && support.isSideSolid(world, placement.pos.down(), EnumFacing.UP)
                        && !support.getBlock().hasTileEntity(support)
                        && owner.getTileEntity(placement.pos, Chunk.EnumCreateEntityType.CHECK) == null
                        && !StructureProtection.contains(structures, placement.pos)
                        && !StructureProtection.contains(structures, placement.pos.down())
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

                    result = transitionResult(world, owner, placement.pos.down(), replacement,
                            cardinal, diagonal, placement.level, placement.cave);
                }

                repairs.put(placement, result == null ? Blocks.AIR.getDefaultState() : result);
                materials.put(placement, replacement);
            }
        }

        boolean changed = false;

        for (Map.Entry<Placement, IBlockState> repair : repairs.entrySet()) {
            Placement placement = repair.getKey();

            if (!owner.getBlockState(placement.pos).equals(placement.state)) {
                continue;
            }

            IBlockState state = repair.getValue();
            IBlockState supportBefore = owner.getBlockState(placement.pos.down());
            TerrainMaterial previousMaterial = placement.material;
            boolean ourDirt = placement.dirtifiedSupport != null
                    && supportBefore.equals(placement.dirtifiedSupport)
                    && previousMaterial.sources.contains(placement.originalSupport);

            changed |= setGenerationState(owner, placement.pos, state);
            placement.state = state;
            placement.material = materials.get(placement);

            IBlockState supportAfter = placement.material != null && state.getBlock() != Blocks.AIR
                    ? supportAfterPlacement(placement.material, state) : null;
            if (supportAfter != null) {
                if (!ourDirt || previousMaterial != placement.material) {
                    placement.originalSupport = supportBefore;
                }
                changed |= setGenerationState(owner, placement.pos.down(), supportAfter);
                placement.dirtifiedSupport = placement.material.grass
                        && !supportAfter.equals(placement.originalSupport) ? supportAfter : null;
            } else {
                // Shrinking our own slab exposes its old grass. Never undo a decorator's replacement terrain.
                if (ourDirt && previousMaterial == placement.material && state.getBlock() != Blocks.AIR) {
                    changed |= setGenerationState(owner, placement.pos.down(), placement.originalSupport);
                }
                placement.dirtifiedSupport = null;
            }
        }
        if (changed) {
            owner.generateSkylightMap();
            owner.markDirty();
        }

        placements.removeIf(placement -> placement.state.getBlock() == Blocks.AIR);
        // Biome decoration can introduce new grass oases as well as beaches. Re-snapshot registered dry floors only in
        // this tracked fresh owner; occupied targets and existing saved chunks remain untouched.
        placed += recordedOnly ? 0 : smooth(world, owner, SkysTerrainSmoother.level, true);
        return true;
    }

    /** Calculate all placements before writing. Normal generation calls this through the event. */
    public int smooth(World world, Chunk owner, int level) {
        return smooth(world, owner, level, false);
    }

    private int smooth(World world, Chunk owner, int level, boolean afterDecoration) {
        Footprint.validateLevel(level);

        long key = ExistingChunkIndex.key(owner.x, owner.z);

        if (afterDecoration && !pending.containsKey(key)) {
            return 0;
        }

        Decisions buffer = decisions.get();

        buffer.clear();

        int startX = owner.x << 4;
        int startZ = owner.z << 4;

        List<StructureBoundingBox> structures = StructureProtection.bounds(world, startX, startZ);
        boolean grassAllowed = GrassSlabsApi.grassGenerationAllowed();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localBlockZ = 0; localBlockZ < 16; ++localBlockZ) {
            for (int localBlockX = 0; localBlockX < 16; ++localBlockX) {
                int surfaceY = SmoothingFoliage.surfaceHeight(owner, localBlockX, localBlockZ, startX, startZ);

                // Visit upward-facing floors at every depth. Finish every decision before the first world write.
                if (surfaceY < 0) {
                    continue;
                }

                IBlockState previous = owner.getBlockState(
                        cursor.setPos(startX + localBlockX, Math.min(surfaceY, 254) + 1, startZ + localBlockZ));

                for (int floorY = Math.min(surfaceY, 254); floorY >= 0; --floorY) {
                    IBlockState lower = owner
                            .getBlockState(cursor.setPos(startX + localBlockX, floorY, startZ + localBlockZ));
                    IBlockState above = previous;

                    previous = lower;

                    // Ignore thin natural cover when finding a floor, but never tunnel through occupied terrain.
                    if (above.getMaterial() != Material.AIR && !SmoothingFoliage.isCover(above)
                            || lower.getMaterial() == Material.AIR) {
                        continue;
                    }

                    TerrainMaterial material = catalogue.source(lower);

                    if (material == null || !TerrainSmootherConfig.groupEnabled(material.group)
                            || material.grass && !grassAllowed
                            || material.mineralogy && !SkysTerrainSmoother.mineralogy) {
                        continue;
                    }

                    BlockPos support = new BlockPos(startX + localBlockX, floorY, startZ + localBlockZ);
                    BlockPos target = support.up();

                    boolean cave = floorY < surfaceY;

                    if (cave && !caveHeadroom(world, owner, support)
                            || !lower.isSideSolid(world, support, EnumFacing.UP)
                            || !SmoothingFoliage.canReplace(world, owner, target)
                            || lower.getBlock().hasTileEntity(lower)
                            || owner.getTileEntity(target, Chunk.EnumCreateEntityType.CHECK) != null
                            || StructureProtection.contains(structures, target)
                            || StructureProtection.contains(structures, support)
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

                    IBlockState result = transitionResult(world, owner, support, material,
                            cardinal, diagonal, level, cave);

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
            IBlockState above = owner.getBlockState(target);

            if (!above.equals(buffer.originalTargets[index]) || !SmoothingFoliage.canReplace(world, owner, target)) {
                continue;
            }
            IBlockState originalSupport = owner.getBlockState(target.down());
            if (setGenerationState(owner, target, buffer.results[index])) {
                if (above.getBlock() == Blocks.DOUBLE_PLANT) {
                    setGenerationState(owner, target.up(), Blocks.AIR.getDefaultState());
                }
                if (buffer.supports[index] != null) {
                    setGenerationState(owner, target.down(), buffer.supports[index]);
                }

                placements.add(new Placement(target, buffer.results[index], buffer.materials[index], level,
                        buffer.caves[index], originalSupport));
                ++count;
            }
        }
        if (count > 0) {
            owner.generateSkylightMap();
            owner.markDirty();
        }
        if (afterDecoration) {
            pending.get(key).addAll(placements);
        } else {
            // Decoration can create new floors. Track this owner until its bordering decorators finish.
            resumedOwners.remove(key);
            savedPending.remove(key);
            pending.put(key, placements);
            dirtyOwners.add(key);
        }

        return count;
    }

    private static boolean setGenerationState(Chunk owner, BlockPos pos, IBlockState state) {
        if (pos.getY() < 0 || pos.getY() > 255 || pos.getX() >> 4 != owner.x || pos.getZ() >> 4 != owner.z) {
            throw new IllegalArgumentException("Terrain smoothing cannot write outside its owning chunk");
        }
        if (owner.getBlockState(pos).equals(state)) {
            return false;
        }

        ExtendedBlockStorage[] storage = owner.getBlockStorageArray();
        int index = pos.getY() >> 4;
        ExtendedBlockStorage section = storage[index];

        if (section == Chunk.NULL_BLOCK_STORAGE) {
            section = new ExtendedBlockStorage(index << 4, owner.getWorld().provider.hasSkyLight());
            storage[index] = section;
        }

        // Chunk#setBlockState can start cross-chunk lighting work in 1.12. Update storage here, then rebuild only the
        // owning chunk's light map.
        section.set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
        return true;
    }

    private IBlockState transitionResult(World world, Chunk owner, BlockPos support,
            TerrainMaterial material, int cardinal, int diagonal, int level, boolean cave) {
        int mask = Footprint.mask(cardinal, diagonal, level);
        if (mask == 0) {
            return null;
        }

        int lowerEdges = lowerEdges(world, support);
        int safeMask = Footprint.trimDownhillEdges(mask, lowerEdges);
        IBlockState result = catalogue.result(material, safeMask, level);

        // Missing finer shapes fall back to slabs. Check the actual piece against the downhill edges.
        if (result == null || (touchingEdges(result) & lowerEdges) != 0) {
            return null;
        }

        if (!cave && level >= 2 && lowerEdges == 0 && result != material.slab && Integer.bitCount(cardinal) == 1) {
            int uphill = Integer.numberOfTrailingZeros(cardinal);
            if (safeMask == Footprint.EDGES[uphill]) {
                // A full slab gives a broader tread where both sides of a one-block rise have room.
                int dx = DX[uphill];
                int dz = DZ[uphill];
                if (hasTerrainFloor(world, owner, support.add(-dx, 0, -dz))
                        && hasTerrainFloor(world, owner, support.add(2 * dx, 1, 2 * dz))) {
                    return material.slab;
                }
            }
        }

        return result;
    }

    private static IBlockState supportAfterPlacement(TerrainMaterial material, IBlockState piece) {
        // A slab hides the whole surface. Finer grass pieces must leave the visible ground grassy.
        return material.grass && !GrassSlabsApi.coversSupport(piece, EmptyAccess.INSTANCE, BlockPos.ORIGIN)
                ? null : material.supportAfterPlacement;
    }

    private boolean raisesLowerEdge(World world, BlockPos support, IBlockState result) {
        return (touchingEdges(result) & lowerEdges(world, support)) != 0;
    }

    private int touchingEdges(IBlockState result) {
        Integer edges = pieceEdges.get(result);

        if (edges == null) {
            AxisAlignedBB bounds = result.getBoundingBox(EmptyAccess.INSTANCE, BlockPos.ORIGIN);
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
    private int lowerEdges(World world, BlockPos support) {
        int edges = 0;

        for (int direction = 0; direction < 4; direction++) {
            BlockPos beside = support.add(DX[direction], 0, DZ[direction]);
            Chunk neighbour = loaded(world, beside.getX() >> 4, beside.getZ() >> 4);

            // This is a downhill veto, not another source of higher terrain. Read only the loaded halo.
            if (neighbour == null) {
                continue;
            }

            IBlockState atLip = neighbour.getBlockState(beside);
            if (catalogue.piece(atLip) != null) {
                edges |= 1 << direction;
                continue;
            }
            if (atLip.getMaterial() == Material.AIR || SmoothingFoliage.isPlant(atLip)
                    || atLip.getBlock() == Blocks.SNOW_LAYER) {
                BlockPos lowerFloor = beside.down();
                IBlockState below = neighbour.getBlockState(lowerFloor);

                if (below.isFullCube() && below.isSideSolid(world, lowerFloor, EnumFacing.UP)
                        && !below.getBlock().hasTileEntity(below)) {
                    edges |= 1 << direction;
                }
            }
        }

        return edges;
    }

    private boolean caveHeadroom(World world, Chunk owner, BlockPos support) {
        // Three clear blocks leave 2.5 blocks above a new cap. A two-block tunnel must stay walkable.
        if (support.getY() > 252) {
            return false;
        }
        for (int height = 2; height <= 3; height++) {
            BlockPos pos = support.up(height);
            IBlockState state = owner.getBlockState(pos);

            if (state.getMaterial() != Material.AIR || !state.getBlock().isAir(state, world, pos)
                    || owner.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK) != null) {
                return false;
            }
        }

        return true;
    }

    private boolean oreSupport(IBlockState state) {
        Boolean cached = oreSupports.get(state);

        if (cached != null) {
            return cached;
        }

        boolean isOre = false;

        if (state.isFullCube() && !state.getBlock().hasTileEntity(state) && !state.getMaterial().isLiquid()) {
            isOre = state.getBlock() instanceof BlockOre || state.getBlock() instanceof BlockRedstoneOre;

            if (!isOre) {
                ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));

                if (stack.getItem() != null) {
                    for (int oreId : OreDictionary.getOreIDs(stack)) {
                        if (OreDictionary.getOreName(oreId).startsWith("ore")) {
                            isOre = true;
                            break;
                        }
                    }
                }
            }
        }

        oreSupports.put(state, isOre);
        return isOre;
    }

    private boolean higher(World world, Chunk owner, int localX, int localZ, int lowerY) {
        if (!Footprint.allowedNeighbour(localX, localZ)) {
            return false;
        }

        int worldX = (owner.x << 4) + localX;
        int worldZ = (owner.z << 4) + localZ;

        Chunk neighbour = loaded(world, worldX >> 4, worldZ >> 4);

        if (neighbour == null) {
            return false;
        }

        return hasTerrainFloor(world, owner, new BlockPos(worldX, lowerY + 1, worldZ));
    }

    private boolean hasTerrainFloor(World world, Chunk owner, BlockPos floor) {
        Chunk neighbour = floor.getX() >> 4 == owner.x && floor.getZ() >> 4 == owner.z
                ? owner : loaded(world, floor.getX() >> 4, floor.getZ() >> 4);
        if (neighbour == null) {
            return false;
        }
        // Compare logical floors at this height, including existing caps over BOP's native soil.
        IBlockState state = neighbour.getBlockState(floor);
        IBlockState above = neighbour.getBlockState(floor.up());

        TerrainMaterial source = catalogue.source(state);
        TerrainMaterial cap = catalogue.piece(above);

        if (cap != null) {
            return source != null || cap.sources.contains(state)
                    || cap.supportAfterPlacement != null && state.equals(cap.supportAfterPlacement)
                    || oreSupport(state);
        }

        return source != null && SmoothingFoliage.canReplace(world, neighbour, floor.up());
    }

    /** A missing neighbour is unavailable, never a reason to request or generate another chunk. */
    private static Chunk loaded(World world, int chunkX, int chunkZ) {
        return world.getChunkProvider() instanceof ChunkProviderServer
                ? ((ChunkProviderServer) world.getChunkProvider()).getLoadedChunk(chunkX, chunkZ)
                : null;
    }

    private static final class Decisions {
        int size;
        int[] positions = new int[256];
        IBlockState[] results = new IBlockState[256], supports = new IBlockState[256];
        IBlockState[] originalTargets = new IBlockState[256];
        TerrainMaterial[] materials = new TerrainMaterial[256];
        boolean[] caves = new boolean[256];

        void clear() {
            Arrays.fill(results, 0, size, null);
            Arrays.fill(supports, 0, size, null);
            Arrays.fill(materials, 0, size, null);
            Arrays.fill(originalTargets, 0, size, null);
            size = 0;
        }

        void add(int position, IBlockState result, TerrainMaterial material, boolean cave, IBlockState originalTarget) {
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
        IBlockState state;
        TerrainMaterial material;
        IBlockState originalSupport;
        IBlockState dirtifiedSupport;
        IBlockState expectedSupport;
        final int level;
        final boolean cave;

        Placement(BlockPos pos, IBlockState state, TerrainMaterial material, int level, boolean cave,
                IBlockState originalSupport) {
            this.pos = pos;
            this.state = state;
            this.material = material;
            this.level = level;
            this.cave = cave;
            this.originalSupport = originalSupport;
            IBlockState dirt = supportAfterPlacement(material, state);
            this.dirtifiedSupport = material.grass && dirt != null && !dirt.equals(originalSupport) ? dirt : null;
        }
    }
}
