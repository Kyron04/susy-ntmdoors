package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/** The rebar concrete pillar: a normal axis-aligned column block. */
public class BlockConcretePillar extends BlockRotatedPillar {

    public BlockConcretePillar(String name) {
        super(Material.ROCK);
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(ConcreteSet.TAB);
        setHardness(15F);
        setResistance(180F);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
    }
}
