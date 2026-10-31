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
 * Biometric Keycard. Behaves exactly like a colour RFID card - the programmer can carry private
 * keys onto it and the scanner reads it - but it also records the player it first entered, and
 * shows that owner in its tooltip.
 *
 * <p>Its name is yellow and its sprite is fixed: unlike the seven colour cards it is not a
 * recolourable variant, because the yellow band is what marks it as the biometric one.</p>
 */
public class ItemBiometricKeycard extends ItemRfidCard {

    public ItemBiometricKeycard() {
        super("biometric_keycard");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.YELLOW + super.getItemStackDisplayName(stack);
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
