package com.ntmdoors.network;

import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.TerminalKeyData;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player confirmed a name in the device naming prompt. The server adds or
 * renames the device in the held Terminal Relay Key's pending session.
 */
public class MessageTerminalBind implements IMessage {

    private int hand;
    private BlockPos pos;
    private String name;

    public MessageTerminalBind() {
    }

    public MessageTerminalBind(EnumHand hand, BlockPos pos, String name) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.pos = pos;
        this.name = name;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readByte();
        pos = BlockPos.fromLong(buf.readLong());
        name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand);
        buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
    }

    public static class Handler implements IMessageHandler<MessageTerminalBind, IMessage> {

        @Override
        public IMessage onMessage(MessageTerminalBind message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageTerminalBind message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;

            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemTerminalRelayKey)) return;

            TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
            if (session == null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.terminal_binding_required"), true);
                return;
            }
            if (session.dimension != player.world.provider.getDimension()) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.terminal_binding_wrong_dimension"), true);
                return;
            }

            MonitorDevice device = MonitorDevice.find(player.world, message.pos);
            if (device == null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.terminal_device_invalid"), true);
                return;
            }

            String name = PrivateKeys.sanitiseName(message.name);
            if (name.isEmpty()) name = device.type.displayName();
            MonitorDevice bound = device.withName(name);
            TerminalKeyData.addOrUpdate(stack, bound);
            HeldItems.sync(player);

            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_device_bound", bound.name, bound.type.displayName()), true);
            player.world.playSound(null, message.pos, ModSounds.BUTTON9,
                    SoundCategory.BLOCKS, 0.8F, 1F);
        }
    }
}
