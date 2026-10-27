package com.ntmdoors.door;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a door part's animation on a collision box.
 *
 * <p>The renderer applies every {@link PartAnim} with {@code GlStateManager.translate/rotate},
 * which post-multiplies the current matrix, so a vertex is transformed by
 * {@code A1 * A2 * ... * An}. The model offset is applied after the part transforms and before the
 * model/facing yaw. This class mirrors that order exactly, which lets a box bound to an animated
 * OBJ group follow the mesh instead of jumping between cell-sized states.</p>
 *
 * <p>A rotated box is approximated by the AABB of its eight transformed corners. For non-90-degree
 * rotations the box is sliced first and every slice gets its own AABB, keeping the union close to
 * the real panel instead of one large balloon.</p>
 */
public final class DoorGeometry {

    private static final AxisAlignedBB[] EMPTY = new AxisAlignedBB[0];
    private static final double EPS = 1.0E-7;

    private DoorGeometry() {
    }

    /**
     * Transforms a model-space box through a group's animation.
     *
     * @param steps  the group's animation steps, or null for a group that never moves
     * @param open   door open fraction (0 closed, 1 open)
     * @param box    box in model space
     * @param splits number of slices for rotating parts (1 = no slicing)
     * @param yaw    combined facing yaw and model yaw, in degrees
     * @param ox     model offset X (applied in model space)
     * @param oy     model offset Y
     * @param oz     model offset Z
     * @return boxes in door-local space, relative to the core cell's bottom centre
     */
    public static AxisAlignedBB[] transform(List<PartAnim> steps, float open, AxisAlignedBB box,
                                            int splits, float yaw,
                                            double ox, double oy, double oz) {
        if (box == null) return EMPTY;

        boolean rotates = false;
        double axisX = 0, axisY = 0, axisZ = 0;
        if (steps != null) {
            for (PartAnim a : steps) {
                if (a.type == PartAnim.Type.ROTATE && !a.constant) {
                    rotates = true;
                    axisX = a.x;
                    axisY = a.y;
                    axisZ = a.z;
                    break;
                }
            }
        }

        Mat mat = new Mat();
        if (steps != null) {
            for (PartAnim a : steps) {
                double p = a.progress(open);
                if (a.type == PartAnim.Type.TRANSLATE) {
                    mat.translate(a.x * p, a.y * p, a.z * p);
                } else {
                    mat.translate(a.px, a.py, a.pz);
                    mat.rotate(a.angle * p, a.x, a.y, a.z);
                    mat.translate(-a.px, -a.py, -a.pz);
                }
            }
        }
        // The renderer translates by modelOffset while the model matrix is still in model space,
        // so the offset is applied before the model yaw.
        mat.translate(ox, oy, oz);

        int n = rotates ? Math.max(1, splits) : 1;
        int splitAxis = n > 1 ? splitAxis(box, axisX, axisY, axisZ) : 0;
        double[] point = new double[3];
        List<AxisAlignedBB> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AxisAlignedBB slice = slice(box, splitAxis, i, n);
            double x1 = Double.POSITIVE_INFINITY, y1 = Double.POSITIVE_INFINITY, z1 = Double.POSITIVE_INFINITY;
            double x2 = Double.NEGATIVE_INFINITY, y2 = Double.NEGATIVE_INFINITY, z2 = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? slice.minX : slice.maxX;
                double y = (corner & 2) == 0 ? slice.minY : slice.maxY;
                double z = (corner & 4) == 0 ? slice.minZ : slice.maxZ;
                mat.point(x, y, z, point);
                rotateY(point, yaw);
                if (point[0] < x1) x1 = point[0];
                if (point[1] < y1) y1 = point[1];
                if (point[2] < z1) z1 = point[2];
                if (point[0] > x2) x2 = point[0];
                if (point[1] > y2) y2 = point[1];
                if (point[2] > z2) z2 = point[2];
            }
            if (x2 - x1 > EPS && y2 - y1 > EPS && z2 - z1 > EPS) {
                result.add(new AxisAlignedBB(x1, y1, z1, x2, y2, z2));
            }
        }
        return result.toArray(EMPTY);
    }

    /**
     * Picks the axis to slice along: the longest axis that is not parallel to the rotation axis.
     * Slicing across the rotation plane is what makes a swinging panel's staircase tight.
     */
    private static int splitAxis(AxisAlignedBB box, double ax, double ay, double az) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < EPS) return longestAxis(box);
        double dx = Math.abs(ax) / len;
        double dy = Math.abs(ay) / len;
        double dz = Math.abs(az) / len;
        double ex = box.maxX - box.minX;
        double ey = box.maxY - box.minY;
        double ez = box.maxZ - box.minZ;
        int axis = -1;
        double best = -1;
        if (dx < 0.7 && ex > best) {
            axis = 0;
            best = ex;
        }
        if (dy < 0.7 && ey > best) {
            axis = 1;
            best = ey;
        }
        if (dz < 0.7 && ez > best) {
            axis = 2;
            best = ez;
        }
        return axis < 0 ? longestAxis(box) : axis;
    }

    private static int longestAxis(AxisAlignedBB box) {
        double ex = box.maxX - box.minX;
        double ey = box.maxY - box.minY;
        double ez = box.maxZ - box.minZ;
        if (ex >= ey && ex >= ez) return 0;
        return ey >= ez ? 1 : 2;
    }

    private static AxisAlignedBB slice(AxisAlignedBB box, int axis, int index, int count) {
        if (count <= 1) return box;
        double min, max;
        if (axis == 0) {
            min = box.minX;
            max = box.maxX;
        } else if (axis == 1) {
            min = box.minY;
            max = box.maxY;
        } else {
            min = box.minZ;
            max = box.maxZ;
        }
        double lo = min + (max - min) * index / count;
        double hi = min + (max - min) * (index + 1) / count;
        if (axis == 0) return new AxisAlignedBB(lo, box.minY, box.minZ, hi, box.maxY, box.maxZ);
        if (axis == 1) return new AxisAlignedBB(box.minX, lo, box.minZ, box.maxX, hi, box.maxZ);
        return new AxisAlignedBB(box.minX, box.minY, lo, box.maxX, box.maxY, hi);
    }

    /** Matches {@code glRotatef(degrees, 0, 1, 0)}. */
    private static void rotateY(double[] point, float degrees) {
        if (degrees == 0F) return;
        double r = Math.toRadians(degrees);
        double c = Math.cos(r);
        double s = Math.sin(r);
        double x = point[0];
        double z = point[2];
        point[0] = c * x + s * z;
        point[2] = -s * x + c * z;
    }

    /** Affine transform with the same post-multiplication order as OpenGL. */
    private static final class Mat {
        double m00 = 1, m01, m02, m03;
        double m10, m11 = 1, m12, m13;
        double m20, m21, m22 = 1, m23;

        void translate(double x, double y, double z) {
            m03 += m00 * x + m01 * y + m02 * z;
            m13 += m10 * x + m11 * y + m12 * z;
            m23 += m20 * x + m21 * y + m22 * z;
        }

        void rotate(double degrees, double ax, double ay, double az) {
            double len = Math.sqrt(ax * ax + ay * ay + az * az);
            if (len < EPS || degrees == 0.0) return;
            ax /= len;
            ay /= len;
            az /= len;
            double r = Math.toRadians(degrees);
            double c = Math.cos(r);
            double s = Math.sin(r);
            double t = 1.0 - c;

            double r00 = t * ax * ax + c;
            double r01 = t * ax * ay - s * az;
            double r02 = t * ax * az + s * ay;
            double r10 = t * ax * ay + s * az;
            double r11 = t * ay * ay + c;
            double r12 = t * ay * az - s * ax;
            double r20 = t * ax * az - s * ay;
            double r21 = t * ay * az + s * ax;
            double r22 = t * az * az + c;

            double n00 = m00 * r00 + m01 * r10 + m02 * r20;
            double n01 = m00 * r01 + m01 * r11 + m02 * r21;
            double n02 = m00 * r02 + m01 * r12 + m02 * r22;
            double n10 = m10 * r00 + m11 * r10 + m12 * r20;
            double n11 = m10 * r01 + m11 * r11 + m12 * r21;
            double n12 = m10 * r02 + m11 * r12 + m12 * r22;
            double n20 = m20 * r00 + m21 * r10 + m22 * r20;
            double n21 = m20 * r01 + m21 * r11 + m22 * r21;
            double n22 = m20 * r02 + m21 * r12 + m22 * r22;
            m00 = n00;
            m01 = n01;
            m02 = n02;
            m10 = n10;
            m11 = n11;
            m12 = n12;
            m20 = n20;
            m21 = n21;
            m22 = n22;
        }

        void point(double x, double y, double z, double[] out) {
            out[0] = m00 * x + m01 * y + m02 * z + m03;
            out[1] = m10 * x + m11 * y + m12 * z + m13;
            out[2] = m20 * x + m21 * y + m22 * z + m23;
        }
    }
}
