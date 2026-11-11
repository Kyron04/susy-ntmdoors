package com.ntmdoors.client.render;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Geometric Sutherland-Hodgman polygon clipping engine for Wavefront OBJ triangles in model space.
 * Operates with zero per-frame heap allocations using ThreadLocal scratch buffers and
 * interpolates vertex position, texture UV, and surface normals.
 */
public final class ObjClipping {

    private static final float EPSILON = 1e-6f;
    private static final float MIN_TRI_AREA_SQ = 1e-10f;
    public static final int STRIDE = 8; // x, y, z, u, v, nx, ny, nz
    private static final int MAX_VERTS = 16;

    private static final ThreadLocal<ClipScratch> SCRATCH = ThreadLocal.withInitial(ClipScratch::new);

    private static final class ClipScratch {
        final float[] bufA = new float[MAX_VERTS * STRIDE];
        final float[] bufB = new float[MAX_VERTS * STRIDE];
        final Vector3f vPos = new Vector3f();
        final Vector3f vNorm = new Vector3f();
    }

    private ObjClipping() {}

    /**
     * Clips a triangle against the given ClipBounds in model space and emits the resulting
     * triangulated polygon into the target VertexConsumer.
     *
     * @param renderMatrix   Model-to-Camera transformation matrix (applied at consumer.vertex)
     * @param renderNormal   Model-to-Camera normal matrix
     * @param groupTransform Group animation matrix transforming raw OBJ vertices to model space (nullable)
     * @param groupNormal    Group animation normal matrix (nullable)
     * @param consumer       Target VertexConsumer
     * @param rawData        Raw float array of OBJ group (8 floats per vertex)
     * @param off0           Offset for vertex 0
     * @param off1           Offset for vertex 1
     * @param off2           Offset for vertex 2
     * @param bounds         Model-space clipping bounds
     * @param light          Packed light coordinate
     * @param overlay        Packed overlay coordinate
     * @param r              Red color component
     * @param g              Green color component
     * @param b              Blue color component
     * @param a              Alpha color component
     */
    public static void clipAndEmitTriangle(
        Matrix4f renderMatrix,
        Matrix3f renderNormal,
        Matrix4f groupTransform,
        Matrix3f groupNormal,
        VertexConsumer consumer,
        float[] rawData,
        int off0, int off1, int off2,
        ClipBounds bounds,
        int light, int overlay,
        float r, float g, float b, float a
    ) {
        if (bounds == null || bounds.isInfinite()) {
            // Trivial accept: no active clipping planes
            emitTransformedVertex(consumer, renderMatrix, renderNormal, groupTransform, groupNormal,
                    rawData, off0, light, overlay, r, g, b, a);
            emitTransformedVertex(consumer, renderMatrix, renderNormal, groupTransform, groupNormal,
                    rawData, off1, light, overlay, r, g, b, a);
            emitTransformedVertex(consumer, renderMatrix, renderNormal, groupTransform, groupNormal,
                    rawData, off2, light, overlay, r, g, b, a);
            return;
        }

        ClipScratch sc = SCRATCH.get();
        float[] currentIn = sc.bufA;
        float[] currentOut = sc.bufB;

        // 1. Transform raw vertices to model space
        transformVertex(rawData, off0, currentIn, 0, groupTransform, groupNormal, sc);
        transformVertex(rawData, off1, currentIn, STRIDE, groupTransform, groupNormal, sc);
        transformVertex(rawData, off2, currentIn, STRIDE * 2, groupTransform, groupNormal, sc);

        // 2. Triangle-level Trivial Rejection check
        float minX = Math.min(currentIn[0], Math.min(currentIn[STRIDE], currentIn[STRIDE * 2]));
        float maxX = Math.max(currentIn[0], Math.max(currentIn[STRIDE], currentIn[STRIDE * 2]));
        if (!Double.isInfinite(bounds.minX()) && maxX < (float) bounds.minX() - EPSILON) return;
        if (!Double.isInfinite(bounds.maxX()) && minX > (float) bounds.maxX() + EPSILON) return;

        float minY = Math.min(currentIn[1], Math.min(currentIn[STRIDE + 1], currentIn[STRIDE * 2 + 1]));
        float maxY = Math.max(currentIn[1], Math.max(currentIn[STRIDE + 1], currentIn[STRIDE * 2 + 1]));
        if (!Double.isInfinite(bounds.minY()) && maxY < (float) bounds.minY() - EPSILON) return;
        if (!Double.isInfinite(bounds.maxY()) && minY > (float) bounds.maxY() + EPSILON) return;

        float minZ = Math.min(currentIn[2], Math.min(currentIn[STRIDE + 2], currentIn[STRIDE * 2 + 2]));
        float maxZ = Math.max(currentIn[2], Math.max(currentIn[STRIDE + 2], currentIn[STRIDE * 2 + 2]));
        if (!Double.isInfinite(bounds.minZ()) && maxZ < (float) bounds.minZ() - EPSILON) return;
        if (!Double.isInfinite(bounds.maxZ()) && minZ > (float) bounds.maxZ() + EPSILON) return;

        // 3. Triangle-level Trivial Acceptance check
        boolean triInside =
            (Double.isInfinite(bounds.minX()) || minX >= (float) bounds.minX() - EPSILON) &&
            (Double.isInfinite(bounds.maxX()) || maxX <= (float) bounds.maxX() + EPSILON) &&
            (Double.isInfinite(bounds.minY()) || minY >= (float) bounds.minY() - EPSILON) &&
            (Double.isInfinite(bounds.maxY()) || maxY <= (float) bounds.maxY() + EPSILON) &&
            (Double.isInfinite(bounds.minZ()) || minZ >= (float) bounds.minZ() - EPSILON) &&
            (Double.isInfinite(bounds.maxZ()) || maxZ <= (float) bounds.maxZ() + EPSILON);

        if (triInside) {
            emitVertex(consumer, renderMatrix, renderNormal, currentIn, 0, light, overlay, r, g, b, a);
            emitVertex(consumer, renderMatrix, renderNormal, currentIn, STRIDE, light, overlay, r, g, b, a);
            emitVertex(consumer, renderMatrix, renderNormal, currentIn, STRIDE * 2, light, overlay, r, g, b, a);
            return;
        }

        // 4. Sutherland-Hodgman clipping against active planes
        int count = 3;

        // +X plane (x >= minX)
        if (!Double.isInfinite(bounds.minX())) {
            count = clipPlaneMin(currentIn, count, currentOut, 0, (float) bounds.minX());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }
        // -X plane (x <= maxX)
        if (!Double.isInfinite(bounds.maxX())) {
            count = clipPlaneMax(currentIn, count, currentOut, 0, (float) bounds.maxX());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }
        // +Y plane (y >= minY)
        if (!Double.isInfinite(bounds.minY())) {
            count = clipPlaneMin(currentIn, count, currentOut, 1, (float) bounds.minY());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }
        // -Y plane (y <= maxY)
        if (!Double.isInfinite(bounds.maxY())) {
            count = clipPlaneMax(currentIn, count, currentOut, 1, (float) bounds.maxY());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }
        // +Z plane (z >= minZ)
        if (!Double.isInfinite(bounds.minZ())) {
            count = clipPlaneMin(currentIn, count, currentOut, 2, (float) bounds.minZ());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }
        // -Z plane (z <= maxZ)
        if (!Double.isInfinite(bounds.maxZ())) {
            count = clipPlaneMax(currentIn, count, currentOut, 2, (float) bounds.maxZ());
            if (count < 3) return;
            float[] tmp = currentIn; currentIn = currentOut; currentOut = tmp;
        }

        // 5. Triangulate convex polygon via triangle fan rooted at vertex 0
        for (int i = 1; i < count - 1; i++) {
            int offA = 0;
            int offB = i * STRIDE;
            int offC = (i + 1) * STRIDE;

            // Reject degenerate / collinear triangles
            float e1x = currentIn[offB] - currentIn[offA];
            float e1y = currentIn[offB + 1] - currentIn[offA + 1];
            float e1z = currentIn[offB + 2] - currentIn[offA + 2];
            float e2x = currentIn[offC] - currentIn[offA];
            float e2y = currentIn[offC + 1] - currentIn[offA + 1];
            float e2z = currentIn[offC + 2] - currentIn[offA + 2];

            float cx = e1y * e2z - e1z * e2y;
            float cy = e1z * e2x - e1x * e2z;
            float cz = e1x * e2y - e1y * e2x;
            if (cx * cx + cy * cy + cz * cz < MIN_TRI_AREA_SQ) continue;

            emitVertex(consumer, renderMatrix, renderNormal, currentIn, offA, light, overlay, r, g, b, a);
            emitVertex(consumer, renderMatrix, renderNormal, currentIn, offB, light, overlay, r, g, b, a);
            emitVertex(consumer, renderMatrix, renderNormal, currentIn, offC, light, overlay, r, g, b, a);
        }
    }

    private static int clipPlaneMin(float[] in, int inCount, float[] out, int axis, float limit) {
        int outCount = 0;
        int prev = (inCount - 1) * STRIDE;
        float prevVal = in[prev + axis];
        boolean prevInside = prevVal >= limit - EPSILON;

        for (int i = 0; i < inCount; i++) {
            int curr = i * STRIDE;
            float currVal = in[curr + axis];
            boolean currInside = currVal >= limit - EPSILON;

            if (currInside) {
                if (!prevInside) {
                    float t = (limit - prevVal) / (currVal - prevVal);
                    t = Math.max(0.0f, Math.min(1.0f, t));
                    interpolate(out, outCount * STRIDE, in, prev, curr, t);
                    out[outCount * STRIDE + axis] = limit;
                    outCount++;
                }
                copy(out, outCount * STRIDE, in, curr);
                outCount++;
            } else if (prevInside) {
                float t = (limit - prevVal) / (currVal - prevVal);
                t = Math.max(0.0f, Math.min(1.0f, t));
                interpolate(out, outCount * STRIDE, in, prev, curr, t);
                out[outCount * STRIDE + axis] = limit;
                outCount++;
            }
            prev = curr;
            prevVal = currVal;
            prevInside = currInside;
        }
        return outCount;
    }

    private static int clipPlaneMax(float[] in, int inCount, float[] out, int axis, float limit) {
        int outCount = 0;
        int prev = (inCount - 1) * STRIDE;
        float prevVal = in[prev + axis];
        boolean prevInside = prevVal <= limit + EPSILON;

        for (int i = 0; i < inCount; i++) {
            int curr = i * STRIDE;
            float currVal = in[curr + axis];
            boolean currInside = currVal <= limit + EPSILON;

            if (currInside) {
                if (!prevInside) {
                    float t = (limit - prevVal) / (currVal - prevVal);
                    t = Math.max(0.0f, Math.min(1.0f, t));
                    interpolate(out, outCount * STRIDE, in, prev, curr, t);
                    out[outCount * STRIDE + axis] = limit;
                    outCount++;
                }
                copy(out, outCount * STRIDE, in, curr);
                outCount++;
            } else if (prevInside) {
                float t = (limit - prevVal) / (currVal - prevVal);
                t = Math.max(0.0f, Math.min(1.0f, t));
                interpolate(out, outCount * STRIDE, in, prev, curr, t);
                out[outCount * STRIDE + axis] = limit;
                outCount++;
            }
            prev = curr;
            prevVal = currVal;
            prevInside = currInside;
        }
        return outCount;
    }

    private static void transformVertex(float[] src, int srcOff, float[] dst, int dstOff,
                                        Matrix4f posMat, Matrix3f normMat, ClipScratch sc) {
        if (posMat != null) {
            sc.vPos.set(src[srcOff], src[srcOff + 1], src[srcOff + 2]);
            posMat.transformPosition(sc.vPos);
            dst[dstOff] = sc.vPos.x;
            dst[dstOff + 1] = sc.vPos.y;
            dst[dstOff + 2] = sc.vPos.z;
        } else {
            dst[dstOff] = src[srcOff];
            dst[dstOff + 1] = src[srcOff + 1];
            dst[dstOff + 2] = src[srcOff + 2];
        }

        dst[dstOff + 3] = src[srcOff + 3]; // U
        dst[dstOff + 4] = src[srcOff + 4]; // V

        if (normMat != null) {
            sc.vNorm.set(src[srcOff + 5], src[srcOff + 6], src[srcOff + 7]);
            normMat.transform(sc.vNorm);
            dst[dstOff + 5] = sc.vNorm.x;
            dst[dstOff + 6] = sc.vNorm.y;
            dst[dstOff + 7] = sc.vNorm.z;
        } else {
            dst[dstOff + 5] = src[srcOff + 5];
            dst[dstOff + 6] = src[srcOff + 6];
            dst[dstOff + 7] = src[srcOff + 7];
        }
    }

    private static void interpolate(float[] dst, int dOff, float[] src, int sA, int sB, float t) {
        dst[dOff] = src[sA] + t * (src[sB] - src[sA]);
        dst[dOff + 1] = src[sA + 1] + t * (src[sB + 1] - src[sA + 1]);
        dst[dOff + 2] = src[sA + 2] + t * (src[sB + 2] - src[sA + 2]);

        dst[dOff + 3] = src[sA + 3] + t * (src[sB + 3] - src[sA + 3]);
        dst[dOff + 4] = src[sA + 4] + t * (src[sB + 4] - src[sA + 4]);

        float nx = src[sA + 5] + t * (src[sB + 5] - src[sA + 5]);
        float ny = src[sA + 6] + t * (src[sB + 6] - src[sA + 6]);
        float nz = src[sA + 7] + t * (src[sB + 7] - src[sA + 7]);
        float lenSq = nx * nx + ny * ny + nz * nz;
        if (lenSq > 1e-12f) {
            float inv = (float) (1.0 / Math.sqrt(lenSq));
            nx *= inv;
            ny *= inv;
            nz *= inv;
        }
        dst[dOff + 5] = nx;
        dst[dOff + 6] = ny;
        dst[dOff + 7] = nz;
    }

    private static void copy(float[] dst, int dOff, float[] src, int sOff) {
        System.arraycopy(src, sOff, dst, dOff, STRIDE);
    }

    private static void emitVertex(VertexConsumer consumer, Matrix4f mat, Matrix3f normMat,
                                   float[] buf, int off, int light, int overlay,
                                   float r, float g, float b, float a) {
        consumer.vertex(mat, buf[off], buf[off + 1], buf[off + 2])
                .color(r, g, b, a)
                .texture(buf[off + 3], buf[off + 4])
                .overlay(overlay)
                .light(light)
                .normal(normMat, buf[off + 5], buf[off + 6], buf[off + 7])
                .next();
    }

    private static void emitTransformedVertex(VertexConsumer consumer, Matrix4f renderMat, Matrix3f renderNorm,
                                              Matrix4f groupMat, Matrix3f groupNorm,
                                              float[] raw, int off, int light, int overlay,
                                              float r, float g, float b, float a) {
        float x = raw[off], y = raw[off + 1], z = raw[off + 2];
        float u = raw[off + 3], v = raw[off + 4];
        float nx = raw[off + 5], ny = raw[off + 6], nz = raw[off + 7];

        if (groupMat != null) {
            float gx = groupMat.m00() * x + groupMat.m10() * y + groupMat.m20() * z + groupMat.m30();
            float gy = groupMat.m01() * x + groupMat.m11() * y + groupMat.m21() * z + groupMat.m31();
            float gz = groupMat.m02() * x + groupMat.m12() * y + groupMat.m22() * z + groupMat.m32();
            x = gx; y = gy; z = gz;
        }
        if (groupNorm != null) {
            float gnx = groupNorm.m00() * nx + groupNorm.m10() * ny + groupNorm.m20() * nz;
            float gny = groupNorm.m01() * nx + groupNorm.m11() * ny + groupNorm.m21() * nz;
            float gnz = groupNorm.m02() * nx + groupNorm.m12() * ny + groupNorm.m22() * nz;
            nx = gnx; ny = gny; nz = gnz;
        }

        consumer.vertex(renderMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(renderNorm, nx, ny, nz)
                .next();
    }
}
