package com.ntmdoors.door;

/**
 * One animation step for a named OBJ group. Pure data (no GL) so it is safe to load on a server.
 * The renderer applies the steps of a part in list order; "t" is the door's open fraction (0 closed .. 1 open)
 * and each step only runs between its start and end fraction.
 * Model space: origin = bottom centre of the core cell, door facing NORTH (-Z), +X = right, +Z = behind the door.
 */
public final class PartAnim {

    public enum Type { TRANSLATE, ROTATE }

    public final Type type;
    public final double x, y, z;      // translation vector, or rotation axis
    public final double angle;        // degrees (rotation only)
    public final double px, py, pz;   // rotation pivot (rotation only)
    public final float start, end;
    /** True for a static transform that is always applied in full, regardless of the door state. */
    public final boolean constant;

    private PartAnim(Type type, double x, double y, double z, double angle, double px, double py, double pz, float start, float end, boolean constant) {
        this.type = type;
        this.x = x; this.y = y; this.z = z;
        this.angle = angle;
        this.px = px; this.py = py; this.pz = pz;
        this.start = start; this.end = end;
        this.constant = constant;
    }

    public static PartAnim translate(double dx, double dy, double dz, float start, float end) {
        return new PartAnim(Type.TRANSLATE, dx, dy, dz, 0, 0, 0, 0, start, end, false);
    }

    /** A translation that is always applied in full; used to move the origin before a hinge rotation. */
    public static PartAnim constantTranslate(double dx, double dy, double dz) {
        return new PartAnim(Type.TRANSLATE, dx, dy, dz, 0, 0, 0, 0, 0F, 1F, true);
    }

    public static PartAnim rotate(double angleDeg, double ax, double ay, double az, double px, double py, double pz, float start, float end) {
        return new PartAnim(Type.ROTATE, ax, ay, az, angleDeg, px, py, pz, start, end, false);
    }

    /** A rotation that is always applied in full; useful for a part's authored base pose. */
    public static PartAnim constantRotate(double angleDeg, double ax, double ay, double az, double px, double py, double pz) {
        return new PartAnim(Type.ROTATE, ax, ay, az, angleDeg, px, py, pz, 0F, 1F, true);
    }

    /** Eased 0..1 progress of this step for the given open fraction. */
    public float progress(float t) {
        if (constant) return 1F;
        float p = (t - start) / (end - start);
        p = p < 0F ? 0F : (p > 1F ? 1F : p);
        return p * p * (3F - 2F * p);
    }
}
