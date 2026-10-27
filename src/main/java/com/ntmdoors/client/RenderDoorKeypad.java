package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the keypad model, pressed keys, and the LCD prompts. The transform mirrors the
 * plate() helper in BlockDoorKeypad: the model face points the same way as the block's FACING.
 */
@SideOnly(Side.CLIENT)
public class RenderDoorKeypad extends TileEntitySpecialRenderer<TileEntityDoorKeypad> {

    private static final ResourceLocation TEX_KEYPAD = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keypad.png");
    private static final ResourceLocation TEX_SUCCESS = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keypad_success.png");
    private static final ResourceLocation TEX_ERROR = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keypad_error.png");
    private static final ResourceLocation MODEL = new ResourceLocation(NTMDoors.MODID, "models/doors/keypad.obj");

    // 9 numbers in a 3x3 grid above the function row (R, 0, E) at the bottom.
    private static final String[] BUTTON_LABELS = {
            "9", "8", "7",
            "6", "5", "4",
            "3", "2", "1",
            "R", "0", "E"
    };

    @Override
    public void render(TileEntityDoorKeypad te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockDoorKeypad)) return;
        EnumFacing facing = state.getValue(BlockDoorKeypad.FACING);

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(Facings.yaw(facing), 0F, 1F, 0F);
        // Same offset/scale the block uses for its plate box and hit test.
        GlStateManager.translate(0, BlockDoorKeypad.MODEL_OFFSET_Y, BlockDoorKeypad.MODEL_OFFSET_Z);
        GlStateManager.scale(BlockDoorKeypad.MODEL_SCALE_X, BlockDoorKeypad.MODEL_SCALE_Y, BlockDoorKeypad.MODEL_SCALE_Z);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        ResourceLocation activeTex = TEX_KEYPAD;
        if (te.getSuccessTicks() > 0) activeTex = TEX_SUCCESS;
        else if (te.getFailTicks() > 0) activeTex = TEX_ERROR;

        bindTexture(activeTex);
        model.draw("Keypad");
        model.draw("Display");

        for (int i = 0; i < TileEntityDoorKeypad.BUTTONS; i++) {
            String group = buttonGroup(i);
            boolean pressed = te.getCooldown(i) > 0;
            GlStateManager.pushMatrix();
            if (pressed) {
                GlStateManager.translate(0, 0, 0.05);
                bindTexture(TEX_SUCCESS);
            } else {
                bindTexture(TEX_KEYPAD);
            }
            model.draw(group);
            GlStateManager.popMatrix();
        }

        drawLabelsAndDisplay(te);

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    private static String buttonGroup(int id) {
        if (id < 9) return "K" + (id + 1);
        if (id == 9) return "KReset";
        if (id == 10) return "K0";
        return "KReturn";
    }

    /** Labels sit a hair below the button quad centre so they look centred on the key cap. */
    private static final float LABEL_Y_OFFSET = -0.013671875F;
    private static final float LABEL_SCALE = 0.01F;
    private static final float DISPLAY_SCALE = 0.0065F;

    private void drawLabelsAndDisplay(TileEntityDoorKeypad te) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Labels and display text are drawn just in front of the keypad face with depth testing on,
        // so they are occluded by world geometry (no X-ray) but still sit on top of the keypad.
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);
        drawButtonLabels(font);
        drawDisplay(font, te);
        GlStateManager.disablePolygonOffset();
    }

    /** Button labels. The font is flipped vertically so it reads upright on the keypad face. */
    private static void drawButtonLabels(FontRenderer font) {
        for (int i = 0; i < TileEntityDoorKeypad.BUTTONS; i++) {
            TileEntityDoorKeypad.Button button = TileEntityDoorKeypad.BUTTON_LAYOUT[i];
            String text = BUTTON_LABELS[i];
            int w = font.getStringWidth(text);

            GlStateManager.pushMatrix();
            GlStateManager.translate(button.x, button.y + LABEL_Y_OFFSET, 0.02F);
            GlStateManager.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
            GlStateManager.color(1F, 1F, 1F, 1F);
            font.drawString(text, -w / 2, -font.FONT_HEIGHT / 2, 0xFF202020);
            GlStateManager.popMatrix();
        }
    }

    /** The LCD prompt: the typed code, a status line, or the idle prompt. */
    private static void drawDisplay(FontRenderer font, TileEntityDoorKeypad te) {
        int code = te.buildIntCode();
        boolean showInput = !te.hasStoredCode() || te.isSettingCode();
        String line1;
        String line2 = "";
        int color;
        boolean singleLine = false;
        if (te.getCodeSetTicks() > 0) {
            line1 = "Code Set!";
            color = 0xFFFFFF00;
        } else if (te.getBlackScreenTicks() > 0) {
            line1 = "";
            color = 0;
        } else if (te.getSuccessTicks() > 0) {
            line1 = "Access";
            line2 = "Granted";
            color = 0xFF15FF00;
        } else if (te.getFailTicks() > 0) {
            line1 = "Access";
            line2 = "Denied";
            color = 0xFFFF0800;
        } else if (code < 0) {
            line1 = showInput ? "Input code" : "Enter code";
            color = 0xFFFFFFFF;
        } else {
            line1 = String.valueOf(code);
            color = showInput ? 0xFFFFEE00 : 0xFFFFFFFF;
            singleLine = true;
        }

        // The display sits centred at y ~0.84375, slightly further forward than the buttons so it
        // clears the raised black screen bezel.
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.84375, 0.04F);
        GlStateManager.scale(DISPLAY_SCALE, -DISPLAY_SCALE, DISPLAY_SCALE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (!line1.isEmpty()) {
            int y = singleLine ? -font.FONT_HEIGHT / 2 : -font.FONT_HEIGHT;
            font.drawString(line1, -font.getStringWidth(line1) / 2, y, color);
        }
        if (!line2.isEmpty()) {
            font.drawString(line2, -font.getStringWidth(line2) / 2, 2, color);
        }
        GlStateManager.popMatrix();
    }
}
