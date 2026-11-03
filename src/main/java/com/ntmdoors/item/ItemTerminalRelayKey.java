package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Terminal Relay Key. Reserved for binding devices to the Security Terminal.
 *
 * <p>The binding behaviour is not implemented yet; the key is only registered as an item and
 * carries its description for the later pass.</p>
 */
public class ItemTerminalRelayKey extends Item {

    public ItemTerminalRelayKey() {
        setRegistryName(NTMDoors.MODID, "terminal_relay_key");
        setTranslationKey(NTMDoors.MODID + ".terminal_relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GOLD + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Binds devices to the Security Terminal",
                "Sneak + Right Click");
    }
}
