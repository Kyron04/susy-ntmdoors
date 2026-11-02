package com.ntmdoors.client;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.12.2 equivalent of HBM's ItemRenderLibraryDoors.
 *
 * HBM does not use flat PNG icons for its doors: the door item is rendered from the same OBJ model
 * that is used in the world. We do the same here, scaling the model's bounding box into one block so
 * the normal item display transforms (GUI, hand, ground, item frame) work for every door size.
 */
@SideOnly(Side.CLIENT)
public class RenderDoorItem extends TileEntityItemStackRenderer {

    public static final RenderDoorItem INSTANCE = new RenderDoorItem();
    /** Per-door transform; the OBJ bounds only change on a resource reload. */
    private static final Map<DoorDecl, ItemBounds> BOUNDS = new HashMap<>();

    /** Drops normalized item bounds after a resource reload. */
    public static void clearCache() {
        BOUNDS.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        Block block = Block.getBlockFromItem(stack.getItem());
        if (!(block instanceof BlockDoorGeneric)) return;

        DoorDecl d = ((BlockDoorGeneric) block).decl;
        if (d == null || d.model == null) return;

        ObjModel model = ObjModel.get(d.model);
        if (model == null) return;

        Collection<String> groups = d.itemGroups.isEmpty() ? model.groups() : d.itemGroups;
        ItemBounds info = BOUNDS.get(d);
        if (info == null) {
            // Most door models include huge decorative tracks. Normalise only the parts that
            // actually represent the door in the inventory so the icon remains recognisable.
            float[] b = model.bounds(groups);
            if (b == null) {
                b = new float[]{model.minX(), model.minY(), model.minZ(),
                        model.maxX(), model.maxY(), model.maxZ()};
            }
            float sizeX = b[3] - b[0];
            float sizeY = b[4] - b[1];
            float sizeZ = b[5] - b[2];
            float size = Math.max(sizeX, Math.max(sizeY, sizeZ));
            if (size <= 0F) return;
            info = new ItemBounds(1F / size,
                    (b[0] + b[3]) * 0.5F,
                    (b[1] + b[4]) * 0.5F,
                    (b[2] + b[5]) * 0.5F);
            BOUNDS.put(d, info);
        }

        // The door OBJs come from several different HBM versions and a few of them have inverted or
        // missing normals. Item lighting then renders the inventory icon almost black. Fullbright
        // here only affects the item preview; the placed TESR still uses normal world lighting.
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (lighting) GlStateManager.disableLighting();
        if (cull) GlStateManager.disableCull();

        GlStateManager.pushMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // RenderItem translates items by (-0.5, -0.5, -0.5). Put the model's centre at (0.5, 0.5, 0.5)
        // so that a door of any footprint ends up centred on the item's origin.
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        GlStateManager.scale(info.scale, info.scale, info.scale);
        GlStateManager.rotate(d.modelYaw, 0F, 1F, 0F);
        GlStateManager.translate(-info.cx, -info.cy, -info.cz);

        RenderDoor.drawParts(d, model, 0F, groups, false, 0);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        if (cull) GlStateManager.enableCull();
        if (lighting) GlStateManager.enableLighting();
    }

    private static final class ItemBounds {
        final float scale, cx, cy, cz;

        ItemBounds(float scale, float cx, float cy, float cz) {
            this.scale = scale;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }
    }
}
