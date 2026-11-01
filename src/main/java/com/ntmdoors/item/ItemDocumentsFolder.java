package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carried document folder. It is only the physical item for now; the twenty-page editor and the
 * stored page data will be added on top of this later, so each folder stays a single unique item.
 */
public class ItemDocumentsFolder extends Item {

    /** Pages a folder is intended to hold once the writing screens exist. */
    public static final int PAGE_COUNT = 20;

    public ItemDocumentsFolder() {
        setRegistryName(NTMDoors.MODID, "documents_folder");
        setTranslationKey(NTMDoors.MODID + ".documents_folder");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip, "Effectively a glorified book with a cooler aesthetic");
    }
}
