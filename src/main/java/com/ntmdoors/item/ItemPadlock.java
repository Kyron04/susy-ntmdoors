package com.ntmdoors.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** A padlock carries a lock code. Sneak + right click on a door attaches it. */
public class ItemPadlock extends ItemLockCode {

    public ItemPadlock() {
        super("padlock");
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        LockUtil.ensureCode(stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isRemote) LockUtil.ensureCode(stack);
    }
}
