package com.ntmdoors.client;

import com.ntmdoors.network.MessageKeypadCode;
import com.ntmdoors.network.MessageKeypadOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.util.Codes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Redstone Relay Key screen for a keypad. Page one sets the first code; page two carries the two
 * wireless event toggles and the keypad's door controls.
 */
@SideOnly(Side.CLIENT)
public class GuiKeypad extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;
    private static final int PAGE_COUNT = 2;

    private static final int OPTION_TOP = 46;
    private static final int OPTION_HEIGHT = 24;
    private static final int OPTION_AREA = 102;
    private static final int ROW_HEIGHT = 24;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;
    private static final int SCROLL_WIDTH = 3;

    private static final int ARROW_SIZE = 18;
    private static final int ARROW_Y = 159;

    private static final int[] OPTIONS = {
            MessageKeypadOption.ON_FAIL,
            MessageKeypadOption.ON_SUCCESS,
            MessageKeypadOption.RESET_CODE,
            MessageKeypadOption.OVERRIDE_DOOR_LOCKS,
            MessageKeypadOption.FORCE_CLOSE
    };

    private static final int RED_PANEL = 0xFF2A0F12;
    private static final int RED_RULE = 0xFF6A2A2A;
    private static final int RED_HOVER = 0xFF3A1418;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFDCDCDC;
    private static final int HOVER = 0xFF3A3D42;
    private static final int RULE = 0xFF4A4A4E;

    private final BlockPos pos;

    private int left;
    private int top;
    private int page;
    private int scrollOptions;
    private int lastMouseX;
    private int lastMouseY;

    private GuiTextField field;
    private GuiButton setButton;

    public GuiKeypad(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        page = 0;
        scrollOptions = 0;

        field = new GuiTextField(0, fontRenderer, left + 14, top + 62, 120, 18);
        field.setMaxStringLength(TileEntityDoorKeypad.CODE_LENGTH);
        field.setTextColor(0xFFE8E8E8);
        field.setFocused(true);

        setButton = new BevelButton(0, left + 150, top + 61, 92, 20, "Set Code");
        buttonList.add(setButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private TileEntityDoorKeypad keypad() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntityDoorKeypad ? (TileEntityDoorKeypad) te : null;
    }

    private boolean codePageVisible() {
        TileEntityDoorKeypad keypad = keypad();
        return page == 0 && keypad != null && !keypad.hasStoredCode();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (field != null) field.updateCursorCounter();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || page != 1 || !inOptionsArea(lastMouseX, lastMouseY)) return;
        scrollOptions += wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            if (inArrow(mouseX, mouseY, false)) {
                changePage(-1);
                return;
            }
            if (inArrow(mouseX, mouseY, true)) {
                changePage(1);
                return;
            }

            if (page == 0) {
                if (codePageVisible()) field.mouseClicked(mouseX, mouseY, mouseButton);
            } else {
                int option = optionAt(mouseX, mouseY);
                if (option >= 0) {
                    ModNetwork.INSTANCE.sendToServer(new MessageKeypadOption(pos, option,
                            optionIsAction(option) ? false : !optionValue(option)));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (MonitorReturnContext.handleEscape()) return;
            mc.displayGuiScreen(null);
            return;
        }
        if (page == 0 && codePageVisible()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                setCode();
                return;
            }
            if (field.textboxKeyTyped(typedChar, keyCode)) {
                field.setText(Codes.sanitise(field.getText(), TileEntityDoorKeypad.CODE_LENGTH));
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            setCode();
            return;
        }
        super.actionPerformed(button);
    }

    private void setCode() {
        if (!codePageVisible()) return;
        String code = Codes.sanitise(field.getText(), TileEntityDoorKeypad.CODE_LENGTH);
        if (code.isEmpty()) return;
        ModNetwork.INSTANCE.sendToServer(new MessageKeypadCode(pos, code));
        field.setText("");
        page = 1;
        field.setFocused(false);
    }

    private void changePage(int delta) {
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
        if (field != null) field.setFocused(page == 0);
        if (page == 1) scrollOptions = 0;
    }

    private boolean optionValue(int option) {
        TileEntityDoorKeypad keypad = keypad();
        switch (option) {
            case MessageKeypadOption.ON_FAIL:
                return keypad != null && keypad.isOnFail();
            case MessageKeypadOption.ON_SUCCESS:
                return keypad != null && keypad.isOnSuccess();
            default:
                return false;
        }
    }

    private static boolean optionIsAction(int option) {
        return option >= MessageKeypadOption.RESET_CODE;
    }

    private int optionAt(int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return -1;
        int clamped = clampScroll(scrollOptions, OPTIONS.length, OPTION_AREA);
        int row = (mouseY - (top + OPTION_TOP) + clamped) / OPTION_HEIGHT;
        return row >= 0 && row < OPTIONS.length ? OPTIONS[row] : -1;
    }

    private boolean inOptionsArea(int mouseX, int mouseY) {
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= top + OPTION_TOP && mouseY < top + OPTION_TOP + OPTION_AREA;
    }

    private boolean inOption(int index, int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return false;
        int y = top + OPTION_TOP + index * OPTION_HEIGHT - clampScroll(scrollOptions, OPTIONS.length, OPTION_AREA);
        return mouseY >= y && mouseY < y + OPTION_HEIGHT - 2;
    }

    private boolean inArrow(int mouseX, int mouseY, boolean right) {
        int x = right ? left + PANEL_WIDTH - 12 - ARROW_SIZE : left + 12;
        int y = top + ARROW_Y;
        return mouseX >= x && mouseX < x + ARROW_SIZE && mouseY >= y && mouseY < y + ARROW_SIZE;
    }

    private static int clampScroll(int scroll, int count, int height) {
        return Math.max(0, Math.min(Math.max(0, count * ROW_HEIGHT - height), scroll));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        if (page == 1) {
            drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);
        }

        if (page == 0) drawCodePage();
        else drawOptionsPage(mouseX, mouseY);

        boolean showCode = codePageVisible();
        if (field != null) {
            field.setVisible(showCode);
            field.setFocused(showCode);
        }
        if (setButton != null) setButton.visible = showCode;
        if (showCode && field != null) field.drawTextBox();

        drawFooter();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCodePage() {
        drawTitle("DOOR CODE");
        drawString(fontRenderer, "FIRST DOOR CODE", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        TileEntityDoorKeypad keypad = keypad();
        if (keypad == null) {
            drawCenteredString(fontRenderer, "Keypad unavailable", left + PANEL_WIDTH / 2,
                    top + 62, DIM);
            return;
        }
        if (keypad.hasStoredCode()) {
            drawString(fontRenderer, "A code is already set.", left + 16, top + 54, COLOUR);
            drawString(fontRenderer, "Use Reset Code on the toggles page to clear it.",
                    left + 16, top + 68, MUTED);
            drawString(fontRenderer, "Doors linked: " + keypad.getDoorCount(),
                    left + 16, top + 86, DIM);
        } else {
            drawString(fontRenderer, "Choose the first door code for this keypad.",
                    left + 16, top + 50, MUTED);
            drawString(fontRenderer, "1-6 digits. Press Enter or Set Code.",
                    left + 16, top + 92, DIM);
        }
    }

    private void drawOptionsPage(int mouseX, int mouseY) {
        drawTitle("TOGGLES");
        drawString(fontRenderer, "EVENT OUTPUTS & CONTROLS", left + 12, top + 29, 0xFFF0C0C0);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);

        scrollOptions = clampScroll(scrollOptions, OPTIONS.length, OPTION_AREA);
        GuiClip.begin(left + 8, top + OPTION_TOP, PANEL_WIDTH - 16, OPTION_AREA);
        for (int i = 0; i < OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT - scrollOptions;
            if (y + OPTION_HEIGHT < top + OPTION_TOP || y > top + OPTION_TOP + OPTION_AREA) continue;
            drawOption(OPTIONS[i], i, y, mouseX, mouseY);
        }
        GuiClip.end();
        drawScrollbar(left + PANEL_WIDTH - 8, top + OPTION_TOP, OPTION_AREA,
                OPTIONS.length, OPTION_HEIGHT, scrollOptions);
    }

    private void drawTitle(String rightLabel) {
        drawString(fontRenderer, "KEYPAD", left + 12, top + 10, COLOUR);
        drawString(fontRenderer, rightLabel,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(rightLabel), top + 10,
                page == 1 ? 0xFFE0B0B0 : MUTED);
        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    /** One option row: label and hint on the left, an ON/OFF pill or action label on the right. */
    private void drawOption(int option, int index, int y, int mouseX, int mouseY) {
        boolean action = optionIsAction(option);
        boolean on = optionValue(option);
        boolean hovered = inOption(index, mouseX, mouseY);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + OPTION_HEIGHT - 5, RED_HOVER);
        }

        drawString(fontRenderer, optionLabel(option), left + 16, y, COLOUR);
        drawString(fontRenderer, optionHint(option), left + 16, y + 10, MUTED);

        int x = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        int pillY = y + 1;
        int fill;
        int edge;
        int text;
        if (action) {
            fill = 0xFF2E3136;
            edge = 0xFF8A8A90;
            text = 0xFFE8E8E8;
        } else if (on) {
            fill = 0xFF344434;
            edge = 0xFF7FA87F;
            text = 0xFFD6F0D6;
        } else {
            fill = 0xFF3A3030;
            edge = 0xFF8A6A6A;
            text = 0xFFE6C8C8;
        }
        drawRect(x, pillY, x + PILL_WIDTH, pillY + PILL_HEIGHT, fill);
        drawRect(x, pillY, x + PILL_WIDTH, pillY + 1, edge);
        drawRect(x, pillY + PILL_HEIGHT - 1, x + PILL_WIDTH, pillY + PILL_HEIGHT,
                action ? 0xFF1D1F22 : on ? 0xFF223022 : 0xFF241C1C);

        String state = action ? actionLabel(option) : on ? "ON" : "OFF";
        drawCenteredString(fontRenderer, state, x + PILL_WIDTH / 2, pillY + 3, text);
    }

    private static String optionLabel(int option) {
        switch (option) {
            case MessageKeypadOption.ON_FAIL:
                return "On fail";
            case MessageKeypadOption.ON_SUCCESS:
                return "On success";
            case MessageKeypadOption.RESET_CODE:
                return "Reset Code";
            case MessageKeypadOption.OVERRIDE_DOOR_LOCKS:
                return "Override Door Locks";
            case MessageKeypadOption.FORCE_CLOSE:
                return "Force Close";
            default:
                return "";
        }
    }

    private static String optionHint(int option) {
        switch (option) {
            case MessageKeypadOption.ON_FAIL:
                return "Pulse linked Redstone Gates when a code is refused.";
            case MessageKeypadOption.ON_SUCCESS:
                return "Pulse linked Redstone Gates when a code is accepted.";
            case MessageKeypadOption.RESET_CODE:
                return "Clears the code and unlinks this keypad's doors.";
            case MessageKeypadOption.OVERRIDE_DOOR_LOCKS:
                return "Force opens controlled doors through lockdown.";
            case MessageKeypadOption.FORCE_CLOSE:
                return "Closes every controlled door.";
            default:
                return "";
        }
    }

    private static String actionLabel(int option) {
        switch (option) {
            case MessageKeypadOption.RESET_CODE:
                return "RESET";
            case MessageKeypadOption.OVERRIDE_DOOR_LOCKS:
                return "OPEN";
            case MessageKeypadOption.FORCE_CLOSE:
                return "CLOSE";
            default:
                return "";
        }
    }

    private void drawScrollbar(int x, int y, int height, int count, int rowHeight, int scroll) {
        int max = Math.max(0, count * rowHeight - height);
        if (max <= 0) return;

        drawRect(x, y, x + SCROLL_WIDTH, y + height, 0xFF1B1B1E);
        int thumb = Math.max(14, height * height / (count * rowHeight));
        int travel = height - thumb;
        int offset = (int) ((long) scroll * travel / max);
        drawRect(x, y + offset, x + SCROLL_WIDTH, y + offset + thumb, 0xFFB4B4B4);
    }

    private void drawFooter() {
        drawRect(left + 12, top + 152, left + PANEL_WIDTH - 12, top + 153, RULE);
        drawPageArrows(lastMouseX, lastMouseY);

        String hint = page == 0
                ? "Set a first code; the toggles page has controls"
                : "Changes apply to this keypad and its doors";
        drawCenteredString(fontRenderer, hint, left + PANEL_WIDTH / 2, top + 164, MUTED);
        drawCenteredString(fontRenderer, (page + 1) + "/" + PAGE_COUNT,
                left + PANEL_WIDTH / 2, top + 180, DIM);
    }

    private void drawPageArrows(int mouseX, int mouseY) {
        drawArrow(left + 12, top + ARROW_Y, false, mouseX, mouseY);
        drawArrow(left + PANEL_WIDTH - 12 - ARROW_SIZE, top + ARROW_Y, true, mouseX, mouseY);
    }

    private void drawArrow(int x, int y, boolean right, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + ARROW_SIZE
                && mouseY >= y && mouseY < y + ARROW_SIZE;
        drawRect(x, y, x + ARROW_SIZE, y + ARROW_SIZE, hovered ? HOVER : 0xFF232326);
        drawRect(x, y, x + ARROW_SIZE, y + 1, RULE);
        drawRect(x, y + ARROW_SIZE - 1, x + ARROW_SIZE, y + ARROW_SIZE, 0xFF151517);

        int centreX = x + ARROW_SIZE / 2;
        int centreY = y + ARROW_SIZE / 2;
        for (int i = 0; i < 5; i++) {
            int dx = right ? 4 - i : i - 4;
            drawRect(centreX + dx, centreY - i, centreX + dx + 1, centreY + 1 + i, COLOUR);
        }
    }

    /** Flat bevel button matching the Relay Key name screen. */
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
