package com.ntmdoors.monitor;

import com.ntmdoors.network.NetworkUtil;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side monitor terminal sessions.
 *
 * <p>Opening the password screen is not authorization. A session is created only when the player
 * opens a passwordless desk that has already skipped setup, or when a password check succeeds.
 * Every terminal action packet must pass {@link #isAuthorized(EntityPlayer, BlockPos)}. Sessions
 * are tied to one player, desk and dimension, expire after ten minutes of inactivity, and are
 * invalidated if the desk's password state changes.</p>
 */
public final class MonitorAuth {

    private static final long SESSION_TTL_MS = 10L * 60L * 1000L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final BlockPos desk;
        private final int dimension;
        private final String fingerprint;
        private long lastUse;

        private Session(BlockPos desk, int dimension, String fingerprint) {
            this.desk = desk;
            this.dimension = dimension;
            this.fingerprint = fingerprint;
            this.lastUse = System.currentTimeMillis();
        }
    }

    /** Grants a session after a successful password check or a passwordless open. */
    public static void authorize(EntityPlayer player, BlockPos desk) {
        if (player == null || player.world == null || player.world.isRemote || desk == null) {
            return;
        }
        TileEntity te = player.world.getTileEntity(desk);
        if (!(te instanceof TileEntityMonitorDesk)) return;
        SESSIONS.put(player.getUniqueID(), new Session(desk.toImmutable(),
                player.world.provider.getDimension(),
                ((TileEntityMonitorDesk) te).getPasswordFingerprint()));
    }

    /** True only while this player has an unexpired session for this desk. */
    public static boolean isAuthorized(EntityPlayer player, BlockPos desk) {
        if (player == null || player.world == null || player.world.isRemote || desk == null) {
            return false;
        }
        UUID id = player.getUniqueID();
        Session session = SESSIONS.get(id);
        if (session == null) return false;

        long now = System.currentTimeMillis();
        if (now - session.lastUse > SESSION_TTL_MS
                || session.dimension != player.world.provider.getDimension()
                || !session.desk.equals(desk)
                || !NetworkUtil.isWithinReach(player, desk)) {
            SESSIONS.remove(id);
            return false;
        }

        TileEntity te = player.world.getTileEntity(desk);
        if (!(te instanceof TileEntityMonitorDesk)) {
            SESSIONS.remove(id);
            return false;
        }
        if (!session.fingerprint.equals(
                ((TileEntityMonitorDesk) te).getPasswordFingerprint())) {
            SESSIONS.remove(id);
            return false;
        }

        session.lastUse = now;
        return true;
    }

    /** Drops one player's session, for example after the terminal GUI closes. */
    public static void clear(EntityPlayer player) {
        if (player != null) SESSIONS.remove(player.getUniqueID());
    }

    /** Drops a player's session only when it belongs to the given desk. */
    public static void clear(EntityPlayer player, BlockPos desk) {
        if (player == null || desk == null) return;
        Session session = SESSIONS.get(player.getUniqueID());
        if (session != null && session.desk.equals(desk)) {
            SESSIONS.remove(player.getUniqueID());
        }
    }

    private MonitorAuth() {
    }
}
