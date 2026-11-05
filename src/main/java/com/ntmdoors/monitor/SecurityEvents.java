package com.ntmdoors.monitor;

import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Server-side facade for the Security net event toggles.
 *
 * <p>The actual state is mirrored into {@link SecurityEventData}, so a toggle keeps applying after
 * the terminal chunk unloads or the server restarts. Device bindings themselves remain stored only
 * on the desk and are never changed by event enforcement.</p>
 */
public final class SecurityEvents {

    public static final int SHUTDOWN = 1;
    public static final int LOCKDOWN = 2;
    public static final int ALARMS = 4;
    public static final int DISABLE_KEYPADS = 8;
    public static final int DISABLE_SCANNERS = 16;
    public static final int DISABLE_SENSORS = 32;
    public static final int DISABLE_GATES = 64;

    public static boolean isShutdown(World world, BlockPos devicePos) {
        return hasState(world, devicePos, SHUTDOWN);
    }

    public static boolean isLockdown(World world, BlockPos devicePos) {
        return hasState(world, devicePos, LOCKDOWN);
    }

    public static boolean isAlarm(World world, BlockPos devicePos) {
        return hasState(world, devicePos, ALARMS);
    }

    public static boolean isKeypadDisabled(World world, BlockPos devicePos) {
        return hasState(world, devicePos, DISABLE_KEYPADS);
    }

    public static boolean isScannerDisabled(World world, BlockPos devicePos) {
        return hasState(world, devicePos, DISABLE_SCANNERS);
    }

    public static boolean isSensorDisabled(World world, BlockPos devicePos) {
        return hasState(world, devicePos, DISABLE_SENSORS);
    }

    public static boolean isGateDisabled(World world, BlockPos devicePos) {
        return hasState(world, devicePos, DISABLE_GATES);
    }

    private static boolean hasState(World world, BlockPos devicePos, int flag) {
        SecurityEventData data = SecurityEventData.get(world);
        return data != null && (data.state(devicePos) & flag) != 0;
    }

    /** Refreshes the persistent index from a desk's current nets and device bindings. */
    public static void updateDesk(TileEntityMonitorDesk desk) {
        SecurityEventData.updateDesk(desk);
    }

    /** Removes a broken desk from the event index. */
    public static void removeDesk(World world, BlockPos deskPos) {
        SecurityEventData.removeDesk(world, deskPos);
    }

    /** Closes every loaded door in the net. */
    public static void closeAllDoors(World world, SecurityNet net) {
        if (world == null || world.isRemote || net == null) return;
        for (MonitorDevice device : net.getDevices()) {
            if (device.type != MonitorDevice.Type.DOOR) continue;
            if (!world.isBlockLoaded(device.pos)) continue;
            TileEntity te = world.getTileEntity(device.pos);
            if (te instanceof TileEntityDoorGeneric) {
                ((TileEntityDoorGeneric) te).closeForSystem();
            }
        }
    }

    /** Opens every loaded door in the net unless shutdown or lockdown is active. */
    public static void openAllDoors(World world, SecurityNet net) {
        if (world == null || world.isRemote || net == null) return;
        if (net.getEventEnabled(SecurityNet.EVENT_SYSTEM_SHUTDOWN)
                || net.getEventEnabled(SecurityNet.EVENT_SYSTEM_LOCKDOWN)) {
            closeAllDoors(world, net);
            return;
        }
        for (MonitorDevice device : net.getDevices()) {
            if (device.type != MonitorDevice.Type.DOOR) continue;
            if (!world.isBlockLoaded(device.pos)) continue;
            TileEntity te = world.getTileEntity(device.pos);
            if (te instanceof TileEntityDoorGeneric) {
                ((TileEntityDoorGeneric) te).openForSystem();
            }
        }
    }

    private SecurityEvents() {
    }
}
