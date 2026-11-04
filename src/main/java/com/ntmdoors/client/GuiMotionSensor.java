package com.ntmdoors.client;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.network.MessageSensorArea;
import com.ntmdoors.network.MessageSensorKey;
import com.ntmdoors.network.MessageSensorOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityMotionSensor;
import com.ntmdoors.tile.TileEntityMotionSensor.BiometricId;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Motion Sensor console. Page one assigns private keys and biometric identities from the Operator
 * Keycard carried in the player's inventory; page two holds the detection toggles.
 */
@SideOnly(Side.CLIENT)
public class GuiMotionSensor extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;
    private static final int PAGE_COUNT = 4;

    private static final int LIST_TOP = 46;
    private static final int LIST_HEIGHT = 102;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 150;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 182;
    private static final int SCROLL_WIDTH = 3;

    private static final int OPTION_TOP = 46;
    private static final int OPTION_HEIGHT = 24;
    private static final int OPTION_AREA = 102;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;

    private static final int ARROW_SIZE = 18;
    private static final int ARROW_Y = 159;

    /** Bound Area page: one 5x5 footprint grid, depth shown as opacity. */
    private static final int AREA_GRID_X = 24;
    private static final int AREA_GRID_Y = 46;
    private static final int AREA_CELL = 20;
    private static final int AREA_ACTION_X = 136;
    private static final int AREA_FULL_Y = 112;
    private static final int AREA_CLEAR_Y = 132;
    private static final int AREA_ACTION_WIDTH = 100;
    private static final int AREA_ACTION_HEIGHT = 16;

    private static final int[] SETTINGS_OPTIONS = {
            MessageSensorOption.DETECT_PLAYERS,
            MessageSensorOption.DETECT_HOSTILE,
            MessageSensorOption.DETECT_NEUTRAL,
            MessageSensorOption.PERSIST_WITHIN_BOUNDS,
            MessageSensorOption.PERSIST_OUT_OF_BOUNDS,
            MessageSensorOption.MATCH_HOLDER,
            MessageSensorOption.IGNORE_KEYS,
            MessageSensorOption.IGNORE_IDS,
            MessageSensorOption.ENABLED
    };

    private static final int[] REDSTONE_OPTIONS = {
            MessageSensorOption.REDSTONE_AUTHORIZED,
            MessageSensorOption.REDSTONE_UNAUTHORIZED,
            MessageSensorOption.REDSTONE_DISABLED,
            MessageSensorOption.REDSTONE_ENABLED
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
    private int scrollLeft;
    private int scrollRight;
    private int scrollOptions;
    private int lastMouseX;
    private int lastMouseY;

    public GuiMotionSensor(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        page = 0;
        scrollLeft = 0;
        scrollRight = 0;
        scrollOptions = 0;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------ data

    private TileEntityMotionSensor sensor() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntityMotionSensor ? (TileEntityMotionSensor) te : null;
    }

    private boolean optionValue(int option) {
        TileEntityMotionSensor sensor = sensor();
        switch (option) {
            case MessageSensorOption.MATCH_HOLDER:
                return sensor == null || sensor.isMatchHolder();
            case MessageSensorOption.IGNORE_KEYS:
                return sensor != null && sensor.isIgnoreKeys();
            case MessageSensorOption.IGNORE_IDS:
                return sensor != null && sensor.isIgnoreIds();
            case MessageSensorOption.ENABLED:
                return sensor == null || sensor.isEnabled();
            case MessageSensorOption.DETECT_PLAYERS:
                return sensor == null || sensor.isDetectPlayers();
            case MessageSensorOption.DETECT_HOSTILE:
                return sensor == null || sensor.isDetectHostile();
            case MessageSensorOption.DETECT_NEUTRAL:
                return sensor == null || sensor.isDetectNeutral();
            case MessageSensorOption.PERSIST_WITHIN_BOUNDS:
                return sensor != null && sensor.isPersistWithinBounds();
            case MessageSensorOption.PERSIST_OUT_OF_BOUNDS:
                return sensor != null && sensor.isPersistOutOfBounds();
            case MessageSensorOption.REDSTONE_AUTHORIZED:
                return sensor != null && sensor.isRedstoneEvent(TileEntityMotionSensor.RS_AUTHORIZED);
            case MessageSensorOption.REDSTONE_UNAUTHORIZED:
                return sensor != null && sensor.isRedstoneEvent(TileEntityMotionSensor.RS_UNAUTHORIZED);
            case MessageSensorOption.REDSTONE_DISABLED:
                return sensor != null && sensor.isRedstoneEvent(TileEntityMotionSensor.RS_DISABLED);
            case MessageSensorOption.REDSTONE_ENABLED:
                return sensor != null && sensor.isRedstoneEvent(TileEntityMotionSensor.RS_ENABLED);
            default:
                return false;
        }
    }

    private List<Entry> assigned() {
        TileEntityMotionSensor sensor = sensor();
        if (sensor == null) return Collections.emptyList();

        List<Entry> out = new ArrayList<>();
        for (BiometricId id : sensor.getAssignedIds()) out.add(Entry.identity(id));
        for (PrivateKeys.Key key : sensor.getAssignedKeys()) out.add(Entry.key(key));
        return out;
    }

    private List<Entry> available() {
        ItemStack card = operatorCard();
        if (card.isEmpty()) return Collections.emptyList();

        List<Entry> assigned = assigned();
        List<Entry> out = new ArrayList<>();
        if (Biometrics.isBound(card)) {
            String uuid = Biometrics.ownerUuid(card);
            if (!hasValue(assigned, uuid)) {
                out.add(Entry.identity(new BiometricId(uuid, Biometrics.ownerName(card))));
            }
        }
        for (PrivateKeys.Key key : PrivateKeys.get(card)) {
            if (!hasValue(assigned, key.serial)) out.add(Entry.key(key));
        }
        return out;
    }

    private static boolean hasValue(List<Entry> entries, String value) {
        for (Entry entry : entries) {
            if (entry.value.equals(value)) return true;
        }
        return false;
    }

    /** The Motion Relay Key occupies the hand, so look through the whole inventory. */
    private ItemStack operatorCard() {
        return HeldItems.findInInventory(mc.player, ItemOperatorKeycard.class);
    }

    // ------------------------------------------------------------ input

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        if (page == 0) {
            if (inLeftList(lastMouseX, lastMouseY)) {
                scrollLeft = clampScroll(scrollLeft + (wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT),
                        assigned().size(), LIST_HEIGHT);
            } else if (inRightList(lastMouseX, lastMouseY)) {
                scrollRight = clampScroll(scrollRight + (wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT),
                        available().size(), LIST_HEIGHT);
            }
        } else if (page == 1 && inOptionsArea(lastMouseX, lastMouseY)) {
            scrollOptions += wheel > 0 ? -OPTION_HEIGHT : OPTION_HEIGHT;
        }
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
                List<Entry> assigned = assigned();
                int leftRow = rowAt(mouseX, mouseY, LEFT_X, scrollLeft, assigned.size());
                if (leftRow >= 0) {
                    send(assigned.get(leftRow), false);
                    return;
                }

                List<Entry> available = available();
                int rightRow = rowAt(mouseX, mouseY, RIGHT_X, scrollRight, available.size());
                if (rightRow >= 0) {
                    send(available.get(rightRow), true);
                    return;
                }
            } else if (page == 1) {
                int option = settingsOptionAt(mouseX, mouseY);
                if (option >= 0) {
                    ModNetwork.INSTANCE.sendToServer(
                            new MessageSensorOption(pos, option, !optionValue(option)));
                    return;
                }
            } else if (page == 3) {
                int option = redstoneOptionAt(mouseX, mouseY);
                if (option >= 0) {
                    ModNetwork.INSTANCE.sendToServer(
                            new MessageSensorOption(pos, option, !optionValue(option)));
                    return;
                }
            }
        }

        if (page == 2 && (mouseButton == 0 || mouseButton == 1)) {
            int cell = areaCellAt(mouseX, mouseY);
            if (cell >= 0) {
                int[] depths = currentDepths();
                if (mouseButton == 0) {
                    depths[cell] = Math.min(5, depths[cell] + 1);
                    if (depths[12] < 1) depths[12] = 1;
                    sendAreaDepths(depths);
                } else {
                    if (depths[cell] <= 0) return;
                    depths[cell] = Math.max(0, depths[cell] - 1);
                    if (cell == 12) depths[cell] = Math.max(1, depths[cell]);
                    sendAreaDepths(depths);
                }
                return;
            }

            if (mouseButton == 0) {
                int action = areaActionAt(mouseX, mouseY);
                if (action == 0) {
                    int[] depths = new int[25];
                    java.util.Arrays.fill(depths, 5);
                    sendAreaDepths(depths);
                    return;
                }
                if (action == 1) {
                    ModNetwork.INSTANCE.sendToServer(
                            new MessageSensorArea(pos, true, new byte[0]));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void send(Entry entry, boolean assign) {
        ModNetwork.INSTANCE.sendToServer(
                new MessageSensorKey(pos, entry.value, assign, entry.biometric));
    }

    // ------------------------------------------------------------ bound area

    private int[] currentDepths() {
        int[] depths = new int[25];
        TileEntityMotionSensor sensor = sensor();
        if (sensor == null || !sensor.hasArea()) return depths;
        for (int v = 0; v < 5; v++) {
            for (int u = 0; u < 5; u++) depths[u + v * 5] = sensor.getAreaDepth(u, v);
        }
        return depths;
    }

    private void sendAreaDepths(int[] depths) {
        byte[] bytes = new byte[depths.length];
        for (int i = 0; i < depths.length; i++) bytes[i] = (byte) depths[i];
        ModNetwork.INSTANCE.sendToServer(new MessageSensorArea(pos, false, bytes));
    }

    /** Grid index u + v*5, or -1 when the mouse is outside the 5x5 footprint. */
    private int areaCellAt(int mouseX, int mouseY) {
        int x = mouseX - (left + AREA_GRID_X);
        int y = mouseY - (top + AREA_GRID_Y);
        if (x < 0 || y < 0 || x >= AREA_CELL * 5 || y >= AREA_CELL * 5) return -1;
        return x / AREA_CELL + (y / AREA_CELL) * 5;
    }

    private int areaActionAt(int mouseX, int mouseY) {
        int x = left + AREA_ACTION_X;
        if (mouseX >= x && mouseX < x + AREA_ACTION_WIDTH) {
            if (mouseY >= top + AREA_FULL_Y && mouseY < top + AREA_FULL_Y + AREA_ACTION_HEIGHT) {
                return 0;
            }
            if (mouseY >= top + AREA_CLEAR_Y && mouseY < top + AREA_CLEAR_Y + AREA_ACTION_HEIGHT) {
                return 1;
            }
        }
        return -1;
    }

    private void changePage(int delta) {
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
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

    private int rowAt(int mouseX, int mouseY, int listX, int scroll, int count) {
        if (!inList(mouseX, mouseY, listX)) return -1;
        int row = (mouseY - (top + LIST_TOP) + scroll) / ROW_HEIGHT;
        return row >= 0 && row < count ? row : -1;
    }

    private boolean inLeftList(int mouseX, int mouseY) {
        return inList(mouseX, mouseY, LEFT_X);
    }

    private boolean inRightList(int mouseX, int mouseY) {
        return inList(mouseX, mouseY, RIGHT_X);
    }

    private boolean inList(int mouseX, int mouseY, int listX) {
        int x = left + listX;
        int y = top + LIST_TOP;
        return mouseX >= x && mouseX < x + LIST_WIDTH
                && mouseY >= y && mouseY < y + LIST_HEIGHT;
    }

    private boolean inOptionsArea(int mouseX, int mouseY) {
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= top + OPTION_TOP && mouseY < top + OPTION_TOP + OPTION_AREA;
    }

    private int settingsOptionAt(int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return -1;
        int clamped = clampOptionsScroll(scrollOptions, SETTINGS_OPTIONS.length);
        int row = (mouseY - (top + OPTION_TOP) + clamped) / OPTION_HEIGHT;
        return row >= 0 && row < SETTINGS_OPTIONS.length ? SETTINGS_OPTIONS[row] : -1;
    }

    private int redstoneOptionAt(int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return -1;
        int row = (mouseY - (top + OPTION_TOP)) / OPTION_HEIGHT;
        return row >= 0 && row < REDSTONE_OPTIONS.length ? REDSTONE_OPTIONS[row] : -1;
    }

    private boolean inOption(int index, int mouseX, int mouseY, int scroll, int count) {
        if (!inOptionsArea(mouseX, mouseY)) return false;
        int y = top + OPTION_TOP + index * OPTION_HEIGHT - clampOptionsScroll(scroll, count);
        return mouseY >= y && mouseY < y + OPTION_HEIGHT - 2;
    }

    private boolean inArrow(int mouseX, int mouseY, boolean right) {
        int x = right ? left + PANEL_WIDTH - 12 - ARROW_SIZE : left + 12;
        int y = top + ARROW_Y;
        return mouseX >= x && mouseX < x + ARROW_SIZE && mouseY >= y && mouseY < y + ARROW_SIZE;
    }

    private static int clampScroll(int scroll, int count, int height) {
        return Math.max(0, Math.min(maxScroll(count, height), scroll));
    }

    private static int clampOptionsScroll(int scroll, int count) {
        int max = Math.max(0, count * OPTION_HEIGHT - OPTION_AREA);
        return Math.max(0, Math.min(max, scroll));
    }

    private static int maxScroll(int count, int height) {
        return Math.max(0, count * ROW_HEIGHT - height);
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        if (page == 3) {
            drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);
        }

        if (page == 0) drawKeysPage(mouseX, mouseY);
        else if (page == 1) drawOptionsPage(mouseX, mouseY);
        else if (page == 2) drawAreaPage(mouseX, mouseY);
        else drawRedstonePage(mouseX, mouseY);

        drawFooter(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawKeysPage(int mouseX, int mouseY) {
        List<Entry> assigned = assigned();
        List<Entry> available = available();
        int count = assigned.size();
        drawTitle(count == 0 ? "Nothing assigned"
                : count + (count == 1 ? " entry assigned" : " entries assigned"));
        drawHeadings();
        scrollLeft = clampScroll(scrollLeft, assigned.size(), LIST_HEIGHT);
        scrollRight = clampScroll(scrollRight, available.size(), LIST_HEIGHT);

        drawList(LEFT_X, assigned, scrollLeft, mouseX, mouseY, "nothing assigned");
        drawList(RIGHT_X, available, scrollRight, mouseX, mouseY,
                operatorCard().isEmpty() ? "carry an Operator Keycard" : "nothing available");
        drawScrollbar(LEFT_X + LIST_WIDTH + 2, top + LIST_TOP, LIST_HEIGHT,
                assigned.size(), ROW_HEIGHT, scrollLeft);
        drawScrollbar(RIGHT_X + LIST_WIDTH + 2, top + LIST_TOP, LIST_HEIGHT,
                available.size(), ROW_HEIGHT, scrollRight);
    }

    private void drawOptionsPage(int mouseX, int mouseY) {
        drawTitle("OPTIONS");
        drawString(fontRenderer, "TOGGLES", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        scrollOptions = clampOptionsScroll(scrollOptions, SETTINGS_OPTIONS.length);
        GuiClip.begin(left + 8, top + OPTION_TOP, PANEL_WIDTH - 16, OPTION_AREA);
        for (int i = 0; i < SETTINGS_OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT - scrollOptions;
            if (y + OPTION_HEIGHT < top + OPTION_TOP || y > top + OPTION_TOP + OPTION_AREA) continue;
            drawOption(SETTINGS_OPTIONS[i], i, y, mouseX, mouseY, scrollOptions,
                    SETTINGS_OPTIONS.length);
        }
        GuiClip.end();
        drawScrollbar(left + PANEL_WIDTH - 8, top + OPTION_TOP, OPTION_AREA,
                SETTINGS_OPTIONS.length, OPTION_HEIGHT, scrollOptions);
    }

    private void drawRedstonePage(int mouseX, int mouseY) {
        drawTitle("REDSTONE");
        drawString(fontRenderer, "EVENT OUTPUTS", left + 12, top + 29, 0xFFF0C0C0);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);

        for (int i = 0; i < REDSTONE_OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT;
            drawOption(REDSTONE_OPTIONS[i], i, y, mouseX, mouseY, 0, REDSTONE_OPTIONS.length);
        }
    }

    private void drawAreaPage(int mouseX, int mouseY) {
        TileEntityMotionSensor sensor = sensor();
        boolean bound = sensor != null && sensor.hasArea();
        int[] depths = currentDepths();
        int selected = 0;
        for (int depth : depths) selected += depth;

        drawTitle(bound ? selected + " blocks" : "UNBOUND");
        drawString(fontRenderer, "MAX 5 x 5 x 5", left + 24, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        for (int v = 0; v < 5; v++) {
            for (int u = 0; u < 5; u++) {
                int x = left + AREA_GRID_X + u * AREA_CELL;
                int y = top + AREA_GRID_Y + v * AREA_CELL;
                drawAreaCell(u, v, x, y, depths[u + v * 5], mouseX, mouseY);
            }
        }

        // The sensor sits at the centre of the footprint, regardless of the selected depths.
        int centreX = left + AREA_GRID_X + 2 * AREA_CELL + AREA_CELL / 2;
        int centreY = top + AREA_GRID_Y + 2 * AREA_CELL + AREA_CELL / 2;
        drawRect(centreX - 3, centreY - 3, centreX + 4, centreY + 4, 0xFF0A5A2A);
        drawRect(centreX - 2, centreY - 2, centreX + 3, centreY + 3, 0xFF35E06A);
        GlStateManager.disableBlend();

        drawString(fontRenderer, "LEFT CLICK +1 DEPTH", left + 136, top + 52, COLOUR);
        drawString(fontRenderer, "RIGHT CLICK -1 DEPTH", left + 136, top + 64, COLOUR);
        drawString(fontRenderer, "OPACITY = DEPTH", left + 136, top + 76, MUTED);
        drawString(fontRenderer, "CENTER DOT = SENSOR", left + 136, top + 88, MUTED);
        drawString(fontRenderer, "SELECTED: " + selected, left + 136, top + 100, MUTED);

        drawActionButton(AREA_ACTION_X, AREA_FULL_Y, AREA_ACTION_WIDTH,
                "Full 5x5x5", areaActionAt(mouseX, mouseY) == 0);
        drawActionButton(AREA_ACTION_X, AREA_CLEAR_Y, AREA_ACTION_WIDTH,
                "Clear", areaActionAt(mouseX, mouseY) == 1);
    }

    private void drawAreaCell(int u, int v, int x, int y, int depth, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + AREA_CELL && mouseY >= y && mouseY < y + AREA_CELL;
        drawRect(x, y, x + AREA_CELL, y + AREA_CELL, hovered ? 0xFF2A2D32 : 0xFF1B1B1E);

        if (depth > 0) {
            int alpha = Math.min(210, 55 + depth * 31);
            int colour = (alpha << 24) | 0x003A7BFF;
            drawRect(x + 1, y + 1, x + AREA_CELL - 1, y + AREA_CELL - 1, colour);
        }

        drawRect(x, y, x + AREA_CELL, y + 1, RULE);
        drawRect(x, y, x + 1, y + AREA_CELL, RULE);
        drawRect(x + AREA_CELL - 1, y, x + AREA_CELL, y + AREA_CELL, RULE);
        drawRect(x, y + AREA_CELL - 1, x + AREA_CELL, y + AREA_CELL, RULE);
        drawString(fontRenderer, String.valueOf(depth), x + 2, y + 2, depth > 0 ? 0xFFFFFFFF : 0xFF6A6A6A);
    }

    private void drawActionButton(int x, int y, int width, String label, boolean hovered) {
        int ax = left + x;
        drawRect(ax, top + y, ax + width, top + y + AREA_ACTION_HEIGHT,
                hovered ? HOVER : 0xFF232326);
        drawRect(ax, top + y, ax + width, top + y + 1, RULE);
        drawRect(ax, top + y + AREA_ACTION_HEIGHT - 1, ax + width, top + y + AREA_ACTION_HEIGHT,
                0xFF151517);
        drawCenteredString(fontRenderer, label, ax + width / 2, top + y + 4, COLOUR);
    }

    private void drawTitle(String rightLabel) {
        drawString(fontRenderer, "MOTION SENSOR", left + 12, top + 10, COLOUR);
        drawString(fontRenderer, rightLabel,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(rightLabel), top + 10, MUTED);
        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawHeadings() {
        drawString(fontRenderer, "ASSIGNED KEYS/IDS", left + LEFT_X, top + 29, HEADING);
        drawString(fontRenderer, "AVAILABLE KEYS/IDS", left + RIGHT_X, top + 29, DIM);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        int divider = left + 172;
        drawRect(divider, top + 22, divider + 1, top + 150, 0xFF18181A);
        drawRect(divider + 1, top + 22, divider + 2, top + 150, 0xFF4A4A4E);
    }

    private void drawList(int listX, List<Entry> entries, int scroll,
                          int mouseX, int mouseY, String emptyText) {
        int x = left + listX;
        int y = top + LIST_TOP;

        if (entries.isEmpty()) {
            drawCenteredString(fontRenderer, emptyText, x + LIST_WIDTH / 2, y + 42, DIM);
            return;
        }

        GuiClip.begin(x, y, LIST_WIDTH, LIST_HEIGHT);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = y + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < y || rowY > y + LIST_HEIGHT) continue;

            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH
                    && mouseY >= Math.max(y, rowY) && mouseY < Math.min(y + LIST_HEIGHT, rowY + ROW_HEIGHT - 2);
            if (hovered) {
                drawRect(x, rowY, x + LIST_WIDTH, rowY + ROW_HEIGHT - 2, HOVER);
            }

            Entry entry = entries.get(i);
            drawString(fontRenderer, trim(entry.label, LIST_WIDTH - 8), x + 4, rowY + 3,
                    hovered ? 0xFFFFFFFF : COLOUR);
            drawString(fontRenderer, entry.detail, x + 4, rowY + 13, MUTED);
        }
        GuiClip.end();
    }

    /** One settings row: label and hint on the left, an ON/OFF pill on the right. */
    private void drawOption(int option, int index, int y, int mouseX, int mouseY,
                            int scroll, int count) {
        boolean on = optionValue(option);
        boolean hovered = inOption(index, mouseX, mouseY, scroll, count);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + OPTION_HEIGHT - 5,
                    page == 3 ? RED_HOVER : 0xFF232326);
        }

        drawString(fontRenderer, optionLabel(option), left + 16, y, COLOUR);
        drawString(fontRenderer, optionHint(option), left + 16, y + 10, MUTED);

        int x = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        int pillY = y + 1;
        int fill = on ? 0xFF344434 : 0xFF3A3030;
        int edge = on ? 0xFF7FA87F : 0xFF8A6A6A;
        int text = on ? 0xFFD6F0D6 : 0xFFE6C8C8;
        drawRect(x, pillY, x + PILL_WIDTH, pillY + PILL_HEIGHT, fill);
        drawRect(x, pillY, x + PILL_WIDTH, pillY + 1, edge);
        drawRect(x, pillY + PILL_HEIGHT - 1, x + PILL_WIDTH, pillY + PILL_HEIGHT,
                on ? 0xFF223022 : 0xFF241C1C);
        drawCenteredString(fontRenderer, on ? "ON" : "OFF", x + PILL_WIDTH / 2, pillY + 3, text);
    }

    private static String optionLabel(int option) {
        switch (option) {
            case MessageSensorOption.MATCH_HOLDER:
                return "Match user";
            case MessageSensorOption.IGNORE_KEYS:
                return "Ignore private keys";
            case MessageSensorOption.IGNORE_IDS:
                return "Ignore biometric IDs";
            case MessageSensorOption.ENABLED:
                return "Enabled";
            case MessageSensorOption.DETECT_PLAYERS:
                return "Detect players";
            case MessageSensorOption.DETECT_HOSTILE:
                return "Detect hostile mobs";
            case MessageSensorOption.DETECT_NEUTRAL:
                return "Detect neutral mobs";
            case MessageSensorOption.PERSIST_WITHIN_BOUNDS:
                return "Persist within bounds";
            case MessageSensorOption.PERSIST_OUT_OF_BOUNDS:
                return "Persist out of bounds";
            case MessageSensorOption.REDSTONE_AUTHORIZED:
                return "Authorized entry";
            case MessageSensorOption.REDSTONE_UNAUTHORIZED:
                return "Unauthorized entry";
            case MessageSensorOption.REDSTONE_DISABLED:
                return "Sensor disabled";
            case MessageSensorOption.REDSTONE_ENABLED:
                return "Sensor enabled";
            default:
                return "";
        }
    }

    private static String optionHint(int option) {
        switch (option) {
            case MessageSensorOption.MATCH_HOLDER:
                return "Biometric cards only work for their owner.";
            case MessageSensorOption.IGNORE_KEYS:
                return "Assigned private keys are ignored.";
            case MessageSensorOption.IGNORE_IDS:
                return "Assigned biometric IDs are ignored.";
            case MessageSensorOption.ENABLED:
                return "Disable to stop detection.";
            case MessageSensorOption.DETECT_PLAYERS:
                return "Players in the bound area.";
            case MessageSensorOption.DETECT_HOSTILE:
                return "Monsters and hostile mobs.";
            case MessageSensorOption.DETECT_NEUTRAL:
                return "Animals, villagers and NPCs.";
            case MessageSensorOption.PERSIST_WITHIN_BOUNDS:
                return "Hold the signal until they leave.";
            case MessageSensorOption.PERSIST_OUT_OF_BOUNDS:
                return "Power while the area is empty.";
            case MessageSensorOption.REDSTONE_AUTHORIZED:
                return "A cleared player enters.";
            case MessageSensorOption.REDSTONE_UNAUTHORIZED:
                return "An unknown player enters.";
            case MessageSensorOption.REDSTONE_DISABLED:
                return "Fires when disabled.";
            case MessageSensorOption.REDSTONE_ENABLED:
                return "Fires when re-enabled.";
            default:
                return "";
        }
    }

    private String trim(String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;
        String cut = text;
        while (!cut.isEmpty() && fontRenderer.getStringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
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

    private void drawFooter(int mouseX, int mouseY) {
        drawRect(left + 12, top + 152, left + PANEL_WIDTH - 12, top + 153, RULE);
        drawPageArrows(mouseX, mouseY);

        String hint;
        if (page == 0) hint = "Click a key or ID to move it - ESC to close";
        else if (page == 1) hint = "Changes apply to this Motion Sensor";
        else if (page == 2) hint = "Left click adds depth, right click removes it";
        else hint = "Each ON event pulses every bound Redstone Gate";
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

    /** One row: a private key or a biometric identity. */
    private static final class Entry {

        final boolean biometric;
        final String label;
        final String detail;
        final String value;

        private Entry(boolean biometric, String label, String detail, String value) {
            this.biometric = biometric;
            this.label = label;
            this.detail = detail;
            this.value = value;
        }

        static Entry key(PrivateKeys.Key key) {
            return new Entry(false, key.name, key.serial, key.serial);
        }

        static Entry identity(BiometricId id) {
            return new Entry(true, Biometrics.label(id.name), shortId(id.uuid), id.uuid);
        }

        private static String shortId(String uuid) {
            return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        }
    }
}
