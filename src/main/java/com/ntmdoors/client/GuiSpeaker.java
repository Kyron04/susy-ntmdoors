// SPDX-License-Identifier: LGPL-3.0-only
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

/** Small safe-style control screen for the speaker: Enabled, Sound, Range and Volume. */
@SideOnly(Side.CLIENT)
public class GuiSpeaker extends GuiScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 260;
    private static final int ROW_TOP = 44;
    private static final int ROW_HEIGHT = 30;
    private static final int PILL_WIDTH = 72;
    private static final int PILL_HEIGHT = 14;

    private static final int RANGE_HEADING_TOP = 108;
    private static final int RANGE_LABEL_TOP = 126;
    private static final int RANGE_SLIDER_TOP = 144;
    private static final int VOLUME_HEADING_TOP = 170;
    private static final int VOLUME_LABEL_TOP = 188;
    private static final int VOLUME_SLIDER_TOP = 206;
    private static final int SLIDER_LEFT = 16;
    private static final int SLIDER_RIGHT = PANEL_WIDTH - 16;
    private static final int HOLD_ROW_TOP = 44;
    private static final int HOLD_ROW_HEIGHT = 30;
    private static final int TAB_WIDTH = 52;
    private static final int TIMER_PILL_WIDTH = 76;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;
    private static final int HOVER = 0xFF3A2A2D;

    private final BlockPos pos;
    private int left;
    private int top;
    private int page;
    private boolean draggingRange;
    private boolean draggingVolume;
    private int previewRange = TileEntitySpeaker.DEFAULT_RANGE;
    private int previewVolume = TileEntitySpeaker.DEFAULT_VOLUME;

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
        page = 0;
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
        drawRect(left + 1, top + 23, left + PANEL_WIDTH - 1, top + 226, 0xFF1B1B1E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);

        drawTitle();

        TileEntitySpeaker speaker = speaker();
        if (speaker == null) {
            drawCenteredString(fontRenderer, "Unavailable", left + PANEL_WIDTH / 2,
                    top + 66, MUTED);
        } else if (page == 1) {
            drawRedstonePage(speaker, mouseX, mouseY);
        } else {
            drawHeading();
            drawRow(0, "Enabled", "Plays while powered.",
                    speaker.isEnabled() ? "ON" : "OFF", speaker.isEnabled(), mouseX, mouseY);
            drawRow(1, "Sound", "Click to cycle.",
                    TileEntitySpeaker.soundName(speaker.getSound()).toUpperCase(Locale.ROOT),
                    true, mouseX, mouseY);
            drawRange(speaker, mouseX, mouseY);
            drawVolume(speaker, mouseX, mouseY);
            drawFooter(speaker);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        drawString(fontRenderer, "SPEAKER", left + 12, top + 10, COLOUR);
        drawTab(settingsTabX(), "SETTINGS", page == 0);
        drawTab(redstoneTabX(), "REDSTONE", page == 1);
    }

    private void drawTab(int x, String label, boolean active) {
        drawString(fontRenderer, label, x, top + 10, active ? COLOUR : MUTED);
        if (active) {
            drawRect(x, top + 19, x + fontRenderer.getStringWidth(label), top + 20, HEADING);
        }
    }

    private int settingsTabX() {
        return left + PANEL_WIDTH - 12 - TAB_WIDTH * 2 - 4;
    }

    private int redstoneTabX() {
        return left + PANEL_WIDTH - 12 - TAB_WIDTH;
    }

    private boolean inTab(int mouseX, int mouseY, int x) {
        return mouseX >= x && mouseX < x + TAB_WIDTH
                && mouseY >= top + 5 && mouseY < top + 21;
    }

    private void drawRedstonePage(TileEntitySpeaker speaker, int mouseX, int mouseY) {
        drawString(fontRenderer, "WIRELESS", left + 12, top + 28, HEADING);
        drawRect(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);

        int holdY = top + HOLD_ROW_TOP;
        if (inHoldRow(mouseX, mouseY)) {
            drawRect(left + 8, holdY - 3, left + PANEL_WIDTH - 8,
                    holdY + HOLD_ROW_HEIGHT - 5, HOVER);
        }
        drawString(fontRenderer, "Remain active after wireless", left + 16, holdY, COLOUR);
        drawString(fontRenderer, "Stays on until switched off.", left + 16, holdY + 11, MUTED);
        drawPill(left + PANEL_WIDTH - 12 - PILL_WIDTH, holdY + 2,
                speaker.isRemainActive() ? "ON" : "OFF", speaker.isRemainActive());

        drawString(fontRenderer, "ACTIVATION DURATION", left + 12, top + 92, HEADING);
        drawRect(left + 12, top + 103, left + PANEL_WIDTH - 12, top + 104, RULE);
        drawString(fontRenderer, "Timer starts when the signal ends.", left + 16, top + 112, MUTED);
        drawString(fontRenderer, "Current: " + speaker.getActiveSeconds() + " s",
                left + 16, top + 128, COLOUR);
        drawTimerPill(speaker, mouseX, mouseY);

        String signal = speaker.isPowered() ? "SIGNAL ON" : "SIGNAL OFF";
        int signalColour = speaker.isPowered() ? 0xFFD6F0D6 : DIM;
        drawRect(left + 12, top + 226, left + PANEL_WIDTH - 12, top + 227, RULE);
        drawString(fontRenderer, signal, left + 14, top + 232, signalColour);
        drawCenteredString(fontRenderer, "Click TIME to change it. Right-click goes back.",
                left + PANEL_WIDTH / 2, top + 246, MUTED);
    }

    private void drawTimerPill(TileEntitySpeaker speaker, int mouseX, int mouseY) {
        int x = left + PANEL_WIDTH - 12 - TIMER_PILL_WIDTH;
        int y = top + 86;
        boolean hovered = inTimerPill(mouseX, mouseY);
        drawRect(x, y, x + TIMER_PILL_WIDTH, y + PILL_HEIGHT,
                hovered ? HOVER : 0xFF2A0F12);
        drawRect(x, y, x + TIMER_PILL_WIDTH, y + 1, RULE);
        drawRect(x, y + PILL_HEIGHT - 1, x + TIMER_PILL_WIDTH, y + PILL_HEIGHT, 0xFF1A0708);
        drawCenteredString(fontRenderer, "TIME " + speaker.getActiveSeconds() + " s",
                x + TIMER_PILL_WIDTH / 2, y + 3, hovered ? 0xFFFFFFFF : 0xFFE6C8C8);
    }

    private boolean inHoldRow(int mouseX, int mouseY) {
        int y = top + HOLD_ROW_TOP;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + HOLD_ROW_HEIGHT - 5;
    }

    private boolean inTimerPill(int mouseX, int mouseY) {
        int x = left + PANEL_WIDTH - 12 - TIMER_PILL_WIDTH;
        int y = top + 86;
        return mouseX >= x && mouseX < x + TIMER_PILL_WIDTH
                && mouseY >= y && mouseY < y + PILL_HEIGHT;
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

    private void drawRange(TileEntitySpeaker speaker, int mouseX, int mouseY) {
        drawString(fontRenderer, "RANGE", left + 12, top + RANGE_HEADING_TOP, HEADING);
        drawRect(left + 12, top + RANGE_HEADING_TOP + 11,
                left + PANEL_WIDTH - 12, top + RANGE_HEADING_TOP + 12, RULE);

        int range = currentRange(speaker);
        drawString(fontRenderer, "Range", left + 16, top + RANGE_LABEL_TOP, COLOUR);
        String value = range + " / " + TileEntitySpeaker.MAX_RANGE + " blocks";
        drawString(fontRenderer, value,
                left + PANEL_WIDTH - 16 - fontRenderer.getStringWidth(value),
                top + RANGE_LABEL_TOP, MUTED);

        drawSlider(TileEntitySpeaker.MIN_RANGE, TileEntitySpeaker.MAX_RANGE, range,
                top + RANGE_SLIDER_TOP, draggingRange, mouseX, mouseY);
    }

    private void drawVolume(TileEntitySpeaker speaker, int mouseX, int mouseY) {
        drawString(fontRenderer, "VOLUME", left + 12, top + VOLUME_HEADING_TOP, HEADING);
        drawRect(left + 12, top + VOLUME_HEADING_TOP + 11,
                left + PANEL_WIDTH - 12, top + VOLUME_HEADING_TOP + 12, RULE);

        int volume = currentVolume(speaker);
        drawString(fontRenderer, "Level", left + 16, top + VOLUME_LABEL_TOP, COLOUR);
        String value = volume + "%";
        drawString(fontRenderer, value,
                left + PANEL_WIDTH - 16 - fontRenderer.getStringWidth(value),
                top + VOLUME_LABEL_TOP, MUTED);

        drawSlider(TileEntitySpeaker.MIN_VOLUME, TileEntitySpeaker.MAX_VOLUME, volume,
                top + VOLUME_SLIDER_TOP, draggingVolume, mouseX, mouseY);
    }

    private void drawSlider(int min, int max, int value, int trackTop,
                            boolean dragging, int mouseX, int mouseY) {
        int trackLeft = left + SLIDER_LEFT;
        int trackRight = left + SLIDER_RIGHT;
        drawRect(trackLeft, trackTop, trackRight, trackTop + 4, 0xFF0B0B0D);
        drawRect(trackLeft, trackTop, trackRight, trackTop + 1, RULE);

        int knobX = trackLeft + Math.round((value - min) / (float) (max - min)
                * (trackRight - trackLeft));
        drawRect(trackLeft, trackTop, knobX, trackTop + 4, 0xFF5A2A2A);
        boolean active = dragging || inSlider(mouseX, mouseY, trackTop);
        drawRect(knobX - 5, trackTop - 4, knobX + 5, trackTop + 8, 0xFF080808);
        drawRect(knobX - 4, trackTop - 3, knobX + 4, trackTop + 7,
                active ? 0xFF8A4A4A : 0xFF5A2A2A);
        drawRect(knobX - 2, trackTop - 1, knobX + 2, trackTop + 5, 0xFFE6C8C8);

        drawString(fontRenderer, Integer.toString(min),
                left + SLIDER_LEFT, trackTop + 9, DIM);
        String maxText = Integer.toString(max);
        drawString(fontRenderer, maxText,
                left + SLIDER_RIGHT - fontRenderer.getStringWidth(maxText),
                trackTop + 9, DIM);
    }

    private int currentRange(TileEntitySpeaker speaker) {
        if (draggingRange) return previewRange;
        return speaker == null ? TileEntitySpeaker.DEFAULT_RANGE : speaker.getRange();
    }

    private int currentVolume(TileEntitySpeaker speaker) {
        if (draggingVolume) return previewVolume;
        return speaker == null ? TileEntitySpeaker.DEFAULT_VOLUME : speaker.getVolume();
    }

    private void drawFooter(TileEntitySpeaker speaker) {
        drawRect(left + 12, top + 226, left + PANEL_WIDTH - 12, top + 227, RULE);
        String signal = speaker.isPowered() ? "SIGNAL ON" : "SIGNAL OFF";
        int signalColour = speaker.isPowered() ? 0xFFD6F0D6 : DIM;
        drawString(fontRenderer, signal, left + 14, top + 232, signalColour);

        String output = speaker.isEnabled() && speaker.isPowered() && speaker.getVolume() > 0
                ? "PLAYING" : "SILENT";
        int outputColour = "PLAYING".equals(output) ? 0xFFD6F0D6 : DIM;
        drawString(fontRenderer, output,
                left + PANEL_WIDTH - 14 - fontRenderer.getStringWidth(output), top + 232, outputColour);
        drawCenteredString(fontRenderer, "Changes apply immediately.",
                left + PANEL_WIDTH / 2, top + 246, MUTED);
    }

    private boolean inRow(int index, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 3 && mouseY < y + ROW_HEIGHT - 5;
    }

    private boolean inSlider(int mouseX, int mouseY, int trackTop) {
        return mouseX >= left + SLIDER_LEFT - 6 && mouseX <= left + SLIDER_RIGHT + 6
                && mouseY >= trackTop - 8 && mouseY <= trackTop + 10;
    }

    private int rowAt(int mouseX, int mouseY) {
        for (int row = 0; row < 2; row++) {
            if (inRow(row, mouseX, mouseY)) return row;
        }
        return -1;
    }

    private void updateRange(int mouseX) {
        previewRange = valueAt(mouseX, TileEntitySpeaker.MIN_RANGE, TileEntitySpeaker.MAX_RANGE);
    }

    private void updateVolume(int mouseX) {
        previewVolume = valueAt(mouseX, TileEntitySpeaker.MIN_VOLUME, TileEntitySpeaker.MAX_VOLUME);
    }

    private int valueAt(int mouseX, int min, int max) {
        int trackLeft = left + SLIDER_LEFT;
        int trackRight = left + SLIDER_RIGHT;
        float fraction = (mouseX - trackLeft) / (float) (trackRight - trackLeft);
        fraction = Math.max(0F, Math.min(1F, fraction));
        return min + Math.round(fraction * (max - min));
    }

    private void sendOption(int option, int value) {
        ModNetwork.INSTANCE.sendToServer(new MessageSpeakerOption(pos, option, value));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && inTab(mouseX, mouseY, settingsTabX())) {
            page = 0;
            return;
        }
        if (mouseButton == 0 && inTab(mouseX, mouseY, redstoneTabX())) {
            page = 1;
            return;
        }
        if (page == 1 && mouseButton == 1 && inTimerPill(mouseX, mouseY)) {
            sendOption(MessageSpeakerOption.ACTIVE_PREV, 0);
            return;
        }
        if (mouseButton == 0) {
            TileEntitySpeaker speaker = speaker();
            if (page == 1) {
                if (speaker != null && inHoldRow(mouseX, mouseY)) {
                    sendOption(MessageSpeakerOption.REMAIN_ACTIVE,
                            speaker.isRemainActive() ? 0 : 1);
                    return;
                }
                if (speaker != null && inTimerPill(mouseX, mouseY)) {
                    sendOption(MessageSpeakerOption.ACTIVE_NEXT, 0);
                    return;
                }
                return;
            }
            if (speaker != null && inSlider(mouseX, mouseY, top + RANGE_SLIDER_TOP)) {
                previewRange = speaker.getRange();
                draggingRange = true;
                updateRange(mouseX);
                return;
            }
            if (speaker != null && inSlider(mouseX, mouseY, top + VOLUME_SLIDER_TOP)) {
                previewVolume = speaker.getVolume();
                draggingVolume = true;
                updateVolume(mouseX);
                return;
            }
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
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (draggingRange) updateRange(mouseX);
        else if (draggingVolume) updateVolume(mouseX);
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingRange) {
            updateRange(mouseX);
            sendOption(MessageSpeakerOption.RANGE, previewRange);
            draggingRange = false;
        } else if (draggingVolume) {
            updateVolume(mouseX);
            sendOption(MessageSpeakerOption.VOLUME, previewVolume);
            draggingVolume = false;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void onGuiClosed() {
        if (draggingRange) {
            sendOption(MessageSpeakerOption.RANGE, previewRange);
            draggingRange = false;
        }
        if (draggingVolume) {
            sendOption(MessageSpeakerOption.VOLUME, previewVolume);
            draggingVolume = false;
        }
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
