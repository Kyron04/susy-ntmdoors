package com.ntmdoors.concrete;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Metadata slab item; one metadata value per family variant. */
public class ItemConcreteSlab extends ItemSlab {

    private final String[] variantNames;
    private final String[] itemModels;

    public ItemConcreteSlab(Block single, Block doubled, String[] variantNames, String[] itemModels) {
        super(single, (BlockSlab) single, (BlockSlab) doubled);
        this.variantNames = variantNames;
        this.itemModels = itemModels;
    }

    public int variantCount() {
        return variantNames.length;
    }

    public String variantName(int meta) {
        return variantNames[Math.floorMod(meta, variantNames.length)];
    }

    public String itemModel(int meta) {
        return itemModels[Math.floorMod(meta, itemModels.length)];
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey(stack) + "." + variantName(stack.getMetadata());
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int meta = 0; meta < variantCount(); meta++) {
            items.add(new ItemStack(this, 1, meta));
        }
    }
}
