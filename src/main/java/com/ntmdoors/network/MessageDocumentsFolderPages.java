package com.ntmdoors.network;

import com.ntmdoors.item.ItemDocumentsFolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client to server: the Documents Folder editor closed. The client has already written its local
 * copy; the server clamps the list, writes it onto the held folder and refreshes the inventory, so
 * the pages survive relogging and reach every other viewer of the same stack.
 */
public class MessageDocumentsFolderPages implements IMessage {

    private int hand;
    private List<String> pages = new ArrayList<>();

    public MessageDocumentsFolderPages() {
    }

    public MessageDocumentsFolderPages(EnumHand hand, List<String> pages) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.pages = pages == null ? new ArrayList<>() : pages;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readUnsignedByte();
        int count = Math.min(buf.readUnsignedByte(), ItemDocumentsFolder.PAGE_COUNT);
        pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand);
        int count = Math.min(pages.size(), ItemDocumentsFolder.PAGE_COUNT);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            String page = pages.get(i);
            ByteBufUtils.writeUTF8String(buf, page == null ? "" : page);
        }
    }

    public static class Handler implements IMessageHandler<MessageDocumentsFolderPages, IMessage> {

        @Override
        public IMessage onMessage(MessageDocumentsFolderPages message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageDocumentsFolderPages message) {
            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemDocumentsFolder)) return;

            ItemDocumentsFolder.setPages(stack, ItemDocumentsFolder.clean(message.pages));
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
    }
}
