package com.ntmdoors.client;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashSet;
import java.util.Set;

/** Client-only visibility toggles for the blue Motion Sensor bound-area projection. */
@SideOnly(Side.CLIENT)
public final class MotionSensorOverlay {

    private static final Set<BlockPos> VISIBLE = new HashSet<>();

    public static void toggle(BlockPos pos) {
        if (pos == null) return;
        BlockPos key = pos.toImmutable();
        if (!VISIBLE.remove(key)) VISIBLE.add(key);
    }

    public static boolean isVisible(BlockPos pos) {
        return pos != null && VISIBLE.contains(pos);
    }

    public static void clear() {
        VISIBLE.clear();
    }

    private MotionSensorOverlay() {
    }
}
