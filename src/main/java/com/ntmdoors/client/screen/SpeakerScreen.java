package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.SpeakerBlockEntity;
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

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class SpeakerScreen extends Screen {

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

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFF0C0C0;
    private static final int RULE = 0xFF4A4A4E;
    private static final int HOVER = 0xFF3A2A2D;

    private final BlockPos pos;
    private int left;
    private int top;
    private boolean draggingRange;
    private boolean draggingVolume;
    private int previewRange = SpeakerBlockEntity.DEFAULT_RANGE;
    private int previewVolume = SpeakerBlockEntity.DEFAULT_VOLUME;

    public SpeakerScreen(BlockPos pos) {
        super(Text.translatable("block.ntmdoors.speaker"));
        this.pos = pos;
    }

    private SpeakerBlockEntity speaker() {
        if (client == null || client.world == null) return null;
        BlockEntity be = client.world.getBlockEntity(pos);
        return be instanceof SpeakerBlockEntity s ? s : null;
    }

    @Override
    protected void init() {
        super.init();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        SpeakerBlockEntity s = speaker();
        if (s != null) {
            previewRange = s.getRange();
            previewVolume = s.getVolume();
        }
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
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        context.fill(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);
        context.fill(left + 1, top + 23, left + PANEL_WIDTH - 1, top + 226, 0xFF1B1B1E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);

        drawTitle(context);

        SpeakerBlockEntity speaker = speaker();
        if (speaker == null) {
            context.drawCenteredTextWithShadow(textRenderer, "Unavailable", left + PANEL_WIDTH / 2, top + 66, MUTED);
        } else {
            drawHeading(context);
            drawRow(context, 0, "Enabled", "Plays while powered.",
                    speaker.isEnabled() ? "ON" : "OFF", speaker.isEnabled(), mouseX, mouseY);
            drawRow(context, 1, "Sound", "Click to cycle.",
                    SpeakerBlockEntity.soundName(speaker.getSound()).toUpperCase(Locale.ROOT),
                    true, mouseX, mouseY);
            drawRange(context, speaker, mouseX, mouseY);
            drawVolume(context, speaker, mouseX, mouseY);
            drawFooter(context, speaker);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTitle(DrawContext context) {
        context.drawText(textRenderer, "SPEAKER", left + 12, top + 10, COLOUR, false);
        String right = "SETTINGS";
        context.drawText(textRenderer, right, left + PANEL_WIDTH - 12 - textRenderer.getWidth(right), top + 10, MUTED, false);
    }

    private void drawHeading(DrawContext context) {
        context.drawText(textRenderer, "OUTPUT", left + 12, top + 28, HEADING, false);
        context.fill(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);
    }

    private void drawRow(DrawContext context, int index, String label, String hint, String pillText,
                         boolean pillOn, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        if (inRow(index, mouseX, mouseY)) {
            context.fill(left + 8, y - 3, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 5, HOVER);
        }

        context.drawText(textRenderer, label, left + 16, y, COLOUR, false);
        context.drawText(textRenderer, hint, left + 16, y + 10, MUTED, false);

        int pillX = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        drawPill(context, pillX, y + 1, pillText, pillOn);
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

    private void drawRange(DrawContext context, SpeakerBlockEntity speaker, int mouseX, int mouseY) {
        context.drawText(textRenderer, "RANGE", left + 12, top + RANGE_HEADING_TOP, HEADING, false);
        context.fill(left + 12, top + RANGE_HEADING_TOP + 11, left + PANEL_WIDTH - 12, top + RANGE_HEADING_TOP + 12, RULE);

        int display = draggingRange ? previewRange : speaker.getRange();
        String current = display + " blocks";
        context.drawText(textRenderer, "Audible range: " + current, left + 16, top + RANGE_LABEL_TOP, COLOUR, false);

        drawSlider(context, top + RANGE_SLIDER_TOP, display, SpeakerBlockEntity.MIN_RANGE, SpeakerBlockEntity.MAX_RANGE, mouseX, mouseY);
    }

    private void drawVolume(DrawContext context, SpeakerBlockEntity speaker, int mouseX, int mouseY) {
        context.drawText(textRenderer, "VOLUME", left + 12, top + VOLUME_HEADING_TOP, HEADING, false);
        context.fill(left + 12, top + VOLUME_HEADING_TOP + 11, left + PANEL_WIDTH - 12, top + VOLUME_HEADING_TOP + 12, RULE);

        int display = draggingVolume ? previewVolume : speaker.getVolume();
        context.drawText(textRenderer, "Master volume: " + display + "%", left + 16, top + VOLUME_LABEL_TOP, COLOUR, false);

        drawSlider(context, top + VOLUME_SLIDER_TOP, display, SpeakerBlockEntity.MIN_VOLUME, SpeakerBlockEntity.MAX_VOLUME, mouseX, mouseY);
    }

    private void drawSlider(DrawContext context, int trackTop, int value, int min, int max, int mouseX, int mouseY) {
        int trackLeft = left + SLIDER_LEFT;
        int trackRight = left + SLIDER_RIGHT;
        context.fill(trackLeft, trackTop, trackRight, trackTop + 3, 0xFF141416);
        context.fill(trackLeft, trackTop + 3, trackRight, trackTop + 4, 0xFF2E2E32);

        float fraction = (value - min) / (float) (max - min);
        int thumbX = trackLeft + Math.round(fraction * (trackRight - trackLeft));
        boolean hovered = inSlider(mouseX, mouseY, trackTop);
        int thumbFill = hovered ? 0xFF8A5A5D : 0xFF5A4A4D;
        context.fill(thumbX - 4, trackTop - 5, thumbX + 4, trackTop + 8, 0xFF111111);
        context.fill(thumbX - 3, trackTop - 4, thumbX + 3, trackTop + 7, thumbFill);
        context.fill(thumbX - 3, trackTop - 4, thumbX + 3, trackTop - 3, 0xFFE6C8C8);
    }

    private void drawFooter(DrawContext context, SpeakerBlockEntity speaker) {
        context.fill(left + 12, top + 226, left + PANEL_WIDTH - 12, top + 227, RULE);
        String signal = speaker.isPowered() ? "SIGNAL ON" : "SIGNAL OFF";
        int signalColour = speaker.isPowered() ? 0xFFD6F0D6 : DIM;
        context.drawText(textRenderer, signal, left + 14, top + 232, signalColour, false);

        String output = speaker.isEnabled() && speaker.isPowered() && speaker.getVolume() > 0 ? "PLAYING" : "SILENT";
        int outputColour = "PLAYING".equals(output) ? 0xFFD6F0D6 : DIM;
        context.drawText(textRenderer, output, left + PANEL_WIDTH - 14 - textRenderer.getWidth(output), top + 232, outputColour, false);
        context.drawCenteredTextWithShadow(textRenderer, "Changes apply immediately.", left + PANEL_WIDTH / 2, top + 246, MUTED);
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
        previewRange = valueAt(mouseX, SpeakerBlockEntity.MIN_RANGE, SpeakerBlockEntity.MAX_RANGE);
    }

    private void updateVolume(int mouseX) {
        previewVolume = valueAt(mouseX, SpeakerBlockEntity.MIN_VOLUME, SpeakerBlockEntity.MAX_VOLUME);
    }

    private int valueAt(int mouseX, int min, int max) {
        int trackLeft = left + SLIDER_LEFT;
        int trackRight = left + SLIDER_RIGHT;
        float fraction = (mouseX - trackLeft) / (float) (trackRight - trackLeft);
        fraction = Math.max(0.0F, Math.min(1.0F, fraction));
        return min + Math.round(fraction * (max - min));
    }

    private void sendOption(byte option, int value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(option);
        buf.writeInt(value);
        ClientPlayNetworking.send(ModMessages.SPEAKER_OPTION_ID, buf);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            SpeakerBlockEntity speaker = speaker();
            if (speaker != null && inSlider((int) mouseX, (int) mouseY, top + RANGE_SLIDER_TOP)) {
                previewRange = speaker.getRange();
                draggingRange = true;
                updateRange((int) mouseX);
                return true;
            }
            if (speaker != null && inSlider((int) mouseX, (int) mouseY, top + VOLUME_SLIDER_TOP)) {
                previewVolume = speaker.getVolume();
                draggingVolume = true;
                updateVolume((int) mouseX);
                return true;
            }
            int row = speaker == null ? -1 : rowAt((int) mouseX, (int) mouseY);
            if (row == 0) {
                sendOption((byte) 0, speaker.isEnabled() ? 0 : 1);
                return true;
            }
            if (row == 1) {
                sendOption((byte) 1, (speaker.getSound() + 1) % SpeakerBlockEntity.SOUND_COUNT);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRange) {
            updateRange((int) mouseX);
            return true;
        } else if (draggingVolume) {
            updateVolume((int) mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingRange) {
            updateRange((int) mouseX);
            sendOption((byte) 2, previewRange);
            draggingRange = false;
            return true;
        } else if (draggingVolume) {
            updateVolume((int) mouseX);
            sendOption((byte) 3, previewVolume);
            draggingVolume = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (draggingRange) {
            sendOption((byte) 2, previewRange);
            draggingRange = false;
        }
        if (draggingVolume) {
            sendOption((byte) 3, previewVolume);
            draggingVolume = false;
        }
        super.close();
    }
}
