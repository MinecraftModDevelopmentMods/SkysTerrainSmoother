package zone.moddev.mc.skysterrainsmoother.probe;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Confirms Mineralogy support uses native slabs and the core's rock pieces without an add-on. */
public final class MineralogyProbe {
    public static int run() {
        if (!Boolean.getBoolean("skysterrainsmoother.requireMineralogy")) {
            return 0;
        }
        require(Loader.isModLoaded("mineralogy") && Loader.isModLoaded("orespawn"),
                "the optional profile loaded Mineralogy and its OreSpawn dependency");
        require(!Loader.isModLoaded("skysbuildingpiecesmineralogy"), "no separate piece add-on is installed");
        require(SkysTerrainSmoother.level == 3, "a fresh configuration uses level 3");
        int count = 0;
        for (TerrainMaterial material : MaterialCatalogue.instance().materials()) {
            if (!material.mineralogy) {
                continue;
            }
            ResourceLocation slabId = new ResourceLocation("mineralogy", material.id.getPath() + "_slab");
            require(Block.REGISTRY.getObject(slabId) == material.slab.getBlock(), "native slab identity");
            boolean bottom = false;
            for (IProperty<?> property : material.slab.getPropertyKeys()) {
                if (property.getName().equals("facing")) {
                    bottom = material.slab.getValue(property) == EnumFacing.UP;
                }
            }
            require(bottom, "Mineralogy FACING=UP is the bottom horizontal slab");
            for (int direction = 0; direction < 4; direction++) {
                require(material.step(direction) != null && material.corner(direction) != null,
                        "built-in steps and corners exist in every direction");
                require(material.step(direction).getBlock().getRegistryName().toString()
                                .startsWith("skysbuildingpieces:mineralogy_"),
                        "the core owns the rock pieces");
            }
            require(MaterialCatalogue.instance().result(material, 1, 1).equals(material.slab),
                    "level 1 retains the native slab");
            require(MaterialCatalogue.instance().result(material, 1, 3).equals(material.corner(0)),
                    "level 3 uses the core's northwest corner");
            require(MaterialCatalogue.instance().result(material, 3, 2).equals(material.step(0)),
                    "level 2 uses the core's north step");
            count++;
        }
        // The locked legacy releases have no pumice, chert, gypsum or chalk slabs.
        require(count == 24, "all 24 available natural rock slabs are registered, got " + count);
        System.out.println("TERRAIN_SMOOTHER_MINERALOGY_PASS materials=" + count + " finer_shapes=24 addon=false");
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private MineralogyProbe() {
    }
}
