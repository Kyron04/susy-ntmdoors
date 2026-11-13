package com.ntmdoors.network;

import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: choose the cosmetic wallpaper for one physical monitor screen.
 * Empty means the normal terminal and {@code builtin:<id>} is a packaged image. Remote URLs are
 * deliberately not supported; the server stores only a packaged wallpaper id.
 */
public class MessageMonitorWallpaper implements IMessage {

    private BlockPos deskPos;
    private int screenIndex;
    private String wallpaper = "";

    public MessageMonitorWallpaper() {
    }

    public MessageMonitorWallpaper(BlockPos deskPos, int screenIndex, String wallpaper) {
        this.deskPos = deskPos;
        this.screenIndex = screenIndex;
        this.wallpaper = wallpaper == null ? "" : wallpaper;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        screenIndex = buf.readByte();
        wallpaper = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeByte(screenIndex);
        ByteBufUtils.writeUTF8String(buf, wallpaper);
    }

    public static class Handler implements IMessageHandler<MessageMonitorWallpaper, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorWallpaper message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageMonitorWallpaper message) {
            if (message.deskPos == null || !NetworkUtil.isWithinReach(player, message.deskPos)) {
                return;
            }
            if (!MonitorWallpaper.isValid(message.wallpaper)) return;
            TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(player.world, message.deskPos);
            if (desk == null) return;
            desk.setScreenWallpaper(message.screenIndex, MonitorWallpaper.normalize(message.wallpaper));
        }
    }
}
