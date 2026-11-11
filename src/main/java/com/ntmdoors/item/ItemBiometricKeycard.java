package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.YELLOW);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Holds the biometric data of the player",
                "alongside the private key.");
        super.appendTooltip(stack, world, tooltip, context);
        if (!Biometrics.isBound(stack)) Biometrics.addOwnerLine(tooltip, stack);
    }
}
