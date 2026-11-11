package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.MotionSensorBlockEntity;
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

import java.util.Arrays;

@Environment(EnvType.CLIENT)
public class MotionSensorScreen extends Screen {

    public static final int PANEL_WIDTH = 344;
    public static final int PANEL_HEIGHT = 196;
    public static final int PAGE_COUNT = 4;

    private static final int OPTION_TOP = 46;
    private static final int OPTION_HEIGHT = 20;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;

    private static final int ARROW_SIZE = 18;
    private static final int ARROW_Y = 159;

    private static final int AREA_GRID_X = 24;
    private static final int AREA_GRID_Y = 46;
    private static final int AREA_CELL = 20;
    private static final int AREA_FULL_Y = 60;
    private static final int AREA_CLEAR_Y = 90;
    private static final int AREA_ACTION_WIDTH = 100;
    private static final int AREA_ACTION_HEIGHT = 20;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFDCDCDC;
    private static final int HOVER = 0xFF3A3D42;
    private static final int RULE = 0xFF4A4A4E;
    private static final int RED_PANEL = 0xFF2A0F12;
    private static final int RED_RULE = 0xFF6A2A2A;
    private static final int RED_HOVER = 0xFF3A1418;

    private final BlockPos pos;
    private int left;
    private int top;
    private int page = 0;

    public MotionSensorScreen(BlockPos pos) {
        super(Text.literal("Motion Sensor Console"));
        this.pos = pos;
    }

    private MotionSensorBlockEntity sensor() {
        if (client == null || client.world == null) return null;
        BlockEntity be = client.world.getBlockEntity(pos);
        return be instanceof MotionSensorBlockEntity s ? s : null;
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

        // Panel background
        context.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        context.fill(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);
        context.fill(left + 1, top + 23, left + PANEL_WIDTH - 1, top + 152, page == 3 ? RED_PANEL : 0xFF1B1B1E);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);

        drawTitle(context);

        if (page == 0) drawAssignmentsPage(context, mouseX, mouseY);
        else if (page == 1) drawSettingsPage(context, mouseX, mouseY);
        else if (page == 2) drawAreaPage(context, mouseX, mouseY);
        else if (page == 3) drawRedstonePage(context, mouseX, mouseY);

        drawFooter(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTitle(DrawContext context) {
        context.drawText(textRenderer, "MOTION SENSOR", left + 12, top + 10, COLOUR, false);
        String sub = switch (page) {
            case 0 -> "CREDENTIALS";
            case 1 -> "DETECTION";
            case 2 -> "BOUND AREA";
            case 3 -> "REDSTONE";
            default -> "";
        };
        context.drawText(textRenderer, sub, left + PANEL_WIDTH - 12 - textRenderer.getWidth(sub), top + 10, MUTED, false);
    }

    private void drawAssignmentsPage(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "ASSIGNED KEYS & BIOMETRIC IDS", left + 12, top + 28, HEADING, false);
        context.fill(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);

        MotionSensorBlockEntity s = sensor();
        if (s == null) return;

        int y = top + 46;
        context.drawText(textRenderer, "Enrolled Private Keys: " + s.getAssignedKeys().size(), left + 16, y, COLOUR, false);
        context.drawText(textRenderer, "Enrolled Biometric IDs: " + s.getAssignedIds().size(), left + 16, y + 16, COLOUR, false);

        context.drawText(textRenderer, "Hold an Operator Keycard to manage credentials.", left + 16, y + 40, MUTED, false);
    }

    private void drawSettingsPage(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "DETECTION OPTIONS", left + 12, top + 28, HEADING, false);
        context.fill(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);

        int[] options = {0, 1, 2, 3, 4, 5, 8}; // Players, Hostile, Neutral, PersistIn, PersistOut, MatchHolder, Enabled
        for (int i = 0; i < options.length; i++) {
            int opt = options[i];
            int y = top + OPTION_TOP + i * 14;
            boolean hovered = mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8 && mouseY >= y && mouseY < y + 14;
            if (hovered) {
                context.fill(left + 8, y, left + PANEL_WIDTH - 8, y + 14, HOVER);
            }

            String label = getSettingLabel(opt);
            boolean on = getSettingValue(opt);

            context.drawText(textRenderer, label, left + 16, y + 2, COLOUR, false);
            drawPill(context, left + PANEL_WIDTH - 12 - PILL_WIDTH, y, on ? "ON" : "OFF", on);
        }
    }

    private void drawAreaPage(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "BOUND DETECTION FOOTPRINT (5x5x5)", left + 12, top + 28, HEADING, false);
        context.fill(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RULE);

        MotionSensorBlockEntity s = sensor();
        byte[] depths = s != null ? s.getAreaDepths() : new byte[25];

        // Draw 5x5 grid
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                int cx = left + AREA_GRID_X + u * AREA_CELL;
                int cy = top + AREA_GRID_Y + v * AREA_CELL;
                int depth = depths[u + v * 5];

                boolean hovered = mouseX >= cx && mouseX < cx + AREA_CELL && mouseY >= cy && mouseY < cy + AREA_CELL;
                context.fill(cx, cy, cx + AREA_CELL - 1, cy + AREA_CELL - 1, hovered ? 0xFF3A4A5A : (depth > 0 ? 0xFF143048 : 0xFF141416));
                context.fill(cx + 1, cy + 1, cx + AREA_CELL - 2, cy + AREA_CELL - 2, depth > 0 ? 0xFF184870 : 0xFF222226);

                String dStr = String.valueOf(depth);
                context.drawCenteredTextWithShadow(textRenderer, dStr, cx + AREA_CELL / 2, cy + 5, depth > 0 ? 0xFF88DDFF : 0xFF666666);
            }
        }

        // Action buttons
        int bx = left + 150;
        drawActionButton(context, bx, top + AREA_FULL_Y, "Full 5x5x5", mouseX, mouseY);
        drawActionButton(context, bx, top + AREA_CLEAR_Y, "Clear Area", mouseX, mouseY);
    }

    private void drawActionButton(DrawContext context, int x, int y, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + AREA_ACTION_WIDTH && mouseY >= y && mouseY < y + AREA_ACTION_HEIGHT;
        context.fill(x, y, x + AREA_ACTION_WIDTH, y + AREA_ACTION_HEIGHT, hovered ? 0xFF5A2A2A : 0xFF333336);
        context.fill(x + 1, y + 1, x + AREA_ACTION_WIDTH - 1, y + AREA_ACTION_HEIGHT - 1, hovered ? 0xFF2A0F12 : 0xFF222226);
        context.drawCenteredTextWithShadow(textRenderer, label, x + AREA_ACTION_WIDTH / 2, y + 6, COLOUR);
    }

    private void drawRedstonePage(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, "REDSTONE TRIGGERS", left + 12, top + 28, 0xFFF0C0C0, false);
        context.fill(left + 12, top + 39, left + PANEL_WIDTH - 12, top + 40, RED_RULE);

        int[] options = {9, 10, 11, 12}; // Authorized, Unauthorized, Disabled, Enabled
        for (int i = 0; i < options.length; i++) {
            int opt = options[i];
            int y = top + OPTION_TOP + i * 24;
            boolean hovered = mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8 && mouseY >= y && mouseY < y + 24;
            if (hovered) {
                context.fill(left + 8, y, left + PANEL_WIDTH - 8, y + 24, RED_HOVER);
            }

            String label = getSettingLabel(opt);
            boolean on = getSettingValue(opt);

            context.drawText(textRenderer, label, left + 16, y + 4, 0xFFE6C8C8, false);
            drawPill(context, left + PANEL_WIDTH - 12 - PILL_WIDTH, y + 2, on ? "ON" : "OFF", on);
        }
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

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        context.fill(left + 12, top + 152, left + PANEL_WIDTH - 12, top + 153, RULE);

        // Left Arrow
        boolean leftHovered = mouseX >= left + 12 && mouseX < left + 12 + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE;
        context.fill(left + 12, top + ARROW_Y, left + 12 + ARROW_SIZE, top + ARROW_Y + ARROW_SIZE, leftHovered ? HOVER : 0xFF232326);
        context.drawText(textRenderer, "<", left + 17, top + ARROW_Y + 5, COLOUR, false);

        // Right Arrow
        int rx = left + PANEL_WIDTH - 12 - ARROW_SIZE;
        boolean rightHovered = mouseX >= rx && mouseX < rx + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE;
        context.fill(rx, top + ARROW_Y, rx + ARROW_SIZE, top + ARROW_Y + ARROW_SIZE, rightHovered ? HOVER : 0xFF232326);
        context.drawText(textRenderer, ">", rx + 7, top + ARROW_Y + 5, COLOUR, false);

        String hint = switch (page) {
            case 0 -> "Enrolled credentials can bypass the alarm";
            case 1 -> "Detection entity categories";
            case 2 -> "Click cell: L-click +depth, R-click -depth";
            case 3 -> "Fires pulses to linked Redstone Gates";
            default -> "";
        };
        context.drawCenteredTextWithShadow(textRenderer, hint, left + PANEL_WIDTH / 2, top + 160, MUTED);
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + "/" + PAGE_COUNT, left + PANEL_WIDTH / 2, top + 174, DIM);
    }

    private String getSettingLabel(int option) {
        return switch (option) {
            case 0 -> "Detect Players";
            case 1 -> "Detect Hostile";
            case 2 -> "Detect Neutral";
            case 3 -> "Persist Within Bounds";
            case 4 -> "Persist Out of Bounds";
            case 5 -> "Match Holder";
            case 8 -> "Sensor Enabled";
            case 9 -> "Authorized Entry";
            case 10 -> "Unauthorized Entry";
            case 11 -> "Sensor Disabled";
            case 12 -> "Sensor Enabled Event";
            default -> "Option";
        };
    }

    private boolean getSettingValue(int option) {
        MotionSensorBlockEntity s = sensor();
        if (s == null) return false;
        return switch (option) {
            case 0 -> s.isDetectPlayers();
            case 1 -> s.isDetectHostile();
            case 2 -> s.isDetectNeutral();
            case 3 -> s.isPersistWithinBounds();
            case 4 -> s.isPersistOutOfBounds();
            case 5 -> s.isMatchHolder();
            case 8 -> s.isEnabled();
            case 9 -> s.isRedstoneEvent(MotionSensorBlockEntity.RS_AUTHORIZED);
            case 10 -> s.isRedstoneEvent(MotionSensorBlockEntity.RS_UNAUTHORIZED);
            case 11 -> s.isRedstoneEvent(MotionSensorBlockEntity.RS_DISABLED);
            case 12 -> s.isRedstoneEvent(MotionSensorBlockEntity.RS_ENABLED);
            default -> false;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Arrow Navigation
        if (button == 0) {
            if (mouseX >= left + 12 && mouseX < left + 12 + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE) {
                page = (page + PAGE_COUNT - 1) % PAGE_COUNT;
                return true;
            }
            int rx = left + PANEL_WIDTH - 12 - ARROW_SIZE;
            if (mouseX >= rx && mouseX < rx + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE) {
                page = (page + 1) % PAGE_COUNT;
                return true;
            }
        }

        MotionSensorBlockEntity s = sensor();
        if (s == null) return super.mouseClicked(mouseX, mouseY, button);

        if (page == 1) {
            int[] options = {0, 1, 2, 3, 4, 5, 8};
            for (int i = 0; i < options.length; i++) {
                int y = top + OPTION_TOP + i * 14;
                if (mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8 && mouseY >= y && mouseY < y + 14) {
                    int opt = options[i];
                    boolean current = getSettingValue(opt);
                    sendOption(opt, !current);
                    return true;
                }
            }
        } else if (page == 2) {
            // Check grid cells
            for (int u = 0; u < 5; u++) {
                for (int v = 0; v < 5; v++) {
                    int cx = left + AREA_GRID_X + u * AREA_CELL;
                    int cy = top + AREA_GRID_Y + v * AREA_CELL;
                    if (mouseX >= cx && mouseX < cx + AREA_CELL && mouseY >= cy && mouseY < cy + AREA_CELL) {
                        byte[] depths = Arrays.copyOf(s.getAreaDepths(), 25);
                        int cur = depths[u + v * 5];
                        int delta = button == 0 ? 1 : -1;
                        int next = Math.max(0, Math.min(5, cur + delta));
                        if (u == MotionSensorBlockEntity.CENTER && v == MotionSensorBlockEntity.CENTER && next < 1) {
                            next = 1;
                        }
                        depths[u + v * 5] = (byte) next;
                        sendArea(false, depths);
                        return true;
                    }
                }
            }

            // Buttons Full / Clear
            int bx = left + 150;
            if (mouseX >= bx && mouseX < bx + AREA_ACTION_WIDTH) {
                if (mouseY >= top + AREA_FULL_Y && mouseY < top + AREA_FULL_Y + AREA_ACTION_HEIGHT) {
                    byte[] full = new byte[25];
                    Arrays.fill(full, (byte) 5);
                    sendArea(false, full);
                    return true;
                }
                if (mouseY >= top + AREA_CLEAR_Y && mouseY < top + AREA_CLEAR_Y + AREA_ACTION_HEIGHT) {
                    sendArea(true, new byte[25]);
                    return true;
                }
            }
        } else if (page == 3) {
            int[] options = {9, 10, 11, 12};
            for (int i = 0; i < options.length; i++) {
                int y = top + OPTION_TOP + i * 24;
                if (mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8 && mouseY >= y && mouseY < y + 24) {
                    int opt = options[i];
                    boolean current = getSettingValue(opt);
                    sendOption(opt, !current);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendOption(int option, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(option);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(ModMessages.MOTION_SENSOR_OPTION_ID, buf);
    }

    private void sendArea(boolean clear, byte[] depths) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(clear);
        buf.writeByteArray(depths);
        ClientPlayNetworking.send(ModMessages.MOTION_SENSOR_AREA_ID, buf);
    }
}
