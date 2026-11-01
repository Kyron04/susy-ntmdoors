package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * RFID card with a fixed name and a line of flavour text.
 *
 * <p>It behaves exactly like a normal colour card: the programmer can copy private keys and a
 * biometric identity onto it, scanners accept it, and sneak right-click opens the key viewer. It is
 * simply not part of the recolour palette, so its themed sprite and description stay put.</p>
 */
public class ItemNamedRfidCard extends ItemRfidCard {

    private final String[] description;

    public ItemNamedRfidCard(String name, String... description) {
        super(name);
        this.description = description;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip, description);
        super.addInformation(stack, world, tooltip, flag);
    }
}
