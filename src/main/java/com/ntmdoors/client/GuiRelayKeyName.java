package com.ntmdoors.client;

import com.ntmdoors.network.MessageRelayKeyName;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Naming screen for a fresh Relay Key. The serial is not shown here: it is generated on the server
 * the moment the player confirms, so cancelling leaves the key untouched.
 */
@SideOnly(Side.CLIENT)
public class GuiRelayKeyName extends GuiScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_HEIGHT = 120;
    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;

    private final EnumHand hand;
    private GuiTextField field;
    private int left;
    private int top;

    public GuiRelayKeyName(EnumHand hand) {
        this.hand = hand;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        field = new GuiTextField(0, fontRenderer, left + 14, top + 44, PANEL_WIDTH - 28, 18);
        field.setMaxStringLength(PrivateKeys.NAME_LENGTH);
        field.setFocused(true);

        buttonList.add(new BevelButton(0, left + 14, top + 88, 98, 20, "Create Key"));
        buttonList.add(new BevelButton(1, left + PANEL_WIDTH - 112, top + 88, 98, 20, "Cancel"));
    }

    @Override
    public void updateScreen() {
        field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return;
        }
        if (field.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        field.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            confirm();
        } else {
            mc.displayGuiScreen(null);
        }
    }

    private void confirm() {
        String name = PrivateKeys.saniseName(field.getText());
        if (name.isEmpty()) return;
        ModNetwork.INSTANCE.sendToServer(new MessageRelayKeyName(name, hand));
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Light metal frame over a dark panel, matching the keycard programmer.
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);

        drawString(fontRenderer, "Private Key", left + 14, top + 12, COLOUR);
        String counter = field.getText().length() + "/" + PrivateKeys.NAME_LENGTH;
        drawString(fontRenderer, counter, left + PANEL_WIDTH - 14 - fontRenderer.getStringWidth(counter),
                top + 12, MUTED);
        drawString(fontRenderer, "Name your key", left + 14, top + 28, MUTED);
        drawString(fontRenderer, "This key can only be created once", left + 14, top + 68, MUTED);

        field.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Flat bevel button in the same style as the programmer's drawn controls. */
    private static class BevelButton extends GuiButton {

        BevelButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

            drawRect(x, y, x + width, y + height, COLOUR);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF111111);
            drawRect(x + 2, y + 2, x + width - 2, y + height - 2, hovered ? 0xFF3C3C40 : 0xFF1B1B1E);
            mc.fontRenderer.drawString(displayString,
                    x + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
                    y + (height - 8) / 2, hovered ? 0xFFFFFFFF : COLOUR);
        }
    }
}
