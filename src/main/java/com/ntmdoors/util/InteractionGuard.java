package com.ntmdoors.util;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.block.BlockKeycardProgrammer;
import com.ntmdoors.block.BlockKeycardScanner;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;

/**
 * Shared check for keycard items that open a screen on sneak right-click. The screen must not
 * replace an interaction the player is already aiming at, so doors, machines and anything else with
 * a tile entity keep their own right-click.
 */
public final class InteractionGuard {

    private static final double REACH = 5.0D;

    /** True when the player is looking at something that answers a right click itself. */
    public static boolean targetsInteractiveBlock(EntityPlayer player) {
        RayTraceResult hit = player.rayTrace(REACH, 1.0F);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        IBlockState state = player.world.getBlockState(hit.getBlockPos());
        Block block = state.getBlock();
        return block.hasTileEntity(state)
                || block instanceof BlockDoorGeneric
                || block instanceof BlockDoorKeypad
                || block instanceof BlockKeycardScanner
                || block instanceof BlockKeycardProgrammer;
    }

    private InteractionGuard() {
    }
}
