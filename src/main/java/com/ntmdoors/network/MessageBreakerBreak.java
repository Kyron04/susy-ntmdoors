package com.ntmdoors.network;

import com.ntmdoors.item.ItemBreakerRelayKey;
import com.ntmdoors.util.HeldItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the player confirmed the Breaker screen. */
public class MessageBreakerBreak implements IMessage {

    private BlockPos pos;

    public MessageBreakerBreak() {
    }

    public MessageBreakerBreak(BlockPos pos) {
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

    public static class Handler implements IMessageHandler<MessageBreakerBreak, IMessage> {

        @Override
        public IMessage onMessage(MessageBreakerBreak message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageBreakerBreak message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;
            if (!HeldItems.holds(player, ItemBreakerRelayKey.class)) return;

            if (ItemBreakerRelayKey.breakTarget(player, player.world, message.pos)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.breaker_done"), true);
            } else {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.breaker_failed"), true);
            }
        }
    }
}
