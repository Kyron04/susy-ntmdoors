package com.ntmdoors.monitor;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Terminal Relay Key data. A key has a persistent link to one Security net and, while binding, a
 * pending device list for that same net.
 */
public final class TerminalKeyData {

    private static final String SESSION_ROOT = "TerminalBinding";
    private static final String SESSION_DESK = "Desk";
    private static final String SESSION_DIM = "DeskDim";
    private static final String SESSION_NET = "Net";
    private static final String SESSION_DEVICES = "Devices";

    private static final String LINK_ROOT = "TerminalLink";
    private static final String LINK_DESK = "Desk";
    private static final String LINK_DIM = "DeskDim";
    private static final String LINK_NET = "Net";
    private static final String LINK_ID = "KeyId";

    /** Immutable view of the key's current binding session. */
    public static final class Session {

        public final BlockPos desk;
        public final int dimension;
        public final int netId;
        public final List<MonitorDevice> devices;

        private Session(BlockPos desk, int dimension, int netId, List<MonitorDevice> devices) {
            this.desk = desk;
            this.dimension = dimension;
            this.netId = netId;
            this.devices = Collections.unmodifiableList(new ArrayList<>(devices));
        }
    }

    /** Persistent link from the key to one net. */
    public static final class Link {

        public final BlockPos desk;
        public final int dimension;
        public final int netId;
        public final String keyId;

        private Link(BlockPos desk, int dimension, int netId, String keyId) {
            this.desk = desk;
            this.dimension = dimension;
            this.netId = netId;
            this.keyId = keyId;
        }
    }

    @Nullable
    public static Session getSession(ItemStack stack) {
        NBTTagCompound tag = root(stack, SESSION_ROOT, false);
        if (tag == null || !tag.hasKey(SESSION_DESK)) return null;

        BlockPos desk = BlockPos.fromLong(tag.getLong(SESSION_DESK));
        int dimension = tag.getInteger(SESSION_DIM);
        int netId = tag.hasKey(SESSION_NET) ? tag.getInteger(SESSION_NET) : 0;
        List<MonitorDevice> devices = tag.hasKey(SESSION_DEVICES, Constants.NBT.TAG_LIST)
                ? MonitorDevice.fromNBTList(tag.getTagList(SESSION_DEVICES, Constants.NBT.TAG_COMPOUND))
                : Collections.emptyList();
        return new Session(desk, dimension, netId, devices);
    }

    public static boolean hasSession(ItemStack stack) {
        return getSession(stack) != null;
    }

    /** Starts a session for one net, copying that net's confirmed devices into the pending list. */
    public static void start(ItemStack stack, BlockPos desk, int dimension, int netId,
                             List<MonitorDevice> existingDevices) {
        NBTTagCompound tag = root(stack, SESSION_ROOT, true);
        if (tag == null) return;
        tag.setLong(SESSION_DESK, desk.toLong());
        tag.setInteger(SESSION_DIM, dimension);
        tag.setInteger(SESSION_NET, netId);
        tag.setTag(SESSION_DEVICES, MonitorDevice.toNBTList(existingDevices));
    }

    public static List<MonitorDevice> getDevices(ItemStack stack) {
        Session session = getSession(stack);
        return session == null ? Collections.emptyList() : new ArrayList<>(session.devices);
    }

    public static void setDevices(ItemStack stack, List<MonitorDevice> devices) {
        NBTTagCompound tag = root(stack, SESSION_ROOT, true);
        if (tag == null) return;
        tag.setTag(SESSION_DEVICES, MonitorDevice.toNBTList(devices));
    }

    /** Adds a device, or replaces the name of the same device if it is already pending. */
    public static void addOrUpdate(ItemStack stack, MonitorDevice device) {
        List<MonitorDevice> devices = getDevices(stack);
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).matches(device)) {
                devices.set(i, device);
                setDevices(stack, devices);
                return;
            }
        }
        devices.add(device);
        setDevices(stack, devices);
    }

    @Nullable
    public static MonitorDevice find(ItemStack stack, MonitorDevice device) {
        if (device == null) return null;
        for (MonitorDevice pending : getDevices(stack)) {
            if (pending.matches(device)) return pending;
        }
        return null;
    }

    @Nullable
    public static Link getLink(ItemStack stack) {
        NBTTagCompound tag = root(stack, LINK_ROOT, false);
        if (tag == null || !tag.hasKey(LINK_ID) || !tag.hasKey(LINK_DESK)) return null;
        String keyId = tag.getString(LINK_ID);
        if (keyId.isEmpty()) return null;
        return new Link(BlockPos.fromLong(tag.getLong(LINK_DESK)),
                tag.getInteger(LINK_DIM), tag.getInteger(LINK_NET), keyId);
    }

    public static boolean hasLink(ItemStack stack) {
        return getLink(stack) != null;
    }

    public static void setLink(ItemStack stack, BlockPos desk, int dimension, int netId,
                               String keyId) {
        NBTTagCompound tag = root(stack, LINK_ROOT, true);
        if (tag == null) return;
        tag.setLong(LINK_DESK, desk.toLong());
        tag.setInteger(LINK_DIM, dimension);
        tag.setInteger(LINK_NET, netId);
        tag.setString(LINK_ID, keyId == null ? "" : keyId);
    }

    public static void clearLink(ItemStack stack) {
        NBTTagCompound tag = root(stack, LINK_ROOT, false);
        if (tag == null) return;
        removeRoot(stack, LINK_ROOT);
    }

    public static void clearSession(ItemStack stack) {
        removeRoot(stack, SESSION_ROOT);
    }

    private static void removeRoot(ItemStack stack, String rootName) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        tag.removeTag(rootName);
        if (tag.getSize() == 0) stack.setTagCompound(null);
    }

    @Nullable
    private static NBTTagCompound root(ItemStack stack, String rootName, boolean create) {
        if (stack.isEmpty()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            if (!create) return null;
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        if (!tag.hasKey(rootName, Constants.NBT.TAG_COMPOUND)) {
            if (!create) return null;
            tag.setTag(rootName, new NBTTagCompound());
        }
        return tag.getCompoundTag(rootName);
    }

    private TerminalKeyData() {
    }
}
