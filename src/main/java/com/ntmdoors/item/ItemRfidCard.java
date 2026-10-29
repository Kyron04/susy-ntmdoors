package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.minecraft.item.Item;

/**
 * RFID card item. Every colour variant is identical apart from its registry name, which selects
 * the matching texture and model.
 */
public class ItemRfidCard extends Item {

    public ItemRfidCard(String name) {
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
    }
}
