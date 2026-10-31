package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

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

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Overrides everything regardless of door type,",
                "binding state, etc");
    }

    /** True when either hand holds a Creative Keycard. */
    public static boolean isHeldBy(EntityPlayer player) {
        return isCreativeCard(player.getHeldItemMainhand()) || isCreativeCard(player.getHeldItemOffhand());
    }

    private static boolean isCreativeCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemCreativeKeycard;
    }
}
