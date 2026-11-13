package com.ntmdoors.client.screen;

import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern CRT Security Terminal Monitor Screen for Fabric 1.20.1.
 * Supports Password Gate, Security Network management, Device control,
 * System Events (Lockdown, Alarms, Shutdown), Wallpaper personalization,
 * and direct launches for Notepad and Terminal Backup.
 */
@Environment(EnvType.CLIENT)
public class MonitorScreen extends Screen {

    public static final int PANEL_WIDTH = 380;
    public static final int PANEL_HEIGHT = 240;

    public static final String[] SECURITY_EVENTS = {
            "System Shutdown",
            "System Lockdown",
            "Trigger Alarms",
            "Disable Keypads",
            "Disable Scanners",
            "Disable Sensors",
            "Disable Redstone Gates"
    };

    private static final String[] PRESET_WALLPAPERS = {
            "default", "amber_matrix", "blueprint", "security_grid", "radiation", "terminal_dark"
    };

    private final BlockPos deskPos;
    private final int screenIndex;
    private final boolean hasPassword;
    private boolean authenticated;

    // Tabs: 0 = Security, 1 = Wallpapers, 2 = Notepad, 3 = Backup
    private int activeTab = 0;

    // Security Net Sub-tab: 0 = Devices, 1 = Events
    private int securitySubTab = 0;

    // Password State
    private TextFieldWidget passwordInput;
    private String passwordError = "";

    // Security State
    private final List<SecurityNetSummary> nets = new ArrayList<>();
    private int selectedNetId = -1;
    private String selectedNetName = "";
    private int selectedNetEventMask = 0;
    private String selectedNetKeyName = "";
    private final List<MonitorDevice> devices = new ArrayList<>();
    private int deviceScroll = 0;
    private static final int DEVICES_PER_PAGE = 5;

    // Wallpaper State
    private TextFieldWidget wallpaperInput;

    public MonitorScreen(BlockPos deskPos, int screenIndex, boolean hasPassword, boolean authenticated) {
        super(Text.literal("Security Terminal OS"));
        this.deskPos = deskPos;
        this.screenIndex = screenIndex;
        this.hasPassword = hasPassword;
        this.authenticated = authenticated;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();

        if (authenticated && nets.isEmpty()) {
            ModMessages.sendMonitorNetRequest(deskPos, -1);
        }
    }

    public void updateNets(List<SecurityNetSummary> newNets) {
        nets.clear();
        if (newNets != null) nets.addAll(newNets);
        if (selectedNetId == -1 && !nets.isEmpty()) {
            selectNet(nets.get(0).id);
        } else {
            rebuildWidgets();
        }
    }

    public void updateDevices(int netId, String name, int eventMask, String keyName, List<MonitorDevice> newDevices) {
        this.selectedNetId = netId;
        this.selectedNetName = name == null ? "" : name;
        this.selectedNetEventMask = eventMask;
        this.selectedNetKeyName = keyName == null ? "" : keyName;
        this.devices.clear();
        if (newDevices != null) this.devices.addAll(newDevices);
        rebuildWidgets();
    }

    public void onPasswordSuccess() {
        this.authenticated = true;
        this.passwordError = "";
        ModMessages.sendMonitorNetRequest(deskPos, -1);
        rebuildWidgets();
    }

    public void onPasswordFail() {
        this.passwordError = "Access Denied: Invalid Password";
        rebuildWidgets();
    }

    private void selectNet(int netId) {
        this.selectedNetId = netId;
        this.deviceScroll = 0;
        ModMessages.sendMonitorNetRequest(deskPos, netId);
    }

    private void rebuildWidgets() {
        clearChildren();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        if (!authenticated) {
            initPasswordWidgets(left, top);
            return;
        }

        // Top Navigation Tabs
        int tabW = 82;
        int tabH = 18;
        int tabY = top + 18;

        addDrawableChild(ButtonWidget.builder(Text.literal(activeTab == 0 ? "> Security <" : "Security"), b -> {
            activeTab = 0;
            rebuildWidgets();
        }).dimensions(left + 8, tabY, tabW, tabH).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(activeTab == 1 ? "> Display <" : "Display"), b -> {
            activeTab = 1;
            rebuildWidgets();
        }).dimensions(left + 94, tabY, tabW, tabH).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Notepad"), b -> {
            ModMessages.sendNotepadAction(deskPos, (byte) 0, -1, "");
        }).dimensions(left + 180, tabY, tabW, tabH).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("USB Backup"), b -> {
            ModMessages.sendMonitorBackupOpen(deskPos, false);
        }).dimensions(left + 266, tabY, tabW, tabH).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> close())
                .dimensions(left + PANEL_WIDTH - 24, top + 4, 18, 14).build());

        if (activeTab == 0) {
            initSecurityWidgets(left, top);
        } else if (activeTab == 1) {
            initWallpaperWidgets(left, top);
        }
    }

    private void initPasswordWidgets(int left, int top) {
        int centerX = left + PANEL_WIDTH / 2;
        int formY = top + 80;

        if (hasPassword) {
            passwordInput = new TextFieldWidget(textRenderer, centerX - 80, formY, 160, 20, Text.literal("Password"));
            passwordInput.setMaxLength(32);
            addSelectableChild(passwordInput);

            addDrawableChild(ButtonWidget.builder(Text.literal("Login"), b -> {
                String pass = passwordInput.getText();
                if (!pass.isEmpty()) {
                    ModMessages.sendMonitorAction(deskPos, 2, pass, 0);
                }
            }).dimensions(centerX - 80, formY + 28, 76, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                    .dimensions(centerX + 4, formY + 28, 76, 20).build());
        } else {
            // Password setup prompt
            passwordInput = new TextFieldWidget(textRenderer, centerX - 80, formY, 160, 20, Text.literal("New Password"));
            passwordInput.setMaxLength(32);
            addSelectableChild(passwordInput);

            addDrawableChild(ButtonWidget.builder(Text.literal("Set Password"), b -> {
                String pass = passwordInput.getText();
                if (!pass.isEmpty()) {
                    ModMessages.sendMonitorAction(deskPos, 0, pass, 0);
                }
            }).dimensions(centerX - 80, formY + 28, 76, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Skip Setup"), b -> {
                ModMessages.sendMonitorAction(deskPos, 1, "", 0);
            }).dimensions(centerX + 4, formY + 28, 76, 20).build());
        }
    }

    private void initSecurityWidgets(int left, int top) {
        int contentY = top + 40;

        // Left Column: Net Selector List
        int netListX = left + 8;
        int netListW = 100;
        int netListH = 150;

        int ny = contentY + 2;
        for (SecurityNetSummary net : nets) {
            final int nid = net.id;
            boolean isSel = (nid == selectedNetId);
            String label = (isSel ? "> " : "") + (net.name.isEmpty() ? "Net #" + nid : net.name);
            if (label.length() > 14) label = label.substring(0, 13) + "..";

            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> selectNet(nid))
                    .dimensions(netListX, ny, netListW, 16).build());
            ny += 18;
            if (ny > contentY + netListH - 20) break;
        }

        // Net control buttons at bottom of left column
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Net"), b -> {
            ModMessages.sendMonitorAction(deskPos, 3, "Sector " + (nets.size() + 1), 0);
            ModMessages.sendMonitorNetRequest(deskPos, -1);
        }).dimensions(netListX, top + PANEL_HEIGHT - 36, 48, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("- Del"), b -> {
            if (selectedNetId >= 0) {
                ModMessages.sendMonitorAction(deskPos, 4, "", selectedNetId);
                selectedNetId = -1;
                ModMessages.sendMonitorNetRequest(deskPos, -1);
            }
        }).dimensions(netListX + 52, top + PANEL_HEIGHT - 36, 48, 16).build());

        // Right Area: Net Details
        int rightX = left + 114;
        int rightW = PANEL_WIDTH - 122;

        if (selectedNetId >= 0) {
            // Sub-tabs: Devices vs Events
            addDrawableChild(ButtonWidget.builder(Text.literal(securitySubTab == 0 ? "[Devices]" : "Devices"), b -> {
                securitySubTab = 0;
                rebuildWidgets();
            }).dimensions(rightX, contentY, 80, 16).build());

            addDrawableChild(ButtonWidget.builder(Text.literal(securitySubTab == 1 ? "[System Events]" : "System Events"), b -> {
                securitySubTab = 1;
                rebuildWidgets();
            }).dimensions(rightX + 84, contentY, 96, 16).build());

            if (securitySubTab == 0) {
                initDeviceView(rightX, contentY + 20, rightW);
            } else {
                initEventsView(rightX, contentY + 20, rightW);
            }
        }
    }

    private void initDeviceView(int rightX, int startY, int rightW) {
        // Global door actions
        addDrawableChild(ButtonWidget.builder(Text.literal("Open All"), b -> {
            for (MonitorDevice d : devices) {
                if (d.type == MonitorDevice.Type.DOOR) {
                    ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 0, d.pos, 0, false);
                }
            }
        }).dimensions(rightX, startY, 68, 15).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close All"), b -> {
            for (MonitorDevice d : devices) {
                if (d.type == MonitorDevice.Type.DOOR) {
                    ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 1, d.pos, 0, false);
                }
            }
        }).dimensions(rightX + 72, startY, 68, 15).build());

        // Paging controls
        int maxPages = Math.max(1, (int) Math.ceil((double) devices.size() / DEVICES_PER_PAGE));
        int currentPage = deviceScroll / DEVICES_PER_PAGE;

        if (currentPage > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                deviceScroll = Math.max(0, deviceScroll - DEVICES_PER_PAGE);
                rebuildWidgets();
            }).dimensions(rightX + rightW - 36, startY, 16, 15).build());
        }
        if (currentPage + 1 < maxPages) {
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                deviceScroll = Math.min(devices.size() - 1, deviceScroll + DEVICES_PER_PAGE);
                rebuildWidgets();
            }).dimensions(rightX + rightW - 18, startY, 16, 15).build());
        }

        // List devices for current page
        int rowY = startY + 20;
        int end = Math.min(devices.size(), deviceScroll + DEVICES_PER_PAGE);
        for (int i = deviceScroll; i < end; i++) {
            MonitorDevice dev = devices.get(i);
            int rowIdx = i;

            if (dev.type == MonitorDevice.Type.DOOR) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Open"), b -> {
                    ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 0, dev.pos, 0, false);
                }).dimensions(rightX + rightW - 74, rowY, 34, 14).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> {
                    ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 1, dev.pos, 0, false);
                }).dimensions(rightX + rightW - 38, rowY, 36, 14).build());
            } else {
                String btnText = dev.enabled ? "ON" : "OFF";
                addDrawableChild(ButtonWidget.builder(Text.literal(btnText), b -> {
                    MonitorDevice current = devices.get(rowIdx);
                    boolean newState = !current.enabled;
                    ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 2, current.pos, 0, newState);
                    devices.set(rowIdx, current.withState(newState, current.loaded));
                    b.setMessage(Text.literal(newState ? "ON" : "OFF"));
                }).dimensions(rightX + rightW - 48, rowY, 46, 14).build());
            }

            rowY += 22;
        }
    }

    private void initEventsView(int rightX, int startY, int rightW) {
        int evY = startY + 2;
        for (int i = 0; i < SECURITY_EVENTS.length; i++) {
            final int evIdx = i;
            boolean active = (selectedNetEventMask & (1 << i)) != 0;
            String text = (active ? "[ON]  " : "[OFF] ") + SECURITY_EVENTS[i];

            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {
                boolean newState = (selectedNetEventMask & (1 << evIdx)) == 0;
                ModMessages.sendMonitorDeviceControl(deskPos, selectedNetId, 3, BlockPos.ORIGIN, evIdx, newState);
                if (newState) {
                    selectedNetEventMask |= (1 << evIdx);
                } else {
                    selectedNetEventMask &= ~(1 << evIdx);
                }
                b.setMessage(Text.literal((newState ? "[ON]  " : "[OFF] ") + SECURITY_EVENTS[evIdx]));
            }).dimensions(rightX, evY, rightW - 8, 16).build());

            evY += 19;
        }
    }

    private void initWallpaperWidgets(int left, int top) {
        int contentY = top + 50;
        int colW = 110;

        // Presets
        for (int i = 0; i < PRESET_WALLPAPERS.length; i++) {
            String preset = PRESET_WALLPAPERS[i];
            int col = i % 3;
            int row = i / 3;
            int px = left + 14 + col * 120;
            int py = contentY + row * 26;

            addDrawableChild(ButtonWidget.builder(Text.literal(preset), b -> {
                ModMessages.sendMonitorWallpaper(deskPos, (byte) screenIndex, preset);
            }).dimensions(px, py, colW, 20).build());
        }

        // Custom URL input
        int customY = contentY + 70;
        wallpaperInput = new TextFieldWidget(textRenderer, left + 14, customY, 240, 20, Text.literal("Wallpaper URL"));
        wallpaperInput.setMaxLength(256);
        addSelectableChild(wallpaperInput);

        addDrawableChild(ButtonWidget.builder(Text.literal("Apply URL"), b -> {
            String url = wallpaperInput.getText().trim();
            if (!url.isEmpty()) {
                ModMessages.sendMonitorWallpaper(deskPos, (byte) screenIndex, url);
            }
        }).dimensions(left + 260, customY, 90, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Outer Monitor CRT Casing
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF080808);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF2A2A2A);
        context.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, 0xFF0C0C0C);

        // Header Title
        String titleStr = "SECURITY MONITOR TERMINAL - DISPLAY #" + screenIndex;
        context.drawText(textRenderer, titleStr, left + 8, top + 6, 0xFFFF8A00, false);

        if (!authenticated) {
            renderPasswordState(context, left, top);
        } else {
            if (activeTab == 0) {
                renderSecurityState(context, left, top);
            } else if (activeTab == 1) {
                renderWallpaperState(context, left, top);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPasswordState(DrawContext context, int left, int top) {
        int centerX = left + PANEL_WIDTH / 2;
        if (hasPassword) {
            context.drawCenteredTextWithShadow(textRenderer, "SECURITY LOCKOUT: AUTHENTICATION REQUIRED", centerX, top + 55, 0xFFFFB454);
            if (passwordInput != null) passwordInput.render(context, 0, 0, 0);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "FIRST TIME SETUP: TERMINAL PASSWORD", centerX, top + 55, 0xFFFFB454);
            context.drawCenteredTextWithShadow(textRenderer, "Configure a passcode or skip to allow unrestricted access", centerX, top + 68, 0xFF9A6A3A);
            if (passwordInput != null) passwordInput.render(context, 0, 0, 0);
        }

        if (!passwordError.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, passwordError, centerX, top + 140, 0xFFFF4A2E);
        }
    }

    private void renderSecurityState(DrawContext context, int left, int top) {
        // Divider line between left net list and right detail pane
        int divX = left + 110;
        context.fill(divX, top + 38, divX + 1, top + PANEL_HEIGHT - 10, 0xFF2A2B2E);

        if (selectedNetId >= 0 && securitySubTab == 0) {
            int rightX = left + 114;
            int rightW = PANEL_WIDTH - 122;
            int startY = top + 80;

            int end = Math.min(devices.size(), deviceScroll + DEVICES_PER_PAGE);
            int rowY = startY;
            for (int i = deviceScroll; i < end; i++) {
                MonitorDevice dev = devices.get(i);
                String tag = "[" + dev.type.displayName() + "] ";
                String name = dev.name;
                if (textRenderer.getWidth(tag + name) > rightW - 80) {
                    name = textRenderer.trimToWidth(name, rightW - 80 - textRenderer.getWidth(tag) - 8) + "..";
                }

                int color = dev.enabled ? 0xFFFFD9A8 : 0xFF7A3D00;
                context.drawText(textRenderer, tag + name, rightX + 2, rowY + 3, color, false);
                rowY += 22;
            }

            if (devices.isEmpty()) {
                context.drawText(textRenderer, "No devices bound to this network.", rightX + 8, startY + 20, 0xFF9A6A3A, false);
                context.drawText(textRenderer, "Use a Terminal Relay Key to link devices.", rightX + 8, startY + 34, 0xFF7A3D00, false);
            }
        }
    }

    private void renderWallpaperState(DrawContext context, int left, int top) {
        context.drawText(textRenderer, "Select a preset theme or enter a texture path / image URL:", left + 14, top + 40, 0xFFFFB454, false);
        if (wallpaperInput != null) wallpaperInput.render(context, 0, 0, 0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
