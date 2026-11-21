// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.util;

import com.ntmdoors.block.BlockEmancipationGrill;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Shared geometry for the Emancipation Grill field. Both the client renderer and future server
 * logic use the same thin box and the same pairing rules.
 */
public final class EmancipationFieldGeometry {

    public static final int MAX_DISTANCE = 10;
    public static final double THICKNESS = 1.0D / 16.0D;
    /** Total height of the three-block vertical grill column. */
    public static final double HEIGHT = 3.0D;
    /**
     * The blue emitter strip runs from 3 model pixels above the column bottom to 3 pixels below
     * the top (y 3..45 out of 48). The field must stop at those same lines.
     */
    public static final double VERTICAL_INSET = 3.0D / 16.0D;
    /** The horizontal emitter strip centre, measured from the bar's base. */
    public static final double HORIZONTAL_FIELD_Y = 4.0D / 16.0D;
    /** Length of the horizontal bar and its field. */
    public static final double HORIZONTAL_BAR_LENGTH = 3.0D;
    /** The horizontal glow strip stops three pixels short at each end, like the vertical unit. */
    public static final double HORIZONTAL_FIELD_LENGTH =
            HORIZONTAL_BAR_LENGTH - VERTICAL_INSET * 2.0D;
    private static final double EMITTER_INSET = 1.0D / 64.0D;
    /** Emitter strip centre is four model pixels from the block centre. */
    private static final int STRIP_OFFSET = 4;

    private EmancipationFieldGeometry() {
    }

    /** Finds the matching grill on the opposite wall, or null. */
    @Nullable
    public static BlockPos findPartner(World world, BlockPos core) {
        if (world == null) return null;

        IBlockState state = world.getBlockState(core);
        if (!(state.getBlock() instanceof BlockEmancipationGrill)) return null;
        BlockEmancipationGrill grill = (BlockEmancipationGrill) state.getBlock();
        if (!grill.isCore()) return null;

        EnumFacing facing = state.getValue(BlockEmancipationGrill.FACING);
        if (facing.getAxis() == EnumFacing.Axis.Y) return null;
        boolean mirrored = state.getValue(BlockEmancipationGrill.MIRRORED);
        BlockEmancipationGrill.Mount mount = grill.mount();

        for (int step = 1; step <= MAX_DISTANCE; step++) {
            BlockPos p = core.offset(facing, step);
            IBlockState other = world.getBlockState(p);
            if (!(other.getBlock() instanceof BlockEmancipationGrill)) continue;

            BlockEmancipationGrill otherGrill = (BlockEmancipationGrill) other.getBlock();
            if (!otherGrill.isCore() || otherGrill.mount() != mount) continue;
            if (other.getValue(BlockEmancipationGrill.FACING) != facing.getOpposite()) continue;
            if (other.getValue(BlockEmancipationGrill.MIRRORED) == mirrored) continue;

            // Horizontal bars have no vertical tolerance: the center cells must line up.
            if (mount == BlockEmancipationGrill.Mount.HORIZONTAL
                    && p.getY() != core.getY()) {
                continue;
            }

            // The two grill centers must share the same line along the wall.
            if (facing.getAxis() == EnumFacing.Axis.X) {
                if (p.getZ() != core.getZ()) continue;
            } else if (p.getX() != core.getX()) {
                continue;
            }

            boolean otherMirror = !mirrored;
            if (!hasParts(world, p, facing.getOpposite(), otherMirror, mount)) {
                continue;
            }
            return p;
        }
        return null;
    }

    @Nullable
    public static AxisAlignedBB getFieldBox(World world, BlockPos core) {
        if (world == null) return null;
        BlockPos partner = findPartner(world, core);
        if (partner == null) return null;

        IBlockState state = world.getBlockState(core);
        if (!(state.getBlock() instanceof BlockEmancipationGrill)) return null;
        BlockEmancipationGrill grill = (BlockEmancipationGrill) state.getBlock();
        if (!grill.isCore()) return null;

        EnumFacing facing = state.getValue(BlockEmancipationGrill.FACING);
        boolean mirrored = state.getValue(BlockEmancipationGrill.MIRRORED);
        return box(core, partner, facing, mirrored, grill.mount());
    }

    /** The thin field cell. Both grills compute the same box from their own state. */
    public static AxisAlignedBB box(BlockPos core, BlockPos partner, EnumFacing facing,
                                    boolean mirrored, BlockEmancipationGrill.Mount mount) {
        int dx = facing.getDirectionVec().getX();
        int dz = facing.getDirectionVec().getZ();

        double ax = core.getX() + 0.5D - dx * EMITTER_INSET;
        double az = core.getZ() + 0.5D - dz * EMITTER_INSET;
        double bx = partner.getX() + 0.5D + dx * EMITTER_INSET;
        double bz = partner.getZ() + 0.5D + dz * EMITTER_INSET;

        if (mount == BlockEmancipationGrill.Mount.HORIZONTAL) {
            // The bar's emitter plane is horizontal: thin in Y, spanning the gap and the
            // three-block bar length along the wall.
            double planeY = core.getY() + HORIZONTAL_FIELD_Y;
            double y0 = planeY - THICKNESS / 2.0D;
            double y1 = planeY + THICKNESS / 2.0D;
            double half = HORIZONTAL_FIELD_LENGTH / 2.0D;
            if (facing.getAxis() == EnumFacing.Axis.X) {
                double centreZ = core.getZ() + 0.5D;
                return new AxisAlignedBB(
                        Math.min(ax, bx), y0, centreZ - half,
                        Math.max(ax, bx), y1, centreZ + half);
            }
            double centreX = core.getX() + 0.5D;
            return new AxisAlignedBB(
                    centreX - half, y0, Math.min(az, bz),
                    centreX + half, y1, Math.max(az, bz));
        }

        double y0 = core.getY() + VERTICAL_INSET;
        double y1 = core.getY() + HEIGHT - VERTICAL_INSET;
        int strip = mirrored ? -STRIP_OFFSET : STRIP_OFFSET;

        if (facing.getAxis() == EnumFacing.Axis.X) {
            double stripZ = core.getZ() + 0.5D + Facings.worldZ(strip, 0, facing) / 16.0D;
            return new AxisAlignedBB(
                    Math.min(ax, bx), y0, stripZ - THICKNESS / 2.0D,
                    Math.max(ax, bx), y1, stripZ + THICKNESS / 2.0D);
        }

        double stripX = core.getX() + 0.5D + Facings.worldX(strip, 0, facing) / 16.0D;
        return new AxisAlignedBB(
                stripX - THICKNESS / 2.0D, y0, Math.min(az, bz),
                stripX + THICKNESS / 2.0D, y1, Math.max(az, bz));
    }

    private static boolean hasParts(World world, BlockPos core, EnumFacing facing,
                                    boolean mirrored, BlockEmancipationGrill.Mount mount) {
        if (mount == BlockEmancipationGrill.Mount.VERTICAL) {
            return isLayer(world, core.up(), BlockEmancipationGrill.Layer.MIDDLE, facing, mirrored, mount)
                    && isLayer(world, core.up(2), BlockEmancipationGrill.Layer.TOP, facing, mirrored, mount);
        }
        EnumFacing along = BlockEmancipationGrill.alongWall(facing);
        return isLayer(world, core.offset(along, -1), BlockEmancipationGrill.Layer.BOTTOM, facing, mirrored, mount)
                && isLayer(world, core.offset(along, 1), BlockEmancipationGrill.Layer.TOP, facing, mirrored, mount);
    }

    private static boolean isLayer(World world, BlockPos pos, BlockEmancipationGrill.Layer layer,
                                   EnumFacing facing, boolean mirrored,
                                   BlockEmancipationGrill.Mount mount) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockEmancipationGrill
                && ((BlockEmancipationGrill) state.getBlock()).layer() == layer
                && ((BlockEmancipationGrill) state.getBlock()).mount() == mount
                && state.getValue(BlockEmancipationGrill.FACING) == facing
                && state.getValue(BlockEmancipationGrill.MIRRORED) == mirrored;
    }
}
