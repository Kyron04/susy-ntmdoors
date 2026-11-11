package com.ntmdoors.test;

import com.ntmdoors.client.render.ClipBounds;
import com.ntmdoors.client.render.KeyframeAnimation;
import com.ntmdoors.client.render.ObjClipping;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChallengerM1StressTest {

    private static final Path ANIM_FILE = Path.of("src", "main", "resources", "assets", "ntmdoors", "models", "doors", "transition_seal.anim");

    // =========================================================================
    // 1. SUTHERLAND-HODGMAN CLIPPING STRESS TESTS
    // =========================================================================

    @Test
    @DisplayName("SH-1: Trivial Accept across arbitrary bounding volumes")
    public void testTrivialAccept() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        float[] rawData = new float[]{
            0.2f, 0.3f, 0.4f,  0.1f, 0.2f,  0.0f, 1.0f, 0.0f,
            0.5f, 0.3f, 0.4f,  0.3f, 0.4f,  0.0f, 1.0f, 0.0f,
            0.2f, 0.8f, 0.4f,  0.5f, 0.6f,  0.0f, 1.0f, 0.0f
        };

        ClipBounds bounds = new ClipBounds(0.0, 1.0, 0.0, 1.0, 0.0, 1.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertEquals(3, consumer.vertices.size());
        assertEquals(0.2f, consumer.vertices.get(0).x(), 1e-4f);
        assertEquals(0.5f, consumer.vertices.get(1).x(), 1e-4f);
        assertEquals(0.2f, consumer.vertices.get(2).x(), 1e-4f);
    }

    @Test
    @DisplayName("SH-2: Trivial Reject across all 6 directional planes")
    public void testTrivialRejectAllAxes() {
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();
        ClipBounds bounds = new ClipBounds(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);

        // Test offset in +X, -X, +Y, -Y, +Z, -Z
        float[][] offsets = new float[][]{
            {5.0f, 0.0f, 0.0f},
            {-5.0f, 0.0f, 0.0f},
            {0.0f, 5.0f, 0.0f},
            {0.0f, -5.0f, 0.0f},
            {0.0f, 0.0f, 5.0f},
            {0.0f, 0.0f, -5.0f}
        };

        for (float[] off : offsets) {
            ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
            float[] rawData = new float[]{
                off[0],        off[1],        off[2],        0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
                off[0] + 0.5f, off[1],        off[2],        0.5f, 0.0f,  0.0f, 1.0f, 0.0f,
                off[0],        off[1] + 0.5f, off[2],        0.0f, 0.5f,  0.0f, 1.0f, 0.0f
            };

            ObjClipping.clipAndEmitTriangle(
                renderMat, renderNorm, null, null,
                consumer, rawData, 0, 8, 16,
                bounds, 0x00F000F0, 0,
                1.0f, 1.0f, 1.0f, 1.0f
            );

            assertEquals(0, consumer.vertices.size(), "Should reject triangle at offset " + off[0] + "," + off[1] + "," + off[2]);
        }
    }

    @Test
    @DisplayName("SH-3: Multi-Plane Straddling - 2 Plane Corners producing a Pentagon (5-gon -> 3 triangles = 9 vertices)")
    public void testStraddleTwoPlanesPentagon() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Triangle vertices:
        // A: (-2.0, 0.0, 0.0) -> sliced by X >= -1
        // B: ( 2.0, 0.0, 0.0) -> sliced by X <=  1
        // C: ( 0.0, 3.0, 0.0) -> inside bounds
        float[] rawData = new float[]{
            -2.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
             2.0f, 0.0f, 0.0f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
             0.0f, 3.0f, 0.0f,  0.5f, 1.0f,  0.0f, 0.0f, 1.0f
        };

        // Clip bounds: -1.0 <= X <= 1.0 (cutting off both corner A and corner B)
        ClipBounds bounds = new ClipBounds(-1.0, 1.0, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // Cutting off 2 corners turns a triangle into a pentagon (5 vertices).
        // 5 vertices triangulated via fan produces 5 - 2 = 3 triangles = 9 vertices.
        assertEquals(9, consumer.vertices.size(), "Pentagon must yield 9 vertices (3 triangles)");

        for (ObjClippingTest.Vertex v : consumer.vertices) {
            assertTrue(v.x() <= 1.00001f && v.x() >= -1.00001f, "X must be in [-1, 1]: " + v.x());
            assertTrue(v.y() >= -0.00001f && v.y() <= 3.00001f, "Y must be in [0, 3]: " + v.y());
        }
    }

    @Test
    @DisplayName("SH-4: Multi-Plane Straddling - 3 Plane Corners producing a Hexagon (6-gon -> 4 triangles = 12 vertices)")
    public void testStraddleThreePlanesHexagon() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Equilateral triangle in 3D:
        // Center: (0.7, 0.7, 0.7) which is inside [0, 1]^3
        // V0: (1.5, 0.3, 0.3) -> X > 1
        // V1: (0.3, 1.5, 0.3) -> Y > 1
        // V2: (0.3, 0.3, 1.5) -> Z > 1
        float[] rawData = new float[]{
            1.5f, 0.3f, 0.3f,  0.0f, 0.0f,  0.577f, 0.577f, 0.577f,
            0.3f, 1.5f, 0.3f,  1.0f, 0.0f,  0.577f, 0.577f, 0.577f,
            0.3f, 0.3f, 1.5f,  0.5f, 1.0f,  0.577f, 0.577f, 0.577f
        };

        // Clip bounds: X <= 1.0, Y <= 1.0, Z <= 1.0
        ClipBounds bounds = new ClipBounds(0.0, 1.0, 0.0, 1.0, 0.0, 1.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // Chopping all 3 corners produces a hexagon (6 vertices).
        // 6 vertices triangulated via fan produces 6 - 2 = 4 triangles = 12 vertices.
        assertEquals(12, consumer.vertices.size(), "Hexagon must yield 12 vertices (4 triangles)");

        for (ObjClippingTest.Vertex v : consumer.vertices) {
            assertTrue(v.x() <= 1.00001f && v.x() >= -0.00001f, "X in bounds: " + v.x());
            assertTrue(v.y() <= 1.00001f && v.y() >= -0.00001f, "Y in bounds: " + v.y());
            assertTrue(v.z() <= 1.00001f && v.z() >= -0.00001f, "Z in bounds: " + v.z());
        }
    }

    @Test
    @DisplayName("SH-5: Coplanar Triangle at exact boundary plane is preserved")
    public void testCoplanarTriangleAtBoundary() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Triangle exactly on plane Z = 2.0
        float[] rawData = new float[]{
            0.0f, 0.0f, 2.0f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 2.0f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 2.0f,  0.0f, 1.0f,  0.0f, 0.0f, 1.0f
        };

        // Clip plane maxZ = 2.0
        ClipBounds bounds = new ClipBounds(-5.0, 5.0, -5.0, 5.0, -5.0, 2.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertEquals(3, consumer.vertices.size(), "Coplanar triangle at boundary must be retained");
        for (ObjClippingTest.Vertex v : consumer.vertices) {
            assertEquals(2.0f, v.z(), 1e-4f, "Z must remain exactly 2.0");
        }
    }

    @Test
    @DisplayName("SH-6: Slanted Triangle producing microscopic sliver is safely rejected without NaN")
    public void testSliverTriangleRejection() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Triangle barely crossing plane x = 0: two vertices at x = -1e-7, one at x = -1e-7 + 1e-8
        float[] rawData = new float[]{
            1.000000001f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
            1.000000002f, 1.0f, 0.0f,  0.0f, 1.0f,  0.0f, 1.0f, 0.0f,
            0.999999999f, 0.5f, 0.0f,  1.0f, 0.5f,  0.0f, 1.0f, 0.0f
        };

        ClipBounds bounds = new ClipBounds(-10.0, 1.0, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // Either rejected due to area < 1e-10 or emitted without NaN
        for (ObjClippingTest.Vertex v : consumer.vertices) {
            assertFalse(Float.isNaN(v.x()));
            assertFalse(Float.isNaN(v.y()));
            assertFalse(Float.isNaN(v.z()));
            assertFalse(Float.isNaN(v.u()));
            assertFalse(Float.isNaN(v.v()));
            assertFalse(Float.isNaN(v.nx()));
            assertFalse(Float.isNaN(v.ny()));
            assertFalse(Float.isNaN(v.nz()));
        }
    }

    @Test
    @DisplayName("SH-7: Exact Attribute (UV and Normals) Midpoint Interpolation")
    public void testAttributeInterpolationExactness() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Edge 0->1 goes from (-1.0, 0, 0) to (1.0, 0, 0)
        // Midpoint at x = 0 is t = 0.5
        // V0: U=0.2, V=0.4, Normal=(1, 0, 0)
        // V1: U=0.8, V=0.6, Normal=(0, 1, 0)
        // V2: (0.0, 1.0, 0), U=0.5, V=1.0, Normal=(0, 0, 1)
        float[] rawData = new float[]{
            -1.0f, 0.0f, 0.0f,  0.2f, 0.4f,  1.0f, 0.0f, 0.0f,
             1.0f, 0.0f, 0.0f,  0.8f, 0.6f,  0.0f, 1.0f, 0.0f,
             0.0f, 1.0f, 0.0f,  0.5f, 1.0f,  0.0f, 0.0f, 1.0f
        };

        // Clip x >= 0.0
        ClipBounds bounds = new ClipBounds(0.0, 10.0, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // Must emit a triangle with the interpolated vertex at (0, 0, 0)
        boolean foundMidpoint = false;
        for (ObjClippingTest.Vertex v : consumer.vertices) {
            if (Math.abs(v.x()) < 1e-5f && Math.abs(v.y()) < 1e-5f) {
                foundMidpoint = true;
                // U must be 0.5, V must be 0.5
                assertEquals(0.5f, v.u(), 1e-4f, "Interpolated U must be exact midpoint 0.5");
                assertEquals(0.5f, v.v(), 1e-4f, "Interpolated V must be exact midpoint 0.5");

                // Normal is normalized (1, 1, 0) / sqrt(2)
                float expectedNormComp = 1.0f / (float) Math.sqrt(2.0);
                assertEquals(expectedNormComp, v.nx(), 1e-3f, "Normal nx must be normalized");
                assertEquals(expectedNormComp, v.ny(), 1e-3f, "Normal ny must be normalized");
                assertEquals(0.0f, v.nz(), 1e-4f, "Normal nz must be 0.0");

                float len = (float) Math.sqrt(v.nx() * v.nx() + v.ny() * v.ny() + v.nz() * v.nz());
                assertEquals(1.0f, len, 1e-4f, "Normal vector must have unit length 1.0");
            }
        }
        assertTrue(foundMidpoint, "Must find clipped midpoint vertex at (0, 0, 0)");
    }

    @Test
    @DisplayName("SH-8: Polygon Area Conservation and Normal Half-Space Preservation")
    public void testAreaConservationAndWinding() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Right triangle from (-1, 0) to (1, 0) to (1, 2)
        // Total area = 0.5 * base * height = 0.5 * 2 * 2 = 2.0
        // Clipped by x >= 0:
        // Left part x < 0 is triangle from (-1, 0) to (0, 0) to (0, 1): area = 0.5 * 1 * 1 = 0.5
        // Right part x >= 0 is trapezoid: vertices (0,0), (1,0), (1,2), (0,1): area = 2.0 - 0.5 = 1.5
        float[] rawData = new float[]{
            -1.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
             1.0f, 0.0f, 0.0f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
             1.0f, 2.0f, 0.0f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f
        };

        ClipBounds bounds = new ClipBounds(0.0, 10.0, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // Sum areas of all emitted triangles
        List<ObjClippingTest.Vertex> verts = consumer.vertices;
        assertEquals(6, verts.size(), "Trapezoid must emit 2 triangles (6 vertices)");

        float totalArea = 0.0f;
        for (int i = 0; i < verts.size(); i += 3) {
            ObjClippingTest.Vertex a = verts.get(i);
            ObjClippingTest.Vertex b = verts.get(i + 1);
            ObjClippingTest.Vertex c = verts.get(i + 2);

            float e1x = b.x() - a.x(), e1y = b.y() - a.y(), e1z = b.z() - a.z();
            float e2x = c.x() - a.x(), e2y = c.y() - a.y(), e2z = c.z() - a.z();

            float cx = e1y * e2z - e1z * e2y;
            float cy = e1z * e2x - e1x * e2z;
            float cz = e1x * e2y - e1y * e2x;

            // Normal must point along +Z (same as input)
            assertTrue(cz > 0, "Emitted triangle must preserve CCW winding order (+Z normal): " + cz);

            float triArea = 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            totalArea += triArea;
        }

        // Expected trapezoid area is exactly 1.5
        assertEquals(1.5f, totalArea, 1e-4f, "Sum of fan triangle areas must exactly equal analytical area 1.5");
    }

    @Test
    @DisplayName("SH-9: Multi-Threaded Concurrent Clipping Thread-Safety")
    public void testConcurrentClippingThreadSafety() throws InterruptedException {
        int threadCount = 16;
        int iterationsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];
        boolean[] failed = new boolean[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                try {
                    ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
                    Matrix4f renderMat = new Matrix4f().identity();
                    Matrix3f renderNorm = new Matrix3f().identity();
                    ClipBounds bounds = new ClipBounds(0.0, 10.0, -10.0, 10.0, -10.0, 10.0);

                    float[] rawData = new float[]{
                        -1.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
                         1.0f, 0.0f, 0.0f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
                         1.0f, 2.0f, 0.0f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f
                    };

                    for (int i = 0; i < iterationsPerThread; i++) {
                        consumer.vertices.clear();
                        ObjClipping.clipAndEmitTriangle(
                            renderMat, renderNorm, null, null,
                            consumer, rawData, 0, 8, 16,
                            bounds, 0x00F000F0, 0,
                            1.0f, 1.0f, 1.0f, 1.0f
                        );
                        if (consumer.vertices.size() != 6) {
                            failed[threadIdx] = true;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    failed[threadIdx] = true;
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        for (int t = 0; t < threadCount; t++) {
            assertFalse(failed[t], "Thread " + t + " failed during concurrent clipping execution");
        }
    }

    @Test
    @DisplayName("SH-10: GroupTransform Pre-Clipping Model-Space Integration")
    public void testGroupTransformPreClipping() {
        ObjClippingTest.MockVertexConsumer consumer = new ObjClippingTest.MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Raw triangle centered around origin
        float[] rawData = new float[]{
            0.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, 0.0f,  1.0f, 0.0f,  0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,  0.0f, 1.0f,  0.0f, 1.0f, 0.0f
        };

        // Group transform translates by +10 in X: model-space triangle will be at x in [10, 11]
        Matrix4f groupTransform = new Matrix4f().translation(10.0f, 0.0f, 0.0f);
        Matrix3f groupNormal = new Matrix3f().identity();

        // Clip bounds: X in [0, 5]. Model space triangle at [10, 11] must be trivially rejected!
        ClipBounds boundsReject = new ClipBounds(0.0, 5.0, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, groupTransform, groupNormal,
            consumer, rawData, 0, 8, 16,
            boundsReject, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );
        assertEquals(0, consumer.vertices.size(), "Group transformed triangle outside bounds must be rejected");

        // Clip bounds: X in [0, 10.5]. Must bisect triangle at model space x = 10.5
        consumer.vertices.clear();
        ClipBounds boundsBisect = new ClipBounds(0.0, 10.5, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, groupTransform, groupNormal,
            consumer, rawData, 0, 8, 16,
            boundsBisect, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );
        assertTrue(consumer.vertices.size() >= 3, "Must emit clipped triangle");
        for (ObjClippingTest.Vertex v : consumer.vertices) {
            assertTrue(v.x() <= 10.50001f, "Vertex x must not exceed 10.5: " + v.x());
        }
    }

    // =========================================================================
    // 2. KEYFRAME ANIMATION ENGINE EMPIRICAL STRESS TESTS
    // =========================================================================

    @Test
    @DisplayName("ANIM-4: Shepperd's Algorithm - Exhaustive Branch Coverage (180-deg rotations on all 3 axes)")
    public void testShepperdAllBranches() {
        // Branch 1: 180 deg around X (diag(1, -1, -1))
        float[] rotX180 = new float[]{
             1.0f,  0.0f,  0.0f, 1.0f,
             0.0f, -1.0f,  0.0f, 2.0f,
             0.0f,  0.0f, -1.0f, 3.0f
        };
        KeyframeAnimation.Sample sX = new KeyframeAnimation.Sample(0.0f, rotX180);
        assertEquals(1.0f, Math.abs(sX.rotation.x), 1e-4f, "Branch 1 must yield |qx|=1");
        assertEquals(0.0f, sX.rotation.y, 1e-4f);
        assertEquals(0.0f, sX.rotation.z, 1e-4f);
        assertEquals(0.0f, sX.rotation.w, 1e-4f);

        // Matrix reconstruction check for Branch 1
        Matrix4f reconX = new Matrix4f().translationRotate(sX.tx, sX.ty, sX.tz, sX.rotation);
        assertEquals(1.0f, reconX.m00(), 1e-4f);
        assertEquals(-1.0f, reconX.m11(), 1e-4f);
        assertEquals(-1.0f, reconX.m22(), 1e-4f);
        assertEquals(1.0f, reconX.m30(), 1e-4f);

        // Branch 2: 180 deg around Y (diag(-1, 1, -1))
        float[] rotY180 = new float[]{
            -1.0f,  0.0f,  0.0f, 4.0f,
             0.0f,  1.0f,  0.0f, 5.0f,
             0.0f,  0.0f, -1.0f, 6.0f
        };
        KeyframeAnimation.Sample sY = new KeyframeAnimation.Sample(0.0f, rotY180);
        assertEquals(0.0f, sX.rotation.y, 1e-4f);
        assertEquals(1.0f, Math.abs(sY.rotation.y), 1e-4f, "Branch 2 must yield |qy|=1");
        assertEquals(0.0f, sY.rotation.x, 1e-4f);
        assertEquals(0.0f, sY.rotation.z, 1e-4f);

        Matrix4f reconY = new Matrix4f().translationRotate(sY.tx, sY.ty, sY.tz, sY.rotation);
        assertEquals(-1.0f, reconY.m00(), 1e-4f);
        assertEquals(1.0f, reconY.m11(), 1e-4f);
        assertEquals(-1.0f, reconY.m22(), 1e-4f);

        // Branch 3: 180 deg around Z (diag(-1, -1, 1))
        float[] rotZ180 = new float[]{
            -1.0f,  0.0f,  0.0f, 7.0f,
             0.0f, -1.0f,  0.0f, 8.0f,
             0.0f,  0.0f,  1.0f, 9.0f
        };
        KeyframeAnimation.Sample sZ = new KeyframeAnimation.Sample(0.0f, rotZ180);
        assertEquals(1.0f, Math.abs(sZ.rotation.z), 1e-4f, "Branch 3 must yield |qz|=1");
        assertEquals(0.0f, sZ.rotation.x, 1e-4f);
        assertEquals(0.0f, sZ.rotation.y, 1e-4f);

        Matrix4f reconZ = new Matrix4f().translationRotate(sZ.tx, sZ.ty, sZ.tz, sZ.rotation);
        assertEquals(-1.0f, reconZ.m00(), 1e-4f);
        assertEquals(-1.0f, reconZ.m11(), 1e-4f);
        assertEquals(1.0f, reconZ.m22(), 1e-4f);
    }

    @Test
    @DisplayName("ANIM-5: Out-of-bounds OpenFraction Clamping and Boundary Invariance")
    public void testOpenFractionBoundaryClamping() {
        float[] m0 = new float[]{ 1,0,0, 1.0f, 0,1,0, 2.0f, 0,0,1, 3.0f };
        float[] m1 = new float[]{ 1,0,0, 5.0f, 0,1,0, 6.0f, 0,0,1, 7.0f };
        KeyframeAnimation.Sample[] samples = new KeyframeAnimation.Sample[]{
            new KeyframeAnimation.Sample(0.0f, m0),
            new KeyframeAnimation.Sample(1.0f, m1)
        };
        KeyframeAnimation anim = new KeyframeAnimation(samples);

        Matrix4f destNeg = anim.getInterpolatedMatrix(-100.0f, new Matrix4f());
        Vector3f transNeg = destNeg.getTranslation(new Vector3f());
        assertEquals(1.0f, transNeg.x, 1e-4f, "t < 0 must clamp to sample 0");
        assertEquals(2.0f, transNeg.y, 1e-4f);
        assertEquals(3.0f, transNeg.z, 1e-4f);

        Matrix4f destOver = anim.getInterpolatedMatrix(100.0f, new Matrix4f());
        Vector3f transOver = destOver.getTranslation(new Vector3f());
        assertEquals(5.0f, transOver.x, 1e-4f, "t > 1 must clamp to sample 1");
        assertEquals(6.0f, transOver.y, 1e-4f);
        assertEquals(7.0f, transOver.z, 1e-4f);
    }


    // =========================================================================
    // 2. KEYFRAME ANIMATION ENGINE EMPIRICAL STRESS TESTS
    // =========================================================================

    @Test
    @DisplayName("ANIM-1: Precision and Sample Count of transition_seal.anim (11,540 samples)")
    public void testTransitionSealFullParse() throws Exception {
        assertTrue(Files.exists(ANIM_FILE), "transition_seal.anim must exist");

        try (InputStream in = new FileInputStream(ANIM_FILE.toFile())) {
            Map<String, KeyframeAnimation> anims = KeyframeAnimation.parse(in);
            assertEquals(20, anims.size(), "Must contain exactly 20 groups");

            int totalSamples = 0;
            for (Map.Entry<String, KeyframeAnimation> entry : anims.entrySet()) {
                KeyframeAnimation ka = entry.getValue();
                assertEquals(577, ka.sampleCount(), "Group " + entry.getKey() + " must have 577 samples");
                totalSamples += ka.sampleCount();
            }
            assertEquals(11540, totalSamples, "Total samples must be 20 * 577 = 11,540");
        }
    }

    @Test
    @DisplayName("ANIM-2: Mathematical Reconstruction Error across ALL 11,540 samples in transition_seal.anim")
    public void testMatrixReconstructionErrorAllSamples() throws Exception {
        // Parse raw file lines directly to obtain original 12 floats per sample
        List<String> lines = Files.readAllLines(ANIM_FILE);
        int verifiedSamples = 0;
        float maxError = 0.0f;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 14) continue;

            float time = Float.parseFloat(parts[1]);
            float[] m = new float[12];
            for (int i = 0; i < 12; i++) {
                m[i] = Float.parseFloat(parts[i + 2]);
            }

            KeyframeAnimation.Sample sample = new KeyframeAnimation.Sample(time, m);

            // Reconstruct Matrix4f using sample's translation and quaternion
            Matrix4f recon = new Matrix4f().translationRotate(sample.tx, sample.ty, sample.tz, sample.rotation);

            // Matrix in KeyframeAnimation.java is row-major 4x3:
            // Row 0: m00=m[0], m01=m[1], m02=m[2], tx=m[3]
            // Row 1: m10=m[4], m11=m[5], m12=m[6], ty=m[7]
            // Row 2: m20=m[8], m21=m[9], m22=m[10], tz=m[11]
            // In JOML, recon.m<col><row>():
            // recon.m00() = col 0, row 0 = m[0]
            // recon.m10() = col 1, row 0 = m[1]
            // recon.m20() = col 2, row 0 = m[2]
            // recon.m30() = col 3, row 0 = m[3]
            float[] reconArray = new float[]{
                recon.m00(), recon.m10(), recon.m20(), recon.m30(),
                recon.m01(), recon.m11(), recon.m21(), recon.m31(),
                recon.m02(), recon.m12(), recon.m22(), recon.m32()
            };

            for (int i = 0; i < 12; i++) {
                float err = Math.abs(reconArray[i] - m[i]);
                if (err > maxError) maxError = err;
            }

            // Also verify quaternion is unit length
            float qLenSq = sample.rotation.x * sample.rotation.x + sample.rotation.y * sample.rotation.y +
                           sample.rotation.z * sample.rotation.z + sample.rotation.w * sample.rotation.w;
            assertEquals(1.0f, qLenSq, 1e-4f, "Quaternion must be unit length for sample at time " + time);

            verifiedSamples++;
        }

        assertEquals(11540, verifiedSamples, "Must verify all 11,540 samples");
        // Max error across all 11,540 samples must be very small (< 1e-3)
        assertTrue(maxError < 1e-3f, "Max matrix reconstruction error across all 11,540 samples must be < 1e-3, was: " + maxError);
    }

    @Test
    @DisplayName("ANIM-3: Slerp Smoothness, Unit Length, and Orthogonality across 20,000 Dense Interpolations")
    public void testSlerpSmoothnessDenseInterpolation() throws Exception {
        Map<String, KeyframeAnimation> anims;
        try (InputStream in = new FileInputStream(ANIM_FILE.toFile())) {
            anims = KeyframeAnimation.parse(in);
        }

        Matrix4f dest = new Matrix4f();
        int denseSteps = 1000;

        for (Map.Entry<String, KeyframeAnimation> entry : anims.entrySet()) {
            KeyframeAnimation ka = entry.getValue();

            for (int i = 0; i <= denseSteps; i++) {
                float t = i / (float) denseSteps;
                ka.getInterpolatedMatrix(t, dest);

                // Verify translation and rotation values are finite
                Vector3f trans = dest.getTranslation(new Vector3f());
                assertFalse(Float.isNaN(trans.x) || Float.isInfinite(trans.x));
                assertFalse(Float.isNaN(trans.y) || Float.isInfinite(trans.y));
                assertFalse(Float.isNaN(trans.z) || Float.isInfinite(trans.z));

                // Extract rotation quaternion from interpolated matrix
                Quaternionf q = dest.getUnnormalizedRotation(new Quaternionf());
                float qLen = (float) Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
                assertEquals(1.0f, qLen, 1e-4f, "Interpolated rotation must remain unit quaternion at t=" + t);

                // Verify determinant is 1.0 (rigid rotation)
                float det = dest.determinant();
                assertEquals(1.0f, det, 1e-4f, "Interpolated matrix determinant must be 1.0 (pure rigid transform) at t=" + t);
            }
        }
    }
}
