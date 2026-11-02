package com.ntmdoors.hlblocks;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Item of a {@link BlockGroup}: one metadata value per block state variant. */
public class ItemBlockGroup extends ItemBlock {

    private final BlockGroup group;

    public ItemBlockGroup(BlockGroup block) {
        super(block);
        this.group = block;
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    public int getVariantCount() {
        return this.group.getVariants().size();
    }

    public String getVariantName(int meta) {
        return this.group.getVariants()
                .get(Math.floorMod(meta, getVariantCount())).getName();
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey(stack) + "." + getVariantName(stack.getMetadata());
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) {
            return;
        }
        for (int meta = 0; meta < getVariantCount(); meta++) {
            items.add(new ItemStack(this, 1, meta));
        }
    }
}
