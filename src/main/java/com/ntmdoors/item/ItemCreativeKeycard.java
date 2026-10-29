package com.ntmdoors.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

/**
 * Debug keycard. It opens every door regardless of locks, security, keypad bindings or redstone,
 * and its name is shown in purple like other mods' creative-only items.
 */
public class ItemCreativeKeycard extends ItemRfidCard {

    public ItemCreativeKeycard() {
        super("creative_keycard");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.LIGHT_PURPLE + super.getItemStackDisplayName(stack);
    }

    /** True when either hand holds a Creative Keycard. */
    public static boolean isHeldBy(EntityPlayer player) {
        return isCreativeCard(player.getHeldItemMainhand()) || isCreativeCard(player.getHeldItemOffhand());
    }

    private static boolean isCreativeCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemCreativeKeycard;
    }
}
