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
 * Renders the Security Shroud leggings OBJ on both legs. The OBJ is baked into the biped's Y-down
 * space; each leg is rotated around its biped pivot.
 */
@SideOnly(Side.CLIENT)
public final class ModelSecurityShroudLeggings extends ModelBiped {

    private static final ResourceLocation MODEL = new ResourceLocation(
            NTMDoors.MODID, "models/armor/security_shroud_leggings.obj");
    private static final float DEG_TO_RAD = 0.017453292F;
    private static final float BAKED_LEG_PIVOT_Y = 12.0F;
    private static final float BAKED_LEG_PIVOT_Z = 0.1F;

    public static final ModelSecurityShroudLeggings INSTANCE = new ModelSecurityShroudLeggings();

    private ModelSecurityShroudLeggings() {
        super(0.0F, 0.0F, 64, 64);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {
        // Armor layers call render() directly, so the rotation pass ModelBiped.render normally
        // performs has to run here or the legs stay in their rest pose.
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
            // Armor layers call render() directly, so the leggings must apply it too, once at the
            // root before the leg pivots. It is in blocks, not model pixels: do not scale it.
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        // Entity renderers disable culling; draw both sides so custom OBJ winding cannot hide a
        // face in armor-stand previews.
        GlStateManager.disableCull();
        renderPart(model, "left_leg", this.bipedLeftLeg, scale);
        renderPart(model, "right_leg", this.bipedRightLeg, scale);
        if (cull) GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private void applyArmorStandPose(EntityArmorStand stand) {
        copyRotation(stand.getLeftLegRotation(), this.bipedLeftLeg);
        this.bipedLeftLeg.setRotationPoint(1.9F, 11.0F, 0.0F);
        copyRotation(stand.getRightLegRotation(), this.bipedRightLeg);
        this.bipedRightLeg.setRotationPoint(-1.9F, 11.0F, 0.0F);
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
        // The OBJ is baked around the standing leg pivot (y=12, z=0.1). ModelBiped moves the
        // pivot in sneak, and ModelArmorStandArmor uses y=11, z=0; apply that delta before
        // undoing the current pivot so the baked vertices rotate around the live pivot.
        GlStateManager.translate(0.0F,
                (part.rotationPointY - BAKED_LEG_PIVOT_Y) * scale,
                (part.rotationPointZ - BAKED_LEG_PIVOT_Z) * scale);
        // postRender rotates around the pivot but also translates to it; undo that translation so
        // the absolute OBJ vertices can be rotated around the same pivot.
        GlStateManager.translate(-part.rotationPointX * scale, -part.rotationPointY * scale,
                -part.rotationPointZ * scale);
        GlStateManager.scale(scale, scale, scale);
        model.draw(group);
        GlStateManager.popMatrix();
    }
}
