package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.tile.RedstoneBinding;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Redstone Gate. It is off when placed, turns on while it receives redstone power or a wireless
 * pulse from a bound safe/scanner, and emits a full-strength signal while on. The gate's own output
 * is excluded while it probes for input so an adjacent normal cube cannot latch it on.
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
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityRedstoneGate();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (world.isRemote) return;
        TileEntityRedstoneGate gate = ensureTileEntity(world, pos);
        if (gate != null) gate.onNeighborChanged();
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) return;
        TileEntityRedstoneGate gate = ensureTileEntity(world, pos);
        if (gate != null) gate.onNeighborChanged();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityRedstoneGate) ((TileEntityRedstoneGate) te).clearDevices();
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking() || !(held.getItem() instanceof ItemRedstoneRelayKey)) return false;

        if (!world.isRemote) {
            TileEntityRedstoneGate gate = ensureTileEntity(world, pos);
            if (gate == null) return true;
            RedstoneBinding binding = RedstoneBinding.findSession(world, player);
            if (binding == null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.redstone_binding_required"), true);
            } else {
                binding.link(player, gate);
            }
        }
        return true;
    }

    @Nullable
    private static TileEntityRedstoneGate ensureTileEntity(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityRedstoneGate) return (TileEntityRedstoneGate) te;
        if (!(world.getBlockState(pos).getBlock() instanceof BlockRedstoneGate)) return null;
        TileEntityRedstoneGate created = new TileEntityRedstoneGate();
        created.setWorld(world);
        created.setPos(pos);
        world.setTileEntity(pos, created);
        return created;
    }

    /**
     * True if the gate is receiving a redstone input. The gate's own output is hidden during this
     * probe; see {@link #PROBING_POWER}.
     */
    public static boolean isReceivingPower(World world, BlockPos pos) {
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
