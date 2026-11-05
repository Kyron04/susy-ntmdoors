package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityMonitorDesk;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the live page currently shown by an open terminal. The desk stores the page
 * and mirrors it onto every physical screen; closing the terminal clears the viewing flag so a
 * password-protected desk falls back to its lock screen.
 */
public class MessageMonitorDisplay implements IMessage {

    private BlockPos deskPos;
    private int screenIndex;
    private boolean viewing;
    private int stage;
    private int netId;
    private int notepadDocId = -1;
    private boolean eventsTab;
    private int netsScroll;
    private int securityScroll;
    private int eventsScroll;

    public MessageMonitorDisplay() {
    }

    public MessageMonitorDisplay(BlockPos deskPos, int screenIndex, boolean viewing, int stage,
                                 int netId, boolean eventsTab, int netsScroll,
                                 int securityScroll, int eventsScroll) {
        this(deskPos, screenIndex, viewing, stage, netId, -1, eventsTab, netsScroll,
                securityScroll, eventsScroll);
    }

    public MessageMonitorDisplay(BlockPos deskPos, int screenIndex, boolean viewing, int stage,
                                 int netId, int notepadDocId, boolean eventsTab,
                                 int netsScroll, int securityScroll, int eventsScroll) {
        this.deskPos = deskPos;
        this.screenIndex = screenIndex;
        this.viewing = viewing;
        this.stage = stage;
        this.netId = netId;
        this.notepadDocId = notepadDocId;
        this.eventsTab = eventsTab;
        this.netsScroll = netsScroll;
        this.securityScroll = securityScroll;
        this.eventsScroll = eventsScroll;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        screenIndex = buf.readByte();
        viewing = buf.readBoolean();
        stage = buf.readByte();
        netId = buf.readInt();
        notepadDocId = buf.readInt();
        eventsTab = buf.readBoolean();
        netsScroll = buf.readShort();
        securityScroll = buf.readShort();
        eventsScroll = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeByte(screenIndex);
        buf.writeBoolean(viewing);
        buf.writeByte(stage);
        buf.writeInt(netId);
        buf.writeInt(notepadDocId);
        buf.writeBoolean(eventsTab);
        buf.writeShort(netsScroll);
        buf.writeShort(securityScroll);
        buf.writeShort(eventsScroll);
    }

    public static class Handler implements IMessageHandler<MessageMonitorDisplay, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorDisplay message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageMonitorDisplay message) {
            if (message.deskPos == null || !NetworkUtil.isWithinReach(player, message.deskPos)) {
                return;
            }
            TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(player.world, message.deskPos);
            if (desk == null) return;
            desk.setScreenDisplayState(message.screenIndex, message.stage, message.netId,
                    message.notepadDocId, message.eventsTab, message.netsScroll,
                    message.securityScroll, message.eventsScroll,
                    player.getUniqueID(), message.viewing);
        }
    }
}
