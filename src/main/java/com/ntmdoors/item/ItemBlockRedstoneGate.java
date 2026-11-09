package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Item form for the Redstone Gate, with its red menu name and binding-output description. */
public class ItemBlockRedstoneGate extends ItemBlock {

    public ItemBlockRedstoneGate(Block block) {
        super(block);
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.RED + Names.translationOr(getTranslationKey(stack) + ".name", "Redstone Gate");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Bound activities from Keypads, Keycard Scanners,",
                "and Safes will emit a redstone signal.",
                "Sneak + Right Click with the Redstone Relay Key",
                "to link other gates and doors to this one.");
    }
}
