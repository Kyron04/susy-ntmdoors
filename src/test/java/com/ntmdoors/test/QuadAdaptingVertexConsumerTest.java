package com.ntmdoors.test;

import com.ntmdoors.client.render.QuadAdaptingVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QuadAdaptingVertexConsumerTest {

    public record RecordedVertex(
            float x, float y, float z,
            float r, float g, float b, float a,
            float u, float v,
            int overlay, int light,
            float nx, float ny, float nz
    ) {}

    public static class RecordingConsumer implements VertexConsumer {
        public final List<RecordedVertex> vertices = new ArrayList<>();
        private float cx, cy, cz;
        private float cr = 1, cg = 1, cb = 1, ca = 1;
        private float cu, cv;
        private int cov, clgt;
        private float cnx, cny, cnz;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.cx = (float) x;
            this.cy = (float) y;
            this.cz = (float) z;
            return this;
        }

        @Override
        public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
            this.cx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
            this.cy = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
            this.cz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.cr = red / 255.0f;
            this.cg = green / 255.0f;
            this.cb = blue / 255.0f;
            this.ca = alpha / 255.0f;
            return this;
        }

        @Override
        public VertexConsumer color(float red, float green, float blue, float alpha) {
            this.cr = red;
            this.cg = green;
            this.cb = blue;
            this.ca = alpha;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.cu = u;
            this.cv = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.cov = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer overlay(int overlay) {
            this.cov = overlay;
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.clgt = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer light(int light) {
            this.clgt = light;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.cnx = x;
            this.cny = y;
            this.cnz = z;
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
            this.cnx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z;
            this.cny = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z;
            this.cnz = matrix.m02() * x + matrix.m12() * y + matrix.m22() * z;
            return this;
        }

        @Override
        public void next() {
            vertices.add(new RecordedVertex(cx, cy, cz, cr, cg, cb, ca, cu, cv, cov, clgt, cnx, cny, cnz));
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unfixColor() {}
    }

    @Test
    @DisplayName("Single Triangle Adapted to Quad: 3 input vertices produce exactly 4 output vertices with v3 == v2")
    public void testSingleTriangleQuadAdaptation() {
        RecordingConsumer recorder = new RecordingConsumer();
        VertexConsumer consumer = QuadAdaptingVertexConsumer.wrap(recorder);

        // Emit Triangle 1: (A, B, C)
        consumer.vertex(1.0, 2.0, 3.0).color(1.0f, 0.0f, 0.0f, 1.0f).texture(0.1f, 0.2f).overlay(0).light(15).normal(0f, 1f, 0f).next();
        consumer.vertex(4.0, 5.0, 6.0).color(0.0f, 1.0f, 0.0f, 1.0f).texture(0.3f, 0.4f).overlay(0).light(15).normal(0f, 1f, 0f).next();
        consumer.vertex(7.0, 8.0, 9.0).color(0.0f, 0.0f, 1.0f, 1.0f).texture(0.5f, 0.6f).overlay(0).light(15).normal(0f, 1f, 0f).next();

        assertEquals(4, recorder.vertices.size(), "Single triangle must yield exactly 4 vertices for QUADS draw mode");

        // Vertex 0 == A
        assertEquals(1.0f, recorder.vertices.get(0).x());
        assertEquals(2.0f, recorder.vertices.get(0).y());
        assertEquals(3.0f, recorder.vertices.get(0).z());

        // Vertex 1 == B
        assertEquals(4.0f, recorder.vertices.get(1).x());
        assertEquals(5.0f, recorder.vertices.get(1).y());
        assertEquals(6.0f, recorder.vertices.get(1).z());

        // Vertex 2 == C
        assertEquals(7.0f, recorder.vertices.get(2).x());
        assertEquals(8.0f, recorder.vertices.get(2).y());
        assertEquals(9.0f, recorder.vertices.get(2).z());

        // Vertex 3 must be an exact duplicate of Vertex 2
        assertEquals(recorder.vertices.get(2).x(), recorder.vertices.get(3).x());
        assertEquals(recorder.vertices.get(2).y(), recorder.vertices.get(3).y());
        assertEquals(recorder.vertices.get(2).z(), recorder.vertices.get(3).z());
        assertEquals(recorder.vertices.get(2).u(), recorder.vertices.get(3).u());
        assertEquals(recorder.vertices.get(2).v(), recorder.vertices.get(3).v());
        assertEquals(recorder.vertices.get(2).r(), recorder.vertices.get(3).r());
        assertEquals(recorder.vertices.get(2).g(), recorder.vertices.get(3).g());
        assertEquals(recorder.vertices.get(2).b(), recorder.vertices.get(3).b());
    }

    @Test
    @DisplayName("Multiple Triangles Stream: 100 triangles yield exactly 400 vertices (multiple of 4, no misalignment)")
    public void testMultipleTrianglesAlignment() {
        RecordingConsumer recorder = new RecordingConsumer();
        VertexConsumer consumer = QuadAdaptingVertexConsumer.wrap(recorder);
        Matrix4f posMat = new Matrix4f().identity();
        Matrix3f normMat = new Matrix3f().identity();

        for (int i = 0; i < 100; i++) {
            consumer.vertex(posMat, i, 0, 0).color(1f, 1f, 1f, 1f).texture(0f, 0f).overlay(0).light(0).normal(normMat, 0f, 1f, 0f).next();
            consumer.vertex(posMat, i, 1, 0).color(1f, 1f, 1f, 1f).texture(0f, 1f).overlay(0).light(0).normal(normMat, 0f, 1f, 0f).next();
            consumer.vertex(posMat, i + 1, 1, 0).color(1f, 1f, 1f, 1f).texture(1f, 1f).overlay(0).light(0).normal(normMat, 0f, 1f, 0f).next();
        }

        assertEquals(400, recorder.vertices.size(), "100 triangles must emit exactly 400 vertices (100 quads)");
        assertEquals(0, recorder.vertices.size() % 4, "Buffer length must be divisible by 4 for QUADS buffer");
    }
}
