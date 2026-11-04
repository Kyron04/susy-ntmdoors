package com.ntmdoors.client;

import com.ntmdoors.network.MessageTerminalBind;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Name prompt shown immediately after a Terminal Relay Key binds a device. The name is sent to the
 * server with the device position; cancelling leaves the device out of the pending list.
 */
@SideOnly(Side.CLIENT)
public class GuiDeviceName extends GuiScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 150;

    private static final int ORANGE = 0xFFFF8A00;
    private static final int ORANGE_LIGHT = 0xFFFFB454;
    private static final int ORANGE_DIM = 0xFF7A3D00;
    private static final int ORANGE_ERROR = 0xFFFF4A2E;
    private static final int TEXT = 0xFFFFD9A8;
    private static final int MUTED = 0xFF9A6A3A;
    private static final int FIELD_BG = 0xFF020202;
    private static final int BUTTON_BG = 0xFF101010;
    private static final int BUTTON_HOVER = 0xFF2A1500;

    private final EnumHand hand;
    private final BlockPos pos;
    private final String defaultName;
    private final String typeName;

    private GuiTextField field;
    private int left;
    private int top;
    private String error = "";

    public GuiDeviceName(EnumHand hand, BlockPos pos, String defaultName, String typeName) {
        this.hand = hand;
        this.pos = pos;
        this.defaultName = defaultName == null ? "" : defaultName;
        this.typeName = typeName == null ? "Device" : typeName;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        if (field == null) {
            field = new GuiTextField(0, fontRenderer, left + 16, top + 64, PANEL_WIDTH - 32, 18);
            field.setMaxStringLength(PrivateKeys.NAME_LENGTH);
            field.setEnableBackgroundDrawing(false);
            field.setTextColor(TEXT);
            field.setDisabledTextColour(MUTED);
            field.setCanLoseFocus(false);
            field.setText(defaultName);
            field.setCursorPositionEnd();
            field.setSelectionPos(0);
        } else {
            field.x = left + 16;
            field.y = top + 64;
            field.width = PANEL_WIDTH - 32;
            field.height = 18;
        }
        field.setFocused(true);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        field.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawRect(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0xFF000000);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF080808);
        drawRect(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF141414);
        drawRect(left + 4, top + 4, left + PANEL_WIDTH - 4, top + 24, 0xFF0C0C0C);
        drawRect(left + 4, top + 23, left + PANEL_WIDTH - 4, top + 24, ORANGE);

        drawString(fontRenderer, "Assign Name", left + 12, top + 9, ORANGE_LIGHT);
        drawString(fontRenderer, typeName + " - bound to terminal",
                left + 16, top + 32, MUTED);
        drawString(fontRenderer, "Name", left + 16, top + 50, ORANGE);

        drawRect(field.x - 1, field.y - 1, field.x + field.width + 1, field.y + field.height + 1,
                ORANGE);
        drawRect(field.x, field.y, field.x + field.width, field.y + field.height, FIELD_BG);
        field.drawTextBox();

        int saveX = left + 16;
        int cancelX = left + PANEL_WIDTH - 16 - 128;
        int buttonY = top + 112;
        drawButton("Save", saveX, buttonY, 128, 20,
                isHovering(mouseX, mouseY, saveX, buttonY, 128, 20));
        drawButton("Cancel", cancelX, buttonY, 128, 20,
                isHovering(mouseX, mouseY, cancelX, buttonY, 128, 20));

        if (!error.isEmpty()) {
            drawCenteredString(fontRenderer, error, left + PANEL_WIDTH / 2, top + 92, ORANGE_ERROR);
        }
    }

    private void drawButton(String label, int x, int y, int width, int height, boolean hover) {
        drawRect(x, y, x + width, y + height, hover ? BUTTON_HOVER : BUTTON_BG);
        drawRect(x, y, x + width, y + 1, hover ? ORANGE : ORANGE_DIM);
        drawRect(x, y + height - 1, x + width, y + height, hover ? ORANGE : ORANGE_DIM);
        drawRect(x, y, x + 1, y + height, hover ? ORANGE : ORANGE_DIM);
        drawRect(x + width - 1, y, x + width, y + height, hover ? ORANGE : ORANGE_DIM);
        drawCenteredString(fontRenderer, label, x + width / 2, y + (height - 8) / 2,
                hover ? ORANGE_LIGHT : TEXT);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        field.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            int saveX = left + 16;
            int cancelX = left + PANEL_WIDTH - 16 - 128;
            int buttonY = top + 112;
            if (isHovering(mouseX, mouseY, saveX, buttonY, 128, 20)) {
                playClick();
                confirm();
                return;
            }
            if (isHovering(mouseX, mouseY, cancelX, buttonY, 128, 20)) {
                playClick();
                mc.displayGuiScreen(null);
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
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirm();
            return;
        }
        if (field.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void confirm() {
        String name = PrivateKeys.sanitiseName(field.getText());
        if (name.isEmpty()) {
            error = "Enter a name";
            return;
        }
        ModNetwork.INSTANCE.sendToServer(new MessageTerminalBind(hand, pos, name));
        mc.displayGuiScreen(null);
    }

    private void playClick() {
        if (mc.player != null) mc.player.playSound(ModSounds.GUI_CLICK, 0.5F, 1F);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
