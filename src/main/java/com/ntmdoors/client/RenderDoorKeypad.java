package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorKeypad.Feedback;
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

        Feedback feedback = te.getFeedback();
        ResourceLocation activeTex = TEX_KEYPAD;
        if (feedback == Feedback.SUCCESS || feedback == Feedback.RESET_DONE) {
            activeTex = TEX_SUCCESS;
        } else if (feedback == Feedback.DENIED) {
            activeTex = TEX_ERROR;
        }

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

    /**
     * The LCD: feedback first, then the prompt for the keypad's current mode. Prompts are kept to
     * two short lines because the display is only 0.625 model units wide.
     */
    private static void drawDisplay(FontRenderer font, TileEntityDoorKeypad te) {
        String line1;
        String line2 = "";
        int color;
        int line2Color = 0xFFFFFFFF;
        boolean twoLines = false;

        Feedback feedback = te.getFeedback();
        if (feedback != Feedback.NONE) {
            switch (feedback) {
                case CODE_SET:
                    line1 = "Code Set!";
                    color = 0xFFFFFF00;
                    break;
                case CODE_SYNCED:
                    line1 = "Code synced";
                    color = 0xFFB04CFF;
                    break;
                case RESET_DONE:
                    line1 = "Code reset!";
                    color = 0xFF15FF00;
                    break;
                case SUCCESS:
                    line1 = "Access";
                    line2 = "Granted";
                    color = 0xFF15FF00;
                    twoLines = true;
                    break;
                case NO_DOOR:
                    line1 = "No door";
                    color = 0xFFFF0800;
                    break;
                default: // DENIED
                    line1 = "Access";
                    line2 = "Denied";
                    color = 0xFFFF0800;
                    twoLines = true;
            }
        } else if (te.getBlackScreenTicks() > 0) {
            line1 = "";
            color = 0;
        } else if (te.getMode() == TileEntityDoorKeypad.Mode.RESET_CONFIRM) {
            line1 = "Reset code?";
            color = 0xFF3FA9F5;
        } else if (te.getMode() == TileEntityDoorKeypad.Mode.RESET_ENTER) {
            line1 = "Input current";
            line2 = te.getEntry().isEmpty() ? "code" : te.getEntry();
            color = 0xFFFFEE00;
            line2Color = 0xFFFFEE00;
            twoLines = true;
        } else if (te.getBindMode() == TileEntityDoorKeypad.BindMode.MULTI) {
            int slot = te.getSelectedSlot();
            if (slot >= 0) {
                line1 = "Door " + (slot + 1);
                line2 = te.getEntry().isEmpty() ? "Input code" : te.getEntry();
                color = 0xFFFFFFFF;
                line2Color = te.getEntry().isEmpty() ? 0xFFFFFFFF : 0xFFFFEE00;
            } else {
                line1 = "Multi";
                line2 = "Bounding Mode";
                color = 0xFF3FA9F5;
                line2Color = 0xFF3FA9F5;
            }
            twoLines = true;
        } else if (te.getBindMode() == TileEntityDoorKeypad.BindMode.DISTANCE) {
            boolean typing = !te.getEntry().isEmpty();
            line1 = typing ? "Enter code" : "Distance";
            line2 = typing ? te.getEntry() : "Bounding Mode";
            color = typing ? 0xFFFFFFFF : 0xFF3FA9F5;
            line2Color = typing ? 0xFFFFEE00 : 0xFF3FA9F5;
            twoLines = true;
        } else {
            switch (te.getMode()) {
                case RESET_CONFIRM:
                    line1 = "Reset code?";
                    color = 0xFF3FA9F5;
                    break;
                case RESET_ENTER:
                    line1 = "Input current";
                    line2 = te.getEntry().isEmpty() ? "code" : te.getEntry();
                    color = 0xFFFFEE00;
                    line2Color = 0xFFFFEE00;
                    twoLines = true;
                    break;
                case SET_CODE:
                    line1 = te.getEntry().isEmpty() ? "Input code" : "New code";
                    line2 = te.getEntry();
                    color = 0xFFFFFFFF;
                    line2Color = 0xFFFFEE00;
                    twoLines = !line2.isEmpty();
                    break;
                default: // ENTER_CODE
                    line1 = "Enter code";
                    line2 = te.getEntry();
                    color = 0xFFFFFFFF;
                    twoLines = !line2.isEmpty();
            }
        }

        // The display sits centred at y ~0.84375, slightly further forward than the buttons so it
        // clears the raised black screen bezel.
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.84375, 0.04F);
        GlStateManager.scale(DISPLAY_SCALE, -DISPLAY_SCALE, DISPLAY_SCALE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        if (!line1.isEmpty()) {
            int y = twoLines ? -font.FONT_HEIGHT : -font.FONT_HEIGHT / 2;
            font.drawString(line1, -font.getStringWidth(line1) / 2, y, color);
        }
        if (!line2.isEmpty()) {
            font.drawString(line2, -font.getStringWidth(line2) / 2, 2, line2Color);
        }
        GlStateManager.popMatrix();
    }
}
