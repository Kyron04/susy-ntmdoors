package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Item form for the keycard programmer. Provides a hardcoded fallback display name so the tooltip
 * never shows a raw translation key.
 */
public class ItemBlockKeycardProgrammer extends ItemBlock {

    public ItemBlockKeycardProgrammer(Block block) {
        super(block);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Names.translationOr(getTranslationKey(stack) + ".name", "Keycard Programmer");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "This machine is able to bind keycards, backup",
                "private keys, and give keycards colors");
    }
}
