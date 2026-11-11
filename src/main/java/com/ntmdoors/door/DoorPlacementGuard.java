package com.ntmdoors.door;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.DoorPart;
import com.ntmdoors.block.entity.DoorBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * fully open so end-exclusive moving-part boxes are included.
     */
    private static final float[] OPEN_FRACTIONS = {
            0.1F, 0.2F, 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F, 0.999F
    };

    private static final Set<DoorBlockEntity> ACTIVE_CORES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void registerActive(DoorBlockEntity core) {
        if (core != null) ACTIVE_CORES.add(core);
    }

    public static void unregisterActive(DoorBlockEntity core) {
        if (core != null) ACTIVE_CORES.remove(core);
    }

    public static void clearActive() {
        ACTIVE_CORES.clear();
    }

    public static int getActiveCount() {
        return ACTIVE_CORES.size();
    }

    public static boolean isRegistered(DoorBlockEntity core) {
        return core != null && ACTIVE_CORES.contains(core);
    }

    /** True when a block placed at {@code target} would intersect a loaded door's moving panel. */
    public static boolean blocksPlacement(World world, BlockPos target) {
        if (target == null) return false;
        Box block = new Box(target);
        for (DoorBlockEntity core : ACTIVE_CORES) {
            if (core.isRemoved() || core.isBreached()) continue;
            if (world != null && core.getWorld() != null && core.getWorld() != world) continue;
            if (core.getState() == DoorState.CLOSED) continue;
            BlockPos corePos = core.getPos();
            if (corePos.getSquaredDistance(target) > MAX_DISTANCE_SQ) continue;

            Direction facing = Direction.NORTH;
            BlockState state = core.getCachedState();
            if (state != null) {
                if (!(state.getBlock() instanceof BlockDoorGeneric)) continue;
                if (state.get(BlockDoorGeneric.PART) != DoorPart.CORE) continue;
                facing = state.get(BlockDoorGeneric.FACING);
            }

            DoorDecl decl = core.getDecl();
            if (decl == null) continue;
            if (intersectsMovingPanel(decl, corePos, facing, block)) return true;
        }
        return false;
    }

    public static boolean intersectsMovingPanel(DoorDecl decl, BlockPos corePos, Direction facing, Box target) {
        double ox = corePos.getX() + 0.5D;
        double oy = corePos.getY();
        double oz = corePos.getZ() + 0.5D;

        for (float fraction : OPEN_FRACTIONS) {
            for (boolean collision : new boolean[]{true, false}) {
                for (Box box : decl.getMovingBoxes(facing, fraction, collision)) {
                    if (target.intersects(box.offset(ox, oy, oz))) return true;
                }
            }
        }
        return false;
    }

    private DoorPlacementGuard() {
    }
}