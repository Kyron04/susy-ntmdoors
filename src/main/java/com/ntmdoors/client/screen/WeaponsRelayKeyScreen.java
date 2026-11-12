package com.ntmdoors.client.screen;

import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Modern Fabric 1.20.1 Client Screen for Weapons Relay Key mode selection.
 * 260x150 dark panel with interactive row selection.
 */
@Environment(EnvType.CLIENT)
public class WeaponsRelayKeyScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 150;
    private static final int ROW_TOP = 44;
    private static final int ROW_HEIGHT = 38;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;
    private static final int HOVER = 0xFF3A2A2D;
    private static final int SELECTED = 0xFF3A2A2A;

    private final Hand hand;

    public WeaponsRelayKeyScreen(Hand hand) {
        super(Text.literal("Weapons Relay Key"));
        this.hand = hand;
    }

    private ItemStack stack() {
        if (client == null || client.player == null) return ItemStack.EMPTY;
        return client.player.getStackInHand(hand);
    }

    private WeaponsMode mode() {
        ItemStack stack = stack();
        return stack.getItem() instanceof ItemWeaponsRelayKey
                ? WeaponsKeyData.mode(stack) : WeaponsMode.REGULAR;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        context.fill(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        context.drawText(textRenderer, Text.literal("WEAPONS RELAY KEY"), left + 12, top + 8, COLOUR, false);
        String right = "BIND MODE";
        context.drawText(textRenderer, Text.literal(right), left + PANEL_WIDTH - 12 - textRenderer.getWidth(right), top + 8, MUTED, false);

        WeaponsMode current = mode();
        drawModeRow(context, left, top, 0, WeaponsMode.REGULAR, current == WeaponsMode.REGULAR, mouseX, mouseY);
        drawModeRow(context, left, top, 1, WeaponsMode.DEAD_MANS_HAND, current == WeaponsMode.DEAD_MANS_HAND, mouseX, mouseY);

        context.fill(left + 12, top + PANEL_HEIGHT - 18, left + PANEL_WIDTH - 12, top + PANEL_HEIGHT - 17, RULE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Sneak + Right Click an explosive to bind it."),
                left + PANEL_WIDTH / 2, top + PANEL_HEIGHT - 13, MUTED);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawModeRow(DrawContext context, int left, int top, int index, WeaponsMode mode, boolean selected, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        if (inRow(left, top, index, mouseX, mouseY)) {
            context.fill(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, HOVER);
        }
        if (selected) {
            context.fill(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, SELECTED);
        }
        context.drawText(textRenderer, Text.literal(mode.displayName), left + 16, y, selected ? 0xFFFFD0D0 : COLOUR, false);
        context.drawText(textRenderer, Text.literal(mode.description), left + 16, y + 12, MUTED, false);
        if (selected) {
            String tag = "SELECTED";
            context.drawText(textRenderer, Text.literal(tag), left + PANEL_WIDTH - 16 - textRenderer.getWidth(tag), y + 2, HEADING, false);
        }
    }

    private boolean inRow(int left, int top, int index, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + ROW_HEIGHT - 5;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            if (inRow(left, top, 0, (int) mouseX, (int) mouseY)) {
                ModMessages.sendWeaponsKeyMode(hand, WeaponsMode.REGULAR);
                close();
                return true;
            }
            if (inRow(left, top, 1, (int) mouseX, (int) mouseY)) {
                ModMessages.sendWeaponsKeyMode(hand, WeaponsMode.DEAD_MANS_HAND);
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
