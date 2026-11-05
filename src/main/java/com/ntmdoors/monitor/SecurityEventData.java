package com.ntmdoors.monitor;

import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-dimension index of active Security net events.
 *
 * <p>The monitor desk is the source of truth, but its chunk may be unloaded while a bound door or
 * sensor is still active. This data mirrors each desk's active event bits per device into the world
 * save so shutdown and lockdown stay enforced away from the terminal. Updating a desk replaces only
 * that desk's contribution; device bindings on the desk itself are never modified.</p>
 */
public final class SecurityEventData extends WorldSavedData {

    private static final String DATA_NAME = "ntmdoors_security_events";
    private static final String DESKS = "Desks";
    private static final String DESK_POS = "Desk";
    private static final String DEVICES = "Devices";
    private static final String DEVICE_POS = "Pos";
    private static final String DEVICE_FLAGS = "Flags";

    /** desk position -> (device position -> event bit mask) */
    private final Map<BlockPos, Map<BlockPos, Integer>> contributions = new HashMap<>();
    /** Combined view across every desk, rebuilt lazily after a change. */
    private final Map<BlockPos, Integer> deviceStates = new HashMap<>();
    private boolean dirty = true;

    public SecurityEventData(String name) {
        super(name);
    }

    @Nullable
    public static SecurityEventData get(World world) {
        if (world == null || world.isRemote) return null;
        WorldSavedData data = world.getPerWorldStorage()
                .getOrLoadData(SecurityEventData.class, DATA_NAME);
        if (data instanceof SecurityEventData) return (SecurityEventData) data;

        SecurityEventData created = new SecurityEventData(DATA_NAME);
        world.getPerWorldStorage().setData(DATA_NAME, created);
        return created;
    }

    /** Rebuilds one desk's contribution from its current nets and device bindings. */
    public static void updateDesk(TileEntityMonitorDesk desk) {
        if (desk == null || desk.getWorld() == null || desk.getWorld().isRemote) return;
        SecurityEventData data = get(desk.getWorld());
        if (data == null) return;

        Map<BlockPos, Integer> contribution = new HashMap<>();
        for (SecurityNet net : desk.getNets()) {
            int mask = net.getEventMask();
            if (mask == 0) continue;
            for (MonitorDevice device : net.getDevices()) {
                int bits;
                switch (device.type) {
                    case DOOR:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN);
                        break;
                    case KEYPAD:
                        bits = mask & (SecurityEvents.SHUTDOWN
                                | SecurityEvents.DISABLE_KEYPADS);
                        break;
                    case SCANNER:
                        bits = mask & (SecurityEvents.SHUTDOWN
                                | SecurityEvents.DISABLE_SCANNERS);
                        break;
                    case MOTION_SENSOR:
                        bits = mask & (SecurityEvents.SHUTDOWN
                                | SecurityEvents.DISABLE_SENSORS);
                        break;
                    case REDSTONE_GATE:
                        bits = mask & (SecurityEvents.SHUTDOWN
                                | SecurityEvents.DISABLE_GATES);
                        break;
                    case SPEAKER:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.ALARMS);
                        break;
                    default:
                        bits = 0;
                        break;
                }
                if (bits != 0) {
                    contribution.merge(device.pos, bits, (a, b) -> a | b);
                }
            }
        }

        BlockPos deskPos = desk.getPos().toImmutable();
        Map<BlockPos, Integer> previous = data.contributions.get(deskPos);
        if (contribution.isEmpty()) {
            if (previous == null) return;
            data.contributions.remove(deskPos);
        } else {
            if (contribution.equals(previous)) return;
            data.contributions.put(deskPos, contribution);
        }
        data.dirty = true;
        data.markDirty();
    }

    /** Removes a destroyed desk's contribution without touching any device bindings. */
    public static void removeDesk(World world, BlockPos deskPos) {
        if (world == null || world.isRemote || deskPos == null) return;
        SecurityEventData data = get(world);
        if (data == null) return;
        if (data.contributions.remove(deskPos.toImmutable()) != null) {
            data.dirty = true;
            data.markDirty();
        }
    }

    public int state(BlockPos devicePos) {
        if (devicePos == null) return 0;
        if (dirty) rebuildDeviceStates();
        Integer value = deviceStates.get(devicePos);
        return value == null ? 0 : value;
    }

    private void rebuildDeviceStates() {
        deviceStates.clear();
        for (Map<BlockPos, Integer> contribution : contributions.values()) {
            for (Map.Entry<BlockPos, Integer> entry : contribution.entrySet()) {
                deviceStates.merge(entry.getKey(), entry.getValue(), (a, b) -> a | b);
            }
        }
        dirty = false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        contributions.clear();
        NBTTagList desks = nbt.getTagList(DESKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < desks.tagCount(); i++) {
            NBTTagCompound deskTag = desks.getCompoundTagAt(i);
            BlockPos deskPos = BlockPos.fromLong(deskTag.getLong(DESK_POS));
            Map<BlockPos, Integer> contribution = new HashMap<>();
            NBTTagList devices = deskTag.getTagList(DEVICES, Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < devices.tagCount(); j++) {
                NBTTagCompound deviceTag = devices.getCompoundTagAt(j);
                int flags = deviceTag.getInteger(DEVICE_FLAGS);
                if (flags == 0) continue;
                contribution.put(BlockPos.fromLong(deviceTag.getLong(DEVICE_POS)), flags);
            }
            if (!contribution.isEmpty()) contributions.put(deskPos, contribution);
        }
        dirty = true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList desks = new NBTTagList();
        for (Map.Entry<BlockPos, Map<BlockPos, Integer>> deskEntry : contributions.entrySet()) {
            NBTTagCompound deskTag = new NBTTagCompound();
            deskTag.setLong(DESK_POS, deskEntry.getKey().toLong());

            NBTTagList devices = new NBTTagList();
            for (Map.Entry<BlockPos, Integer> deviceEntry : deskEntry.getValue().entrySet()) {
                NBTTagCompound deviceTag = new NBTTagCompound();
                deviceTag.setLong(DEVICE_POS, deviceEntry.getKey().toLong());
                deviceTag.setInteger(DEVICE_FLAGS, deviceEntry.getValue());
                devices.appendTag(deviceTag);
            }
            deskTag.setTag(DEVICES, devices);
            desks.appendTag(deskTag);
        }
        nbt.setTag(DESKS, desks);
        return nbt;
    }
}
