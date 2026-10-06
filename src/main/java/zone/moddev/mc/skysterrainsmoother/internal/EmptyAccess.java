package zone.moddev.mc.skysterrainsmoother.internal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Biomes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

final class EmptyAccess implements IBlockAccess {
    static final EmptyAccess INSTANCE = new EmptyAccess();
    public TileEntity getTileEntity(BlockPos pos) { return null; }
    public int getCombinedLight(BlockPos pos,int light) { return 0; }
    public IBlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }
    public boolean isAirBlock(BlockPos pos) { return true; }
    public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
    public int getStrongPower(BlockPos pos,EnumFacing side) { return 0; }
    public WorldType getWorldType() { return WorldType.DEFAULT; }
    public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback) { return false; }
}
