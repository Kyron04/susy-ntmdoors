package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityEmancipationGrill;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player used an Emancipation Relay Key on a grill core. The server checks
 * the key and ownership, then sends back the security core to open (or a denial).
 */
public class MessageGrillAccess implements IMessage {

    private BlockPos pos;

    public MessageGrillAccess() {
    }

    public MessageGrillAccess(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
    }

    public static class Handler implements IMessageHandler<MessageGrillAccess, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillAccess message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGrillAccess message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityEmancipationGrill)) return;

            TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
            boolean allowed = grill.hasConsoleAccess(player);
            ModNetwork.INSTANCE.sendTo(
                    new MessageGrillAccessResult(grill.getPos(), allowed), player);
        }
    }
}
