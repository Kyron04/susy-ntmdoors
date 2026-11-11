package com.ntmdoors.client.render;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.KeycardProgrammerBlock;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * 3D BER for Keycard Programmer: renders keycard_programmer.obj, emissive groups, sloped screen LCD text, and bold front label.
 */
@Environment(EnvType.CLIENT)
public final class KeycardProgrammerBlockEntityRenderer implements BlockEntityRenderer<KeycardProgrammerBlockEntity> {

    public static final Identifier MODEL_ID = Identifier.of(NTMDoors.MOD_ID, "models/doors/keycard_programmer.obj");
    public static final Identifier TEX_PROGRAMMER = Identifier.of(NTMDoors.MOD_ID, "textures/models/doors/keycard_programmer.png");

    private static final String[] LIT_GROUPS = { "Skirt", "Body", "Screen", "Trim", "Slit", "Buttons", "Vents", "HandPanel" };
    private static final String[] EMISSIVE_GROUPS = { "Light", "HandLight" };

    // Slope tilt angle: atan(0.20 / 0.62) ≈ 17.877°
    private static final float SCREEN_TILT = 17.877F;

    private final TextRenderer textRenderer;

    public KeycardProgrammerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(KeycardProgrammerBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ObjModel model = ObjModel.get(MODEL_ID);
        if (model == null) return;

        BlockState state = be.getCachedState();
        Direction facing = state.contains(KeycardProgrammerBlock.FACING) ? state.get(KeycardProgrammerBlock.FACING) : Direction.NORTH;

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Facings.yaw(facing)));

        VertexConsumer consumer = vertexConsumers.getBuffer(ObjRenderer.getEntityCutoutNoCull(TEX_PROGRAMMER));

        // Render Normal Geometry
        for (String g : LIT_GROUPS) {
            ObjRenderer.renderGroup(matrices, consumer, model, g, light, overlay, null);
        }

        // Render Fullbright Emissive Groups
        for (String g : EMISSIVE_GROUPS) {
            ObjRenderer.renderGroup(matrices, consumer, model, g, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, null);
        }

        // Render Sloped Screen LCD Text
        matrices.push();
        matrices.translate(-0.17F, 0.354F, -0.08F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F - SCREEN_TILT));
        matrices.scale(-0.0028F, -0.0028F, 0.0028F);

        String title = "Keycard Programmer N4";
        String subtitle = "Insert keycard";
        textRenderer.draw(title, -textRenderer.getWidth(title) / 2.0F, -12.0F, 0xFF9EE6F0, false,
                matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        textRenderer.draw(subtitle, -textRenderer.getWidth(subtitle) / 2.0F, 1.0F, 0xFFAFCBD4, false,
                matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();

        // Render Front Slit Bold Label: "Insert here"
        matrices.push();
        matrices.translate(0.25F, 0.192F, -0.3238F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.scale(0.002F, -0.002F, 0.002F);

        String frontText = "Insert here";
        int fx = -textRenderer.getWidth(frontText) / 2;
        // Bold effect: draw twice with 1 pixel offset
        textRenderer.draw(frontText, fx, 0, 0xFF2A2B2E, false, matrices.peek().getPositionMatrix(),
                vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        textRenderer.draw(frontText, fx + 1, 0, 0xFF2A2B2E, false, matrices.peek().getPositionMatrix(),
                vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        matrices.pop();

        matrices.pop();
    }
}
