package com.ntmdoors.monitor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Security net: a named device list plus at most one linked Terminal Relay Key.
 * The key link is stored on both sides; the net's link id is authoritative if they disagree.
 */
public final class SecurityNet {

    public static final int EVENT_SYSTEM_SHUTDOWN = 0;
    public static final int EVENT_SYSTEM_LOCKDOWN = 1;
    public static final int EVENT_TRIGGER_ALARMS = 2;
    public static final int EVENT_DISABLE_KEYPADS = 3;
    public static final int EVENT_DISABLE_SCANNERS = 4;
    public static final int EVENT_DISABLE_SENSORS = 5;
    public static final int EVENT_DISABLE_REDSTONE_GATES = 6;
    public static final int EVENT_COUNT = 7;

    public final int id;
    private String name;
    private final List<MonitorDevice> devices = new ArrayList<>();
    private String linkedKeyId = "";
    private String linkedKeyName = "";
    private boolean systemShutdown;
    private boolean systemLockdown;
    private boolean triggerAlarms;
    private boolean disableKeypads;
    private boolean disableScanners;
    private boolean disableSensors;
    private boolean disableRedstoneGates;

    public SecurityNet(int id, @Nullable String name) {
        this.id = id;
        this.name = name == null ? "" : name;
    }

    public String displayName() {
        return name.isEmpty() ? "Security net " + (id + 1) : name;
    }

    public List<MonitorDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    public void setDevices(List<MonitorDevice> newDevices) {
        devices.clear();
        devices.addAll(newDevices);
    }

    public String getLinkedKeyId() {
        return linkedKeyId;
    }

    public String getLinkedKeyName() {
        return linkedKeyName;
    }

    public boolean hasLinkedKey() {
        return !linkedKeyId.isEmpty();
    }

    public void linkKey(String keyId, String keyName) {
        linkedKeyId = keyId == null ? "" : keyId;
        linkedKeyName = keyName == null ? "Terminal Relay Key" : keyName;
    }

    public void unlinkKey() {
        linkedKeyId = "";
        linkedKeyName = "";
    }

    public boolean getEventEnabled(int eventId) {
        switch (eventId) {
            case EVENT_SYSTEM_SHUTDOWN:
                return systemShutdown;
            case EVENT_SYSTEM_LOCKDOWN:
                return systemLockdown;
            case EVENT_TRIGGER_ALARMS:
                return triggerAlarms;
            case EVENT_DISABLE_KEYPADS:
                return disableKeypads;
            case EVENT_DISABLE_SCANNERS:
                return disableScanners;
            case EVENT_DISABLE_SENSORS:
                return disableSensors;
            case EVENT_DISABLE_REDSTONE_GATES:
                return disableRedstoneGates;
            default:
                return false;
        }
    }

    /** Server side: stores one Events tab toggle. Event behavior is handled separately. */
    public void setEventEnabled(int eventId, boolean enabled) {
        switch (eventId) {
            case EVENT_SYSTEM_SHUTDOWN:
                systemShutdown = enabled;
                break;
            case EVENT_SYSTEM_LOCKDOWN:
                systemLockdown = enabled;
                break;
            case EVENT_TRIGGER_ALARMS:
                triggerAlarms = enabled;
                break;
            case EVENT_DISABLE_KEYPADS:
                disableKeypads = enabled;
                break;
            case EVENT_DISABLE_SCANNERS:
                disableScanners = enabled;
                break;
            case EVENT_DISABLE_SENSORS:
                disableSensors = enabled;
                break;
            case EVENT_DISABLE_REDSTONE_GATES:
                disableRedstoneGates = enabled;
                break;
            default:
                break;
        }
    }

    /** Bit mask used by the client and the persistent event index. */
    public int getEventMask() {
        int mask = 0;
        for (int i = 0; i < EVENT_COUNT; i++) {
            if (getEventEnabled(i)) mask |= 1 << i;
        }
        return mask;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", id);
        tag.setString("Name", name);
        tag.setTag("Devices", MonitorDevice.toNBTList(devices));
        tag.setString("LinkedKeyId", linkedKeyId);
        tag.setString("LinkedKeyName", linkedKeyName);
        tag.setBoolean("SystemShutdown", systemShutdown);
        tag.setBoolean("SystemLockdown", systemLockdown);
        tag.setBoolean("TriggerAlarms", triggerAlarms);
        tag.setBoolean("DisableKeypads", disableKeypads);
        tag.setBoolean("DisableScanners", disableScanners);
        tag.setBoolean("DisableSensors", disableSensors);
        tag.setBoolean("DisableRedstoneGates", disableRedstoneGates);
        return tag;
    }

    @Nullable
    public static SecurityNet fromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("Id")) return null;
        SecurityNet net = new SecurityNet(tag.getInteger("Id"), tag.getString("Name"));
        if (tag.hasKey("Devices", Constants.NBT.TAG_LIST)) {
            net.devices.addAll(MonitorDevice.fromNBTList(
                    tag.getTagList("Devices", Constants.NBT.TAG_COMPOUND)));
        }
        net.linkedKeyId = tag.getString("LinkedKeyId");
        net.linkedKeyName = tag.getString("LinkedKeyName");
        net.systemShutdown = tag.getBoolean("SystemShutdown");
        net.systemLockdown = tag.getBoolean("SystemLockdown");
        net.triggerAlarms = tag.getBoolean("TriggerAlarms");
        net.disableKeypads = tag.getBoolean("DisableKeypads");
        net.disableScanners = tag.getBoolean("DisableScanners");
        net.disableSensors = tag.getBoolean("DisableSensors");
        net.disableRedstoneGates = tag.getBoolean("DisableRedstoneGates");
        return net;
    }
}
