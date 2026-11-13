package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.util.RandomSource;
import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;

/** Exercise falling terrain pieces through native entities, placement and crafting. */
final class SandProbe {
    static int run(ServerLevel world) {
        ServerPlayer player = ProbeItems.player(world, new GameProfile(
                UUID.fromString("14581a55-6ff1-4c98-834f-3f41057e395b"), "SandProbe"));
        player.getAbilities().instabuild = false;
        int checks = 0;
        SandSlabBlock[] slabs = {SandContent.slab, SandContent.redSlab, SandContent.gravelSlab};
        for (int materialIndex = 0; materialIndex < slabs.length; materialIndex++) {
            SandSlabBlock slab = slabs[materialIndex];
            BlockPos position = new BlockPos(1200 + materialIndex * 8, 220, 1200);
            player.setPos(position.getX() + 4, 220, position.getZ() + 4);
            world.getChunk(position);
            for (int orientation = 0; orientation < 2; orientation++) {
                BlockState state = slab.defaultBlockState().setValue(SandSlabBlock.TYPE, orientation == 0
                        ? net.minecraft.world.level.block.state.properties.SlabType.TOP
                        : net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
                var mined = net.minecraft.world.level.block.Block.getDrops(state, world, position, null);
                RuntimeProbe.require(mined.size() == 1 && mined.get(0).is(slab.asItem()) && mined.get(0).getCount() == 1,
                        "Mining either half drops its matching slab rather than a full block or flint");
                for (int height = 215; height <= 224; height++) {
                    ProbeWorld.clear(world, new BlockPos(position.getX(), height, position.getZ()));
                }
                world.setBlock(position.below(5), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(position, state, 2);
                slab.tick(state, world, position, RandomSource.create(1));
                List<FallingBlockEntity> falling = world.getEntitiesOfClass(FallingBlockEntity.class,
                        new AABB(position).inflate(1));
                falling.removeIf(entity -> !entity.isAlive());
                RuntimeProbe.require(falling.size() == 1, "A falling slab uses one native entity");
                FallingBlockEntity entity = falling.get(0);
                for (int tick = 0; tick < 100 && entity.isAlive(); tick++) {
                    entity.tick();
                }
                RuntimeProbe.require(!entity.isAlive() && world.getBlockState(position.below(4)).equals(state),
                        "Both halves keep their material when falling");
                world.setBlock(position, state, 2);
                slab.tick(state, world, position, RandomSource.create(1));
                falling = world.getEntitiesOfClass(FallingBlockEntity.class, new AABB(position).inflate(1));
                falling.removeIf(candidate -> !candidate.isAlive());
                RuntimeProbe.require(falling.size() == 1, "Failed-fall fixture has one live entity");
                entity = falling.get(0);
                entity.tick();
                entity.time = 601;
                entity.tick();
                List<ItemEntity> drops = world.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2));
                RuntimeProbe.require(drops.stream().anyMatch(drop -> drop.getItem().getItem() == slab.asItem()),
                        "An unsuccessful fall drops the matching slab");
                drops.forEach(ItemEntity::discard);
                checks += 5;
            }
            world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
            ProbeWorld.clear(world, position);
            ProbeWorld.clear(world, position.above());
            ItemStack held = new ItemStack(slab, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            RuntimeProbe.require(use(player, position.below(), Direction.UP, 1).consumesAction(),
                    "Bottom slab placement succeeds");
            RuntimeProbe.require(world.getBlockState(position).equals(slab.defaultBlockState()),
                    "The held material places a bottom half");
            RuntimeProbe.require(use(player, position, Direction.UP, .5) == InteractionResult.SUCCESS
                    && world.getBlockState(position).equals(slab.fullState()), "Matching halves become their native full block");
            RuntimeProbe.require(held.isEmpty(), "Joining consumes exactly two slab items");
            CraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
                @Override
                public ItemStack quickMoveStack(Player player, int slot) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(Player player) {
                    return true;
                }
            }, 3, 3);
            grid.setItem(0, new ItemStack(slab));
            grid.setItem(1, new ItemStack(slab));
            ItemStack result = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), world)
                    .orElseThrow(() -> new AssertionError("Slab recombination recipe missing")).value().assemble(grid.asCraftInput(), world.registryAccess());
            RuntimeProbe.require(ItemStack.matches(new ItemStack(slab.fullState().getBlock()), result),
                    "Two slabs craft the matching full block");
            grid.clearContent();
            for (int slot = 0; slot < 3; slot++) {
                grid.setItem(slot, new ItemStack(slab.fullState().getBlock()));
            }
            ItemStack cut = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), world)
                    .orElseThrow(() -> new AssertionError("Falling slab cutting recipe missing")).value().assemble(grid.asCraftInput(), world.registryAccess());
            RuntimeProbe.require(ItemStack.matches(new ItemStack(slab, 6), cut),
                    "Three full blocks cut into six matching slabs");
            checks += 6;
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_SAND_PASS checks=" + checks);
        return checks + mixedShoreTransitions(world) + gravelPersistence(world);
    }

    private static int mixedShoreTransitions(ServerLevel world) {
        BlockPos floor = new BlockPos(1304, 200, 1304);
        var owner = world.getChunkAt(floor);
        SandSlabBlock[] pieces = {SandContent.slab, SandContent.redSlab, SandContent.gravelSlab};
        int checks = 0;
        for (int level = 1; level <= 3; level++) {
            for (SandSlabBlock lower : pieces) {
                for (SandSlabBlock higher : pieces) {
                    for (boolean snow : new boolean[] {false, true}) {
                        for (int blockZ = -3; blockZ <= 3; blockZ++) {
                            for (int blockX = -3; blockX <= 3; blockX++) {
                                for (int height = 0; height <= 4; height++) {
                                    ProbeWorld.clear(world, floor.offset(blockX, height, blockZ));
                                }
                                world.setBlock(floor.offset(blockX, 0, blockZ), lower.fullState(), 2);
                            }
                        }
                        world.setBlock(floor.north().above(), higher.fullState(), 2);
                        if (snow) {
                            world.setBlock(floor.above(), Blocks.SNOW.defaultBlockState(), 2);
                            world.setBlock(floor.north().above(2), Blocks.SNOW.defaultBlockState(), 2);
                        }
                        ProbeWorld.rebuildHeights(owner);
                        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.engine.smooth(world, owner, level);
                        RuntimeProbe.require(world.getBlockState(floor.above()).equals(lower.defaultBlockState()),
                                "Mixed sand/gravel shores use the lower material at level " + level + ", snow=" + snow);
                        RuntimeProbe.require(world.getBlockState(floor).equals(lower.fullState()),
                                "Smoothing leaves the shore's original support material alone");
                        checks += 2;
                    }
                }
            }
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info(
                "TERRAIN_MIXED_SHORES_PASS checks={} levels=3 snow=true", checks);
        return checks;
    }

    private static int gravelPersistence(ServerLevel world) {
        boolean reload = System.getProperty("skysterrainsmoother.integrationPhase", "fresh").contains("reload");
        int index = 0;
        for (var half : new net.minecraft.world.level.block.state.properties.SlabType[] {
                net.minecraft.world.level.block.state.properties.SlabType.BOTTOM,
                net.minecraft.world.level.block.state.properties.SlabType.TOP }) {
            for (boolean wet : new boolean[] {false, true}) {
                BlockPos position = new BlockPos(1256 + index * 2, 210, 1256);
                BlockState expected = SandContent.gravelSlab.defaultBlockState()
                        .setValue(SandSlabBlock.TYPE, half).setValue(SandSlabBlock.WATERLOGGED, wet);
                if (!reload) {
                    world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
                    world.setBlock(position, expected, 2);
                }
                RuntimeProbe.require(world.getBlockState(position).equals(expected),
                        "Gravel keeps both halves and waterlogging across a real save and reload");
                index++;
            }
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info(
                "TERRAIN_GRAVEL_PERSISTENCE_PASS checks={} reload={}", index, reload);
        return index;
    }

    private static InteractionResult use(ServerPlayer player, BlockPos position, Direction face, double height) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(position.getX() + .5, position.getY() + height,
                        position.getZ() + .5), face, position, false)));
    }

    private SandProbe() {
    }
}
