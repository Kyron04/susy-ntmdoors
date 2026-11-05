package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.gui.ContainerTerminalBackup;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Collections;

/**
 * Terminal Backup console. In initiate mode the left key receives this terminal's snapshot with
 * Upload, and the middle Backup button copies that key onto the blank right key. Restore mode uses
 * one key slot and overwrites the terminal with that key's snapshot. Both modes use the same
 * ten-second progress frames and looping sound as the keycard programmer's legacy backup.
 */
@SideOnly(Side.CLIENT)
public class GuiTerminalBackup extends GuiContainer {

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DARK_TEXT = 0xFF2B2B2E;
    private static final float BUTTON_VOLUME = 0.8F;
    private static final int BACKUP_FRAME_WIDTH = 876;
    private static final int BACKUP_FRAME_HEIGHT = 64;

    private static final ResourceLocation[] BACKUP_FRAMES = {
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_000.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_025.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_050.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_075.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_100.png")
    };

    private final TileEntityMonitorDesk tile;
    private final boolean restore;
    private String status = "";
    private int statusTicks;

    public GuiTerminalBackup(InventoryPlayer playerInventory, TileEntityMonitorDesk tile,
                             boolean restore) {
        super(new ContainerTerminalBackup(playerInventory, tile, restore));
        this.tile = tile;
        this.restore = restore;
        this.xSize = ContainerTerminalBackup.GUI_WIDTH;
        this.ySize = ContainerTerminalBackup.GUI_HEIGHT;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw after JEI/HEI's DrawScreenEvent.Post pass so names and NBT stay on top.
        drawSlotTooltip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // A tooltip drawn last frame leaves lighting and rescale-normal enabled; reset first.
        GuiContainerStyle.resetGuiState();

        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0F);
        GlStateManager.color(1F, 1F, 1F, 1F);

        GuiContainerStyle.drawCasing(xSize, ySize);
        drawRect(4, 20, xSize - 4, 138, 0xFF0B0B0D);
        drawRect(5, 21, xSize - 5, 137, 0xFF1B1B1E);
        drawRect(4, 140, xSize - 4, 228, 0xFF0B0B0D);
        drawRect(5, 141, xSize - 5, 227, 0xFF1B1B1E);

        if (!restore) {
            drawButton(ContainerTerminalBackup.UPLOAD_BUTTON_X,
                    ContainerTerminalBackup.UPLOAD_BUTTON_Y,
                    ContainerTerminalBackup.UPLOAD_BUTTON_WIDTH,
                    ContainerTerminalBackup.UPLOAD_BUTTON_HEIGHT, mouseX, mouseY);
        }
        drawButton(ContainerTerminalBackup.BACKUP_BUTTON_X,
                ContainerTerminalBackup.BACKUP_BUTTON_Y,
                ContainerTerminalBackup.BACKUP_BUTTON_WIDTH,
                ContainerTerminalBackup.BACKUP_BUTTON_HEIGHT, mouseX, mouseY);
        drawButton(ContainerTerminalBackup.BACK_BUTTON_X,
                ContainerTerminalBackup.BACK_BUTTON_Y,
                ContainerTerminalBackup.BACK_BUTTON_WIDTH,
                ContainerTerminalBackup.BACK_BUTTON_HEIGHT, mouseX, mouseY);
        drawBackupFrame(tile.getBackupFrame());
        drawSlotFrames();
        // Gui.drawRect leaves texture 2D disabled; item rendering follows this pass.
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GlStateManager.popMatrix();
    }

    private void drawSlotFrames() {
        for (Slot slot : inventorySlots.inventorySlots) {
            GuiContainerStyle.drawSlotFrame(slot.xPos, slot.yPos);
        }
    }

    /** Draws one of the five supplied progress frames at the GUI bar size. */
    private void drawBackupFrame(int frame) {
        int index = Math.max(0, Math.min(BACKUP_FRAMES.length - 1, frame));
        mc.getTextureManager().bindTexture(BACKUP_FRAMES[index]);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawScaledCustomSizeModalRect(ContainerTerminalBackup.BAR_X,
                ContainerTerminalBackup.BAR_Y, 0F, 0F,
                BACKUP_FRAME_WIDTH, BACKUP_FRAME_HEIGHT,
                ContainerTerminalBackup.BAR_WIDTH, ContainerTerminalBackup.BAR_HEIGHT,
                BACKUP_FRAME_WIDTH, BACKUP_FRAME_HEIGHT);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GlStateManager.pushAttrib();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        fontRenderer.drawString("Terminal Backup", 44, 7, DARK_TEXT);
        drawCenteredString(fontRenderer, restore ? "Restore Backup" : "Initiate Backup",
                xSize / 2, 23, COLOUR);
        drawCenteredString(fontRenderer, restore ? "BACKUP" : "SOURCE", 32, 31, MUTED);
        if (!restore) {
            drawCenteredString(fontRenderer, "BLANK", 144, 31, MUTED);
            drawButtonLabel("Upload", ContainerTerminalBackup.UPLOAD_BUTTON_X,
                    ContainerTerminalBackup.UPLOAD_BUTTON_Y,
                    ContainerTerminalBackup.UPLOAD_BUTTON_WIDTH,
                    ContainerTerminalBackup.UPLOAD_BUTTON_HEIGHT);
        }
        drawButtonLabel(restore ? "Restore" : "Backup",
                ContainerTerminalBackup.BACKUP_BUTTON_X,
                ContainerTerminalBackup.BACKUP_BUTTON_Y,
                ContainerTerminalBackup.BACKUP_BUTTON_WIDTH,
                ContainerTerminalBackup.BACKUP_BUTTON_HEIGHT);
        drawButtonLabel("Back", ContainerTerminalBackup.BACK_BUTTON_X,
                ContainerTerminalBackup.BACK_BUTTON_Y,
                ContainerTerminalBackup.BACK_BUTTON_WIDTH,
                ContainerTerminalBackup.BACK_BUTTON_HEIGHT);

        String line = statusTicks > 0 ? status : tile.getBackupNotice();
        if (line.isEmpty()) {
            if (tile.isBackingUp()) {
                line = tile.isRestoreMode() ? "Restoring..." : "Backing up...";
            } else if (tile.isBackupSuccess()) {
                line = tile.isRestoreMode() ? "Restore complete" : "Backup complete";
            }
        }
        if (!line.isEmpty()) {
            drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(line, xSize - 12),
                    xSize / 2, 100, MUTED);
        }

        if (restore) {
            drawCenteredString(fontRenderer, "Restore overwrites this terminal",
                    xSize / 2, 110, MUTED);
            drawCenteredString(fontRenderer, "with the key's saved data.",
                    xSize / 2, 120, MUTED);
            drawCenteredString(fontRenderer, "The key is not consumed.",
                    xSize / 2, 130, MUTED);
        } else {
            drawCenteredString(fontRenderer, "Upload writes terminal data",
                    xSize / 2, 110, MUTED);
            drawCenteredString(fontRenderer, "to the left key.",
                    xSize / 2, 120, MUTED);
            drawCenteredString(fontRenderer, "Backup copies it.",
                    xSize / 2, 130, MUTED);
        }

        GlStateManager.popAttrib();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int x = mouseX - guiLeft;
            int y = mouseY - guiTop;

            if (inRect(x, y, ContainerTerminalBackup.BACK_BUTTON_X,
                    ContainerTerminalBackup.BACK_BUTTON_Y,
                    ContainerTerminalBackup.BACK_BUTTON_WIDTH,
                    ContainerTerminalBackup.BACK_BUTTON_HEIGHT)) {
                playClick();
                if (!TerminalBackupContext.reopenTerminal(tile.hasPassword(),
                        tile.isPasswordSkipped())) {
                    mc.displayGuiScreen(null);
                }
                return;
            }

            if (!restore && inRect(x, y, ContainerTerminalBackup.UPLOAD_BUTTON_X,
                    ContainerTerminalBackup.UPLOAD_BUTTON_Y,
                    ContainerTerminalBackup.UPLOAD_BUTTON_WIDTH,
                    ContainerTerminalBackup.UPLOAD_BUTTON_HEIGHT)) {
                playClick();
                upload();
                return;
            }

            if (inRect(x, y, ContainerTerminalBackup.BACKUP_BUTTON_X,
                    ContainerTerminalBackup.BACKUP_BUTTON_Y,
                    ContainerTerminalBackup.BACKUP_BUTTON_WIDTH,
                    ContainerTerminalBackup.BACKUP_BUTTON_HEIGHT)) {
                playClick();
                if (restore) {
                    restore();
                } else {
                    backup();
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void upload() {
        if (tile.isBackingUp()) {
            setStatus("Operation already running");
            return;
        }
        ItemStack source = inventorySlots.getSlot(TileEntityMonitorDesk.BACKUP_SLOT_LEFT).getStack();
        if (!TileEntityMonitorDesk.isBackupKey(source)) {
            setStatus("Insert a key in the left slot");
            return;
        }
        status = "";
        statusTicks = 0;
        mc.playerController.windowClick(inventorySlots.windowId,
                ContainerTerminalBackup.UPLOAD_ACTION_SLOT, 0, ClickType.PICKUP, mc.player);
    }

    private void backup() {
        if (tile.isBackingUp()) {
            setStatus("Operation already running");
            return;
        }
        ItemStack source = inventorySlots.getSlot(TileEntityMonitorDesk.BACKUP_SLOT_LEFT).getStack();
        ItemStack target = inventorySlots.getSlot(TileEntityMonitorDesk.BACKUP_SLOT_RIGHT).getStack();
        TileEntityMonitorDesk.BackupFailure failure =
                TileEntityMonitorDesk.getBackupFailure(source, target);
        if (failure != TileEntityMonitorDesk.BackupFailure.NONE) {
            setStatus(backupFailureStatus(failure));
            return;
        }
        status = "";
        statusTicks = 0;
        mc.playerController.windowClick(inventorySlots.windowId,
                ContainerTerminalBackup.BACKUP_ACTION_SLOT, 0, ClickType.PICKUP, mc.player);
    }

    private void restore() {
        if (tile.isBackingUp()) {
            setStatus("Operation already running");
            return;
        }
        ItemStack source = inventorySlots.getSlot(TileEntityMonitorDesk.BACKUP_SLOT_LEFT).getStack();
        TileEntityMonitorDesk.RestoreFailure failure =
                TileEntityMonitorDesk.getRestoreFailure(source);
        if (failure != TileEntityMonitorDesk.RestoreFailure.NONE) {
            setStatus(restoreFailureStatus(failure));
            return;
        }
        status = "";
        statusTicks = 0;
        mc.playerController.windowClick(inventorySlots.windowId,
                ContainerTerminalBackup.RESTORE_ACTION_SLOT, 0, ClickType.PICKUP, mc.player);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && TerminalBackupContext.reopenTerminal(
                tile.hasPassword(), tile.isPasswordSkipped())) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** Slot tooltip drawn after super.drawScreen so JEI/HEI cannot cover it. */
    private void drawSlotTooltip(int mouseX, int mouseY) {
        Slot slot = findSlot(mouseX, mouseY);
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (!stack.isEmpty()) {
            this.renderToolTip(stack, mouseX, mouseY);
            GuiContainerStyle.resetGuiState();
            return;
        }

        String name = ghostNameFor(slot.slotNumber);
        if (name != null) {
            GuiContainerStyle.drawSimpleTooltip(fontRenderer, Collections.singletonList(name),
                    mouseX, mouseY, width, height);
        }
    }

    /** Vanilla's own hovered-slot pass stays empty so the tooltip is only drawn once. */
    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
    }

    private Slot findSlot(int mouseX, int mouseY) {
        for (Slot slot : inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private String ghostNameFor(int slot) {
        if (restore) {
            return slot == TileEntityMonitorDesk.BACKUP_SLOT_LEFT
                    ? "Terminal Backup Key" : null;
        }
        return slot == TileEntityMonitorDesk.BACKUP_SLOT_LEFT
                || slot == TileEntityMonitorDesk.BACKUP_SLOT_RIGHT
                ? "Terminal Backup Key" : null;
    }

    private void setStatus(String text) {
        status = text == null ? "" : text;
        statusTicks = status.isEmpty() ? 0 : 80;
    }

    private static String backupFailureStatus(TileEntityMonitorDesk.BackupFailure failure) {
        switch (failure) {
            case EMPTY:
                return "Insert a key in both slots";
            case INVALID_ITEM:
                return "Both must be Backup Keys";
            case NO_DATA:
                return "Left key has no backup";
            case TARGET_NOT_FRESH:
                return "The right key must be empty";
            default:
                return "";
        }
    }

    private static String restoreFailureStatus(TileEntityMonitorDesk.RestoreFailure failure) {
        switch (failure) {
            case EMPTY:
                return "Insert a backup key";
            case INVALID_ITEM:
                return "That is not a Backup Key";
            case NO_DATA:
                return "That key has no backup";
            default:
                return "";
        }
    }

    private void playClick() {
        if (mc.player != null) {
            mc.player.playSound(ModSounds.BUTTON9, BUTTON_VOLUME, 1F);
        }
    }

    private void drawButton(int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hover = isHovering(mouseX, mouseY, x, y, width, height);
        drawRect(x, y, x + width, y + height, hover ? 0xFF5C5C60 : 0xFF3E3E42);
        drawRect(x, y, x + width, y + 1, 0xFFB4B4B4);
        drawRect(x, y, x + 1, y + height, 0xFFB4B4B4);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF151517);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF151517);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        int mx = mouseX - guiLeft;
        int my = mouseY - guiTop;
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private static boolean inRect(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private void drawButtonLabel(String text, int x, int y, int width, int height) {
        drawCenteredString(fontRenderer, text, x + width / 2,
                y + (height - fontRenderer.FONT_HEIGHT) / 2, COLOUR);
    }
}
