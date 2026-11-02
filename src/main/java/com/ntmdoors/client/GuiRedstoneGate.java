package com.ntmdoors.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Redstone Gate screen. It uses the same red panel as the scanner's event-output page, but the gate
 * has no event toggles of its own: an event on any bound safe or scanner powers the gate for twenty
 * ticks. The page is intentionally empty for now so gate-specific controls can be added later
 * without changing the panel.
 */
@SideOnly(Side.CLIENT)
public class GuiRedstoneGate extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;

    private static final int RED_PANEL = 0xFF2A0F12;
    private static final int RED_RULE = 0xFF6A2A2A;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;

    private final BlockPos pos;

    private int left;
    private int top;

    public GuiRedstoneGate(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Match the light metal frame and dark face used by the scanner console.
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);

        drawTitle();
        drawString(fontRenderer, "EVENT OUTPUTS", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);
        drawFooter();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        drawString(fontRenderer, "REDSTONE GATE", left + 12, top + 10, COLOUR);
        String right = "WIRELESS";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);
        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawFooter() {
        drawRect(left + 12, top + 152, left + PANEL_WIDTH - 12, top + 153, RULE);
        drawCenteredString(fontRenderer, "Wireless events keep this gate powered for 20 ticks",
                left + PANEL_WIDTH / 2, top + 164, MUTED);
        drawCenteredString(fontRenderer, "1/1", left + PANEL_WIDTH / 2, top + 180, DIM);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** The gate position is kept for the controls that will fill this page later. */
    public BlockPos getPos() {
        return pos;
    }
}
