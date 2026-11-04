package com.ntmdoors.monitor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

    public final int id;
    private String name;
    private final List<MonitorDevice> devices = new ArrayList<>();
    private String linkedKeyId = "";
    private String linkedKeyName = "";

    public SecurityNet(int id, @Nullable String name) {
        this.id = id;
        this.name = name == null ? "" : name;
    }

    public String displayName() {
        return name.isEmpty() ? "Security net " + (id + 1) : name;
    }

    public void setName(@Nullable String name) {
        this.name = name == null ? "" : name;
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

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", id);
        tag.setString("Name", name);
        tag.setTag("Devices", MonitorDevice.toNBTList(devices));
        tag.setString("LinkedKeyId", linkedKeyId);
        tag.setString("LinkedKeyName", linkedKeyName);
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
        return net;
    }
}
