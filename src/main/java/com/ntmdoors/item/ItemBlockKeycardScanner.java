package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Item form for the keycard scanner. Provides a hardcoded fallback display name so the tooltip
 * never shows a raw translation key.
 */
public class ItemBlockKeycardScanner extends ItemBlock {

    public ItemBlockKeycardScanner(Block block) {
        super(block);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Names.translationOr(getTranslationKey(stack) + ".name", "Keycard Scanner");
    }
}
