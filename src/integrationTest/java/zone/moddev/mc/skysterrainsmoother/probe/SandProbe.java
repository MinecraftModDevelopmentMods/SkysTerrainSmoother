package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;
import zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock;

/** Exercise the registered sand items, native falling entities and real recipe manager. */
final class SandProbe {
    static int run(ServerWorld world) {
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(
                UUID.fromString("14581a55-6ff1-4c98-834f-3f41057e395b"), "SandProbe"));
        player.abilities.instabuild = false;
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
                        new AxisAlignedBB(position).inflate(1));
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
                falling = world.getEntitiesOfClass(FallingBlockEntity.class, new AxisAlignedBB(position).inflate(1));
                falling.removeIf(candidate -> !candidate.isAlive());
                RuntimeProbe.require(falling.size() == 1, "Failed-fall fixture has one live entity");
                entity = falling.get(0);
                entity.tick();
                entity.time = 601;
                entity.tick();
                List<ItemEntity> drops = world.getEntitiesOfClass(ItemEntity.class, new AxisAlignedBB(position).inflate(2));
                RuntimeProbe.require(drops.stream().anyMatch(drop -> drop.getItem().getItem() == slab.asItem()),
                        "An unsuccessful fall drops the correct sand colour");
                drops.forEach(ItemEntity::remove);
                checks += 4;
            }
            world.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 2);
            ProbeWorld.clear(world, position);
            ProbeWorld.clear(world, position.above());
            ItemStack held = new ItemStack(slab, 2);
            player.setItemInHand(Hand.MAIN_HAND, held);
            RuntimeProbe.require(use(player, position.below(), Direction.UP, 1) == ActionResultType.CONSUME,
                    "Bottom sand placement succeeds");
            RuntimeProbe.require(world.getBlockState(position).equals(SandContent.legacyState(colour * 2 + 1)),
                    "The held colour places a bottom half");
            RuntimeProbe.require(use(player, position, Direction.UP, .5) == ActionResultType.SUCCESS
                    && world.getBlockState(position).equals(slab.fullState()), "Matching halves become ordinary sand");
            RuntimeProbe.require(held.isEmpty(), "Joining consumes exactly two slab items");
            CraftingInventory grid = new CraftingInventory(new Container(null, 0) {
                public boolean stillValid(PlayerEntity player) {
                    return true;
                }
            }, 2, 2);
            grid.setItem(0, new ItemStack(slab));
            grid.setItem(1, new ItemStack(slab));
            ItemStack result = world.getRecipeManager().getRecipeFor(IRecipeType.CRAFTING, grid, world)
                    .orElseThrow(() -> new AssertionError("Sand recombination recipe missing")).assemble(grid);
            RuntimeProbe.require(ItemStack.matches(new ItemStack(slab.fullState().getBlock()), result),
                    "Two sand slabs craft the matching full block");
            checks += 5;
        }
        System.out.println("TERRAIN_SMOOTHER_SAND_PASS checks=" + checks);
        return checks;
    }

    private static ActionResultType use(FakePlayer player, BlockPos position, Direction face, double height) {
        return player.getMainHandItem().useOn(new ItemUseContext(player, Hand.MAIN_HAND,
                new BlockRayTraceResult(new Vector3d(position.getX() + .5, position.getY() + height,
                        position.getZ() + .5), face, position, false)));
    }

    private SandProbe() {
    }
}
