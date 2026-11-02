package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.concrete.BlockConcreteVariant;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Item for a metadata concrete block: one stack per variant, one translation key per variant. */
public class ItemBlockConcreteVariant extends ItemBlock {

    private final BlockConcreteVariant variantBlock;

    public ItemBlockConcreteVariant(BlockConcreteVariant block) {
        super(block);
        this.variantBlock = block;
        this.hasSubtypes = true;
        this.canRepair = false;
    }

    public int variantCount() {
        return variantBlock.variantCount();
    }

    public String modelName(int meta) {
        return variantBlock.modelName(meta);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int meta = 0; meta < variantBlock.creativeVariantCount(); meta++) {
            items.add(new ItemStack(this, 1, meta));
        }
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        if (!variantBlock.hasSeparateNames()) return super.getTranslationKey(stack);
        // No ".name" here: getUnlocalizedNameInefficiently translates this key and
        // getItemStackDisplayName appends ".name" afterwards. Returning the full key made the
        // display name "Cyan Concrete.name".
        return "tile." + NTMDoors.MODID + "." + variantBlock.getRegistryName().getPath()
                + "." + variantBlock.variantName(stack.getMetadata());
    }
}
