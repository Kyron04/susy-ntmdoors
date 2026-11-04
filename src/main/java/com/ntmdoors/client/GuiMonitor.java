package com.ntmdoors.client;

import com.ntmdoors.network.MessageMonitorAction;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Monitor terminal. The screen flow is:
 *
 * <ol>
 *     <li>Ask whether the desk should get a password (first open only).</li>
 *     <li>Enter and confirm a new password, or enter the existing one.</li>
 *     <li>Show the stacked task list: Security, Opencomputers, Factory.</li>
 * </ol>
 *
 * <p>The password itself never leaves the client; only its SHA-256 hash is sent to the server.
 * The per-desk hash lives on {@code TileEntityMonitorDesk} and is checked server-side.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiMonitor extends GuiScreen {

    private enum Stage {
        ASK,
        SET,
        CONFIRM,
        ENTER,
        MENU
    }

    private static final String[] TASKS = {"Security", "Opencomputers", "Factory"};

    private static final int ORANGE = 0xFFFF8A00;
    private static final int ORANGE_LIGHT = 0xFFFFB454;
    private static final int ORANGE_DIM = 0xFF7A3D00;
    private static final int ORANGE_GLOW = 0x33FF8A00;
    private static final int ORANGE_ERROR = 0xFFFF4A2E;
    private static final int TEXT = 0xFFFFD9A8;
    private static final int MUTED = 0xFF9A6A3A;

    private static final int BLACK = 0xFF000000;
    private static final int CASING_DARK = 0xFF080808;
    private static final int CASING = 0xFF141414;
    private static final int CASING_LIGHT = 0xFF2A2A2A;
    private static final int TITLE_BG = 0xFF0C0C0C;
    private static final int SCREEN_BG = 0xFF040404;
    private static final int FIELD_BG = 0xFF020202;
    private static final int BUTTON_BG = 0xFF101010;
    private static final int BUTTON_HOVER = 0xFF2A1500;
    private static final int ROW_BG = 0xFF080808;
    private static final int ROW_HOVER = 0xFF241200;

    private static final int TITLE_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 20;
    private static final int TITLE_TEXT_Y = 10;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_GAP = 8;

    private final BlockPos origin;

    private boolean hasPassword;
    private Stage stage;

    private final StringBuilder password = new StringBuilder();
    private String firstPassword = "";
    private String error = "";
    private boolean waiting;
    private int ticks;
    private int selectedTask = -1;

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int fieldX;
    private int fieldWidth;
    private int buttonX;
    private int buttonWidth;

    public GuiMonitor(BlockPos origin, boolean hasPassword, boolean passwordSkipped) {
        this.origin = origin;
        this.hasPassword = hasPassword;
        this.stage = hasPassword ? Stage.ENTER : passwordSkipped ? Stage.MENU : Stage.ASK;
    }

    @Override
    public void initGui() {
        super.initGui();
        int maxWidth = Math.max(1, this.width - 8);
        int maxHeight = Math.max(1, this.height - 8);
        this.guiWidth = Math.min(maxWidth, Math.max(160, this.width * 9 / 10));
        this.guiHeight = Math.min(maxHeight, Math.max(120, this.height * 9 / 10));
        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        int screenLeft = guiLeft + 6;
        int screenRight = guiLeft + guiWidth - 6;
        int screenTop = guiTop + 5 + TITLE_HEIGHT + 1;
        int screenBottom = guiTop + guiHeight - 5 - FOOTER_HEIGHT;
        this.contentLeft = screenLeft + 14;
        this.contentRight = screenRight - 14;
        this.contentTop = screenTop + 14;
        this.contentBottom = screenBottom - 14;

        this.fieldWidth = Math.max(100, Math.min(320, contentRight - contentLeft - 24));
        this.fieldX = (contentLeft + contentRight - fieldWidth) / 2;
        this.buttonWidth = Math.max(70, Math.min(110, contentRight - contentLeft - 60));
        this.buttonX = (contentLeft + contentRight - buttonWidth) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ticks++;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawFrame();
        drawContent(mouseX, mouseY);
    }

    // ------------------------------------------------------------ frame

    private void drawFrame() {
        int left = guiLeft;
        int top = guiTop;
        int right = left + guiWidth;
        int bottom = top + guiHeight;

        drawRect(left - 2, top - 2, right + 2, bottom + 2, BLACK);
        drawRect(left, top, right, bottom, CASING_DARK);
        drawRect(left + 2, top + 2, right - 2, bottom - 2, CASING);
        drawRect(left + 2, top + 2, right - 2, top + 3, CASING_LIGHT);
        drawRect(left + 2, top + 2, left + 3, bottom - 2, CASING_LIGHT);
        drawRect(left + 2, bottom - 3, right - 2, bottom - 2, 0xFF050505);
        drawRect(right - 3, top + 2, right - 2, bottom - 2, 0xFF050505);

        drawRect(left + 4, top + 4, right - 4, top + 5, ORANGE_DIM);
        drawRect(left + 4, top + 4, left + 5, bottom - 4, ORANGE_DIM);
        drawRect(left + 4, bottom - 5, right - 4, bottom - 4, ORANGE_DIM);
        drawRect(right - 5, top + 4, right - 4, bottom - 4, ORANGE_DIM);

        int innerLeft = left + 5;
        int innerRight = right - 5;
        int titleBottom = top + 5 + TITLE_HEIGHT;

        drawRect(innerLeft, top + 5, innerRight, titleBottom, TITLE_BG);
        drawRect(innerLeft, titleBottom - 1, innerRight, titleBottom, ORANGE);
        fontRenderer.drawString("Monitor", innerLeft + 6, top + TITLE_TEXT_Y, ORANGE_LIGHT);
        String status = statusText();
        fontRenderer.drawString(status, innerRight - 6 - fontRenderer.getStringWidth(status),
                top + TITLE_TEXT_Y, MUTED);

        int screenTop = titleBottom + 1;
        int screenBottom = bottom - 5 - FOOTER_HEIGHT;
        drawRect(innerLeft, screenTop, innerRight, screenBottom, BLACK);
        drawRect(innerLeft + 1, screenTop + 1, innerRight - 1, screenBottom - 1, SCREEN_BG);
        drawGradientRect(innerLeft + 1, screenTop + 1, innerRight - 1, screenTop + 24,
                ORANGE_GLOW, 0x00000000);
        drawCornerBrackets(innerLeft + 8, screenTop + 8, innerRight - 8, screenBottom - 8);

        int footerTop = screenBottom + 1;
        drawRect(innerLeft, footerTop, innerRight, bottom - 5, TITLE_BG);
        drawRect(innerLeft, footerTop, innerRight, footerTop + 1, ORANGE_DIM);
        int led = waiting && (ticks / 5) % 2 == 0 ? ORANGE_DIM : ORANGE;
        if (stage == Stage.ENTER && !waiting) led = ORANGE_DIM;
        drawRect(innerLeft + 6, footerTop + 8, innerLeft + 10, footerTop + 12, led);
        drawRect(innerLeft + 11, footerTop + 9, innerLeft + 13, footerTop + 11, ORANGE_DIM);
    }

    private String statusText() {
        if (waiting) return "WORKING";
        if (stage == Stage.MENU) return hasPassword ? "ONLINE" : "OPEN";
        if (stage == Stage.ENTER) return "LOCKED";
        return "SETUP";
    }

    // ------------------------------------------------------------ content

    private void drawContent(int mouseX, int mouseY) {
        switch (stage) {
            case ASK:
                drawAsk(mouseX, mouseY);
                break;
            case SET:
            case CONFIRM:
            case ENTER:
                drawPassword(mouseX, mouseY);
                break;
            case MENU:
                drawMenu(mouseX, mouseY);
                break;
            default:
                break;
        }
        drawStatusLine(statusY());
    }

    private void drawAsk(int mouseX, int mouseY) {
        int centerX = centerX();
        int centerY = centerY();
        drawCenteredString(fontRenderer, "Do you want to set a password?", centerX,
                centerY - 26, ORANGE_LIGHT);
        drawCenteredString(fontRenderer, "This will lock the terminal when it is reopened.",
                centerX, centerY - 8, MUTED);

        int buttonWidth = 80;
        int gap = 16;
        int total = buttonWidth * 2 + gap;
        int x = centerX - total / 2;
        int y = centerY + 18;
        drawButton("Yes", x, y, buttonWidth, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, x, y, buttonWidth, BUTTON_HEIGHT));
        drawButton("No", x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, x + buttonWidth + gap, y,
                        buttonWidth, BUTTON_HEIGHT));
    }

    private void drawPassword(int mouseX, int mouseY) {
        int centerX = centerX();
        int fieldY = passwordFieldY();
        int buttonY = passwordButtonY();

        String heading;
        String subtitle;
        String button;
        switch (stage) {
            case SET:
                heading = "Enter Password";
                subtitle = "Choose a password for this terminal.";
                button = "Continue";
                break;
            case CONFIRM:
                heading = "Enter Password Again";
                subtitle = "Confirm the password to finish setup.";
                button = "Confirm";
                break;
            default:
                heading = "Enter Password";
                subtitle = "This terminal is locked.";
                button = "Unlock";
                break;
        }

        drawCenteredString(fontRenderer, heading, centerX, fieldY - 44, ORANGE_LIGHT);
        drawCenteredString(fontRenderer, subtitle, centerX, fieldY - 28, MUTED);
        drawCenteredString(fontRenderer, "Password", centerX, fieldY - 12, ORANGE);
        drawTextField(fieldY);
        drawCenteredString(fontRenderer, "1-" + MonitorPasswords.MAX_LENGTH + " characters",
                centerX, fieldY + FIELD_HEIGHT + 4, MUTED);

        drawButton(waiting ? "Please wait" : button, buttonX, buttonY,
                buttonWidth, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, buttonX, buttonY,
                        buttonWidth, BUTTON_HEIGHT));
    }

    private void drawMenu(int mouseX, int mouseY) {
        int centerX = centerX();
        int width = menuWidth();
        int x = centerX - width / 2;
        int start = menuStart();

        if (menuShowHeading()) {
            drawCenteredString(fontRenderer, "TASKS", centerX, start - 42, ORANGE_LIGHT);
            drawCenteredString(fontRenderer, "Select a module", centerX, start - 24, MUTED);
        }

        for (int i = 0; i < TASKS.length; i++) {
            int y = start + i * (ROW_HEIGHT + ROW_GAP);
            boolean hover = isHovering(mouseX, mouseY, x, y, width, ROW_HEIGHT);
            boolean selected = selectedTask == i;
            int fill = hover || selected ? ROW_HOVER : ROW_BG;
            int border = hover || selected ? ORANGE : ORANGE_DIM;

            drawRect(x, y, x + width, y + ROW_HEIGHT, fill);
            drawRect(x, y, x + width, y + 1, border);
            drawRect(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, border);
            drawRect(x, y, x + 1, y + ROW_HEIGHT, border);
            drawRect(x + width - 1, y, x + width, y + ROW_HEIGHT, border);
            if (selected) drawRect(x + 1, y + 1, x + 4, y + ROW_HEIGHT - 1, ORANGE);

            fontRenderer.drawString(TASKS[i], x + 14, y + 11,
                    hover || selected ? ORANGE_LIGHT : TEXT);
            if (hover || selected) fontRenderer.drawString(">", x + width - 14, y + 11, ORANGE);
        }
    }

    private void drawStatusLine(int y) {
        if (waiting) {
            drawCenteredString(fontRenderer, "Contacting terminal...", centerX(), y, MUTED);
        } else if (!error.isEmpty()) {
            drawCenteredString(fontRenderer, error, centerX(), y, ORANGE_ERROR);
        }
    }

    private void drawTextField(int y) {
        int border = waiting ? ORANGE_DIM : ORANGE;
        drawRect(fieldX - 1, y - 1, fieldX + fieldWidth + 1, y + FIELD_HEIGHT + 1, border);
        drawRect(fieldX, y, fieldX + fieldWidth, y + FIELD_HEIGHT, FIELD_BG);
        drawRect(fieldX, y + FIELD_HEIGHT - 1, fieldX + fieldWidth, y + FIELD_HEIGHT, 0xFF1A0E04);

        String masked = mask(password.length());
        int textY = y + (FIELD_HEIGHT - 8) / 2;
        if (!masked.isEmpty()) {
            fontRenderer.drawString(masked, fieldX + 8, textY, TEXT);
        }
        if (!waiting && (ticks / 10) % 2 == 0) {
            int cursorX = fieldX + 8 + fontRenderer.getStringWidth(masked);
            drawRect(cursorX, y + 4, cursorX + 1, y + FIELD_HEIGHT - 4, ORANGE_LIGHT);
        }
    }

    private void drawButton(String label, int x, int y, int width, int height, boolean hover) {
        int fill = waiting ? 0xFF080808 : hover ? BUTTON_HOVER : BUTTON_BG;
        int border = waiting ? ORANGE_DIM : hover ? ORANGE : ORANGE_DIM;
        int text = waiting ? MUTED : hover ? ORANGE_LIGHT : TEXT;
        drawRect(x, y, x + width, y + height, fill);
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        drawCenteredString(fontRenderer, label, x + width / 2, y + (height - 8) / 2, text);
    }

    private void drawCornerBrackets(int left, int top, int right, int bottom) {
        int length = 12;
        drawRect(left, top, left + length, top + 1, ORANGE_DIM);
        drawRect(left, top, left + 1, top + length, ORANGE_DIM);

        drawRect(right - length, top, right, top + 1, ORANGE_DIM);
        drawRect(right - 1, top, right, top + length, ORANGE_DIM);

        drawRect(left, bottom - 1, left + length, bottom, ORANGE_DIM);
        drawRect(left, bottom - length, left + 1, bottom, ORANGE_DIM);

        drawRect(right - length, bottom - 1, right, bottom, ORANGE_DIM);
        drawRect(right - 1, bottom - length, right, bottom, ORANGE_DIM);
    }

    // ------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && !waiting) {
            if (stage == Stage.ASK) {
                int centerX = centerX();
                int centerY = centerY();
                int width = 80;
                int gap = 16;
                int total = width * 2 + gap;
                int x = centerX - total / 2;
                int y = centerY + 18;
                if (isHovering(mouseX, mouseY, x, y, width, BUTTON_HEIGHT)) {
                    stage = Stage.SET;
                    password.setLength(0);
                    firstPassword = "";
                    error = "";
                    return;
                }
                if (isHovering(mouseX, mouseY, x + width + gap, y, width, BUTTON_HEIGHT)) {
                    sendAction(MessageMonitorAction.SKIP_PASSWORD, "");
                    return;
                }
            } else if (stage == Stage.SET || stage == Stage.CONFIRM || stage == Stage.ENTER) {
                if (isHovering(mouseX, mouseY, buttonX, passwordButtonY(),
                        buttonWidth, BUTTON_HEIGHT)) {
                    submitPassword();
                    return;
                }
            } else if (stage == Stage.MENU) {
                int width = menuWidth();
                int x = centerX() - width / 2;
                int start = menuStart();
                for (int i = 0; i < TASKS.length; i++) {
                    int y = start + i * (ROW_HEIGHT + ROW_GAP);
                    if (isHovering(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
                        selectedTask = i;
                        return;
                    }
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (stage == Stage.CONFIRM) {
                stage = Stage.SET;
                password.setLength(0);
                firstPassword = "";
                error = "";
                return;
            }
            if (stage == Stage.SET) {
                stage = Stage.ASK;
                password.setLength(0);
                firstPassword = "";
                error = "";
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }

        if (waiting) return;
        if (stage == Stage.ASK || stage == Stage.MENU) {
            super.keyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            submitPassword();
            return;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
            if (password.length() > 0) password.deleteCharAt(password.length() - 1);
            return;
        }
        if (GuiScreen.isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_V) pasteClipboard();
            return;
        }
        if (typedChar >= 32 && typedChar != 127
                && password.length() < MonitorPasswords.MAX_LENGTH) {
            password.append(typedChar);
        }
    }

    private void pasteClipboard() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null) return;
        for (int i = 0; i < clip.length() && password.length() < MonitorPasswords.MAX_LENGTH; i++) {
            char c = clip.charAt(i);
            if (c >= 32 && c != 127) password.append(c);
        }
    }

    private void submitPassword() {
        if (stage == Stage.SET) {
            if (password.length() == 0) {
                error = "Enter a password";
                return;
            }
            firstPassword = password.toString();
            password.setLength(0);
            stage = Stage.CONFIRM;
            error = "";
            return;
        }

        if (stage == Stage.CONFIRM) {
            if (!firstPassword.equals(password.toString())) {
                error = "Passwords do not match";
                password.setLength(0);
                return;
            }
            sendAction(MessageMonitorAction.SET_PASSWORD, firstPassword);
            return;
        }

        if (stage == Stage.ENTER) {
            if (password.length() == 0) {
                error = "Enter a password";
                return;
            }
            sendAction(MessageMonitorAction.CHECK_PASSWORD, password.toString());
        }
    }

    private void sendAction(int action, String plainPassword) {
        waiting = true;
        error = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorAction(origin, action,
                MonitorPasswords.hash(plainPassword)));
    }

    /** Called by the client proxy when the server replies to set, skip or check. */
    public void handleServerResult(int result, boolean hasPassword) {
        waiting = false;
        this.hasPassword = hasPassword;

        switch (result) {
            case MessageMonitorState.SET_OK:
                stage = Stage.MENU;
                password.setLength(0);
                firstPassword = "";
                error = "";
                selectedTask = -1;
                break;
            case MessageMonitorState.SKIP_OK:
                stage = Stage.MENU;
                password.setLength(0);
                firstPassword = "";
                error = "";
                break;
            case MessageMonitorState.CHECK_OK:
                stage = Stage.MENU;
                password.setLength(0);
                error = "";
                break;
            case MessageMonitorState.WRONG_PASSWORD:
                stage = Stage.ENTER;
                password.setLength(0);
                error = "Wrong password";
                break;
            case MessageMonitorState.INVALID:
                error = "Password not accepted";
                break;
            case MessageMonitorState.UNAVAILABLE:
                error = "Terminal unavailable";
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------ layout

    private int centerX() {
        return (contentLeft + contentRight) / 2;
    }

    private int centerY() {
        return (contentTop + contentBottom) / 2;
    }

    private int passwordFieldY() {
        int min = contentTop + 44;
        int max = contentBottom - 75;
        if (max < min) max = min;
        return Math.max(min, Math.min(centerY() - 8, max));
    }

    private int passwordButtonY() {
        return passwordFieldY() + 40;
    }

    private int statusY() {
        if (stage == Stage.ASK || stage == Stage.MENU) return contentBottom - 18;
        return passwordButtonY() + BUTTON_HEIGHT + 6;
    }

    private int menuWidth() {
        return Math.max(120, Math.min(360, contentRight - contentLeft - 40));
    }

    private boolean menuShowHeading() {
        int height = contentBottom - contentTop;
        int total = TASKS.length * ROW_HEIGHT + (TASKS.length - 1) * ROW_GAP;
        return height >= total + 51;
    }

    private int menuStart() {
        int height = contentBottom - contentTop;
        int total = TASKS.length * ROW_HEIGHT + (TASKS.length - 1) * ROW_GAP;
        int pad = menuShowHeading() ? 42 : 30;
        return contentTop + Math.max(pad, (height - total) / 2);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String mask(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append('*');
        return out.toString();
    }
}
