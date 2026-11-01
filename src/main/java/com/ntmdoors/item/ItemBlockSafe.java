package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Item form for the safe. It is a single block item because the safe will own an inventory and a
 * lock later; stacking them would make no sense once the contents are stored.
 */
public class ItemBlockSafe extends ItemBlock {

    public ItemBlockSafe(Block block) {
        super(block);
        setMaxStackSize(1);
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Names.translationOr(getTranslationKey(stack) + ".name", "Safe");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip, "A heavy steel safe");
    }
}
