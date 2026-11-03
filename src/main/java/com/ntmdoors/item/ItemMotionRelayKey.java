package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Motion Relay Key. Reserved for defining the 5x5x5 area a Motion Sensor watches.
 *
 * <p>The area-binding behaviour is not implemented yet; the key is only registered as an item and
 * carries its description for the later pass.</p>
 */
public class ItemMotionRelayKey extends Item {

    public ItemMotionRelayKey() {
        setRegistryName(NTMDoors.MODID, "motion_relay_key");
        setTranslationKey(NTMDoors.MODID + ".motion_relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.BLUE + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Allows you to define an area the sensor is",
                "placed in within 5 by 5 by 5 area",
                "Sneak + Right Click");
    }
}
