package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.minecraft.item.Item;

/** Plain crafting component with no behaviour. */
public class ItemCraftingPart extends Item {

    public ItemCraftingPart(String name) {
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
    }
}
