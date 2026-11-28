// SPDX-License-Identifier: LGPL-3.0-only
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
 * Device-to-gate links are blue, gate-to-gate links are red, door targets are amber, grill
 * targets are cyan and speaker targets are green. The overlay ignores depth so wiring is visible
 * through walls.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class RedstoneMonocleOverlay {

    private static final float[] GATE_GATE = {0.95F, 0.20F, 0.20F, 0.85F};
    private static final float[] TARGET_DOOR = {1.00F, 0.72F, 0.18F, 0.85F};
    private static final float[] TARGET_GRILL = {0.35F, 0.85F, 1.00F, 0.85F};
    private static final float[] TARGET_SPEAKER = {0.55F, 0.95F, 0.45F, 0.85F};
    private static final float[] DEVICE_GATE = {0.25F, 0.62F, 1.00F, 0.80F};

    /** How far away a link may be drawn. This only limits the overlay, not the link itself. */
    private static final int LINK_RANGE_CHUNKS = 4;
    private static final double LINK_RANGE_SQ =
            (LINK_RANGE_CHUNKS * 16.0D) * (LINK_RANGE_CHUNKS * 16.0D);

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
        List<Link> targetDoor = new ArrayList<>();
        List<Link> targetGrill = new ArrayList<>();
        List<Link> targetSpeaker = new ArrayList<>();
        List<Link> deviceGate = new ArrayList<>();

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof RedstoneBindingHost)) continue;
            BlockPos hostPos = te.getPos();
            if (!inRange(player, hostPos)) continue;

            RedstoneBinding binding = ((RedstoneBindingHost) te).getRedstoneBinding();
            boolean gateHost = te instanceof TileEntityRedstoneGate;
            for (BlockPos target : binding.getGates()) {
                if (!inRange(player, target)) continue;
                (gateHost ? gateGate : deviceGate).add(new Link(hostPos, target));
            }
            for (BlockPos target : binding.getDoors()) {
                if (inRange(player, target)) targetDoor.add(new Link(hostPos, target));
            }
            for (BlockPos target : binding.getGrills()) {
                if (inRange(player, target)) targetGrill.add(new Link(hostPos, target));
            }
            for (BlockPos target : binding.getSpeakers()) {
                if (inRange(player, target)) targetSpeaker.add(new Link(hostPos, target));
            }
        }

        if (gateGate.isEmpty() && targetDoor.isEmpty() && targetGrill.isEmpty()
                && targetSpeaker.isEmpty() && deviceGate.isEmpty()) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        // This event runs after the world is drawn, with lighting and fog already disabled.
        // Do not touch those states: an exception or a missed restore here would leak a global
        // GL state into the next frame and corrupt chunk rendering. Depth writes are already
        // disabled while the depth test is off, so there is no need to change the depth mask.
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY,
                    -renderManager.viewerPosZ);
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.disableCull();
            // The monocle is a diagnostic overlay: keep the links visible through walls.
            GlStateManager.disableDepth();
            GL11.glLineWidth(2.0F);

            draw(gateGate, GATE_GATE);
            draw(targetDoor, TARGET_DOOR);
            draw(targetGrill, TARGET_GRILL);
            draw(targetSpeaker, TARGET_SPEAKER);
            draw(deviceGate, DEVICE_GATE);
        } finally {
            // Always restore, even if a draw call throws. A leaked depth test or texture state
            // is what turns this overlay into missing-chunk-looking visual corruption.
            GL11.glLineWidth(1.0F);
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.popMatrix();
        }
    }

    /**
     * True when a link endpoint is close enough to draw. Both ends of a link are checked so the
     * overlay never reveals a line stretching beyond the four-chunk limit.
     */
    private static boolean inRange(EntityPlayer player, BlockPos pos) {
        return player.getDistanceSq(pos) <= LINK_RANGE_SQ;
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
