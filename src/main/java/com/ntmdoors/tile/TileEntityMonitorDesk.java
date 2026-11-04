package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Per-desk terminal state. There is exactly one tile entity per monitor desk, on the bottom core
 * cell; it is tickless and only stores the password hash plus the "no password" choice.
 *
 * <p>The full NBT (world save) keeps the hash. The update tag sent to clients contains only
 * {@code PasswordSet} and {@code PasswordSkipped}, so the hash never leaves the server.</p>
 */
public class TileEntityMonitorDesk extends TileEntitySynced {

    private String passwordHash = "";
    private boolean passwordSet;
    private boolean passwordSkipped;

    /** Returns the core tile entity, creating it if an old desk has not produced one yet. */
    public static TileEntityMonitorDesk at(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityMonitorDesk) return (TileEntityMonitorDesk) tile;
        if (tile != null || world.isRemote) return null;

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof BlockMonitorSetup) && !(block instanceof BlockMonitorDesk)) return null;
        if (!block.hasTileEntity(state)) return null;

        TileEntityMonitorDesk monitor = new TileEntityMonitorDesk();
        world.setTileEntity(pos, monitor);
        return monitor;
    }

    public boolean hasPassword() {
        return passwordSet;
    }

    public boolean isPasswordSkipped() {
        return passwordSkipped;
    }

    /** Server side: stores the already-hashed password and clears the "skipped" choice. */
    public boolean setPasswordHash(String hash) {
        if (world == null || world.isRemote || !MonitorPasswords.isValidHash(hash)) return false;
        passwordHash = hash;
        passwordSet = true;
        passwordSkipped = false;
        sync();
        return true;
    }

    /** Server side: the player chose not to protect this desk. */
    public boolean skipPasswordSetup() {
        if (world == null || world.isRemote) return false;
        passwordSkipped = true;
        sync();
        return true;
    }

    /** Server side: checks a client-supplied hash against the stored hash. */
    public boolean checkPasswordHash(String hash) {
        return passwordSet && MonitorPasswords.matches(passwordHash, hash);
    }

    /** Server side: removes the password and allows the setup prompt to appear again. */
    public boolean clearPassword() {
        if (world == null || world.isRemote) return false;
        passwordHash = "";
        passwordSet = false;
        passwordSkipped = false;
        sync();
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("PasswordHash", passwordHash);
        tag.setBoolean("PasswordSet", passwordSet);
        tag.setBoolean("PasswordSkipped", passwordSkipped);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("PasswordHash")) {
            passwordHash = tag.getString("PasswordHash");
        }
        if (tag.hasKey("PasswordSet")) {
            passwordSet = tag.getBoolean("PasswordSet");
        } else {
            passwordSet = !passwordHash.isEmpty();
        }
        passwordSkipped = tag.getBoolean("PasswordSkipped");
    }

    /** Clients get the position and the two flags only; the hash is server-side state. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        // TileEntity.readFromNBT always reads x/y/z, so the stripped tag must still carry them.
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        tag.setBoolean("PasswordSet", passwordSet);
        tag.setBoolean("PasswordSkipped", passwordSkipped);
        return tag;
    }
}
