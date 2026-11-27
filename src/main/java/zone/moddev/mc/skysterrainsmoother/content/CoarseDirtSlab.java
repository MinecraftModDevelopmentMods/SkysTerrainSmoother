package zone.moddev.mc.skysterrainsmoother.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysterrainsmoother.SkysTerrainSmoother;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import zone.moddev.mc.skysgrassslabs.init.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/** Retains the old coarse-soil slab without turning it into spreading dirt. */
@Mod.EventBusSubscriber(modid = SkysTerrainSmoother.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CoarseDirtSlab extends SlabBlock {
    private static final MapCodec<CoarseDirtSlab> CODEC = simpleCodec(CoarseDirtSlab::new);

    private static final String ADDON = "skysterrainsmootherbop";
    public static final String BLOCK_ID = ADDON + ":dirt_slab_00";
    private static final ResourceLocation SAVED_BLOCK = ResourceLocation.parse(BLOCK_ID);
    private static final ResourceLocation SAVED_ITEM = ResourceLocation.fromNamespaceAndPath(ADDON, "coarse_dirt_slab");
    private static final DefaultArtifactVersion TRANSFER_VERSION = new DefaultArtifactVersion("0.1.4.121011");
    private static CoarseDirtSlab slab;
    private static Item item;

    private static boolean oldAddonOwnsSlab() {
        // Allow players to update Terrain Smoother before updating the optional add-on.
        return ModList.get().getModContainerById(ADDON)
                .map(mod -> mod.getModInfo().getVersion().compareTo(TRANSFER_VERSION) < 0)
                .orElse(false);
    }

    public static SlabBlock registeredBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(SAVED_BLOCK);
        if (!(block instanceof SlabBlock registered)) {
            throw new IllegalStateException("Missing the saved coarse dirt slab: " + SAVED_BLOCK);
        }
        return registered;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (oldAddonOwnsSlab()) {
            return;
        }
        event.register(ForgeRegistries.Keys.BLOCKS, registry -> {
            slab = new CoarseDirtSlab();
            registry.register(SAVED_BLOCK, slab);
        });
        event.register(ForgeRegistries.Keys.ITEMS, registry -> {
            registry.register(SAVED_BLOCK, new BlockItem(slab, new Item.Properties()));
            item = new BlockItem(slab, new Item.Properties()) {
                @Override
                public Component getName(ItemStack stack) {
                    return Component.translatable("pieces.skysterrainsmootherbop.slab",
                            Component.translatable(Blocks.COARSE_DIRT.getDescriptionId()));
                }
            };
            registry.register(SAVED_ITEM, item);
            BuildingPiecesApi.registerStateItems(slab, state -> item);
        });
    }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (item != null && CreativeModeTabs.BUILDING_BLOCKS.equals(event.getTabKey())) {
            event.accept(item);
        }
    }

    CoarseDirtSlab() {
        this(Properties.of().mapColor(MapColor.DIRT).strength(.5f).sound(SoundType.GRAVEL)
                .noOcclusion().randomTicks());
    }

    private CoarseDirtSlab(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SnowyDirtBlock.SNOWY, false));
    }

    @Override
    public MapCodec<CoarseDirtSlab> codec() {
        return CODEC;
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction action,
            boolean simulate) {
        if (action != ToolActions.SHOVEL_FLATTEN || !context.getItemInHand().canPerformAction(action)
                || state.getValue(WATERLOGGED)) {
            return super.getToolModifiedState(state, context, action, simulate);
        }
        return state.getValue(TYPE) == SlabType.DOUBLE
                ? Blocks.DIRT_PATH.defaultBlockState()
                : ModBlocks.PATH_SLAB.get().defaultBlockState()
                        .setValue(TYPE, state.getValue(TYPE)).setValue(WATERLOGGED, false);
    }

    @Override
    public Item asItem() {
        // Registry reloads can otherwise select the old palette's hidden recovery item.
        // Placement, picking and drops must keep using the playable coarse-dirt slab.
        return item == null ? super.asItem() : item;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SnowyDirtBlock.SNOWY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        if (state.getValue(TYPE) == SlabType.DOUBLE) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return state.setValue(SnowyDirtBlock.SNOWY, snowy(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState neighbour,
            LevelAccessor world, BlockPos position, BlockPos neighbourPosition) {
        return super.updateShape(state, facing, neighbour, world, position, neighbourPosition)
                .setValue(SnowyDirtBlock.SNOWY, snowy(world, position, facing, neighbour, neighbourPosition));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos position, RandomSource random) {
        tick(state, world, position, random);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos position, RandomSource random) {
        if (!world.isClientSide) {
            BlockState updated = state.setValue(SnowyDirtBlock.SNOWY, snowy(world, position));
            if (updated != state) {
                world.setBlock(position, updated, 2 | 16);
            }
        }
    }

    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter world, BlockPos position, Direction face,
            net.minecraftforge.common.IPlantable plant) {
        BlockState full = Blocks.COARSE_DIRT.defaultBlockState();
        return state.getValue(TYPE) == SlabType.TOP && !state.getValue(WATERLOGGED) && face == Direction.UP
                && full.getBlock().canSustainPlant(full, world, position, face, plant);
    }

    private static boolean snowy(LevelAccessor world, BlockPos position) {
        return snowy(world, position, Direction.UP, world.getBlockState(position.above()), position.above());
    }

    private static boolean snowy(LevelAccessor world, BlockPos position, Direction side, BlockState neighbour,
            BlockPos neighbourPosition) {
        // Share the dirt slab's snow calculation without giving coarse soil a grass-spreading tick.
        return ModBlocks.DIRT_SLAB.get().defaultBlockState()
                .updateShape(side, neighbour, world, position, neighbourPosition).getValue(SnowyDirtBlock.SNOWY);
    }
}
