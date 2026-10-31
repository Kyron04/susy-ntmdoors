package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric Operator Keycard. Everything the Operator Keycard does - privileged card, sneak
 * right-click on a bound scanner to set its security code - plus a recorded owner and a blue
 * name. The owner is carried now so the access check can compare it later.
 */
public class ItemBiometricOperatorKeycard extends ItemOperatorKeycard {

    public ItemBiometricOperatorKeycard() {
        super("biometric_operator_keycard");
    }

    @Override
    protected TextFormatting nameColour() {
        return TextFormatting.AQUA;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        Biometrics.tick(stack, world, entity);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        Biometrics.addOwnerLine(tooltip, stack);
    }
}
