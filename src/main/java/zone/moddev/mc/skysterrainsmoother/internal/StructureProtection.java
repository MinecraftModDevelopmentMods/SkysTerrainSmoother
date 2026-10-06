package zone.moddev.mc.skysterrainsmoother.internal;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.gen.structure.StructureBoundingBox;

final class StructureProtection {
    private StructureProtection() { }
    static List<StructureBoundingBox> bounds(World world,int startX,int startZ) {
        List<StructureBoundingBox> result = new ArrayList<>();
        for (String id : new String[]{"Mineshaft","Village","Stronghold","Temple","Monument","Fortress"}) {
            MapGenStructureData data = (MapGenStructureData)world.getPerWorldStorage().getOrLoadData(MapGenStructureData.class,id);
            if (data == null) continue;
            NBTTagCompound starts = data.getTagCompound();
            for (String key : starts.getKeySet()) {
                int[] saved = starts.getCompoundTag(key).getIntArray("BB");
                if (saved.length != 6) continue;
                StructureBoundingBox box = new StructureBoundingBox(saved);
                if (box.intersectsWith(startX,startZ,startX+15,startZ+15)) result.add(box);
            }
        }
        return result;
    }
    static boolean contains(List<StructureBoundingBox> bounds,BlockPos target) {
        for(StructureBoundingBox box:bounds)if(box.isVecInside(target)||box.isVecInside(target.down()))return true;
        return false;
    }
}
