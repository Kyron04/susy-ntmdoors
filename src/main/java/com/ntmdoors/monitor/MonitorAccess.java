package com.ntmdoors.monitor;

import com.ntmdoors.network.NetworkUtil;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side remote-control context for the Security terminal.
 *
 * <p>Opening the Security device list records the desk the player is standing at. Device packets
 * may then use the normal eight-block reach <em>or</em> the terminal context. The context is only
 * valid while the player stays within reach of that desk and the device remains in its confirmed
 * list, so walking away ends remote access.</p>
 */
public final class MonitorAccess {

    private static final long SESSION_TTL_MS = 10L * 60L * 1000L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final BlockPos desk;
        private final int dimension;
        private final int netId;
        private final Set<BlockPos> extras = new HashSet<>();
        private long lastUse;

        private Session(BlockPos desk, int dimension, int netId) {
            this.desk = desk;
            this.dimension = dimension;
            this.netId = netId;
            this.lastUse = System.currentTimeMillis();
        }
    }

    /** Called when a Security net's device list is requested from a desk within normal reach. */
    public static void setTerminal(EntityPlayer player, BlockPos desk, int netId) {
        if (player == null || player.world == null || player.world.isRemote || desk == null) return;
        SESSIONS.put(player.getUniqueID(), new Session(
                desk.toImmutable(), player.world.provider.getDimension(), netId));
    }

    /** Allows one extra device (for example a door's controlling scanner) for this terminal session. */
    public static void authorizeDevice(EntityPlayer player, BlockPos pos) {
        if (player == null || player.world == null || player.world.isRemote || pos == null) return;
        Session session = SESSIONS.get(player.getUniqueID());
        if (session != null) {
            session.extras.add(pos.toImmutable());
            session.lastUse = System.currentTimeMillis();
        }
    }

    /** True when the player is at a terminal whose confirmed list contains this device. */
    public static boolean isRemoteAuthorized(EntityPlayer player, BlockPos devicePos) {
        if (player == null || player.world == null || player.world.isRemote || devicePos == null) {
            return false;
        }

        UUID id = player.getUniqueID();
        Session session = SESSIONS.get(id);
        if (session == null) return false;

        long now = System.currentTimeMillis();
        if (now - session.lastUse > SESSION_TTL_MS) {
            SESSIONS.remove(id);
            return false;
        }
        if (session.dimension != player.world.provider.getDimension()
                || !NetworkUtil.isWithinReach(player, session.desk)) {
            SESSIONS.remove(id);
            return false;
        }

        TileEntity te = player.world.getTileEntity(session.desk);
        if (!(te instanceof TileEntityMonitorDesk)) {
            SESSIONS.remove(id);
            return false;
        }

        SecurityNet net = ((TileEntityMonitorDesk) te).getNet(session.netId);
        if (net == null) return false;

        MonitorDevice current = MonitorDevice.find(player.world, devicePos);
        if (current == null) return false;
        for (MonitorDevice device : net.getDevices()) {
            if (device.matches(current)) {
                session.lastUse = now;
                return true;
            }
        }
        if (session.extras.contains(devicePos)) {
            session.lastUse = now;
            return true;
        }
        return false;
    }

    /** Normal device reach, or the active linked terminal context. */
    public static boolean canAccess(EntityPlayer player, BlockPos devicePos) {
        if (devicePos == null) return false;
        return NetworkUtil.isWithinReach(player, devicePos)
                || isRemoteAuthorized(player, devicePos);
    }

    private MonitorAccess() {
    }
}
