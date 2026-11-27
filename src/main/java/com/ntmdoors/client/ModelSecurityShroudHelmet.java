// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Rotations;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

/**
 * Renders the supplied Security Shroud helmet OBJ on the player's head. The OBJ is converted from
 * a Blockbench Java Block/Item model; all of its faces are axis-aligned boxes with their original
 * per-face UVs.
 */
@SideOnly(Side.CLIENT)
public final class ModelSecurityShroudHelmet extends ModelBiped {

    private static final ResourceLocation MODEL = new ResourceLocation(
            NTMDoors.MODID, "models/armor/security_shroud_helmet.obj");
    private static final String GROUP = "helmet";
    private static final float DEG_TO_RAD = 0.017453292F;

    public static final ModelSecurityShroudHelmet INSTANCE = new ModelSecurityShroudHelmet();

    private ModelSecurityShroudHelmet() {
        super(0.0F, 0.0F, 64, 64);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // Armor layers call render() directly, so the rotation pass ModelBiped.render normally
        // performs has to run here or the head stays in its rest pose.
        // Keep the sneak state explicit; armor-layer callers are not required to copy it.
        boolean sneaking = entity instanceof EntityLivingBase
                && ((EntityLivingBase) entity).isSneaking();
        this.isSneak = sneaking;
        if (entity instanceof EntityArmorStand) {
            applyArmorStandPose((EntityArmorStand) entity);
        } else {
            setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                    scale, entity);
        }

        ObjModel model = ObjModel.get(MODEL);
        if (model == null) return;

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.pushMatrix();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        // The armor texture is opaque; use the default texture modulation instead of inheriting a
        // hurt/brightness combine state, and leave the neutral state behind for later layers.
        GlStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.isChild) {
            GlStateManager.scale(0.75F, 0.75F, 0.75F);
            GlStateManager.translate(0.0F, 16.0F * scale, 0.0F);
        } else if (sneaking) {
            // ModelBiped.render applies this 0.2-block sneak translation to the main player model.
            // Armor layers call render() directly, so the armor must apply it too. It is in blocks,
            // not model pixels: do not multiply by scale, and apply it before the head pivot.
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        if (!this.bipedHead.showModel || this.bipedHead.isHidden) return;
        bipedHead.postRender(scale);
        // The OBJ vertices are already in the head's Y-down space.
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.enableCull();
        model.draw(GROUP);
        if (!cull) GlStateManager.disableCull();
        GlStateManager.popMatrix();
    }

    private void applyArmorStandPose(EntityArmorStand stand) {
        copyRotation(stand.getHeadRotation(), this.bipedHead);
        this.bipedHead.setRotationPoint(0.0F, 1.0F, 0.0F);
    }

    private static void copyRotation(Rotations rotations, ModelRenderer part) {
        part.rotateAngleX = rotations.getX() * DEG_TO_RAD;
        part.rotateAngleY = rotations.getY() * DEG_TO_RAD;
        part.rotateAngleZ = rotations.getZ() * DEG_TO_RAD;
    }
}
