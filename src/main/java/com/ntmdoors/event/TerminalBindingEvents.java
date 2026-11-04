package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemTerminalRelayKey;
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
 * Gives a held Terminal Relay Key priority over block activation, {@link GuardEvents} and the
 * speaker's sneak-click screen. The handler checks both hands because the key is often carried in
 * the offhand.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class TerminalBindingEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !player.isSneaking()) return;

        EnumHand hand = terminalKeyHand(player);
        if (hand == null) return;

        EnumActionResult result = ItemTerminalRelayKey.useTerminalKey(
                player, event.getWorld(), event.getPos(), hand);
        if (result == EnumActionResult.PASS) return;

        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    @Nullable
    private static EnumHand terminalKeyHand(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemTerminalRelayKey) return EnumHand.MAIN_HAND;

        ItemStack off = player.getHeldItemOffhand();
        return off.getItem() instanceof ItemTerminalRelayKey ? EnumHand.OFF_HAND : null;
    }

    private TerminalBindingEvents() {
    }
}
