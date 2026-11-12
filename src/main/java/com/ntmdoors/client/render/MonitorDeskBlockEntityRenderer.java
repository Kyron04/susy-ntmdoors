package com.ntmdoors.client.render;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.client.display.DisplayImages;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.util.Facings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * World-space CRT screen BlockEntityRenderer for Monitor Desks.
 * Renders physical 4:3 CRT monitors with authentic inward angles (-22.5°/0°/+22.5°),
 * dark glass, amber bezel borders, scanlines, and sinusoidal phosphor glow.
 */
@Environment(EnvType.CLIENT)
public class MonitorDeskBlockEntityRenderer implements BlockEntityRenderer<MonitorDeskBlockEntity> {

    public static final int GUI_WIDTH = 512;
    public static final int GUI_HEIGHT = 384;

    private static final Identifier WHITE_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");

    public MonitorDeskBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    private static final class ScreenDef {
        final BlockPos pos;
        final Direction facing;
        final int screenIndex;
        final float minX, maxX, minY, maxY, z;
        final float originX, originZ, angle;

        ScreenDef(BlockPos pos, Direction facing, int screenIndex,
                  float minX, float maxX, float minY, float maxY, float z,
                  float originX, float originZ, float angle) {
            this.pos = pos;
            this.facing = facing;
            this.screenIndex = screenIndex;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.z = z;
            this.originX = originX;
            this.originZ = originZ;
            this.angle = angle;
        }
    }

    @Override
    public void render(MonitorDeskBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity.getWorld() == null) return;
        BlockState coreState = entity.getCachedState();
        List<ScreenDef> screens = collectScreens(entity, coreState);
        if (screens.isEmpty()) return;

        long worldTime = entity.getWorld().getTime();
        float glow = 0.97F + 0.03F * (float) Math.sin((worldTime + tickDelta) * 0.25F);

        for (ScreenDef screen : screens) {
            renderScreen(entity, screen, glow, tickDelta, matrices, vertexConsumers);
        }
    }

    private List<ScreenDef> collectScreens(MonitorDeskBlockEntity te, BlockState coreState) {
        List<ScreenDef> screens = new ArrayList<>();
        Block coreBlock = coreState.getBlock();
        if (te.getWorld() == null) return screens;

        if (coreBlock instanceof BlockMonitorSetup setup) {
            if (setup.layer() != BlockMonitorSetup.Layer.BOTTOM) return screens;
            Direction facing = coreState.get(BlockMonitorSetup.FACING);
            for (BlockMonitorSetup.Layer layer : new BlockMonitorSetup.Layer[]{
                    BlockMonitorSetup.Layer.MIDDLE, BlockMonitorSetup.Layer.TOP}) {
                for (BlockMonitorSetup.Column column : BlockMonitorSetup.Column.values()) {
                    BlockPos pos = te.getPos().add(BlockMonitorSetup.offset(layer, column, facing));
                    BlockState state = te.getWorld().getBlockState(pos);
                    if (!(state.getBlock() instanceof BlockMonitorSetup block)) continue;
                    if (block.layer() != layer || state.get(BlockMonitorSetup.FACING) != facing
                            || state.get(BlockMonitorSetup.COLUMN) != column) {
                        continue;
                    }
                    ScreenDef s = setupScreen(pos, facing, layer, column);
                    if (s != null) screens.add(s);
                }
            }
            return screens;
        }

        if (coreBlock instanceof BlockMonitorDesk coreDesk) {
            if (coreDesk.layer() != BlockMonitorSetup.Layer.BOTTOM) return screens;
            Direction facing = coreState.get(BlockMonitorDesk.FACING);
            for (BlockMonitorDesk b : coreDesk.desk().blocks()) {
                if (b.layer() != BlockMonitorSetup.Layer.MIDDLE) continue;
                for (BlockMonitorSetup.Column col : b.columns()) {
                    BlockPos pos = te.getPos().add(coreDesk.desk().offset(b, col, facing));
                    BlockState state = te.getWorld().getBlockState(pos);
                    if (!state.isOf(b)) continue;
                    if (state.get(b.columnProperty()) != col) continue;
                    if (state.get(BlockMonitorDesk.FACING) != facing) continue;
                    ScreenDef s = smallScreen(pos, facing, b, col);
                    if (s != null) screens.add(s);
                }
            }
            return screens;
        }

        return screens;
    }

    private ScreenDef setupScreen(BlockPos pos, Direction facing,
                                  BlockMonitorSetup.Layer layer,
                                  BlockMonitorSetup.Column column) {
        float minY = (layer == BlockMonitorSetup.Layer.MIDDLE) ? 3.5F : -0.5F;
        float maxY = (layer == BlockMonitorSetup.Layer.MIDDLE) ? 12.5F : 8.5F;

        float minX, maxX, screenZ, originX, originZ, angle;
        switch (column) {
            case LEFT -> {
                minX = 4.0F; maxX = 16.0F; screenZ = 9.25F;
                originX = 10.0F; originZ = 11.55F; angle = -22.5F;
            }
            case RIGHT -> {
                minX = 0.0F; maxX = 12.0F; screenZ = 9.25F;
                originX = 6.0F; originZ = 11.55F; angle = 22.5F;
            }
            default -> {
                minX = 2.0F; maxX = 14.0F; screenZ = 10.25F;
                originX = 0.0F; originZ = 0.0F; angle = 0.0F;
            }
        }
        int screenIndex = (layer == BlockMonitorSetup.Layer.TOP ? 3 : 0) + column.ordinal();
        return new ScreenDef(pos, facing, screenIndex, minX, maxX, minY, maxY, screenZ, originX, originZ, angle);
    }

    private ScreenDef smallScreen(BlockPos pos, Direction facing,
                                  BlockMonitorDesk block,
                                  BlockMonitorSetup.Column column) {
        if (block == ModBlocks.SINGLE_MONITOR_SETUP_MIDDLE) {
            if (column != BlockMonitorSetup.Column.RIGHT) return null;
            return new ScreenDef(pos, facing, 2, -1.5F, 10.5F, 3.5F, 12.5F, 10.25F, 0F, 0F, 0F);
        }
        if (block == ModBlocks.DUAL_MONITOR_SETUP_2X2_MIDDLE) {
            if (column == BlockMonitorSetup.Column.LEFT) {
                return new ScreenDef(pos, facing, 0, 4.0F, 16.0F, 3.5F, 12.5F, 9.25F, 10.0F, 11.55F, -22.5F);
            }
            if (column == BlockMonitorSetup.Column.RIGHT) {
                return new ScreenDef(pos, facing, 1, 2.0F, 14.0F, 3.5F, 12.5F, 10.25F, 0F, 0F, 0F);
            }
            return null;
        }

        float minX, maxX, screenZ, originX, originZ, angle;
        switch (column) {
            case LEFT -> {
                minX = 4.0F; maxX = 16.0F; screenZ = 9.25F;
                originX = 10.0F; originZ = 11.55F; angle = -22.5F;
            }
            case RIGHT -> {
                minX = 0.0F; maxX = 12.0F; screenZ = 9.25F;
                originX = 6.0F; originZ = 11.55F; angle = 22.5F;
            }
            default -> {
                minX = 2.0F; maxX = 14.0F; screenZ = 10.25F;
                originX = 0.0F; originZ = 0.0F; angle = 0.0F;
            }
        }
        return new ScreenDef(pos, facing, column.ordinal(), minX, maxX, 3.5F, 12.5F, screenZ, originX, originZ, angle);
    }

    private void renderScreen(MonitorDeskBlockEntity te, ScreenDef screen, float glow, float tickDelta,
                              MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        matrices.push();

        // Translate relative to core pos
        double cellX = screen.pos.getX() - te.getPos().getX();
        double cellY = screen.pos.getY() - te.getPos().getY();
        double cellZ = screen.pos.getZ() - te.getPos().getZ();

        matrices.translate(cellX + 0.5, cellY, cellZ + 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(Facings.yaw(screen.facing)));

        if (screen.angle != 0.0F) {
            double ox = screen.originX / 16.0 - 0.5;
            double oz = screen.originZ / 16.0 - 0.5;
            matrices.translate(ox, 0.0, oz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(screen.angle));
            matrices.translate(-ox, 0.0, -oz);
        }

        // Screen geometry bounds in block units
        float left = screen.minX / 16.0F - 0.5F;
        float right = screen.maxX / 16.0F - 0.5F;
        float bottom = screen.minY / 16.0F;
        float top = screen.maxY / 16.0F;
        float z = screen.z / 16.0F - 0.5F - 0.009F;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 1. CRT Background Quad
        VertexConsumer bgConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(WHITE_TEXTURE));
        float bgR = 0.015F * glow;
        float bgG = 0.015F * glow;
        float bgB = 0.015F * glow;
        drawQuad(matrix, bgConsumer, left, right, bottom, top, z, bgR, bgG, bgB, 1.0F, 0xF000F0);

        // 2. Amber Bezel Border (0.008 thick)
        float b = 0.008F;
        float borderZ = z - 0.0005F;
        float aR = 0.50F * glow;
        float aG = 0.27F * glow;
        float aB = 0.00F * glow;
        drawQuad(matrix, bgConsumer, left, right, top - b, top, borderZ, aR, aG, aB, 1.0F, 0xF000F0);
        drawQuad(matrix, bgConsumer, left, right, bottom, bottom + b, borderZ, aR, aG, aB, 1.0F, 0xF000F0);
        drawQuad(matrix, bgConsumer, left, left + b, bottom, top, borderZ, aR, aG, aB, 1.0F, 0xF000F0);
        drawQuad(matrix, bgConsumer, right - b, right, bottom, top, borderZ, aR, aG, aB, 1.0F, 0xF000F0);

        // 3. Wallpaper or Terminal UI
        String wallpaper = te.getScreenWallpaper(screen.screenIndex);
        if (!wallpaper.isEmpty() && DisplayImages.isReady(wallpaper)) {
            Identifier texture = DisplayImages.textureFor(wallpaper);
            if (texture != null) {
                VertexConsumer texConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(texture));
                drawTexturedQuad(matrix, texConsumer, left, right, bottom, top, z - 0.001F, 1.0F, 1.0F, 1.0F, 1.0F, 0xF000F0);
            }
        } else {
            // Draw Terminal CRT Text in Amber
            matrices.push();
            matrices.translate(right, top, z - 0.001F);
            matrices.scale(-(right - left) / GUI_WIDTH, -(top - bottom) / GUI_HEIGHT, 1.0F);

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            if (tr != null) {
                int textColor = 0xFFFF8A00;
                String header = "NTM-OS v2.4 (TERMINAL " + (screen.screenIndex + 1) + ")";
                tr.draw(header, 20, 30, textColor, false, matrices.peek().getPositionMatrix(),
                        vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);

                int netId = te.getScreenNetId(screen.screenIndex);
                String netName = netId >= 0 ? "NET #" + (netId + 1) : "SYSTEM READY";
                tr.draw("STATUS: " + netName, 20, 50, 0xFFFFB454, false, matrices.peek().getPositionMatrix(),
                        vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);

                tr.draw("NETS: " + te.getNets().size() + " | NOTES: " + te.getNotepadDocuments().size(),
                        20, 70, 0xFF7A3D00, false, matrices.peek().getPositionMatrix(),
                        vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
            }
            matrices.pop();
        }

        matrices.pop();
    }

    private static void drawQuad(Matrix4f matrix, VertexConsumer consumer,
                                 float x1, float x2, float y1, float y2, float z,
                                 float r, float g, float b, float a, int light) {
        consumer.vertex(matrix, x1, y1, z).color(r, g, b, a).texture(0F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x2, y1, z).color(r, g, b, a).texture(0F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x2, y2, z).color(r, g, b, a).texture(0F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x1, y2, z).color(r, g, b, a).texture(0F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
    }

    private static void drawTexturedQuad(Matrix4f matrix, VertexConsumer consumer,
                                         float x1, float x2, float y1, float y2, float z,
                                         float r, float g, float b, float a, int light) {
        // Reverse U axis because local +X is viewer's left
        consumer.vertex(matrix, x1, y1, z).color(r, g, b, a).texture(1F, 1F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x2, y1, z).color(r, g, b, a).texture(0F, 1F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x2, y2, z).color(r, g, b, a).texture(0F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
        consumer.vertex(matrix, x1, y2, z).color(r, g, b, a).texture(1F, 0F).overlay(0, 10).light(light).normal(0F, 0F, -1F).next();
    }
}
