package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.network.MessageMonitorAction;
import com.ntmdoors.network.MessageMonitorBackupOpen;
import com.ntmdoors.network.MessageMonitorDoorScanner;
import com.ntmdoors.network.MessageMonitorDisplay;
import com.ntmdoors.network.MessageMonitorDoorScannerResult;
import com.ntmdoors.network.MessageMonitorKeyResult;
import com.ntmdoors.network.MessageMonitorNetRequest;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.MonitorPasswords;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
import org.lwjgl.opengl.GL11;

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

    enum Stage {
        ASK(MonitorDisplay.STAGE_ASK),
        SET(MonitorDisplay.STAGE_SET),
        CONFIRM(MonitorDisplay.STAGE_CONFIRM),
        ENTER(MonitorDisplay.STAGE_ENTER),
        MENU(MonitorDisplay.STAGE_MENU),
        SECURITY_HOME(MonitorDisplay.STAGE_SECURITY_HOME),
        SECURITY(MonitorDisplay.STAGE_SECURITY),
        NAME_NET(MonitorDisplay.STAGE_NAME_NET),
        NAME_KEY(MonitorDisplay.STAGE_NAME_KEY),
        DELETE_NET(MonitorDisplay.STAGE_DELETE_NET),
        DISPLAY(MonitorDisplay.STAGE_DISPLAY),
        BACKUP(MonitorDisplay.STAGE_BACKUP);

        private final int id;

        Stage(int id) {
            this.id = id;
        }

        private static Stage byId(int id) {
            for (Stage stage : values()) {
                if (stage.id == id) return stage;
            }
            return MENU;
        }
    }

    private static final String[] TASKS = {
            "Security", "Opencomputers", "Factory", "Display", "Notepad", "Terminal Backup"
    };
    private static final String[] SECURITY_EVENTS = {
            "System Shutdown", "System Lockdown", "Trigger Alarms",
            "Disable Keypads", "Disable Scanners", "Disable Sensors",
            "Disable Redstone Gates"
    };
    private static final String[] SECURITY_ACTIONS = {
            "Close all doors", "Open all doors"
    };
    private static final int SECURITY_TAB_HEIGHT = 18;

    static final int ORANGE = 0xFFFF8A00;
    static final int ORANGE_LIGHT = 0xFFFFB454;
    static final int ORANGE_DIM = 0xFF7A3D00;
    static final int ORANGE_GLOW = 0x33FF8A00;
    static final int ORANGE_ERROR = 0xFFFF4A2E;
    static final int TEXT = 0xFFFFD9A8;
    static final int MUTED = 0xFF9A6A3A;

    static final int BLACK = 0xFF000000;
    static final int CASING_DARK = 0xFF080808;
    static final int CASING = 0xFF141414;
    static final int CASING_LIGHT = 0xFF2A2A2A;
    static final int TITLE_BG = 0xFF0C0C0C;
    static final int SCREEN_BG = 0xFF040404;
    static final int FIELD_BG = 0xFF020202;
    static final int BUTTON_BG = 0xFF101010;
    static final int BUTTON_HOVER = 0xFF2A1500;
    static final int ROW_BG = 0xFF080808;
    static final int ROW_HOVER = 0xFF241200;

    static final int TITLE_HEIGHT = 20;
    static final int CLOSE_BUTTON_SIZE = 12;
    static final int FOOTER_HEIGHT = 20;
    static final int TITLE_TEXT_Y = 10;
    static final int FIELD_HEIGHT = 20;
    static final int BUTTON_HEIGHT = 20;
    static final int ROW_HEIGHT = 26;
    static final int ROW_GAP = 6;
    static final int SECURITY_ROW_HEIGHT = 28;
    static final int DOOR_BUTTON_WIDTH = 38;
    static final int DOOR_BUTTON_HEIGHT = 15;
    static final int DOOR_BUTTON_GAP = 4;
    static final int DISPLAY_TILE_GAP = 8;
    static final int DISPLAY_TILE_HEIGHT = 78;
    static final int BACKUP_OPTION_HEIGHT = 36;

    final BlockPos origin;
    final int screenIndex;
    final boolean passive;

    boolean hasPassword;
    boolean passwordSkipped;
    private boolean resumeApplied;
    Stage stage;

    private final StringBuilder password = new StringBuilder();
    private String firstPassword = "";
    private String error = "";
    private boolean waiting;
    int ticks;
    private int selectedTask = -1;
    private int menuScroll;
    private final List<MonitorDevice> securityDevices = new ArrayList<>();
    private final List<SecurityNetSummary> securityNets = new ArrayList<>();
    private boolean securityLoading;
    private boolean netsLoading;
    private int securityScroll;
    private int netsScroll;
    private int eventsScroll;
    private String securityNotice = "";
    private BlockPos pendingDoor;
    private int selectedNetId = -1;
    private String selectedNetName = "";
    private boolean securityEventsTab;
    private final boolean[] securityEvents = new boolean[SecurityNet.EVENT_COUNT];
    final GuiMonitorDisplayController display = new GuiMonitorDisplayController(this);

    private String linkedKeyName = "";
    private int pendingKeyHand = -1;
    private final StringBuilder nameInput = new StringBuilder();
    private Stage nameReturnStage = Stage.SECURITY_HOME;
    private boolean deleteRequested;

    private int lastSentStage = Integer.MIN_VALUE;
    private int lastSentNetId = Integer.MIN_VALUE;
    private boolean lastSentEventsTab;
    private int lastSentNetsScroll = Integer.MIN_VALUE;
    private int lastSentSecurityScroll = Integer.MIN_VALUE;
    private int lastSentEventsScroll = Integer.MIN_VALUE;

    int guiWidth;
    int guiHeight;
    int guiLeft;
    int guiTop;
    int contentLeft;
    int contentRight;
    int contentTop;
    int contentBottom;
    int fieldX;
    int fieldWidth;
    int buttonX;
    int buttonWidth;

    public GuiMonitor(BlockPos origin, boolean hasPassword, boolean passwordSkipped,
                      int screenIndex) {
        this(origin, hasPassword, passwordSkipped, screenIndex, false);
    }

    private GuiMonitor(BlockPos origin, boolean hasPassword, boolean passwordSkipped,
                       int screenIndex, boolean passive) {
        this.origin = origin;
        this.screenIndex = screenIndex;
        this.passwordSkipped = passwordSkipped;
        this.hasPassword = hasPassword;
        this.passive = passive;
        this.stage = hasPassword ? Stage.ENTER : passwordSkipped ? Stage.MENU : Stage.ASK;
    }

    /** A detached terminal page used by the physical monitor renderer. */
    public static GuiMonitor forDisplay(BlockPos origin, int screenIndex) {
        return new GuiMonitor(origin, true, true, screenIndex, true);
    }

    /** Reopens an already-unlocked terminal on its task menu. */
    public static GuiMonitor reopened(BlockPos origin, boolean hasPassword,
                                      boolean passwordSkipped, int screenIndex) {
        GuiMonitor gui = new GuiMonitor(origin, hasPassword, passwordSkipped, screenIndex);
        gui.resumeApplied = true;
        gui.stage = Stage.MENU;
        return gui;
    }

    /** Reopens an already-unlocked terminal directly on one net's device list. */
    public static GuiMonitor forSecurity(BlockPos origin, boolean hasPassword,
                                         int screenIndex, int netId, String netName) {
        GuiMonitor gui = new GuiMonitor(origin, hasPassword, true, screenIndex);
        gui.resumeApplied = true;
        gui.openSecurityNet(netId, netName);
        return gui;
    }

    /** Reopens the terminal directly on the Terminal Backup task. */
    public static GuiMonitor forBackup(BlockPos origin, boolean hasPassword,
                                       boolean passwordSkipped, int screenIndex) {
        GuiMonitor gui = new GuiMonitor(origin, hasPassword, passwordSkipped, screenIndex);
        gui.resumeApplied = true;
        gui.openBackupMenu();
        return gui;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (passive) {
            // Physical monitor screens are already bounded by the block model. Use the whole
            // panel so the terminal does not render as a small window with black margins.
            this.guiWidth = this.width;
            this.guiHeight = this.height;
            this.guiLeft = 0;
            this.guiTop = 0;
        } else {
            int maxWidth = Math.max(1, this.width - 8);
            int maxHeight = Math.max(1, this.height - 8);
            this.guiWidth = Math.min(maxWidth, Math.max(160, this.width * 9 / 10));
            this.guiHeight = Math.min(maxHeight, Math.max(120, this.height * 9 / 10));
            this.guiLeft = (this.width - this.guiWidth) / 2;
            this.guiTop = (this.height - this.guiHeight) / 2;
        }

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
        if (!passive) {
            // Force the first update tick to publish the current page to the desk screens.
            this.lastSentStage = Integer.MIN_VALUE;
            if (!resumeApplied) {
                resumeApplied = true;
                if (hasPassword) {
                    stage = Stage.ENTER;
                } else if (passwordSkipped) {
                    applyResumeState();
                } else {
                    stage = Stage.ASK;
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ticks++;
        if (!passive) {
            publishDisplayState(true);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (!passive) {
            publishDisplayState(false);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!passive) {
            drawDefaultBackground();
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawFrame(mouseX, mouseY);
        drawContent(mouseX, mouseY);
    }

    /** Sends the live page to the desk when it changes, or marks the desk idle on close. */
    private void publishDisplayState(boolean viewing) {
        if (mc.player == null) return;
        if (viewing
                && stage.id == lastSentStage
                && selectedNetId == lastSentNetId
                && securityEventsTab == lastSentEventsTab
                && netsScroll == lastSentNetsScroll
                && securityScroll == lastSentSecurityScroll
                && eventsScroll == lastSentEventsScroll) {
            return;
        }
        lastSentStage = stage.id;
        lastSentNetId = selectedNetId;
        lastSentEventsTab = securityEventsTab;
        lastSentNetsScroll = netsScroll;
        lastSentSecurityScroll = securityScroll;
        lastSentEventsScroll = eventsScroll;
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorDisplay(origin, screenIndex, viewing,
                stage.id, selectedNetId, securityEventsTab,
                netsScroll, securityScroll, eventsScroll));
    }

    // ------------------------------------------------------------ frame

    private void drawFrame(int mouseX, int mouseY) {
        int left = guiLeft;
        int top = guiTop;
        int right = left + guiWidth;
        int bottom = top + guiHeight;

        if (!passive) drawRect(left - 2, top - 2, right + 2, bottom + 2, BLACK);
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
        int statusRight = innerRight - 6 - (passive ? 0 : CLOSE_BUTTON_SIZE + 6);
        String status = statusText();
        fontRenderer.drawString(status, statusRight - fontRenderer.getStringWidth(status),
                top + TITLE_TEXT_Y, MUTED);
        if (!passive) {
            int closeX = closeButtonX();
            int closeY = closeButtonY();
            boolean hover = isHovering(mouseX, mouseY, closeX, closeY,
                    CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
            drawRect(closeX, closeY, closeX + CLOSE_BUTTON_SIZE,
                    closeY + CLOSE_BUTTON_SIZE, hover ? BUTTON_HOVER : BUTTON_BG);
            int border = hover ? ORANGE : ORANGE_DIM;
            drawRect(closeX, closeY, closeX + CLOSE_BUTTON_SIZE, closeY + 1, border);
            drawRect(closeX, closeY + CLOSE_BUTTON_SIZE - 1, closeX + CLOSE_BUTTON_SIZE,
                    closeY + CLOSE_BUTTON_SIZE, border);
            drawRect(closeX, closeY, closeX + 1, closeY + CLOSE_BUTTON_SIZE, border);
            drawRect(closeX + CLOSE_BUTTON_SIZE - 1, closeY, closeX + CLOSE_BUTTON_SIZE,
                    closeY + CLOSE_BUTTON_SIZE, border);
            drawCenteredString(fontRenderer, "X", closeX + CLOSE_BUTTON_SIZE / 2,
                    closeY + 2, hover ? ORANGE_LIGHT : ORANGE);
        }

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
        if (!passive) {
            drawButton("EXIT", quickExitX(), quickExitY(), 44, 14,
                    isHovering(mouseX, mouseY, quickExitX(), quickExitY(), 44, 14));
        }
    }

    private String statusText() {
        if (waiting) return "WORKING";
        if (stage == Stage.SECURITY_HOME) return "SECURITY";
        if (stage == Stage.SECURITY) return securityLoading ? "LOADING" : "SECURITY";
        if (stage == Stage.NAME_NET || stage == Stage.NAME_KEY) return "SECURITY";
        if (stage == Stage.DELETE_NET) return "CONFIRM";
        if (stage == Stage.BACKUP) return "BACKUP";
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
            case DISPLAY:
                display.draw(mouseX, mouseY);
                break;
            case BACKUP:
                drawBackupPage(mouseX, mouseY);
                break;
            default:
                break;
        }
        if (stage != Stage.SECURITY && stage != Stage.SECURITY_HOME
                && stage != Stage.DELETE_NET && stage != Stage.DISPLAY
                && stage != Stage.BACKUP) {
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
        int visible = menuVisibleRows();
        int maxScroll = menuMaxScroll();
        if (menuScroll > maxScroll) menuScroll = maxScroll;

        if (menuShowHeading()) {
            drawCenteredString(fontRenderer, "TASKS", centerX, start - 42, ORANGE_LIGHT);
            drawCenteredString(fontRenderer, "Select a module", centerX, start - 24, MUTED);
        }

        for (int i = 0; i < visible; i++) {
            int index = menuScroll + i;
            if (index >= TASKS.length) break;
            int y = start + i * (ROW_HEIGHT + ROW_GAP);
            boolean hover = isHovering(mouseX, mouseY, x, y, width, ROW_HEIGHT);
            boolean selected = selectedTask == index;
            int fill = hover || selected ? ROW_HOVER : ROW_BG;
            int border = hover || selected ? ORANGE : ORANGE_DIM;

            drawRect(x, y, x + width, y + ROW_HEIGHT, fill);
            drawRect(x, y, x + width, y + 1, border);
            drawRect(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, border);
            drawRect(x, y, x + 1, y + ROW_HEIGHT, border);
            drawRect(x + width - 1, y, x + width, y + ROW_HEIGHT, border);
            if (selected) drawRect(x + 1, y + 1, x + 4, y + ROW_HEIGHT - 1, ORANGE);

            String label = fontRenderer.trimStringToWidth(TASKS[index], width - 34);
            fontRenderer.drawString(label, x + 14, y + 9,
                    hover || selected ? ORANGE_LIGHT : TEXT);
            if (hover || selected) fontRenderer.drawString(">", x + width - 14, y + 9, ORANGE);
        }
        if (maxScroll > 0) {
            int barX = x + width + 4;
            int barTop = start;
            int barHeight = visible * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
            drawRect(barX, barTop, barX + 2, barTop + barHeight, 0xFF050505);
            int thumb = Math.max(10, barHeight * visible / TASKS.length);
            int thumbY = barTop + (barHeight - thumb) * menuScroll / maxScroll;
            drawRect(barX, thumbY, barX + 2, thumbY + thumb, ORANGE);
        }
    }

    // ------------------------------------------------------------ terminal backup

    private void openBackupMenu() {
        stage = Stage.BACKUP;
        selectedTask = 5;
        waiting = false;
        error = "";
    }

    private void drawBackupPage(int mouseX, int mouseY) {
        int centerX = centerX();
        int width = backupOptionWidth();
        int x = backupOptionX();
        int top = backupOptionTop();

        drawCenteredString(fontRenderer, "TERMINAL BACKUP", centerX, top - 38, ORANGE_LIGHT);
        drawCenteredString(fontRenderer, "Choose an operation", centerX, top - 20, MUTED);

        drawBackupOption(x, backupOptionY(0), width, "Initiate backup",
                "Copy this terminal's data onto a key.", mouseX, mouseY);
        drawBackupOption(x, backupOptionY(1), width, "Restore backup",
                "Restore terminal data from a key.", mouseX, mouseY);

        drawButton("Back", contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, contentLeft, contentBottom - 24,
                        64, BUTTON_HEIGHT));
    }

    private void drawBackupOption(int x, int y, int width, String title, String subtitle,
                                  int mouseX, int mouseY) {
        boolean hover = isHovering(mouseX, mouseY, x, y, width, BACKUP_OPTION_HEIGHT);
        int border = hover ? ORANGE : ORANGE_DIM;
        drawRect(x, y, x + width, y + BACKUP_OPTION_HEIGHT, hover ? ROW_HOVER : ROW_BG);
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + BACKUP_OPTION_HEIGHT - 1, x + width, y + BACKUP_OPTION_HEIGHT, border);
        drawRect(x, y, x + 1, y + BACKUP_OPTION_HEIGHT, border);
        drawRect(x + width - 1, y, x + width, y + BACKUP_OPTION_HEIGHT, border);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(title, width - 24),
                x + 12, y + 7, hover ? ORANGE_LIGHT : TEXT);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(subtitle, width - 24),
                x + 12, y + 21, MUTED);
        if (hover) fontRenderer.drawString(">", x + width - 14, y + 14, ORANGE);
    }

    private int backupOptionWidth() {
        return Math.max(170, Math.min(380, contentRight - contentLeft - 40));
    }

    private int backupOptionX() {
        return centerX() - backupOptionWidth() / 2;
    }

    private int backupOptionTop() {
        int available = contentBottom - contentTop;
        int total = BACKUP_OPTION_HEIGHT * 2 + 8;
        return contentTop + Math.max(38, (available - total) / 2 - 10);
    }

    private int backupOptionY(int index) {
        return backupOptionTop() + index * (BACKUP_OPTION_HEIGHT + 8);
    }

    private void openBackupConsole(boolean restore) {
        TerminalBackupContext.set(origin, screenIndex);
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorBackupOpen(origin, restore));
    }

    // display page extracted to GuiMonitorDisplayController

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
        drawSecurityTabs(mouseX, mouseY);
        if (securityEventsTab) {
            drawSecurityEvents(mouseX, mouseY);
        } else {
            drawSecurityDevices(mouseX, mouseY);
        }

        drawButton("Back", contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, contentLeft, contentBottom - 24, 64, BUTTON_HEIGHT));
        int deleteWidth = deleteNetButtonWidth();
        drawDangerButton("Delete net", deleteNetButtonX(), contentBottom - 24,
                deleteWidth, BUTTON_HEIGHT,
                isHovering(mouseX, mouseY, deleteNetButtonX(), contentBottom - 24,
                        deleteWidth, BUTTON_HEIGHT));
        if (!securityEventsTab) drawKeySlotItem();
    }

    private void drawSecurityTabs(int mouseX, int mouseY) {
        int width = securityTabWidth();
        drawTab("DEVICES", securityTabX(0), contentTop, width, SECURITY_TAB_HEIGHT,
                !securityEventsTab,
                isHovering(mouseX, mouseY, securityTabX(0), contentTop,
                        width, SECURITY_TAB_HEIGHT));
        drawTab("EVENTS", securityTabX(1), contentTop, width, SECURITY_TAB_HEIGHT,
                securityEventsTab,
                isHovering(mouseX, mouseY, securityTabX(1), contentTop,
                        width, SECURITY_TAB_HEIGHT));
        drawRect(securityTabX(1) + width, contentTop + SECURITY_TAB_HEIGHT - 1,
                contentRight, contentTop + SECURITY_TAB_HEIGHT, ORANGE_DIM);
    }

    void drawTab(String label, int x, int y, int width, int height,
                 boolean selected, boolean hover) {
        drawRect(x, y, x + width, y + height,
                selected ? BUTTON_HOVER : hover ? ROW_HOVER : ROW_BG);
        int border = selected ? ORANGE : ORANGE_DIM;
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        drawCenteredString(fontRenderer, label, x + width / 2, y + (height - 8) / 2,
                selected ? ORANGE_LIGHT : hover ? TEXT : MUTED);
    }

    private void drawSecurityDevices(int mouseX, int mouseY) {
        int listLeft = securityListLeft();
        int listRight = securityListRight();
        int listTop = securityListTop();
        int listBottom = securityListBottom();
        int leftWidth = Math.max(0, listLeft - contentLeft - 10);
        int leftTop = securityContentTop();

        String netName = fontRenderer.trimStringToWidth(
                selectedNetName.isEmpty() ? "Security net" : selectedNetName, leftWidth);
        drawString(fontRenderer, netName, contentLeft, leftTop + 4, ORANGE_LIGHT);
        drawRect(contentLeft, leftTop + 18, contentLeft + leftWidth, leftTop + 19, ORANGE_DIM);
        String count = securityDevices.size() + (securityDevices.size() == 1 ? " device" : " devices");
        drawString(fontRenderer, count, contentLeft, leftTop + 28, TEXT);
        drawString(fontRenderer, "TERMINAL KEY", contentLeft, leftTop + 42, MUTED);

        String keyName = currentKeyName();
        String shownKey = keyName.isEmpty() ? "No key linked"
                : fontRenderer.trimStringToWidth(keyName, leftWidth);
        drawString(fontRenderer, shownKey, contentLeft, leftTop + 54,
                keyName.isEmpty() ? MUTED : TEXT);
        drawKeySlotFrame();

        int keyButtonY = leftTop + 84;
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
                    contentLeft, leftTop + 104, ORANGE_ERROR);
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
                boolean door = device.type == MonitorDevice.Type.DOOR;
                String offline = !door && !device.loaded ? "OFFLINE" : null;
                int textRight = door ? doorOpenX(rowRight) - 4
                        : device.loaded ? deviceToggleHitX(rowRight) - 4
                        : rowRight - 8 - fontRenderer.getStringWidth(offline) - 4;
                String name = fontRenderer.trimStringToWidth(device.name,
                        Math.max(24, textRight - rowLeft - 6));
                drawString(fontRenderer, name, rowLeft + 6, y + 4,
                        hover ? ORANGE_LIGHT : TEXT);
                drawString(fontRenderer, device.type.displayName(), rowLeft + 6, y + 15, MUTED);
                if (door) {
                    drawButton("Open", doorOpenX(rowRight), doorButtonY(y),
                            DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT,
                            isHovering(mouseX, mouseY, doorOpenX(rowRight), doorButtonY(y),
                                    DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT));
                    drawButton("Close", doorCloseX(rowRight), doorButtonY(y),
                            DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT,
                            isHovering(mouseX, mouseY, doorCloseX(rowRight), doorButtonY(y),
                                    DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT));
                } else if (device.loaded) {
                    String state = device.enabled ? "ON" : "OFF";
                    int stateX = rowRight - 42 - fontRenderer.getStringWidth(state);
                    drawString(fontRenderer, state, stateX, y + 9,
                            device.enabled ? ORANGE_LIGHT : MUTED);
                    drawToggleSwitch(deviceToggleX(rowRight), deviceToggleY(y),
                            device.enabled, hover);
                } else {
                    drawString(fontRenderer, offline,
                            rowRight - 8 - fontRenderer.getStringWidth(offline), y + 9, MUTED);
                }
            }
            drawListScrollbar(rowsTop, rowsBottom, visible, maxScroll,
                    securityDevices.size(), securityScroll);
        }
    }

    private void drawSecurityEvents(int mouseX, int mouseY) {
        int listLeft = securityListLeft();
        int listRight = securityListRight();
        int listTop = securityListTop();
        int listBottom = securityListBottom();
        int leftWidth = Math.max(0, listLeft - contentLeft - 10);
        int leftTop = securityContentTop();

        String netName = fontRenderer.trimStringToWidth(
                selectedNetName.isEmpty() ? "Security net" : selectedNetName, leftWidth);
        drawString(fontRenderer, netName, contentLeft, leftTop + 4, ORANGE_LIGHT);
        drawRect(contentLeft, leftTop + 18, contentLeft + leftWidth, leftTop + 19, ORANGE_DIM);
        drawString(fontRenderer, "Event controls", contentLeft, leftTop + 28, MUTED);
        drawString(fontRenderer, SECURITY_EVENTS.length + " toggles",
                contentLeft, leftTop + 42, TEXT);
        drawString(fontRenderer, SECURITY_ACTIONS.length + " actions",
                contentLeft, leftTop + 54, TEXT);
        drawString(fontRenderer, "Saved per net.", contentLeft, leftTop + 74, MUTED);
        drawString(fontRenderer, "Use the switches", contentLeft, leftTop + 84, MUTED);
        if (!securityNotice.isEmpty()) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(securityNotice, leftWidth),
                    contentLeft, leftTop + 104, ORANGE_ERROR);
        }

        drawRect(listLeft, listTop, listRight, listBottom, 0xFF060606);
        drawRect(listLeft, listTop, listRight, listTop + 1, ORANGE_DIM);
        drawRect(listLeft, listBottom - 1, listRight, listBottom, ORANGE_DIM);
        drawString(fontRenderer, "EVENTS", listLeft + 8, listTop + 6, ORANGE);

        int rowsTop = securityRowsTop();
        int rowsBottom = securityRowsBottom();
        int visible = securityVisibleRows();
        int total = SECURITY_EVENTS.length + SECURITY_ACTIONS.length;
        int maxScroll = Math.max(0, total - visible);
        if (eventsScroll > maxScroll) eventsScroll = maxScroll;
        int rowLeft = listLeft + 6;
        int rowRight = listRight - 8;
        for (int i = 0; i < visible; i++) {
            int index = eventsScroll + i;
            if (index >= total) break;
            int y = rowsTop + i * SECURITY_ROW_HEIGHT;
            boolean hover = isHovering(mouseX, mouseY, rowLeft, y,
                    rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2);
            if (index < SECURITY_EVENTS.length) {
                boolean enabled = securityEvents[index];
                drawRect(rowLeft, y, rowRight, y + SECURITY_ROW_HEIGHT - 2,
                        hover ? ROW_HOVER : ROW_BG);
                drawRect(rowLeft, y, rowRight, y + 1, ORANGE_DIM);
                String label = fontRenderer.trimStringToWidth(
                        SECURITY_EVENTS[index], rowRight - rowLeft - 72);
                drawString(fontRenderer, label, rowLeft + 6, y + 4,
                        hover ? ORANGE_LIGHT : TEXT);
                String state = enabled ? "ON" : "OFF";
                int stateX = rowRight - 42 - fontRenderer.getStringWidth(state);
                drawString(fontRenderer, state, stateX, y + 9,
                        enabled ? ORANGE_LIGHT : MUTED);
                drawToggleSwitch(rowRight - 34, y + 8, enabled, hover);
            } else {
                drawEventActionRow(index - SECURITY_EVENTS.length, y,
                        rowLeft, rowRight, hover);
            }
        }
        drawListScrollbar(rowsTop, rowsBottom, visible, maxScroll, total, eventsScroll);
    }

    private void drawEventActionRow(int actionIndex, int y, int rowLeft, int rowRight,
                                    boolean hover) {
        boolean blocked = actionIndex == 1
                && (securityEvents[SecurityNet.EVENT_SYSTEM_SHUTDOWN]
                || securityEvents[SecurityNet.EVENT_SYSTEM_LOCKDOWN]);
        drawRect(rowLeft, y, rowRight, y + SECURITY_ROW_HEIGHT - 2,
                hover ? ROW_HOVER : ROW_BG);
        drawRect(rowLeft, y, rowRight, y + 1, ORANGE_DIM);
        String label = fontRenderer.trimStringToWidth(
                SECURITY_ACTIONS[actionIndex], rowRight - rowLeft - 64);
        drawString(fontRenderer, label, rowLeft + 6, y + 4,
                blocked ? MUTED : hover ? ORANGE_LIGHT : TEXT);
        String state = blocked ? "BLOCKED" : "RUN";
        drawString(fontRenderer, state,
                rowRight - 6 - fontRenderer.getStringWidth(state), y + 9,
                blocked ? ORANGE_ERROR : hover ? ORANGE_LIGHT : ORANGE);
    }

    private void drawToggleSwitch(int x, int y, boolean on, boolean hover) {
        int width = 26;
        int height = 12;
        drawRect(x, y, x + width, y + height, on ? 0xFF3A1A00 : 0xFF160C04);
        int border = on ? ORANGE : hover ? ORANGE_LIGHT : ORANGE_DIM;
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        int knobX = on ? x + width - 12 : x + 2;
        drawRect(knobX, y + 2, knobX + 10, y + height - 2,
                on ? ORANGE_LIGHT : hover ? ORANGE : MUTED);
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
        int y = securityContentTop() + 64;
        drawRect(x - 1, y - 1, x + 19, y + 19, ORANGE_DIM);
        drawRect(x, y, x + 18, y + 18, FIELD_BG);
        drawRect(x, y, x + 18, y + 1, 0xFF1A0E04);
    }

    private void drawKeySlotItem() {
        ItemStack key = keyDisplayStack();
        if (key.isEmpty()) return;
        // This page is also drawn into the physical monitor screen while the world is being
        // rendered. Never leave depth testing disabled: the next world renderer would draw its
        // faces without a depth buffer and a nearby multi-part model would look like a crystal.
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(key, contentLeft + 1,
                securityContentTop() + 65);
        RenderHelper.disableStandardItemLighting();
        if (depth) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        if (lighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
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

    void drawButton(String label, int x, int y, int width, int height, boolean hover) {
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
        if (mouseButton == 0 && !passive
                && isHovering(mouseX, mouseY, closeButtonX(), closeButtonY(),
                        CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)) {
            playClick();
            mc.displayGuiScreen(null);
            return;
        }
        if (mouseButton == 0 && !passive
                && isHovering(mouseX, mouseY, quickExitX(), quickExitY(), 44, 14)) {
            playClick();
            mc.displayGuiScreen(null);
            return;
        }
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
                int visible = menuVisibleRows();
                for (int i = 0; i < visible; i++) {
                    int index = menuScroll + i;
                    if (index >= TASKS.length) break;
                    int y = start + i * (ROW_HEIGHT + ROW_GAP);
                    if (isHovering(mouseX, mouseY, x, y, width, ROW_HEIGHT)) {
                        playClick();
                        selectedTask = index;
                        if (index == 0) openSecurityHome();
                        else if (index == 3) display.open();
                        else if (index == 4) {
                            mc.displayGuiScreen(new GuiNotepad(origin, screenIndex,
                                    hasPassword, passwordSkipped));
                        } else if (index == 5) {
                            openBackupMenu();
                        }
                        return;
                    }
                }
            } else if (stage == Stage.DISPLAY) {
                display.handleClick(mouseX, mouseY);
                return;
            } else if (stage == Stage.BACKUP) {
                int x = backupOptionX();
                int width = backupOptionWidth();
                if (isHovering(mouseX, mouseY, x, backupOptionY(0),
                        width, BACKUP_OPTION_HEIGHT)) {
                    playClick();
                    openBackupConsole(false);
                    return;
                }
                if (isHovering(mouseX, mouseY, x, backupOptionY(1),
                        width, BACKUP_OPTION_HEIGHT)) {
                    playClick();
                    openBackupConsole(true);
                    return;
                }
                if (isHovering(mouseX, mouseY, contentLeft, contentBottom - 24,
                        64, BUTTON_HEIGHT)) {
                    playClick();
                    stage = Stage.MENU;
                    return;
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
                int tabWidth = securityTabWidth();
                if (isHovering(mouseX, mouseY, securityTabX(0), contentTop,
                        tabWidth, SECURITY_TAB_HEIGHT)) {
                    playClick();
                    securityEventsTab = false;
                    securityNotice = "";
                    return;
                }
                if (isHovering(mouseX, mouseY, securityTabX(1), contentTop,
                        tabWidth, SECURITY_TAB_HEIGHT)) {
                    playClick();
                    securityEventsTab = true;
                    securityNotice = "";
                    return;
                }

                if (isHovering(mouseX, mouseY, contentLeft, contentBottom - 24,
                        64, BUTTON_HEIGHT)) {
                    playClick();
                    waiting = false;
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

                if (securityEventsTab) {
                    int rowLeft = securityListLeft() + 6;
                    int rowRight = securityListRight() - 8;
                    int rowsTop = securityRowsTop();
                    int visible = securityVisibleRows();
                    int total = SECURITY_EVENTS.length + SECURITY_ACTIONS.length;
                    for (int i = 0; i < visible; i++) {
                        int index = eventsScroll + i;
                        if (index >= total) break;
                        int y = rowsTop + i * SECURITY_ROW_HEIGHT;
                        if (isHovering(mouseX, mouseY, rowLeft, y,
                                rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2)) {
                            playClick();
                            if (index < SECURITY_EVENTS.length) {
                                toggleSecurityEvent(index);
                            } else {
                                runDoorAction(index - SECURITY_EVENTS.length == 1);
                            }
                            return;
                        }
                    }
                    return;
                }

                int leftTop = securityContentTop();
                if (isHovering(mouseX, mouseY, contentLeft, leftTop + 64, 18, 18)) {
                    if (linkedKeyName.isEmpty()) {
                        playClick();
                        pendingKeyHand = heldTerminalKeyHand();
                        securityNotice = pendingKeyHand >= 0 ? "" : "Hold a key";
                    }
                    return;
                }

                int leftWidth = Math.max(0, securityListLeft() - contentLeft - 10);
                int keyButtonY = leftTop + 84;
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
                    MonitorDevice device = securityDevices.get(index);
                    if (device.type == MonitorDevice.Type.DOOR) {
                        int buttonY = doorButtonY(y);
                        if (isHovering(mouseX, mouseY, doorOpenX(rowRight), buttonY,
                                DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT)) {
                            playClick();
                            sendDoorAction(device, true);
                            return;
                        }
                        if (isHovering(mouseX, mouseY, doorCloseX(rowRight), buttonY,
                                DOOR_BUTTON_WIDTH, DOOR_BUTTON_HEIGHT)) {
                            playClick();
                            sendDoorAction(device, false);
                            return;
                        }
                    } else if (device.loaded && isHovering(mouseX, mouseY,
                            deviceToggleHitX(rowRight), y + 6, 58, 16)) {
                        playClick();
                        sendDeviceToggle(device, !device.enabled);
                        return;
                    }
                    if (isHovering(mouseX, mouseY, rowLeft, y,
                            rowRight - rowLeft, SECURITY_ROW_HEIGHT - 2)) {
                        playClick();
                        openDevice(device);
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
            if (stage == Stage.DISPLAY) {
                display.closeToMenu();
                return;
            }
            if (stage == Stage.BACKUP) {
                stage = Stage.MENU;
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
                if (securityEventsTab) scrollEvents(-1);
                else scrollSecurity(-1);
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                if (securityEventsTab) scrollEvents(1);
                else scrollSecurity(1);
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
            if (stage == Stage.MENU) {
                if (keyCode == Keyboard.KEY_UP) {
                    scrollMenu(-1);
                    return;
                }
                if (keyCode == Keyboard.KEY_DOWN) {
                    scrollMenu(1);
                    return;
                }
            }
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
                this.passwordSkipped = false;
                password.setLength(0);
                firstPassword = "";
                error = "";
                selectedTask = -1;
                applyResumeState();
                break;
            case MessageMonitorState.SKIP_OK:
                this.passwordSkipped = true;
                password.setLength(0);
                firstPassword = "";
                error = "";
                applyResumeState();
                break;
            case MessageMonitorState.CHECK_OK:
                this.passwordSkipped = false;
                password.setLength(0);
                error = "";
                applyResumeState();
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

    private int closeButtonX() {
        return guiLeft + guiWidth - 5 - 6 - CLOSE_BUTTON_SIZE;
    }

    private int closeButtonY() {
        return guiTop + 9;
    }

    private int quickExitX() {
        return guiLeft + guiWidth - 53;
    }

    private int quickExitY() {
        return guiTop + guiHeight - 22;
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
        int max = passive ? 420 : 360;
        return Math.max(120, Math.min(max, contentRight - contentLeft - 40));
    }

    private int menuVisibleRows() {
        int height = contentBottom - contentTop;
        int available = Math.max(1, height - 30);
        int stride = ROW_HEIGHT + ROW_GAP;
        return Math.max(1, Math.min(TASKS.length,
                (available + ROW_GAP) / stride));
    }

    private int menuMaxScroll() {
        return Math.max(0, TASKS.length - menuVisibleRows());
    }

    private void scrollMenu(int delta) {
        menuScroll = Math.max(0, Math.min(menuMaxScroll(), menuScroll + delta));
    }

    private boolean menuShowHeading() {
        int height = contentBottom - contentTop;
        int visible = menuVisibleRows();
        int total = visible * ROW_HEIGHT + (visible - 1) * ROW_GAP;
        return height >= total + 51;
    }

    private int menuStart() {
        int height = contentBottom - contentTop;
        int visible = menuVisibleRows();
        int total = visible * ROW_HEIGHT + (visible - 1) * ROW_GAP;
        int pad = menuShowHeading() ? 42 : 30;
        return contentTop + Math.max(pad, (height - total) / 2);
    }

    private int doorCloseX(int rowRight) {
        return rowRight - 6 - DOOR_BUTTON_WIDTH;
    }

    private int doorOpenX(int rowRight) {
        return doorCloseX(rowRight) - DOOR_BUTTON_GAP - DOOR_BUTTON_WIDTH;
    }

    private int doorButtonY(int rowY) {
        return rowY + 6;
    }

    private int deviceToggleX(int rowRight) {
        return rowRight - 34;
    }

    private int deviceToggleY(int rowY) {
        return rowY + 8;
    }

    /** The switch plus its ON/OFF label, so a click anywhere in the state block toggles. */
    private int deviceToggleHitX(int rowRight) {
        return rowRight - 66;
    }

    private int securityListLeft() {
        int total = contentRight - contentLeft;
        int leftWidth = Math.max(80, Math.min(150, total * 35 / 100));
        return contentLeft + leftWidth + 10;
    }

    private int securityListRight() {
        return contentRight;
    }

    private int securityContentTop() {
        return contentTop + SECURITY_TAB_HEIGHT + 4;
    }

    private int securityTabWidth() {
        return Math.max(54, Math.min(88, (contentRight - contentLeft - 4) / 2));
    }

    private int securityTabX(int index) {
        return contentLeft + index * (securityTabWidth() + 4);
    }

    private int securityListTop() {
        return contentTop + 24 + (stage == Stage.SECURITY ? SECURITY_TAB_HEIGHT + 4 : 0);
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

    private void resetSecurityEvents() {
        securityEventsTab = false;
        eventsScroll = 0;
        for (int i = 0; i < securityEvents.length; i++) {
            securityEvents[i] = false;
        }
    }

    /** Restores this monitor's last stable page after the password gate. */
    private void applyResumeState() {
        if (passive || mc.world == null) return;
        net.minecraft.tileentity.TileEntity tile = mc.world.getTileEntity(origin);
        if (!(tile instanceof TileEntityMonitorDesk)) {
            stage = Stage.MENU;
            return;
        }
        TileEntityMonitorDesk desk = (TileEntityMonitorDesk) tile;
        int stageId = desk.getScreenResumeStage(screenIndex);
        int netId = desk.getScreenResumeNetId(screenIndex);
        boolean eventsTab = desk.getScreenResumeEventsTab(screenIndex);
        int netsScroll = desk.getScreenResumeNetsScroll(screenIndex);
        int securityScroll = desk.getScreenResumeSecurityScroll(screenIndex);
        int eventsScroll = desk.getScreenResumeEventsScroll(screenIndex);

        switch (stageId) {
            case MonitorDisplay.STAGE_SECURITY:
                if (netId >= 0) {
                    openSecurityNet(netId, displayNetName(desk, netId));
                    securityEventsTab = eventsTab;
                    this.securityScroll = securityScroll;
                    this.eventsScroll = eventsScroll;
                } else {
                    openSecurityHome();
                    this.netsScroll = netsScroll;
                }
                break;
            case MonitorDisplay.STAGE_SECURITY_HOME:
            case MonitorDisplay.STAGE_NAME_NET:
                openSecurityHome();
                this.netsScroll = netsScroll;
                break;
            case MonitorDisplay.STAGE_NAME_KEY:
            case MonitorDisplay.STAGE_DELETE_NET:
                if (netId >= 0) {
                    openSecurityNet(netId, displayNetName(desk, netId));
                    securityEventsTab = eventsTab;
                    this.securityScroll = securityScroll;
                    this.eventsScroll = eventsScroll;
                } else {
                    openSecurityHome();
                    this.netsScroll = netsScroll;
                }
                break;
            case MonitorDisplay.STAGE_MENU:
            default:
                stage = Stage.MENU;
                break;
        }
    }

    private static String displayNetName(TileEntityMonitorDesk desk, int netId) {
        MonitorDisplay.NetData data = desk.getDisplayNetData(netId);
        if (data != null) return data.name;
        for (SecurityNetSummary net : desk.getDisplayNets()) {
            if (net.id == netId) return net.name;
        }
        return "";
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
        resetSecurityEvents();
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
        resetSecurityEvents();
        error = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(origin,
                MessageMonitorNetRequest.REQUEST_DEVICES, netId, "", 0));
    }

    private void openDevice(MonitorDevice device) {
        if (device == null) return;
        if (securityEvents[SecurityNet.EVENT_SYSTEM_SHUTDOWN]) {
            securityNotice = "System shutdown";
            return;
        }
        if (device.type == MonitorDevice.Type.DOOR
                && securityEvents[SecurityNet.EVENT_SYSTEM_LOCKDOWN]) {
            securityNotice = "System lockdown";
            return;
        }
        if (device.type == MonitorDevice.Type.KEYPAD
                && securityEvents[SecurityNet.EVENT_DISABLE_KEYPADS]) {
            securityNotice = "Keypad disabled";
            return;
        }
        if (device.type == MonitorDevice.Type.SCANNER
                && securityEvents[SecurityNet.EVENT_DISABLE_SCANNERS]) {
            securityNotice = "Scanner disabled";
            return;
        }
        if (device.type == MonitorDevice.Type.MOTION_SENSOR
                && securityEvents[SecurityNet.EVENT_DISABLE_SENSORS]) {
            securityNotice = "Sensor disabled";
            return;
        }
        if (device.type == MonitorDevice.Type.REDSTONE_GATE
                && securityEvents[SecurityNet.EVENT_DISABLE_REDSTONE_GATES]) {
            securityNotice = "Gate disabled";
            return;
        }
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
        MonitorReturnContext.set(origin, hasPassword, screenIndex,
                selectedNetId, selectedNetName);
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
            MonitorReturnContext.set(origin, hasPassword, screenIndex,
                    selectedNetId, selectedNetName);
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

    private void toggleSecurityEvent(int index) {
        if (index < 0 || index >= securityEvents.length || selectedNetId < 0) return;
        securityNotice = "";
        boolean enabled = !securityEvents[index];
        securityEvents[index] = enabled;
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                origin, MessageMonitorNetRequest.TOGGLE_EVENT, selectedNetId, "", 0,
                index, enabled));
    }

    private void sendDeviceToggle(MonitorDevice device, boolean enabled) {
        if (device == null || selectedNetId < 0 || !device.loaded) return;
        securityNotice = enabled ? "Device enabled" : "Device disabled";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                origin, MessageMonitorNetRequest.TOGGLE_DEVICE, selectedNetId, "", 0,
                device.pos, enabled));
    }

    private void sendDoorAction(MonitorDevice device, boolean open) {
        if (device == null || selectedNetId < 0) return;
        if (open && (securityEvents[SecurityNet.EVENT_SYSTEM_SHUTDOWN]
                || securityEvents[SecurityNet.EVENT_SYSTEM_LOCKDOWN])) {
            securityNotice = "Blocked by system event";
            return;
        }
        securityNotice = "";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                origin, open ? MessageMonitorNetRequest.OPEN_DOOR
                        : MessageMonitorNetRequest.CLOSE_DOOR,
                selectedNetId, "", 0, device.pos));
    }

    private void runDoorAction(boolean open) {
        if (selectedNetId < 0) return;
        if (open && (securityEvents[SecurityNet.EVENT_SYSTEM_SHUTDOWN]
                || securityEvents[SecurityNet.EVENT_SYSTEM_LOCKDOWN])) {
            securityNotice = "Blocked by system event";
            return;
        }
        securityNotice = open ? "Opening doors" : "Closing doors";
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorNetRequest(
                origin, open ? MessageMonitorNetRequest.OPEN_ALL_DOORS
                        : MessageMonitorNetRequest.CLOSE_ALL_DOORS,
                selectedNetId, "", 0));
    }

    void playClick() {
        if (mc.player != null) mc.player.playSound(ModSounds.GUI_CLICK, 0.5F, 1F);
    }

    void goToMenu() {
        stage = Stage.MENU;
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
            resetSecurityEvents();
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
                              String linkedKeyName, int eventMask,
                              List<MonitorDevice> devices) {
        if (stage != Stage.SECURITY || pos == null || !pos.equals(origin)) return;
        if (netId != selectedNetId) return;
        selectedNetName = netName == null ? "" : netName;
        this.linkedKeyName = linkedKeyName == null ? "" : linkedKeyName;
        for (int i = 0; i < securityEvents.length; i++) {
            securityEvents[i] = (eventMask & (1 << i)) != 0;
        }
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

    private int eventsMaxScroll() {
        int total = SECURITY_EVENTS.length + SECURITY_ACTIONS.length;
        return Math.max(0, total - securityVisibleRows());
    }

    private void scrollEvents(int delta) {
        int amount = GuiScreen.isShiftKeyDown() ? 3 : 1;
        eventsScroll = Math.max(0, Math.min(eventsMaxScroll(),
                eventsScroll + delta * amount));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (stage == Stage.SECURITY) {
            if (securityEventsTab) scrollEvents(wheel > 0 ? -1 : 1);
            else scrollSecurity(wheel > 0 ? -1 : 1);
        } else if (stage == Stage.SECURITY_HOME) {
            scrollNets(wheel > 0 ? -1 : 1);
        } else if (stage == Stage.MENU) {
            scrollMenu(wheel > 0 ? -1 : 1);
        }
    }

    // ------------------------------------------------------------ passive display

    /** Keeps a detached display page sized to the physical screen texture. */
    public void ensureDisplaySize(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (this.width != width || this.height != height
                || this.fontRenderer != mc.fontRenderer) {
            setWorldAndResolution(mc, width, height);
        }
    }

    public void setDisplayTicks(int ticks) {
        this.ticks = ticks;
    }

    public void setDisplayPasswordState(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public void setDisplayScroll(int netsScroll, int securityScroll, int eventsScroll) {
        this.netsScroll = Math.max(0, netsScroll);
        this.securityScroll = Math.max(0, securityScroll);
        this.eventsScroll = Math.max(0, eventsScroll);
    }

    public void showDisplayStage(int stageId) {
        this.stage = Stage.byId(stageId);
        this.netsLoading = false;
        this.securityLoading = false;
        this.waiting = false;
        this.error = "";
        this.securityNotice = "";
        this.pendingDoor = null;
        this.pendingKeyHand = -1;
        this.deleteRequested = false;
        this.selectedTask = -1;
        this.selectedNetId = -1;
        this.selectedNetName = "";
        this.linkedKeyName = "";
        this.nameInput.setLength(0);
        this.password.setLength(0);
        this.firstPassword = "";
        this.securityEventsTab = false;
        this.resetSecurityEvents();
        this.display.resetForDisplay();
    }

    public void showDisplayWallpaper(String wallpaper) {
        showDisplayStage(MonitorDisplay.STAGE_DISPLAY);
        this.display.showPassiveWallpaper(wallpaper);
    }

    public void showDisplayHome(List<SecurityNetSummary> nets) {
        showDisplayStage(MonitorDisplay.STAGE_SECURITY_HOME);
        this.securityNets.clear();
        if (nets != null) this.securityNets.addAll(nets);
        this.netsLoading = false;
    }

    public void showDisplayNet(int netId, String netName, List<MonitorDevice> devices,
                               int eventMask, String keyName, boolean eventsTab) {
        showDisplayStage(MonitorDisplay.STAGE_SECURITY);
        this.selectedNetId = netId;
        this.selectedNetName = netName == null ? "" : netName;
        this.securityDevices.clear();
        if (devices != null) this.securityDevices.addAll(devices);
        for (int i = 0; i < this.securityEvents.length; i++) {
            this.securityEvents[i] = (eventMask & (1 << i)) != 0;
        }
        this.linkedKeyName = keyName == null ? "" : keyName;
        this.securityEventsTab = eventsTab;
        this.securityLoading = false;
    }

    public void showDisplayDelete(String netName) {
        showDisplayStage(MonitorDisplay.STAGE_DELETE_NET);
        this.selectedNetName = netName == null ? "" : netName;
    }

    boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Page controllers live outside the screen class, so they cannot reach the protected field. */
    FontRenderer font() {
        return fontRenderer;
    }

    private static String mask(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append('*');
        return out.toString();
    }
}
