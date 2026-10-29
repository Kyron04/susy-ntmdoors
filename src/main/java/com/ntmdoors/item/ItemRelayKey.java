package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.minecraft.item.Item;

/**
 * Relay Key. Part of the keycard system groundwork; it has no in-game behaviour yet.
 */
public class ItemRelayKey extends Item {

    public ItemRelayKey() {
        setRegistryName(NTMDoors.MODID, "relay_key");
        setTranslationKey(NTMDoors.MODID + ".relay_key");
        setCreativeTab(NTMDoors.TAB);
    }
}
