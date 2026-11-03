package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntityMotionSensor;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws the selected Motion Sensor cells as translucent blue boxes. The projection is purely a
 * client-side aid toggled with the Motion Relay Key; it is not part of the server state.
 */
@SideOnly(Side.CLIENT)
public class RenderMotionSensorArea extends TileEntitySpecialRenderer<TileEntityMotionSensor> {

    @Override
    public void render(TileEntityMotionSensor te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (!MotionSensorOverlay.isVisible(te.getPos())) return;

        List<BlockPos> cells = te.getSelectedCells();
        if (cells.isEmpty()) return;

        BlockPos origin = te.getPos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Faint blue faces.
        GlStateManager.color(0.22F, 0.52F, 1.0F, 0.16F);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        for (BlockPos cell : cells) {
            double cx = cell.getX() - origin.getX();
            double cy = cell.getY() - origin.getY();
            double cz = cell.getZ() - origin.getZ();
            addFaces(buffer, cx, cy, cz);
        }
        tessellator.draw();

        // Brighter wireframe so the individual boxes stay readable.
        GlStateManager.color(0.35F, 0.70F, 1.0F, 0.80F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        for (BlockPos cell : cells) {
            double cx = cell.getX() - origin.getX();
            double cy = cell.getY() - origin.getY();
            double cz = cell.getZ() - origin.getZ();
            addEdges(buffer, cx, cy, cz);
        }
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
    }

    private static void addFaces(BufferBuilder buffer, double x, double y, double z) {
        double x2 = x + 1, y2 = y + 1, z2 = z + 1;

        // bottom / top
        buffer.pos(x, y, z).endVertex();
        buffer.pos(x2, y, z).endVertex();
        buffer.pos(x2, y, z2).endVertex();
        buffer.pos(x, y, z2).endVertex();

        buffer.pos(x, y2, z).endVertex();
        buffer.pos(x, y2, z2).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
        buffer.pos(x2, y2, z).endVertex();

        // north / south
        buffer.pos(x, y, z).endVertex();
        buffer.pos(x, y2, z).endVertex();
        buffer.pos(x2, y2, z).endVertex();
        buffer.pos(x2, y, z).endVertex();

        buffer.pos(x, y, z2).endVertex();
        buffer.pos(x2, y, z2).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
        buffer.pos(x, y2, z2).endVertex();

        // west / east
        buffer.pos(x, y, z).endVertex();
        buffer.pos(x, y, z2).endVertex();
        buffer.pos(x, y2, z2).endVertex();
        buffer.pos(x, y2, z).endVertex();

        buffer.pos(x2, y, z).endVertex();
        buffer.pos(x2, y2, z).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
        buffer.pos(x2, y, z2).endVertex();
    }

    private static void addEdges(BufferBuilder buffer, double x, double y, double z) {
        double x2 = x + 1, y2 = y + 1, z2 = z + 1;

        line(buffer, x, y, z, x2, y, z);
        line(buffer, x2, y, z, x2, y, z2);
        line(buffer, x2, y, z2, x, y, z2);
        line(buffer, x, y, z2, x, y, z);

        line(buffer, x, y2, z, x2, y2, z);
        line(buffer, x2, y2, z, x2, y2, z2);
        line(buffer, x2, y2, z2, x, y2, z2);
        line(buffer, x, y2, z2, x, y2, z);

        line(buffer, x, y, z, x, y2, z);
        line(buffer, x2, y, z, x2, y2, z);
        line(buffer, x2, y, z2, x2, y2, z2);
        line(buffer, x, y, z2, x, y2, z2);
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
    }
}
