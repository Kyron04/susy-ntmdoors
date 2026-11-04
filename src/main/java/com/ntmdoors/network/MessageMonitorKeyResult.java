package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to client: result of binding, unlinking or renaming a net's Terminal Relay Key. */
public class MessageMonitorKeyResult implements IMessage {

    public static final int BOUND = 0;
    public static final int UNLINKED = 1;
    public static final int RENAMED = 2;
    public static final int NO_KEY = 3;
    public static final int NO_NET = 4;
    public static final int KEY_ALREADY_LINKED = 5;
    public static final int NET_ALREADY_LINKED = 6;

    private BlockPos deskPos;
    private int netId;
    private int result;
    private String keyName;

    public MessageMonitorKeyResult() {
    }

    public MessageMonitorKeyResult(BlockPos deskPos, int netId, int result, String keyName) {
        this.deskPos = deskPos;
        this.netId = netId;
        this.result = result;
        this.keyName = keyName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        netId = buf.readInt();
        result = buf.readByte();
        keyName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeInt(netId);
        buf.writeByte(result);
        ByteBufUtils.writeUTF8String(buf, keyName == null ? "" : keyName);
    }

    public static class Handler implements IMessageHandler<MessageMonitorKeyResult, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorKeyResult message, MessageContext ctx) {
            if (message.deskPos != null) {
                NTMDoors.proxy.handleMonitorKeyResult(
                        message.deskPos, message.netId, message.result, message.keyName);
            }
            return null;
        }
    }
}
