package com.ntmdoors.monitor;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

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
        NbtCompound tag = root(stack, SESSION_ROOT, false);
        if (tag == null || !tag.contains(SESSION_DESK, NbtElement.LONG_TYPE)) return null;

        BlockPos desk = BlockPos.fromLong(tag.getLong(SESSION_DESK));
        int dimension = tag.getInt(SESSION_DIM);
        int netId = tag.contains(SESSION_NET, NbtElement.INT_TYPE) ? tag.getInt(SESSION_NET) : 0;
        List<MonitorDevice> devices = tag.contains(SESSION_DEVICES, NbtElement.LIST_TYPE)
                ? MonitorDevice.fromNbtList(tag.getList(SESSION_DEVICES, NbtElement.COMPOUND_TYPE))
                : Collections.emptyList();
        return new Session(desk, dimension, netId, devices);
    }

    /** Starts a session for one net, copying that net's confirmed devices into the pending list. */
    public static void start(ItemStack stack, BlockPos desk, int dimension, int netId,
                             List<MonitorDevice> existingDevices) {
        NbtCompound tag = root(stack, SESSION_ROOT, true);
        if (tag == null) return;
        tag.putLong(SESSION_DESK, desk.asLong());
        tag.putInt(SESSION_DIM, dimension);
        tag.putInt(SESSION_NET, netId);
        tag.put(SESSION_DEVICES, MonitorDevice.toNbtList(existingDevices));
    }

    public static List<MonitorDevice> getDevices(ItemStack stack) {
        Session session = getSession(stack);
        return session == null ? Collections.emptyList() : new ArrayList<>(session.devices);
    }

    public static void setDevices(ItemStack stack, List<MonitorDevice> devices) {
        NbtCompound tag = root(stack, SESSION_ROOT, true);
        if (tag == null) return;
        tag.put(SESSION_DEVICES, MonitorDevice.toNbtList(devices));
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
        NbtCompound tag = root(stack, LINK_ROOT, false);
        if (tag == null || !tag.contains(LINK_ID, NbtElement.STRING_TYPE) || !tag.contains(LINK_DESK, NbtElement.LONG_TYPE)) return null;
        String keyId = tag.getString(LINK_ID);
        if (keyId.isEmpty()) return null;
        return new Link(BlockPos.fromLong(tag.getLong(LINK_DESK)),
                tag.getInt(LINK_DIM), tag.getInt(LINK_NET), keyId);
    }

    public static void setLink(ItemStack stack, BlockPos desk, int dimension, int netId,
                               String keyId) {
        NbtCompound tag = root(stack, LINK_ROOT, true);
        if (tag == null) return;
        tag.putLong(LINK_DESK, desk.asLong());
        tag.putInt(LINK_DIM, dimension);
        tag.putInt(LINK_NET, netId);
        tag.putString(LINK_ID, keyId == null ? "" : keyId);
    }

    public static void clearLink(ItemStack stack) {
        NbtCompound tag = root(stack, LINK_ROOT, false);
        if (tag == null) return;
        removeRoot(stack, LINK_ROOT);
    }

    public static void clearSession(ItemStack stack) {
        removeRoot(stack, SESSION_ROOT);
    }

    private static void removeRoot(ItemStack stack, String rootName) {
        if (stack.isEmpty() || !stack.hasNbt()) return;
        NbtCompound tag = stack.getNbt();
        if (tag != null) {
            tag.remove(rootName);
            if (tag.isEmpty()) {
                stack.setNbt(null);
            }
        }
    }

    @Nullable
    private static NbtCompound root(ItemStack stack, String rootName, boolean create) {
        if (stack.isEmpty()) return null;
        NbtCompound tag = stack.getNbt();
        if (tag == null) {
            if (!create) return null;
            tag = stack.getOrCreateNbt();
        }
        if (!tag.contains(rootName, NbtElement.COMPOUND_TYPE)) {
            if (!create) return null;
            NbtCompound sub = new NbtCompound();
            tag.put(rootName, sub);
            return sub;
        }
        return tag.getCompound(rootName);
    }

    private TerminalKeyData() {
    }
}
