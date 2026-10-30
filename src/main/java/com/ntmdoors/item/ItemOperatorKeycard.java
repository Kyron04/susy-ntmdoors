package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Operator Keycard. A privileged card produced by the Keycard Programmer; its access rules will be
 * defined later. It shares the RFID card item model and hand display, so it is also recognised by
 * the scanner as a card. Its name is orange and its stored keys are listed in the card viewer.
 */
public class ItemOperatorKeycard extends ItemRfidCard {

    public ItemOperatorKeycard() {
        super("operator_keycard");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GOLD + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "The master keycard of your security network.",
                "Holds several private keys that can be assigned",
                "to scanners bound to doors");
    }
}
