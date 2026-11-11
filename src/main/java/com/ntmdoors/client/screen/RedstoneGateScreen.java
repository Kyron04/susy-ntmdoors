package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class RedstoneGateScreen extends Screen {

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
    private static final int[] ADD_OPTIONS = {2, 3, 4, 5};
    private static final int[] SUB_OPTIONS = {6, 7, 8, 9};

    private final BlockPos pos;
    private int left;
    private int top;

    public RedstoneGateScreen(BlockPos pos) {
        super(Text.translatable("block.ntmdoors.redstone_gate"));
        this.pos = pos;
    }

    private RedstoneGateBlockEntity gate() {
        if (client == null || client.world == null) return null;
        BlockEntity be = client.world.getBlockEntity(pos);
        return be instanceof RedstoneGateBlockEntity g ? g : null;
    }

    @Override
    protected void init() {
        super.init();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);

        drawTitle(context);
        drawOperation(context, mouseX, mouseY);
        drawDuration(context, mouseX, mouseY);
        drawFooter(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTitle(DrawContext context) {
        context.drawText(textRenderer, "REDSTONE GATE", left + 12, top + 10, COLOUR, false);
        String right = "SETTINGS";
        context.drawText(textRenderer, right, left + PANEL_WIDTH - 12 - textRenderer.getWidth(right), top + 10, MUTED, false);
        context.fill(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawOperation(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "OPERATION", left + 12, top + 29, HEADING, false);
        context.fill(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);

        RedstoneGateBlockEntity g = gate();
        drawToggle(context, 0, "Stay active after signal", "Keeps it on after a signal, until switched off here.",
                g != null && g.isStayActive(), mouseX, mouseY);
        drawToggle(context, 1, "Invert operation", "Powered when there is no input.",
                g != null && g.isInverted(), mouseX, mouseY);
    }

    private void drawToggle(DrawContext context, int index, String label, String hint, boolean on, int mouseX, int mouseY) {
        int y = top + TOGGLE_TOP + index * TOGGLE_HEIGHT;
        boolean hovered = inToggle(index, mouseX, mouseY);
        if (hovered) {
            context.fill(left + 8, y - 3, left + PANEL_WIDTH - 8, y + TOGGLE_HEIGHT - 5, RED_HOVER);
        }

        context.drawText(textRenderer, label, left + 16, y, COLOUR, false);
        context.drawText(textRenderer, hint, left + 16, y + 10, MUTED, false);

        int pillX = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        drawPill(context, pillX, y + 1, on ? "ON" : "OFF", on);
    }

    private void drawDuration(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "ACTIVATION DURATION", left + 12, top + 96, HEADING, false);
        context.fill(left + 12, top + 107, left + PANEL_WIDTH - 12, top + 108, RED_RULE);

        RedstoneGateBlockEntity g = gate();
        String current = g == null ? "Unavailable" : "Current: " + (g.getActiveTicks() / 20) + " s";
        context.drawCenteredTextWithShadow(textRenderer, current, left + PANEL_WIDTH / 2, top + 112, MUTED);

        for (int i = 0; i < 4; i++) {
            drawDurationButton(context, ADD_LABELS[i], i, 0, mouseX, mouseY);
            drawDurationButton(context, SUB_LABELS[i], i, 1, mouseX, mouseY);
        }
    }

    private void drawDurationButton(DrawContext context, String label, int column, int row, int mouseX, int mouseY) {
        int bx = buttonX(column);
        int by = buttonY(row);
        boolean hovered = mouseX >= bx && mouseX < bx + BUTTON_WIDTH && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
        context.fill(bx, by, bx + BUTTON_WIDTH, by + BUTTON_HEIGHT, 0xFF080808);
        context.fill(bx + 1, by + 1, bx + BUTTON_WIDTH - 1, by + BUTTON_HEIGHT - 1, hovered ? 0xFF8A4A4A : 0xFF5A2A2A);
        context.fill(bx + 3, by + 3, bx + BUTTON_WIDTH - 3, by + BUTTON_HEIGHT - 3, hovered ? 0xFF3A1418 : 0xFF2A0F12);
        context.drawCenteredTextWithShadow(textRenderer, label, bx + BUTTON_WIDTH / 2, by + 6, hovered ? 0xFFFFFFFF : 0xFFE6C8C8);
    }

    private void drawPill(DrawContext context, int px, int py, String label, boolean on) {
        int fill = on ? 0xFF344434 : 0xFF3A3030;
        int edge = on ? 0xFF7FA87F : 0xFF8A6A6A;
        int text = on ? 0xFFD6F0D6 : 0xFFE6C8C8;
        context.fill(px, py, px + PILL_WIDTH, py + PILL_HEIGHT, fill);
        context.fill(px, py, px + PILL_WIDTH, py + 1, edge);
        context.fill(px, py + PILL_HEIGHT - 1, px + PILL_WIDTH, py + PILL_HEIGHT, on ? 0xFF223022 : 0xFF241C1C);
        context.drawCenteredTextWithShadow(textRenderer, label, px + PILL_WIDTH / 2, py + 3, text);
    }

    private void drawFooter(DrawContext context) {
        context.fill(left + 12, top + 174, left + PANEL_WIDTH - 12, top + 175, RULE);
        context.drawCenteredTextWithShadow(textRenderer, "Changes apply to this gate immediately",
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
                int bx = buttonX(column);
                if (mouseX < bx || mouseX >= bx + BUTTON_WIDTH) continue;
                return row == 0 ? ADD_OPTIONS[column] : SUB_OPTIONS[column];
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int duration = durationOptionAt((int) mouseX, (int) mouseY);
            if (duration >= 0) {
                sendOption((byte) duration, false);
                return true;
            }
            if (inToggle(0, (int) mouseX, (int) mouseY)) {
                RedstoneGateBlockEntity g = gate();
                sendOption((byte) 0, g != null && !g.isStayActive());
                return true;
            }
            if (inToggle(1, (int) mouseX, (int) mouseY)) {
                RedstoneGateBlockEntity g = gate();
                sendOption((byte) 1, g != null && !g.isInverted());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendOption(byte option, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(option);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(ModMessages.GATE_OPTION_ID, buf);
    }
}
