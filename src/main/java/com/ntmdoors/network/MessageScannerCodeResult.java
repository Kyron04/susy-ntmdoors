package com.ntmdoors.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: the outcome of a scanner security code entry. The screen closes on a set or
 * accepted code and stays open with "Access denied" after a mismatch. Client classes are only
 * referenced inside the handler body, so a dedicated server never loads them.
 */
public class MessageScannerCodeResult implements IMessage {

    public static final int ACCEPTED = 0;
    public static final int SET = 1;
    public static final int DENIED = 2;
    public static final int UNBOUND = 3;

    private BlockPos pos;
    private int result;

    public MessageScannerCodeResult() {
    }

    public MessageScannerCodeResult(BlockPos pos, int result) {
        this.pos = pos;
        this.result = result;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        result = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        buf.writeByte(result);
    }

    public static class Handler implements IMessageHandler<MessageScannerCodeResult, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerCodeResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof com.ntmdoors.client.GuiScannerCode) {
                    ((com.ntmdoors.client.GuiScannerCode) screen).onResult(message.pos, message.result);
                }
            });
            return null;
        }
    }
}
