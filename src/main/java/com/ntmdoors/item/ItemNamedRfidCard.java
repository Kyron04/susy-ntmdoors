package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * RFID card with a fixed name and a line of flavour text.
 *
 * <p>It behaves exactly like a normal colour card: the programmer can copy private keys and a
 * biometric identity onto it, scanners accept it, and sneak right-click opens the key viewer. Its
 * themed sprite and description stick to the card until the programmer's second-page palette is
 * used to change its style.</p>
 */
public class ItemNamedRfidCard extends ItemRfidCard {

    private final String[] description;
    @Nullable
    private final TextFormatting nameColour;

    public ItemNamedRfidCard(String name, String... description) {
        this(null, name, description);
    }

    /** Same card, with a fixed name colour; used for the gold Googa card. */
    public ItemNamedRfidCard(@Nullable TextFormatting nameColour, String name, String... description) {
        super(name);
        this.nameColour = nameColour;
        this.description = description;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        return nameColour == null ? name : nameColour + name;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip, description);
        super.addInformation(stack, world, tooltip, flag);
    }
}
