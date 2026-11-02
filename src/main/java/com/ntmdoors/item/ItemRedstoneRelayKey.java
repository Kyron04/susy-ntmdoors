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
 * Redstone Relay Key. Reserved for binding keypads, scanners and safes to a Redstone Gate; the
 * binding logic is not implemented yet, so the item currently carries only its controls.
 */
public class ItemRedstoneRelayKey extends Item {

    public ItemRedstoneRelayKey() {
        setRegistryName(NTMDoors.MODID, "redstone_relay_key");
        setTranslationKey(NTMDoors.MODID + ".redstone_relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.RED + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Allows Keypads, Keycard Scanners, and Safes",
                "to be bound to the Redstone Gate");
    }
}
