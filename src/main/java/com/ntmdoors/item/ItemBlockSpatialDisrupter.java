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

/** Item form for the Spatial Disrupter, carrying its description line. */
public class ItemBlockSpatialDisrupter extends BlockItem {

    public ItemBlockSpatialDisrupter(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Weakens the structural integrity of blast-resistant blocks").formatted(Formatting.GRAY));
    }
}
