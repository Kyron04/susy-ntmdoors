// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.util.Names;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "This machine can bind keycards, back up",
                "private keys, and change keycard colours.");
    }
}
