package com.ntmdoors.client.screen;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemDeveloperKeycard;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Scissor-clipped scrolling list screen displaying private keys and biometric data carried by an RFID card.
 */
@Environment(EnvType.CLIENT)
public class KeycardViewerScreen extends Screen {

    public static final int PANEL_WIDTH = 252;
    public static final int PANEL_HEIGHT = 178;

    private final ItemStack cardStack;
    private final List<String> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public KeycardViewerScreen(ItemStack cardStack) {
        super(Text.literal("Keycard Data"));
        this.cardStack = cardStack;
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();

        if (cardStack.getItem() instanceof ItemCreativeKeycard) {
            entries.add("You have access to EVERYTHING!!!!!");
            entries.add("THE ENTIRE LIBRARY OF BABEL");
        } else if (ItemDeveloperKeycard.isRedacted(cardStack)) {
            entries.add("Unknown");
        } else {
            if (Biometrics.isBound(cardStack)) {
                entries.add("Biometric ID (" + Biometrics.ownerName(cardStack) + ")");
                entries.add("UUID: " + Biometrics.ownerUuid(cardStack));
                entries.add("");
            }
            List<PrivateKeys.Key> keys = PrivateKeys.get(cardStack);
            if (keys.isEmpty() && !Biometrics.isBound(cardStack)) {
                entries.add("Card is empty (no keys or biometric data)");
            } else {
                for (PrivateKeys.Key k : keys) {
                    entries.add("Key: " + k.name);
                    entries.add("Serial: " + k.serial);
                    entries.add("");
                }
            }
        }

        int totalHeight = entries.size() * 12;
        int visibleHeight = PANEL_HEIGHT - 40;
        maxScroll = Math.max(0, totalHeight - visibleHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (amount * 12)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264) { // Down arrow
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + 12));
            return true;
        } else if (keyCode == 265) { // Up arrow
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - 12));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Metal Panel Frame
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF4A4A4E);
        context.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + 22, 0xFF2A2B2E);
        context.fill(left + 4, top + 26, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF0A0A0C);

        // Header Title
        context.drawText(textRenderer, cardStack.getName(), left + 10, top + 9, 0xFFE8E8E8, false);

        // Scissor-clipped entry list
        context.enableScissor(left + 8, top + 28, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8);
        int y = top + 30 - scrollOffset;
        for (String entry : entries) {
            int color = entry.startsWith("Biometric ID") ? 0xFF55FFFF : 0xFFE8E8E8;
            if (entry.startsWith("Key:")) color = 0xFFFFD24A;
            context.drawText(textRenderer, entry, left + 12, y, color, false);
            y += 12;
        }
        context.disableScissor();

        // Scroll bar
        if (maxScroll > 0) {
            int scrollBarHeight = Math.max(16, (PANEL_HEIGHT - 40) * (PANEL_HEIGHT - 40) / (entries.size() * 12));
            int scrollY = top + 28 + (scrollOffset * (PANEL_HEIGHT - 40 - scrollBarHeight) / maxScroll);
            context.fill(left + PANEL_WIDTH - 8, top + 28, left + PANEL_WIDTH - 5, top + PANEL_HEIGHT - 12, 0xFF1F1F22);
            context.fill(left + PANEL_WIDTH - 8, scrollY, left + PANEL_WIDTH - 5, scrollY + scrollBarHeight, 0xFF8A8A8A);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
