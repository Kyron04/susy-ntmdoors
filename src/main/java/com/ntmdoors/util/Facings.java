package com.ntmdoors.util;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * Facing math shared by the door geometry, the keypad hit test and the renderers.
 *
 * <p>Every model is authored facing NORTH (-Z). These helpers turn a model-local X/Z pair into
 * world space for the facing the block was placed with. Keeping the rotation in one place stops
 * the geometry, the hit tests and the GL yaw from drifting apart.</p>
 */
public final class Facings {

    /** GL yaw (degrees) that turns a NORTH-authored model into {@code f}. */
    public static float yaw(EnumFacing f) {
        switch (f) {
            case EAST: return -90F;
            case SOUTH: return 180F;
            case WEST: return 90F;
            default: return 0F;
        }
    }

    /** World X of a model-local point once the model is rotated to face {@code f}. */
    public static double worldX(double x, double z, EnumFacing f) {
        switch (f) {
            case EAST: return -z;
            case SOUTH: return -x;
            case WEST: return z;
            default: return x;
        }
    }

    /** World Z of a model-local point once the model is rotated to face {@code f}. */
    public static double worldZ(double x, double z, EnumFacing f) {
        switch (f) {
            case EAST: return x;
            case SOUTH: return -z;
            case WEST: return -x;
            default: return z;
        }
    }

    /** Model-space X of a world-space offset, i.e. the inverse of {@link #worldX}. */
    public static double localX(double x, double z, EnumFacing f) {
        return worldX(x, z, inverse(f));
    }

    /** Model-space Z of a world-space offset, i.e. the inverse of {@link #worldZ}. */
    public static double localZ(double x, double z, EnumFacing f) {
        return worldZ(x, z, inverse(f));
    }

    /**
     * The horizontal facing whose yaw is the negative of {@code f}'s. Note that this is not
     * {@link EnumFacing#getOpposite()}: the opposite turns the model 180 degrees, while this
     * undoes the turn, which for SOUTH happens to be SOUTH again.
     */
    public static EnumFacing inverse(EnumFacing f) {
        return EnumFacing.byHorizontalIndex((4 - f.getHorizontalIndex()) % 4);
    }

    /** Rotates a model-space box for {@code f} and normalises the two opposite corners. */
    public static AxisAlignedBB rotate(AxisAlignedBB box, EnumFacing f) {
        double x1 = worldX(box.minX, box.minZ, f);
        double z1 = worldZ(box.minX, box.minZ, f);
        double x2 = worldX(box.maxX, box.maxZ, f);
        double z2 = worldZ(box.maxX, box.maxZ, f);
        return new AxisAlignedBB(
                Math.min(x1, x2), box.minY, Math.min(z1, z2),
                Math.max(x1, x2), box.maxY, Math.max(z1, z2));
    }

    private Facings() {
    }
}
