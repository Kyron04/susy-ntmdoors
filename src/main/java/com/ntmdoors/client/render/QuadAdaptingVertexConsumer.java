package com.ntmdoors.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Adapts streaming triangular primitives (v0, v1, v2) into Minecraft's native
 * VertexFormat.DrawMode.QUADS buffer by repeating the 3rd vertex (v0, v1, v2, v2).
 *
 * <p>In OpenGL / Minecraft QUADS index buffering (0, 1, 2, 0, 2, 3):
 * <ul>
 *   <li>Triangle 1 receives (v0, v1, v2) -> exact geometry rendered with original winding</li>
 *   <li>Triangle 2 receives (v0, v2, v2) -> degenerate zero-area triangle culled by GPU rasterizer</li>
 * </ul>
 * This prevents the catastrophic 3-vs-4 vertex-offset misalignment that shatters OBJ geometry
 * across quads.</p>
 */
@Environment(EnvType.CLIENT)
public final class QuadAdaptingVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;

    private int triIndex = 0;

    // Last vertex attributes cached for 4th vertex duplication
    private Matrix4f lastPosMat;
    private float lastX, lastY, lastZ;
    private boolean useDoublePos;
    private double lastDx, lastDy, lastDz;

    private int lastColorMode; // 0=none, 1=rgbaInt, 2=rgbaFloat
    private int lastRi, lastGi, lastBi, lastAi;
    private float lastRf, lastGf, lastBf, lastAf;

    private float lastU, lastV;

    private int lastOverlay;
    private int lastLight;

    private Matrix3f lastNormMat;
    private float lastNx, lastNy, lastNz;

    public QuadAdaptingVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    public static VertexConsumer wrap(VertexConsumer consumer) {
        if (consumer instanceof QuadAdaptingVertexConsumer) {
            return consumer;
        }
        return new QuadAdaptingVertexConsumer(consumer);
    }

    public VertexConsumer getDelegate() {
        return delegate;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.useDoublePos = true;
        this.lastPosMat = null;
        this.lastDx = x;
        this.lastDy = y;
        this.lastDz = z;
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
        this.useDoublePos = false;
        this.lastPosMat = matrix;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        delegate.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.lastColorMode = 1;
        this.lastRi = red;
        this.lastGi = green;
        this.lastBi = blue;
        this.lastAi = alpha;
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        this.lastColorMode = 2;
        this.lastRf = red;
        this.lastGf = green;
        this.lastBf = blue;
        this.lastAf = alpha;
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        this.lastU = u;
        this.lastV = v;
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        this.lastOverlay = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int overlay) {
        this.lastOverlay = overlay;
        delegate.overlay(overlay);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        this.lastLight = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int light) {
        this.lastLight = light;
        delegate.light(light);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.lastNormMat = null;
        this.lastNx = x;
        this.lastNy = y;
        this.lastNz = z;
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
        this.lastNormMat = matrix;
        this.lastNx = x;
        this.lastNy = y;
        this.lastNz = z;
        delegate.normal(matrix, x, y, z);
        return this;
    }

    @Override
    public void next() {
        delegate.next();
        triIndex++;
        if (triIndex == 3) {
            // Complete 4-vertex quad by repeating 3rd vertex
            if (useDoublePos) {
                delegate.vertex(lastDx, lastDy, lastDz);
            } else if (lastPosMat != null) {
                delegate.vertex(lastPosMat, lastX, lastY, lastZ);
            } else {
                delegate.vertex(lastX, lastY, lastZ);
            }

            if (lastColorMode == 2) {
                delegate.color(lastRf, lastGf, lastBf, lastAf);
            } else if (lastColorMode == 1) {
                delegate.color(lastRi, lastGi, lastBi, lastAi);
            }

            delegate.texture(lastU, lastV);
            delegate.overlay(lastOverlay);
            delegate.light(lastLight);

            if (lastNormMat != null) {
                delegate.normal(lastNormMat, lastNx, lastNy, lastNz);
            } else {
                delegate.normal(lastNx, lastNy, lastNz);
            }

            delegate.next();
            triIndex = 0;
        }
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        delegate.fixedColor(red, green, blue, alpha);
    }

    @Override
    public void unfixColor() {
        delegate.unfixColor();
    }
}
