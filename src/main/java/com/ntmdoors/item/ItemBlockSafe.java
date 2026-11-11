package com.ntmdoors.item;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemBlockSafe extends BlockItem {

    private final String descriptionKey;

    public ItemBlockSafe(Block block, String descriptionKey, Settings settings) {
        super(block, settings.maxCount(1));
        this.descriptionKey = descriptionKey;
    }

    public ItemBlockSafe(Block block, Settings settings) {
        this(block, "ntmdoors.desc.safe", settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(descriptionKey).formatted(Formatting.GRAY));
    }
}
