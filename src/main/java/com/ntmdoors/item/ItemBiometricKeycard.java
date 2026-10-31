package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric Keycard. Behaves exactly like a colour RFID card - the programmer can carry private
 * keys onto it and the scanner reads it - but it also carries a biometric identity, which the
 * programmer binds onto it from a Biometric Relay Key.
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
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Holds the biometric data of the player",
                "alongside the private key");
        // The base card lists the private keys and then the identity line when one exists. An
        // unbound card is still worth labelling, so add the empty state here.
        super.addInformation(stack, world, tooltip, flag);
        if (!Biometrics.isBound(stack)) Biometrics.addOwnerLine(tooltip, stack);
    }
}
