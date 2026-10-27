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

/** An item that carries a padlock code (a padlock or a key) and shows it in its tooltip. */
public abstract class ItemLockCode extends Item {

    ItemLockCode(String name) {
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int code = LockUtil.getCode(stack);
        if (code != 0) {
            tooltip.add(new TextComponentTranslation("ntmdoors.lock.id", Integer.toHexString(code)).getFormattedText());
        }
    }
}
