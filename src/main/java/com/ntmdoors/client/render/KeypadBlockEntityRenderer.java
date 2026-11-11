package com.ntmdoors.client.render;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.KeypadBlock;
import com.ntmdoors.block.entity.KeypadBlockEntity;
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
 * 3D BER for Keypad: renders keypad.obj groups, button press shifts, and upright world LCD text.
 */
@Environment(EnvType.CLIENT)
public final class KeypadBlockEntityRenderer implements BlockEntityRenderer<KeypadBlockEntity> {

    public static final Identifier MODEL_ID = Identifier.of(NTMDoors.MOD_ID, "models/doors/keypad.obj");
    public static final Identifier TEX_KEYPAD = Identifier.of(NTMDoors.MOD_ID, "textures/models/doors/keypad.png");
    public static final Identifier TEX_SUCCESS = Identifier.of(NTMDoors.MOD_ID, "textures/models/doors/keypad_success.png");

    private static final String[] BUTTON_GROUPS = {
            "K1", "K2", "K3", "K4", "K5", "K6", "K7", "K8", "K9", "KReset", "K0", "KReturn"
    };

    private static final String[] GLYPHS = {
            "9", "8", "7", "6", "5", "4", "3", "2", "1", "R", "0", "E"
    };

    private final TextRenderer textRenderer;

    public KeypadBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
    }

    @Override
    public void render(KeypadBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ObjModel model = ObjModel.get(MODEL_ID);
        if (model == null) return;

        BlockState state = be.getCachedState();
        Direction facing = state.contains(KeypadBlock.FACING) ? state.get(KeypadBlock.FACING) : Direction.NORTH;

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Facings.yaw(facing)));

        VertexConsumer keypadConsumer = vertexConsumers.getBuffer(ObjRenderer.getEntityCutoutNoCull(TEX_KEYPAD));
        VertexConsumer successConsumer = vertexConsumers.getBuffer(ObjRenderer.getEntityCutoutNoCull(TEX_SUCCESS));

        // Render Base Plate & Display Housing
        ObjRenderer.renderGroup(matrices, keypadConsumer, model, "Keypad", light, overlay, null);
        ObjRenderer.renderGroup(matrices, keypadConsumer, model, "Display", light, overlay, null);

        // Render 12 Buttons
        for (int i = 0; i < BUTTON_GROUPS.length; i++) {
            boolean pressed = be.getCooldown(i) > 0;
            matrices.push();
            if (pressed) {
                matrices.translate(0, 0, 0.05);
            }
            VertexConsumer consumer = pressed ? successConsumer : keypadConsumer;
            ObjRenderer.renderGroup(matrices, consumer, model, BUTTON_GROUPS[i], light, overlay, null);
            matrices.pop();
        }

        // Render In-World Button Glyphs
        matrices.push();
        matrices.translate(0, KeypadBlock.MODEL_OFFSET_Y, KeypadBlock.MODEL_OFFSET_Z);
        matrices.scale((float) KeypadBlock.MODEL_SCALE_X, (float) KeypadBlock.MODEL_SCALE_Y, (float) KeypadBlock.MODEL_SCALE_Z);

        for (int i = 0; i < KeypadBlockEntity.BUTTON_LAYOUT.length; i++) {
            KeypadBlockEntity.Button btn = KeypadBlockEntity.BUTTON_LAYOUT[i];
            boolean pressed = be.getCooldown(i) > 0;

            matrices.push();
            matrices.translate(btn.x, btn.y - 0.013671875F, 0.02F + (pressed ? 0.05F : 0F));
            matrices.scale(0.01F, -0.01F, 0.01F);
            String glyph = GLYPHS[i];
            int glyphWidth = textRenderer.getWidth(glyph);
            textRenderer.draw(glyph, -glyphWidth / 2.0F, -4.0F, 0xFF202020, false, matrices.peek().getPositionMatrix(),
                    vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            matrices.pop();
        }

        // Render LCD Display Screen Text
        matrices.push();
        matrices.translate(0, 0.84375, 0.04F);
        matrices.scale(0.0065F, -0.0065F, 0.0065F);

        String line1 = "";
        String line2 = "";
        int color1 = 0xFFFFFFFF;
        int color2 = 0xFFFFEE00;

        KeypadBlockEntity.Feedback fb = be.getFeedback();
        if (fb != KeypadBlockEntity.Feedback.NONE) {
            switch (fb) {
                case CODE_SET -> { line1 = "Code Set!"; color1 = 0xFFFFFF00; }
                case CODE_SYNCED -> { line1 = "Code synced"; color1 = 0xFFB04CFF; }
                case RESET_DONE -> { line1 = "Code reset!"; color1 = 0xFF15FF00; }
                case SUCCESS -> { line1 = "Access"; line2 = "Granted"; color1 = 0xFF15FF00; color2 = 0xFF15FF00; }
                case DENIED -> { line1 = "Access"; line2 = "Denied"; color1 = 0xFFFF0800; color2 = 0xFFFF0800; }
                case UNABLE -> { line1 = "Unable to bound"; color1 = 0xFFFF0800; }
                case DISABLED -> { line1 = "Keypad"; line2 = "Disabled"; color1 = 0xFFFF0800; color2 = 0xFFFF0800; }
            }
        } else {
            switch (be.getMode()) {
                case RESET_CONFIRM -> { line1 = "Reset code?"; color1 = 0xFF3FA9F5; }
                case RESET_ENTER -> { line1 = "Input current"; line2 = be.getEntry(); color1 = 0xFFFFEE00; color2 = 0xFFFFFFFF; }
                case SET_CODE -> {
                    line1 = be.getBindMode() == KeypadBlockEntity.BindMode.MULTI ? "Input multi code" : "Input code";
                    line2 = be.getEntry();
                }
                case ENTER_CODE -> {
                    line1 = "Enter code";
                    line2 = be.getEntry();
                }
            }
        }

        if (!line1.isEmpty()) {
            int w1 = textRenderer.getWidth(line1);
            textRenderer.draw(line1, -w1 / 2.0F, -6.0F, color1, false, matrices.peek().getPositionMatrix(),
                    vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
        if (!line2.isEmpty()) {
            int w2 = textRenderer.getWidth(line2);
            textRenderer.draw(line2, -w2 / 2.0F, 4.0F, color2, false, matrices.peek().getPositionMatrix(),
                    vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }

        matrices.pop(); // LCD
        matrices.pop(); // Plate
        matrices.pop(); // Model base
    }
}
