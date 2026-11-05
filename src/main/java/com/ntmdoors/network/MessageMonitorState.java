package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: opens the monitor terminal or reports the result of a password action.
 *
 * <p>{@link #OPEN} carries the current password flags for the clicked desk. All other results are
 * replies to {@link MessageMonitorAction} and are routed back into the open {@code GuiMonitor}.</p>
 */
public class MessageMonitorState implements IMessage {

    public static final int OPEN = 0;
    public static final int SET_OK = 1;
    public static final int SKIP_OK = 2;
    public static final int CHECK_OK = 3;
    public static final int WRONG_PASSWORD = 4;
    public static final int INVALID = 5;
    public static final int UNAVAILABLE = 6;

    private BlockPos pos;
    private int result;
    private boolean hasPassword;
    private boolean passwordSkipped;
    private int screenIndex = -1;

    public MessageMonitorState() {
    }

    public MessageMonitorState(BlockPos pos, int result, boolean hasPassword, boolean passwordSkipped) {
        this(pos, result, hasPassword, passwordSkipped, -1);
    }

    public MessageMonitorState(BlockPos pos, int result, boolean hasPassword,
                               boolean passwordSkipped, int screenIndex) {
        this.pos = pos;
        this.result = result;
        this.hasPassword = hasPassword;
        this.passwordSkipped = passwordSkipped;
        this.screenIndex = screenIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        result = buf.readByte();
        hasPassword = buf.readBoolean();
        passwordSkipped = buf.readBoolean();
        screenIndex = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeByte(result);
        buf.writeBoolean(hasPassword);
        buf.writeBoolean(passwordSkipped);
        buf.writeByte(screenIndex);
    }

    public static class Handler implements IMessageHandler<MessageMonitorState, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorState message, MessageContext ctx) {
            if (message.pos == null) return null;
            NTMDoors.proxy.handleMonitorState(message.pos, message.result,
                    message.hasPassword, message.passwordSkipped, message.screenIndex);
            return null;
        }
    }
}
