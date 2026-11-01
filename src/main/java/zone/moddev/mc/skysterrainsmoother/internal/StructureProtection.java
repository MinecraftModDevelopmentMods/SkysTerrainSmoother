package zone.moddev.mc.skysterrainsmoother.internal;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.feature.structure.StructureStart;

/** Read known structure bounds without asking the generator for another chunk. */
final class StructureProtection {
    private final List<MutableBoundingBox> bounds = new ArrayList<>();
    private boolean unresolved;

    static StructureProtection read(IChunk owner, Function<ChunkPos, IChunk> loadedChunk) {
        StructureProtection result = new StructureProtection();
        Set<Long> visited = new HashSet<>();
        visited.add(owner.getPos().asLong());
        result.addStarts(owner);
        for (LongSet references : owner.getStructureReferences().values()) {
            for (long reference : references) {
                if (!visited.add(reference)) {
                    continue;
                }
                IChunk startChunk = loadedChunk.apply(new ChunkPos(reference));
                if (startChunk == null) {
                    // The reference may point beyond the loaded generation halo.
                    // Leave this chunk alone instead of loading it to find out.
                    result.unresolved = true;
                } else {
                    result.addStarts(startChunk);
                }
            }
        }
        return result;
    }

    boolean canInspectSafely() {
        return !unresolved;
    }

    boolean contains(BlockPos position) {
        for (MutableBoundingBox box : bounds) {
            if (box.isVecInside(position)) {
                return true;
            }
        }
        return false;
    }

    private void addStarts(IChunk chunk) {
        for (StructureStart start : chunk.getStructureStarts().values()) {
            if (start != null && start.isValid()) {
                bounds.add(new MutableBoundingBox(start.getBoundingBox()));
            }
        }
    }
}
