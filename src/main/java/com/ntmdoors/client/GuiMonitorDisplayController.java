package com.ntmdoors.client;

import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.network.MessageMonitorWallpaper;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

import static com.ntmdoors.client.GuiMonitor.*;

/**
 * Owns the terminal Display page. Only the packaged built-in wallpapers are offered; the monitor
 * never accepts or downloads custom URLs.
 */
final class GuiMonitorDisplayController {

    private final GuiMonitor gui;

    String wallpaper = "";

    GuiMonitorDisplayController(GuiMonitor gui) {
        this.gui = gui;
    }

    void resetForDisplay() {
        wallpaper = "";
    }

    void open() {
        gui.stage = GuiMonitor.Stage.DISPLAY;
        resetForDisplay();
        wallpaper = currentWallpaper();
    }

    void closeToMenu() {
        gui.goToMenu();
    }

    void showPassiveWallpaper(String value) {
        resetForDisplay();
        wallpaper = value == null ? "" : value;
    }

    private String currentWallpaper() {
        if (gui.mc.world == null) return "";
        net.minecraft.tileentity.TileEntity tile = gui.mc.world.getTileEntity(gui.origin);
        if (!(tile instanceof TileEntityMonitorDesk)) return "";
        return ((TileEntityMonitorDesk) tile).getScreenWallpaper(gui.screenIndex);
    }

    private int gridTop() {
        return gui.contentTop + 18;
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
        gui.drawString(gui.font(), "BUILT-IN WALLPAPERS", gui.contentLeft, gui.contentTop + 4,
                ORANGE);
        drawBuiltinTab(mouseX, mouseY);

        gui.drawButton("Back", gui.contentLeft, gui.contentBottom - 24, 64, BUTTON_HEIGHT,
                gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentBottom - 24,
                        64, BUTTON_HEIGHT));
    }

    private void drawBuiltinTab(int mouseX, int mouseY) {
        List<MonitorWallpaper.Builtin> builtins = MonitorWallpaper.builtins();
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

    void handleClick(int mouseX, int mouseY) {
        if (gui.isHovering(mouseX, mouseY, gui.contentLeft, gui.contentBottom - 24,
                64, BUTTON_HEIGHT)) {
            gui.playClick();
            closeToMenu();
            return;
        }

        List<MonitorWallpaper.Builtin> builtins = MonitorWallpaper.builtins();
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
    }

    private void applyWallpaper(String value) {
        String normalized = MonitorWallpaper.normalize(value);
        wallpaper = normalized;
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorWallpaper(
                gui.origin, gui.screenIndex, normalized));
    }
}
