package com.ntmdoors.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/** Decorative monitor-only block: a monitor bank without the desk, terminal or tile entity. */
public class BlockMonitorOnly extends BlockDesk {

    public BlockMonitorOnly(String name, int columns) {
        super(name, columns, Material.IRON, SoundType.METAL, 2.0F, 5F);
    }
}
