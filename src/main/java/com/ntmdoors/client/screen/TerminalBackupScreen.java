package com.ntmdoors.client.screen;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.sound.BackupLoopSound;
import com.ntmdoors.screen.TerminalBackupScreenHandler;
import com.ntmdoors.sound.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Modern Fabric 1.20.1 Client Screen for Terminal Backup console.
 */
@Environment(EnvType.CLIENT)
public class TerminalBackupScreen extends HandledScreen<TerminalBackupScreenHandler> {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 232;

    private static final Identifier[] BACKUP_FRAMES = {
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_000.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_025.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_050.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_075.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_100.png")
    };

    private ButtonWidget uploadButton;
    private ButtonWidget backupButton;
    private ButtonWidget restoreButton;
    private ButtonWidget cancelButton;
    private BackupLoopSound soundLoop;

    public TerminalBackupScreen(TerminalBackupScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        this.uploadButton = addDrawableChild(ButtonWidget.builder(Text.literal("Upload"), b -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 0);
            }
        }).dimensions(x + 14, y + 60, 44, 16).build());

        this.backupButton = addDrawableChild(ButtonWidget.builder(Text.literal("Backup"), b -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 1);
            }
        }).dimensions(x + 65, y + 40, 46, 16).build());

        this.restoreButton = addDrawableChild(ButtonWidget.builder(Text.literal("Restore"), b -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 2);
            }
        }).dimensions(x + 65, y + 40, 46, 16).build());

        this.cancelButton = addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, 3);
            }
        }).dimensions(x + 65, y + 60, 46, 16).build());

        updateButtons();
    }

    private void updateButtons() {
        boolean active = handler.isBackupActive();
        boolean restore = handler.isRestoreMode();

        if (uploadButton != null) uploadButton.visible = (!restore && !active);
        if (backupButton != null) backupButton.visible = (!restore && !active);
        if (restoreButton != null) restoreButton.visible = (restore && !active);
        if (cancelButton != null) cancelButton.visible = active;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        updateButtons();

        // Audio Loop Management
        if (handler.isBackupActive()) {
            if (soundLoop == null && client != null && client.world != null) {
                BlockEntity be = client.world.getBlockEntity(handler.getPos());
                if (be != null) {
                    soundLoop = new BackupLoopSound(be, handler::isBackupActive, ModSounds.SCANNER_BELL1, 0.4F);
                    client.getSoundManager().play(soundLoop);
                }
            }
        } else {
            soundLoop = null;
        }
    }

    @Override
    public void close() {
        if (soundLoop != null && client != null) {
            client.getSoundManager().stop(soundLoop);
            soundLoop = null;
        }
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;

        // Outer Dark Metal Chassis
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF141416);
        context.fill(left + 2, top + 2, left + backgroundWidth - 2, top + backgroundHeight - 2, 0xFF4A4B50);
        context.fill(left + 3, top + 3, left + backgroundWidth - 3, top + 15, 0xFF2A2B2E);

        // Machine CRT Upper Panel
        context.fill(left + 4, top + 20, left + backgroundWidth - 4, top + 138, 0xFF0B0B0D);
        context.fill(left + 5, top + 21, left + backgroundWidth - 5, top + 137, 0xFF1B1B1E);

        // Player Inventory Lower Panel
        context.fill(left + 4, top + 140, left + backgroundWidth - 4, top + 228, 0xFF0B0B0D);
        context.fill(left + 5, top + 141, left + backgroundWidth - 5, top + 227, 0xFF1B1B1E);

        // Draw Slots
        for (int i = 0; i < handler.slots.size(); i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            int sx = left + slot.x;
            int sy = top + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF7A7B80);
            context.fill(sx, sy, sx + 16, sy + 16, 0xFF101012);
        }

        // Animated 5-frame progress bar
        int barX = left + 24;
        int barY = top + 84;
        int barW = 128;
        int barH = 12;

        int frame = 0;
        if (handler.isBackupActive()) {
            float progress = 1.0F - (float) handler.getBackupTicks() / 200.0F;
            frame = (int) Math.min(4, Math.max(0, progress * 4.0F));
        } else if (handler.isBackupSuccess()) {
            frame = 4;
        }

        Identifier progressTexture = BACKUP_FRAMES[frame];
        context.drawTexture(progressTexture, barX, barY, 0, 0, barW, barH, barW, barH);

        // Status text
        if (handler.isBackupActive()) {
            String status = handler.isRestoreMode() ? "Restoring..." : "Backing up...";
            context.drawText(textRenderer, status, left + 24, top + 104, 0xFFFF8A00, false);
        } else if (handler.isBackupSuccess()) {
            context.drawText(textRenderer, "Operation Complete", left + 24, top + 104, 0xFF55FF55, false);
        }
    }
}
