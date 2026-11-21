// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Decorative monitor-only block: a monitor bank without the desk, terminal or tile entity. */
public class BlockMonitorOnly extends BlockDesk {

    public BlockMonitorOnly(String name, int columns) {
        super(name, columns, Material.IRON, SoundType.METAL, 2.0F, 5F);
    }

    /** Every cell uses the same half-depth wall as the monitor desks, leaving the front walkable. */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return MonitorBoxes.forFacing(state.getValue(FACING));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return MonitorBoxes.forFacing(state.getValue(FACING));
    }
}
