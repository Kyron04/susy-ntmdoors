package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
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
 * Renders the keycard programmer's console model, its lit indicator and the two screen lines.
 *
 * <p>The model is authored facing NORTH with its base at y = 0, so the transform is the block
 * centre plus a yaw, exactly like the keypad. The screen text is drawn on the sloped top face:
 * the same tilt the model uses, rotated so the glyphs read upright for a player at the front.</p>
 */
@SideOnly(Side.CLIENT)
public class RenderKeycardProgrammer extends TileEntitySpecialRenderer<TileEntityKeycardProgrammer> {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(NTMDoors.MODID, "textures/models/doors/keycard_programmer.png");
    public static final ResourceLocation MODEL =
            new ResourceLocation(NTMDoors.MODID, "models/doors/keycard_programmer.obj");

    /** Lit parts, drawn once with the model. */
    private static final String[] GROUPS = {"Skirt", "Body", "Screen", "Trim", "Slit", "Buttons", "Vents", "HandPanel"};
    /** Emissive parts, drawn fullbright. */
    private static final String[] LIGHT_GROUPS = {"Light", "HandLight"};

    private static final String TITLE = "Keycard Programmer N4";
    private static final String PROMPT = "Insert keycard";
    /** Printed label above the front slit. */
    private static final String SLIT_LABEL = "Insert here";

    private static final int COLOR_TITLE = 0xFF9EE6F0;
    private static final int COLOR_PROMPT = 0xFFAFCBD4;
    /** Dark print pressed into the panel, so the label reads as part of the machine. */
    private static final int COLOR_LABEL = 0xFF2A2B2E;

    private static final float TEXT_SCALE = 0.0028F;
    /** Width of the recessed screen in model units, minus a small margin. */
    private static final float TEXT_MAX_WIDTH = 0.34F;
    /** Lifts the glyphs off the glass to keep them clear of the screen quad. */
    private static final float TEXT_LIFT = 0.002F;

    /** Front label placement, on the vertical face beside the slit. */
    private static final float LABEL_X = 0.25F;
    private static final float LABEL_Y = 0.192F;
    private static final float LABEL_Z = -0.3238F;
    private static final float LABEL_SCALE = 0.002F;

    @Override
    public void render(TileEntityKeycardProgrammer te, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockKeycardProgrammer)) return;
        EnumFacing facing = state.getValue(BlockKeycardProgrammer.FACING);

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(Facings.yaw(facing), 0F, 1F, 0F);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        bindTexture(TEXTURE);
        for (String group : GROUPS) model.draw(group);

        // The indicator and the screen text are light sources, not lit geometry.
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        for (String group : LIGHT_GROUPS) model.draw(group);
        if (lit) GlStateManager.enableLighting();
        drawScreenText();
        drawFrontLabel();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /**
     * Draws the title and prompt on the sloped screen. The font is laid along the screen plane:
     * rotate the vertical glyph quad down onto the top face, then tilt it back by the same angle
     * as the model so the two cannot drift apart.
     */
    private static void drawScreenText() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(
                BlockKeycardProgrammer.SCREEN_CENTER_X,
                BlockKeycardProgrammer.screenY(BlockKeycardProgrammer.SCREEN_CENTER_Z) + TEXT_LIFT,
                BlockKeycardProgrammer.SCREEN_CENTER_Z);
        GlStateManager.rotate(-BlockKeycardProgrammer.SCREEN_TILT, 1F, 0F, 0F);
        GlStateManager.rotate(90F, 1F, 0F, 0F);
        // Mirroring X lays the glyphs the right way round on the sloped screen: the screen's
        // "right" is the player's left, so without the flip the text reads mirrored.
        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        GlStateManager.color(1F, 1F, 1F, 1F);

        drawCentred(font, TITLE, -12, COLOR_TITLE);
        drawCentred(font, PROMPT, 1, COLOR_PROMPT);

        GlStateManager.popMatrix();
        GlStateManager.disablePolygonOffset();
        GlStateManager.enableCull();
        if (lighting) GlStateManager.enableLighting();
    }

    /** Draws one screen line centred on the screen, shrinking it if it would overflow. */
    private static void drawCentred(FontRenderer font, String text, int y, int color) {
        int width = font.getStringWidth(text);
        float scale = 1F;
        float limitPixels = TEXT_MAX_WIDTH / TEXT_SCALE;
        if (width > limitPixels) scale = limitPixels / width;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        font.drawString(text, Math.round(-width / 2F), y, color);
        GlStateManager.popMatrix();
    }

    /**
     * Draws the printed label above the card slit. The front face points at -Z, so the glyphs are
     * turned to face it; without the turn they would read mirrored like the screen does.
     */
    private static void drawFrontLabel() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.pushMatrix();
        GlStateManager.translate(LABEL_X, LABEL_Y, LABEL_Z);
        GlStateManager.rotate(180F, 0F, 1F, 0F);
        GlStateManager.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        GlStateManager.color(1F, 1F, 1F, 1F);

        int width = font.getStringWidth(SLIT_LABEL);
        int x = Math.round(-width / 2F);
        int y = -font.FONT_HEIGHT / 2;
        // Drawn twice, one pixel apart, for a bold printed look without a bright sign plaque.
        font.drawString(SLIT_LABEL, x, y, COLOR_LABEL);
        font.drawString(SLIT_LABEL, x + 1, y, COLOR_LABEL);

        GlStateManager.popMatrix();
        GlStateManager.enableCull();
        if (lighting) GlStateManager.enableLighting();
    }
}
