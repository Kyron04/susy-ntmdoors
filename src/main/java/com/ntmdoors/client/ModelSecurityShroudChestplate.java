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
 * Renders the Security Shroud chestplate OBJ on the body and both arms. The OBJ is baked into the
 * biped's Y-down space; each part is rotated around its biped pivot.
 */
@SideOnly(Side.CLIENT)
public final class ModelSecurityShroudChestplate extends ModelBiped {

    private static final ResourceLocation MODEL = new ResourceLocation(
            NTMDoors.MODID, "models/armor/security_shroud_chestplate.obj");
    private static final float DEG_TO_RAD = 0.017453292F;

    public static final ModelSecurityShroudChestplate INSTANCE =
            new ModelSecurityShroudChestplate();

    private ModelSecurityShroudChestplate() {
        super(0.0F, 0.0F, 64, 64);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // Armor layers call render() directly, so the rotation pass ModelBiped.render normally
        // performs has to run here or the body and arms stay in their rest pose.
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
            // Body/armor child transform, not the head transform.
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(0.0F, 24.0F * scale, 0.0F);
        } else if (sneaking) {
            // ModelBiped.render applies this 0.2-block sneak translation to the main player model.
            // Armor layers call render() directly, so the chestplate must apply it too, once at the
            // root before the body/arm pivots. It is in blocks, not model pixels: do not scale it.
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        // Entity renderers disable culling; draw both sides so custom OBJ winding cannot hide a
        // face in armor-stand previews.
        GlStateManager.disableCull();
        renderPart(model, "body", this.bipedBody, scale);
        // The Blockbench source names the +X arm "arm_right"; the OBJ group is renamed
        // "left_arm" because +X is the player's left arm.
        renderPart(model, "left_arm", this.bipedLeftArm, scale);
        renderPart(model, "right_arm", this.bipedRightArm, scale);
        if (cull) GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private void applyArmorStandPose(EntityArmorStand stand) {
        copyRotation(stand.getBodyRotation(), this.bipedBody);
        copyRotation(stand.getLeftArmRotation(), this.bipedLeftArm);
        copyRotation(stand.getRightArmRotation(), this.bipedRightArm);
        // ModelBiped mutates arm pivots for sneak/ride poses. The model instances are shared
        // between player and preview renders, so restore the armor-stand pivots explicitly.
        this.bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.bipedLeftArm.setRotationPoint(5.0F, 2.0F, 0.0F);
        this.bipedRightArm.setRotationPoint(-5.0F, 2.0F, 0.0F);
        this.isChild = false;
        this.isRiding = false;
        this.isSneak = false;
    }

    private static void copyRotation(Rotations rotations, ModelRenderer part) {
        part.rotateAngleX = rotations.getX() * DEG_TO_RAD;
        part.rotateAngleY = rotations.getY() * DEG_TO_RAD;
        part.rotateAngleZ = rotations.getZ() * DEG_TO_RAD;
    }

    private static void renderPart(ObjModel model, String group, ModelRenderer part, float scale) {
        if (!part.showModel || part.isHidden) return;
        GlStateManager.pushMatrix();
        part.postRender(scale);
        // postRender rotates around the pivot but also translates to it; undo that translation so
        // the absolute OBJ vertices can be rotated around the same pivot.
        GlStateManager.translate(-part.rotationPointX * scale, -part.rotationPointY * scale,
                -part.rotationPointZ * scale);
        GlStateManager.scale(scale, scale, scale);
        model.draw(group);
        GlStateManager.popMatrix();
    }
}
