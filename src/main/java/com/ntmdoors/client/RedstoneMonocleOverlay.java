package com.ntmdoors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.item.ItemRedstoneMonocle;
import com.ntmdoors.util.RedstoneBinding;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Redstone Monocle's wireless-link overlay while the item is held in either hand.
 * Devices are drawn in blue, gate-to-gate links in red, and gate-to-door links in amber. The
 * overlay ignores depth so wiring is visible through walls.
 */
@Environment(EnvType.CLIENT)
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

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(RedstoneMonocleOverlay::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;
        if (!ItemRedstoneMonocle.isHeldBy(player)) return;

        List<Link> gateGate = new ArrayList<>();
        List<Link> gateDoor = new ArrayList<>();
        List<Link> deviceGate = new ArrayList<>();

        for (RedstoneBinding binding : RedstoneBinding.ACTIVE_BINDINGS) {
            BlockEntity be = binding.getHost();
            if (be == null || be.isRemoved() || be.getWorld() == null || !be.getWorld().isClient()) continue;
            BlockPos source = be.getPos();
            if (be instanceof RedstoneGateBlockEntity) {
                for (BlockPos target : binding.getGates()) gateGate.add(new Link(source, target));
                for (BlockPos target : binding.getDoors()) gateDoor.add(new Link(source, target));
            } else {
                for (BlockPos target : binding.getGates()) deviceGate.add(new Link(source, target));
            }
        }

        if (gateGate.isEmpty() && gateDoor.isEmpty() && deviceGate.isEmpty()) return;

        Vec3d camPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        drawLinks(buffer, posMat, gateGate, GATE_GATE, camPos);
        drawLinks(buffer, posMat, gateDoor, GATE_DOOR, camPos);
        drawLinks(buffer, posMat, deviceGate, DEVICE_GATE, camPos);

        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void drawLinks(BufferBuilder buffer, Matrix4f posMat, List<Link> links, float[] color, Vec3d camPos) {
        for (Link link : links) {
            float x1 = (float) (link.from.getX() + 0.5 - camPos.x);
            float y1 = (float) (link.from.getY() + 0.5 - camPos.y);
            float z1 = (float) (link.from.getZ() + 0.5 - camPos.z);
            float x2 = (float) (link.to.getX() + 0.5 - camPos.x);
            float y2 = (float) (link.to.getY() + 0.5 - camPos.y);
            float z2 = (float) (link.to.getZ() + 0.5 - camPos.z);

            buffer.vertex(posMat, x1, y1, z1).color(color[0], color[1], color[2], color[3]).next();
            buffer.vertex(posMat, x2, y2, z2).color(color[0], color[1], color[2], color[3]).next();
        }
    }

    private RedstoneMonocleOverlay() {}
}
