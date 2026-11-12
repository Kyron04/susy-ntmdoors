package com.ntmdoors.client.screen;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.KeycardProgrammerScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 3-Page Keycard Programmer GUI: Key Binding, 31-Card Palette Recoloring, and Legacy Backup.
 */
@Environment(EnvType.CLIENT)
public class KeycardProgrammerScreen extends HandledScreen<KeycardProgrammerScreenHandler> {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 200;

    private static final Identifier[] BACKUP_FRAMES = {
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_000.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_025.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_050.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_075.png"),
            Identifier.of(NTMDoors.MOD_ID, "textures/gui/legacy_backup_progress_100.png")
    };

    private int currentTab = 0;
    private ButtonWidget bindButton1;
    private ButtonWidget bindButton2;
    private ButtonWidget backupButton;
    private String status = "";
    private int statusTicks = 0;
    private int statusColor = 0xFFFF5555;

    public KeycardProgrammerScreen(KeycardProgrammerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.currentTab = handler.getPage();

        // Tab Navigation Buttons
        int tabWidth = 52;
        int tabHeight = 16;
        addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> setTab(0))
                .dimensions(x + 7, y + 16, tabWidth, tabHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Palette"), b -> setTab(1))
                .dimensions(x + 62, y + 16, tabWidth, tabHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Backup"), b -> setTab(2))
                .dimensions(x + 117, y + 16, tabWidth, tabHeight).build());

        // Page 0: Bind Buttons (placed to the right of target card slot)
        this.bindButton1 = addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> {
            if (currentTab == 0) handleBindClick(0);
        }).dimensions(x + 120, y + 38, 46, 18).build());

        this.bindButton2 = addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> {
            if (currentTab == 0) handleBindClick(1);
        }).dimensions(x + 120, y + 78, 46, 18).build());

        // Page 2: Backup Button
        this.backupButton = addDrawableChild(ButtonWidget.builder(Text.literal("Start Backup"), b -> {
            if (currentTab == 2) {
                setStatus("Backup started...", 0xFF55FF55);
                ModMessages.sendProgrammerAction((byte) 2, 0);
            }
        }).dimensions(x + 48, y + 39, 80, 18).build());

        updateButtonVisibility();
    }

    private void handleBindClick(int row) {
        ItemStack source = handler.slots.get(row == 0 ? 0 : 2).getStack();
        ItemStack target = handler.slots.get(row == 0 ? 1 : 3).getStack();

        KeycardProgrammerBlockEntity.BindFailure failure = KeycardProgrammerBlockEntity.checkBindFailure(source, target, row);
        if (failure != KeycardProgrammerBlockEntity.BindFailure.NONE) {
            String msg = switch (failure) {
                case EMPTY -> "Insert items into both slots";
                case NO_KEY -> "Source key has no security key!";
                case NO_BIOMETRIC -> "Source key has no biometric data!";
                case INVALID_TARGET -> (row == 0) ? "Top row requires Operator Card" : "Bottom row requires RFID Card";
                case BLANK_CARD -> "Blank card must be coloured on Palette first";
                case ALREADY_BOUND -> "Key already bound to this card";
                case BIOMETRIC_TAKEN -> "Target card already has biometric ID";
                case BIOMETRIC_ALREADY -> "Target card already has this biometric ID";
                default -> "Cannot bind key";
            };
            setStatus(msg, 0xFFFF5555);
            if (client != null && client.player != null) {
                client.player.playSound(ModSounds.SCANNER_BUTTON8, 0.8F, 1.0F);
            }
            return;
        }

        setStatus("Key bound successfully!", 0xFF55FF55);
        ModMessages.sendProgrammerAction((byte) 0, row);
    }

    private void setStatus(String text, int color) {
        this.status = text;
        this.statusColor = color;
        this.statusTicks = 120;
    }

    private void setTab(int tab) {
        this.currentTab = tab;
        handler.setPage(tab);
        ModMessages.sendProgrammerPage((byte) tab);
        this.status = "";
        this.statusTicks = 0;
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (bindButton1 != null) bindButton1.visible = (currentTab == 0);
        if (bindButton2 != null) bindButton2.visible = (currentTab == 0);
        if (backupButton != null) backupButton.visible = (currentTab == 2);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;

        // Metal Case
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF18181A);
        context.fill(left + 2, top + 2, left + backgroundWidth - 2, top + backgroundHeight - 2, 0xFFC8C8C8);
        context.fill(left + 3, top + 3, left + backgroundWidth - 3, top + 14, 0xFFECECEC);
        context.fill(left + 4, top + 34, left + backgroundWidth - 4, top + 104, 0xFF2A2B2E);

        // Active Tab Indicator
        int activeTabX = switch (currentTab) {
            case 0 -> left + 7;
            case 1 -> left + 62;
            case 2 -> left + 117;
            default -> left + 7;
        };
        context.fill(activeTabX, top + 32, activeTabX + 52, top + 34, 0xFF75FF82);

        // Slot Outlines for Visible Machine Slots
        for (int i = 0; i < KeycardProgrammerBlockEntity.INVENTORY_SIZE; i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            if (slot.isEnabled()) {
                int sx = left + slot.x;
                int sy = top + slot.y;
                context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFB8B8B8);
                context.fill(sx, sy, sx + 16, sy + 16, 0xFF141416);
            }
        }

        // Arrows in Tab 0
        if (currentTab == 0) {
            context.drawText(textRenderer, Text.literal("==>"), left + 54, top + 42, 0xFF75FF82, false);
            context.drawText(textRenderer, Text.literal("==>"), left + 54, top + 82, 0xFF75FF82, false);
        }

        // Draw Player Inventory Slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = left + 8 + col * 18;
                int sy = top + 110 + row * 18;
                context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8A8A8A);
                context.fill(sx, sy, sx + 16, sy + 16, 0xFF141416);
            }
        }
        for (int col = 0; col < 9; col++) {
            int sx = left + 8 + col * 18;
            int sy = top + 168;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8A8A8A);
            context.fill(sx, sy, sx + 16, sy + 16, 0xFF141416);
        }

        // Page-specific Background Elements
        if (currentTab == 1) {
            // Palette Grid
            int startX = left + 12;
            int startY = top + 60;
            for (int i = 0; i < ModItems.PROGRAMMER_CARDS.size(); i++) {
                int col = i % 8;
                int row = i / 8;
                int px = startX + col * 19;
                int py = startY + row * 18;
                ItemRfidCard card = ModItems.PROGRAMMER_CARDS.get(i);
                context.drawItem(new ItemStack(card), px, py);
            }
        } else if (currentTab == 2) {
            // Backup Progress Bar
            int progress = handler.getBackupTicks();
            int frameIndex = Math.min(4, Math.max(0, (progress * 4) / KeycardProgrammerBlockEntity.BACKUP_TICKS_TOTAL));
            if (handler.isBackupSuccess()) frameIndex = 4;
            context.drawTexture(BACKUP_FRAMES[frameIndex], left + 24, top + 64, 0, 0, 128, 12, 128, 12);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentTab == 1 && button == 0) {
            int left = this.x;
            int top = this.y;
            int startX = left + 12;
            int startY = top + 60;
            for (int i = 0; i < ModItems.PROGRAMMER_CARDS.size(); i++) {
                int col = i % 8;
                int row = i / 8;
                int px = startX + col * 19;
                int py = startY + row * 18;
                if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
                    setStatus("Color applied!", 0xFF55FF55);
                    ModMessages.sendProgrammerAction((byte) 1, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 8, 4, 0xFF2A2B2E, false);
        context.drawText(textRenderer, playerInventoryTitle, 8, 98, 0xFF2A2B2E, false);

        if (currentTab == 0) {
            // Row 0 labels
            context.drawText(textRenderer, Text.literal("Source"), 11, 26, 0xFF8A8A8A, false);
            context.drawText(textRenderer, Text.literal("Operator Card"), 94, 26, 0xFF8A8A8A, false);

            // Row 1 labels
            context.drawText(textRenderer, Text.literal("Source"), 11, 66, 0xFF8A8A8A, false);
            context.drawText(textRenderer, Text.literal("RFID Card"), 94, 66, 0xFF8A8A8A, false);

            // Status feedback
            if (statusTicks > 0 && !status.isEmpty()) {
                int sw = textRenderer.getWidth(status);
                context.drawText(textRenderer, Text.literal(status), Math.max(8, (backgroundWidth - sw) / 2), 58, statusColor, false);
            }
        } else if (currentTab == 1) {
            context.drawText(textRenderer, Text.literal("Place RFID card:"), 11, 42, 0xFF8A8A8A, false);
            if (statusTicks > 0 && !status.isEmpty()) {
                int sw = textRenderer.getWidth(status);
                context.drawText(textRenderer, Text.literal(status), Math.max(8, (backgroundWidth - sw) / 2), 26, statusColor, false);
            }
        } else if (currentTab == 2) {
            context.drawText(textRenderer, Text.literal("Source"), 24, 28, 0xFF8A8A8A, false);
            context.drawText(textRenderer, Text.literal("Target"), 136, 28, 0xFF8A8A8A, false);
            if (handler.isBackupActive()) {
                context.drawText(textRenderer, Text.literal("Backing up data..."), 44, 82, 0xFFE0E0E0, false);
            } else if (handler.isBackupSuccess()) {
                context.drawText(textRenderer, Text.literal("Backup Complete!"), 48, 82, 0xFF75FF82, false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (statusTicks > 0) statusTicks--;
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        // Ghost tooltip for empty programmer slots
        if (focusedSlot != null && !focusedSlot.hasStack() && client != null && client.player != null && focusedSlot.inventory != client.player.getInventory()) {
            String ghostText = getGhostTooltip(focusedSlot.getIndex());
            if (ghostText != null) {
                context.drawTooltip(textRenderer, Text.literal(ghostText), mouseX, mouseY);
            }
        }
    }

    private String getGhostTooltip(int slotIndex) {
        return switch (slotIndex) {
            case KeycardProgrammerBlockEntity.SLOT_RELAY_ONE -> "Relay Key or Operator Keycard (Source)";
            case KeycardProgrammerBlockEntity.SLOT_OPERATOR -> "Operator Keycard (Target)";
            case KeycardProgrammerBlockEntity.SLOT_RELAY_TWO -> "Relay Key or Operator Keycard (Source)";
            case KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE -> "Coloured RFID Card (Target)";
            case KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO -> "RFID Card (Place to recolour)";
            case KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT -> "Source Key / Card to back up";
            case KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT -> "Blank Key / Card to receive backup";
            default -> null;
        };
    }
}
