package com.ntmdoors.item;

import com.ntmdoors.concrete.BlockConcreteVariant;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Item form for the Ultra High Performance Concrete family. Shares the metadata variant behaviour
 * with the other concrete cubes (one stack and translation key per shade) and adds a fixed grey
 * description tooltip plus a hardcoded fallback display name so the tooltip never shows a raw
 * translation key. Used by both the hard set and the decorative panel set.
 */
public class ItemBlockUltraHighPerformanceConcrete extends ItemBlockConcreteVariant {

    private final String fallbackName;
    private final boolean showDescription;

    public ItemBlockUltraHighPerformanceConcrete(BlockConcreteVariant block) {
        this(block, "Ultra High Performance Concrete", true);
    }

    public ItemBlockUltraHighPerformanceConcrete(BlockConcreteVariant block, String fallbackName) {
        this(block, fallbackName, true);
    }

    public ItemBlockUltraHighPerformanceConcrete(BlockConcreteVariant block, String fallbackName,
                                                 boolean showDescription) {
        super(block);
        this.fallbackName = fallbackName;
        this.showDescription = showDescription;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Names.translationOr(getTranslationKey(stack) + ".name", fallbackName);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        if (showDescription) Names.addDescription(tooltip, "GBU-57 not included");
    }
}
