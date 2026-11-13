package com.ntmdoors.network;

import com.ntmdoors.item.ItemUndefinedRelayKey;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * Client to server: the Undefined Relay Key screen chose a key type. The server validates the
 * index against its own type list, then replaces the held programming key with a fresh blank key.
 */
public class MessageRelayKeyType implements IMessage {

    private int hand;
    private int type;

    public MessageRelayKeyType() {
    }

    public MessageRelayKeyType(EnumHand hand, int type) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.type = type;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readUnsignedByte();
        type = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand);
        buf.writeByte(type);
    }

    public static class Handler implements IMessageHandler<MessageRelayKeyType, IMessage> {

        @Override
        public IMessage onMessage(MessageRelayKeyType message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageRelayKeyType message) {
            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemUndefinedRelayKey)) return;

            List<Item> types = ModContent.UNDEFINED_KEY_TYPES;
            if (message.type < 0 || message.type >= types.size()) return;

            ItemStack converted = new ItemStack(types.get(message.type));
            player.setHeldItem(hand, converted);
            HeldItems.sync(player);

            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.SCANNER_BUTTON3, SoundCategory.PLAYERS, 0.8F, 1F);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.key_programmed", converted.getDisplayName()), true);
        }
    }
}
