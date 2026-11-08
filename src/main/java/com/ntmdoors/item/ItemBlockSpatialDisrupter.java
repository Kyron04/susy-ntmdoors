package com.ntmdoors.item;

import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Item form for the Spatial Disrupter, carrying its description line. */
public class ItemBlockSpatialDisrupter extends ItemBlock {

    public ItemBlockSpatialDisrupter(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        Names.addDescription(tooltip, "Weakens the structural integrity of UHPC");
    }
}
