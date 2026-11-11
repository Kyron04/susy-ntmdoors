package com.ntmdoors.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Mesh rendering emitter that streams triangulated vertices into VertexConsumer
 * using RenderLayer.getEntityCutoutNoCull to ensure single-sided door geometry
 * remains visible without backface culling artifacts.
 */
@Environment(EnvType.CLIENT)
public final class ObjRenderer {

    private ObjRenderer() {}

    /**
     * Gets the standard RenderLayer for door OBJ models, which disables backface culling.
     */
    public static RenderLayer getEntityCutoutNoCull(Identifier texture) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    /**
     * Renders an entire ObjModel with default white tint.
     */
    public static void render(MatrixStack matrices, VertexConsumer consumer, ObjModel model,
                              int light, int overlay, ClipBounds clipBounds) {
        render(matrices, consumer, model, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f, clipBounds);
    }

    /**
     * Renders an entire ObjModel with RGBA tint.
     */
    public static void render(MatrixStack matrices, VertexConsumer consumer, ObjModel model,
                              int light, int overlay, float r, float g, float b, float a,
                              ClipBounds clipBounds) {
        if (model == null) return;
        model.render(matrices, consumer, light, overlay, r, g, b, a, clipBounds);
    }

    /**
     * Renders a specific group from an ObjModel.
     */
    public static void renderGroup(MatrixStack matrices, VertexConsumer consumer, ObjModel model,
                                   String groupName, int light, int overlay, ClipBounds clipBounds) {
        renderGroup(matrices, consumer, model, groupName, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f, clipBounds);
    }

    /**
     * Renders a specific group from an ObjModel with RGBA tint.
     */
    public static void renderGroup(MatrixStack matrices, VertexConsumer consumer, ObjModel model,
                                   String groupName, int light, int overlay,
                                   float r, float g, float b, float a, ClipBounds clipBounds) {
        if (model == null) return;
        model.renderGroup(matrices, consumer, groupName, light, overlay, r, g, b, a, clipBounds);
    }

    /**
     * Renders a single MeshGroup with an optional group animation transform and clipping.
     * When clipping is active, matrices.peek() MUST be in Model Space!
     */
    public static void renderGroup(
        MatrixStack matrices,
        VertexConsumer consumer,
        ObjModel.MeshGroup group,
        Matrix4f groupAnimTransform,
        Matrix3f groupAnimNormal,
        ClipBounds clipBounds,
        int light, int overlay,
        float r, float g, float b, float a
    ) {
        if (group == null || group.vertexCount() == 0) return;

        boolean needsClip = clipBounds != null && !clipBounds.isInfinite();

        if (!needsClip) {
            // Fast path: push group transform onto MatrixStack
            matrices.push();
            if (groupAnimTransform != null) {
                matrices.peek().getPositionMatrix().mul(groupAnimTransform);
                if (groupAnimNormal != null) {
                    matrices.peek().getNormalMatrix().mul(groupAnimNormal);
                }
            }

            Matrix4f posMat = matrices.peek().getPositionMatrix();
            Matrix3f normMat = matrices.peek().getNormalMatrix();
            float[] d = group.vertices();
            int len = d.length;

            for (int i = 0; i < len; i += 8) {
                consumer.vertex(posMat, d[i], d[i + 1], d[i + 2])
                        .color(r, g, b, a)
                        .texture(d[i + 3], d[i + 4])
                        .overlay(overlay)
                        .light(light)
                        .normal(normMat, d[i + 5], d[i + 6], d[i + 7])
                        .next();
            }

            matrices.pop();
        } else {
            // Clipped path: matrices.peek() is in Model Space
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Matrix3f modelNormal = matrices.peek().getNormalMatrix();
            float[] d = group.vertices();
            int len = d.length;

            for (int i = 0; i < len; i += 24) {
                ObjClipping.clipAndEmitTriangle(
                    modelMatrix, modelNormal,
                    groupAnimTransform, groupAnimNormal,
                    consumer, d, i, i + 8, i + 16,
                    clipBounds, light, overlay, r, g, b, a
                );
            }
        }
    }

    /**
     * Renders an entire model using a VertexConsumerProvider and the cutout-no-cull layer.
     */
    public static void renderModel(MatrixStack matrices, VertexConsumerProvider consumers,
                                   ObjModel model, Identifier texture, int light, int overlay,
                                   ClipBounds clipBounds) {
        if (model == null || texture == null) return;
        VertexConsumer consumer = consumers.getBuffer(getEntityCutoutNoCull(texture));
        render(matrices, consumer, model, light, overlay, clipBounds);
    }
}
