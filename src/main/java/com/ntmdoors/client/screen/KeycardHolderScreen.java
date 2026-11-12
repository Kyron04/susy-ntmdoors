package com.ntmdoors.client.screen;

import com.ntmdoors.screen.KeycardHolderScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Modern Fabric 1.20.1 Client Screen for Keycard Holder.
 * Uses procedural DrawContext rendering for the light casing, dark machine face, slot frames,
 * and card ghost silhouettes.
 */
@Environment(EnvType.CLIENT)
public class KeycardHolderScreen extends HandledScreen<KeycardHolderScreenHandler> {

    public KeycardHolderScreen(KeycardHolderScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = KeycardHolderScreenHandler.GUI_WIDTH;
        this.backgroundHeight = KeycardHolderScreenHandler.GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 7;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 118;
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

        // Outer beveled metal case
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF18181A);
        context.fill(left + 2, top + 2, left + backgroundWidth - 2, top + backgroundHeight - 2, 0xFFC8C8C8);
        context.fill(left + 3, top + 3, left + backgroundWidth - 3, top + 18, 0xFFECECEC);

        // Dark machine face holding the card grid
        context.fill(left + 4, top + 20, left + backgroundWidth - 4, top + 120, 0xFF0B0B0D);
        context.fill(left + 5, top + 21, left + backgroundWidth - 5, top + 119, 0xFF1B1B1E);

        // Player inventory well
        context.fill(left + 4, top + 122, left + backgroundWidth - 4, top + backgroundHeight - 4, 0xFF0B0B0D);
        context.fill(left + 5, top + 123, left + backgroundWidth - 5, top + backgroundHeight - 5, 0xFF1B1B1E);

        // Outlined slot frames around all slots
        for (Slot slot : handler.slots) {
            int sx = left + slot.x;
            int sy = top + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFB8B8B8);
            context.fill(sx, sy, sx + 16, sy + 16, 0xFF141416);
        }

        // Draw card ghost icons in empty holder slots (0..11)
        for (int i = 0; i < KeycardHolderScreenHandler.HOLDER_SLOT_COUNT; i++) {
            Slot slot = handler.slots.get(i);
            if (!slot.hasStack()) {
                int sx = left + slot.x;
                int sy = top + slot.y;
                // Card silhouette in dark grey
                context.fill(sx + 2, sy + 3, sx + 14, sy + 13, 0xFF28282C);
                context.fill(sx + 4, sy + 5, sx + 12, sy + 7, 0xFF38383F);
                context.fill(sx + 4, sy + 9, sx + 9, sy + 11, 0xFF38383F);
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, Text.literal("Keycard Holder"), 8, 7, 0xFF2B2B2E, false);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Keycards"), backgroundWidth / 2, 24, 0xFFE8E8E8);

        String countStr = handler.getStoredCount() + "/" + KeycardHolderScreenHandler.HOLDER_SLOT_COUNT;
        context.drawText(textRenderer, Text.literal(countStr), backgroundWidth - 8 - textRenderer.getWidth(countStr), 24, 0xFFAFAFAF, false);
    }
}
