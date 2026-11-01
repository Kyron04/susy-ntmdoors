package com.ntmdoors.network;

import com.ntmdoors.client.GuiSafeBiometric;
import com.ntmdoors.client.GuiSafeCode;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: the outcome of a safe code or biometric read. The success / failure card is
 * shown for a moment before the server opens the container.
 */
public class MessageSafeResult implements IMessage {

    public static final int ACCEPTED = 0;
    public static final int SET = 1;
    public static final int DENIED = 2;
    public static final int LOCKED_OUT = 3;

    private BlockPos pos;
    private int result;

    public MessageSafeResult() {
    }

    public MessageSafeResult(BlockPos pos, int result) {
        this.pos = pos;
        this.result = result;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        result = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeByte(result);
    }

    public static class Handler implements IMessageHandler<MessageSafeResult, IMessage> {

        @Override
        public IMessage onMessage(MessageSafeResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiSafeCode) {
                    ((GuiSafeCode) screen).onSafeResult(message.pos, message.result);
                } else if (screen instanceof GuiSafeBiometric) {
                    ((GuiSafeBiometric) screen).onSafeResult(message.pos, message.result);
                }
            });
            return null;
        }
    }
}
