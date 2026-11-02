package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/** "Moldy Debris": the crumbled remains of Über Concrete, falling like sand or gravel. */
public class BlockConcreteDebris extends BlockFalling {

    public BlockConcreteDebris(String name) {
        super(Material.ROCK);
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(10F);
        setResistance(20F);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
    }
}
