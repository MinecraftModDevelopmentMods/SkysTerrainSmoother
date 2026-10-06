package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import zone.moddev.mc.skysterrainsmoother.api.*;

/** Baked lookup tables. No registry scans or reflective lookups in the column pass. */
public final class MaterialCatalogue {
    private static final MaterialCatalogue INSTANCE=new MaterialCatalogue();
    public static MaterialCatalogue instance(){return INSTANCE;}
    private final Map<ResourceLocation, TerrainMaterial> materials = new LinkedHashMap<>();
    private final Map<IBlockState, TerrainMaterial> sources = new HashMap<>(), pieces = new HashMap<>();
    private final List<TerrainSmoothingApi.BiomeRule> biomeRules = new ArrayList<>();
    private final List<TerrainSmoothingApi.AreaProtection> protections = new ArrayList<>();
    private boolean frozen;
    public void register(TerrainMaterial material) {
        requireOpen(); validate(material);
        if (materials.containsKey(material.id)) throw new IllegalArgumentException("Duplicate material " + material.id);
        for (IBlockState source : material.sources) if (sources.containsKey(canonical(source)))
            throw new IllegalArgumentException("Conflicting terrain source " + source);
        Map<ResourceLocation,TerrainMaterial> candidate=new LinkedHashMap<>(materials);
        candidate.put(material.id, material); commit(candidate);
    }
    public void addShapes(ResourceLocation id, IBlockState[] steps, IBlockState[] corners) {
        requireOpen(); TerrainMaterial current = materials.get(id);
        if (current == null) throw new IllegalArgumentException("Register the base material first: " + id);
        TerrainMaterial updated = current.withAdditionalShapes(steps, corners); validate(updated);
        Map<ResourceLocation,TerrainMaterial> candidate=new LinkedHashMap<>(materials);
        candidate.put(id, updated); commit(candidate);
    }
    public void addBiomeRule(TerrainSmoothingApi.BiomeRule rule) { requireOpen(); biomeRules.add(Objects.requireNonNull(rule)); }
    public void addAreaProtection(TerrainSmoothingApi.AreaProtection rule) { requireOpen(); protections.add(Objects.requireNonNull(rule)); }
    public void freeze() { requireOpen(); frozen = true; }
    public boolean frozen() { return frozen; }
    public int size() { return materials.size(); }
    public TerrainMaterial material(ResourceLocation id){return materials.get(id);}
    public Collection<TerrainMaterial> materials() { return Collections.unmodifiableCollection(materials.values()); }
    public TerrainMaterial source(IBlockState state) { return sources.get(canonical(state)); }
    public TerrainMaterial piece(IBlockState state) { return pieces.get(canonical(state)); }
    public boolean allows(TerrainMaterial material, Biome biome, int dimension, BlockPos target) {
        for (TerrainSmoothingApi.BiomeRule rule : biomeRules) if (!rule.allows(material.id, biome)) return false;
        for (TerrainSmoothingApi.AreaProtection rule : protections) if (rule.protects(dimension, target, biome)) return false;
        return true;
    }
    public IBlockState result(TerrainMaterial material, int mask, int level) {
        int shape = Footprint.shape(mask, level), direction = Footprint.direction(mask, shape);
        if (shape == 0) return null;
        IBlockState fine = shape == 2 ? material.step(direction) : shape == 3 ? material.corner(direction) : null;
        return fine == null ? material.slab : fine;
    }
    private void requireOpen() { if (frozen) throw new IllegalStateException("Terrain catalogue is frozen"); }
    private void commit(Map<ResourceLocation,TerrainMaterial> candidate) {
        Map<IBlockState,TerrainMaterial> newSources=new HashMap<>(),newPieces=new HashMap<>();
        for (TerrainMaterial material : candidate.values()) {
            for (IBlockState source : material.sources) {
                TerrainMaterial prior=newSources.put(canonical(source),material);
                if(prior!=null)throw new IllegalArgumentException("Conflicting terrain source "+source);
            }
            indexPiece(newPieces,material.slab, material);
            for (int direction = 0; direction < 4; ++direction) {
                indexPiece(newPieces,material.step(direction), material); indexPiece(newPieces,material.corner(direction), material);
            }
        }
        materials.clear();materials.putAll(candidate);
        sources.clear();sources.putAll(newSources);pieces.clear();pieces.putAll(newPieces);
    }
    private static void indexPiece(Map<IBlockState,TerrainMaterial> outputs,IBlockState state, TerrainMaterial material) {
        if (state == null) return;
        TerrainMaterial prior = outputs.put(canonical(state), material);
        if (prior != null && prior != material) throw new IllegalArgumentException("Conflicting terrain output " + state);
    }
    private static void validate(TerrainMaterial material) {
        for (IBlockState source : material.sources) if (!source.isFullCube() || source.getBlock().hasTileEntity(source))
            throw new IllegalArgumentException("Terrain sources must be full blocks without tile entities");
        if (material.supportAfterPlacement != null && (!material.supportAfterPlacement.isFullCube() ||
                material.supportAfterPlacement.getBlock().hasTileEntity(material.supportAfterPlacement)))
            throw new IllegalArgumentException("Support conversion requires a full non-tile state");
        validatePiece(material.slab, 15);
        for (int direction = 0; direction < 4; ++direction) {
            if (material.step(direction) != null) validatePiece(material.step(direction), Footprint.EDGES[direction]);
            if (material.corner(direction) != null) validatePiece(material.corner(direction), 1 << direction);
        }
    }
    private static void validatePiece(IBlockState state, int footprint) {
        if (state.getBlock().hasTileEntity(state) || state.isFullCube()) throw new IllegalArgumentException("A terrain piece cannot be full or tile-backed");
        AxisAlignedBB box = state.getBoundingBox(EmptyAccess.INSTANCE, BlockPos.ORIGIN);
        double x0=1,z0=1,x1=0,z1=0;
        for(int cell=0;cell<4;++cell)if((footprint&1<<cell)!=0) {
            double x=cell==0||cell==3?0:.5,z=cell<2?0:.5;
            x0=Math.min(x0,x);z0=Math.min(z0,z);x1=Math.max(x1,x+.5);z1=Math.max(z1,z+.5);
        }
        if (box == null || box.minX != x0 || box.maxX != x1 || box.minZ != z0 || box.maxZ != z1 || box.minY != 0 || box.maxY != .5)
            throw new IllegalArgumentException("Incorrect bottom-piece geometry: " + state);
    }
    private static IBlockState canonical(IBlockState state) {
        return state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state));
    }
}
