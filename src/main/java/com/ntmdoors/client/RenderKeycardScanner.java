package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockKeycardScanner;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Renders the keycard scanner model, its illuminated security stripe and the screen prompt.
 * The transform mirrors {@link BlockKeycardScanner#plate}: the model is authored facing +Z (like
 * the keypad) and its back plate sits on the block's wall-side face.
 */
@SideOnly(Side.CLIENT)
public class RenderKeycardScanner extends TileEntitySpecialRenderer<TileEntityKeycardScanner> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keycard_scanner.png");
    public static final ResourceLocation MODEL = new ResourceLocation(NTMDoors.MODID, "models/doors/keycard_scanner.obj");

    /** Emission group drawn fullbright, so the blue security stripe reads in the dark. */
    public static final String SECURITY_GROUP = "SecurityLine";

    /** Lit model groups, drawn in this order; each group is a separate display list. */
    public static final String[] GROUPS = {
            "Base", "Trim", "Screen", "LightGreen", "LightYellow", "LightRed", "Slot", "Vents"
    };

    /** The scanner's idle prompt. */
    private static final String SCREEN_TEXT = "Please insert key";
    private static final float TEXT_SCALE = 0.0042F;
    /** Width of the recessed screen in model units, minus a small margin. */
    private static final float TEXT_MAX_WIDTH = 0.36F;
    private static final int TEXT_COLOR = 0xFF9FD8FF;

    @Override
    public void render(TileEntityKeycardScanner te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockKeycardScanner)) return;
        EnumFacing facing = state.getValue(BlockKeycardScanner.FACING);

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(Facings.yaw(facing), 0F, 1F, 0F);
        GlStateManager.translate(0, BlockKeycardScanner.MODEL_OFFSET_Y, BlockKeycardScanner.MODEL_OFFSET_Z);
        GlStateManager.scale(BlockKeycardScanner.MODEL_SCALE_X, BlockKeycardScanner.MODEL_SCALE_Y, BlockKeycardScanner.MODEL_SCALE_Z);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        bindTexture(TEXTURE);
        for (String group : GROUPS) model.draw(group);

        // The security stripe and the LCD text are light sources, not lit geometry.
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        model.draw(SECURITY_GROUP);
        if (lit) GlStateManager.enableLighting();
        drawScreenText();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /**
     * Draws the idle prompt just in front of the recessed screen. The font is flipped vertically
     * so it reads upright on the scanner face, and drawn unlit so the glyphs do not take the
     * normal of the last model quad.
     */
    private static void drawScreenText() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(SCREEN_TEXT);

        float scale = TEXT_SCALE;
        if (width > 0 && width * scale > TEXT_MAX_WIDTH) scale = TEXT_MAX_WIDTH / width;

        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);
        GlStateManager.pushMatrix();
        // Centre of the recessed screen: x = (-0.29 + 0.11) / 2, y = (0.58 + 0.90) / 2.
        GlStateManager.translate(-0.09F, 0.74F, 0.284F);
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.color(1F, 1F, 1F, 1F);
        font.drawString(SCREEN_TEXT, -width / 2, -font.FONT_HEIGHT / 2, TEXT_COLOR);
        GlStateManager.popMatrix();
        GlStateManager.disablePolygonOffset();
        if (lit) GlStateManager.enableLighting();
    }
}
