package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorKeypad;
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
        if (te == null) return;
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockDoorKeypad)) return;
        EnumFacing facing = state.getValue(BlockDoorKeypad.FACING);

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(RenderDoor.yaw(facing), 0F, 1F, 0F);
        // Center the plate vertically on the block face and pull it a hair off the wall
        // so the back face does not z-fight with the block it is mounted on.
        GlStateManager.translate(0, 0.29375F, -0.47F);
        GlStateManager.scale(0.35F, 0.4125F, 0.25F);

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

    /**
     * Centres of each button and the display in model space (model spans x=-0.375..0.375,
     * y=0..1, z=0 at the front face). Text is drawn slightly in front of the face with the
     * font's Y axis flipped so it reads upright.
     */
    private static final float[][] BUTTON_CENTRES = {
            {0.234375F, 0.615234F}, {0F, 0.615234F}, {-0.234375F, 0.615234F},
            {0.234375F, 0.439453F}, {0F, 0.439453F}, {-0.234375F, 0.439453F},
            {0.234375F, 0.263672F}, {0F, 0.263672F}, {-0.234375F, 0.263672F},
            {0.234375F, 0.087891F}, {0F, 0.087891F}, {-0.234375F, 0.087891F}
    };

    private void drawLabelsAndDisplay(TileEntityDoorKeypad te) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Labels and display text are drawn just in front of the keypad face with depth testing on,
        // so they are occluded by world geometry (no X-ray) but still sit on top of the keypad.
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);

        // Button labels. The font is flipped vertically (Y scale -1) so it reads upright on the
        // vertical keypad face while still facing outward (+Z) instead of being mirrored.
        float labelScale = 0.01F;
        for (int i = 0; i < TileEntityDoorKeypad.BUTTONS; i++) {
            float cx = BUTTON_CENTRES[i][0];
            float cy = BUTTON_CENTRES[i][1];
            String text = BUTTON_LABELS[i];
            int w = font.getStringWidth(text);

            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0.02F);
            GlStateManager.scale(labelScale, -labelScale, labelScale);
            GlStateManager.color(1F, 1F, 1F, 1F);
            font.drawString(text, -w / 2, -font.FONT_HEIGHT / 2, 0xFF202020);
            GlStateManager.popMatrix();
        }

        // Display area: centred on the screen at y ~0.84375, slightly further forward than
        // the buttons so it clears the raised black screen bezel.
        int code = te.buildIntCode();
        float displayScale = 0.0065F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.84375, 0.04F);
        GlStateManager.scale(displayScale, -displayScale, displayScale);
        GlStateManager.color(1F, 1F, 1F, 1F);

        String line1, line2;
        int color;
        boolean showInput = !te.hasStoredCode() || te.isSettingCode();
        if (te.getCodeSetTicks() > 0) {
            line1 = "Code Set!";
            line2 = "";
            color = 0xFFFFFF00;
        } else if (te.getBlackScreenTicks() > 0) {
            line1 = "";
            line2 = "";
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
            line2 = "";
            color = 0xFFFFFFFF;
        } else {
            String text = String.valueOf(code);
            color = showInput ? 0xFFFFEE00 : 0xFFFFFFFF;
            int w = font.getStringWidth(text);
            font.drawString(text, -w / 2, -font.FONT_HEIGHT / 2, color);
            GlStateManager.popMatrix();
            GlStateManager.disablePolygonOffset();
            return;
        }
        int w1 = font.getStringWidth(line1);
        int w2 = font.getStringWidth(line2);
        font.drawString(line1, -w1 / 2, -font.FONT_HEIGHT, color);
        font.drawString(line2, -w2 / 2, 2, color);

        GlStateManager.popMatrix();
        GlStateManager.disablePolygonOffset();
    }
}
