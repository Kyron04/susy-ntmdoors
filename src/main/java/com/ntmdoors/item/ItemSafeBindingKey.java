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
 * Safe Binding Key. A small creative-only USB key reserved for binding safes to the security
 * network; it currently carries only its sprite and description.
 */
public class ItemSafeBindingKey extends Item {

    public ItemSafeBindingKey() {
        setRegistryName(NTMDoors.MODID, "safe_binding_key");
        setTranslationKey(NTMDoors.MODID + ".safe_binding_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Binds safes to the security infrastructure",
                "to protect your stuff.");
    }
}
