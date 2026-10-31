package com.ntmdoors.network;

import com.ntmdoors.gui.ContainerKeycardProgrammer;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the programmer GUI changed pages. The server container needs the same page so
 * shift-clicking only fills slots that are actually on screen.
 */
public class MessageProgrammerPage implements IMessage {

    private int page;

    public MessageProgrammerPage() {
    }

    public MessageProgrammerPage(int page) {
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(page);
    }

    public static class Handler implements IMessageHandler<MessageProgrammerPage, IMessage> {

        @Override
        public IMessage onMessage(MessageProgrammerPage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerKeycardProgrammer) {
                    ((ContainerKeycardProgrammer) player.openContainer).setPage(message.page);
                }
            });
            return null;
        }
    }
}
