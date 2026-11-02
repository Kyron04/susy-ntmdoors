package com.ntmdoors.hlblocks;

import com.ntmdoors.concrete.ConcreteGroups;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server side of the shift-scroll cycle. Handles the HL metadata groups and the shared concrete
 * families; the concrete families replace the held stack because they span several block ids.
 */
public class CycleHandler implements IMessageHandler<CycleMessage, IMessage> {

    @Override
    public IMessage onMessage(final CycleMessage message, MessageContext ctx) {
        final EntityPlayerMP player = ctx.getServerHandler().player;
        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> cycle(player, message));
        return null;
    }

    private static void cycle(EntityPlayer player, CycleMessage message) {
        EnumHand hand = message.getHand();

        // Make sure the player is still holding the same item in the same place.
        int heldSlot = hand == EnumHand.OFF_HAND ? -1 : player.inventory.currentItem;
        if (heldSlot != message.getSlot()) {
            return;
        }

        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            return;
        }

        if (held.getItem() instanceof ItemBlockGroup) {
            ItemBlockGroup item = (ItemBlockGroup) held.getItem();
            int count = item.getVariantCount();
            if (count <= 1) {
                return;
            }
            held.setItemDamage(Math.floorMod(held.getMetadata() + message.getOffset(), count));
            return;
        }

        ItemStack next = ConcreteGroups.next(held, message.getOffset());
        if (next != null) {
            player.setHeldItem(hand, next);
        }
    }
}
