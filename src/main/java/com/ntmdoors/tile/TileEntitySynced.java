package com.ntmdoors.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Tile entity with the persistence/sync boilerplate shared by the door cells and the keypad.
 * State is sent with the description packet as a full copy of the saved NBT.
 */
public abstract class TileEntitySynced extends TileEntity {

    /** Marks the tile dirty and, on the server, broadcasts its state to nearby clients. */
    protected void sync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    /**
     * Property changes (the silo hatch's pass flag) must not recreate the tile entity. Forge's
     * default for modded tile entities treats every new state object as a refresh.
     */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeSyncTag(new NBTTagCompound());
    }

    /** NBT sent to clients. Defaults to the full saved state; override to strip server-only fields. */
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        return writeToNBT(tag);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
