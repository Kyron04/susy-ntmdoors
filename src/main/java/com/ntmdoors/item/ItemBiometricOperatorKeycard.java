package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Biometric Operator Keycard. Everything the Operator Keycard does - privileged card, sneak
 * right-click on a bound scanner to set its security code - plus a biometric identity and an aqua
 * name. The identity is bound onto it by the programmer, never automatically.
 */
public class ItemBiometricOperatorKeycard extends ItemOperatorKeycard {

    public ItemBiometricOperatorKeycard() {
        super("biometric_operator_keycard");
    }

    @Override
    protected Formatting nameColour() {
        return Formatting.AQUA;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Does exactly what the normal Operator Keycard does",
                "while storing your biometric data.",
                "Sneak + Right Click a Redstone Gate for its screen.");
        Biometrics.addOwnerLine(tooltip, stack);
        PrivateKeys.addTooltip(tooltip, stack);
    }
}
