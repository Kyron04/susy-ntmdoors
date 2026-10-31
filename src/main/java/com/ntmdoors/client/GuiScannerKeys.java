package com.ntmdoors.client;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.network.MessageScannerKey;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityKeycardScanner;
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
 * Scanner key assignment screen, reached after a correct security code. The left column lists the
 * private keys that currently open the scanner's doors; the right column lists the keys on the held
 * Operator Keycard that are not assigned yet. Clicking a key moves it between the two lists.
 */
@SideOnly(Side.CLIENT)
public class GuiScannerKeys extends GuiScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 196;

    private static final int LIST_TOP = 46;
    private static final int LIST_HEIGHT = 102;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 116;
    private static final int LEFT_X = 12;
    private static final int RIGHT_X = 146;
    private static final int SCROLL_WIDTH = 3;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HEADING = 0xFFDCDCDC;
    private static final int HOVER = 0xFF3A3D42;
    private static final int RULE = 0xFF4A4A4E;

    private final BlockPos pos;

    private int left;
    private int top;
    private int scrollLeft;
    private int scrollRight;
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
        scrollLeft = 0;
        scrollRight = 0;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------ data

    private List<PrivateKeys.Key> assigned() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        if (te instanceof TileEntityKeycardScanner) {
            return ((TileEntityKeycardScanner) te).getAssignedKeys();
        }
        return Collections.emptyList();
    }

    private List<PrivateKeys.Key> available() {
        ItemStack card = operatorCard();
        if (card.isEmpty()) return Collections.emptyList();

        List<PrivateKeys.Key> assigned = assigned();
        List<PrivateKeys.Key> out = new ArrayList<>();
        for (PrivateKeys.Key key : PrivateKeys.get(card)) {
            if (!hasSerial(assigned, key.serial)) out.add(key);
        }
        return out;
    }

    private static boolean hasSerial(List<PrivateKeys.Key> keys, String serial) {
        for (PrivateKeys.Key key : keys) {
            if (key.serial.equals(serial)) return true;
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

        if (inLeftList(lastMouseX, lastMouseY)) {
            scrollLeft = clampScroll(scrollLeft + (wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT), assigned().size());
        } else if (inRightList(lastMouseX, lastMouseY)) {
            scrollRight = clampScroll(scrollRight + (wheel > 0 ? -ROW_HEIGHT : ROW_HEIGHT), available().size());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            List<PrivateKeys.Key> assigned = assigned();
            int leftRow = rowAt(mouseX, mouseY, LEFT_X, scrollLeft, assigned.size());
            if (leftRow >= 0) {
                send(assigned.get(leftRow).serial, false);
                return;
            }

            List<PrivateKeys.Key> available = available();
            int rightRow = rowAt(mouseX, mouseY, RIGHT_X, scrollRight, available.size());
            if (rightRow >= 0) {
                send(available.get(rightRow).serial, true);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void send(String serial, boolean assign) {
        ModNetwork.INSTANCE.sendToServer(new MessageScannerKey(pos, serial, assign));
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

    private static int clampScroll(int scroll, int count) {
        return Math.max(0, Math.min(maxScroll(count), scroll));
    }

    private static int maxScroll(int count) {
        return Math.max(0, count * ROW_HEIGHT - LIST_HEIGHT);
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

        drawTitle();
        drawHeadings();

        List<PrivateKeys.Key> assigned = assigned();
        List<PrivateKeys.Key> available = available();
        scrollLeft = clampScroll(scrollLeft, assigned.size());
        scrollRight = clampScroll(scrollRight, available.size());

        drawList(LEFT_X, assigned, scrollLeft, mouseX, mouseY, "no keys assigned");
        drawList(RIGHT_X, available, scrollRight, mouseX, mouseY,
                operatorCard().isEmpty() ? "hold an Operator Keycard" : "no unassigned keys");
        drawScrollbar(LEFT_X + LIST_WIDTH + 2, assigned.size(), scrollLeft);
        drawScrollbar(RIGHT_X + LIST_WIDTH + 2, available.size(), scrollRight);

        drawFooter();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTitle() {
        drawString(fontRenderer, "KEYCARD SCANNER", left + 12, top + 10, COLOUR);

        int count = assigned().size();
        String label = count == 0 ? "No keys assigned"
                : count + (count == 1 ? " key assigned" : " keys assigned");
        drawString(fontRenderer, label,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(label), top + 10, MUTED);

        drawRect(left + 12, top + 22, left + PANEL_WIDTH - 12, top + 23, RULE);
    }

    private void drawHeadings() {
        drawString(fontRenderer, "ASSIGNED KEYS", left + LEFT_X, top + 29, HEADING);
        drawString(fontRenderer, "NON-ASSIGNED KEYS", left + RIGHT_X, top + 29, DIM);
        drawRect(left + 12, top + 40, left + PANEL_WIDTH - 12, top + 41, RULE);

        // Vertical divider between the two columns, like the mockup's centre seam.
        int divider = left + 140;
        drawRect(divider, top + 22, divider + 1, top + 150, 0xFF18181A);
        drawRect(divider + 1, top + 22, divider + 2, top + 150, 0xFF4A4A4E);
    }

    private void drawList(int listX, List<PrivateKeys.Key> keys, int scroll,
                          int mouseX, int mouseY, String emptyText) {
        int x = left + listX;
        int y = top + LIST_TOP;

        if (keys.isEmpty()) {
            drawCenteredString(fontRenderer, emptyText, x + LIST_WIDTH / 2, y + 42, DIM);
            return;
        }

        beginClip(x, y, LIST_WIDTH, LIST_HEIGHT);
        for (int i = 0; i < keys.size(); i++) {
            int rowY = y + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < y || rowY > y + LIST_HEIGHT) continue;

            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH
                    && mouseY >= Math.max(y, rowY) && mouseY < Math.min(y + LIST_HEIGHT, rowY + ROW_HEIGHT - 2);
            if (hovered) {
                drawRect(x, rowY, x + LIST_WIDTH, rowY + ROW_HEIGHT - 2, HOVER);
            }

            PrivateKeys.Key key = keys.get(i);
            drawString(fontRenderer, trim(key.name, LIST_WIDTH - 8), x + 4, rowY + 3,
                    hovered ? 0xFFFFFFFF : COLOUR);
            drawString(fontRenderer, key.serial, x + 4, rowY + 13, MUTED);
        }
        endClip();
    }

    private String trim(String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;
        String cut = text;
        while (!cut.isEmpty() && fontRenderer.getStringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private void drawScrollbar(int x, int count, int scroll) {
        int max = maxScroll(count);
        if (max <= 0) return;

        int y = top + LIST_TOP;
        drawRect(x, y, x + SCROLL_WIDTH, y + LIST_HEIGHT, 0xFF1B1B1E);

        int thumb = Math.max(14, LIST_HEIGHT * LIST_HEIGHT / (count * ROW_HEIGHT));
        int travel = LIST_HEIGHT - thumb;
        int offset = (int) ((long) scroll * travel / max);
        drawRect(x, y + offset, x + SCROLL_WIDTH, y + offset + thumb, 0xFFB4B4B4);
    }

    private void drawFooter() {
        drawRect(left + 12, top + 152, left + PANEL_WIDTH - 12, top + 153, RULE);

        int centre = left + PANEL_WIDTH / 2;
        drawCenteredString(fontRenderer, "Private Keys can be assigned on the left.",
                centre, top + 160, MUTED);
        drawCenteredString(fontRenderer, "Any keycard sharing one can open the door.",
                centre, top + 172, MUTED);
        drawCenteredString(fontRenderer, "Click a key to move it - ESC to close",
                centre, top + 184, DIM);
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
}
