package com.ntmdoors.tile;

import com.ntmdoors.block.BlockRedstoneGate;
import com.ntmdoors.util.BlockPositions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Gate logic. Redstone input and wireless pulses share the block's POWERED state; a wireless pulse
 * keeps the gate on for its duration, then the gate falls back to whatever redstone input remains.
 */
public class TileEntityRedstoneGate extends TileEntitySynced implements ITickable {

    /** Every wireless event keeps the gate powered for exactly one second. */
    public static final int WIRELESS_PULSE_TICKS = 20;
    /** Ticks the gate stays off so attached redstone can settle before input is checked again. */
    private static final int INPUT_SETTLE_TICKS = 1;

    private final List<BlockPos> boundDevices = new ArrayList<>();
    /** Absolute world time at which the current wireless pulse ends; 0 when idle. */
    private long pulseEnd;
    /** While positive, neighbor changes are ignored so the gate cannot read its own output back. */
    private int inputSettleTicks;

    /** Server side: adds a safe or scanner to this gate's wireless link. */
    public void bindDevice(BlockPos devicePos) {
        if (devicePos == null) return;
        BlockPos device = devicePos.toImmutable();
        if (boundDevices.contains(device)) return;
        boundDevices.add(device);
        markDirty();
    }

    /** Server side: removes one device from this gate's wireless link. */
    public void unbindDevice(BlockPos devicePos) {
        if (devicePos != null && boundDevices.remove(devicePos)) markDirty();
    }

    /** Server side: clears both sides of every link before the gate is broken. */
    public void clearDevices() {
        World world = getWorld();
        if (world != null && !world.isRemote) {
            for (BlockPos devicePos : new ArrayList<>(boundDevices)) {
                if (!world.isBlockLoaded(devicePos)) continue;
                TileEntity te = world.getTileEntity(devicePos);
                if (te instanceof RedstoneBindingHost) {
                    ((RedstoneBindingHost) te).getRedstoneBinding().onGateRemoved(pos);
                }
            }
        }
        boundDevices.clear();
        markDirty();
    }

    /**
     * Server side: turns the gate on for at least {@code ticks} ticks, and never less than
     * {@link #WIRELESS_PULSE_TICKS}. A second wireless event extends the current pulse instead of
     * shortening it.
     */
    public void pulse(int ticks) {
        if (getWorld() == null || getWorld().isRemote) return;
        long now = getWorld().getTotalWorldTime();
        long end = now + Math.max(ticks, WIRELESS_PULSE_TICKS);
        // A second event while the gate is already pulsing extends the current window.
        if (end > pulseEnd) pulseEnd = end;
        inputSettleTicks = 0;
        setPowered(true);
        markDirty();
    }

    /** Server side: remaining wireless pulse ticks; exposed for diagnostics and the gate screen. */
    public int getWirelessTicks() {
        if (getWorld() == null || pulseEnd <= 0) return 0;
        return (int) Math.max(0L, pulseEnd - getWorld().getTotalWorldTime());
    }

    private boolean isPulsing() {
        return getWorld() != null && pulseEnd > getWorld().getTotalWorldTime();
    }

    public void onNeighborChanged() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (inputSettleTicks > 0) return;
        if (!isPulsing()) setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
    }

    @Override
    public void update() {
        if (getWorld() == null || getWorld().isRemote) return;

        if (inputSettleTicks > 0) {
            if (--inputSettleTicks == 0) {
                setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
            }
            return;
        }
        if (pulseEnd <= 0 || isPulsing()) return;

        pulseEnd = 0;
        // Force the gate off and ignore input for a moment. The wire network it was powering needs
        // a tick to update; reading the input immediately lets a line latch the gate back on.
        inputSettleTicks = INPUT_SETTLE_TICKS;
        setPowered(false);
    }

    @Override
    public void onLoad() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (isPulsing()) setPowered(true);
        else setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
    }

    private void setPowered(boolean powered) {
        World world = getWorld();
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRedstoneGate)) return;
        if (state.getValue(BlockRedstoneGate.POWERED) == powered) return;
        world.setBlockState(pos, state.withProperty(BlockRedstoneGate.POWERED, powered), 3);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("boundDevices", BlockPositions.encodeList(boundDevices));
        tag.setLong("wirelessPulseEnd", pulseEnd);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boundDevices.clear();
        boundDevices.addAll(BlockPositions.decodeList(tag.getString("boundDevices")));
        if (tag.hasKey("wirelessPulseEnd")) {
            pulseEnd = tag.getLong("wirelessPulseEnd");
        } else if (tag.hasKey("wirelessTicks") && getWorld() != null) {
            // Migrate the old remaining-ticks field from worlds saved by earlier builds.
            pulseEnd = getWorld().getTotalWorldTime() + tag.getInteger("wirelessTicks");
        } else {
            pulseEnd = 0;
        }
    }
}
