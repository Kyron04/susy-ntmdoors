package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 3-Page Administration Screen for Keycard Scanner:
 * Page 0: Transfer access entries (keys & biometrics)
 * Page 1: Scanner settings (Holder, Lockdown, Lockout, Ignore, Force)
 * Page 2: Redstone Event toggles
 */
@Environment(EnvType.CLIENT)
public class ScannerKeysScreen extends Screen {

    public static final int PANEL_WIDTH = 256;
    public static final int PANEL_HEIGHT = 200;

    private final BlockPos pos;
    private int activePage = 0;

    public ScannerKeysScreen(BlockPos pos) {
        super(Text.literal("Keycard Scanner Console"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Navigation Tabs
        addDrawableChild(ButtonWidget.builder(Text.literal("Keys"), b -> { activePage = 0; rebuildWidgets(); })
                .dimensions(left + 8, top + 16, 76, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> { activePage = 1; rebuildWidgets(); })
                .dimensions(left + 90, top + 16, 76, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Redstone"), b -> { activePage = 2; rebuildWidgets(); })
                .dimensions(left + 172, top + 16, 76, 16).build());

        KeycardScannerBlockEntity scanner = getScanner();

        if (activePage == 1 && scanner != null) {
            // Settings Page Toggles
            int y = top + 38;
            addDrawableChild(ButtonWidget.builder(Text.literal("Match Holder: " + (scanner.isMatchHolder() ? "ON" : "OFF")), b -> {
                ModMessages.sendScannerOption(pos, (byte) 0, !scanner.isMatchHolder());
                b.setMessage(Text.literal("Match Holder: " + (!scanner.isMatchHolder() ? "ON" : "OFF")));
            }).dimensions(left + 12, y, 110, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Lockdown: " + (scanner.isLockdown() ? "ON" : "OFF")), b -> {
                ModMessages.sendScannerOption(pos, (byte) 1, !scanner.isLockdown());
                b.setMessage(Text.literal("Lockdown: " + (!scanner.isLockdown() ? "ON" : "OFF")));
            }).dimensions(left + 134, y, 110, 18).build());

            y += 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Lockout Mode: " + (scanner.isLockoutMode() ? "ON" : "OFF")), b -> {
                ModMessages.sendScannerOption(pos, (byte) 2, !scanner.isLockoutMode());
                b.setMessage(Text.literal("Lockout Mode: " + (!scanner.isLockoutMode() ? "ON" : "OFF")));
            }).dimensions(left + 12, y, 110, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Ignore Keys: " + (scanner.isIgnoreKeys() ? "ON" : "OFF")), b -> {
                ModMessages.sendScannerOption(pos, (byte) 3, !scanner.isIgnoreKeys());
                b.setMessage(Text.literal("Ignore Keys: " + (!scanner.isIgnoreKeys() ? "ON" : "OFF")));
            }).dimensions(left + 134, y, 110, 18).build());

            y += 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Ignore IDs: " + (scanner.isIgnoreIds() ? "ON" : "OFF")), b -> {
                ModMessages.sendScannerOption(pos, (byte) 4, !scanner.isIgnoreIds());
                b.setMessage(Text.literal("Ignore IDs: " + (!scanner.isIgnoreIds() ? "ON" : "OFF")));
            }).dimensions(left + 12, y, 110, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Reset Code"), b -> {
                ModMessages.sendScannerOption(pos, (byte) 7, true);
                if (client != null && client.player != null) {
                    client.player.sendMessage(Text.translatable("ntmdoors.msg.scanner_code_reset"), true);
                }
            }).dimensions(left + 134, y, 110, 18).build());

            y += 30;
            addDrawableChild(ButtonWidget.builder(Text.literal("Force Open Doors"), b -> {
                ModMessages.sendScannerOption(pos, (byte) 5, true);
            }).dimensions(left + 12, y, 110, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Force Close Doors"), b -> {
                ModMessages.sendScannerOption(pos, (byte) 6, true);
            }).dimensions(left + 134, y, 110, 18).build());
        } else if (activePage == 2 && scanner != null) {
            // Redstone Page: 9 Events
            String[] labels = {
                    "Scan Fail", "Scan Success", "Match Fail", "Match Success",
                    "Key Fail", "Key Success", "Biometric Fail", "Biometric Success", "Lockout Alarm"
            };
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                int col = i % 2;
                int row = i / 2;
                boolean current = scanner.getRedstoneEvent(idx);
                addDrawableChild(ButtonWidget.builder(Text.literal(labels[idx] + ": " + (current ? "ON" : "OFF")), b -> {
                    boolean next = !scanner.getRedstoneEvent(idx);
                    scanner.setRedstoneEvent(idx, next);
                    ModMessages.sendScannerOption(pos, (byte) (8 + idx), next);
                    b.setMessage(Text.literal(labels[idx] + ": " + (next ? "ON" : "OFF")));
                }).dimensions(left + 12 + col * 122, top + 38 + row * 24, 116, 18).build());
            }
        }
    }

    private KeycardScannerBlockEntity getScanner() {
        if (client != null && client.world != null) {
            BlockEntity be = client.world.getBlockEntity(pos);
            if (be instanceof KeycardScannerBlockEntity s) return s;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activePage == 0 && button == 0) {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            KeycardScannerBlockEntity scanner = getScanner();
            if (scanner != null && client != null && client.player != null) {
                // Left Column: Scanner Assigned Entries -> click to unassign
                int startY = top + 40;
                List<PrivateKeys.Key> keys = scanner.getAssignedKeys();
                for (int i = 0; i < keys.size(); i++) {
                    int ey = startY + i * 14;
                    if (mouseX >= left + 12 && mouseX <= left + 120 && mouseY >= ey && mouseY <= ey + 12) {
                        ModMessages.sendScannerKey(pos, keys.get(i).serial, false, false, keys.get(i).name);
                        return true;
                    }
                }
                int bioStart = startY + keys.size() * 14;
                List<String> ids = scanner.getAssignedIds();
                for (int i = 0; i < ids.size(); i++) {
                    int ey = bioStart + i * 14;
                    if (mouseX >= left + 12 && mouseX <= left + 120 && mouseY >= ey && mouseY <= ey + 12) {
                        ModMessages.sendScannerKey(pos, ids.get(i), false, true, "");
                        return true;
                    }
                }

                // Right Column: Operator Card Entries -> click to assign
                ItemStack card = client.player.getMainHandStack();
                if (!(card.getItem() instanceof ItemOperatorKeycard)) {
                    card = client.player.getOffHandStack();
                }
                if (!card.isEmpty()) {
                    List<PrivateKeys.Key> cardKeys = PrivateKeys.get(card);
                    int rightX = left + 134;
                    for (int i = 0; i < cardKeys.size(); i++) {
                        int ey = startY + i * 14;
                        if (mouseX >= rightX && mouseX <= rightX + 110 && mouseY >= ey && mouseY <= ey + 12) {
                            PrivateKeys.Key k = cardKeys.get(i);
                            ModMessages.sendScannerKey(pos, k.serial, true, false, k.name);
                            return true;
                        }
                    }
                    if (Biometrics.isBound(card)) {
                        int ey = startY + cardKeys.size() * 14;
                        if (mouseX >= rightX && mouseX <= rightX + 110 && mouseY >= ey && mouseY <= ey + 12) {
                            ModMessages.sendScannerKey(pos, Biometrics.ownerUuid(card), true, true, Biometrics.ownerName(card));
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Bezel & Panel
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF4A4A4E);
        context.fill(left + 4, top + 34, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF222226);

        // Header
        context.drawText(textRenderer, title, left + 10, top + 6, 0xFFE8E8E8, false);

        KeycardScannerBlockEntity scanner = getScanner();
        if (activePage == 0 && scanner != null) {
            // Page 0: Transfer Columns
            context.drawText(textRenderer, Text.literal("Scanner Entries"), left + 14, top + 36, 0xFF75FF82, false);
            context.drawText(textRenderer, Text.literal("Card Entries"), left + 136, top + 36, 0xFFFFD24A, false);

            int startY = top + 48;
            // Draw Scanner Assigned Keys
            List<PrivateKeys.Key> keys = scanner.getAssignedKeys();
            for (int i = 0; i < Math.min(keys.size(), 8); i++) {
                PrivateKeys.Key k = keys.get(i);
                context.drawText(textRenderer, Text.literal("- " + k.name), left + 14, startY + i * 14, 0xFFE8E8E8, false);
            }
            int bioY = startY + Math.min(keys.size(), 8) * 14;
            List<String> idNames = scanner.getAssignedIdNames();
            for (int i = 0; i < Math.min(idNames.size(), 3); i++) {
                context.drawText(textRenderer, Text.literal("- Bio: " + idNames.get(i)), left + 14, bioY + i * 14, 0xFF55FFFF, false);
            }

            // Draw Operator Card Entries
            if (client != null && client.player != null) {
                ItemStack card = client.player.getMainHandStack();
                if (!(card.getItem() instanceof ItemOperatorKeycard)) {
                    card = client.player.getOffHandStack();
                }
                if (!card.isEmpty()) {
                    List<PrivateKeys.Key> cardKeys = PrivateKeys.get(card);
                    for (int i = 0; i < Math.min(cardKeys.size(), 8); i++) {
                        PrivateKeys.Key k = cardKeys.get(i);
                        context.drawText(textRenderer, Text.literal("+ " + k.name), left + 136, startY + i * 14, 0xFFE8E8E8, false);
                    }
                    if (Biometrics.isBound(card)) {
                        int cy = startY + Math.min(cardKeys.size(), 8) * 14;
                        context.drawText(textRenderer, Text.literal("+ Bio: " + Biometrics.ownerName(card)), left + 136, cy, 0xFF55FFFF, false);
                    }
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
