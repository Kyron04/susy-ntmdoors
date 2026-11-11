package com.ntmdoors.client;

import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.network.MessageMonitorWallpaper;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import static com.ntmdoors.client.GuiMonitor.*;

/**
 * Owns the terminal Display page (builtin wallpapers + custom URL). Extracted from
 * {@link GuiMonitor} to keep that screen's responsibilities separated.
 */
final class GuiMonitorDisplayController {

    private final GuiMonitor gui;

    boolean customTab;
    String wallpaper = "";
    final StringBuilder url = new StringBuilder();
    String notice = "";
    boolean urlFocused;

    GuiMonitorDisplayController(GuiMonitor gui) {
        this.gui = gui;
    }

    void resetForDisplay() {
        customTab = false;
        wallpaper = "";
        url.setLength(0);
        notice = "";
        urlFocused = false;
    }

    void open() {
        gui.stage = GuiMonitor.Stage.DISPLAY;
        resetForDisplay();
        wallpaper = currentWallpaper();
        if (MonitorWallpaper.isUrl(wallpaper)) {
            customTab = true;
            String u = MonitorWallpaper.urlOf(wallpaper);
            if (u != null) url.append(u);
        }
        urlFocused = customTab;
    }

    void closeToMenu() {
        gui.goToMenu();
        notice = "";
        urlFocused = false;
    }

    void showPassiveWallpaper(String value) {
        resetForDisplay();
        wallpaper = value == null ? "" : value;
        if (MonitorWallpaper.isUrl(wallpaper)) {
            customTab = true;
            String u = MonitorWallpaper.urlOf(wallpaper);
            if (u != null) url.append(u);
        }
    }

    private String currentWallpaper() {
        if (gui.mc.world == null) return "";
        net.minecraft.tileentity.TileEntity tile = gui.mc.world.getTileEntity(gui.origin);
        if (!(tile instanceof TileEntityMonitorDesk)) return "";
        return ((TileEntityMonitorDesk) tile).getScreenWallpaper(gui.screenIndex);
    }

    private int tabWidth() {
        return Math.max(54, Math.min(110, (gui.contentRight - gui.contentLeft - 4) / 2));
    }

    private int gridTop() {
        return gui.contentTop + DISPLAY_TAB_HEIGHT + 10;
    }

    private int gridBottom() {
        return gui.contentBottom - 30;
    }

    private int cellWidth() {
        int columns = 3;
        return Math.max(40, (gui.contentRight - gui.contentLeft
                - (columns - 1) * DISPLAY_TILE_GAP) / columns);
    }

    private int cellHeight() {
        int rows = 2;
        int available = gridBottom() - gridTop();
        int height = (available - (rows - 1) * DISPLAY_TILE_GAP) / rows;
        return Math.max(30, Math.min(DISPLAY_TILE_HEIGHT, height));
    }

    private int tileX(int index) {
        return gui.contentLeft + (index % 3) * (cellWidth() + DISPLAY_TILE_GAP);
    }

    private int tileY(int index) {
        return gridTop() + (index / 3) * (cellHeight() + DISPLAY_TILE_GAP);
    }

    void draw(int mouseX, int mouseY) {
        int tabWidth = tabWidth();
        gui.drawTab("BUILT-IN", gui.contentLeft, gui.contentTop, tabWidth, DISPLAY_TAB_HEIGHT,
                !customTab,
                gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentTop, tabWidth, DISPLAY_TAB_HEIGHT));
        gui.drawTab("CUSTOM", gui.contentLeft + tabWidth + 4, gui.contentTop, tabWidth, DISPLAY_TAB_HEIGHT,
                customTab,
                gui.isHovering(mouseX, mouseY, gui.contentLeft + tabWidth + 4, gui.contentTop,
                        tabWidth, DISPLAY_TAB_HEIGHT));
        gui.drawRect(gui.contentLeft + tabWidth * 2 + 4, gui.contentTop + DISPLAY_TAB_HEIGHT - 1,
                gui.contentRight, gui.contentTop + DISPLAY_TAB_HEIGHT, ORANGE_DIM);

        if (customTab) {
            drawCustomTab(mouseX, mouseY);
        } else {
            drawBuiltinTab(mouseX, mouseY);
        }

        gui.drawButton("Back", gui.contentLeft, gui.contentBottom - 24, 64, BUTTON_HEIGHT,
                gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentBottom - 24,
                        64, BUTTON_HEIGHT));
        if (!notice.isEmpty()) {
            String n = gui.font().trimStringToWidth(notice,
                    gui.contentRight - gui.contentLeft - 80);
            gui.drawString(gui.font(), n,
                    gui.contentRight - gui.font().getStringWidth(n),
                    gui.contentTop + 4, ORANGE_ERROR);
        }
    }

    private void drawBuiltinTab(int mouseX, int mouseY) {
        java.util.List<MonitorWallpaper.Builtin> builtins = MonitorWallpaper.builtins();
        int total = builtins.size() + 1;
        for (int i = 0; i < total; i++) {
            String value = i == 0 ? "" : MonitorWallpaper.builtin(builtins.get(i - 1).id);
            int x = tileX(i);
            int y = tileY(i);
            int width = cellWidth();
            int height = cellHeight();
            boolean hover = gui.isHovering(mouseX, mouseY, x, y, width, height);
            boolean selected = wallpaper.equals(value);
            drawWallpaperTile(x, y, width, height, value, selected, hover);
        }
    }

    private void drawWallpaperTile(int x, int y, int width, int height,
                                   String value, boolean selected, boolean hover) {
        int fill = selected ? 0xFF1A0E04 : hover ? ROW_HOVER : ROW_BG;
        int border = selected || hover ? ORANGE : ORANGE_DIM;
        gui.drawRect(x, y, x + width, y + height, fill);
        gui.drawRect(x, y, x + width, y + 1, border);
        gui.drawRect(x, y + height - 1, x + width, y + height, border);
        gui.drawRect(x, y, x + 1, y + height, border);
        gui.drawRect(x + width - 1, y, x + width, y + height, border);

        int imageX = x + 5;
        int imageY = y + 5;
        int imageWidth = Math.max(1, width - 10);
        int imageHeight = Math.max(1, height - 23);
        gui.drawRect(imageX, imageY, imageX + imageWidth, imageY + imageHeight, 0xFF020202);

        if (value.isEmpty()) {
            int cx = imageX + imageWidth / 2;
            int cy = imageY + imageHeight / 2;
            gui.drawRect(cx - 14, cy - 9, cx + 14, cy + 9, 0xFF0A0A0A);
            gui.drawRect(cx - 12, cy - 7, cx + 12, cy + 7, 0xFF241200);
            gui.drawCenteredString(gui.font(), "TERMINAL", cx, y + height - 14,
                    selected ? ORANGE_LIGHT : TEXT);
        } else {
            String id = MonitorWallpaper.builtinId(value);
            boolean exists = id != null && DisplayImages.builtinExists(id);
            if (exists) {
                net.minecraft.util.ResourceLocation texture = MonitorWallpaper.texture(value);
                if (texture != null) {
                    GlStateManager.color(1F, 1F, 1F, 1F);
                    gui.mc.getTextureManager().bindTexture(texture);
                    gui.drawModalRectWithCustomSizedTexture(imageX, imageY, 0F, 0F,
                            imageWidth, imageHeight, imageWidth, imageHeight);
                }
            } else {
                gui.drawCenteredString(gui.font(), "MISSING", imageX + imageWidth / 2,
                        imageY + imageHeight / 2 - 4, ORANGE_ERROR);
            }
            String label = MonitorWallpaper.label(value);
            String shown = gui.font().trimStringToWidth(label, width - 12);
            gui.drawCenteredString(gui.font(), shown, x + width / 2, y + height - 14,
                    selected ? ORANGE_LIGHT : hover ? TEXT : MUTED);
        }

        if (selected) {
            gui.drawRect(x + 1, y + 1, x + 4, y + height - 1, ORANGE);
        }
    }

    private void drawCustomTab(int mouseX, int mouseY) {
        int top = gridTop();
        gui.drawString(gui.font(), "IMAGE URL", gui.contentLeft, top - 2, ORANGE);
        int fieldY = top + 14;
        drawUrlField(fieldY);

        int buttonY = fieldY + FIELD_HEIGHT + 8;
        gui.drawButton("Apply", gui.contentLeft, buttonY, 70, BUTTON_HEIGHT,
                gui.isHovering(mouseX, mouseY, gui.contentLeft, buttonY, 70, BUTTON_HEIGHT));
        gui.drawButton("Paste", gui.contentLeft + 78, buttonY, 70, BUTTON_HEIGHT,
                gui.isHovering(mouseX, mouseY, gui.contentLeft + 78, buttonY, 70, BUTTON_HEIGHT));
        gui.drawButton("Clear", gui.contentLeft + 156, buttonY, 70, BUTTON_HEIGHT,
                gui.isHovering(mouseX, mouseY, gui.contentLeft + 156, buttonY, 70, BUTTON_HEIGHT));

        int infoY = buttonY + 30;
        gui.drawString(gui.font(), "Direct PNG or JPG URL.", gui.contentLeft, infoY, MUTED);
        gui.drawString(gui.font(), "Static images only, no video.",
                gui.contentLeft, infoY + 12, MUTED);
        gui.drawString(gui.font(), "Downloaded by each viewer's client.",
                gui.contentLeft, infoY + 24, MUTED);
        if (MonitorWallpaper.isUrl(wallpaper)) {
            String current = "ACTIVE: " + MonitorWallpaper.urlOf(wallpaper);
            gui.drawString(gui.font(), gui.font().trimStringToWidth(
                            current, gui.contentRight - gui.contentLeft),
                    gui.contentLeft, infoY + 42, ORANGE_LIGHT);
        }
    }

    private void drawUrlField(int y) {
        int width = gui.contentRight - gui.contentLeft;
        int border = urlFocused ? ORANGE : ORANGE_DIM;
        gui.drawRect(gui.contentLeft - 1, y - 1, gui.contentLeft + width + 1, y + FIELD_HEIGHT + 1, border);
        gui.drawRect(gui.contentLeft, y, gui.contentLeft + width, y + FIELD_HEIGHT, FIELD_BG);
        gui.drawRect(gui.contentLeft, y + FIELD_HEIGHT - 1, gui.contentLeft + width,
                y + FIELD_HEIGHT, 0xFF1A0E04);

        String text = gui.font().trimStringToWidth(url.toString(), width - 16);
        int textY = y + (FIELD_HEIGHT - 8) / 2;
        if (!text.isEmpty()) {
            gui.font().drawString(text, gui.contentLeft + 8, textY, TEXT);
        }
        if (urlFocused && (gui.ticks / 10) % 2 == 0) {
            int cursorX = gui.contentLeft + 8 + gui.font().getStringWidth(text);
            gui.drawRect(cursorX, y + 4, cursorX + 1, y + FIELD_HEIGHT - 4, ORANGE_LIGHT);
        }
    }

    void handleClick(int mouseX, int mouseY) {
        int tabWidth = tabWidth();
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentTop, tabWidth, DISPLAY_TAB_HEIGHT)) {
            gui.playClick();
            customTab = false;
            urlFocused = false;
            notice = "";
            return;
        }
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft + tabWidth + 4, gui.contentTop,
                tabWidth, DISPLAY_TAB_HEIGHT)) {
            gui.playClick();
            customTab = true;
            urlFocused = true;
            notice = "";
            return;
        }
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentBottom - 24,
                64, BUTTON_HEIGHT)) {
            gui.playClick();
            closeToMenu();
            return;
        }

        if (!customTab) {
            java.util.List<MonitorWallpaper.Builtin> builtins = MonitorWallpaper.builtins();
            int total = builtins.size() + 1;
            for (int i = 0; i < total; i++) {
                int x = tileX(i);
                int y = tileY(i);
                if (gui.isHovering(mouseX, mouseY, x, y, cellWidth(), cellHeight())) {
                    gui.playClick();
                    String value = i == 0 ? "" : MonitorWallpaper.builtin(builtins.get(i - 1).id);
                    applyWallpaper(value);
                    return;
                }
            }
            return;
        }

        int top = gridTop();
        int fieldY = top + 14;
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft, fieldY,
                gui.contentRight - gui.contentLeft, FIELD_HEIGHT)) {
            urlFocused = true;
            return;
        }
        int buttonY = fieldY + FIELD_HEIGHT + 8;
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft, buttonY, 70, BUTTON_HEIGHT)) {
            gui.playClick();
            applyUrl();
            return;
        }
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft + 78, buttonY, 70, BUTTON_HEIGHT)) {
            gui.playClick();
            pasteUrl();
            return;
        }
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft + 156, buttonY, 70, BUTTON_HEIGHT)) {
            gui.playClick();
            url.setLength(0);
            applyWallpaper("");
            return;
        }
        urlFocused = false;
    }

    void handleKey(char typedChar, int keyCode) {
        if (customTab) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                applyUrl();
                return;
            }
            if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (url.length() > 0) {
                    url.deleteCharAt(url.length() - 1);
                }
                return;
            }
            if (GuiScreen.isCtrlKeyDown()) {
                if (keyCode == Keyboard.KEY_V) pasteUrl();
                return;
            }
            if (typedChar > 32 && typedChar != 127
                    && url.length() < DISPLAY_URL_LENGTH) {
                url.append(typedChar);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            customTab = true;
            urlFocused = true;
            notice = "";
        }
    }

    private void applyUrl() {
        String value = url.toString().trim();
        if (!MonitorWallpaper.isValidHttpUrl(value)) {
            notice = "Use an http:// or https:// image URL";
            return;
        }
        applyWallpaper(MonitorWallpaper.url(value));
    }

    private void pasteUrl() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null) return;
        url.setLength(0);
        for (int i = 0; i < clip.length() && url.length() < DISPLAY_URL_LENGTH; i++) {
            char c = clip.charAt(i);
            if (c > 32 && c != 127) url.append(c);
        }
        urlFocused = true;
    }

    private void applyWallpaper(String value) {
        String normalized = MonitorWallpaper.normalize(value);
        wallpaper = normalized;
        notice = normalized.isEmpty() ? "Terminal display restored" : "Display applied";
        urlFocused = false;
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorWallpaper(
                gui.origin, gui.screenIndex, normalized));
    }
}
