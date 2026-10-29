package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Draws the scanner OBJ for the inventory, hand and ground item forms, so it sits at the same
 * three-dimensional angle as the doors and other block items instead of being a flat sprite.
 */
@SideOnly(Side.CLIENT)
public class RenderKeycardScannerItem extends TileEntityItemStackRenderer {

    public static final RenderKeycardScannerItem INSTANCE = new RenderKeycardScannerItem();

    /**
     * Longest side of the item, in blocks. 1.0 matches a vanilla block item, i.e. 16 pixels in an
     * inventory slot; the model itself is 0.75 x 1.0 x 0.3 in OBJ units.
     */
    private static final float ITEM_SIZE = 1.0F;

    /** Logged once so a blank item can be told apart from a renderer that never ran. */
    private static boolean reported;

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        ObjModel model = ObjModel.get(RenderKeycardScanner.MODEL);
        if (model == null) return;

        float sizeX = model.maxX() - model.minX();
        float sizeY = model.maxY() - model.minY();
        float sizeZ = model.maxZ() - model.minZ();
        float size = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (size <= 0F) return;

        float scale = ITEM_SIZE / size;
        float cx = (model.minX() + model.maxX()) * 0.5F;
        float cy = (model.minY() + model.maxY()) * 0.5F;
        float cz = (model.minZ() + model.maxZ()) * 0.5F;

        // Item previews should not depend on the light where the player happens to stand.
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (lighting) GlStateManager.disableLighting();
        if (cull) GlStateManager.disableCull();

        GlStateManager.pushMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // RenderItem translates items by (-0.5, -0.5, -0.5). Centre the scanner on the item origin.
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(-cx, -cy, -cz);

        Minecraft.getMinecraft().getTextureManager().bindTexture(RenderKeycardScanner.TEXTURE);
        if (!reported) {
            reported = true;
            NTMDoors.LOGGER.info("Keycard Scanner item renderer ran: {} groups, scale {}", 
                    RenderKeycardScanner.GROUPS.length, scale);
        }
        for (String group : RenderKeycardScanner.GROUPS) model.draw(group);
        model.draw(RenderKeycardScanner.SECURITY_GROUP);

        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();

        if (cull) GlStateManager.enableCull();
        if (lighting) GlStateManager.enableLighting();
    }
}
