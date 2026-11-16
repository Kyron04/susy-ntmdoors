package com.ntmdoors.block;

import com.ntmdoors.util.Facings;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * The shared half-depth monitor wall used by every desk and monitor block.
 *
 * <p>The supplied models sit against the back half of their cell. Keeping the box here means the
 * large desk, the small desks and the decorative monitor-only blocks cannot drift apart.</p>
 */
final class MonitorBoxes {

    /** Authored monitor wall: the south half of a block, centred so it can be rotated. */
    private static final AxisAlignedBB LOCAL = new AxisAlignedBB(-0.5D, 0D, 0D, 0.5D, 1D, 0.5D);
    private static final AxisAlignedBB NORTH = rotated(EnumFacing.NORTH);
    private static final AxisAlignedBB SOUTH = rotated(EnumFacing.SOUTH);
    private static final AxisAlignedBB EAST = rotated(EnumFacing.EAST);
    private static final AxisAlignedBB WEST = rotated(EnumFacing.WEST);

    private static AxisAlignedBB rotated(EnumFacing facing) {
        return Facings.rotate(LOCAL, facing).offset(0.5D, 0D, 0.5D);
    }

    static AxisAlignedBB forFacing(EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return SOUTH;
            case EAST:
                return EAST;
            case WEST:
                return WEST;
            case NORTH:
            default:
                return NORTH;
        }
    }

    private MonitorBoxes() {
    }
}
