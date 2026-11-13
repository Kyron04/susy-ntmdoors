package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.monitor.MonitorAuth;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: opens the Terminal Backup console for one monitor desk. */
public class MessageMonitorBackupOpen implements IMessage {

    private BlockPos deskPos;
    private boolean restore;

    public MessageMonitorBackupOpen() {
    }

    public MessageMonitorBackupOpen(BlockPos deskPos, boolean restore) {
        this.deskPos = deskPos;
        this.restore = restore;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        restore = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeBoolean(restore);
    }

    public static class Handler implements IMessageHandler<MessageMonitorBackupOpen, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorBackupOpen message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> open(player, message));
            return null;
        }

        private static void open(EntityPlayerMP player, MessageMonitorBackupOpen message) {
            if (message.deskPos == null || !NetworkUtil.isWithinReach(player, message.deskPos)) {
                return;
            }
            TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(player.world, message.deskPos);
            if (desk == null) return;
            if (!MonitorAuth.isAuthorized(player, message.deskPos)) return;
            player.openGui(NTMDoors.INSTANCE,
                    message.restore ? GuiHandler.TERMINAL_BACKUP_RESTORE
                            : GuiHandler.TERMINAL_BACKUP,
                    player.world, message.deskPos.getX(), message.deskPos.getY(),
                    message.deskPos.getZ());
        }
    }
}
