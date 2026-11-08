package com.ntmdoors.network;

import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the Weapons Relay Key screen selected a binding mode. */
public class MessageWeaponsKeyMode implements IMessage {

    private int hand;
    private int mode;

    public MessageWeaponsKeyMode() {
    }

    public MessageWeaponsKeyMode(EnumHand hand, WeaponsMode mode) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.mode = mode.id;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readByte();
        mode = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand);
        buf.writeByte(mode);
    }

    public static class Handler implements IMessageHandler<MessageWeaponsKeyMode, IMessage> {

        @Override
        public IMessage onMessage(MessageWeaponsKeyMode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageWeaponsKeyMode message) {
            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemWeaponsRelayKey)) return;

            WeaponsMode mode = WeaponsMode.byId(message.mode);
            WeaponsKeyData.setMode(stack, mode);
            WeaponsKeyData.clearSession(stack);
            HeldItems.sync(player);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.weapons_mode_changed", mode.displayName), true);
        }
    }
}
