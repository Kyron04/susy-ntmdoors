package com.ntmdoors.client;

import com.ntmdoors.network.MessageGateOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Redstone Gate settings: hold, invert and the wireless activation duration. */
@SideOnly(Side.CLIENT)
public class GuiRedstoneGate extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;

    private static final int RED_PANEL = 0xFF2A0F12;
    private static final int RED_RULE = 0xFF6A2A2A;
    private static final int RED_HOVER = 0xFF3A1418;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;

    private static final int TOGGLE_TOP = 46;
    private static final int TOGGLE_HEIGHT = 24;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;

    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_TOP = 126;
    private static final int BUTTON_ROW_GAP = 4;

    private static final String[] ADD_LABELS = {"+1s", "+5s", "+10s", "+20s"};
    private static final String[] SUB_LABELS = {"-1s", "-5s", "-10s", "-20s"};
    private static final int[] ADD_OPTIONS = {
            MessageGateOption.ADD_1, MessageGateOption.ADD_5,
            MessageGateOption.ADD_10, MessageGateOption.ADD_20
    };
    private static final int[] SUB_OPTIONS = {
            MessageGateOption.SUB_1, MessageGateOption.SUB_5,
            MessageGateOption.SUB_10, MessageGateOption.SUB_20
    };

    private final BlockPos pos;

    private int left;
    private int top;

    public GuiRedstoneGate(BlockPos pos) {
        this.pos = pos;
    }

    private TileEntityRedstoneGate gate() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntityRedstoneGate ? (TileEntityRedstoneGate) te : null;
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
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);

        drawTitle();
        drawOperation(mouseX, mouseY);
        drawDuration(mouseX, mouseY);
        drawFooter();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        drawString(fontRenderer, "REDSTONE GATE", left + 12, top + 10, COLOUR);
        String right = "SETTINGS";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);
        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawOperation(int mouseX, int mouseY) {
        drawString(fontRenderer, "OPERATION", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);

        TileEntityRedstoneGate gate = gate();
        drawToggle(0, "Stay active after signal",
                "Holds the gate on until switched off here.",
                gate != null && gate.isStayActive(), mouseX, mouseY);
        drawToggle(1, "Invert operation",
                "Powered when there is no input.",
                gate != null && gate.isInverted(), mouseX, mouseY);
    }

    private void drawToggle(int index, String label, String hint, boolean on, int mouseX, int mouseY) {
        int y = top + TOGGLE_TOP + index * TOGGLE_HEIGHT;
        boolean hovered = inToggle(index, mouseX, mouseY);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + TOGGLE_HEIGHT - 5, RED_HOVER);
        }

        drawString(fontRenderer, label, left + 16, y, COLOUR);
        drawString(fontRenderer, hint, left + 16, y + 10, MUTED);

        int x = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        drawPill(x, y + 1, on ? "ON" : "OFF", on);
    }

    private void drawDuration(int mouseX, int mouseY) {
        drawString(fontRenderer, "ACTIVATION DURATION", left + 12, top + 96, HEADING);
        drawRect(left + 12, top + 107, left + PANEL_WIDTH - 12, top + 108, RED_RULE);

        TileEntityRedstoneGate gate = gate();
        String current = gate == null ? "Unavailable"
                : "Current: " + gate.getActiveSeconds() + " s";
        drawCenteredString(fontRenderer, current, left + PANEL_WIDTH / 2, top + 112, MUTED);

        for (int i = 0; i < 4; i++) {
            drawDurationButton(ADD_LABELS[i], i, 0, mouseX, mouseY);
            drawDurationButton(SUB_LABELS[i], i, 1, mouseX, mouseY);
        }
    }

    private void drawDurationButton(String label, int column, int row, int mouseX, int mouseY) {
        int x = buttonX(column);
        int y = buttonY(row);
        boolean hovered = mouseX >= x && mouseX < x + BUTTON_WIDTH
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        drawRect(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, 0xFF080808);
        drawRect(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1,
                hovered ? 0xFF8A4A4A : 0xFF5A2A2A);
        drawRect(x + 3, y + 3, x + BUTTON_WIDTH - 3, y + BUTTON_HEIGHT - 3,
                hovered ? 0xFF3A1418 : 0xFF2A0F12);
        drawCenteredString(fontRenderer, label, x + BUTTON_WIDTH / 2, y + 6,
                hovered ? 0xFFFFFFFF : 0xFFE6C8C8);
    }

    private void drawPill(int x, int y, String label, boolean on) {
        int fill = on ? 0xFF344434 : 0xFF3A3030;
        int edge = on ? 0xFF7FA87F : 0xFF8A6A6A;
        int text = on ? 0xFFD6F0D6 : 0xFFE6C8C8;
        drawRect(x, y, x + PILL_WIDTH, y + PILL_HEIGHT, fill);
        drawRect(x, y, x + PILL_WIDTH, y + 1, edge);
        drawRect(x, y + PILL_HEIGHT - 1, x + PILL_WIDTH, y + PILL_HEIGHT,
                on ? 0xFF223022 : 0xFF241C1C);
        drawCenteredString(fontRenderer, label, x + PILL_WIDTH / 2, y + 3, text);
    }

    private void drawFooter() {
        drawRect(left + 12, top + 174, left + PANEL_WIDTH - 12, top + 175, RULE);
        drawCenteredString(fontRenderer, "Changes apply to this gate immediately",
                left + PANEL_WIDTH / 2, top + 181, MUTED);
    }

    private int buttonX(int column) {
        int total = BUTTON_WIDTH * 4 + BUTTON_GAP * 3;
        return left + (PANEL_WIDTH - total) / 2 + column * (BUTTON_WIDTH + BUTTON_GAP);
    }

    private int buttonY(int row) {
        return top + BUTTON_TOP + row * (BUTTON_HEIGHT + BUTTON_ROW_GAP);
    }

    private boolean inToggle(int index, int mouseX, int mouseY) {
        int y = top + TOGGLE_TOP + index * TOGGLE_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + TOGGLE_HEIGHT - 5;
    }

    private int durationOptionAt(int mouseX, int mouseY) {
        for (int row = 0; row < 2; row++) {
            int y = buttonY(row);
            if (mouseY < y || mouseY >= y + BUTTON_HEIGHT) continue;
            for (int column = 0; column < 4; column++) {
                int x = buttonX(column);
                if (mouseX < x || mouseX >= x + BUTTON_WIDTH) continue;
                return row == 0 ? ADD_OPTIONS[column] : SUB_OPTIONS[column];
            }
        }
        return -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int duration = durationOptionAt(mouseX, mouseY);
            if (duration >= 0) {
                ModNetwork.INSTANCE.sendToServer(new MessageGateOption(pos, duration, false));
                return;
            }
            if (inToggle(0, mouseX, mouseY)) {
                TileEntityRedstoneGate gate = gate();
                ModNetwork.INSTANCE.sendToServer(new MessageGateOption(pos,
                        MessageGateOption.STAY_ACTIVE, gate != null && !gate.isStayActive()));
                return;
            }
            if (inToggle(1, mouseX, mouseY)) {
                TileEntityRedstoneGate gate = gate();
                ModNetwork.INSTANCE.sendToServer(new MessageGateOption(pos,
                        MessageGateOption.INVERT, gate != null && !gate.isInverted()));
                return;
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
        super.keyTyped(typedChar, keyCode);
    }
}
