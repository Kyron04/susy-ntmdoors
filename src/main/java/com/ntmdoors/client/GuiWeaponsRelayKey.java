package com.ntmdoors.client;

import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.network.MessageWeaponsKeyMode;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Binding-mode picker for the Weapons Relay Key. */
@SideOnly(Side.CLIENT)
public class GuiWeaponsRelayKey extends GuiScreen {

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

    private final EnumHand hand;
    private int left;
    private int top;

    public GuiWeaponsRelayKey(EnumHand hand) {
        this.hand = hand;
    }

    private ItemStack stack() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.getHeldItem(hand);
    }

    private WeaponsMode mode() {
        ItemStack stack = stack();
        return stack.getItem() instanceof ItemWeaponsRelayKey
                ? WeaponsKeyData.mode(stack) : WeaponsMode.REGULAR;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        drawRect(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        drawString(fontRenderer, "WEAPONS RELAY KEY", left + 12, top + 10, COLOUR);
        String right = "BIND MODE";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);

        WeaponsMode current = mode();
        drawModeRow(0, WeaponsMode.REGULAR, current == WeaponsMode.REGULAR, mouseX, mouseY);
        drawModeRow(1, WeaponsMode.DEAD_MANS_HAND, current == WeaponsMode.DEAD_MANS_HAND,
                mouseX, mouseY);

        drawRect(left + 12, top + PANEL_HEIGHT - 18, left + PANEL_WIDTH - 12,
                top + PANEL_HEIGHT - 17, RULE);
        drawCenteredString(fontRenderer, "Sneak + Right Click an explosive to bind it.",
                left + PANEL_WIDTH / 2, top + PANEL_HEIGHT - 13, MUTED);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawModeRow(int index, WeaponsMode mode, boolean selected,
                             int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        if (inRow(index, mouseX, mouseY)) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, HOVER);
        }
        if (selected) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, SELECTED);
        }
        drawString(fontRenderer, mode.displayName, left + 16, y,
                selected ? 0xFFFFD0D0 : COLOUR);
        drawString(fontRenderer, mode.description, left + 16, y + 12, MUTED);
        if (selected) {
            String tag = "SELECTED";
            drawString(fontRenderer, tag,
                    left + PANEL_WIDTH - 16 - fontRenderer.getStringWidth(tag), y + 2, HEADING);
        }
    }

    private boolean inRow(int index, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + ROW_HEIGHT - 5;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            if (inRow(0, mouseX, mouseY)) {
                ModNetwork.INSTANCE.sendToServer(
                        new MessageWeaponsKeyMode(hand, WeaponsMode.REGULAR));
                return;
            }
            if (inRow(1, mouseX, mouseY)) {
                ModNetwork.INSTANCE.sendToServer(
                        new MessageWeaponsKeyMode(hand, WeaponsMode.DEAD_MANS_HAND));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
