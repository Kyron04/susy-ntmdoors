package com.ntmdoors.client;

import com.ntmdoors.network.MessageSpeakerOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Locale;

/** Small safe-style toggle screen for the speaker: Enabled and the selected sound. */
@SideOnly(Side.CLIENT)
public class GuiSpeaker extends GuiScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 150;
    private static final int ROW_TOP = 44;
    private static final int ROW_HEIGHT = 30;
    private static final int PILL_WIDTH = 60;
    private static final int PILL_HEIGHT = 14;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;
    private static final int HOVER = 0xFF3A2A2D;

    private final BlockPos pos;
    private int left;
    private int top;

    public GuiSpeaker(BlockPos pos) {
        this.pos = pos;
    }

    private TileEntitySpeaker speaker() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntitySpeaker ? (TileEntitySpeaker) te : null;
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
        drawRect(left + 1, top + 23, left + PANEL_WIDTH - 1, top + 108, 0xFF1B1B1E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);

        drawTitle();

        TileEntitySpeaker speaker = speaker();
        if (speaker == null) {
            drawCenteredString(fontRenderer, "Unavailable", left + PANEL_WIDTH / 2,
                    top + 66, MUTED);
        } else {
            drawHeading();
            drawRow(0, "Enabled", "Plays while powered.",
                    speaker.isEnabled() ? "ON" : "OFF", speaker.isEnabled(), mouseX, mouseY);
            drawRow(1, "Sound", "Alarm (placeholder).",
                    TileEntitySpeaker.soundName(speaker.getSound()).toUpperCase(Locale.ROOT),
                    true, mouseX, mouseY);
            drawFooter(speaker);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        drawString(fontRenderer, "SPEAKER", left + 12, top + 10, COLOUR);
        String right = "SETTINGS";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);
    }

    private void drawHeading() {
        drawString(fontRenderer, "OUTPUT", left + 12, top + 28, HEADING);
        drawRect(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);
    }

    private void drawRow(int index, String label, String hint, String pillText,
                         boolean pillOn, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        if (inRow(index, mouseX, mouseY)) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, HOVER);
        }

        drawString(fontRenderer, label, left + 16, y, COLOUR);
        drawString(fontRenderer, hint, left + 16, y + 11, MUTED);
        drawPill(left + PANEL_WIDTH - 12 - PILL_WIDTH, y + 2, pillText, pillOn);
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

    private void drawFooter(TileEntitySpeaker speaker) {
        drawRect(left + 12, top + 108, left + PANEL_WIDTH - 12, top + 109, RULE);
        String signal = speaker.isPowered() ? "SIGNAL ON" : "SIGNAL OFF";
        int signalColour = speaker.isPowered() ? 0xFFD6F0D6 : DIM;
        drawString(fontRenderer, signal, left + 14, top + 116, signalColour);

        String output = speaker.isEnabled() && speaker.isPowered() ? "PLAYING" : "SILENT";
        int outputColour = speaker.isEnabled() && speaker.isPowered() ? 0xFFD6F0D6 : DIM;
        drawString(fontRenderer, output,
                left + PANEL_WIDTH - 14 - fontRenderer.getStringWidth(output), top + 116, outputColour);
        drawCenteredString(fontRenderer, "Changes apply immediately.",
                left + PANEL_WIDTH / 2, top + 131, MUTED);
    }

    private boolean inRow(int index, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + ROW_HEIGHT - 5;
    }

    private int rowAt(int mouseX, int mouseY) {
        for (int row = 0; row < 2; row++) {
            if (inRow(row, mouseX, mouseY)) return row;
        }
        return -1;
    }

    private void sendOption(int option, int value) {
        ModNetwork.INSTANCE.sendToServer(new MessageSpeakerOption(pos, option, value));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            TileEntitySpeaker speaker = speaker();
            int row = speaker == null ? -1 : rowAt(mouseX, mouseY);
            if (row == 0) {
                sendOption(MessageSpeakerOption.ENABLED, speaker.isEnabled() ? 0 : 1);
                return;
            }
            if (row == 1) {
                sendOption(MessageSpeakerOption.SOUND,
                        (speaker.getSound() + 1) % TileEntitySpeaker.SOUND_COUNT);
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
