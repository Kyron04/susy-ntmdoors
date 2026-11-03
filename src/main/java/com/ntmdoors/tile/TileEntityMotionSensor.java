package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMotionSensor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;

/**
 * Stores the Motion Sensor's current light state. The block state only serialises the six-way
 * facing, so the status lives here and {@link BlockMotionSensor#getActualState} feeds it back into
 * the model. That keeps all four light models available without exceeding the four metadata bits.
 */
public class TileEntityMotionSensor extends TileEntitySynced {

    private BlockMotionSensor.Status status = BlockMotionSensor.Status.IDLE;

    public BlockMotionSensor.Status getStatus() {
        return status;
    }

    /** Server side: changes the light and syncs the new actual state to nearby clients. */
    public boolean setStatus(BlockMotionSensor.Status status) {
        if (status == null || this.status == status) return false;
        this.status = status;
        sync();
        return true;
    }

    /** Creative preview helper; the real detection logic will set a status directly. */
    public boolean cycleStatus() {
        BlockMotionSensor.Status[] values = BlockMotionSensor.Status.values();
        return setStatus(values[(status.ordinal() + 1) % values.length]);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("status", (byte) status.ordinal());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        status = BlockMotionSensor.Status.values()[tag.getByte("status") & 3];
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        if (world != null && world.isRemote) {
            // The block state itself does not change; force the chunk mesh to ask for the new
            // actual state from this tile entity.
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
}
