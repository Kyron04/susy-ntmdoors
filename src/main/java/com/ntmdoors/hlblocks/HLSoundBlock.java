package com.ntmdoors.hlblocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Plain decorative HL block that reports a fixed sound type. */
public class HLSoundBlock extends Block {

    private final SoundType soundType;

    public HLSoundBlock(Material material, SoundType soundType) {
        super(material);
        this.soundType = soundType;
    }

    @Override
    public SoundType getSoundType(IBlockState state, World world, BlockPos pos, Entity entity) {
        return this.soundType;
    }
}
