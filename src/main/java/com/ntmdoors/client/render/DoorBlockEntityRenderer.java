package com.ntmdoors.client.render;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.PartAnim;
import com.ntmdoors.util.Facings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Modern Fabric BlockEntityRenderer for multiblock doors.
 * Integrates ObjModel, geometric ObjClipping, and KeyframeAnimation.
 */
@Environment(EnvType.CLIENT)
public final class DoorBlockEntityRenderer implements BlockEntityRenderer<DoorBlockEntity> {

    public DoorBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public boolean rendersOutsideBoundingBox(DoorBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }

    @Override
    public boolean isInRenderDistance(DoorBlockEntity be, Vec3d cameraPos) {
        DoorDecl decl = be.getDecl();
        double radius = decl != null ? Math.max(64.0, decl.renderRadius + 32.0) : 64.0;
        return cameraPos.isInRange(Vec3d.ofCenter(be.getPos()), radius);
    }

    @Override
    public void render(DoorBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        DoorDecl d = be.getDecl();
        if (d == null || d.model == null) return;

        ObjModel model = ObjModel.get(d.model);
        if (model == null) return;

        BlockState state = be.getCachedState();
        Direction facing = state.contains(BlockDoorGeneric.FACING)
                ? state.get(BlockDoorGeneric.FACING)
                : Direction.NORTH;

        float openFraction = be.getOpenFraction(tickDelta);
        boolean breached = be.isBreached();
        int skinIndex = be.getSkinIndex();

        matrices.push();

        // 1. Position at bottom-center of core cell
        matrices.translate(0.5, 0.0, 0.5);

        // 2. Rotate to horizontal facing + model authoring yaw
        float yaw = Facings.yaw(facing) + d.modelYaw;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));

        // 3. Apply model translation
        matrices.translate(d.modelX, d.modelY, d.modelZ);

        // MatrixStack is now precisely in Model Space
        drawParts(d, model, openFraction, skinIndex, breached, matrices, vertexConsumers, light, overlay);

        matrices.pop();
    }

    private void drawParts(DoorDecl d, ObjModel model, float openFraction, int skinIndex,
                           boolean breached, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ClipBounds clipBounds = (Double.isInfinite(d.clipMinX) && Double.isInfinite(d.clipMaxX)
                && Double.isInfinite(d.clipMinY) && Double.isInfinite(d.clipMaxY)
                && Double.isInfinite(d.clipMinZ) && Double.isInfinite(d.clipMaxZ))
                ? null
                : new ClipBounds(d.clipMinX, d.clipMaxX, d.clipMinY, d.clipMaxY, d.clipMinZ, d.clipMaxZ);

        Map<String, KeyframeAnimation> keyframes = d.keyframeAnim != null
                ? KeyframeAnimation.get(d.keyframeAnim)
                : Collections.emptyMap();

        for (String groupName : model.groups()) {
            if (!d.renderGroups.isEmpty() && !d.renderGroups.contains(groupName)) {
                continue;
            }

            ObjModel.MeshGroup group = model.getGroup(groupName);
            if (group == null || group.vertexCount() == 0) continue;

            List<PartAnim> steps = d.anims.get(groupName);
            KeyframeAnimation keyframe = keyframes.get(groupName);
            boolean isAnimated = (steps != null && !steps.isEmpty()) || keyframe != null;

            // Breached doors hide destroyed animated panels
            if (breached && isAnimated) continue;

            Identifier texture = d.textureForPart(skinIndex, groupName);
            VertexConsumer consumer = vertexConsumers.getBuffer(ObjRenderer.getEntityCutoutNoCull(texture));

            Matrix4f groupAnimTransform = null;
            Matrix3f groupAnimNormal = null;

            if (keyframe != null) {
                groupAnimTransform = new Matrix4f();
                keyframe.getInterpolatedMatrix(openFraction, groupAnimTransform);
                groupAnimNormal = new Matrix3f(groupAnimTransform).invert().transpose();
            } else if (steps != null && !steps.isEmpty()) {
                groupAnimTransform = new Matrix4f().identity();
                for (PartAnim a : steps) {
                    float p = a.progress(openFraction);
                    if (a.type == PartAnim.Type.TRANSLATE) {
                        groupAnimTransform.translate((float) (a.x * p), (float) (a.y * p), (float) (a.z * p));
                    } else if (a.type == PartAnim.Type.ROTATE) {
                        groupAnimTransform.translate((float) a.px, (float) a.py, (float) a.pz);
                        groupAnimTransform.rotate((float) Math.toRadians(a.angle * p), (float) a.x, (float) a.y, (float) a.z);
                        groupAnimTransform.translate((float) -a.px, (float) -a.py, (float) -a.pz);
                    }
                }
                groupAnimNormal = new Matrix3f(groupAnimTransform).invert().transpose();
            }

            boolean clipGroup = isAnimated || d.clipAllGroups;
            ClipBounds activeClip = clipGroup ? clipBounds : null;

            ObjRenderer.renderGroup(
                matrices,
                consumer,
                group,
                groupAnimTransform,
                groupAnimNormal,
                activeClip,
                light,
                overlay,
                1.0f, 1.0f, 1.0f, 1.0f
            );
        }
    }
}
