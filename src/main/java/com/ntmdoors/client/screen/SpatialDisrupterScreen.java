package com.ntmdoors.client.screen;

import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Modern Fabric 1.20.1 Client Screen for Spatial Disrupter activation.
 * 280x156 dark CRT panel with confirmation button.
 */
@Environment(EnvType.CLIENT)
public class SpatialDisrupterScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 156;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int RULE = 0xFF4A4A4E;
    private static final int WARNING = 0xFFFF6A6A;

    private final BlockPos pos;
    private ButtonWidget activateButton;

    public SpatialDisrupterScreen(BlockPos pos) {
        super(Text.literal("Spatial Disrupter"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        this.activateButton = addDrawableChild(ButtonWidget.builder(Text.literal("ACTIVATE SPATIAL DISRUPTER"), b -> {
            ModMessages.sendSpatialDisrupterActivate(pos);
            close();
        }).dimensions(left + (PANEL_WIDTH - 190) / 2, top + 74, 190, 20).build());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Outer border & dark background
        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        context.fill(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        // Header
        context.drawText(textRenderer, Text.literal("SPATIAL DISRUPTER"), left + 12, top + 8, COLOUR, false);
        String right = "ACTIVATION";
        context.drawText(textRenderer, Text.literal(right), left + PANEL_WIDTH - 12 - textRenderer.getWidth(right), top + 8, MUTED, false);

        // Description
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Weakens the structural integrity of blast-resistant blocks"),
                left + PANEL_WIDTH / 2, top + 36, COLOUR);

        // Warning Text
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("WARNING: THIS OPERATION CANNOT BE REVERSED"),
                left + PANEL_WIDTH / 2, top + 108, WARNING);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("16x16 chunks will be weakened."),
                left + PANEL_WIDTH / 2, top + 122, MUTED);

        super.render(context, mouseX, mouseY, delta);
    }
}
