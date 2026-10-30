package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * RFID card item. Every colour variant is identical apart from its registry name, which selects
 * the matching texture and model. Cards carry the private keys copied onto them by the programmer;
 * the Operator Keycard inherits this item but draws up its own key display later.
 */
public class ItemRfidCard extends Item {

    public ItemRfidCard(String name) {
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (this instanceof ItemOperatorKeycard) return;
        for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
            tooltip.add(TextFormatting.WHITE + key.name);
            tooltip.add(TextFormatting.WHITE + key.serial);
        }
    }
}
