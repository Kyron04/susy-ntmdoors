package com.ntmdoors.door;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Refuses block placement in the volume an open door's moving panel occupies.
 *
 * <p>A door is only a multiblock at its closed footprint. Sliding panels park in adjacent cells,
 * roller shutters and lifting doors roll into the space above their frame, and the large vehicle
 * door splits sideways. Those cells are air while the door is open, so vanilla would happily let a
 * player place a block inside the visible panel. The check uses the door's open collision geometry,
 * so it works for every door without duplicating per-door parking coordinates.</p>
 *
 * <p>The check is deliberately independent of the current animation fraction: the final open
 * volume is reserved even while the door is closed, otherwise a block placed first would be
 * clipped when the door later opens.</p>
 */
public final class DoorPlacementGuard {

    /** The largest door (transition seal) is roughly 26 blocks wide and 24 high. */
    private static final double MAX_DISTANCE_SQ = 80D * 80D;
    /**
     * Fractions of the opening animation sampled for the guard. The final value is just below
     * fully open so end-exclusive moving-part boxes are included. Sampling the sweep as well as
     * the parked position stops a block from being placed in the path of a panel that is still
     * moving.
     */
    private static final float[] OPEN_FRACTIONS = {
            0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 0.999F
    };

    /** True when a block placed at {@code target} would intersect a loaded door's open panel. */
    public static boolean blocksPlacement(World world, BlockPos target) {
        // Temporarily disabled for diagnosis: always allow placement.
        return false;
    }

    private static boolean intersectsOpenPanel(DoorDecl decl, BlockPos corePos, EnumFacing facing,
                                               AxisAlignedBB target) {
        double ox = corePos.getX() + 0.5D;
        double oy = corePos.getY();
        double oz = corePos.getZ() + 0.5D;

        // Check only the animated moving-panel volume (not static frame geometry). The panel
        // sweeps through these positions, so they are reserved even while the door is closed.
        for (float fraction : OPEN_FRACTIONS) {
            for (boolean collision : new boolean[]{true, false}) {
                for (AxisAlignedBB box : decl.getMovingBoxes(facing, fraction, collision)) {
                    if (target.intersects(box.offset(ox, oy, oz))) return true;
                }
            }
        }
        return false;
    }

    private DoorPlacementGuard() {
    }
}