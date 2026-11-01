package com.ntmdoors.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Shared light-metal case and full-screen result card used by the keypad, scanner code and
 * biometric safe screens.
 */
@SideOnly(Side.CLIENT)
abstract class GuiPanelScreen extends GuiScreen {

    protected int left;
    protected int top;

    protected abstract int panelWidth();

    protected abstract int panelHeight();

    @Override
    public void initGui() {
        super.initGui();
        left = (width - panelWidth()) / 2;
        top = (height - panelHeight()) / 2;
    }

    /** Hard shadow, stepped light metal bezel and the black screen inside it. */
    protected void drawCase() {
        int w = panelWidth();
        int h = panelHeight();

        drawRect(left - 2, top - 2, left + w + 2, top + h + 2, 0xFF555555);
        drawRect(left - 1, top - 1, left + w + 1, top + h + 1, 0xFF777777);
        drawRect(left, top, left + w, top + h, 0xFFC8C8C8);
        drawRect(left + 1, top + 1, left + w - 1, top + 2, 0xFFF1F1F1);
        drawRect(left + 1, top + 1, left + 2, top + h - 1, 0xFFF1F1F1);
        drawRect(left + 1, top + h - 2, left + w - 1, top + h - 1, 0xFF8A8A8A);
        drawRect(left + w - 2, top + 1, left + w - 1, top + h - 1, 0xFF8A8A8A);

        drawRect(left + 4, top + 4, left + w - 4, top + h - 4, 0xFFF1F1F1);
        drawRect(left + 5, top + 5, left + w - 5, top + h - 5, 0xFF090909);
    }

    /** The big glowing verdict from the mockups: only the case, the black screen and the word. */
    protected void drawResult(boolean success) {
        String text = success ? "SUCCESS!!!" : "FAILED!!!";
        int fill = success ? 0xFF75FF82 : 0xFFFF7777;
        int glow = success ? 0x6620FF46 : 0x66FF2525;
        int stroke = success ? 0xFF173D1C : 0xFF4A1111;
        int cx = left + panelWidth() / 2;
        int cy = top + panelHeight() / 2;

        // A larger, translucent copy stands in for the SVG's blur glow.
        drawBigText(text, cx, cy, 2.15F, 0, 0, glow);
        // One-pixel dark outline, as the mockups stroke the glyphs.
        drawBigText(text, cx, cy, 2F, -1, 0, stroke);
        drawBigText(text, cx, cy, 2F, 1, 0, stroke);
        drawBigText(text, cx, cy, 2F, 0, -1, stroke);
        drawBigText(text, cx, cy, 2F, 0, 1, stroke);
        drawBigText(text, cx, cy, 2F, 0, 0, fill);
    }

    private void drawBigText(String text, int cx, int cy, float scale, int dx, int dy, int colour) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx + dx, cy + dy, 0F);
        GlStateManager.scale(scale, scale, 1F);
        fontRenderer.drawString(text, -fontRenderer.getStringWidth(text) / 2, -4, colour);
        GlStateManager.popMatrix();
    }
}
