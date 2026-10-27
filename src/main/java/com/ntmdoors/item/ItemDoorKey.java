package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Opens doors whose padlock has the same code. Craft one by combining a padlock with an iron ingot. */
public class ItemDoorKey extends Item {

    public ItemDoorKey() {
        setRegistryName(NTMDoors.MODID, "door_key");
        setTranslationKey(NTMDoors.MODID + ".door_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int code = LockUtil.getCode(stack);
        if (code != 0) tooltip.add(new TextComponentTranslation("ntmdoors.lock.id", Integer.toHexString(code)).getFormattedText());
    }
}
