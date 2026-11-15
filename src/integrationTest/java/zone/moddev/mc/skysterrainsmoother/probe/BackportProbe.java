package zone.moddev.mc.skysterrainsmoother.probe;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.api.MaterialGroup;
import zone.moddev.mc.skysterrainsmoother.api.TerrainMaterial;
import zone.moddev.mc.skysterrainsmoother.config.TerrainSmootherConfig;
import zone.moddev.mc.skysterrainsmoother.content.GravelSlabBlock;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.internal.MaterialCatalogue;

/** Exercises actual 1.12 states, crafting and gravity rather than modern API stand-ins. */
final class BackportProbe {
    static int run(WorldServer world) throws Exception {
        BlockPos floor = new BlockPos(3896, 220, 3896);
        Chunk owner = world.getChunk(floor);
        MaterialCatalogue catalogue = MaterialCatalogue.instance();
        int checks = 0;
        for (IBlockState source : new IBlockState[] { Blocks.STONE.getDefaultState(), Blocks.SNOW.getDefaultState(),
                Blocks.PACKED_ICE.getDefaultState(), Blocks.GRAVEL.getDefaultState() }) {
            TerrainMaterial material = catalogue.source(source);
            RuntimeProbe.require(material != null, "the legacy material has an exact smoothing mapping: " + source);
            for (EnumFacing uphill : EnumFacing.HORIZONTALS) {
                for (int level = 1; level <= 3; level++) {
                    terrace(world, floor, source, uphill, true);
                    int loaded = world.getChunkProvider().getLoadedChunkCount();
                    SkysTerrainSmoother.engine.smooth(world, owner, level);
                    RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.slab),
                            "a broad open terrace uses the full slab at every level: " + source + "/" + uphill);
                    RuntimeProbe.require(world.getChunkProvider().getLoadedChunkCount() == loaded,
                            "broader tread checks do not load chunks");
                    RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, level) == 0,
                            "the terrace remains stable on another pass");
                    checks += 3;
                    if (level > 1 && material.step(direction(uphill)) != null) {
                        terrace(world, floor, source, uphill, false);
                        SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(world.getBlockState(floor.up()).equals(material.step(direction(uphill))),
                                "an isolated rise retains its narrower step");
                        checks++;
                    }
                }
            }
        }

        Field active = TerrainSmootherConfig.class.getDeclaredField("enabledGroups");
        active.setAccessible(true);
        int original = active.getInt(null);
        try {
            // Changing this private startup snapshot belongs only to the build probe.
            for (TerrainMaterial material : catalogue.materials()) {
                IBlockState source = material.sources.iterator().next();
                active.setInt(null, original & ~(1 << material.group.ordinal()));
                terrace(world, floor, source, EnumFacing.NORTH, true);
                SkysTerrainSmoother.engine.smooth(world, owner, 3);
                RuntimeProbe.require(world.isAirBlock(floor.up()), "disabled family is not placed: " + material.id);
                RuntimeProbe.require(catalogue.source(source) == material && catalogue.piece(material.slab) == material,
                        "disabled families remain recognisable for neighbouring geometry");
                active.setInt(null, original);
                checks += 2;
            }
            active.setInt(null, 0);
            terrace(world, floor, Blocks.STONE.getDefaultState(), EnumFacing.NORTH, true);
            RuntimeProbe.require(SkysTerrainSmoother.engine.smooth(world, owner, 3) == 0, "all material switches off");
        } finally {
            active.setInt(null, original);
        }
        RuntimeProbe.require(catalogue.source(Blocks.SNOW.getDefaultState()).group == MaterialGroup.SNOW,
                "snow belongs to the snow family");
        RuntimeProbe.require(catalogue.source(Blocks.PACKED_ICE.getDefaultState()).group == MaterialGroup.ICE,
                "packed ice belongs to the ice family");
        checks += gravel(world, floor);
        System.out.println("TERRAIN_SMOOTHER_BACKPORT_PASS checks=" + checks
                + " broad_treads=true groups=true snow=true packed_ice=true gravel=true");
        return checks;
    }

    private static void terrace(WorldServer world, BlockPos floor, IBlockState source,
            EnumFacing uphill, boolean broad) {
        for (int offsetZ = -6; offsetZ <= 6; offsetZ++) {
            for (int offsetX = -6; offsetX <= 6; offsetX++) {
                BlockPos support = floor.add(offsetX, 0, offsetZ);
                world.setBlockState(support.down(), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(support, source, 2);
                for (int height = 1; height <= 5; height++) {
                    world.setBlockToAir(support.up(height));
                }
                int distance = offsetX * uphill.getXOffset() + offsetZ * uphill.getZOffset();
                if (distance >= 1 && (broad || distance == 1)) {
                    world.setBlockState(support.up(), source, 2);
                }
            }
        }
    }

    private static int gravel(WorldServer world, BlockPos position) {
        GravelSlabBlock slab = SandContent.gravelSlab;
        FakePlayer player = FakePlayerFactory.get(world,
                new GameProfile(UUID.fromString("d434d1b4-45fa-4932-b723-20be58984c74"), "GravelProbe"));
        player.capabilities.isCreativeMode = false;
        player.setPosition(position.getX() + 3, position.getY(), position.getZ() + 3);
        boolean instant = BlockFalling.fallInstantly;
        try {
            BlockFalling.fallInstantly = true;
            for (int meta = 0; meta < 2; meta++) {
                IBlockState state = slab.getStateFromMeta(meta);
                for (int height = -4; height <= 2; height++) {
                    world.setBlockToAir(position.up(height));
                }
                world.setBlockState(position.down(5), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(position, state, 2);
                slab.updateTick(world, position, state, new Random(1));
                RuntimeProbe.require(world.isAirBlock(position) && world.getBlockState(position.down(4)).equals(state),
                        "falling gravel preserves both horizontal halves");
                RuntimeProbe.require(slab.damageDropped(state) == 0 && slab.getMetaFromState(state) == meta,
                        "gravel drops one regular slab and preserves saved half metadata");
                world.setBlockState(position, state, 2);
                player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(slab, 2));
                ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
                EnumFacing face = meta == 1 ? EnumFacing.UP : EnumFacing.DOWN;
                held.getItem().onItemUse(player, world, position, EnumHand.MAIN_HAND, face, .5f, .5f, .5f);
                RuntimeProbe.require(world.getBlockState(position).getBlock() == Blocks.GRAVEL && held.getCount() == 1,
                        "two complementary gravel halves combine into vanilla gravel");
            }
        } finally {
            BlockFalling.fallInstantly = instant;
        }
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer ignored) { return true; }
        }, 3, 3);
        for (int slot = 0; slot < 3; slot++) {
            grid.setInventorySlotContents(slot, new ItemStack(Blocks.GRAVEL));
        }
        ItemStack cut = CraftingManager.findMatchingResult(grid, world);
        RuntimeProbe.require(ItemStack.areItemsEqual(cut, new ItemStack(slab)) && cut.getCount() == 6,
                "three gravel blocks craft six slabs");
        grid.clear();
        grid.setInventorySlotContents(0, new ItemStack(slab));
        grid.setInventorySlotContents(1, new ItemStack(slab));
        ItemStack whole = CraftingManager.findMatchingResult(grid, world);
        RuntimeProbe.require(ItemStack.areItemsEqual(whole, new ItemStack(Blocks.GRAVEL)) && whole.getCount() == 1,
                "two gravel slabs craft one full block");
        return 8;
    }

    private static int direction(EnumFacing facing) {
        return facing == EnumFacing.NORTH ? 0 : facing == EnumFacing.EAST ? 1 : facing == EnumFacing.SOUTH ? 2 : 3;
    }
}
