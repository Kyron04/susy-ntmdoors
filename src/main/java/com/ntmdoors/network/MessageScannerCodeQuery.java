package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityKeycardScanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the code screen is opening, tell it whether a code is already set. This avoids
 * ever showing "Set a new security code" on a scanner that already has one, even if the client's
 * synced copy is a tick behind.
 */
public class MessageScannerCodeQuery implements IMessage {

    private BlockPos pos;

    public MessageScannerCodeQuery() {
    }

    public MessageScannerCodeQuery(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
    }

    public static class Handler implements IMessageHandler<MessageScannerCodeQuery, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerCodeQuery message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> answer(player, message));
            return null;
        }

        private static void answer(EntityPlayerMP player, MessageScannerCodeQuery message) {
            if (message.pos == null) return;
            if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                    message.pos.getZ() + 0.5D) > 64D) {
                return;
            }
            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) return;

            boolean set = ((TileEntityKeycardScanner) te).hasSecurityCode();
            ModNetwork.INSTANCE.sendTo(new MessageScannerCodeResult(message.pos,
                    set ? MessageScannerCodeResult.STATE_SET : MessageScannerCodeResult.STATE_UNSET), player);
        }
    }
}
