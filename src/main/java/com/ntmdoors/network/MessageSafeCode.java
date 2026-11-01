package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntitySafe;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: one keypad submission on a Code Safe. The server owns the code, so the client
 * only ever sends digits and is told whether the safe was set, unlocked or refused.
 */
public class MessageSafeCode implements IMessage {

    private BlockPos pos;
    private String code;

    public MessageSafeCode() {
    }

    public MessageSafeCode(BlockPos pos, String code) {
        this.pos = pos;
        this.code = code;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        code = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        ByteBufUtils.writeUTF8String(buf, code == null ? "" : code);
    }

    public static class Handler implements IMessageHandler<MessageSafeCode, IMessage> {

        @Override
        public IMessage onMessage(MessageSafeCode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> submit(player, message));
            return null;
        }

        private static void submit(EntityPlayerMP player, MessageSafeCode message) {
            if (message.pos == null) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) {
                deny(player, message.pos);
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntitySafe)) {
                deny(player, message.pos);
                return;
            }
            TileEntitySafe safe = (TileEntitySafe) te;
            int result = safe.submitCode(player, message.code);
            ModNetwork.INSTANCE.sendTo(new MessageSafeResult(message.pos, result), player);
        }

        private static void deny(EntityPlayerMP player, BlockPos pos) {
            ModNetwork.INSTANCE.sendTo(new MessageSafeResult(pos, MessageSafeResult.DENIED), player);
        }
    }
}
