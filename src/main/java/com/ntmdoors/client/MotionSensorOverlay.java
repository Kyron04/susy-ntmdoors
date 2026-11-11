package com.ntmdoors.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class MotionSensorOverlay {

    private static final Set<BlockPos> VISIBLE = new HashSet<>();

    private MotionSensorOverlay() {}

    public static void toggle(BlockPos pos) {
        if (pos == null) return;
        BlockPos p = pos.toImmutable();
        if (!VISIBLE.add(p)) {
            VISIBLE.remove(p);
        }
    }

    public static boolean isVisible(BlockPos pos) {
        return pos != null && VISIBLE.contains(pos);
    }

    public static void clear() {
        VISIBLE.clear();
    }
}
