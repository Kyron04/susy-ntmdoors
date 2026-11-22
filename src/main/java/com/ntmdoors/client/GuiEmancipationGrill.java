// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.item.ItemDeveloperKeycard;
import com.ntmdoors.item.ItemEmancipationRelayKey;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.network.MessageGrillBindStart;
import com.ntmdoors.network.MessageGrillFilter;
import com.ntmdoors.network.MessageGrillKey;
import com.ntmdoors.network.MessageGrillOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.tile.TileEntityEmancipationGrill.BiometricId;
import com.ntmdoors.tile.TileEntityEmancipationGrill.FieldColour;
import com.ntmdoors.tile.TileEntityEmancipationGrill.FieldMode;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Emancipation Grill console. Page one assigns private keys and biometric identities from the
 * Operator Keycard carried in the player's inventory. Page two holds the field behavior modes and
 * access/destruction toggles. Page three sets the field colour. Page four holds the redstone events.
 * Page five configures the whitelist and blacklist patterns used by Filter mode. Page six configures
 * the contraband container and starts the Relay Key binding session.
 */
@SideOnly(Side.CLIENT)
public class GuiEmancipationGrill extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;
    private static final int PAGE_COUNT = 6;
    private static final int CONTAINMENT_PAGE = 5;

    private static final int LIST_TOP = 46;
    private static final int LIST_HEIGHT = 102;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 150;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 182;
    private static final int SCROLL_WIDTH = 3;

    private static final int ARROW_SIZE = 18;
    private static final int ARROW_Y = 159;

    private static final int OPTION_TOP = 46;
    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_AREA = 102;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;
    private static final int[] TOGGLE_OPTIONS = {
            MessageGrillOption.MODE_SOLID,
            MessageGrillOption.MODE_NEUTRAL,
            MessageGrillOption.MODE_LETHAL,
            MessageGrillOption.MODE_WALL,
            MessageGrillOption.MODE_FILTER,
            MessageGrillOption.MATCH_USER,
            MessageGrillOption.IGNORE_PRIVATE_KEY,
            MessageGrillOption.IGNORE_BIOMETRIC_ID,
            MessageGrillOption.ENABLE_FIELD,
            MessageGrillOption.DISABLE_FIELD,
            MessageGrillOption.IGNORE_INVENTORY,
            MessageGrillOption.IGNORE_ITEMS,
            MessageGrillOption.AUDIO,
            MessageGrillOption.DETECT_PLAYERS,
            MessageGrillOption.DETECT_HOSTILE,
            MessageGrillOption.DETECT_NEUTRAL
    };
    private static final int[] REDSTONE_OPTIONS = {
            MessageGrillOption.REDSTONE_SOLID_PASS,
            MessageGrillOption.REDSTONE_SOLID_BLOCK,
            MessageGrillOption.REDSTONE_LETHAL_KILL,
            MessageGrillOption.REDSTONE_MATCH_USER_PASS,
            MessageGrillOption.REDSTONE_MATCH_USER_FAIL
    };
    private static final int[] COLOUR_OPTIONS = {
            MessageGrillOption.COLOUR_RED,
            MessageGrillOption.COLOUR_GREEN,
            MessageGrillOption.COLOUR_BLUE
    };

    private static final int FILTER_PAGE = 4;
    private static final int FILTER_COLUMNS = TileEntityEmancipationGrill.FILTER_COLUMNS;
    private static final int FILTER_ROWS = TileEntityEmancipationGrill.FILTER_ROWS;
    private static final int FILTER_SLOT = 16;
    private static final int FILTER_GAP = 1;
    private static final int FILTER_STEP = FILTER_SLOT + FILTER_GAP;
    private static final int FILTER_TOP = 46;
    private static final int FILTER_LEFT_X = 14;
    private static final int FILTER_RIGHT_X = 178;

    private static final int CONTAINMENT_TOGGLE_X = 20;
    private static final int CONTAINMENT_TOGGLE_Y = 50;
    private static final int CONTAINMENT_BIND_X = 20;
    private static final int CONTAINMENT_BIND_Y = 78;
    private static final int CONTAINMENT_BIND_WIDTH = 160;
    private static final int CONTAINMENT_BIND_HEIGHT = 20;

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
    private int scrollRedstone;
    private int lastMouseX;
    private int lastMouseY;
    private ItemStack hoveredFilterStack = ItemStack.EMPTY;

    public GuiEmancipationGrill(BlockPos pos) {
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
        scrollRedstone = 0;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------ data

    private TileEntityEmancipationGrill grill() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        if (!(te instanceof TileEntityEmancipationGrill)) return null;
        return ((TileEntityEmancipationGrill) te).getSecurityCore();
    }

    private List<Entry> assigned() {
        TileEntityEmancipationGrill grill = grill();
        if (grill == null) return Collections.emptyList();

        List<Entry> out = new ArrayList<>();
        for (BiometricId id : grill.getAssignedIds()) out.add(Entry.identity(id));
        for (PrivateKeys.Key key : grill.getAssignedKeys()) out.add(Entry.key(key));
        return out;
    }

    private List<Entry> available() {
        ItemStack card = operatorCard();
        if (card.isEmpty()) return Collections.emptyList();

        boolean redacted = ItemDeveloperKeycard.isRedacted(card);
        List<Entry> assigned = assigned();
        List<Entry> out = new ArrayList<>();
        if (Biometrics.isBound(card)) {
            String uuid = Biometrics.ownerUuid(card);
            if (!hasValue(assigned, uuid)) {
                out.add(redacted
                        ? new Entry(true, Biometrics.label("Unknown"), "Unknown", uuid)
                        : Entry.identity(new BiometricId(uuid, Biometrics.ownerName(card))));
            }
        }
        for (PrivateKeys.Key key : PrivateKeys.get(card)) {
            if (!hasValue(assigned, key.serial)) {
                out.add(redacted ? new Entry(false, "Unknown", "Unknown", key.serial) : Entry.key(key));
            }
        }
        return out;
    }

    private static boolean hasValue(List<Entry> entries, String value) {
        for (Entry entry : entries) {
            if (entry.value.equals(value)) return true;
        }
        return false;
    }

    /** The Relay Key occupies the hand, so look through the whole inventory. */
    private ItemStack operatorCard() {
        return HeldItems.findInInventory(mc.player, ItemOperatorKeycard.class);
    }

    private boolean optionValue(int option) {
        TileEntityEmancipationGrill grill = grill();
        switch (option) {
            case MessageGrillOption.MODE_SOLID:
                return grill == null || grill.getFieldMode() == FieldMode.SOLID;
            case MessageGrillOption.MODE_NEUTRAL:
                return grill != null && grill.getFieldMode() == FieldMode.NEUTRAL;
            case MessageGrillOption.MODE_LETHAL:
                return grill != null && grill.getFieldMode() == FieldMode.LETHAL;
            case MessageGrillOption.MODE_WALL:
                return grill != null && grill.getFieldMode() == FieldMode.WALL;
            case MessageGrillOption.MODE_FILTER:
                return grill != null && grill.getFieldMode() == FieldMode.FILTER;
            case MessageGrillOption.CONTRABAND:
                return grill != null && grill.isContrabandEnabled();
            case MessageGrillOption.COLOUR_RED:
                return grill != null && grill.getFieldColour() == FieldColour.RED;
            case MessageGrillOption.COLOUR_GREEN:
                return grill != null && grill.getFieldColour() == FieldColour.GREEN;
            case MessageGrillOption.COLOUR_BLUE:
                return grill == null || grill.getFieldColour() == FieldColour.BLUE;
            case MessageGrillOption.MATCH_USER:
                return grill == null || grill.isMatchUser();
            case MessageGrillOption.IGNORE_PRIVATE_KEY:
                return grill != null && grill.isIgnorePrivateKey();
            case MessageGrillOption.IGNORE_BIOMETRIC_ID:
                return grill != null && grill.isIgnoreBiometricId();
            case MessageGrillOption.ENABLE_FIELD:
                return grill == null || grill.isFieldEnabled();
            case MessageGrillOption.DISABLE_FIELD:
                return grill != null && !grill.isFieldEnabled();
            case MessageGrillOption.IGNORE_INVENTORY:
                return grill != null && grill.isIgnoreInventory();
            case MessageGrillOption.IGNORE_ITEMS:
                return grill != null && grill.isIgnoreItems();
            case MessageGrillOption.AUDIO:
                return grill == null || grill.isAudioEnabled();
            case MessageGrillOption.DETECT_PLAYERS:
                return grill == null || grill.isDetectPlayers();
            case MessageGrillOption.DETECT_HOSTILE:
                return grill == null || grill.isDetectHostile();
            case MessageGrillOption.DETECT_NEUTRAL:
                return grill == null || grill.isDetectNeutral();
            case MessageGrillOption.REDSTONE_SOLID_PASS:
                return grill != null
                        && grill.isRedstoneEvent(TileEntityEmancipationGrill.RS_SOLID_PASS);
            case MessageGrillOption.REDSTONE_SOLID_BLOCK:
                return grill != null
                        && grill.isRedstoneEvent(TileEntityEmancipationGrill.RS_SOLID_BLOCK);
            case MessageGrillOption.REDSTONE_LETHAL_KILL:
                return grill != null
                        && grill.isRedstoneEvent(TileEntityEmancipationGrill.RS_LETHAL_KILL);
            case MessageGrillOption.REDSTONE_MATCH_USER_PASS:
                return grill != null
                        && grill.isRedstoneEvent(TileEntityEmancipationGrill.RS_MATCH_USER_PASS);
            case MessageGrillOption.REDSTONE_MATCH_USER_FAIL:
                return grill != null
                        && grill.isRedstoneEvent(TileEntityEmancipationGrill.RS_MATCH_USER_FAIL);
            default:
                return false;
        }
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
            scrollOptions = clampOptionsScroll(
                    scrollOptions + (wheel > 0 ? -OPTION_HEIGHT : OPTION_HEIGHT),
                    TOGGLE_OPTIONS.length);
        } else if (page == 3 && inOptionsArea(lastMouseX, lastMouseY)) {
            scrollRedstone = clampOptionsScroll(
                    scrollRedstone + (wheel > 0 ? -OPTION_HEIGHT : OPTION_HEIGHT),
                    REDSTONE_OPTIONS.length);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (page == FILTER_PAGE && mouseButton == 1) {
            FilterTarget target = filterSlotAt(mouseX, mouseY);
            if (target != null) {
                clearFilterSlot(target);
                return;
            }
        }

        if (page == CONTAINMENT_PAGE && mouseButton == 0) {
            if (inContainmentToggle(mouseX, mouseY)) {
                sendOption(MessageGrillOption.CONTRABAND);
                return;
            }
            if (inContainmentBind(mouseX, mouseY) && canStartBinding()) {
                ModNetwork.INSTANCE.sendToServer(new MessageGrillBindStart(pos));
                return;
            }
        }

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
                int option = optionAt(mouseX, mouseY, TOGGLE_OPTIONS, scrollOptions);
                if (option >= 0) {
                    sendOption(option);
                    return;
                }
            } else if (page == 2) {
                int option = optionAt(mouseX, mouseY, COLOUR_OPTIONS, 0);
                if (option >= 0) {
                    sendOption(option);
                    return;
                }
            } else if (page == 3) {
                int option = optionAt(mouseX, mouseY, REDSTONE_OPTIONS, scrollRedstone);
                if (option >= 0) {
                    sendOption(option);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void send(Entry entry, boolean assign) {
        ModNetwork.INSTANCE.sendToServer(
                new MessageGrillKey(pos, entry.value, assign, entry.biometric));
    }

    private void sendOption(int option) {
        boolean value;
        switch (option) {
            case MessageGrillOption.MODE_SOLID:
            case MessageGrillOption.MODE_NEUTRAL:
            case MessageGrillOption.MODE_LETHAL:
            case MessageGrillOption.MODE_WALL:
            case MessageGrillOption.MODE_FILTER:
            case MessageGrillOption.ENABLE_FIELD:
            case MessageGrillOption.COLOUR_RED:
            case MessageGrillOption.COLOUR_GREEN:
            case MessageGrillOption.COLOUR_BLUE:
                value = true;
                break;
            case MessageGrillOption.DISABLE_FIELD:
                value = false;
                break;
            default:
                value = !optionValue(option);
                break;
        }
        ModNetwork.INSTANCE.sendToServer(new MessageGrillOption(pos, option, value));
    }

    private void changePage(int delta) {
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
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

    private int optionAt(int mouseX, int mouseY, int[] options, int scroll) {
        if (!inOptionsArea(mouseX, mouseY)) return -1;
        int clamped = clampOptionsScroll(scroll, options.length);
        int row = (mouseY - (top + OPTION_TOP) + clamped) / OPTION_HEIGHT;
        return row >= 0 && row < options.length ? options[row] : -1;
    }

    private boolean inOption(int index, int mouseX, int mouseY, int scroll) {
        int y = top + OPTION_TOP + index * OPTION_HEIGHT - scroll;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y && mouseY < y + OPTION_HEIGHT - 2;
    }

    private int clampOptionsScroll(int scroll, int count) {
        int max = Math.max(0, count * OPTION_HEIGHT - OPTION_AREA);
        return Math.max(0, Math.min(max, scroll));
    }

    private boolean inArrow(int mouseX, int mouseY, boolean right) {
        int x = right ? left + PANEL_WIDTH - 12 - ARROW_SIZE : left + 12;
        int y = top + ARROW_Y;
        return mouseX >= x && mouseX < x + ARROW_SIZE && mouseY >= y && mouseY < y + ARROW_SIZE;
    }

    private static int clampScroll(int scroll, int count, int height) {
        return Math.max(0, Math.min(maxScroll(count, height), scroll));
    }

    private static int maxScroll(int count, int height) {
        return Math.max(0, count * ROW_HEIGHT - height);
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredFilterStack = ItemStack.EMPTY;
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);
        if (page == 3) {
            drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, RED_PANEL);
        }

        if (page == 0) drawKeysPage(mouseX, mouseY);
        else if (page == 1) drawTogglesPage(mouseX, mouseY);
        else if (page == 2) drawColourPage(mouseX, mouseY);
        else if (page == 3) drawRedstonePage(mouseX, mouseY);
        else if (page == FILTER_PAGE) drawFilterPage(mouseX, mouseY);
        else drawContainmentPage(mouseX, mouseY);

        drawFooter(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (page == FILTER_PAGE && !hoveredFilterStack.isEmpty()) {
            renderToolTip(hoveredFilterStack, mouseX, mouseY);
        }
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

    private void drawTogglesPage(int mouseX, int mouseY) {
        drawTitle("TOGGLES");
        drawString(fontRenderer, "FIELD SETTINGS", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        scrollOptions = clampOptionsScroll(scrollOptions, TOGGLE_OPTIONS.length);
        GuiClip.begin(left + 8, top + OPTION_TOP, PANEL_WIDTH - 16, OPTION_AREA);
        for (int i = 0; i < TOGGLE_OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT - scrollOptions;
            if (y + OPTION_HEIGHT < top + OPTION_TOP || y > top + OPTION_TOP + OPTION_AREA) {
                continue;
            }
            drawOption(TOGGLE_OPTIONS[i], i, y, mouseX, mouseY, scrollOptions);
        }
        GuiClip.end();
        drawScrollbar(left + PANEL_WIDTH - 8, top + OPTION_TOP, OPTION_AREA,
                TOGGLE_OPTIONS.length, OPTION_HEIGHT, scrollOptions);
    }

    private void drawColourPage(int mouseX, int mouseY) {
        drawTitle(colourName() + " FIELD");
        drawString(fontRenderer, "FIELD COLOUR", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        for (int i = 0; i < COLOUR_OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT;
            drawColourOption(COLOUR_OPTIONS[i], i, y, mouseX, mouseY);
        }
    }

    private void drawFilterPage(int mouseX, int mouseY) {
        drawTitle("FILTER LISTS");
        drawString(fontRenderer, "WHITELIST", left + FILTER_LEFT_X, top + 29, 0xFFB8D8B8);
        drawString(fontRenderer, "BLACKLIST", left + FILTER_RIGHT_X, top + 29, 0xFFD8B8B8);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        drawFilterGrid(FILTER_LEFT_X, filterStacks(true), mouseX, mouseY);
        drawFilterGrid(FILTER_RIGHT_X, filterStacks(false), mouseX, mouseY);
    }

    private void drawContainmentPage(int mouseX, int mouseY) {
        drawTitle("CONTRABAND CONTAINMENT");
        TileEntityEmancipationGrill grill = grill();
        boolean enabled = grill != null && grill.isContrabandEnabled();
        boolean filterOn = grill != null && grill.getFieldMode() == FieldMode.FILTER;
        ItemStack key = HeldItems.findInInventory(mc.player, ItemEmancipationRelayKey.class);
        boolean hasKey = !key.isEmpty();
        boolean canBind = enabled && filterOn && hasKey;

        drawString(fontRenderer, "Contraband Containment",
                left + CONTAINMENT_TOGGLE_X, top + CONTAINMENT_TOGGLE_Y + 3, COLOUR);
        drawPill(left + PANEL_WIDTH - 12 - PILL_WIDTH, top + CONTAINMENT_TOGGLE_Y, enabled);

        int bx = left + CONTAINMENT_BIND_X;
        int by = top + CONTAINMENT_BIND_Y;
        int fill = canBind ? 0xFF2E3430 : 0xFF2A2A2C;
        int border = canBind ? 0xFF8FB08F : 0xFF56565A;
        drawRect(bx, by, bx + CONTAINMENT_BIND_WIDTH, by + CONTAINMENT_BIND_HEIGHT, fill);
        drawRect(bx, by, bx + CONTAINMENT_BIND_WIDTH, by + 1, border);
        drawRect(bx, by + CONTAINMENT_BIND_HEIGHT - 1,
                bx + CONTAINMENT_BIND_WIDTH, by + CONTAINMENT_BIND_HEIGHT, border);
        drawCenteredString(fontRenderer,
                canBind ? "BEGIN BINDING SESSION" : "BINDING UNAVAILABLE",
                bx + CONTAINMENT_BIND_WIDTH / 2, by + 6, canBind ? 0xFFD6F0D6 : DIM);

        int statusY = top + 112;
        if (grill != null && grill.hasContrabandBinding()) {
            String name = grill.getContrabandName();
            if (name.isEmpty()) name = "container";
            drawString(fontRenderer, trim("Container: " + name, PANEL_WIDTH - 40),
                    left + 20, statusY, COLOUR);
            BlockPos bound = grill.getContrabandPos();
            if (bound != null) {
                drawString(fontRenderer, "Position: " + bound.getX() + ", " + bound.getY()
                        + ", " + bound.getZ(), left + 20, statusY + 12, MUTED);
            }
        } else {
            drawString(fontRenderer, "No container bound - contraband will be voided",
                    left + 20, statusY, 0xFFE6A0A0);
            drawString(fontRenderer, "Full or unavailable containers void the remainder",
                    left + 20, statusY + 12, MUTED);
        }

        String warning = null;
        if (!filterOn) {
            warning = "Filter Mode is off - enable it on page 2";
        } else if (!enabled) {
            warning = "Enable Contraband Containment above";
        } else if (!hasKey) {
            warning = "Carry the Emancipation Relay Key to bind";
        }
        if (warning != null) {
            drawString(fontRenderer, warning, left + 20, top + 140, 0xFFE6A0A0);
        }
    }

    private void drawFilterGrid(int listX, List<ItemStack> stacks,
                                int mouseX, int mouseY) {
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLUMNS; col++) {
                int x = left + listX + col * FILTER_STEP;
                int y = top + FILTER_TOP + row * FILTER_STEP;
                boolean hovered = mouseX >= x && mouseX < x + FILTER_SLOT
                        && mouseY >= y && mouseY < y + FILTER_SLOT;
                drawRect(x, y, x + FILTER_SLOT, y + FILTER_SLOT,
                        hovered ? HOVER : 0xFF1B1B1E);
                drawRect(x, y, x + FILTER_SLOT, y + 1, 0xFF3A3A3E);
                drawRect(x, y, x + 1, y + FILTER_SLOT, 0xFF3A3A3E);
            }
        }

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLUMNS; col++) {
                int index = row * FILTER_COLUMNS + col;
                int x = left + listX + col * FILTER_STEP;
                int y = top + FILTER_TOP + row * FILTER_STEP;
                ItemStack stack = index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
                if (stack.isEmpty()) continue;

                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
                mc.getRenderItem().renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);
                boolean hovered = mouseX >= x && mouseX < x + FILTER_SLOT
                        && mouseY >= y && mouseY < y + FILTER_SLOT;
                if (hovered) hoveredFilterStack = stack;
            }
        }
        RenderHelper.disableStandardItemLighting();
        if (depth) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        if (lighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private List<ItemStack> filterStacks(boolean whitelist) {
        TileEntityEmancipationGrill grill = grill();
        if (grill == null) return Collections.emptyList();
        return grill.getFilterStacks(whitelist);
    }

    private FilterTarget filterSlotAt(int mouseX, int mouseY) {
        for (FilterTarget target : getFilterTargets()) {
            if (mouseX >= target.x && mouseX < target.x + target.width
                    && mouseY >= target.y && mouseY < target.y + target.height) {
                return target;
            }
        }
        return null;
    }

    private boolean inContainmentToggle(int mouseX, int mouseY) {
        return mouseX >= left + CONTAINMENT_TOGGLE_X
                && mouseX < left + PANEL_WIDTH - 12
                && mouseY >= top + CONTAINMENT_TOGGLE_Y
                && mouseY < top + CONTAINMENT_TOGGLE_Y + PILL_HEIGHT;
    }

    private boolean inContainmentBind(int mouseX, int mouseY) {
        return mouseX >= left + CONTAINMENT_BIND_X
                && mouseX < left + CONTAINMENT_BIND_X + CONTAINMENT_BIND_WIDTH
                && mouseY >= top + CONTAINMENT_BIND_Y
                && mouseY < top + CONTAINMENT_BIND_Y + CONTAINMENT_BIND_HEIGHT;
    }

    private boolean canStartBinding() {
        TileEntityEmancipationGrill grill = grill();
        if (grill == null || !grill.isContrabandEnabled()) return false;
        if (grill.getFieldMode() != FieldMode.FILTER) return false;
        return !HeldItems.findInInventory(mc.player, ItemEmancipationRelayKey.class).isEmpty();
    }

    private void clearFilterSlot(FilterTarget target) {        if (page != FILTER_PAGE) return;
        sendFilter(target.whitelist, target.slot, ItemStack.EMPTY);
    }

    private void sendFilter(boolean whitelist, int slot, ItemStack stack) {
        ModNetwork.INSTANCE.sendToServer(
                new MessageGrillFilter(pos, whitelist, slot, stack));
    }

    /** Called by the JEI ghost handler; only valid while the filter page is open. */
    public void setFilterFromJei(boolean whitelist, int slot, ItemStack stack) {
        if (page != FILTER_PAGE || stack == null || stack.isEmpty()) return;
        sendFilter(whitelist, slot, stack);
    }

    public List<FilterTarget> getFilterTargets() {
        if (page != FILTER_PAGE) return Collections.emptyList();
        List<FilterTarget> targets = new ArrayList<>(FILTER_COLUMNS * FILTER_ROWS * 2);
        addFilterTargets(targets, true, FILTER_LEFT_X);
        addFilterTargets(targets, false, FILTER_RIGHT_X);
        return targets;
    }

    public boolean isFilterPage() {
        return page == FILTER_PAGE;
    }

    public int getPanelLeft() {
        return left;
    }

    public int getPanelTop() {
        return top;
    }

    public int getPanelWidth() {
        return PANEL_WIDTH;
    }

    public int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    private void addFilterTargets(List<FilterTarget> targets, boolean whitelist, int listX) {
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLUMNS; col++) {
                int index = row * FILTER_COLUMNS + col;
                int x = left + listX + col * FILTER_STEP;
                int y = top + FILTER_TOP + row * FILTER_STEP;
                targets.add(new FilterTarget(whitelist, index, x, y, FILTER_SLOT, FILTER_SLOT));
            }
        }
    }

    private void drawRedstonePage(int mouseX, int mouseY) {
        drawTitle("REDSTONE");
        drawString(fontRenderer, "EVENT OUTPUTS", left + 12, top + 29, 0xFFF0C0C0);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RED_RULE);

        scrollRedstone = clampOptionsScroll(scrollRedstone, REDSTONE_OPTIONS.length);
        GuiClip.begin(left + 8, top + OPTION_TOP, PANEL_WIDTH - 16, OPTION_AREA);
        for (int i = 0; i < REDSTONE_OPTIONS.length; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT - scrollRedstone;
            if (y + OPTION_HEIGHT < top + OPTION_TOP || y > top + OPTION_TOP + OPTION_AREA) {
                continue;
            }
            drawOption(REDSTONE_OPTIONS[i], i, y, mouseX, mouseY, scrollRedstone);
        }
        GuiClip.end();
        drawScrollbar(left + PANEL_WIDTH - 8, top + OPTION_TOP, OPTION_AREA,
                REDSTONE_OPTIONS.length, OPTION_HEIGHT, scrollRedstone);
    }

    /** One settings row: label and hint on the left, an ON/OFF pill on the right. */
    private void drawOption(int option, int index, int y, int mouseX, int mouseY, int scroll) {
        boolean on = optionValue(option);
        boolean hovered = inOption(index, mouseX, mouseY, scroll);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + OPTION_HEIGHT - 5,
                    page == 3 ? RED_HOVER : HOVER);
        }

        drawString(fontRenderer, optionLabel(option), left + 16, y, COLOUR);
        drawString(fontRenderer, optionHint(option), left + 16, y + 10, MUTED);
        drawPill(left + PANEL_WIDTH - 12 - PILL_WIDTH, y + 1, on);
    }

    /** One colour row: a swatch, label and hint on the left, an ON/OFF pill on the right. */
    private void drawColourOption(int option, int index, int y, int mouseX, int mouseY) {
        boolean on = optionValue(option);
        boolean hovered = inOption(index, mouseX, mouseY, 0);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + OPTION_HEIGHT - 5, HOVER);
        }

        int swatchX = left + 16;
        int swatchY = y + 3;
        drawRect(swatchX - 1, swatchY - 1, swatchX + 11, swatchY + 11, 0xFF111111);
        drawRect(swatchX, swatchY, swatchX + 10, swatchY + 10, colourSwatch(option));
        drawString(fontRenderer, optionLabel(option), left + 32, y, COLOUR);
        drawString(fontRenderer, optionHint(option), left + 32, y + 10, MUTED);
        drawPill(left + PANEL_WIDTH - 12 - PILL_WIDTH, y + 1, on);
    }

    private void drawPill(int x, int y, boolean on) {
        int fill = on ? 0xFF344434 : 0xFF3A3030;
        int edge = on ? 0xFF7FA87F : 0xFF8A6A6A;
        int text = on ? 0xFFD6F0D6 : 0xFFE6C8C8;
        int bottom = on ? 0xFF223022 : 0xFF241C1C;

        drawRect(x, y, x + PILL_WIDTH, y + PILL_HEIGHT, fill);
        drawRect(x, y, x + PILL_WIDTH, y + 1, edge);
        drawRect(x, y + PILL_HEIGHT - 1, x + PILL_WIDTH, y + PILL_HEIGHT, bottom);
        drawCenteredString(fontRenderer, on ? "ON" : "OFF", x + PILL_WIDTH / 2, y + 3, text);
    }

    private String colourName() {
        TileEntityEmancipationGrill grill = grill();
        FieldColour colour = grill == null ? FieldColour.BLUE : grill.getFieldColour();
        switch (colour) {
            case RED:
                return "RED";
            case GREEN:
                return "GREEN";
            default:
                return "BLUE";
        }
    }

    private static int colourSwatch(int option) {
        switch (option) {
            case MessageGrillOption.COLOUR_RED:
                return 0xFFF24D40;
            case MessageGrillOption.COLOUR_GREEN:
                return 0xFF40D961;
            case MessageGrillOption.COLOUR_BLUE:
                return 0xFF94D1FF;
            default:
                return 0xFF808080;
        }
    }

    private static String optionLabel(int option) {
        switch (option) {
            case MessageGrillOption.MODE_SOLID:
                return "Solid Field";
            case MessageGrillOption.MODE_NEUTRAL:
                return "Neutral Field";
            case MessageGrillOption.MODE_LETHAL:
                return "Lethal Field";
            case MessageGrillOption.MODE_WALL:
                return "Wall Mode";
            case MessageGrillOption.MODE_FILTER:
                return "Filter Mode";
            case MessageGrillOption.COLOUR_RED:
                return "Red";
            case MessageGrillOption.COLOUR_GREEN:
                return "Green";
            case MessageGrillOption.COLOUR_BLUE:
                return "Blue";
            case MessageGrillOption.MATCH_USER:
                return "Match User";
            case MessageGrillOption.IGNORE_PRIVATE_KEY:
                return "Ignore Private Key";
            case MessageGrillOption.IGNORE_BIOMETRIC_ID:
                return "Ignore Biometric ID";
            case MessageGrillOption.ENABLE_FIELD:
                return "Enable Field";
            case MessageGrillOption.DISABLE_FIELD:
                return "Disable Field";
            case MessageGrillOption.IGNORE_INVENTORY:
                return "Ignore Inventory";
            case MessageGrillOption.IGNORE_ITEMS:
                return "Ignore Items";
            case MessageGrillOption.AUDIO:
                return "Audio";
            case MessageGrillOption.DETECT_PLAYERS:
                return "Detect Players";
            case MessageGrillOption.DETECT_HOSTILE:
                return "Detect Hostile Mobs";
            case MessageGrillOption.DETECT_NEUTRAL:
                return "Detect Neutral Mobs";
            case MessageGrillOption.REDSTONE_SOLID_PASS:
                return "Solid: Authorized Pass";
            case MessageGrillOption.REDSTONE_SOLID_BLOCK:
                return "Solid: Unauthorized Block";
            case MessageGrillOption.REDSTONE_LETHAL_KILL:
                return "Lethal: Kill";
            case MessageGrillOption.REDSTONE_MATCH_USER_PASS:
                return "Match User: Match";
            case MessageGrillOption.REDSTONE_MATCH_USER_FAIL:
                return "Match User: Mismatch";
            default:
                return "";
        }
    }

    private static String optionHint(int option) {
        switch (option) {
            case MessageGrillOption.MODE_SOLID:
                return "Block unauthorized players";
            case MessageGrillOption.MODE_NEUTRAL:
                return "Visible, no field effects";
            case MessageGrillOption.MODE_LETHAL:
                return "Kill unauthorized players";
            case MessageGrillOption.MODE_WALL:
                return "Solid wall, no clearance";
            case MessageGrillOption.MODE_FILTER:
                return "Everyone passes; void blacklisted items";
            case MessageGrillOption.COLOUR_RED:
                return "Tint the energy field red";
            case MessageGrillOption.COLOUR_GREEN:
                return "Tint the energy field green";
            case MessageGrillOption.COLOUR_BLUE:
                return "Tint the energy field blue";
            case MessageGrillOption.MATCH_USER:
                return "Biometric ID must match carrier";
            case MessageGrillOption.IGNORE_PRIVATE_KEY:
                return "Private keys grant no access";
            case MessageGrillOption.IGNORE_BIOMETRIC_ID:
                return "Biometric IDs grant no access";
            case MessageGrillOption.ENABLE_FIELD:
                return "Activate this field";
            case MessageGrillOption.DISABLE_FIELD:
                return "Deactivate this field";
            case MessageGrillOption.IGNORE_INVENTORY:
                return "Lethal field keeps items";
            case MessageGrillOption.IGNORE_ITEMS:
                return "Dropped items survive lethal";
            case MessageGrillOption.AUDIO:
                return "Play field sounds";
            case MessageGrillOption.DETECT_PLAYERS:
                return "Affect players in the field";
            case MessageGrillOption.DETECT_HOSTILE:
                return "Affect monsters and hostile mobs";
            case MessageGrillOption.DETECT_NEUTRAL:
                return "Affect animals, villagers and NPCs";
            case MessageGrillOption.REDSTONE_SOLID_PASS:
                return "Pulse when clearance crosses";
            case MessageGrillOption.REDSTONE_SOLID_BLOCK:
                return "Pulse when an entry is blocked";
            case MessageGrillOption.REDSTONE_LETHAL_KILL:
                return "Pulse when the field kills";
            case MessageGrillOption.REDSTONE_MATCH_USER_PASS:
                return "Pulse when ID matches carrier";
            case MessageGrillOption.REDSTONE_MATCH_USER_FAIL:
                return "Pulse when ID does not match";
            default:
                return "";
        }
    }

    private void drawTitle(String rightLabel) {
        drawString(fontRenderer, "EMANCIPATION GRILL", left + 12, top + 10, COLOUR);
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
        int colour = MUTED;
        if (page == 0) {
            hint = "Click a key or ID to move it - ESC to close";
        } else if (page == 1) {
            hint = "Click a setting to change it - ESC to close";
        } else if (page == 2) {
            hint = "Click a colour to apply it - ESC to close";
        } else if (page == 3) {
            hint = "Click a signal to enable it - pulses linked gates";
        } else if (page == FILTER_PAGE) {
            TileEntityEmancipationGrill grill = grill();
            if (grill == null || !grill.isFieldEnabled()) {
                hint = "Field is disabled - enable it on page 2";
                colour = 0xFFE6A0A0;
            } else if (grill.getFieldMode() != FieldMode.FILTER) {
                hint = "Filter Mode is off - enable it on page 2";
                colour = 0xFFE6A0A0;
            } else if (mc.player != null
                    && (mc.player.isCreative() || mc.player.isSpectator())) {
                hint = "Creative/spectator players are not filtered";
                colour = 0xFFE6A0A0;
            } else {
                hint = "Drag from JEI/HEI to set - right-click to clear";
            }
        } else {
            TileEntityEmancipationGrill grill = grill();
            if (grill == null || !grill.isFieldEnabled()) {
                hint = "Field is disabled - enable it on page 2";
                colour = 0xFFE6A0A0;
            } else if (grill.getFieldMode() != FieldMode.FILTER) {
                hint = "Filter Mode is off - enable it on page 2";
                colour = 0xFFE6A0A0;
            } else if (!grill.isContrabandEnabled()) {
                hint = "Contraband Containment is off - enable it above";
            } else if (!grill.hasContrabandBinding()) {
                hint = "No container bound - contraband will be voided";
                colour = 0xFFE6A0A0;
            } else {
                hint = "Bind a container, then confirm at the Grill";
            }
        }
        drawCenteredString(fontRenderer, hint, left + PANEL_WIDTH / 2, top + 164, colour);
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

    /** One filter slot in either grid. Public for the optional JEI ghost handler. */
    public static final class FilterTarget {

        public final boolean whitelist;
        public final int slot;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private FilterTarget(boolean whitelist, int slot, int x, int y, int width, int height) {
            this.whitelist = whitelist;
            this.slot = slot;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
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
