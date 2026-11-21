// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.tile.TileEntityEmancipationGrill.FieldColour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws the Emancipation Grill energy field.
 *
 * <p>The visual is deliberately cheap: one seamless wisp texture is drawn as three UV-scrolled
 * layers on a single plane, followed by a crisp edge texture, four thin core lines and a handful
 * of sparks. There are no shaders, framebuffer copies, per-frame texture uploads or background
 * refraction passes. The thin field cell is still owned by the tile entity for future logic; this
 * renderer only draws a plane through the middle of that cell.</p>
 *
 * <p>The field is collected once per pair during the TESR pass and drawn in the world-last pass,
 * after every door and device renderer. The plane is translucent and does not write depth, so
 * drawing it last makes it blend over the devices behind it instead of being overwritten when the
 * TESR order happens to put one of them after the grill.</p>
 */
@SideOnly(Side.CLIENT)
public class RenderEmancipationField extends DeviceRenderer<TileEntityEmancipationGrill> {

    private static final ResourceLocation FIELD =
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field.png");
    private static final ResourceLocation[] FIELD_VARIANTS = {
            FIELD,
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field_2.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field_3.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field_4.png")
    };
    private static final ResourceLocation EDGE =
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field_edge.png");
    private static final ResourceLocation SPARK =
            new ResourceLocation(NTMDoors.MODID, "textures/blocks/emancipation_grill_field_spark.png");

    /** Width of the soft edge gradient, in blocks. */
    private static final double EDGE_WIDTH = 0.14D;
    /** Width of the bright white edge line, in blocks. */
    private static final double CORE_WIDTH = 0.012D;
    private static final int PARTICLES = 16;

    /**
     * Per-colour palettes, indexed by {@link FieldColour#ordinal()} (RED, GREEN, BLUE). The wisp
     * arrays are indexed by layer. Blue keeps the original field colours exactly.
     */
    private static final float[][] LAYER_RED = {
            {0.95F, 1.00F, 1.00F},
            {0.25F, 0.55F, 0.88F},
            {0.58F, 0.80F, 0.95F}
    };
    private static final float[][] LAYER_GREEN = {
            {0.30F, 0.55F, 0.88F},
            {0.85F, 1.00F, 1.00F},
            {0.82F, 0.94F, 0.99F}
    };
    private static final float[][] LAYER_BLUE = {
            {0.25F, 0.48F, 0.84F},
            {0.38F, 0.62F, 0.90F},
            {1.00F, 1.00F, 1.00F}
    };
    private static final float[] LAYER_ALPHA = {0.20F, 0.13F, 0.07F};
    private static final float[][] WASH = {
            {1.00F, 0.40F, 0.34F, 0.10F},
            {0.42F, 1.00F, 0.48F, 0.10F},
            {0.64F, 0.86F, 1.00F, 0.10F}
    };
    private static final float[][] EDGE_COLOUR = {
            {1.00F, 0.72F, 0.68F, 0.95F},
            {0.70F, 1.00F, 0.76F, 0.95F},
            {0.88F, 0.97F, 1.00F, 0.95F}
    };
    private static final float[][] CORE_COLOUR = {
            {1.00F, 0.92F, 0.90F, 0.85F},
            {0.90F, 1.00F, 0.92F, 0.85F},
            {0.90F, 0.99F, 1.00F, 0.85F}
    };
    private static final float[][] SPARK_COLOUR = {
            {1.00F, 0.76F, 0.72F},
            {0.72F, 1.00F, 0.76F},
            {0.88F, 0.97F, 1.00F}
    };
    /** Blocks per horizontal texture repeat. */
    private static final double[] LAYER_U_REPEAT = {3.0D, 2.0D, 1.35D};
    /** Texture repeats over the three-block field height. */
    private static final double[] LAYER_V_REPEAT = {1.0D, 1.4D, 2.0D};
    /** UV units per game tick. */
    private static final double[] LAYER_U_SPEED = {0.0012D, -0.0018D, 0.0025D};
    private static final double[] LAYER_V_SPEED = {-0.0010D, -0.0016D, -0.0022D};

    /** Pair keys (lower block position) already collected this frame. */
    private static final Set<Long> RENDERED_THIS_FRAME = new HashSet<>();
    /** Visible fields collected during the TESR pass and drawn after the device renderers. */
    private static final Set<TileEntityEmancipationGrill> VISIBLE = new HashSet<>();

    /**
     * Draws the collected fields after the world and all TESRs. The field is translucent and does
     * not write depth, so drawing it here guarantees that doors and devices drawn earlier are
     * blended under it instead of overwriting it when the TESR order happens to put them later.
     */
    static void drawDeferred(float partialTicks) {
        if (VISIBLE.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;

        RenderManager manager = mc.getRenderManager();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-manager.viewerPosX, -manager.viewerPosY,
                    -manager.viewerPosZ);
            for (TileEntityEmancipationGrill grill : VISIBLE) {
                if (grill.getWorld() != mc.world) continue;
                AxisAlignedBB box = grill.getFieldBox();
                BlockPos partner = grill.getPartner();
                if (box == null || partner == null) continue;
                drawField(grill, grill.getPos().getX(), grill.getPos().getY(),
                        grill.getPos().getZ(), partialTicks);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    static void endFrame() {
        RENDERED_THIS_FRAME.clear();
        VISIBLE.clear();
    }

    @Override
    protected void renderDevice(TileEntityEmancipationGrill te, double x, double y, double z,
                                float partialTicks, int destroyStage, float alpha) {
        AxisAlignedBB box = te.getFieldBox();
        BlockPos partner = te.getPartner();
        if (box == null || partner == null) return;

        // One full field per pair per frame. Whichever end is visible claims it first.
        long pairKey = Math.min(te.getPos().toLong(), partner.toLong());
        if (!RENDERED_THIS_FRAME.add(pairKey)) return;
        VISIBLE.add(te);
    }

    private static void drawField(TileEntityEmancipationGrill te, double x, double y, double z,
                                  float partialTicks) {
        AxisAlignedBB box = te.getFieldBox();
        BlockPos partner = te.getPartner();
        if (box == null || partner == null) return;

        long pairKey = Math.min(te.getPos().toLong(), partner.toLong());
        // Deterministic per-pair variation: texture variant, starting patch and a small scale
        // jitter. Adjacent fields therefore do not show the same splash.
        long mixed = pairKey * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 29;
        int variant = (int) Math.floorMod(mixed, (long) FIELD_VARIANTS.length);
        double pairSeed = ((mixed >>> 11) & 0xFFFFFFL) / (double) 0x1000000L;
        double pairU = pairSeed * 17.0D;
        double pairV = pairSeed * 11.0D;
        double pairScale = 0.93D + pairSeed * 0.14D;

        World world = te.getWorld();
        if (world == null) return;
        FieldColour colour = te.getFieldColour();
        int palette = colour == null ? FieldColour.BLUE.ordinal() : colour.ordinal();

        BlockPos core = te.getPos();
        double time = world.getTotalWorldTime() + partialTicks;
        boolean spansX = (box.maxX - box.minX) > (box.maxZ - box.minZ);
        double planeX = (box.minX + box.maxX) * 0.5D;
        double planeZ = (box.minZ + box.maxZ) * 0.5D;
        double alongMin = spansX ? box.minX : box.minZ;
        double alongMax = spansX ? box.maxX : box.maxZ;
        double alongLength = alongMax - alongMin;
        boolean horizontal = (box.maxY - box.minY) <= 0.1D;

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int oldDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int oldSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int oldDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float oldBrightnessX = OpenGlHelper.lastBrightnessX;
        float oldBrightnessY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        if (horizontal) {
            drawHorizontalField(buffer, tessellator, core, box, time, pairU, pairV, pairScale,
                    variant, palette);
        } else {
        // Pale glass wash. Normal alpha keeps the room visible through the field.
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float[] wash = WASH[palette];
        addPlaneQuadColor(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                box.minY, box.maxY, wash[0], wash[1], wash[2], wash[3]);
        tessellator.draw();

        // Three scrolling wisp layers sharing one texture and one draw call.
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(FIELD_VARIANTS[variant]);
        setTextureParameters(GL11.GL_REPEAT, GL11.GL_LINEAR);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double vRepeatScale = (box.maxY - box.minY) < 2.0D
                ? (box.maxY - box.minY) / 3.0D : 1.0D;
        for (int layer = 0; layer < 3; layer++) {
            double u0 = pairU + time * LAYER_U_SPEED[layer];
            double u1 = u0 + alongLength / (LAYER_U_REPEAT[layer] * pairScale);
            double v0 = pairV + time * LAYER_V_SPEED[layer];
            double v1 = v0 + LAYER_V_REPEAT[layer] * vRepeatScale;
            addPlaneQuadTex(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                    box.minY, box.maxY, u0, u1, v0, v1,
                    LAYER_RED[palette][layer], LAYER_GREEN[palette][layer],
                    LAYER_BLUE[palette][layer], LAYER_ALPHA[layer]);
        }
        tessellator.draw();

        // Soft edge gradients.
        Minecraft.getMinecraft().getTextureManager().bindTexture(EDGE);
        setTextureParameters(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        float[] edge = EDGE_COLOUR[palette];
        addEdgeGradient(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                box.minY, box.maxY, edge[0], edge[1], edge[2], edge[3]);
        tessellator.draw();

        // Crisp core lines.
        GlStateManager.disableTexture2D();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float[] coreLine = CORE_COLOUR[palette];
        addEdgeCore(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                box.minY, box.maxY, coreLine[0], coreLine[1], coreLine[2], coreLine[3]);
        tessellator.draw();

        // A few drifting sparks. They sit on the field plane, so this is just 16 tiny quads.
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(SPARK);
        setTextureParameters(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        addSparks(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                box.minY, box.maxY, time, pairSeed, palette);
        tessellator.draw();
        }

        GlStateManager.color(1F, 1F, 1F, 1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                oldBrightnessX, oldBrightnessY);
        GlStateManager.depthMask(depthMask);
        GlStateManager.tryBlendFuncSeparate(oldSrcRgb, oldDstRgb, oldSrcAlpha, oldDstAlpha);
        if (!blend) GlStateManager.disableBlend();
        if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        if (alphaTest) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (depthTest) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        GlStateManager.setActiveTexture(oldActiveTexture);
        GlStateManager.popMatrix();
    }

    private static void drawHorizontalField(BufferBuilder buffer, Tessellator tessellator,
                                            BlockPos core, AxisAlignedBB box, double time,
                                            double pairU, double pairV, double pairScale,
                                            int variant, int palette) {
        double y = (box.minY + box.maxY) * 0.5D - core.getY();
        double x0 = box.minX - core.getX();
        double x1 = box.maxX - core.getX();
        double z0 = box.minZ - core.getZ();
        double z1 = box.maxZ - core.getZ();
        double lengthX = x1 - x0;
        double lengthZ = z1 - z0;

        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float[] wash = WASH[palette];
        horizontalQuadColor(buffer, x0, z0, x1, z1, y, wash[0], wash[1], wash[2], wash[3]);
        tessellator.draw();

        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(FIELD_VARIANTS[variant]);
        setTextureParameters(GL11.GL_REPEAT, GL11.GL_LINEAR);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int layer = 0; layer < 3; layer++) {
            double u0 = pairU + time * LAYER_U_SPEED[layer];
            double u1 = u0 + lengthX / (LAYER_U_REPEAT[layer] * pairScale);
            double v0 = pairV + time * LAYER_V_SPEED[layer];
            double v1 = v0 + lengthZ / (LAYER_V_REPEAT[layer] * pairScale);
            horizontalQuadTex(buffer, x0, z0, x1, z1, y, u0, v0, u1, v1,
                    LAYER_RED[palette][layer], LAYER_GREEN[palette][layer],
                    LAYER_BLUE[palette][layer], LAYER_ALPHA[layer]);
        }
        tessellator.draw();

        Minecraft.getMinecraft().getTextureManager().bindTexture(EDGE);
        setTextureParameters(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double edge = EDGE_WIDTH;
        float[] edgeColour = EDGE_COLOUR[palette];
        horizontalQuadTex(buffer, x0, z0, x0 + edge, z1, y, 0, 0, 1, 1,
                edgeColour[0], edgeColour[1], edgeColour[2], edgeColour[3]);
        horizontalQuadTex(buffer, x1 - edge, z0, x1, z1, y, 1, 0, 0, 1,
                edgeColour[0], edgeColour[1], edgeColour[2], edgeColour[3]);
        horizontalQuadTex(buffer, x0, z0, x1, z0 + edge, y, 0, 0, 1, 1,
                edgeColour[0], edgeColour[1], edgeColour[2], edgeColour[3]);
        horizontalQuadTex(buffer, x0, z1 - edge, x1, z1, y, 0, 1, 1, 0,
                edgeColour[0], edgeColour[1], edgeColour[2], edgeColour[3]);
        tessellator.draw();

        GlStateManager.disableTexture2D();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double coreWidth = CORE_WIDTH;
        float[] coreLine = CORE_COLOUR[palette];
        horizontalQuadColor(buffer, x0, z0, x0 + coreWidth, z1, y,
                coreLine[0], coreLine[1], coreLine[2], coreLine[3]);
        horizontalQuadColor(buffer, x1 - coreWidth, z0, x1, z1, y,
                coreLine[0], coreLine[1], coreLine[2], coreLine[3]);
        horizontalQuadColor(buffer, x0, z0, x1, z0 + coreWidth, y,
                coreLine[0], coreLine[1], coreLine[2], coreLine[3]);
        horizontalQuadColor(buffer, x0, z1 - coreWidth, x1, z1, y,
                coreLine[0], coreLine[1], coreLine[2], coreLine[3]);
        tessellator.draw();

        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(SPARK);
        setTextureParameters(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        float[] spark = SPARK_COLOUR[palette];
        for (int i = 0; i < PARTICLES; i++) {
            double seed = i * 0.6180339887498949D + pairScale * 3.7D;
            double phase = fract(seed + time * (0.010D + (i % 5) * 0.0016D));
            double x = x0 + fract(seed * 7.13D
                    + Math.sin(time * 0.02D + i * 1.7D) * 0.015D) * lengthX;
            double z = z0 + fract(seed * 3.71D
                    + Math.cos(time * 0.017D + i * 2.1D) * 0.015D) * lengthZ;
            double size = 0.025D + (i % 4) * 0.007D;
            float alpha = (float) (0.10D + 0.28D * Math.sin(Math.PI * phase));
            if (alpha <= 0.01F) continue;
            horizontalQuadTex(buffer, x - size, z - size, x + size, z + size, y,
                    0.0D, 0.0D, 1.0D, 1.0D, spark[0], spark[1], spark[2], alpha);
        }
        tessellator.draw();
    }

    private static void horizontalQuadColor(BufferBuilder buffer, double x0, double z0,
                                            double x1, double z1, double y,
                                            float red, float green, float blue, float alpha) {
        buffer.pos(x0, y, z0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x1, y, z0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x1, y, z1).color(red, green, blue, alpha).endVertex();
        buffer.pos(x0, y, z1).color(red, green, blue, alpha).endVertex();
    }

    private static void horizontalQuadTex(BufferBuilder buffer, double x0, double z0,
                                          double x1, double z1, double y,
                                          double u0, double v0, double u1, double v1,
                                          float red, float green, float blue, float alpha) {
        buffer.pos(x0, y, z0).tex(u0, v0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x1, y, z0).tex(u1, v0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x1, y, z1).tex(u1, v1).color(red, green, blue, alpha).endVertex();
        buffer.pos(x0, y, z1).tex(u0, v1).color(red, green, blue, alpha).endVertex();
    }

    private static void setTextureParameters(int wrap, int filter) {
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    }

    private static void addPlaneQuadColor(BufferBuilder buffer, BlockPos core,
                                          boolean spansX, double planeX, double planeZ,
                                          double alongMin, double alongMax,
                                          double minY, double maxY,
                                          float red, float green, float blue, float alpha) {
        planeColor(buffer, core, spansX, planeX, planeZ, alongMin, minY, red, green, blue, alpha);
        planeColor(buffer, core, spansX, planeX, planeZ, alongMax, minY, red, green, blue, alpha);
        planeColor(buffer, core, spansX, planeX, planeZ, alongMax, maxY, red, green, blue, alpha);
        planeColor(buffer, core, spansX, planeX, planeZ, alongMin, maxY, red, green, blue, alpha);
    }

    private static void addPlaneQuadTex(BufferBuilder buffer, BlockPos core,
                                        boolean spansX, double planeX, double planeZ,
                                        double alongMin, double alongMax,
                                        double minY, double maxY,
                                        double u0, double u1, double v0, double v1,
                                        float red, float green, float blue, float alpha) {
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, minY, u0, v0, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, minY, u1, v0, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, maxY, u1, v1, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, maxY, u0, v1, red, green, blue, alpha);
    }

    private static void addEdgeGradient(BufferBuilder buffer, BlockPos core,
                                        boolean spansX, double planeX, double planeZ,
                                        double alongMin, double alongMax,
                                        double minY, double maxY,
                                        float red, float green, float blue, float alpha) {
        double innerMin = alongMin + EDGE_WIDTH;
        double innerMax = alongMax - EDGE_WIDTH;

        // Left and right vertical edges.
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, minY, 0.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, innerMin, minY, 1.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, innerMin, maxY, 1.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, maxY, 0.0D, 1.0D, red, green, blue, alpha);

        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, minY, 0.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, innerMax, minY, 1.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, innerMax, maxY, 1.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, maxY, 0.0D, 1.0D, red, green, blue, alpha);

        // Top and bottom horizontal edges.
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, maxY, 0.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, maxY, 0.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, maxY - EDGE_WIDTH, 1.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, maxY - EDGE_WIDTH, 1.0D, 0.0D, red, green, blue, alpha);

        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, minY, 0.0D, 0.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, minY, 0.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMax, minY + EDGE_WIDTH, 1.0D, 1.0D, red, green, blue, alpha);
        planeTex(buffer, core, spansX, planeX, planeZ, alongMin, minY + EDGE_WIDTH, 1.0D, 0.0D, red, green, blue, alpha);
    }

    private static void addEdgeCore(BufferBuilder buffer, BlockPos core,
                                    boolean spansX, double planeX, double planeZ,
                                    double alongMin, double alongMax,
                                    double minY, double maxY,
                                    float red, float green, float blue, float alpha) {
        double innerMin = alongMin + CORE_WIDTH;
        double innerMax = alongMax - CORE_WIDTH;

        // Left and right core lines.
        addPlaneQuadColor(buffer, core, spansX, planeX, planeZ, alongMin, innerMin,
                minY, maxY, red, green, blue, alpha);
        addPlaneQuadColor(buffer, core, spansX, planeX, planeZ, innerMax, alongMax,
                minY, maxY, red, green, blue, alpha);

        // Bottom and top core lines.
        addPlaneQuadColor(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                minY, minY + CORE_WIDTH, red, green, blue, alpha);
        addPlaneQuadColor(buffer, core, spansX, planeX, planeZ, alongMin, alongMax,
                maxY - CORE_WIDTH, maxY, red, green, blue, alpha);
    }

    private static void addSparks(BufferBuilder buffer, BlockPos core,
                                  boolean spansX, double planeX, double planeZ,
                                  double alongMin, double alongMax,
                                  double minY, double maxY, double time, double pairSeed,
                                  int palette) {
        double length = alongMax - alongMin;
        double height = maxY - minY;
        float[] spark = SPARK_COLOUR[palette];

        for (int i = 0; i < PARTICLES; i++) {
            double seed = i * 0.6180339887498949D + pairSeed * 3.7D;
            double phase = fract(seed + time * (0.010D + (i % 5) * 0.0016D));
            double y = minY + phase * height;
            double along = alongMin + fract(seed * 7.13D
                    + Math.sin(time * 0.02D + i * 1.7D) * 0.015D) * length;
            double size = 0.025D + (i % 4) * 0.007D;
            float alpha = (float) (0.10D + 0.28D * Math.sin(Math.PI * phase));
            if (alpha <= 0.01F) continue;

            planeTex(buffer, core, spansX, planeX, planeZ, along - size, y - size,
                    0.0D, 0.0D, spark[0], spark[1], spark[2], alpha);
            planeTex(buffer, core, spansX, planeX, planeZ, along + size, y - size,
                    1.0D, 0.0D, spark[0], spark[1], spark[2], alpha);
            planeTex(buffer, core, spansX, planeX, planeZ, along + size, y + size,
                    1.0D, 1.0D, spark[0], spark[1], spark[2], alpha);
            planeTex(buffer, core, spansX, planeX, planeZ, along - size, y + size,
                    0.0D, 1.0D, spark[0], spark[1], spark[2], alpha);
        }
    }

    private static void planeColor(BufferBuilder buffer, BlockPos core,
                                   boolean spansX, double planeX, double planeZ,
                                   double along, double y,
                                   float red, float green, float blue, float alpha) {
        buffer.pos(planeLocalX(spansX, planeX, along) - core.getX(),
                        y - core.getY(),
                        planeLocalZ(spansX, planeZ, along) - core.getZ())
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private static void planeTex(BufferBuilder buffer, BlockPos core,
                                 boolean spansX, double planeX, double planeZ,
                                 double along, double y, double u, double v,
                                 float red, float green, float blue, float alpha) {
        buffer.pos(planeLocalX(spansX, planeX, along) - core.getX(),
                        y - core.getY(),
                        planeLocalZ(spansX, planeZ, along) - core.getZ())
                .tex(u, v)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private static double planeLocalX(boolean spansX, double planeX, double along) {
        return spansX ? along : planeX;
    }

    private static double planeLocalZ(boolean spansX, double planeZ, double along) {
        return spansX ? planeZ : along;
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }
}
