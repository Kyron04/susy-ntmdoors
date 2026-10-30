package com.ntmdoors.client;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

/**
 * Read-only card viewer. Opened by sneak right-clicking an Operator Keycard, a coloured RFID card
 * or the Creative Keycard, it lists every private key stored on the card. Long lists scroll with the
 * wheel, the arrow keys or the scrollbar.
 */
@SideOnly(Side.CLIENT)
public class GuiKeycardViewer extends GuiScreen {

    private static final int PANEL_WIDTH = 252;
    private static final int PANEL_HEIGHT = 178;
    private static final int LIST_TOP = 46;
    private static final int LIST_HEIGHT = 110;
    private static final int ROW_HEIGHT = 22;
    private static final int SCROLLBAR_X = PANEL_WIDTH - 10;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;

    private static final String CREATIVE_ENTRY = "THE ENTIRE LIBRARY OF BABEL";

    private final ItemStack card;
    private final List<PrivateKeys.Key> keys;
    private final boolean creative;

    private int left;
    private int top;
    private int scroll;
    private boolean dragging;

    public GuiKeycardViewer(ItemStack card) {
        this.card = card.copy();
        this.creative = card.getItem() instanceof ItemCreativeKeycard;
        this.keys = PrivateKeys.get(card);
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        scroll = 0;
        dragging = false;
    }

    // ------------------------------------------------------------ content

    private int entryCount() {
        return creative ? 1 : keys.size();
    }

    private String entryName(int index) {
        return creative ? CREATIVE_ENTRY : keys.get(index).name;
    }

    private String entrySerial(int index) {
        return creative ? "" : keys.get(index).serial;
    }

    private int maxScroll() {
        return Math.max(0, entryCount() * ROW_HEIGHT - LIST_HEIGHT);
    }

    private int thumbHeight() {
        int max = maxScroll();
        if (max <= 0) return LIST_HEIGHT;
        return Math.max(16, LIST_HEIGHT * LIST_HEIGHT / (entryCount() * ROW_HEIGHT));
    }

    private void scrollBy(int delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll + delta));
    }

    // ------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollBy(wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_UP) {
            scrollBy(-ROW_HEIGHT);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            scrollBy(ROW_HEIGHT);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollBy(-LIST_HEIGHT);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollBy(LIST_HEIGHT);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && maxScroll() > 0 && inScrollbar(mouseX, mouseY)) {
            dragging = true;
            scrollToMouse(mouseY);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging) scrollToMouse(mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    private boolean inScrollbar(int mouseX, int mouseY) {
        return mouseX >= left + SCROLLBAR_X - 2 && mouseX < left + SCROLLBAR_X + SCROLLBAR_WIDTH + 2
                && mouseY >= top + LIST_TOP && mouseY < top + LIST_TOP + LIST_HEIGHT;
    }

    private void scrollToMouse(int mouseY) {
        int travel = LIST_HEIGHT - thumbHeight();
        if (travel <= 0) return;
        double fraction = (double) (mouseY - top - LIST_TOP - thumbHeight() / 2) / (double) travel;
        fraction = Math.max(0D, Math.min(1D, fraction));
        scroll = (int) Math.round(fraction * maxScroll());
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);

        drawString(fontRenderer, card.getDisplayName(), left + 12, top + 12, COLOUR);
        drawString(fontRenderer, creative ? "You have access to EVERYTHING!!!!!" : "You have access to",
                left + 12, top + 26, MUTED);
        drawCount();
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, 0xFF4A4A4E);

        drawList(mouseX, mouseY);
        drawScrollbar();
        drawString(fontRenderer, "ESC to close", left + 12, top + PANEL_HEIGHT - 14, MUTED);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCount() {
        if (creative || keys.isEmpty()) return;
        String count = keys.size() + (keys.size() == 1 ? " key" : " keys");
        drawString(fontRenderer, count,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(count),
                top + 12, MUTED);
    }

    private void drawList(int mouseX, int mouseY) {
        if (!creative && keys.isEmpty()) {
            drawCenteredString(fontRenderer, "no private keys detected",
                    left + PANEL_WIDTH / 2, top + LIST_TOP + 38, MUTED);
            drawCenteredString(fontRenderer, ":(", left + PANEL_WIDTH / 2, top + LIST_TOP + 52, MUTED);
            return;
        }

        beginClip(left + 8, top + LIST_TOP, PANEL_WIDTH - 16, LIST_HEIGHT);
        for (int i = 0; i < entryCount(); i++) {
            int y = top + LIST_TOP + i * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT < top + LIST_TOP || y > top + LIST_TOP + LIST_HEIGHT) continue;

            drawString(fontRenderer, entryName(i), left + 14, y + 3, 0xFFFFFFFF);
            String serial = entrySerial(i);
            if (!serial.isEmpty()) {
                drawString(fontRenderer, serial, left + 14, y + 13, 0xFFFFFFFF);
            }
        }
        endClip();
    }

    private void drawScrollbar() {
        if (maxScroll() <= 0) return;

        int x = left + SCROLLBAR_X;
        int y = top + LIST_TOP;
        drawRect(x, y, x + SCROLLBAR_WIDTH, y + LIST_HEIGHT, 0xFF1B1B1E);

        int thumb = thumbHeight();
        int travel = LIST_HEIGHT - thumb;
        int offset = maxScroll() == 0 ? 0 : (int) ((long) scroll * travel / maxScroll());
        drawRect(x, y + offset, x + SCROLLBAR_WIDTH, y + offset + thumb, 0xFFB4B4B4);
    }

    /** Restricts drawing of the scrolling list to its own box. */
    private void beginClip(int x, int y, int width, int height) {
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
