package com.ntmdoors.block.door;

import com.ntmdoors.door.DoorDecl;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance VoxelShape generator and cache for multiblock doors.
 * Computes exact per-cell clipped shapes and provides O(1) cached lookups
 * for static states (CLOSED, OPEN, BREACHED).
 */
public final class DoorVoxelShapes {

    public enum ShapeState { CLOSED, OPEN, BREACHED }

    public record ShapeKey(DoorDecl decl, Direction facing, BlockPos offset, ShapeState state) {}

    private static final Map<ShapeKey, VoxelShape> STATIC_CACHE = new ConcurrentHashMap<>();
    private static final double EPS = 1e-5;

    private DoorVoxelShapes() {}

    /**
     * Gets the static VoxelShape for a cell in CLOSED, OPEN, or BREACHED state.
     */
    public static VoxelShape getStaticShape(DoorDecl decl, Direction facing, BlockPos cellOffset, ShapeState state) {
        ShapeKey key = new ShapeKey(decl, facing, cellOffset.toImmutable(), state);
        return STATIC_CACHE.computeIfAbsent(key, k -> computeStaticShape(k.decl, k.facing, k.offset, k.state));
    }

    private static VoxelShape computeStaticShape(DoorDecl decl, Direction facing, BlockPos cellOffset, ShapeState state) {
        float openFraction = (state == ShapeState.CLOSED) ? 0.0f : 1.0f;
        boolean includeMoving = (state != ShapeState.BREACHED);
        Box[] boxes = decl.getBoxes(facing, openFraction, true, includeMoving);

        // Cell coordinate offset: cellOffset is (pos - corePos)
        double ox = -cellOffset.getX() + 0.5;
        double oy = -cellOffset.getY();
        double oz = -cellOffset.getZ() + 0.5;

        return clipBoxesToCell(boxes, ox, oy, oz);
    }

    /**
     * Clips an array of door-local boxes into a local [0, 1]^3 cell VoxelShape.
     */
    public static VoxelShape clipBoxesToCell(Box[] boxes, double ox, double oy, double oz) {
        if (boxes == null || boxes.length == 0) return VoxelShapes.empty();

        VoxelShape result = VoxelShapes.empty();
        for (Box b : boxes) {
            double x1 = Math.max(0.0, Math.min(1.0, b.minX + ox));
            double x2 = Math.max(0.0, Math.min(1.0, b.maxX + ox));
            double y1 = Math.max(0.0, Math.min(1.0, b.minY + oy));
            double y2 = Math.max(0.0, Math.min(1.0, b.maxY + oy));
            double z1 = Math.max(0.0, Math.min(1.0, b.minZ + oz));
            double z2 = Math.max(0.0, Math.min(1.0, b.maxZ + oz));

            if (x2 - x1 > EPS && y2 - y1 > EPS && z2 - z1 > EPS) {
                VoxelShape cuboid = VoxelShapes.cuboid(x1, y1, z1, x2, y2, z2);
                result = VoxelShapes.union(result, cuboid);
            }
        }
        return result.simplify();
    }

    public static void clearCache() {
        STATIC_CACHE.clear();
    }
}
