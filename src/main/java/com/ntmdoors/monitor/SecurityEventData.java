package com.ntmdoors.monitor;

import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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
public final class SecurityEventData extends PersistentState {

    public static final String DATA_NAME = "ntmdoors_security_events";
    private static final String DESKS = "Desks";
    private static final String DESK_POS = "Desk";
    private static final String DEVICES = "Devices";
    private static final String DEVICE_POS = "Pos";
    private static final String DEVICE_FLAGS = "Flags";

    /** desk position -> (device position -> event bit mask) */
    private final Map<BlockPos, Map<BlockPos, Integer>> contributions = new HashMap<>();
    /** Combined view across every desk, rebuilt lazily after a change. */
    private final Map<BlockPos, Integer> deviceStates = new HashMap<>();
    private boolean dirtyCache = true;

    public SecurityEventData() {
    }

    @Nullable
    public static SecurityEventData get(World world) {
        if (world == null || world.isClient()) return null;
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.getPersistentStateManager().getOrCreate(
                    SecurityEventData::fromNbt,
                    SecurityEventData::new,
                    DATA_NAME
            );
        }
        return null;
    }

    /** Rebuilds one desk's contribution from its current nets and device bindings. */
    public static void updateDesk(MonitorDeskBlockEntity desk) {
        if (desk == null || desk.getWorld() == null || desk.getWorld().isClient()) return;
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
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN
                                | SecurityEvents.DISABLE_KEYPADS);
                        break;
                    case SCANNER:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN
                                | SecurityEvents.DISABLE_SCANNERS);
                        break;
                    case MOTION_SENSOR:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN
                                | SecurityEvents.DISABLE_SENSORS);
                        break;
                    case REDSTONE_GATE:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN
                                | SecurityEvents.DISABLE_GATES);
                        break;
                    case SPEAKER:
                        bits = mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN
                                | SecurityEvents.ALARMS);
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
        data.dirtyCache = true;
        data.markDirty();
    }

    /** Removes a destroyed desk's contribution without touching any device bindings. */
    public static void removeDesk(World world, BlockPos deskPos) {
        if (world == null || world.isClient() || deskPos == null) return;
        SecurityEventData data = get(world);
        if (data == null) return;
        if (data.contributions.remove(deskPos.toImmutable()) != null) {
            data.dirtyCache = true;
            data.markDirty();
        }
    }

    public int state(BlockPos devicePos) {
        if (devicePos == null) return 0;
        if (dirtyCache) rebuildDeviceStates();
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
        dirtyCache = false;
    }

    public static SecurityEventData fromNbt(NbtCompound nbt) {
        SecurityEventData data = new SecurityEventData();
        data.readNbt(nbt);
        return data;
    }

    public void readNbt(NbtCompound nbt) {
        contributions.clear();
        NbtList desks = nbt.getList(DESKS, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < desks.size(); i++) {
            NbtCompound deskTag = desks.getCompound(i);
            BlockPos deskPos = BlockPos.fromLong(deskTag.getLong(DESK_POS));
            Map<BlockPos, Integer> contribution = new HashMap<>();
            NbtList devices = deskTag.getList(DEVICES, NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < devices.size(); j++) {
                NbtCompound deviceTag = devices.getCompound(j);
                int flags = deviceTag.getInt(DEVICE_FLAGS);
                if (flags == 0) continue;
                contribution.put(BlockPos.fromLong(deviceTag.getLong(DEVICE_POS)), flags);
            }
            if (!contribution.isEmpty()) contributions.put(deskPos, contribution);
        }
        dirtyCache = true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList desks = new NbtList();
        for (Map.Entry<BlockPos, Map<BlockPos, Integer>> deskEntry : contributions.entrySet()) {
            NbtCompound deskTag = new NbtCompound();
            deskTag.putLong(DESK_POS, deskEntry.getKey().asLong());

            NbtList devices = new NbtList();
            for (Map.Entry<BlockPos, Integer> deviceEntry : deskEntry.getValue().entrySet()) {
                NbtCompound deviceTag = new NbtCompound();
                deviceTag.putLong(DEVICE_POS, deviceEntry.getKey().asLong());
                deviceTag.putInt(DEVICE_FLAGS, deviceEntry.getValue());
                devices.add(deviceTag);
            }
            deskTag.put(DEVICES, devices);
            desks.add(deskTag);
        }
        nbt.put(DESKS, desks);
        return nbt;
    }
}
