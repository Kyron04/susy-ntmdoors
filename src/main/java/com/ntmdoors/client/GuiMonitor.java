package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.network.MessageMonitorAction;
import com.ntmdoors.network.MessageMonitorDoorScanner;
import com.ntmdoors.network.MessageMonitorDoorScannerResult;
import com.ntmdoors.network.MessageMonitorKeyResult;
import com.ntmdoors.network.MessageMonitorNetRequest;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.MonitorPasswords;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        MENU,
        SECURITY_HOME,
        SECURITY,
        NAME_NET,
        NAME_KEY,
        DELETE_NET
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
    private static final int SECURITY_ROW_HEIGHT = 28;

    private final BlockPos origin;

    private boolean hasPassword;
    private Stage stage;

    private final StringBuilder password = new StringBuilder();
    private String firstPassword = "";
    private String error = "";
    private boolean waiting;
    private int ticks;
    private int selectedTask = -1;
    private final List<MonitorDevice> securityDevices = new ArrayList<>();
    private final List<SecurityNetSummary> securityNets = new ArrayList<>();
    private boolean securityLoading;
    private boolean netsLoading;
    private int securityScroll;
    private int netsScroll;
    private String securityNotice = "";
    private BlockPos pendingDoor;
    private int selectedNetId = -1;
    private String selectedNetName = "";
    private String linkedKeyName = "";
    private int pendingKeyHand = -1;
    private final StringBuilder nameInput = new StringBuilder();
    private Stage nameReturnStage = Stage.SECURITY_HOME;
    private boolean deleteRequested;

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

    /** Reopens an already-unlocked terminal directly on one net's device list. */
    public static GuiMonitor forSecurity(BlockPos origin, boolean hasPassword,
                                         int netId, String netName) {
        GuiMonitor gui = new GuiMonitor(origin, hasPassword, true);
        gui.openSecurityNet(netId, netName);
        return gui;
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
        if (stage == Stage.SECURITY_HOME) return "SECURITY";
        if (stage == Stage.SECURITY) return securityLoading ? "LOADING" : "SECURITY";
        if (stage == Stage.NAME_NET || stage == Stage.NAME_KEY) return "SECURITY";
        if (stage == Stage.DELETE_NET) return "CONFIRM";
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
            case SECURITY_HOME:
                drawSecurityHome(mouseX, mouseY);
                break;
            case SECURITY:
                drawSecurity(mouseX, mouseY);
                break;
            case NAME_NET:
            case NAME_KEY:
                drawNamePrompt(mouseX, mouseY);
                break;
            case DELETE_NET:
                drawDeleteConfirm(mouseX, mouseY);
                break;
            default:
                break;
        }
        if (stage != Stage.SECURITY && stage != Stage.SECURITY_HOME
                && stage != Stage.DELETE_NET) {
            drawStatusLine(statusY());
        }
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

    private void drawNamePrompt(int mouseX, int mouseY) {
        int centerX = centerX();
        int fieldY = passwordFieldY();
        int buttonY = passwordButtonY();
        String heading = stage == Stage.NAME_NET ? "Name Security Net" : "Rename Terminal Key";
        String subtitle = stage == Stage.NAME_NET
                ? "Leave blank for an automatic name."
                : "This changes the linked key's name.";
        drawCenteredString(fontRenderer, heading, centerX, fieldY - 44, ORANGE_LIGHT);
        drawCenteredString(fontRenderer, subtitle, centerX, fieldY - 28, MUTED);
        drawCenteredString(fontRenderer, "Name", centerX, fieldY - 12, ORANGE);
        drawNameField(fieldY);
        drawButton(waiting ? "Please wait" : "Save", buttonX, buttonY,
                buttonWidth, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, buttonX, buttonY,
                        buttonWidth, BUTTON_HEIGHT));
    }

    private void drawDeleteConfirm(int mouseX, int mouseY) {
        int centerX = centerX();
        int centerY = centerY();
        String name = fontRenderer.trimStringToWidth(
                selectedNetName, contentRight - contentLeft - 24);
        drawCenteredString(fontRenderer, "Delete Security Net?", centerX,
                centerY - 44, ORANGE_LIGHT);
        drawCenteredString(fontRenderer, name, centerX, centerY - 26, TEXT);
        drawCenteredString(fontRenderer, "This removes its devices and key link.",
                centerX, centerY - 8, MUTED);
        drawCenteredString(fontRenderer, "This cannot be undone.",
                centerX, centerY + 6, ORANGE_ERROR);

        int width = 80;
        int gap = 16;
        int x = centerX - (width * 2 + gap) / 2;
        int y = centerY + 28;
        drawButton("Cancel", x, y, width, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, x, y, width, BUTTON_HEIGHT));
        drawDangerButton(waiting ? "Deleting..." : "Delete", x + width + gap, y,
                width, BUTTON_HEIGHT,
                !waiting && isHovering(mouseX, mouseY, x + width + gap, y,
                        width, BUTTON_HEIGHT));
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

    private void drawSecurityHome(int mouseX, int mouseY) {
        int listLeft = securityListLeft();
        int listRight = securityListRight();
        int listTop = securityListTop();
        int listBottom = securityListBottom();
        int leftWidth = Math.max(0, listLeft - contentLeft - 10);

        drawString(fontRenderer, "SECURITY", contentLeft, contentTop + 4, ORANGE_LIGHT);
        drawRect(contentLeft, contentTop + 18, contentLeft + leftWidth, contentTop + 19, ORANGE_DIM);
        drawString(fontRenderer, "Security nets", contentLeft, contentTop + 28, MUTED);
        String count = securityNets.size() + (securityNets.size() == 1 ? " net" : " nets");
        drawString(fontRenderer, count, contentLeft, contentTop + 42, TEXT);
        drawString(fontRenderer, "Create a net to", contentLeft, contentTop + 62, MUTED);
        drawString(fontRenderer, "bind devices.", contentLeft, contentTop + 72, MUTED);
        if (!securityNotice.isEmpty()) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(securityNotice, leftWidth),
                    contentLeft, contentTop + 92, ORANGE_LIGHT);
        }

        drawRect(listLeft, listTop, listRight, listBottom, 0xFF060606);
        drawRect(listLeft, listTop, listRight, listTop + 1, ORANGE_DIM);
        drawRect(listLeft, listBottom - 1, listRight, listBottom, ORANGE_DIM);
        drawString(fontRenderer, "SECURITY NETS", listLeft + 8, listTop + 6, ORANGE);

        int rowsTop = securityRowsTop();
        int rowsBottom = securityRowsBottom();
        int listCenterX = (listLeft + listRight) / 2;
        int listCenterY = (rowsTop + rowsBottom) / 2;
        int total = securityNets.size() + 1;
        int visible = securityVisibleRows();
        int maxScroll = Math.max(0, total - visible);
        if (netsScroll > maxScroll) netsScroll = maxScroll;

        if (netsLoading && securityNets.isEmpty()) {
            drawCenteredString(fontRenderer, "Loading security nets...", listCenterX,
                    listCenterY - 4, MUTED);
        } else {
            int rowLeft = listLeft + 6;
            int rowRight = listRight - 8;
            for (int i = 0; i < visible; i++) {
                int index = netsScroll + i;
                if (index >= total) break;
                int y = rowsTop + i * SECURITY_ROW_HEIGHT;
                boolean hover = isHovering(mouseX, mouseY, rowLeft, y,
                        rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2);
                drawRect(rowLeft, y, rowRight, y + SECURITY_ROW_HEIGHT - 2,
                        hover ? ROW_HOVER : ROW_BG);
                drawRect(rowLeft, y, rowRight, y + 1, ORANGE_DIM);

                if (index < securityNets.size()) {
                    SecurityNetSummary net = securityNets.get(index);
                    String name = fontRenderer.trimStringToWidth(
                            net.name, rowRight - rowLeft - 12);
                    String devices = net.deviceCount + (net.deviceCount == 1
                            ? " device" : " devices");
                    drawString(fontRenderer, name, rowLeft + 6, y + 4,
                            hover ? ORANGE_LIGHT : TEXT);
                    drawString(fontRenderer, devices, rowLeft + 6, y + 15, MUTED);
                    if (hover) drawString(fontRenderer, ">", rowRight - 8, y + 9, ORANGE);
                } else {
                    int plusX = rowLeft + 9;
                    int plusY = y + 13;
                    drawRect(plusX - 6, plusY - 1, plusX + 6, plusY + 1, ORANGE);
                    drawRect(plusX - 1, plusY - 6, plusX + 1, plusY + 6, ORANGE);
                    drawString(fontRenderer, "Add security net", rowLeft + 20, y + 9,
                            hover ? ORANGE_LIGHT : TEXT);
                }
            }
            drawListScrollbar(rowsTop, rowsBottom, visible, maxScroll, total, netsScroll);
        }

        drawButton("Back", contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT));
    }

    private void drawSecurity(int mouseX, int mouseY) {
        int listLeft = securityListLeft();
        int listRight = securityListRight();
        int listTop = securityListTop();
        int listBottom = securityListBottom();
        int leftWidth = Math.max(0, listLeft - contentLeft - 10);

        String netName = fontRenderer.trimStringToWidth(
                selectedNetName.isEmpty() ? "Security net" : selectedNetName, leftWidth);
        drawString(fontRenderer, netName, contentLeft, contentTop + 4, ORANGE_LIGHT);
        drawRect(contentLeft, contentTop + 18, contentLeft + leftWidth, contentTop + 19, ORANGE_DIM);
        String count = securityDevices.size() + (securityDevices.size() == 1 ? " device" : " devices");
        drawString(fontRenderer, count, contentLeft, contentTop + 28, TEXT);
        drawString(fontRenderer, "TERMINAL KEY", contentLeft, contentTop + 42, MUTED);

        String keyName = currentKeyName();
        String shownKey = keyName.isEmpty() ? "No key linked"
                : fontRenderer.trimStringToWidth(keyName, leftWidth);
        drawString(fontRenderer, shownKey, contentLeft, contentTop + 54,
                keyName.isEmpty() ? MUTED : TEXT);
        drawKeySlotFrame();

        int keyButtonY = contentTop + 84;
        if (!linkedKeyName.isEmpty()) {
            int button = Math.max(36, Math.min(56, (leftWidth - 4) / 2));
            drawButton("Rename", contentLeft, keyButtonY, button, 16,
                    isHovering(mouseX, mouseY, contentLeft, keyButtonY, button, 16));
            drawButton("Unlink", contentLeft + button + 4, keyButtonY, button, 16,
                    isHovering(mouseX, mouseY, contentLeft + button + 4, keyButtonY, button, 16));
        } else if (pendingKeyHand >= 0) {
            int button = Math.max(48, Math.min(80, leftWidth));
            drawButton("Bind", contentLeft, keyButtonY, button, 16,
                    isHovering(mouseX, mouseY, contentLeft, keyButtonY, button, 16));
        } else {
            drawString(fontRenderer, "Click slot", contentLeft, keyButtonY, MUTED);
        }
        if (!securityNotice.isEmpty()) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(securityNotice, leftWidth),
                    contentLeft, contentTop + 104, ORANGE_ERROR);
        }

        drawRect(listLeft, listTop, listRight, listBottom, 0xFF060606);
        drawRect(listLeft, listTop, listRight, listTop + 1, ORANGE_DIM);
        drawRect(listLeft, listBottom - 1, listRight, listBottom, ORANGE_DIM);
        drawString(fontRenderer, "BOUND DEVICES", listLeft + 8, listTop + 6, ORANGE);

        int rowsTop = securityRowsTop();
        int rowsBottom = securityRowsBottom();
        int listCenterX = (listLeft + listRight) / 2;
        int listCenterY = (rowsTop + rowsBottom) / 2;
        if (securityLoading) {
            drawCenteredString(fontRenderer, "Loading devices...", listCenterX, listCenterY - 4, MUTED);
        } else if (securityDevices.isEmpty()) {
            drawCenteredString(fontRenderer, "No devices bound", listCenterX, listCenterY - 10, MUTED);
            drawCenteredString(fontRenderer, "Bind with a Terminal Key", listCenterX,
                    listCenterY + 4, MUTED);
        } else {
            int visible = securityVisibleRows();
            int maxScroll = securityMaxScroll();
            if (securityScroll > maxScroll) securityScroll = maxScroll;
            int rowLeft = listLeft + 6;
            int rowRight = listRight - 8;
            for (int i = 0; i < visible; i++) {
                int index = securityScroll + i;
                if (index >= securityDevices.size()) break;
                int y = rowsTop + i * SECURITY_ROW_HEIGHT;
                boolean hover = isHovering(mouseX, mouseY, rowLeft, y,
                        rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2);
                drawRect(rowLeft, y, rowRight, y + SECURITY_ROW_HEIGHT - 2,
                        hover ? ROW_HOVER : ROW_BG);
                drawRect(rowLeft, y, rowRight, y + 1, ORANGE_DIM);

                MonitorDevice device = securityDevices.get(index);
                String name = fontRenderer.trimStringToWidth(device.name, rowRight - rowLeft - 12);
                drawString(fontRenderer, name, rowLeft + 6, y + 4,
                        hover ? ORANGE_LIGHT : TEXT);
                drawString(fontRenderer, device.type.displayName(), rowLeft + 6, y + 15, MUTED);
                if (hover) drawString(fontRenderer, ">", rowRight - 8, y + 9, ORANGE);
            }
            drawListScrollbar(rowsTop, rowsBottom, visible, maxScroll,
                    securityDevices.size(), securityScroll);
        }

        drawButton("Back", contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT));
        int deleteWidth = deleteNetButtonWidth();
        drawDangerButton("Delete net", deleteNetButtonX(), contentBottom - 24,
                deleteWidth, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, deleteNetButtonX(), contentBottom - 24,
                        deleteWidth, BUTTON_HEIGHT));
        drawKeySlotItem();
    }

    private void drawListScrollbar(int rowsTop, int rowsBottom, int visible, int maxScroll,
                                   int total, int scroll) {
        int trackX = securityListRight() - 4;
        drawRect(trackX, rowsTop, trackX + 2, rowsBottom, 0xFF1A0E04);
        if (maxScroll <= 0 || total <= 0) return;
        int trackHeight = rowsBottom - rowsTop;
        int thumbHeight = Math.max(12, trackHeight * visible / total);
        int thumbY = rowsTop + (trackHeight - thumbHeight) * scroll / maxScroll;
        drawRect(trackX, thumbY, trackX + 2, thumbY + thumbHeight, ORANGE_DIM);
    }

    private String currentKeyName() {
        if (!linkedKeyName.isEmpty()) return linkedKeyName;
        if (pendingKeyHand >= 0 && mc.player != null) {
            ItemStack stack = mc.player.getHeldItem(
                    pendingKeyHand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
            if (stack.getItem() instanceof ItemTerminalRelayKey) {
                return stack.getDisplayName();
            }
            pendingKeyHand = -1;
        }
        return "";
    }

    private ItemStack keyDisplayStack() {
        if (!linkedKeyName.isEmpty()) {
            return new ItemStack(ModContent.TERMINAL_RELAY_KEY);
        }
        if (pendingKeyHand >= 0 && mc.player != null) {
            ItemStack stack = mc.player.getHeldItem(
                    pendingKeyHand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
            if (stack.getItem() instanceof ItemTerminalRelayKey) return stack;
            pendingKeyHand = -1;
        }
        return ItemStack.EMPTY;
    }

    private void drawKeySlotFrame() {
        int x = contentLeft;
        int y = contentTop + 64;
        drawRect(x - 1, y - 1, x + 19, y + 19, ORANGE_DIM);
        drawRect(x, y, x + 18, y + 18, FIELD_BG);
        drawRect(x, y, x + 18, y + 1, 0xFF1A0E04);
    }

    private void drawKeySlotItem() {
        ItemStack key = keyDisplayStack();
        if (key.isEmpty()) return;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(key, contentLeft + 1, contentTop + 65);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
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

    private void drawNameField(int y) {
        int border = waiting ? ORANGE_DIM : ORANGE;
        drawRect(fieldX - 1, y - 1, fieldX + fieldWidth + 1, y + FIELD_HEIGHT + 1, border);
        drawRect(fieldX, y, fieldX + fieldWidth, y + FIELD_HEIGHT, FIELD_BG);
        drawRect(fieldX, y + FIELD_HEIGHT - 1, fieldX + fieldWidth, y + FIELD_HEIGHT, 0xFF1A0E04);

        String text = fontRenderer.trimStringToWidth(nameInput.toString(), fieldWidth - 16);
        int textY = y + (FIELD_HEIGHT - 8) / 2;
        if (!text.isEmpty()) {
            fontRenderer.drawString(text, fieldX + 8, textY, TEXT);
        }
        if (!waiting && (ticks / 10) % 2 == 0) {
            int cursorX = fieldX + 8 + fontRenderer.getStringWidth(text);
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

    private void drawDangerButton(String label, int x, int y, int width, int height,
                                  boolean hover) {
        int fill = waiting ? 0xFF080808 : hover ? 0xFF2A0804 : BUTTON_BG;
        int border = waiting ? 0xFF5A1A10 : hover ? ORANGE_ERROR : 0xFF7A2A18;
        int text = waiting ? MUTED : hover ? 0xFFFF7A5E : TEXT;
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
                    playClick();
                    stage = Stage.SET;
                    password.setLength(0);
                    firstPassword = "";
                    error = "";
                    return;
                }
                if (isHovering(mouseX, mouseY, x + width + gap, y, width, BUTTON_HEIGHT)) {
                    playClick();
                    sendAction(MessageMonitorAction.SKIP_PASSWORD, "");
                    return;
                }
            } else if (stage == Stage.SET || stage == Stage.CONFIRM || stage == Stage.ENTER) {
                if (isHovering(mouseX, mouseY, buttonX, passwordButtonY(),
                        buttonWidth, BUTTON_HEIGHT)) {
                    playClick();
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
                        playClick();
                        selectedTask = i;
                        if (i == 0) openSecurityHome();
                        return;
                    }
                }
            } else if (stage == Stage.SECURITY_HOME) {
                if (isHovering(mouseX, mouseY, contentLeft, contentBottom - 24,
                        64, BUTTON_HEIGHT)) {
                    playClick();
                    stage = Stage.MENU;
                    error = "";
                    return;
                }

                int rowLeft = securityListLeft() + 6;
                int rowRight = securityListRight() - 8;
                int rowsTop = securityRowsTop();
                int visible = securityVisibleRows();
                for (int i = 0; i < visible; i++) {
                    int index = netsScroll + i;
                    if (index >= securityNets.size() + 1) break;
                    int y = rowsTop + i * SECURITY_ROW_HEIGHT;
                    if (isHovering(mouseX, mouseY, rowLeft, y,
                            rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2)) {
                        playClick();
                        if (index < securityNets.size()) {
                            SecurityNetSummary net = securityNets.get(index);
                            openSecurityNet(net.id, net.name);
                        } else {
                            startNameNet();
                        }
                        return;
                    }
                }
            } else if (stage == Stage.SECURITY) {
                if (isHovering(mouseX, mouseY, contentLeft, contentBottom - 24,
                        64, BUTTON_HEIGHT)) {
                    playClick();
                    stage = Stage.SECURITY_HOME;
                    securityNotice = "";
                    pendingDoor = null;
                    pendingKeyHand = -1;
                    error = "";
                    openSecurityHome();
                    return;
                }

                int deleteWidth = deleteNetButtonWidth();
                if (isHovering(mouseX, mouseY, deleteNetButtonX(), contentBottom - 24,
                        deleteWidth, BUTTON_HEIGHT)) {
                    playClick();
                    startDeleteNet();
                    return;
                }

                if (isHovering(mouseX, mouseY, contentLeft, contentTop + 64, 18, 18)) {
                    if (linkedKeyName.isEmpty()) {
                        playClick();
                        pendingKeyHand = heldTerminalKeyHand();
                        securityNotice = pendingKeyHand >= 0 ? "" : "Hold a key";
                    }
                    return;
                }

                int leftWidth = Math.max(0, securityListLeft() - contentLeft - 10);
                int keyButtonY = contentTop + 84;
                if (!linkedKeyName.isEmpty()) {
                    int button = Math.max(36, Math.min(56, (leftWidth - 4) / 2));
                    if (isHovering(mouseX, mouseY, contentLeft, keyButtonY, button, 16)) {
                        playClick();
                        startRenameKey();
                        return;
                    }
                    if (isHovering(mouseX, mouseY, contentLeft + button + 4, keyButtonY,
                            button, 16)) {
                        playClick();
                        securityNotice = "";
                        waiting = true;
                        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                                origin, MessageMonitorNetRequest.UNLINK_KEY,
                                selectedNetId, "", 0));
                        return;
                    }
                } else if (pendingKeyHand >= 0) {
                    int button = Math.max(48, Math.min(80, leftWidth));
                    if (isHovering(mouseX, mouseY, contentLeft, keyButtonY, button, 16)) {
                        playClick();
                        securityNotice = "";
                        waiting = true;
                        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                                origin, MessageMonitorNetRequest.BIND_KEY,
                                selectedNetId, "", pendingKeyHand));
                        return;
                    }
                }

                int rowLeft = securityListLeft() + 6;
                int rowRight = securityListRight() - 8;
                int rowsTop = securityRowsTop();
                int visible = securityVisibleRows();
                for (int i = 0; i < visible; i++) {
                    int index = securityScroll + i;
                    if (index >= securityDevices.size()) break;
                    int y = rowsTop + i * SECURITY_ROW_HEIGHT;
                    if (isHovering(mouseX, mouseY, rowLeft, y,
                            rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2)) {
                        playClick();
                        openDevice(securityDevices.get(index));
                        return;
                    }
                }
            } else if (stage == Stage.DELETE_NET) {
                int width = 80;
                int gap = 16;
                int x = centerX() - (width * 2 + gap) / 2;
                int y = centerY() + 28;
                if (isHovering(mouseX, mouseY, x, y, width, BUTTON_HEIGHT)) {
                    playClick();
                    stage = Stage.SECURITY;
                    error = "";
                    return;
                }
                if (isHovering(mouseX, mouseY, x + width + gap, y,
                        width, BUTTON_HEIGHT)) {
                    playClick();
                    submitDeleteNet();
                    return;
                }
            } else if (stage == Stage.NAME_NET || stage == Stage.NAME_KEY) {
                if (isHovering(mouseX, mouseY, buttonX, passwordButtonY(),
                        buttonWidth, BUTTON_HEIGHT)) {
                    playClick();
                    submitName();
                    return;
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
            if (stage == Stage.DELETE_NET) {
                if (waiting) return;
                stage = Stage.SECURITY;
                error = "";
                return;
            }
            if (stage == Stage.SECURITY) {
                waiting = false;
                openSecurityHome();
                return;
            }
            if (stage == Stage.SECURITY_HOME) {
                stage = Stage.MENU;
                error = "";
                return;
            }
            if (stage == Stage.NAME_NET) {
                stage = Stage.SECURITY_HOME;
                nameInput.setLength(0);
                waiting = false;
                error = "";
                return;
            }
            if (stage == Stage.NAME_KEY) {
                stage = Stage.SECURITY;
                nameInput.setLength(0);
                waiting = false;
                error = "";
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }

        if (waiting) return;
        if (stage == Stage.NAME_NET || stage == Stage.NAME_KEY) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submitName();
                return;
            }
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (nameInput.length() > 0) nameInput.deleteCharAt(nameInput.length() - 1);
                return;
            }
            if (GuiScreen.isCtrlKeyDown()) {
                if (keyCode == Keyboard.KEY_V) pasteName();
                return;
            }
            if (typedChar >= 32 && typedChar != 127
                    && nameInput.length() < PrivateKeys.NAME_LENGTH) {
                nameInput.append(typedChar);
            }
            return;
        }
        if (stage == Stage.SECURITY) {
            if (keyCode == Keyboard.KEY_UP) {
                scrollSecurity(-1);
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                scrollSecurity(1);
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }
        if (stage == Stage.SECURITY_HOME) {
            if (keyCode == Keyboard.KEY_UP) {
                scrollNets(-1);
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                scrollNets(1);
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }
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

    private void pasteName() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null) return;
        for (int i = 0; i < clip.length() && nameInput.length() < PrivateKeys.NAME_LENGTH; i++) {
            char c = clip.charAt(i);
            if (c >= 32 && c != 127) nameInput.append(c);
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

    private int securityListLeft() {
        int total = contentRight - contentLeft;
        int leftWidth = Math.max(80, Math.min(150, total * 35 / 100));
        return contentLeft + leftWidth + 10;
    }

    private int securityListRight() {
        return contentRight;
    }

    private int securityListTop() {
        return contentTop + 24;
    }

    private int securityListBottom() {
        return Math.max(securityListTop() + 24, contentBottom - 30);
    }

    private int securityRowsTop() {
        return securityListTop() + 20;
    }

    private int securityRowsBottom() {
        return securityListBottom() - 4;
    }

    private int securityVisibleRows() {
        return Math.max(1, (securityRowsBottom() - securityRowsTop()) / SECURITY_ROW_HEIGHT);
    }

    private int securityMaxScroll() {
        return Math.max(0, securityDevices.size() - securityVisibleRows());
    }

    private int netsMaxScroll() {
        return Math.max(0, securityNets.size() + 1 - securityVisibleRows());
    }

    private int deleteNetButtonWidth() {
        return Math.max(56, Math.min(88, contentRight - contentLeft - 64 - 8));
    }

    private int deleteNetButtonX() {
        return contentRight - deleteNetButtonWidth();
    }

    private void openSecurityHome() {
        stage = Stage.SECURITY_HOME;
        netsLoading = true;
        securityNets.clear();
        netsScroll = 0;
        securityNotice = "";
        pendingDoor = null;
        pendingKeyHand = -1;
        selectedNetId = -1;
        selectedNetName = "";
        linkedKeyName = "";
        nameInput.setLength(0);
        waiting = false;
        deleteRequested = false;
        error = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(origin,
                MessageMonitorNetRequest.REQUEST_NETS, -1, "", 0));
    }

    private void openSecurityNet(int netId, String netName) {
        stage = Stage.SECURITY;
        selectedNetId = netId;
        selectedNetName = netName == null ? "" : netName;
        securityLoading = true;
        securityDevices.clear();
        securityScroll = 0;
        securityNotice = "";
        pendingDoor = null;
        pendingKeyHand = -1;
        linkedKeyName = "";
        waiting = false;
        deleteRequested = false;
        error = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(origin,
                MessageMonitorNetRequest.REQUEST_DEVICES, netId, "", 0));
    }

    private void openDevice(MonitorDevice device) {
        if (device == null) return;
        if (mc.world == null || !mc.world.isBlockLoaded(device.pos)) {
            securityNotice = "Not loaded";
            return;
        }
        if (device.type == MonitorDevice.Type.DOOR) {
            securityNotice = "";
            pendingDoor = device.pos;
            ModNetwork.INSTANCE.sendToServer(new MessageMonitorDoorScanner(device.pos));
            return;
        }
        securityNotice = "";
        pendingDoor = null;
        MonitorReturnContext.set(origin, hasPassword, selectedNetId, selectedNetName);
        NTMDoors.proxy.openLinkedDeviceGui(device);
    }

    /** Called by the client proxy when the server resolves a linked door's scanner. */
    public void handleDoorScanner(BlockPos doorPos, BlockPos scannerPos, int result) {
        if (stage != Stage.SECURITY || doorPos == null) return;
        if (pendingDoor == null || !pendingDoor.equals(doorPos)) return;
        pendingDoor = null;

        boolean known = false;
        for (MonitorDevice device : securityDevices) {
            if (device.type == MonitorDevice.Type.DOOR && device.pos.equals(doorPos)) {
                known = true;
                break;
            }
        }
        if (!known) return;

        if (result == MessageMonitorDoorScannerResult.FOUND && scannerPos != null) {
            if (mc.world == null || !mc.world.isBlockLoaded(scannerPos)) {
                securityNotice = "Not loaded";
                return;
            }
            securityNotice = "";
            MonitorReturnContext.set(origin, hasPassword, selectedNetId, selectedNetName);
            NTMDoors.proxy.openScannerCodeGui(scannerPos);
            return;
        }

        securityNotice = result == MessageMonitorDoorScannerResult.NOT_LOADED
                ? "Not loaded" : "No scanner";
    }

    private int heldTerminalKeyHand() {
        if (mc.player == null) return -1;
        if (mc.player.getHeldItemMainhand().getItem() instanceof ItemTerminalRelayKey) return 0;
        if (mc.player.getHeldItemOffhand().getItem() instanceof ItemTerminalRelayKey) return 1;
        return -1;
    }

    private void startNameNet() {
        stage = Stage.NAME_NET;
        nameReturnStage = Stage.SECURITY_HOME;
        nameInput.setLength(0);
        error = "";
        waiting = false;
        securityNotice = "";
    }

    private void startRenameKey() {
        stage = Stage.NAME_KEY;
        nameReturnStage = Stage.SECURITY;
        nameInput.setLength(0);
        nameInput.append(linkedKeyName);
        error = "";
        waiting = false;
        securityNotice = "";
    }

    private void submitName() {
        if (stage != Stage.NAME_NET && stage != Stage.NAME_KEY) return;
        String name = PrivateKeys.sanitiseName(nameInput.toString());
        waiting = true;
        error = "";
        if (stage == Stage.NAME_NET) {
            ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                    origin, MessageMonitorNetRequest.CREATE_NET, -1, name, 0));
        } else if (stage == Stage.NAME_KEY) {
            ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                    origin, MessageMonitorNetRequest.RENAME_KEY, selectedNetId, name, 0));
        }
    }

    private void startDeleteNet() {
        stage = Stage.DELETE_NET;
        waiting = false;
        error = "";
        securityNotice = "";
    }

    private void submitDeleteNet() {
        if (stage != Stage.DELETE_NET || selectedNetId < 0) return;
        waiting = true;
        deleteRequested = true;
        error = "";
        securityNotice = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                origin, MessageMonitorNetRequest.DELETE_NET, selectedNetId, "", 0));
    }

    private void playClick() {
        if (mc.player != null) mc.player.playSound(ModSounds.GUI_CLICK, 0.5F, 1F);
    }

    /** Called by the client proxy when the server sends the net landing rows. */
    public void handleNets(BlockPos pos, int createdNetId, List<SecurityNetSummary> nets) {
        if (pos == null || !pos.equals(origin)) return;
        securityNets.clear();
        if (nets != null) securityNets.addAll(nets);
        netsLoading = false;

        if (deleteRequested) {
            deleteRequested = false;
            waiting = false;
            stage = Stage.SECURITY_HOME;
            selectedNetId = -1;
            selectedNetName = "";
            linkedKeyName = "";
            pendingKeyHand = -1;
            pendingDoor = null;
            securityDevices.clear();
            securityScroll = 0;
            nameInput.setLength(0);
            securityNotice = "Security net deleted";
            error = "";
            netsScroll = 0;
        }

        netsScroll = Math.max(0, Math.min(netsScroll, netsMaxScroll()));

        if (createdNetId >= 0) {
            waiting = false;
            error = "";
            nameInput.setLength(0);
            for (SecurityNetSummary net : securityNets) {
                if (net.id == createdNetId) {
                    openSecurityNet(net.id, net.name);
                    return;
                }
            }
        }
    }

    /** Called by the client proxy when the server sends one net's devices. */
    public void handleDevices(BlockPos pos, int netId, String netName,
                              String linkedKeyName, List<MonitorDevice> devices) {
        if (stage != Stage.SECURITY || pos == null || !pos.equals(origin)) return;
        if (netId != selectedNetId) return;
        selectedNetName = netName == null ? "" : netName;
        this.linkedKeyName = linkedKeyName == null ? "" : linkedKeyName;
        securityDevices.clear();
        if (devices != null) securityDevices.addAll(devices);
        securityLoading = false;
        securityScroll = Math.max(0, Math.min(securityScroll, securityMaxScroll()));
    }

    /** Called by the client proxy after bind/unlink/rename. */
    public void handleKeyResult(BlockPos pos, int netId, int result, String keyName) {
        if (pos == null || !pos.equals(origin) || netId != selectedNetId) return;
        waiting = false;
        String name = keyName == null ? "" : keyName;

        switch (result) {
            case MessageMonitorKeyResult.BOUND:
                linkedKeyName = name;
                pendingKeyHand = -1;
                securityNotice = "Key linked";
                error = "";
                break;
            case MessageMonitorKeyResult.UNLINKED:
                linkedKeyName = "";
                pendingKeyHand = -1;
                securityNotice = "Key unlinked";
                error = "";
                break;
            case MessageMonitorKeyResult.RENAMED:
                linkedKeyName = name;
                securityNotice = "Key renamed";
                error = "";
                nameInput.setLength(0);
                if (stage == Stage.NAME_KEY) stage = Stage.SECURITY;
                break;
            case MessageMonitorKeyResult.NO_KEY:
                if (stage == Stage.NAME_KEY) error = "No linked key";
                else securityNotice = "No key in hand";
                break;
            case MessageMonitorKeyResult.NO_NET:
                if (stage == Stage.NAME_KEY) error = "No security net";
                else securityNotice = "No security net";
                break;
            case MessageMonitorKeyResult.KEY_ALREADY_LINKED:
                securityNotice = "Key is already linked";
                break;
            case MessageMonitorKeyResult.NET_ALREADY_LINKED:
                securityNotice = "Net has a key";
                break;
            default:
                break;
        }
    }

    private void scrollSecurity(int delta) {
        if (securityLoading) return;
        int amount = GuiScreen.isShiftKeyDown() ? 3 : 1;
        securityScroll = Math.max(0, Math.min(securityMaxScroll(), securityScroll + delta * amount));
    }

    private void scrollNets(int delta) {
        if (netsLoading) return;
        int amount = GuiScreen.isShiftKeyDown() ? 3 : 1;
        netsScroll = Math.max(0, Math.min(netsMaxScroll(), netsScroll + delta * amount));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (stage == Stage.SECURITY) scrollSecurity(wheel > 0 ? -1 : 1);
        else if (stage == Stage.SECURITY_HOME) scrollNets(wheel > 0 ? -1 : 1);
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
