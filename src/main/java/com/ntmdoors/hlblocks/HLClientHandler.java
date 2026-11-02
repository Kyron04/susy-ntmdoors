package com.ntmdoors.hlblocks;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.concrete.ConcreteGroups;
import com.ntmdoors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/** Client side of the HL/concrete shift-scroll cycle. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class HLClientHandler {

    /** Modifier for scroll cycling, rebindable in the controls screen. */
    private static final KeyBinding SCROLL_MODIFIER = new KeyBinding(
            "key." + NTMDoors.MODID + ".cycle",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_LSHIFT,
            "HL Blocks"
    );

    static {
        ClientRegistry.registerKeyBinding(SCROLL_MODIFIER);
    }

    private HLClientHandler() {
    }

    @SubscribeEvent
    public static void onMouseWheel(MouseEvent event) {
        int scroll = event.getDwheel();
        if (scroll == 0 || !SCROLL_MODIFIER.isKeyDown()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.player;
        if (player == null || minecraft.currentScreen != null) {
            return;
        }

        EnumHand hand = EnumHand.MAIN_HAND;
        if (!isCycleable(player.getHeldItem(hand))) {
            hand = EnumHand.OFF_HAND;
        }
        if (!isCycleable(player.getHeldItem(hand))) {
            return;
        }

        int slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : -1;
        ModNetwork.INSTANCE.sendToServer(new CycleMessage(hand, scroll > 0 ? 1 : -1, slot));

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    private static boolean isCycleable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ItemBlockGroup) {
            return ((ItemBlockGroup) stack.getItem()).getVariantCount() > 1;
        }
        return ConcreteGroups.isCycleable(stack);
    }
}
