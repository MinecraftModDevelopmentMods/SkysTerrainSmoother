package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingContainer;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;

/** Exercise the registered sand items, native falling entities and real recipe manager. */
final class SandProbe {
    static int run(ServerLevel world) {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(
                UUID.fromString("14581a55-6ff1-4c98-834f-3f41057e395b"), "SandProbe"));
        player.getAbilities().instabuild = false;
        int checks = 0;
        for (int colour = 0; colour < 2; colour++) {
            SandSlabBlock slab = colour == 0 ? SandContent.slab : SandContent.redSlab;
            BlockPos position = new BlockPos(1200 + colour * 8, 220, 1200);
            player.setPos(position.getX() + 4, 220, position.getZ() + 4);
            world.getChunk(position);
            for (int orientation = 0; orientation < 2; orientation++) {
                BlockState state = SandContent.legacyState(colour * 2 + orientation);
                for (int height = 215; height <= 224; height++) {
                    ProbeWorld.clear(world, new BlockPos(position.getX(), height, position.getZ()));
                }
                world.setBlock(position.below(5), Blocks.STONE.defaultBlockState(), 2);
                world.setBlock(position, state, 2);
                slab.tick(state, world, position, new Random(1));
                List<FallingBlockEntity> falling = world.getEntitiesOfClass(FallingBlockEntity.class,
                        new AABB(position).inflate(1));
                falling.removeIf(entity -> !entity.isAlive());
                RuntimeProbe.require(falling.size() == 1, "Sand uses one native falling entity");
                FallingBlockEntity entity = falling.get(0);
                for (int tick = 0; tick < 100 && entity.isAlive(); tick++) {
                    entity.tick();
                }
                RuntimeProbe.require(!entity.isAlive() && world.getBlockState(position.below(4)).equals(state),
                        "Both sand colours and halves survive falling");
                world.setBlock(position, state, 2);
                slab.tick(state, world, position, new Random(1));
                falling = world.getEntitiesOfClass(FallingBlockEntity.class, new AABB(position).inflate(1));
                falling.removeIf(candidate -> !candidate.isAlive());
                RuntimeProbe.require(falling.size() == 1, "Failed-fall fixture has one live entity");
                entity = falling.get(0);
                entity.tick();
                entity.time = 601;
                entity.tick();
                List<ItemEntity> drops = world.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2));
                RuntimeProbe.require(drops.stream().anyMatch(drop -> drop.getItem().getItem() == slab.asItem()),
                        "An unsuccessful fall drops the correct sand colour");
                drops.forEach(ItemEntity::discard);
                checks += 4;
            }
            world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
            ProbeWorld.clear(world, position);
            ProbeWorld.clear(world, position.above());
            ItemStack held = new ItemStack(slab, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            RuntimeProbe.require(use(player, position.below(), Direction.UP, 1) == InteractionResult.CONSUME,
                    "Bottom sand placement succeeds");
            RuntimeProbe.require(world.getBlockState(position).equals(SandContent.legacyState(colour * 2 + 1)),
                    "The held colour places a bottom half");
            RuntimeProbe.require(use(player, position, Direction.UP, .5) == InteractionResult.SUCCESS
                    && world.getBlockState(position).equals(slab.fullState()), "Matching halves become ordinary sand");
            RuntimeProbe.require(held.isEmpty(), "Joining consumes exactly two slab items");
            CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, 0) {
                public boolean stillValid(Player player) {
                    return true;
                }
            }, 2, 2);
            grid.setItem(0, new ItemStack(slab));
            grid.setItem(1, new ItemStack(slab));
            ItemStack result = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, world)
                    .orElseThrow(() -> new AssertionError("Sand recombination recipe missing")).assemble(grid);
            RuntimeProbe.require(ItemStack.matches(new ItemStack(slab.fullState().getBlock()), result),
                    "Two sand slabs craft the matching full block");
            checks += 5;
        }
        zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother.logger.info("TERRAIN_SMOOTHER_SAND_PASS checks=" + checks);
        return checks;
    }

    private static InteractionResult use(FakePlayer player, BlockPos position, Direction face, double height) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(position.getX() + .5, position.getY() + height,
                        position.getZ() + .5), face, position, false)));
    }

    private SandProbe() {
    }
}
