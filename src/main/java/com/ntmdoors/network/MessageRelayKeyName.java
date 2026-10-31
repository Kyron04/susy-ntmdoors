package com.ntmdoors.network;

import com.ntmdoors.item.ItemBiometricRelayKey;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player confirmed a name in the Relay Key naming screen. The server owns
 * serial generation and the one-key-only rule, so the client never writes the NBT itself.
 */
public class MessageRelayKeyName implements IMessage {

    private String name;
    private int hand;

    public MessageRelayKeyName() {
    }

    public MessageRelayKeyName(String name, EnumHand hand) {
        this.name = name;
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = ByteBufUtils.readUTF8String(buf);
        hand = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeByte(hand);
    }

    public static class Handler implements IMessageHandler<MessageRelayKeyName, IMessage> {

        @Override
        public IMessage onMessage(MessageRelayKeyName message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> create(player, message));
            return null;
        }

        private static void create(EntityPlayerMP player, MessageRelayKeyName message) {
            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemRelayKey)) return;
            // The biometric key collects data instead of generating a key; it never opens this screen.
            if (stack.getItem() instanceof ItemBiometricRelayKey) return;
            if (!PrivateKeys.isBlank(stack)) return;

            String name = PrivateKeys.saniseName(message.name);
            if (name.isEmpty()) return;

            PrivateKeys.add(stack, name, PrivateKeys.createSerial());
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.BUTTON9, SoundCategory.PLAYERS, 0.8F, 1F);
            player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.key_created", name), true);
        }
    }
}
