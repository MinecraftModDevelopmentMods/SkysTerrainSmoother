package zone.moddev.mc.skysterrainsmoother.probe;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowContent;
import zone.moddev.mc.skysterrainsmoother.content.PowderSnowPieceBlock;

/** Real entity, item-use and recipe-manager checks for non-solid snow pieces. */
final class PowderSnowProbe {
    static int run(ServerLevel world) {
        BlockPos position = new BlockPos(4964, 240, 4964);
        world.getChunkAt(position);
        int checks = 0;
        for (var kind : PowderSnowPieceBlock.Kind.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (Half half : Half.values()) {
                    Pig pig = new Pig(EntityType.PIG, world);
                    var state = PowderSnowContent.state(kind, half, facing);
                    var block = (PowderSnowPieceBlock) state.getBlock();
                    world.setBlock(position, state, 2);
                    var outline = state.getShape(world, position);
                    var bounds = outline.bounds();
                    double x = position.getX() + (bounds.minX + bounds.maxX) / 2;
                    double z = position.getZ() + (bounds.minZ + bounds.maxZ) / 2;
                    pig.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    pig.fallDistance = 0;
                    pig.setPos(x, position.getY() + bounds.maxY + .01, z);
                    require(state.getCollisionShape(world, position, CollisionContext.of(pig)).isEmpty(),
                            "Bare feet sink through every powder-snow orientation");
                    pig.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                    require(!state.getCollisionShape(world, position, CollisionContext.of(pig)).isEmpty(),
                            "Leather boots stand on the actual partial surface");
                    pig.setShiftKeyDown(true);
                    require(state.getCollisionShape(world, position, CollisionContext.of(pig)).isEmpty(),
                            "Descending in leather boots allows sinking");
                    pig.setShiftKeyDown(false);
                    pig.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    pig.fallDistance = 4;
                    require(state.getCollisionShape(world, position, CollisionContext.of(pig)).bounds().maxY
                                    < bounds.maxY,
                            "Long falls receive native-style soft landing collision");
                    pig.fallDistance = 0;
                    pig.setPos(x, position.getY() + bounds.minY + .05, z);
                    pig.setIsInPowderSnow(false);
                    pig.setTicksFrozen(0);
                    state.entityInside(world, position, pig);
                    require(pig.isInPowderSnow, "The occupied volume applies native powder-snow freezing");
                    double before = pig.getX();
                    pig.move(MoverType.SELF, new Vec3(.1, 0, 0));
                    require(Math.abs(pig.getX() - before - .09) < .00001,
                            "Powder snow keeps its native movement slowdown");
                    pig.aiStep();
                    require(pig.getTicksFrozen() > 0, "A real living-entity tick accumulates freezing");
                    if (kind != PowderSnowPieceBlock.Kind.SLAB) {
                        double emptyX = bounds.minX == 0 && bounds.maxX == .5 ? .75 : .25;
                        double emptyZ = bounds.minZ == 0 && bounds.maxZ == .5 ? .75 : .25;
                        if (kind == PowderSnowPieceBlock.Kind.STEP && bounds.maxX - bounds.minX == 1) {
                            emptyX = .5;
                        }
                        pig.setPos(position.getX() + emptyX, position.getY() + bounds.minY + .05,
                                position.getZ() + emptyZ);
                        pig.setBoundingBox(new AABB(pig.getX() - .025, pig.getY(), pig.getZ() - .025,
                                pig.getX() + .025, pig.getY() + .05, pig.getZ() + .025));
                        pig.setIsInPowderSnow(false);
                        state.entityInside(world, position, pig);
                        require(!pig.isInPowderSnow, "Empty parts of a step or corner do not freeze entities");
                    }
                    require(block.pickupBlock(null, world, position, state).isEmpty()
                                    && world.getBlockState(position).equals(state),
                            "A partial block cannot be scooped into a full powder bucket");
                    var drops = Block.getDrops(state, world, position, null);
                    require(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                            "Every orientation drops its normal piece when broken by hand");
                    checks += 9;
                }
            }
        }
        checks += recipes(world);
        var player = ProbeItems.player(world, new GameProfile(
                UUID.fromString("90def6bd-e26a-4091-8fc9-36a6eed97eb2"), "PowderSnowProbe"));
        player.getAbilities().instabuild = false;
        player.getAbilities().mayBuild = true;
        player.setPos(position.getX() + 3, position.getY(), position.getZ() + 3);
        var slab = PowderSnowContent.state(PowderSnowPieceBlock.Kind.SLAB, Half.BOTTOM, Direction.NORTH);
        world.setBlock(position, slab, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(slab.getBlock(), 2));
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atLowerCornerOf(position).add(.5, .5, .5), Direction.UP, position, false));
        require(player.getMainHandItem().useOn(context).consumesAction()
                        && world.getBlockState(position).is(Blocks.POWDER_SNOW)
                        && player.getMainHandItem().getCount() == 1,
                "Two placed halves join into vanilla powder snow without creating solid snow");
        checks++;
        var corner = PowderSnowContent.state(PowderSnowPieceBlock.Kind.CORNER, Half.BOTTOM, Direction.NORTH);
        world.setBlock(position, corner, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(corner.getBlock(), 2));
        context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atLowerCornerOf(position).add(.5, .25, .25), Direction.EAST, position, false));
        require(player.getMainHandItem().useOn(context).consumesAction()
                        && world.getBlockState(position).getBlock() instanceof PowderSnowPieceBlock joined
                        && joined.kind() == PowderSnowPieceBlock.Kind.STEP,
                "Adjacent powder corners join into a matching step");
        var step = PowderSnowContent.state(PowderSnowPieceBlock.Kind.STEP, Half.BOTTOM, Direction.NORTH);
        world.setBlock(position, step, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(step.getBlock(), 2));
        context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atLowerCornerOf(position).add(.5, .25, .5), Direction.SOUTH, position, false));
        require(player.getMainHandItem().useOn(context).consumesAction()
                        && world.getBlockState(position).getBlock() instanceof PowderSnowPieceBlock joined
                        && joined.kind() == PowderSnowPieceBlock.Kind.SLAB,
                "Matching powder steps join into a slab");
        checks += 2;
        SkysTerrainSmoother.logger.info("TERRAIN_POWDER_SNOW_PASS checks={} sinking=true freezing=true boots=true"
                + " partial_pickup_blocked=true drops=true volume=true", checks);
        return checks;
    }

    private static int recipes(ServerLevel world) {
        var slab = PowderSnowContent.state(PowderSnowPieceBlock.Kind.SLAB, Half.BOTTOM, Direction.NORTH).getBlock().asItem();
        var step = PowderSnowContent.state(PowderSnowPieceBlock.Kind.STEP, Half.BOTTOM, Direction.NORTH).getBlock().asItem();
        var corner = PowderSnowContent.state(PowderSnowPieceBlock.Kind.CORNER, Half.BOTTOM, Direction.NORTH).getBlock().asItem();
        CraftingInput bucket = CraftingInput.of(1, 1, List.of(new ItemStack(Items.POWDER_SNOW_BUCKET)));
        var cut = world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, bucket, world);
        require(cut.size() == 1, "One powder bucket has one cutting recipe");
        require(ItemStack.matches(new ItemStack(slab, 2), cut.getFirst().value().assemble(bucket, world.registryAccess()))
                        && cut.getFirst().value().getRemainingItems(bucket).getFirst().is(Items.BUCKET),
                "One full powder bucket makes two slabs and returns one empty bucket");
        craft(world, 2, 2, List.of(new ItemStack(slab), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(slab)), step, 4);
        craft(world, 2, 1, List.of(new ItemStack(step), new ItemStack(step)), corner, 4);
        craft(world, 1, 2, List.of(new ItemStack(corner), new ItemStack(corner)), step, 1);
        craft(world, 1, 2, List.of(new ItemStack(step), new ItemStack(step)), slab, 1);
        craft(world, 2, 2, List.of(new ItemStack(slab), new ItemStack(slab), new ItemStack(Items.BUCKET), ItemStack.EMPTY),
                Items.POWDER_SNOW_BUCKET, 1);
        var noBucket = CraftingInput.of(2, 1, List.of(new ItemStack(slab), new ItemStack(slab)));
        require(world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, noBucket, world).isEmpty(),
                "Rebuilding powder snow cannot manufacture an empty bucket");
        return 8;
    }

    private static void craft(ServerLevel world, int width, int height, List<ItemStack> inputs,
            Item output, int count) {
        var input = CraftingInput.of(width, height, inputs);
        var matches = world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, world);
        require(matches.size() == 1 && ItemStack.matches(new ItemStack(output, count),
                matches.getFirst().value().assemble(input, world.registryAccess())),
                "Powder snow has one volume-conserving crafting result: " + inputs);
    }

    private static void require(boolean condition, String message) {
        RuntimeProbe.require(condition, message);
    }
}
