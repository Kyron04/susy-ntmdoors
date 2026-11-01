package com.ntmdoors.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/** Wire helpers shared by the safe and scanner messages. */
public final class NetworkUtil {

    /** Squared reach allowed for any safe or scanner screen action. */
    private static final double INTERACTION_DISTANCE_SQ = 64.0D;

    /** Writes an optional block position as a presence flag followed by its long form. */
    public static void writePos(ByteBuf buf, @Nullable BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
    }

    /** Reads a position written by {@link #writePos}. */
    @Nullable
    public static BlockPos readPos(ByteBuf buf) {
        return buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
    }

    /** True when {@code player} is close enough to act on the block at {@code pos}. */
    public static boolean isWithinReach(EntityPlayer player, BlockPos pos) {
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                <= INTERACTION_DISTANCE_SQ;
    }

    private NetworkUtil() {
    }
}
