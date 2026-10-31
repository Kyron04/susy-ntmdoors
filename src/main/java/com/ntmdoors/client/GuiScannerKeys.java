package com.ntmdoors.client;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.network.MessageScannerKey;
import com.ntmdoors.network.MessageScannerOption;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityKeycardScanner.BiometricId;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
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
 * Scanner console, reached after a correct security code. Page one transfers access entries: the
 * left column lists the private keys and biometric identities that currently open the scanner's
 * doors, the right column lists the entries on the held Operator Keycard that are not assigned
 * yet. Page two is the scanner's settings, one row per toggle, with room to grow.
 */
@SideOnly(Side.CLIENT)
public class GuiScannerKeys extends GuiScreen {

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 196;

    private static final int PAGE_COUNT = 2;

    private static final int LIST_TOP = 46;
    private static final int LIST_HEIGHT = 102;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 150;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 182;
    private static final int SCROLL_WIDTH = 3;

    /** Settings page: rows are stacked and scroll once there are more than fit. */
    private static final int OPTION_TOP = 46;
    private static final int OPTION_HEIGHT = 26;
    private static final int OPTION_AREA = 102;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;

    private static final int ARROW_SIZE = 18;
    private static final int ARROW_Y = 159;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFDCDCDC;
    private static final int HOVER = 0xFF3A3D42;
    private static final int RULE = 0xFF4A4A4E;
    private static final int WARNING = 0xFFFFAA55;

    private final BlockPos pos;

    private int left;
    private int top;
    private int page;
    private int scrollLeft;
    private int scrollRight;
    private int scrollOptions;
    private int lastMouseX;
    private int lastMouseY;

    public GuiScannerKeys(BlockPos pos) {
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

    private TileEntityKeycardScanner scanner() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntityKeycardScanner ? (TileEntityKeycardScanner) te : null;
    }

    private boolean matchHolder() {
        TileEntityKeycardScanner scanner = scanner();
        return scanner == null || scanner.isMatchHolder();
    }

    private boolean doorsMoving() {
        TileEntityKeycardScanner scanner = scanner();
        return scanner != null && scanner.isDoorsMoving();
    }

    private boolean lockedOut() {
        TileEntityKeycardScanner scanner = scanner();
        return scanner != null && scanner.isLockedOut();
    }

    private boolean optionValue(int option) {
        TileEntityKeycardScanner scanner = scanner();
        switch (option) {
            case MessageScannerOption.MATCH_HOLDER:
                return scanner == null || scanner.isMatchHolder();
            case MessageScannerOption.LOCKDOWN:
                return scanner != null && scanner.isLockdown();
            case MessageScannerOption.LOCKOUT:
                return scanner != null && scanner.isLockoutMode();
            case MessageScannerOption.IGNORE_KEYS:
                return scanner != null && scanner.isIgnoreKeys();
            case MessageScannerOption.IGNORE_IDS:
                return scanner != null && scanner.isIgnoreIds();
            default:
                return false;
        }
    }

    private static boolean optionIsAction(int option) {
        return option == MessageScannerOption.FORCE_OPEN
                || option == MessageScannerOption.FORCE_CLOSE;
    }

    private List<Entry> assigned() {
        TileEntityKeycardScanner scanner = scanner();
        if (scanner == null) return Collections.emptyList();

        List<Entry> out = new ArrayList<>();
        for (BiometricId id : scanner.getAssignedIds()) out.add(Entry.identity(id));
        for (PrivateKeys.Key key : scanner.getAssignedKeys()) out.add(Entry.key(key));
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

    private ItemStack operatorCard() {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getHeldItemMainhand();
        if (main.getItem() instanceof ItemOperatorKeycard) return main;
        ItemStack off = mc.player.getHeldItemOffhand();
        return off.getItem() instanceof ItemOperatorKeycard ? off : ItemStack.EMPTY;
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
        } else if (inOptionsArea(lastMouseX, lastMouseY)) {
            // Unclamped here: the draw call clamps against the real option count.
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
            } else if (!doorsMoving()) {
                int option = optionAt(mouseX, mouseY);
                if (option >= 0) {
                    boolean action = optionIsAction(option);
                    ModNetwork.INSTANCE.sendToServer(
                            new MessageScannerOption(pos, option, action || !optionValue(option)));
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void send(Entry entry, boolean assign) {
        ModNetwork.INSTANCE.sendToServer(
                new MessageScannerKey(pos, entry.value, assign, entry.biometric));
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

    private int optionAt(int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return -1;
        int clamped = clampOptionsScroll(scrollOptions);
        int row = (mouseY - (top + OPTION_TOP) + clamped) / OPTION_HEIGHT;
        return row >= 0 && row < MessageScannerOption.OPTION_COUNT ? row : -1;
    }

    private boolean inOption(int index, int mouseX, int mouseY) {
        if (!inOptionsArea(mouseX, mouseY)) return false;
        int y = top + OPTION_TOP + index * OPTION_HEIGHT - clampOptionsScroll(scrollOptions);
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

    private static int maxScroll(int count, int height) {
        return Math.max(0, count * ROW_HEIGHT - height);
    }

    private static int clampOptionsScroll(int scroll) {
        int max = Math.max(0, MessageScannerOption.OPTION_COUNT * OPTION_HEIGHT - OPTION_AREA);
        return Math.max(0, Math.min(max, scroll));
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        drawDefaultBackground();

        // Same light metal frame and dark face as the card viewer and programmer.
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFF3C3C40);

        if (page == 0) drawKeysPage(mouseX, mouseY);
        else drawOptionsPage(mouseX, mouseY);

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
                operatorCard().isEmpty() ? "hold an Operator Keycard" : "nothing available");
        drawScrollbar(LEFT_X + LIST_WIDTH + 2, top + LIST_TOP, LIST_HEIGHT,
                assigned.size(), ROW_HEIGHT, scrollLeft);
        drawScrollbar(RIGHT_X + LIST_WIDTH + 2, top + LIST_TOP, LIST_HEIGHT,
                available.size(), ROW_HEIGHT, scrollRight);
    }

    private void drawOptionsPage(int mouseX, int mouseY) {
        drawTitle("OPTIONS");
        drawString(fontRenderer, "TOGGLES", left + 12, top + 29, HEADING);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        scrollOptions = clampOptionsScroll(scrollOptions);
        beginClip(left + 8, top + OPTION_TOP, PANEL_WIDTH - 16, OPTION_AREA);
        for (int i = 0; i < MessageScannerOption.OPTION_COUNT; i++) {
            int y = top + OPTION_TOP + i * OPTION_HEIGHT - scrollOptions;
            if (y + OPTION_HEIGHT < top + OPTION_TOP || y > top + OPTION_TOP + OPTION_AREA) continue;
            drawOption(i, y, mouseX, mouseY);
        }
        endClip();
        drawScrollbar(left + PANEL_WIDTH - 8, top + OPTION_TOP, OPTION_AREA,
                MessageScannerOption.OPTION_COUNT, OPTION_HEIGHT, scrollOptions);
    }

    private void drawTitle(String rightLabel) {
        drawString(fontRenderer, "KEYCARD SCANNER", left + 12, top + 10, COLOUR);
        drawString(fontRenderer, rightLabel,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(rightLabel), top + 10, MUTED);
        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawHeadings() {
        drawString(fontRenderer, "ASSIGNED KEYS/IDS", left + LEFT_X, top + 29, HEADING);
        drawString(fontRenderer, "AVAILABLE KEYS/IDS", left + RIGHT_X, top + 29, DIM);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        // Vertical divider between the two columns, like the mockup's centre seam.
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

        beginClip(x, y, LIST_WIDTH, LIST_HEIGHT);
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
        endClip();
    }

    /** One settings row: label and hint on the left, an ON/OFF pill on the right. */
    private void drawOption(int option, int y, int mouseX, int mouseY) {
        boolean enabled = !doorsMoving();
        boolean on = optionValue(option);
        boolean hovered = enabled && inOption(option, mouseX, mouseY);
        if (hovered) {
            drawRect(left + 8, y - 3, left + PANEL_WIDTH - 8, y + OPTION_HEIGHT - 5, 0xFF232326);
        }

        int textColour = enabled ? COLOUR : DIM;
        drawString(fontRenderer, optionLabel(option), left + 16, y, textColour);
        drawString(fontRenderer, optionHint(option), left + 16, y + 10,
                enabled ? MUTED : DIM);

        boolean action = optionIsAction(option);
        int x = left + PANEL_WIDTH - 12 - PILL_WIDTH;
        int pillY = y + 1;
        int fill;
        int edge;
        int text;
        if (!enabled) {
            fill = 0xFF2A2A2A;
            edge = 0xFF4A4A4A;
            text = 0xFF777777;
        } else if (action) {
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
                !enabled ? 0xFF1A1A1A : action ? 0xFF1D1F22 : on ? 0xFF223022 : 0xFF241C1C);
        String state = action ? (option == MessageScannerOption.FORCE_OPEN ? "OPEN" : "CLOSE")
                : on ? "ON" : "OFF";
        drawCenteredString(fontRenderer, state, x + PILL_WIDTH / 2, pillY + 3, text);
    }

    private static String optionLabel(int option) {
        switch (option) {
            case MessageScannerOption.MATCH_HOLDER:
                return "Match biometric holder";
            case MessageScannerOption.LOCKDOWN:
                return "Lockdown mode";
            case MessageScannerOption.LOCKOUT:
                return "Lockout mode";
            case MessageScannerOption.IGNORE_KEYS:
                return "Ignore private keys";
            case MessageScannerOption.IGNORE_IDS:
                return "Ignore biometric IDs";
            case MessageScannerOption.FORCE_OPEN:
                return "Override door locks";
            case MessageScannerOption.FORCE_CLOSE:
                return "Force close";
            default:
                return "";
        }
    }

    private static String optionHint(int option) {
        switch (option) {
            case MessageScannerOption.MATCH_HOLDER:
                return "Biometric IDs only work for their owner.";
            case MessageScannerOption.LOCKDOWN:
                return "Locks the doors. Everyone is refused.";
            case MessageScannerOption.LOCKOUT:
                return "3 failures lock it for 5 minutes.";
            case MessageScannerOption.IGNORE_KEYS:
                return "Assigned private keys are ignored.";
            case MessageScannerOption.IGNORE_IDS:
                return "Assigned biometric IDs are ignored.";
            case MessageScannerOption.FORCE_OPEN:
                return "Force open, ignoring locks and keys.";
            case MessageScannerOption.FORCE_CLOSE:
                return "Closes bound doors.";
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
        int colour = MUTED;
        if (page == 0) {
            hint = "Click a key or ID to move it - ESC to close";
        } else if (lockedOut()) {
            hint = "Lockout active - disable Lockout mode";
            colour = WARNING;
        } else if (doorsMoving()) {
            hint = "Doors are moving - wait";
            colour = WARNING;
        } else {
            hint = "Changes apply to this scanner only";
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

    /** Restricts drawing of a scrolling list to its own box. */
    private void beginClip(int x, int y, int width, int height) {
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
