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

    /** The scanner's idle prompt, and the text for every other screen state. */
    private static final String IDLE_TEXT = "Please insert key";
    private static final String BINDING_TEXT = "Binding mode";
    private static final String SCANNING_TEXT = "Scanning";
    private static final String GRANTED_TEXT = "Access granted!";
    private static final String DENIED_TEXT = "Access denied";
    private static final String SYNCED_TEXT = "Synced";
    private static final float TEXT_SCALE = 0.0042F;
    /** Width of the recessed screen in model units, minus a small margin. */
    private static final float TEXT_MAX_WIDTH = 0.36F;

    private static final int COLOR_IDLE = 0xFF9FD8FF;
    private static final int COLOR_BINDING = 0xFFFFD24A;
    private static final int COLOR_SCANNING = 0xFFFFFFFF;
    private static final int COLOR_GRANTED = 0xFF55FF55;
    private static final int COLOR_DENIED = 0xFFFF5555;
    private static final int COLOR_SYNCED = 0xFF9FFFB0;

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
        drawStatusLight(model, te.getState());
        if (lit) GlStateManager.enableLighting();
        drawScreenText(te);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /**
     * Lights the lens that matches the current state. The lens geometry is already drawn with the
     * rest of the model; drawing it again fullbright with additive blending makes it read as lit.
     */
    private static void drawStatusLight(ObjModel model, TileEntityKeycardScanner.State state) {
        String group;
        float red, green, blue;
        switch (state) {
            case GRANTED:
                group = "LightGreen";
                red = 0.55F; green = 1F; blue = 0.55F;
                break;
            case DENIED:
                group = "LightRed";
                red = 1F; green = 0.45F; blue = 0.45F;
                break;
            case BINDING:
            case SCANNING:
                group = "LightYellow";
                red = 1F; green = 0.9F; blue = 0.35F;
                break;
            default:
                return;
        }

        // Additive, drawn twice: the lenses sit in a dark recess, so a plain re-draw barely shows.
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.color(red, green, blue, 1F);
        model.draw(group);
        model.draw(group);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Draws the screen contents: one line normally, two while binding so the door count is visible.
     * The font is flipped vertically so it reads upright on the scanner face, and drawn unlit so
     * the glyphs do not take the normal of the last model quad.
     */
    private static void drawScreenText(TileEntityKeycardScanner te) {
        String first;
        String second = null;
        int color;
        switch (te.getState()) {
            case BINDING:
                first = BINDING_TEXT;
                second = "Bound doors " + te.getVisibleCount();
                color = COLOR_BINDING;
                break;
            case SCANNING:
                first = SCANNING_TEXT;
                color = COLOR_SCANNING;
                break;
            case GRANTED:
                first = GRANTED_TEXT;
                color = COLOR_GRANTED;
                break;
            case DENIED:
                first = DENIED_TEXT;
                color = COLOR_DENIED;
                break;
            case SYNCED:
                first = SYNCED_TEXT;
                color = COLOR_SYNCED;
                break;
            default:
                first = IDLE_TEXT;
                color = COLOR_IDLE;
                break;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);
        GlStateManager.pushMatrix();
        // Centre of the recessed screen: x = (-0.29 + 0.11) / 2, y = (0.58 + 0.90) / 2.
        GlStateManager.translate(-0.09F, 0.74F, 0.284F);
        GlStateManager.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        GlStateManager.color(1F, 1F, 1F, 1F);

        if (second == null) {
            drawCentred(font, first, -font.FONT_HEIGHT / 2, color);
        } else {
            drawCentred(font, first, -font.FONT_HEIGHT - 1, color);
            drawCentred(font, second, 2, color);
        }

        GlStateManager.popMatrix();
        GlStateManager.disablePolygonOffset();
        if (lighting) GlStateManager.enableLighting();
    }

    /** Draws one screen line centred on the screen, shrinking it if it would overflow. */
    private static void drawCentred(FontRenderer font, String text, int y, int color) {
        int width = font.getStringWidth(text);
        float scale = 1F;
        // The matrix is already scaled by TEXT_SCALE; TEXT_MAX_WIDTH is in model units, so the
        // limit in font pixels is TEXT_MAX_WIDTH / TEXT_SCALE.
        float limitPixels = TEXT_MAX_WIDTH / TEXT_SCALE;
        if (width > limitPixels) scale = limitPixels / width;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        font.drawString(text, Math.round(-width / 2F), y, color);
        GlStateManager.popMatrix();
    }
}
