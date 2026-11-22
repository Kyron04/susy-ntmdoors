// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/** Helpers for the Grill's contraband storage container. */
public final class ContrabandContainers {

    /** Maximum distance between the Grill and its container, in chunks. */
    public static final int MAX_CHUNKS = 8;
    private static final double MAX_INTERACTION_DISTANCE_SQ = 64.0D;

    public static int sideIndex(@Nullable EnumFacing side) {
        return side == null ? -1 : side.getIndex();
    }

    @Nullable
    public static EnumFacing side(int sideIndex) {
        return sideIndex >= 0 && sideIndex < 6 ? EnumFacing.byIndex(sideIndex) : null;
    }

    public static boolean isWithinChunks(BlockPos grill, BlockPos container, int maxChunks) {
        if (grill == null || container == null) return false;
        int dx = Math.abs((grill.getX() >> 4) - (container.getX() >> 4));
        int dz = Math.abs((grill.getZ() >> 4) - (container.getZ() >> 4));
        return Math.max(dx, dz) <= maxChunks;
    }

    /** True when the block is loaded, in the stored dimension and still exposes an item handler. */
    public static boolean isStillValid(World world, int dimension, BlockPos pos, int sideIndex,
                                       BlockPos grill) {
        if (world == null || pos == null || world.provider.getDimension() != dimension) return false;
        if (!isWithinChunks(grill, pos, MAX_CHUNKS) || !world.isBlockLoaded(pos)) return false;
        return findHandler(world, pos, sideIndex) != null;
    }

    /** Validates a player's container-selection click. Server side only. */
    public static boolean isValidTarget(EntityPlayer player, World world, BlockPos pos,
                                        EnumFacing side, ItemStack held, BlockPos grill) {
        if (player == null || world == null || world.isRemote || pos == null || grill == null) {
            return false;
        }
        if (player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                > MAX_INTERACTION_DISTANCE_SQ) {
            return false;
        }
        if (!world.isBlockLoaded(pos)) return false;

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == Blocks.AIR) return false;
        if (!player.canPlayerEdit(pos, side, held)) return false;
        if (!isWithinChunks(grill, pos, MAX_CHUNKS)) return false;
        if (!hasLineOfSight(player, world, pos)) return false;
        return findHandler(world, pos, sideIndex(side)) != null;
    }

    /**
     * Inserts as much of the stack as the container accepts. The caller owns the source stack and
     * voids any remainder according to the Grill's containment policy.
     *
     * @return the number of items inserted, or 0 when the container is unavailable or full
     */
    public static int insert(World world, int dimension, BlockPos pos, int sideIndex,
                             BlockPos grill, ItemStack stack) {
        if (stack.isEmpty() || !isStillValid(world, dimension, pos, sideIndex, grill)) return 0;
        IItemHandler handler = findHandler(world, pos, sideIndex);
        if (handler == null) return 0;

        try {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                    handler, stack.copy(), false);
            return stack.getCount() - remainder.getCount();
        } catch (RuntimeException ignored) {
            // A misbehaving handler must not crash the field tick. Treat it as unavailable.
            return 0;
        }
    }

    @Nullable
    private static IItemHandler findHandler(World world, BlockPos pos, int sideIndex) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;
        if (te instanceof TileEntityLockable && ((TileEntityLockable) te).isLocked()) return null;

        EnumFacing side = side(sideIndex);
        IItemHandler handler = te.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
        if (handler == null && side != null) {
            handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }
        return handler == null || handler.getSlots() <= 0 ? null : handler;
    }

    public static String displayName(World world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return "";
        TileEntity te = world.getTileEntity(pos);
        if (te != null && te.getDisplayName() != null) {
            String name = te.getDisplayName().getUnformattedText();
            if (!name.isEmpty()) return name;
        }
        return world.getBlockState(pos).getBlock().getLocalizedName();
    }

    private static boolean hasLineOfSight(EntityPlayer player, World world, BlockPos pos) {
        Vec3d eyes = player.getPositionEyes(1F);
        Vec3d target = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        RayTraceResult hit = world.rayTraceBlocks(eyes, target, false, false, true);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                && pos.equals(hit.getBlockPos());
    }

    private ContrabandContainers() {
    }
}
