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
 * Safe Binding Key. A small USB key that will bind a safe to the security network once the safe
 * logic exists. For now it is a creative-only item with its own sprite and description.
 */
public class ItemSafeBindingKey extends Item {

    public ItemSafeBindingKey() {
        setRegistryName(NTMDoors.MODID, "safe_binding_key");
        setTranslationKey(NTMDoors.MODID + ".safe_binding_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip, "Bound safes to security infostructure to protect your stuff");
    }
}
