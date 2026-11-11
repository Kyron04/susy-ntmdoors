package com.ntmdoors.util;

import com.ntmdoors.block.door.BlockDoorGeneric;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Shared check for keycard items that open a screen on sneak right-click. The screen must not
 * replace an interaction the player is already aiming at, so doors, machines and anything else with
 * a block entity keep their own right-click.
 */
public final class InteractionGuard {

    private static final double REACH = 5.0D;

    /** True when the player is looking at something that answers a right click itself. */
    public static boolean targetsInteractiveBlock(PlayerEntity player) {
        BlockHitResult hit = rayTrace(player, REACH);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        BlockState state = player.getWorld().getBlockState(hit.getBlockPos());
        Block block = state.getBlock();
        return block instanceof BlockWithEntity
                || block instanceof BlockDoorGeneric;
    }

    @Nullable
    public static BlockHitResult rayTrace(PlayerEntity player, double reach) {
        Vec3d eyes = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        return player.getWorld().raycast(new RaycastContext(
                eyes, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }

    private InteractionGuard() {
    }
}
