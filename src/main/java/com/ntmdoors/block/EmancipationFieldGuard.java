package com.ntmdoors.block;

import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.EmancipationFieldGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reserves the doorway slice occupied by an active Emancipation Grill pair. A block may not be
 * placed in the one-block-thick opening at the field line, across the full span between the grills
 * and the full three-block height. This keeps players from walling off the field or burying it in a
 * block while the field is active.
 *
 * <p>The check only runs once a pair exists. Before the second grill is placed the opening is not
 * reserved, so the doorway can be built normally.</p>
 */
public final class EmancipationFieldGuard {

    private static final double MAX_DISTANCE_SQ = 40.0D * 40.0D;

    /** True when a block placed at {@code target} would intrude into an active field opening. */
    public static boolean blocksPlacement(World world, BlockPos target) {
        if (world == null || target == null) return false;

        AxisAlignedBB block = new AxisAlignedBB(target);
        double tx = target.getX() + 0.5D;
        double ty = target.getY() + 0.5D;
        double tz = target.getZ() + 0.5D;

        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityEmancipationGrill)) continue;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            if (!grill.isPairOwner()) continue;
            // Neutral is purely decorative, so it does not reserve the doorway.
            if (grill.getFieldMode() == TileEntityEmancipationGrill.FieldMode.NEUTRAL) continue;

            BlockPos core = grill.getPos();
            if (core.distanceSq(tx, ty, tz) > MAX_DISTANCE_SQ) continue;

            AxisAlignedBB field = grill.getFieldBox();
            if (field == null) continue;

            IBlockState coreState = world.getBlockState(core);
            if (!(coreState.getBlock() instanceof BlockEmancipationGrill)) continue;
            double height = EmancipationFieldGeometry.fieldHeight(
                    ((BlockEmancipationGrill) coreState.getBlock()).mount());
            if (block.intersects(doorwaySlice(field, core.getY(), height))) return true;
        }
        return false;
    }

    /** One-block-thick opening centred on the field plane, full grill span and height. */
    private static AxisAlignedBB doorwaySlice(AxisAlignedBB field, double y0, double height) {
        double y1 = y0 + height;
        boolean spansX = (field.maxX - field.minX) > (field.maxZ - field.minZ);
        if (spansX) {
            double planeZ = (field.minZ + field.maxZ) * 0.5D;
            return new AxisAlignedBB(field.minX, y0, planeZ - 0.5D,
                    field.maxX, y1, planeZ + 0.5D);
        }
        double planeX = (field.minX + field.maxX) * 0.5D;
        return new AxisAlignedBB(planeX - 0.5D, y0, field.minZ,
                planeX + 0.5D, y1, field.maxZ);
    }

    private EmancipationFieldGuard() {
    }
}
