package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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
    private final Formatting nameColour;

    public ItemNamedRfidCard(String name, String... description) {
        this(null, name, description);
    }

    /** Same card, with a fixed name colour; used for the gold Googa card. */
    public ItemNamedRfidCard(@Nullable Formatting nameColour, String name, String... description) {
        super(name);
        this.nameColour = nameColour;
        this.description = description;
    }

    @Override
    public Text getName(ItemStack stack) {
        Text text = super.getName(stack);
        return nameColour == null ? text : text.copy().formatted(nameColour);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip, description);
        super.appendTooltip(stack, world, tooltip, context);
    }
}
