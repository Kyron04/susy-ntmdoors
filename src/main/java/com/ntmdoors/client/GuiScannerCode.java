package com.ntmdoors.client;

import com.ntmdoors.network.MessageScannerCode;
import com.ntmdoors.network.MessageScannerCodeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Security code screen for a scanner that is already bound to doors. The first Operator Keycard
 * visit chooses the code; later visits must repeat it. The layout follows the supplied mockup: a
 * light metal case around a black screen, a green digital readout and twelve green keycaps.
 */
@SideOnly(Side.CLIENT)
public class GuiScannerCode extends GuiScreen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 184;

    private static final int MUTED = 0xFFAFAFAF;
    private static final int GREEN = 0xFF45EB63;
    private static final int GREEN_LIGHT = 0xFF58FF6E;
    private static final int GREEN_TEXT = 0xFF75FF82;
    private static final int GREEN_DIM = 0xFF2E7A3C;
    private static final int RED = 0xFFFF5A5A;

    private static final int BUTTON_WIDTH = 44;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP_X = 6;
    private static final int BUTTON_GAP_Y = 4;
    private static final int GRID_TOP = 64;
    /** A result holds on screen for a little over a second before the GUI closes. */
    private static final int RESULT_TICKS = 30;

    private static final String[] KEY_LABELS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "CLR", "0", "ENTER"
    };

    private final BlockPos pos;
    /** True when the scanner has no code yet, so this visit chooses one. Refreshed from the tile. */
    private boolean setting;

    private int left;
    private int top;
    private String entry = "";
    private String status = "";
    private int statusColour = MUTED;
    private boolean waiting;
    /** True while the SUCCESS!!! / FAILED!!! card is on screen. */
    private boolean showingResult;
    private boolean resultSuccess;
    private int resultTicks;

    public GuiScannerCode(BlockPos pos, boolean setting) {
        this.pos = pos;
        this.setting = setting;
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
    public void updateScreen() {
        super.updateScreen();
        // A code set by this visit or a nearby sync can land a tick after the screen opens, so
        // always trust the tile entity instead of the flag captured when the screen was built.
        if (mc.world != null) {
            TileEntity te = mc.world.getTileEntity(pos);
            if (te instanceof TileEntityKeycardScanner) {
                setting = ((TileEntityKeycardScanner) te).hasSecurityCode();
            }
        }
        if (showingResult && --resultTicks <= 0) {
            // A correct code goes on to the key assignment screen; a failure just closes.
            mc.displayGuiScreen(resultSuccess ? new GuiScannerKeys(pos) : null);
        }
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
        if (key == 9) {
            entry = "";
            clearStatus();
        } else if (key == 11) {
            submit();
        } else {
            append((char) ('0' + (key == 10 ? 0 : key + 1)));
        }
    }

    private void append(char digit) {
        if (entry.length() >= TileEntityKeycardScanner.CODE_LENGTH) return;
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
            status = "Enter a code";
            statusColour = RED;
            return;
        }

        waiting = true;
        status = "Checking...";
        statusColour = GREEN_TEXT;
        ModNetwork.INSTANCE.sendToServer(new MessageScannerCode(pos, entry));
    }

    /** Called by the result packet. Both outcomes hold a full-screen card for a moment. */
    public void onResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        waiting = false;
        entry = "";
        showingResult = true;
        resultSuccess = result == MessageScannerCodeResult.ACCEPTED
                || result == MessageScannerCodeResult.SET;
        resultTicks = RESULT_TICKS;
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
            drawResult();
        } else {
            drawHeading();
            drawReadout();
            drawKeys(mouseX, mouseY);
            drawCenteredString(fontRenderer, "ESC to cancel", left + PANEL_WIDTH / 2,
                    top + PANEL_HEIGHT - 14, 0xFF6A6A6A);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The big glowing verdict from the mockups: only the case, the black screen and the word. */
    private void drawResult() {
        String text = resultSuccess ? "SUCCESS!!!" : "FAILED!!!";
        int fill = resultSuccess ? 0xFF75FF82 : 0xFFFF7777;
        int glow = resultSuccess ? 0x6620FF46 : 0x66FF2525;
        int stroke = resultSuccess ? 0xFF173D1C : 0xFF4A1111;
        int cx = left + PANEL_WIDTH / 2;
        int cy = top + PANEL_HEIGHT / 2;

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

    private void drawCase() {
        // Hard shadow, stepped light metal bezel, then the user-supplied black screen.
        drawRect(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0xFF555555);
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF777777);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFC8C8C8);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFFF1F1F1);
        drawRect(left + 1, top + 1, left + 2, top + PANEL_HEIGHT - 1, 0xFFF1F1F1);
        drawRect(left + 1, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF8A8A8A);
        drawRect(left + PANEL_WIDTH - 2, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF8A8A8A);

        drawRect(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFFF1F1F1);
        drawRect(left + 5, top + 5, left + PANEL_WIDTH - 5, top + PANEL_HEIGHT - 5, 0xFF090909);
    }

    private void drawHeading() {
        drawCenteredString(fontRenderer, "Please input security code",
                left + PANEL_WIDTH / 2, top + 12, 0xFFF2F2F2);

        String line;
        if (!status.isEmpty()) {
            line = status;
        } else if (setting) {
            line = "Set a new security code";
        } else {
            line = "Enter your security code";
        }
        int colour = !status.isEmpty() ? statusColour : MUTED;
        drawCenteredString(fontRenderer, line, left + PANEL_WIDTH / 2, top + 26, colour);
    }

    private void drawReadout() {
        int x = left + (PANEL_WIDTH - 96) / 2;
        int y = top + 38;

        drawRect(x - 2, y - 2, x + 98, y + 20, 0xFF080808);
        drawRect(x - 1, y - 1, x + 97, y + 19, GREEN);
        drawRect(x, y, x + 96, y + 18, 0xFF0B2A12);
        drawRect(x, y, x + 96, y + 1, GREEN_LIGHT);

        String shown = entry.isEmpty() ? "------" : entry;
        int colour = entry.isEmpty() ? GREEN_DIM : GREEN_TEXT;
        drawCenteredString(fontRenderer, shown, x + 48, y + 5, colour);
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
            if (mouseX >= x && mouseY >= y && mouseX < x + BUTTON_WIDTH && mouseY < y + BUTTON_HEIGHT) {
                return i;
            }
        }
        return -1;
    }
}
