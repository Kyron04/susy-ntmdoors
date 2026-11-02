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

    private final List<BlockPos> boundDevices = new ArrayList<>();
    private int wirelessTicks;

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

    /** Server side: turns the gate on for at least {@code ticks} ticks. */
    public void pulse(int ticks) {
        if (getWorld() == null || getWorld().isRemote || ticks <= 0) return;
        wirelessTicks = Math.max(wirelessTicks, ticks);
        setPowered(true);
        markDirty();
    }

    public void onNeighborChanged() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (wirelessTicks <= 0) setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
    }

    @Override
    public void update() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (wirelessTicks <= 0) return;
        if (--wirelessTicks <= 0) {
            wirelessTicks = 0;
            setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
        }
    }

    @Override
    public void onLoad() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (wirelessTicks > 0) setPowered(true);
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
        tag.setInteger("wirelessTicks", wirelessTicks);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boundDevices.clear();
        boundDevices.addAll(BlockPositions.decodeList(tag.getString("boundDevices")));
        wirelessTicks = tag.getInteger("wirelessTicks");
    }
}
