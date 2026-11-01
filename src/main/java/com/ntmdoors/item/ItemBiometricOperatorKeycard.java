package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric Operator Keycard. Everything the Operator Keycard does - privileged card, sneak
 * right-click on a bound scanner to set its security code - plus a biometric identity and a blue
 * name. The identity is bound onto it by the programmer, never automatically.
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
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Does exactly what the normal Operator Keycard does",
                "while storing your biometric data.");
        Biometrics.addOwnerLine(tooltip, stack);
        // The card can carry private keys just like an RFID card; show them instead of hiding
        // the administrator data behind the sneak-right-click viewer.
        PrivateKeys.addTooltip(tooltip, stack);
    }
}
