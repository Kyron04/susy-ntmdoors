package com.ntmdoors.client;

import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Shared numeric keypad screen: light metal case, black readout, twelve green keycaps and the
 * full-screen SUCCESS / FAILED card. Subclasses supply the heading, the code length, the packet
 * they send and what a successful result should open.
 */
@SideOnly(Side.CLIENT)
abstract class GuiCodePad extends GuiPanelScreen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 184;

    private static final int MUTED = 0xFFAFAFAF;
    private static final int GREEN = 0xFF45EB63;
    private static final int GREEN_LIGHT = 0xFF58FF6E;
    private static final int GREEN_TEXT = 0xFF75FF82;
    private static final int GREEN_DIM = 0xFF2E7A3C;
    protected static final int RED = 0xFFFF5A5A;

    private static final int BUTTON_WIDTH = 44;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP_X = 6;
    private static final int BUTTON_GAP_Y = 4;
    private static final int GRID_TOP = 64;

    /** A result holds on screen for a little over a second before the GUI closes. */
    private static final int RESULT_TICKS = 30;
    /** If no response arrives, close instead of leaving a dead screen open. */
    private static final int WAIT_TIMEOUT_TICKS = 100;

    private static final int KEY_CLEAR = 9;
    private static final int KEY_ZERO = 10;
    private static final int KEY_ENTER = 11;

    private static final String[] KEY_LABELS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "CLR", "0", "ENTER"
    };

    private String entry = "";
    private String status = "";
    private int statusColour = MUTED;
    private boolean waiting;
    private int waitTicks;
    private boolean showingResult;
    private boolean resultSuccess;
    private int resultTicks;
    /** Keeps a successful card up until the server replaces the screen; zero means use the timer. */
    private int holdTicks;

    /** Digits accepted by the screen, which is also the length of the empty readout. */
    protected abstract int codeLength();

    protected abstract String heading();

    protected abstract String prompt();

    protected abstract void sendCode(String code);

    protected abstract boolean isSuccess(int result);

    /** Ticks to hold a successful card before closing; zero keeps the normal result timer. */
    protected int successHoldTicks() {
        return 0;
    }

    /** Called once the result card has run its course. */
    protected void finishResult(boolean success) {
        mc.displayGuiScreen(null);
    }

    protected final void showStatus(String text, int colour) {
        status = text;
        statusColour = colour;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (showingResult) {
            if (resultSuccess && holdTicks > 0) {
                if (--holdTicks <= 0) finishResult(true);
            } else if (--resultTicks <= 0) {
                finishResult(resultSuccess);
            }
            return;
        }
        if (waiting && ++waitTicks > WAIT_TIMEOUT_TICKS) {
            waiting = false;
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected int panelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int panelHeight() {
        return PANEL_HEIGHT;
    }

    // ------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (showingResult) return;
        int key = keyAt(mouseX, mouseY);
        if (key >= 0 && mouseButton == 0) {
            press(key);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (waiting || showingResult) return;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submit();
            return;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            if (!entry.isEmpty()) entry = entry.substring(0, entry.length() - 1);
            return;
        }
        if (typedChar >= '0' && typedChar <= '9') {
            append(typedChar);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void press(int key) {
        if (waiting) return;
        clickSound();
        if (key == KEY_CLEAR) {
            entry = "";
            clearStatus();
        } else if (key == KEY_ENTER) {
            submit();
        } else {
            append((char) ('0' + (key == KEY_ZERO ? 0 : key + 1)));
        }
    }

    private void append(char digit) {
        if (entry.length() >= codeLength()) return;
        entry += digit;
        clearStatus();
    }

    private void clearStatus() {
        status = "";
        statusColour = MUTED;
    }

    private void submit() {
        if (waiting) return;
        if (entry.isEmpty()) {
            showStatus("Enter a code", RED);
            return;
        }

        waiting = true;
        waitTicks = 0;
        showStatus("Checking...", GREEN_TEXT);
        sendCode(entry);
    }

    /** Called by a result packet after the subclass has checked the block position. */
    protected final void showResult(boolean success) {
        waiting = false;
        waitTicks = 0;
        entry = "";
        showingResult = true;
        resultSuccess = success;
        resultTicks = RESULT_TICKS;
        holdTicks = success ? successHoldTicks() : 0;
    }

    private void clickSound() {
        if (mc.player == null) return;
        float pitch = 0.9F + (mc.world == null ? 0F : mc.world.rand.nextFloat() * 0.2F);
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.5F, pitch);
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCase();
        if (showingResult) {
            drawResult(resultSuccess);
        } else {
            drawHeading();
            drawReadout();
            drawKeys(mouseX, mouseY);
            drawCenteredString(fontRenderer, "ESC to cancel", left + PANEL_WIDTH / 2,
                    top + PANEL_HEIGHT - 14, 0xFF6A6A6A);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeading() {
        drawCenteredString(fontRenderer, heading(), left + PANEL_WIDTH / 2, top + 12, 0xFFF2F2F2);

        String line = status.isEmpty() ? prompt() : status;
        int colour = status.isEmpty() ? MUTED : statusColour;
        drawCenteredString(fontRenderer, line, left + PANEL_WIDTH / 2, top + 26, colour);
    }

    private void drawReadout() {
        int x = left + (PANEL_WIDTH - 96) / 2;
        int y = top + 38;

        drawRect(x - 2, y - 2, x + 98, y + 20, 0xFF080808);
        drawRect(x - 1, y - 1, x + 97, y + 19, GREEN);
        drawRect(x, y, x + 96, y + 18, 0xFF0B2A12);
        drawRect(x, y, x + 96, y + 1, GREEN_LIGHT);

        String shown = entry.isEmpty() ? emptyReadout() : entry;
        int colour = entry.isEmpty() ? GREEN_DIM : GREEN_TEXT;
        drawCenteredString(fontRenderer, shown, x + 48, y + 5, colour);
    }

    private String emptyReadout() {
        StringBuilder dashes = new StringBuilder(codeLength());
        for (int i = 0; i < codeLength(); i++) dashes.append('-');
        return dashes.toString();
    }

    private void drawKeys(int mouseX, int mouseY) {
        for (int i = 0; i < KEY_LABELS.length; i++) {
            int x = keyX(i);
            int y = keyY(i);
            boolean hovered = !waiting && mouseX >= x && mouseY >= y
                    && mouseX < x + BUTTON_WIDTH && mouseY < y + BUTTON_HEIGHT;
            drawKey(x, y, KEY_LABELS[i], hovered);
        }
    }

    private void drawKey(int x, int y, String label, boolean hovered) {
        drawRect(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, 0xFF080808);
        drawRect(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1,
                hovered ? 0xFF7CFF8D : GREEN);
        drawRect(x + 3, y + 3, x + BUTTON_WIDTH - 3, y + BUTTON_HEIGHT - 3,
                hovered ? 0xFF15461E : 0xFF0B2A12);
        drawRect(x + 3, y + 3, x + BUTTON_WIDTH - 3, y + 4, GREEN_LIGHT);

        int colour = hovered ? 0xFFB6FFBC : GREEN_TEXT;
        int textX = x + (BUTTON_WIDTH - fontRenderer.getStringWidth(label)) / 2;
        fontRenderer.drawString(label, textX, y + (BUTTON_HEIGHT - 8) / 2, colour);
    }

    private int keyX(int index) {
        int gridWidth = BUTTON_WIDTH * 3 + BUTTON_GAP_X * 2;
        return left + (PANEL_WIDTH - gridWidth) / 2 + (index % 3) * (BUTTON_WIDTH + BUTTON_GAP_X);
    }

    private int keyY(int index) {
        return top + GRID_TOP + (index / 3) * (BUTTON_HEIGHT + BUTTON_GAP_Y);
    }

    private int keyAt(int mouseX, int mouseY) {
        for (int i = 0; i < KEY_LABELS.length; i++) {
            int x = keyX(i);
            int y = keyY(i);
            if (mouseX >= x && mouseX < x + BUTTON_WIDTH
                    && mouseY >= y && mouseY < y + BUTTON_HEIGHT) {
                return i;
            }
        }
        return -1;
    }
}
