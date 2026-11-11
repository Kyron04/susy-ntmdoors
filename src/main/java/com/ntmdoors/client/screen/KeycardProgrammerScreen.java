package com.ntmdoors.client.screen;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.KeycardProgrammerScreenHandler;
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

        // Page 0: Bind Buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> {
            if (currentTab == 0) ModMessages.sendProgrammerAction((byte) 0, 0);
        }).dimensions(x + 40, y + 38, 42, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> {
            if (currentTab == 0) ModMessages.sendProgrammerAction((byte) 0, 1);
        }).dimensions(x + 40, y + 78, 42, 18).build());

        // Page 2: Backup Button
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Backup"), b -> {
            if (currentTab == 2) ModMessages.sendProgrammerAction((byte) 2, 0);
        }).dimensions(x + 48, y + 39, 80, 18).build());
    }

    private void setTab(int tab) {
        this.currentTab = tab;
        handler.setPage(tab);
        ModMessages.sendProgrammerPage((byte) tab);
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
                int py = startY + row * 10;
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
                int py = startY + row * 10;
                if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
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
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
