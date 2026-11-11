package com.ntmdoors.client.render;

import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.client.MotionSensorOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

@Environment(EnvType.CLIENT)
public class MotionSensorBlockEntityRenderer implements BlockEntityRenderer<MotionSensorBlockEntity> {

    public MotionSensorBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public boolean rendersOutsideBoundingBox(MotionSensorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void render(MotionSensorBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!MotionSensorOverlay.isVisible(be.getPos())) return;

        BlockPos bePos = be.getPos();
        byte[] depths = be.getAreaDepths();

        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());

        matrices.push();
        for (int u = 0; u < MotionSensorBlockEntity.GRID_SIZE; u++) {
            for (int v = 0; v < MotionSensorBlockEntity.GRID_SIZE; v++) {
                int maxD = depths[u + v * MotionSensorBlockEntity.GRID_SIZE];
                if (maxD <= 0) continue;

                for (int d = 0; d < maxD; d++) {
                    BlockPos cell = be.getCellPos(u, v, d);
                    double relX = cell.getX() - bePos.getX();
                    double relY = cell.getY() - bePos.getY();
                    double relZ = cell.getZ() - bePos.getZ();

                    Box box = new Box(relX, relY, relZ, relX + 1.0, relY + 1.0, relZ + 1.0);
                    WorldRenderer.drawBox(matrices, lineConsumer, box, 0.35F, 0.70F, 1.0F, 0.80F);
                }
            }
        }
        matrices.pop();
    }
}
