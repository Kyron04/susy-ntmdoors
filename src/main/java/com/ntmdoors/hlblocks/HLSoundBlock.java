package com.ntmdoors.hlblocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/** Plain decorative HL block that reports a fixed sound type. */
public class HLSoundBlock extends Block {

    public HLSoundBlock(Material material, SoundType soundType) {
        super(material);
        setSoundType(soundType);
    }
}
