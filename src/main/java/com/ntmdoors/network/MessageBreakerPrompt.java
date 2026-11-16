package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to client: ownership passed, so show the Breaker confirmation screen. */
public class MessageBreakerPrompt implements IMessage {

    private BlockPos pos;
    private String label;

    public MessageBreakerPrompt() {
    }

    public MessageBreakerPrompt(BlockPos pos, String label) {
        this.pos = pos;
        this.label = label == null ? "target" : label;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        label = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        ByteBufUtils.writeUTF8String(buf, label == null ? "target" : label);
    }

    public static class Handler implements IMessageHandler<MessageBreakerPrompt, IMessage> {

        @Override
        public IMessage onMessage(MessageBreakerPrompt message, MessageContext ctx) {
            if (message.pos != null) {
                NTMDoors.proxy.openBreakerConfirm(message.pos, message.label);
            }
            return null;
        }
    }
}
