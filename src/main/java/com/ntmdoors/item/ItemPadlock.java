package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** A padlock carries a lock code. Sneak + right click on a door attaches it. */
public class ItemPadlock extends Item {

    public ItemPadlock() {
        setRegistryName(NTMDoors.MODID, "padlock");
        setTranslationKey(NTMDoors.MODID + ".padlock");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        LockUtil.ensureCode(stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isRemote) LockUtil.ensureCode(stack);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        int code = LockUtil.getCode(stack);
        if (code != 0) tooltip.add(new TextComponentTranslation("ntmdoors.lock.id", Integer.toHexString(code)).getFormattedText());
    }
}
