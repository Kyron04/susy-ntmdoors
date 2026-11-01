package com.ntmdoors.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Shared light-metal styling for the mod's container screens: the casing, slot frames, right
 * aligned labels, flat ghost tooltips and the GL reset needed after a tooltip.
 */
@SideOnly(Side.CLIENT)
final class GuiContainerStyle {

    /** Outer shadow, stepped metal casing and the pale title strip. */
    static void drawCasing(int xSize, int ySize) {
        Gui.drawRect(0, 0, xSize, ySize, 0xFF101012);
        Gui.drawRect(2, 2, xSize - 2, ySize - 2, 0xFFC8C8C8);
        Gui.drawRect(3, 3, xSize - 3, 4, 0xFFECECEC);
        Gui.drawRect(3, 3, 4, ySize - 3, 0xFFECECEC);
        Gui.drawRect(3, ySize - 4, xSize - 3, ySize - 3, 0xFF8A8A8A);
        Gui.drawRect(xSize - 4, 3, xSize - 3, ySize - 3, 0xFF8A8A8A);

        Gui.drawRect(4, 4, xSize - 4, 20, 0xFFD4D4D4);
        Gui.drawRect(4, 19, xSize - 4, 20, 0xFF9A9A9A);
    }

    /** The beveled frame around one inventory slot. */
    static void drawSlotFrame(int x, int y) {
        Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0xFFB8B8B8);
        Gui.drawRect(x, y, x + 16, y + 16, 0xFF343437);
        Gui.drawRect(x, y, x + 16, y + 1, 0xFF1F1F22);
    }

    /**
     * Restores the GL state a container GUI expects after a tooltip has been drawn. Mirrors what
     * GuiUtils.drawHoveringText switches on but never switches back off.
     */
    static void resetGuiState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    static void drawRightAligned(FontRenderer font, String text, int right, int y, int colour) {
        font.drawString(text, right - font.getStringWidth(text), y, colour);
    }

    /** Flat tooltip for empty "ghost" slots, which have no ItemStack to hand to vanilla. */
    static void drawSimpleTooltip(FontRenderer font, List<String> lines, int mouseX, int mouseY,
                                  int screenWidth, int screenHeight) {
        if (lines.isEmpty()) return;

        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.getStringWidth(line));
        }
        int boxWidth = textWidth + 4;
        int boxHeight = lines.size() * 10 + 4;

        int x = mouseX + 8;
        int y = mouseY - 8;
        // Prefer the left side near the screen edge, keeping clear of JEI/HEI's right-hand list.
        if (x + boxWidth + 4 > screenWidth) {
            x = mouseX - boxWidth - 8;
        }
        x = Math.max(2, x);
        if (x + boxWidth + 4 > screenWidth) x = screenWidth - boxWidth - 4;
        if (y + boxHeight + 4 > screenHeight) y = screenHeight - boxHeight - 4;
        y = Math.max(2, y);

        Gui.drawRect(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, 0xFF08080A);
        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, 0xFF1B1B1E);
        Gui.drawRect(x, y, x + boxWidth, y + 1, 0xFF4A4A4E);

        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x + 2, y + 2 + i * 10, 0xFFE8E8E8);
        }
    }

    /** Draws a pre-greyed 16x16 silhouette as flat rectangles. */
    static void drawGhostPixels(int[] pixels, int x, int y) {
        for (int row = 0; row < 16; row++) {
            int runStart = -1;
            int runColour = 0;
            for (int col = 0; col < 16; col++) {
                int colour = pixels[row * 16 + col];
                if (colour == 0) {
                    if (runStart >= 0) {
                        Gui.drawRect(x + runStart, y + row, x + col, y + row + 1, runColour);
                        runStart = -1;
                    }
                } else if (runStart < 0) {
                    runStart = col;
                    runColour = colour;
                } else if (colour != runColour) {
                    Gui.drawRect(x + runStart, y + row, x + col, y + row + 1, runColour);
                    runStart = col;
                    runColour = colour;
                }
            }
            if (runStart >= 0) {
                Gui.drawRect(x + runStart, y + row, x + 16, y + row + 1, runColour);
            }
        }
    }

    private GuiContainerStyle() {
    }
}
