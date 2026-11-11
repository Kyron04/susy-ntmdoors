package com.ntmdoors.client.render;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.util.Facings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * 3D BER for Keycard Scanner: renders keycard_scanner.obj, emissive SecurityLine, additive 3-color LEDs, and recessed LCD text.
 */
@Environment(EnvType.CLIENT)
public final class KeycardScannerBlockEntityRenderer implements BlockEntityRenderer<KeycardScannerBlockEntity> {

    public static final Identifier MODEL_ID = Identifier.of(NTMDoors.MOD_ID, "models/doors/keycard_scanner.obj");
    public static final Identifier TEX_SCANNER = Identifier.of(NTMDoors.MOD_ID, "textures/models/doors/keycard_scanner.png");

    private static final String[] BASE_GROUPS = { "Base", "Trim", "Screen", "Slot", "Vents" };
    private static final float TEXT_SCALE = 0.0042F;
    private static final float TEXT_MAX_WIDTH = 0.36F;

    private final TextRenderer textRenderer;

    public KeycardScannerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(KeycardScannerBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ObjModel model = ObjModel.get(MODEL_ID);
        if (model == null) return;

        BlockState state = be.getCachedState();
        Direction facing = state.contains(KeycardScannerBlock.FACING) ? state.get(KeycardScannerBlock.FACING) : Direction.NORTH;

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Facings.yaw(facing)));

        VertexConsumer consumer = vertexConsumers.getBuffer(ObjRenderer.getEntityCutoutNoCull(TEX_SCANNER));

        // Render Base Model Groups
        for (String g : BASE_GROUPS) {
            ObjRenderer.renderGroup(matrices, consumer, model, g, light, overlay, null);
        }

        // Render Fullbright Emissive Security Line
        ObjRenderer.renderGroup(matrices, consumer, model, "SecurityLine", LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, null);

        // Render 3-Color LEDs
        KeycardScannerBlockEntity.State scannerState = be.getState();
        if (scannerState == KeycardScannerBlockEntity.State.GRANTED) {
            ObjRenderer.renderGroup(matrices, consumer, model, "LightGreen", LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, 0.55F, 1.0F, 0.55F, 1.0F, null);
        } else if (scannerState == KeycardScannerBlockEntity.State.DENIED || be.isLockedOut()) {
            ObjRenderer.renderGroup(matrices, consumer, model, "LightRed", LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, 1.0F, 0.45F, 0.45F, 1.0F, null);
        } else if (scannerState == KeycardScannerBlockEntity.State.BINDING || scannerState == KeycardScannerBlockEntity.State.SCANNING) {
            ObjRenderer.renderGroup(matrices, consumer, model, "LightYellow", LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, 1.0F, 0.9F, 0.35F, 1.0F, null);
        }

        // Render Recessed LCD Screen Text
        matrices.push();
        matrices.translate(-0.09F, 0.74F, 0.284F);
        matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        String line1;
        String line2 = "";
        int color1 = 0xFF9FD8FF;
        int color2 = 0xFFFFFFFF;

        if (be.isLockedOut()) {
            line1 = "Locked";
            color1 = 0xFFFF5555;
        } else {
            switch (scannerState) {
                case BINDING -> {
                    line1 = "Binding mode";
                    line2 = "Bound doors " + be.getVisibleCount();
                    color1 = 0xFFFFD24A;
                }
                case SCANNING -> {
                    line1 = "Scanning";
                    color1 = 0xFFFFFFFF;
                }
                case GRANTED -> {
                    line1 = "Access granted!";
                    color1 = 0xFF55FF55;
                }
                case DENIED -> {
                    line1 = "Access denied";
                    color1 = 0xFFFF5555;
                }
                case SYNCED -> {
                    line1 = "Synced";
                    color1 = 0xFF9FFFB0;
                }
                default -> {
                    line1 = "Please insert key";
                    color1 = 0xFF9FD8FF;
                }
            }
        }

        // Auto-scale if text exceeds screen width limit
        int w1 = textRenderer.getWidth(line1);
        if (w1 * TEXT_SCALE > TEXT_MAX_WIDTH) {
            float scaleAdjust = TEXT_MAX_WIDTH / (w1 * TEXT_SCALE);
            matrices.scale(scaleAdjust, scaleAdjust, 1.0F);
        }

        textRenderer.draw(line1, 0, 0, color1, false, matrices.peek().getPositionMatrix(),
                vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

        if (!line2.isEmpty()) {
            textRenderer.draw(line2, 0, 10, color2, false, matrices.peek().getPositionMatrix(),
                    vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }

        matrices.pop(); // LCD
        matrices.pop(); // Base
    }
}
