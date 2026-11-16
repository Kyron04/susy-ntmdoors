package com.ntmdoors.client;

import com.ntmdoors.network.MessageBreakerBreak;
import com.ntmdoors.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Locale;

/** Confirmation screen for the Breaker Relay Key. */
@SideOnly(Side.CLIENT)
public class GuiBreakerConfirm extends GuiScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 160;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_TOP = 102;
    private static final int BUTTON_GAP = 10;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int RULE = 0xFF4A4A4E;
    private static final int BREAK_FILL = 0xFF3A2424;
    private static final int BREAK_EDGE = 0xFF8A4A4A;
    private static final int BREAK_HOVER = 0xFF5A2A2A;
    private static final int CANCEL_FILL = 0xFF2B2B2E;
    private static final int CANCEL_EDGE = 0xFF6A6A6E;
    private static final int CANCEL_HOVER = 0xFF3A3A3E;

    private final BlockPos pos;
    private final String label;
    private int left;
    private int top;

    public GuiBreakerConfirm(BlockPos pos, String label) {
        this.pos = pos;
        this.label = label == null || label.isEmpty() ? "target" : label;
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

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        drawRect(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        drawString(fontRenderer, "BREAKER RELAY KEY", left + 12, top + 10, COLOUR);
        String right = "CONFIRM";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);

        drawCenteredString(fontRenderer, "Do you want to break this "
                        + label.toLowerCase(Locale.ROOT) + "?",
                left + PANEL_WIDTH / 2, top + 48, COLOUR);
        drawCenteredString(fontRenderer, "This cannot be undone.",
                left + PANEL_WIDTH / 2, top + 66, MUTED);

        int breakLeft = left + (PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_GAP) / 2;
        int cancelLeft = breakLeft + BUTTON_WIDTH + BUTTON_GAP;
        int buttonTop = top + BUTTON_TOP;
        drawButton(breakLeft, buttonTop, "BREAK", mouseX, mouseY, true);
        drawButton(cancelLeft, buttonTop, "CANCEL", mouseX, mouseY, false);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawButton(int x, int y, String label, int mouseX, int mouseY, boolean danger) {
        boolean hover = mouseX >= x && mouseX < x + BUTTON_WIDTH
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        int fill = danger
                ? (hover ? BREAK_HOVER : BREAK_FILL)
                : (hover ? CANCEL_HOVER : CANCEL_FILL);
        int edge = danger ? BREAK_EDGE : CANCEL_EDGE;
        drawRect(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, fill);
        drawRect(x, y, x + BUTTON_WIDTH, y + 1, edge);
        drawRect(x, y + BUTTON_HEIGHT - 1, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, 0xFF151517);
        drawCenteredString(fontRenderer, label, x + BUTTON_WIDTH / 2,
                y + (BUTTON_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                hover ? 0xFFFFFFFF : COLOUR);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int breakLeft = left + (PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_GAP) / 2;
            int cancelLeft = breakLeft + BUTTON_WIDTH + BUTTON_GAP;
            int buttonTop = top + BUTTON_TOP;
            if (mouseX >= breakLeft && mouseX < breakLeft + BUTTON_WIDTH
                    && mouseY >= buttonTop && mouseY < buttonTop + BUTTON_HEIGHT) {
                ModNetwork.INSTANCE.sendToServer(new MessageBreakerBreak(pos));
                mc.displayGuiScreen(null);
                return;
            }
            if (mouseX >= cancelLeft && mouseX < cancelLeft + BUTTON_WIDTH
                    && mouseY >= buttonTop && mouseY < buttonTop + BUTTON_HEIGHT) {
                mc.displayGuiScreen(null);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
