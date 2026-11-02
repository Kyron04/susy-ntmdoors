package com.ntmdoors.network;

import com.ntmdoors.client.GuiKeypadSecurityCode;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to client: outcome of a keypad GUI security-code entry. */
public class MessageKeypadSecurityCodeResult implements IMessage {

    public static final int ACCEPTED = 0;
    public static final int SET = 1;
    public static final int DENIED = 2;

    private BlockPos pos;
    private int result;

    public MessageKeypadSecurityCodeResult() {
    }

    public MessageKeypadSecurityCodeResult(BlockPos pos, int result) {
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

    public static class Handler implements IMessageHandler<MessageKeypadSecurityCodeResult, IMessage> {

        @Override
        public IMessage onMessage(MessageKeypadSecurityCodeResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiKeypadSecurityCode) {
                    ((GuiKeypadSecurityCode) screen).onResult(message.pos, message.result);
                }
            });
            return null;
        }
    }
}
