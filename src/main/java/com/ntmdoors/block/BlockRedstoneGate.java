package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Redstone Gate. It is off when placed, turns on while it receives redstone power, and emits a
 * full-strength signal while on. The binding logic that lets keypads, scanners and safes drive it
 * is deliberately not implemented yet.
 */
public class BlockRedstoneGate extends Block {

    public static final PropertyBool POWERED = PropertyBool.create("powered");

    /**
     * Set while the gate asks the world whether it is powered. The gate's own output must not count
     * as an input, otherwise a normal cube next to it feeds the signal straight back and latches it
     * on after the lever is removed.
     */
    private static final ThreadLocal<Boolean> PROBING_POWER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public BlockRedstoneGate() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "redstone_gate");
        setTranslationKey(NTMDoors.MODID + ".redstone_gate");
        setCreativeTab(NTMDoors.TAB);
        setHardness(3F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(POWERED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, POWERED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(POWERED, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(POWERED) ? 1 : 0;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) updatePowered(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) return;
        IBlockState current = world.getBlockState(pos);
        if (current.getBlock() == this) updatePowered(world, pos, current);
    }

    private void updatePowered(World world, BlockPos pos, IBlockState state) {
        boolean powered = isReceivingPower(world, pos);
        if (powered == state.getValue(POWERED)) return;
        world.setBlockState(pos, state.withProperty(POWERED, powered), 3);
        world.notifyNeighborsOfStateChange(pos, this, false);
    }

    private static boolean isReceivingPower(World world, BlockPos pos) {
        PROBING_POWER.set(Boolean.TRUE);
        try {
            return world.isBlockPowered(pos);
        } finally {
            PROBING_POWER.set(Boolean.FALSE);
        }
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return !PROBING_POWER.get() && state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return !PROBING_POWER.get() && state.getValue(POWERED) ? 15 : 0;
    }
}
