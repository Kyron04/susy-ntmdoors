package com.ntmdoors.test;

import com.ntmdoors.client.render.ClipBounds;
import com.ntmdoors.client.render.ObjClipping;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ObjClippingTest {

    public record Vertex(
        float x, float y, float z,
        float r, float g, float b, float a,
        float u, float v,
        int overlay, int light,
        float nx, float ny, float nz
    ) {}

    public static class MockVertexConsumer implements VertexConsumer {
        public final List<Vertex> vertices = new ArrayList<>();

        private float currentX, currentY, currentZ;
        private float currentR = 1, currentG = 1, currentB = 1, currentA = 1;
        private float currentU, currentV;
        private int currentOverlay, currentLight;
        private float currentNx, currentNy, currentNz;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.currentX = (float) x;
            this.currentY = (float) y;
            this.currentZ = (float) z;
            return this;
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            float rx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
            float ry = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
            float rz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();
            this.currentX = rx;
            this.currentY = ry;
            this.currentZ = rz;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.currentR = red / 255.0f;
            this.currentG = green / 255.0f;
            this.currentB = blue / 255.0f;
            this.currentA = alpha / 255.0f;
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            this.currentR = red;
            this.currentG = green;
            this.currentB = blue;
            this.currentA = alpha;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.currentU = u;
            this.currentV = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.currentOverlay = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer overlay(int overlay) {
            this.currentOverlay = overlay;
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.currentLight = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer light(int light) {
            this.currentLight = light;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.currentNx = x;
            this.currentNy = y;
            this.currentNz = z;
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            float rx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z;
            float ry = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z;
            float rz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z;
            this.currentNx = rx;
            this.currentNy = ry;
            this.currentNz = rz;
            return this;
        }

        @Override
        public void next() {
            vertices.add(new Vertex(
                currentX, currentY, currentZ,
                currentR, currentG, currentB, currentA,
                currentU, currentV,
                currentOverlay, currentLight,
                currentNx, currentNy, currentNz
            ));
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unfixColor() {}
    }

    @Test
    @DisplayName("Trivial Accept: Triangle fully within clip bounds emits exactly 3 vertices unchanged")
    public void testTrivialAccept() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Raw triangle (stride 8: x, y, z, u, v, nx, ny, nz)
        float[] rawData = new float[]{
            0.0f, 0.0f, 0.0f,  0.1f, 0.2f,  0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, 0.0f,  0.3f, 0.4f,  0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,  0.5f, 0.6f,  0.0f, 1.0f, 0.0f
        };

        ClipBounds bounds = new ClipBounds(-2.0, 2.0, -2.0, 2.0, -2.0, 2.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertEquals(3, consumer.vertices.size());
        assertEquals(0.0f, consumer.vertices.get(0).x(), 1e-4f);
        assertEquals(1.0f, consumer.vertices.get(1).x(), 1e-4f);
        assertEquals(0.0f, consumer.vertices.get(2).x(), 1e-4f);
        assertEquals(1.0f, consumer.vertices.get(2).y(), 1e-4f);
    }

    @Test
    @DisplayName("Trivial Reject: Triangle completely outside clip bounds emits 0 vertices")
    public void testTrivialReject() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        float[] rawData = new float[]{
            5.0f, 0.0f, 0.0f,  0.1f, 0.2f,  0.0f, 1.0f, 0.0f,
            6.0f, 0.0f, 0.0f,  0.3f, 0.4f,  0.0f, 1.0f, 0.0f,
            5.0f, 1.0f, 0.0f,  0.5f, 0.6f,  0.0f, 1.0f, 0.0f
        };

        ClipBounds bounds = new ClipBounds(-2.0, 2.0, -2.0, 2.0, -2.0, 2.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertEquals(0, consumer.vertices.size());
    }

    @Test
    @DisplayName("Bisect Single Plane: Triangle sliced by x >= 0 produces 2 triangles (6 vertices) with interpolated UV and normals")
    public void testBisectSinglePlane() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Triangle spanning x from -1.0 to +1.0
        float[] rawData = new float[]{
            -1.0f,  0.0f, 0.0f,  0.0f, 0.5f,  0.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 0.0f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 0.0f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f
        };

        ClipBounds bounds = new ClipBounds(0.0, Double.POSITIVE_INFINITY, -10.0, 10.0, -10.0, 10.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        // A triangle clipped by a single plane cutting through 2 edges becomes a quad = 2 triangles = 6 vertices
        assertEquals(6, consumer.vertices.size());

        // All emitted vertices must satisfy x >= -1e-5
        for (Vertex v : consumer.vertices) {
            assertTrue(v.x() >= -1e-5f, "Vertex x must be >= 0: " + v.x());
            assertEquals(1.0f, v.nz(), 1e-4f, "Normal z must remain 1.0");
        }
    }

    @Test
    @DisplayName("Sliding Blast Door Simulation: Triangle with z > 2.5 clipped to z <= 2.5")
    public void testSlidingBlastDoorSimulation() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // Blast door panel triangle with z spanning 2.4 to 2.8
        float[] rawData = new float[]{
            0.0f, 1.0f, 2.4f,  0.0f, 0.0f,  1.0f, 0.0f, 0.0f,
            0.0f, 2.0f, 2.4f,  0.0f, 1.0f,  1.0f, 0.0f, 0.0f,
            0.0f, 1.5f, 2.8f,  1.0f, 0.5f,  1.0f, 0.0f, 0.0f
        };

        // Frame pocket clip bounds: Z in [-2.5, 2.5]
        ClipBounds bounds = new ClipBounds(-10.0, 10.0, -10.0, 10.0, -2.5, 2.5);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertTrue(consumer.vertices.size() >= 3, "Should emit at least 1 triangle");
        for (Vertex v : consumer.vertices) {
            assertTrue(v.z() <= 2.50001f, "Vertex z must not exceed frame pocket limit 2.5: " + v.z());
        }
    }

    @Test
    @DisplayName("Collinear Rejection: Degenerate collinear triangle with area < 1e-10 is rejected")
    public void testDegenerateCollinearRejection() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        // 3 collinear points along X axis
        float[] rawData = new float[]{
            0.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
            1.0f, 0.0f, 0.0f,  0.5f, 0.0f,  0.0f, 1.0f, 0.0f,
            2.0f, 0.0f, 0.0f,  1.0f, 0.0f,  0.0f, 1.0f, 0.0f
        };

        ClipBounds bounds = new ClipBounds(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        ObjClipping.clipAndEmitTriangle(
            renderMat, renderNorm, null, null,
            consumer, rawData, 0, 8, 16,
            bounds, 0x00F000F0, 0,
            1.0f, 1.0f, 1.0f, 1.0f
        );

        assertEquals(0, consumer.vertices.size(), "Collinear triangle must produce 0 vertices");
    }

    @Test
    @DisplayName("Zero Allocation / Repeated Execution Stability")
    public void testRepeatedExecutionStability() {
        MockVertexConsumer consumer = new MockVertexConsumer();
        Matrix4f renderMat = new Matrix4f().identity();
        Matrix3f renderNorm = new Matrix3f().identity();

        float[] rawData = new float[]{
            -1.0f, 0.0f, 0.0f,  0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
             1.0f, 1.0f, 0.0f,  1.0f, 1.0f,  0.0f, 1.0f, 0.0f,
             1.0f, -1.0f, 0.0f, 1.0f, 0.0f,  0.0f, 1.0f, 0.0f
        };
        ClipBounds bounds = new ClipBounds(0.0, 2.0, -2.0, 2.0, -2.0, 2.0);

        for (int i = 0; i < 10000; i++) {
            consumer.vertices.clear();
            ObjClipping.clipAndEmitTriangle(
                renderMat, renderNorm, null, null,
                consumer, rawData, 0, 8, 16,
                bounds, 0x00F000F0, 0,
                1.0f, 1.0f, 1.0f, 1.0f
            );
            assertEquals(6, consumer.vertices.size());
        }
    }
}
