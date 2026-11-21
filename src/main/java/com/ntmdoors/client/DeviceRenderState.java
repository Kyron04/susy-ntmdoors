// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * Puts every device renderer on the same known GL baseline.
 *
 * <p>Minecraft's TESR pass runs after other mods' tile entities and does not reset the GL state
 * between them. A renderer that leaves a non-white colour, lighting disabled, or a changed depth
 * mode behind tints and shades the next device, and because the visible TESR set changes with the
 * camera angle the result looks angle-dependent. This guard forces the state the device models
 * expect and ends on a clean state, so neither side can leak into the other.</p>
 */
@SideOnly(Side.CLIENT)
final class DeviceRenderState {

    private static float savedBrightnessX;
    private static float savedBrightnessY;

    private DeviceRenderState() {
    }

    static void begin() {
        savedBrightnessX = OpenGlHelper.lastBrightnessX;
        savedBrightnessY = OpenGlHelper.lastBrightnessY;

        GlStateManager.resetColor();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GL11.glEnable(GL11.GL_LIGHTING);
        GlStateManager.enableLighting();
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GlStateManager.enableColorMaterial();
        GlStateManager.colorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        // Re-apply the standard white light rig. Another mod can leave warm/red light colours or
        // ambient values behind, which is enough to tint every lit device face.
        RenderHelper.enableStandardItemLighting();
        GlStateManager.resetColor();
        GlStateManager.color(1F, 1F, 1F, 1F);

        // The device models are closed shells with a few deliberately double-sided quads. They
        // must not inherit a previous renderer's culling choice.
        GL11.glDisable(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();

        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
        GL11.glDepthMask(true);
        GlStateManager.depthMask(true);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableTexture2D();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();

        // Several models are scaled non-uniformly. GL_RESCALE_NORMAL only corrects uniform
        // scaling, so normalise the transformed normals as well or the shading changes with the
        // camera angle.
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GlStateManager.enableRescaleNormal();
        GL11.glEnable(GL11.GL_NORMALIZE);
        GlStateManager.enableNormalize();
    }

    static void end() {
        // Leave a neutral world-rendering state for whatever renders after the last TESR.
        GlStateManager.resetColor();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GL11.glEnable(GL11.GL_LIGHTING);
        GlStateManager.enableLighting();
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GlStateManager.enableColorMaterial();
        GlStateManager.colorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.enableCull();

        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.disableBlend();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
        GL11.glDepthMask(true);
        GlStateManager.depthMask(true);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableTexture2D();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GlStateManager.disableRescaleNormal();
        GL11.glDisable(GL11.GL_NORMALIZE);
        GlStateManager.disableNormalize();

        // If a renderer threw while drawing a fullbright sub-pass, this puts the world lightmap
        // back before the next tile entity is drawn.
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                savedBrightnessX, savedBrightnessY);
    }
}
