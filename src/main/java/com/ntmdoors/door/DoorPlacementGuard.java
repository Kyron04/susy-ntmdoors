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
 * Refuses block placement in the volume a door's moving panel currently occupies or will occupy
 * while open/moving.
 *
 * <p>A door is only a multiblock at its closed footprint. Sliding panels park in adjacent cells,
 * roller shutters and lifting doors roll into the space above their frame, and the large vehicle
 * door splits sideways. Those cells are air while the door is open, so vanilla would happily let a
 * player place a block inside the visible panel. The check uses the door's moving-panel geometry,
 * so it works for every door without duplicating per-door parking coordinates.</p>
 *
 * <p>Fully closed doors do not reserve their future sweep here: reserving it would make the path of
 * every closed door permanently unbuildable and prevent normal door placement nearby. Open or moving
 * doors do reserve the swept/parked panel volume so blocks cannot be placed into the active panel.</p>
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

    /** True when a block placed at {@code target} would intersect a loaded door's moving panel. */
    public static boolean blocksPlacement(World world, BlockPos target) {
        if (world == null || target == null) return false;
        AxisAlignedBB block = new AxisAlignedBB(target);
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityDoorGeneric)) continue;
            TileEntityDoorGeneric core = (TileEntityDoorGeneric) te;
            if (core.isInvalid() || core.isBreached()) continue;
            // A fully closed, settled door keeps its panel in the footprint. Reserving its future
            // sweep would make nearby space (and other door placements) permanently unbuildable.
            if (core.getState() == DoorState.CLOSED) continue;
            BlockPos corePos = core.getPos();
            if (corePos.distanceSq(target) > MAX_DISTANCE_SQ) continue;

            IBlockState state = world.getBlockState(corePos);
            if (!(state.getBlock() instanceof BlockDoorGeneric)) continue;
            if (state.getValue(BlockDoorGeneric.PART) != BlockDoorGeneric.Part.CORE) continue;

            DoorDecl decl = core.getDecl();
            if (decl == null) continue;
            EnumFacing facing = state.getValue(BlockDoorGeneric.FACING);
            if (intersectsMovingPanel(decl, corePos, facing, block)) return true;
        }
        return false;
    }

    private static boolean intersectsMovingPanel(DoorDecl decl, BlockPos corePos, EnumFacing facing,
                                                 AxisAlignedBB target) {
        double ox = corePos.getX() + 0.5D;
        double oy = corePos.getY();
        double oz = corePos.getZ() + 0.5D;

        // Check only the animated moving-panel volume (not static frame geometry). For open/moving
        // doors this covers the parked position and the path the panel currently occupies.
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