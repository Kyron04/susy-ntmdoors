package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemBlockKeypad extends BlockItem {

    public ItemBlockKeypad(Block block) {
        super(block, new FabricItemSettings());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Sets codes on doors to secure access to whatever",
                "the door is protecting.",
                "Can be bound with a Screwdriver.");
    }
}
