package zone.moddev.mc.skysterrainsmoother.internal;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Read known structure bounds without asking the generator for another chunk. */
final class StructureProtection {
    private final List<BoundingBox> bounds = new ArrayList<>();
    private boolean unresolved;

    static StructureProtection read(ChunkAccess owner, Function<ChunkPos, ChunkAccess> loadedChunk) {
        StructureProtection result = new StructureProtection();
        Set<Long> visited = new HashSet<>();
        visited.add(owner.getPos().pack());
        result.addStarts(owner);
        for (LongSet references : owner.getAllReferences().values()) {
            for (long reference : references) {
                if (!visited.add(reference)) {
                    continue;
                }
                ChunkAccess startChunk = loadedChunk.apply(ChunkPos.unpack(reference));
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
        for (BoundingBox box : bounds) {
            if (box.isInside(position)) {
                return true;
            }
        }
        return false;
    }

    private void addStarts(ChunkAccess chunk) {
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start != null && start.isValid()) {
                BoundingBox box = start.getBoundingBox();
                bounds.add(new BoundingBox(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ()));
            }
        }
    }
}
