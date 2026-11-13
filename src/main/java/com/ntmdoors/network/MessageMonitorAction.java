package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.monitor.MonitorAuth;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: password actions for one monitor desk. The client sends the SHA-256 hash rather
 * than the plain password; the server only stores and compares hashes.
 */
public class MessageMonitorAction implements IMessage {

    public static final int SET_PASSWORD = 0;
    public static final int SKIP_PASSWORD = 1;
    public static final int CHECK_PASSWORD = 2;

    private BlockPos pos;
    private int action;
    private String passwordHash;

    public MessageMonitorAction() {
    }

    public MessageMonitorAction(BlockPos pos, int action, String passwordHash) {
        this.pos = pos;
        this.action = action;
        this.passwordHash = passwordHash;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        action = buf.readByte();
        passwordHash = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeByte(action);
        ByteBufUtils.writeUTF8String(buf, passwordHash == null ? "" : passwordHash);
    }

    public static class Handler implements IMessageHandler<MessageMonitorAction, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageMonitorAction message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntityMonitorDesk monitor = TileEntityMonitorDesk.at(player.world, message.pos);
            if (monitor == null) {
                ModNetwork.INSTANCE.sendTo(new MessageMonitorState(message.pos,
                        MessageMonitorState.UNAVAILABLE, false, false), player);
                return;
            }

            int result;
            switch (message.action) {
                case SET_PASSWORD:
                    result = monitor.setPasswordHash(message.passwordHash)
                            ? MessageMonitorState.SET_OK : MessageMonitorState.INVALID;
                    break;
                case SKIP_PASSWORD:
                    result = monitor.skipPasswordSetup()
                            ? MessageMonitorState.SKIP_OK : MessageMonitorState.INVALID;
                    break;
                case CHECK_PASSWORD:
                    result = monitor.checkPasswordHash(message.passwordHash)
                            ? MessageMonitorState.CHECK_OK : MessageMonitorState.WRONG_PASSWORD;
                    break;
                default:
                    result = MessageMonitorState.INVALID;
                    break;
            }

            ModNetwork.INSTANCE.sendTo(new MessageMonitorState(message.pos, result,
                    monitor.hasPassword(), monitor.isPasswordSkipped()), player);

            if (result == MessageMonitorState.SET_OK
                    || result == MessageMonitorState.SKIP_OK
                    || result == MessageMonitorState.CHECK_OK) {
                MonitorAuth.authorize(player, message.pos);
            }
        }
    }
}
