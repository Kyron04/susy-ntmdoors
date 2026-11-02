package com.ntmdoors.hlblocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Registers every included HL Blocks stack under the {@code blockhl} ore dictionary. */
public final class HLBlocksOreDict {

    public static final String BLOCK_HL = "blockhl";

    private HLBlocksOreDict() {
    }

    public static void register() {
        for (Block block : HLBlocksContent.SIMPLE_BLOCKS) {
            OreDictionary.registerOre(BLOCK_HL, new ItemStack(block));
        }
        for (BlockGroup group : HLBlocksContent.GROUP_BLOCKS) {
            for (int meta = 0; meta < group.getVariants().size(); meta++) {
                OreDictionary.registerOre(BLOCK_HL, new ItemStack(group, 1, meta));
            }
        }
    }
}
