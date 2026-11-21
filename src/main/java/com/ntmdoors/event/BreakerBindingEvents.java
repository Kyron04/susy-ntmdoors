// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemBreakerRelayKey;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * Gives a held Breaker Relay Key priority over door and safe activation. Door blocks consume the
 * normal right-click path before the item hooks in some interaction states, so this event is the
 * reliable way to claim a sneak-click on a door cell.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class BreakerBindingEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !player.isSneaking()) return;
        if (breakerKeyHand(player) == null) return;

        EnumActionResult result = ItemBreakerRelayKey.useBreaker(
                player, event.getWorld(), event.getPos());
        if (result == EnumActionResult.PASS) return;

        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    @Nullable
    private static EnumHand breakerKeyHand(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemBreakerRelayKey) return EnumHand.MAIN_HAND;

        ItemStack off = player.getHeldItemOffhand();
        return off.getItem() instanceof ItemBreakerRelayKey ? EnumHand.OFF_HAND : null;
    }

    private BreakerBindingEvents() {
    }
}
