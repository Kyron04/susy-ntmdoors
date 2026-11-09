package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemRedstoneMonocle;
import com.ntmdoors.tile.RedstoneBinding;
import com.ntmdoors.tile.RedstoneBindingHost;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Redstone Monocle's wireless-link overlay while the item is held in either hand.
 * Devices are drawn in blue, gate-to-gate links in red, and gate-to-door links in amber. The
 * overlay ignores depth so wiring is visible through walls.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class RedstoneMonocleOverlay {

    private static final float[] GATE_GATE = {0.95F, 0.20F, 0.20F, 0.85F};
    private static final float[] GATE_DOOR = {1.00F, 0.72F, 0.18F, 0.85F};
    private static final float[] DEVICE_GATE = {0.25F, 0.62F, 1.00F, 0.80F};

    private static final class Link {
        final BlockPos from;
        final BlockPos to;

        Link(BlockPos from, BlockPos to) {
            this.from = from;
            this.to = to;
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;
        if (!ItemRedstoneMonocle.isHeldBy(player)) return;

        List<Link> gateGate = new ArrayList<>();
        List<Link> gateDoor = new ArrayList<>();
        List<Link> deviceGate = new ArrayList<>();

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof RedstoneBindingHost)) continue;
            if (te instanceof TileEntityRedstoneGate) {
                RedstoneBinding binding = ((TileEntityRedstoneGate) te).getRedstoneBinding();
                BlockPos gate = te.getPos();
                for (BlockPos target : binding.getGates()) gateGate.add(new Link(gate, target));
                for (BlockPos target : binding.getDoors()) gateDoor.add(new Link(gate, target));
            } else {
                RedstoneBinding binding = ((RedstoneBindingHost) te).getRedstoneBinding();
                BlockPos device = te.getPos();
                for (BlockPos target : binding.getGates()) deviceGate.add(new Link(device, target));
            }
        }

        if (gateGate.isEmpty() && gateDoor.isEmpty() && deviceGate.isEmpty()) return;

        RenderManager renderManager = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY,
                -renderManager.viewerPosZ);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.depthMask(false);
        // The monocle is a diagnostic overlay: keep the links visible through walls.
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.0F);

        draw(gateGate, GATE_GATE);
        draw(gateDoor, GATE_DOOR);
        draw(deviceGate, DEVICE_GATE);

        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableFog();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
    }

    private static void draw(List<Link> links, float[] colour) {
        if (links.isEmpty()) return;
        GlStateManager.color(colour[0], colour[1], colour[2], colour[3]);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        for (Link link : links) {
            buffer.pos(link.from.getX() + 0.5D, link.from.getY() + 0.5D,
                    link.from.getZ() + 0.5D).endVertex();
            buffer.pos(link.to.getX() + 0.5D, link.to.getY() + 0.5D,
                    link.to.getZ() + 0.5D).endVertex();
        }
        tessellator.draw();
    }

    private RedstoneMonocleOverlay() {
    }
}
