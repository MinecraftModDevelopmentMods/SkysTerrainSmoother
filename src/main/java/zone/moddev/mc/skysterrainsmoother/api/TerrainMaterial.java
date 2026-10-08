package zone.moddev.mc.skysterrainsmoother.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

/** Immutable exact-state mapping. Direction order is north, east, south, west. */
public final class TerrainMaterial {
    public final ResourceLocation id;
    public final Set<IBlockState> sources;
    public final boolean grass;
    public final boolean mineralogy;
    public final IBlockState slab, supportAfterPlacement;
    private final IBlockState[] steps, corners;

    public TerrainMaterial(ResourceLocation id, Set<IBlockState> sources, IBlockState slab,
            IBlockState[] steps, IBlockState[] corners, boolean grass, IBlockState supportAfterPlacement) {
        if (id == null || sources == null || sources.isEmpty() || slab == null)
            throw new IllegalArgumentException("A material requires an identity, exact full states and a slab");
        this.id = id; this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        this.slab = slab; this.steps = copyShapes(steps); this.corners = copyShapes(corners);
        this.grass = grass; this.supportAfterPlacement = supportAfterPlacement;
        boolean mineral=false;
        for(IBlockState source:this.sources) {
            if(source==null)throw new IllegalArgumentException("A source cannot be null");
            ResourceLocation name=source.getBlock().getRegistryName();
            if(name!=null&&name.getResourceDomain().equals("mineralogy"))mineral=true;
        }
        this.mineralogy=mineral;
    }
    private static IBlockState[] copyShapes(IBlockState[] shapes) {
        if (shapes == null) return new IBlockState[4];
        if (shapes.length != 4) throw new IllegalArgumentException("Provide all four directions");
        int present = 0;
        for (IBlockState state : shapes) if (state != null) ++present;
        if (present != 0 && present != 4) throw new IllegalArgumentException("A shape needs every direction or none");
        return shapes.clone();
    }
    public IBlockState step(int direction) { return steps[direction]; }
    public IBlockState corner(int direction) { return corners[direction]; }
    public TerrainMaterial withAdditionalShapes(IBlockState[] addedSteps, IBlockState[] addedCorners) {
        if (addedSteps != null && steps[0] != null || addedCorners != null && corners[0] != null)
            throw new IllegalArgumentException("An add-on may supply missing shapes, not replace existing shapes");
        return new TerrainMaterial(id, sources, slab, addedSteps == null ? steps : addedSteps,
                addedCorners == null ? corners : addedCorners, grass, supportAfterPlacement);
    }
}
