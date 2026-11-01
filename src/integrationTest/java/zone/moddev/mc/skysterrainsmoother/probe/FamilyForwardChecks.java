package zone.moddev.mc.skysterrainsmoother.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowyDirtBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.state.properties.SlabType;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;
import zone.moddev.mc.skysgrassslabs.world.ModWorldState;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import zone.moddev.mc.skysterrainsmoother.content.SandContent;

/** Reads genuine old chunks, inventories and entities through the native loaders. */
final class FamilyForwardChecks {
    static int run(ServerWorld world) throws Exception {
        JsonObject fixture;
        try (InputStreamReader input = new InputStreamReader(new FileInputStream(new File("source-family-fixture.json")),
                StandardCharsets.UTF_8)) {
            fixture = new JsonParser().parse(input).getAsJsonObject();
        }
        int checked = 0;
        for (JsonElement element : fixture.getAsJsonArray("blocks")) {
            JsonObject saved = element.getAsJsonObject();
            BlockPos position = position(saved);
            BlockState actual = world.getBlockState(position);
            BlockState expected = expectedState(saved);
            RuntimeProbe.require(withoutSnow(actual).equals(withoutSnow(expected)),
                    "Forward block identity and orientation at " + position + ": " + actual + " expected " + expected);
            RuntimeProbe.require(actual.getFluidState().isEmpty(), "Pre-waterlogging pieces begin dry");
            if (saved.has("snowy") && actual.hasProperty(SnowyDirtBlock.SNOWY)) {
                BlockState refreshed = actual.getBlock().updateShape(actual, net.minecraft.util.Direction.EAST,
                        world.getBlockState(position.east()), world, position, position.east());
                RuntimeProbe.require(refreshed.getValue(SnowyDirtBlock.SNOWY) == saved.get("snowy").getAsBoolean(),
                        "Saved snow neighbours still produce the same derived snow appearance: " + position);
                checked++;
            }
            checked++;
        }
        if (fixture.has("native_sand_states")) {
            int wet = 0;

            RuntimeProbe.require(fixture.getAsJsonArray("native_sand_states").size() == 8,
                    "The named source includes every sand colour, half and wet state");
            for (JsonElement element : fixture.getAsJsonArray("native_sand_states")) {
                JsonObject saved = element.getAsJsonObject();
                BlockPos position = position(saved);
                BlockState expected = net.minecraft.nbt.NBTUtil.readBlockState(
                        JsonToNBT.parseTag(saved.get("state").getAsString()));
                BlockState actual = world.getBlockState(position);

                RuntimeProbe.require(actual.equals(expected),
                        "Named sand retains its colour, orientation and water: " + position + " / " + actual);
                if (!actual.getFluidState().isEmpty()) {
                    wet++;
                }
                checked++;
            }
            RuntimeProbe.require(wet == 4, "All four waterlogged source sand states survive");
            System.out.println("TERRAIN_NAMED_SAND_FORWARD_PASS states=8 wet=4");
        }
        int stackIndex = 0;
        for (JsonElement element : fixture.getAsJsonArray("stacks")) {
            JsonObject saved = element.getAsJsonObject();
            ChestTileEntity chest = (ChestTileEntity) world.getBlockEntity(position(saved));
            RuntimeProbe.require(chest != null, "A saved source chest survives");
            ItemStack actual = chest.getItem(saved.get("slot").getAsInt());
            CompoundNBT original = JsonToNBT.parseTag(saved.get("item").getAsString());
            JsonObject block = fixture.getAsJsonArray("blocks").get(stackIndex++)
                    .getAsJsonObject();
            ItemStack expected = block.has("material")
                    ? Pieces.legacyItem(block.get("id").getAsString(), block.get("meta").getAsInt(), original.getInt("Count"))
                    : Pieces.stack(expectedState(block), original.getInt("Count"));
            RuntimeProbe.require(actual.getItem() == expected.getItem() && actual.getCount() == expected.getCount(),
                    "Saved piece stack keeps material and count: " + original + " -> " + actual);
            RuntimeProbe.require(actual.hasTag() && actual.getTag().equals(original.getCompound("tag")),
                    "Piece custom NBT survives exactly");
            checked++;
        }
        for (JsonElement element : fixture.getAsJsonArray("entities")) {
            JsonObject saved = element.getAsJsonObject();
            Entity entity = world.getEntity(UUID.fromString(saved.get("uuid").getAsString()));
            RuntimeProbe.require(entity != null, "The original item entity UUID survives");
            ItemStack actual = entity instanceof ItemEntity ? ((ItemEntity) entity).getItem()
                    : ((ItemFrameEntity) entity).getItem();
            CompoundNBT original = JsonToNBT.parseTag(saved.get("item").getAsString());
            if (saved.get("kind").getAsString().equals("item")) {
                checkSandStack(actual, original.getInt("Damage"), original.getInt("Count"));
                RuntimeProbe.require(actual.hasTag() && actual.getTag().equals(original.getCompound("tag")),
                        "Dropped sand slab NBT survives exactly");
            } else {
                checkShovel(actual);
            }
            checked++;
        }
        FakePlayer player = FakePlayerFactory.get(world,
                new GameProfile(UUID.fromString(fixture.get("player").getAsString()), "SkyFixture"));
        world.getServer().getPlayerList().load(player);
        checkShovel(player.inventory.getItem(0));
        checkShovel(player.getEnderChestInventory().getItem(0));
        checkSandStack(player.inventory.getItem(1), 3, 23);
        checkSandStack(player.getEnderChestInventory().getItem(1), 3, 23);
        ItemStack box = player.inventory.getItem(2);
        RuntimeProbe.require(!box.isEmpty() && box.hasTag(), "The carried shulker box survives");
        CompoundNBT nested = box.getTag().getCompound("BlockEntityTag").getList("Items", 10).getCompound(0);
        checkSandStack(ItemStack.of(nested), 3, 23);
        try (java.io.OutputStream output = java.nio.file.Files.newOutputStream(
                world.getServer().getWorldPath(net.minecraft.world.storage.FolderName.PLAYER_DATA_DIR)
                        .resolve(player.getUUID().toString() + ".dat"))) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(player.saveWithoutId(new CompoundNBT()), output);
        }
        CompoundNBT grass = ModWorldState.get(world).save(new CompoundNBT());
        RuntimeProbe.require(grass.getInt("schema_version") == 1 && grass.getLong("migrated_chunks") == 1
                        && grass.getLong("migrated_grass_blocks") == 24
                        && grass.getLong("migrated_dirt_blocks") == 17
                        && grass.getLong("migrated_grass_items") == 19
                        && grass.getLong("migrated_dirt_items") == 23,
                "Grass Slabs schema and migration counters survive unchanged");
        PiecesWorldState pieces = PiecesWorldState.get(world);
        RuntimeProbe.require(pieces.blocks == 29 && pieces.items == 31 && pieces.chunks == 7,
                "Building Pieces counters remain unchanged");
        checkNoRetrogen(fixture);
        System.out.println("SKY_FAMILY_FORWARD_PASS blocks=" + fixture.getAsJsonArray("blocks").size()
                + " stacks=" + fixture.getAsJsonArray("stacks").size()
                + " entities=" + fixture.getAsJsonArray("entities").size() + " players=1");
        return checked + 7;
    }

    @SuppressWarnings("unchecked")
    private static void checkNoRetrogen(JsonObject fixture) throws ReflectiveOperationException {
        java.lang.reflect.Field indexField = zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine.class
                .getDeclaredField("existing");
        indexField.setAccessible(true);
        zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex index =
                (zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex) indexField.get(SkysTerrainSmoother.engine);
        java.util.Set<Long> savedOwners = new java.util.HashSet<>();
        for (String group : new String[] { "blocks", "native_sand_states" }) {
            if (!fixture.has(group)) continue;
            for (JsonElement element : fixture.getAsJsonArray(group)) {
                BlockPos position = position(element.getAsJsonObject());
                int chunkX = position.getX() >> 4;
                int chunkZ = position.getZ() >> 4;
                RuntimeProbe.require(index.contains(chunkX, chunkZ), "The original fixture owner was indexed before generation");
                savedOwners.add(zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex.key(chunkX, chunkZ));
            }
        }
        RuntimeProbe.require(!savedOwners.isEmpty(), "The retrogen check covers saved fixture chunks");
        // A provider can generate new neighbours while opening an old world.
        // Inspect actual processed owners instead of requiring a global zero.
        java.lang.reflect.Field processedField = zone.moddev.mc.skysterrainsmoother.internal.SmoothingEngine.class
                .getDeclaredField("processed");
        processedField.setAccessible(true);
        java.util.Set<Long> processed = (java.util.Set<Long>) processedField.get(SkysTerrainSmoother.engine);
        requireOnlyNewOwners(index, processed);
        boolean rejectedSavedOwner = false;
        try {
            requireOnlyNewOwners(index, java.util.Collections.singleton(savedOwners.iterator().next()));
        } catch (AssertionError expected) {
            rejectedSavedOwner = true;
        }
        RuntimeProbe.require(rejectedSavedOwner, "The retrogen guard rejects a saved owner in its negative control");
        System.out.println("TERRAIN_NO_RETROGEN_PASS saved_owners=" + savedOwners.size()
                + " generated_new_owners=" + processed.size() + " negative_control=true");
    }

    private static void requireOnlyNewOwners(
            zone.moddev.mc.skysterrainsmoother.internal.ExistingChunkIndex index, Iterable<Long> owners) {
        for (long owner : owners) {
            RuntimeProbe.require(!index.contains((int) (owner >> 32), (int) owner),
                    "A previously saved owner must never enter the smoothing pass");
        }
    }

    private static BlockState expectedState(JsonObject saved) {
        if (saved.has("material")) {
            return Pieces.state(saved.get("material").getAsString(), Shape.valueOf(saved.get("shape").getAsString()),
                    saved.get("orientation").getAsInt());
        }
        String id = saved.get("id").getAsString();
        int metadata = saved.get("meta").getAsInt();
        if (id.equals("skysterrainsmoother:sand_slab")) {
            return (metadata < 2 ? SandContent.slab : SandContent.redSlab).defaultBlockState()
                    .setValue(zone.moddev.mc.skysterrainsmoother.content.SandSlabBlock.TYPE, (metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM);
        }
        BlockState state = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id)).defaultBlockState();
        return state.hasProperty(SlabBlock.TYPE)
                ? state.setValue(SlabBlock.TYPE, (metadata & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM) : state;
    }

    private static void checkSandStack(ItemStack stack, int metadata, int count) {
        RuntimeProbe.require(!stack.isEmpty() && stack.getCount() == count
                        && stack.getItem() == (metadata < 2 ? SandContent.slab : SandContent.redSlab).asItem(),
                "Sand slab colour and count survive in every inventory location: " + stack);
    }

    private static void checkShovel(ItemStack stack) {
        RuntimeProbe.require(stack.getItem() == net.minecraft.item.Items.DIAMOND_SHOVEL && stack.getDamageValue() == 17
                        && stack.getHoverName().getString().equals("A saved slope shovel")
                        && net.minecraft.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                                net.minecraft.enchantment.Enchantments.BLOCK_EFFICIENCY, stack) == 3
                        && stack.hasTag() && stack.getTag().getString("family_fixture").equals("Keep the complete tool"),
                "The saved shovel retains damage, name, enchantment and custom data");
    }

    private static BlockState withoutSnow(BlockState state) {
        return state.hasProperty(SnowyDirtBlock.SNOWY) ? state.setValue(SnowyDirtBlock.SNOWY, false) : state;
    }

    private static BlockPos position(JsonObject value) {
        return new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt());
    }

    private FamilyForwardChecks() {
    }
}
