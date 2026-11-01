package com.ntmdoors.network;

import com.ntmdoors.gui.ContainerSafe;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: which page of the safe container is on screen, so hidden slots stay locked. */
public class MessageSafePage implements IMessage {

    private BlockPos pos;
    private int page;

    public MessageSafePage() {
    }

    public MessageSafePage(BlockPos pos, int page) {
        this.pos = pos;
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        page = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        buf.writeByte(page);
    }

    public static class Handler implements IMessageHandler<MessageSafePage, IMessage> {

        @Override
        public IMessage onMessage(MessageSafePage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.pos == null) return;
                if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                        message.pos.getZ() + 0.5D) > 64D) {
                    return;
                }
                if (!(player.openContainer instanceof ContainerSafe)) return;
                ContainerSafe container = (ContainerSafe) player.openContainer;
                if (!container.getTile().getPos().equals(message.pos)) return;
                container.setPage(message.page);
            });
            return null;
        }
    }
}
