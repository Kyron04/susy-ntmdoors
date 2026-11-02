package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.SoundType;

/**
 * A concrete stair block. All shape and placement behaviour comes from vanilla {@link BlockStairs};
 * only the base block and the material values differ per family.
 */
public class BlockConcreteStairs extends BlockStairs {

    public BlockConcreteStairs(Block base, String name, float hardness, float resistance) {
        super(base.getDefaultState());
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(ConcreteSet.TAB);
        setHardness(hardness);
        setResistance(resistance);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
        useNeighborBrightness = true;
    }
}
