package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-desk terminal state. There is exactly one tile entity per monitor desk, on the bottom core
 * cell; it is tickless and stores the password hash, the "no password" choice and the named
 * Security nets. Each net owns its own confirmed device list and at most one linked Terminal Relay
 * Key.
 *
 * <p>The full NBT (world save) keeps the hash and the nets. The update tag sent to clients contains
 * only {@code PasswordSet} and {@code PasswordSkipped}, so the hash and net data stay server-side
 * until the Security screen explicitly requests them.</p>
 */
public class TileEntityMonitorDesk extends TileEntitySynced {

    private String passwordHash = "";
    private boolean passwordSet;
    private boolean passwordSkipped;
    private final List<SecurityNet> nets = new ArrayList<>();
    private int nextNetId;

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

    public List<SecurityNet> getNets() {
        return Collections.unmodifiableList(nets);
    }

    @Nullable
    public SecurityNet getNet(int id) {
        for (SecurityNet net : nets) {
            if (net.id == id) return net;
        }
        return null;
    }

    /** Server side: creates an empty net; a blank name falls back to "Security net N". */
    @Nullable
    public SecurityNet createNet(String name) {
        if (world != null && world.isRemote) return null;
        SecurityNet net = new SecurityNet(nextNetId++, name);
        nets.add(net);
        markDirty();
        return net;
    }

    /** Server side: removes one net and returns it, or null when the net does not exist. */
    @Nullable
    public SecurityNet removeNet(int netId) {
        if (world != null && world.isRemote) return null;
        for (int i = 0; i < nets.size(); i++) {
            SecurityNet net = nets.get(i);
            if (net.id == netId) {
                nets.remove(i);
                markDirty();
                return net;
            }
        }
        return null;
    }

    /** Server side: replaces one net's confirmed device list. */
    public boolean setNetDevices(int netId, List<MonitorDevice> newDevices) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.setDevices(newDevices);
        markDirty();
        return true;
    }

    /** Server side: changes one net's display name. */
    public boolean setNetName(int netId, String name) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.setName(name);
        markDirty();
        return true;
    }

    /** Server side: links one key id/name to a net. */
    public boolean linkKey(int netId, String keyId, String keyName) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.linkKey(keyId, keyName);
        markDirty();
        return true;
    }

    /** Server side: clears a net's key link. */
    public boolean unlinkKey(int netId) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.unlinkKey();
        markDirty();
        return true;
    }

    /** Legacy first-net view, used only by pre-net migration paths. */
    public List<MonitorDevice> getDevices() {
        SecurityNet net = getNet(0);
        return net == null ? Collections.emptyList() : net.getDevices();
    }

    /** Legacy first-net view, used only by pre-net migration paths. */
    public boolean setDevices(List<MonitorDevice> newDevices) {
        SecurityNet net = getNet(0);
        if (net == null) net = createNet("");
        return net != null && setNetDevices(net.id, newDevices);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("PasswordHash", passwordHash);
        tag.setBoolean("PasswordSet", passwordSet);
        tag.setBoolean("PasswordSkipped", passwordSkipped);
        NBTTagList list = new NBTTagList();
        for (SecurityNet net : nets) list.appendTag(net.toNBT());
        tag.setTag("SecurityNets", list);
        tag.setInteger("NextNetId", nextNetId);
        // Keep the old single-list tag populated so an older build still sees net one.
        tag.setTag("Devices", MonitorDevice.toNBTList(getDevices()));
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

        if (tag.hasKey("SecurityNets", Constants.NBT.TAG_LIST)) {
            nets.clear();
            NBTTagList list = tag.getTagList("SecurityNets", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                SecurityNet net = SecurityNet.fromNBT(list.getCompoundTagAt(i));
                if (net != null) nets.add(net);
            }
        } else if (tag.hasKey("Devices", Constants.NBT.TAG_LIST)) {
            // Migration from the one-net build.
            nets.clear();
            SecurityNet net = new SecurityNet(0, "");
            net.setDevices(MonitorDevice.fromNBTList(
                    tag.getTagList("Devices", Constants.NBT.TAG_COMPOUND)));
            nets.add(net);
        }

        nextNetId = tag.hasKey("NextNetId") ? tag.getInteger("NextNetId") : 0;
        for (SecurityNet net : nets) {
            if (net.id >= nextNetId) nextNetId = net.id + 1;
        }
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
