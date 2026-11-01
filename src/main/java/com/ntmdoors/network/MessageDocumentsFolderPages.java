package com.ntmdoors.network;

import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.util.HeldItems;
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
 * copy; the server clamps the list, writes the pages, the custom name and the page marker onto the
 * held folder, and refreshes the inventory, so a folder reopens on the same page after a relog.
 */
public class MessageDocumentsFolderPages implements IMessage {

    private int hand;
    private int page;
    private String name = "";
    private List<String> pages = new ArrayList<>();

    public MessageDocumentsFolderPages() {
    }

    public MessageDocumentsFolderPages(EnumHand hand, int page, String name, List<String> pages) {
        this.hand = hand == EnumHand.OFF_HAND ? 1 : 0;
        this.page = page;
        this.name = name == null ? "" : name;
        this.pages = pages == null ? new ArrayList<>() : pages;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readUnsignedByte();
        page = buf.readUnsignedByte();
        name = ByteBufUtils.readUTF8String(buf);
        int count = Math.min(buf.readUnsignedByte(), ItemDocumentsFolder.PAGE_COUNT);
        pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand);
        buf.writeByte(Math.max(0, Math.min(ItemDocumentsFolder.PAGE_COUNT - 1, page)));
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        int count = Math.min(pages.size(), ItemDocumentsFolder.PAGE_COUNT);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            String pageText = pages.get(i);
            ByteBufUtils.writeUTF8String(buf, pageText == null ? "" : pageText);
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

            List<String> pages = ItemDocumentsFolder.clean(message.pages);
            ItemDocumentsFolder.setPages(stack, pages);
            ItemDocumentsFolder.setCustomName(stack, message.name);
            ItemDocumentsFolder.setLastPage(stack, pages.isEmpty() ? 0 : message.page);

            HeldItems.sync(player);
        }
    }
}
