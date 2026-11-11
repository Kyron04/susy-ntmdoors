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

public class ItemBlockRedstoneGate extends BlockItem {

    public ItemBlockRedstoneGate(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.RED);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Bound activities from Keypads, Keycard Scanners,").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("and Safes will emit a redstone signal.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + Right Click with the Redstone Relay Key").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("to link other gates and doors to this one.").formatted(Formatting.GRAY));
    }
}
