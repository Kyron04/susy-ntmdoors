// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.network.MessageDocumentsFolderPages;
import com.ntmdoors.network.ModNetwork;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Twenty-page editor for the Documents Folder. It follows the vanilla book layout in spirit - one
 * page of wrapped text, typed straight into the stack - but it is drawn as an open manila folder
 * using the same tan, grey and dark red palette as {@code textures/items/documents_folder.png}.
 *
 * <p>There is no signing step. Every close, whether through the Close button or Escape, writes the
 * pages to the local stack and sends the whole list to the server, so a folder always reopens where
 * it was left. Enter is a normal newline; a second Enter leaves the blank line between paragraphs,
 * and long lines word-wrap inside the sheet.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiDocumentsFolder extends GuiScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 212;
    private static final float GUI_SCALE = 1.5F;

    private static final int PAPER_X = 14;
    private static final int PAPER_Y = 22;
    private static final int PAPER_W = 206;
    private static final int PAPER_H = 154;
    private static final int PAPER_BOTTOM = PAPER_Y + PAPER_H;

    private static final int NAME_Y = PAPER_Y + 6;
    private static final int PAGE_Y = PAPER_Y + 17;
    private static final int RULE_Y = PAPER_Y + 25;

    private static final int TEXT_X = 24;
    private static final int TEXT_Y = 52;
    private static final int TEXT_W = 186;
    private static final int TEXT_BOTTOM = 166;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = (TEXT_BOTTOM - TEXT_Y) / LINE_HEIGHT;

    private static final int BOX_X = TEXT_X - 4;
    private static final int BOX_Y = TEXT_Y - 4;
    private static final int BOX_W = TEXT_W + 8;
    private static final int BOX_H = TEXT_BOTTOM - TEXT_Y + 8;

    private static final int TABS_Y = 178;
    private static final int TAB_W = 10;
    private static final int TAB_H = 13;
    private static final int TAB_GAP = 1;
    private static final int TABS_X = (PANEL_WIDTH - (ItemDocumentsFolder.PAGE_COUNT * TAB_W
            + (ItemDocumentsFolder.PAGE_COUNT - 1) * TAB_GAP)) / 2;

    private static final int BAR_Y = 192;
    private static final int BUTTON_Y = 196;

    // Light palette sampled from documents_folder.png: tan board, pale sheet, near-black ink, red tab.
    private int FOLDER = 0xFFA8A680;
    private int FOLDER_DARK = 0xFF8F8D6B;
    private int FOLDER_LIGHT = 0xFFBDBB9B;
    private int FOLDER_EDGE = 0xFF7E7C5E;
    private int PAPER = 0xFFC9C6AA;
    private int PAPER_LIGHT = 0xFFE0DDC0;
    private int PAPER_EDGE_LIGHT = 0xFFD4D1B5;
    private int PAPER_EDGE_DARK = 0xFFB8B598;
    private int INK = 0xFF1C1C1C;
    private int MUTED = 0xFF6F6D54;
    private int RED = 0xFF852E2E;
    private int RED_LIGHT = 0xFF914040;
    private int GREY = 0xFFABABAB;
    private int GREY_DARK = 0xFF969696;
    private int PLACEHOLDER = 0xFF8A8A8A;
    private int NAME_PLACEHOLDER = 0xFF8F8D6B;
    private int SELECTION = 0xFFB8B598;
    private int TAB_BORDER = 0xFF6F6D54;
    private int TAB_TEXT = 0xFFF0E8CC;
    private int BUTTON_BORDER = 0xFF6F6D54;
    private int BOX_BG = 0xFFFFFFFF;
    private int BOX_BORDER = 0xFF1C1C1C;

    // Dark palette: warm near-black folder, muted red accents and a blue-grey writing field.
    private static final int DARK_FOLDER = 0xFF2B2A26;
    private static final int DARK_FOLDER_DARK = 0xFF1E1D1A;
    private static final int DARK_FOLDER_LIGHT = 0xFF45433D;
    private static final int DARK_FOLDER_EDGE = 0xFF1E1D1A;
    private static final int DARK_PAPER = 0xFF3A3832;
    private static final int DARK_PAPER_LIGHT = 0xFF45433D;
    private static final int DARK_PAPER_EDGE_LIGHT = 0xFF4A4840;
    private static final int DARK_PAPER_EDGE_DARK = 0xFF2E2D2A;
    private static final int DARK_INK = 0xFFF2F2F2;
    private static final int DARK_MUTED = 0xFFB8B3A7;
    private static final int DARK_RED = 0xFFB05A5A;
    private static final int DARK_RED_LIGHT = 0xFFC46D6D;
    private static final int DARK_GREY = 0xFF55524A;
    private static final int DARK_GREY_DARK = 0xFF3E3B35;
    private static final int DARK_PLACEHOLDER = 0xFFB8B3A7;
    private static final int DARK_NAME_PLACEHOLDER = 0xFFB8B3A7;
    private static final int DARK_SELECTION = 0xFF5A5850;
    private static final int DARK_TAB_BORDER = 0xFF55524A;
    private static final int DARK_TAB_TEXT = 0xFFF2F2F2;
    private static final int DARK_BUTTON_BORDER = 0xFF55524A;
    private static final int DARK_BOX_BG = 0xFF3E434B;
    private static final int DARK_BOX_BORDER = 0xFF6E737B;

    private static final int TOGGLE_W = 48;
    private static final int TOGGLE_H = 12;
    private static final int TOGGLE_X = PANEL_WIDTH - TOGGLE_W - 4;
    private static final int TOGGLE_Y = 2;

    private final EnumHand hand;
    private final String[] pages = new String[ItemDocumentsFolder.PAGE_COUNT];
    private boolean darkMode;

    private String folderName = "";
    private boolean renaming;
    private String renameText = "";
    private int renameCaret;
    private boolean renameSelectAll;

    private int page;
    private int caret;
    private int desiredX;
    private int caretLineHint = -1;
    private int cursorCounter;
    private final GuiKeyRepeat repeat = new GuiKeyRepeat();
    private boolean repeating;
    private float guiScale = 1F;
    private int screenLeft;
    private int screenTop;
    private int left;
    private int top;

    public GuiDocumentsFolder(EnumHand hand, ItemStack stack) {
        this.hand = hand;
        List<String> saved = ItemDocumentsFolder.getPages(stack);
        for (int i = 0; i < pages.length; i++) {
            pages[i] = i < saved.size() ? saved.get(i) : "";
        }
        this.folderName = ItemDocumentsFolder.getCustomName(stack);
        this.page = ItemDocumentsFolder.getLastPage(stack);
        this.caret = pages[page].length();
        this.darkMode = ItemDocumentsFolder.isDarkMode(stack);
        applyPalette();
    }

    @Override
    public void initGui() {
        super.initGui();
        // Scale the whole screen by 1.5x, but never past the available window; the drawing code
        // stays in its original 240x212 space and the matrix places it on screen.
        guiScale = Math.max(0.5F, Math.min(GUI_SCALE,
                Math.min((width - 8F) / PANEL_WIDTH, (height - 8F) / PANEL_HEIGHT)));
        screenLeft = (width - Math.round(PANEL_WIDTH * guiScale)) / 2;
        screenTop = (height - Math.round(PANEL_HEIGHT * guiScale)) / 2;
        left = 0;
        top = 0;
        // The editor handles repeats itself; window-system repeat events are ignored below.
        repeat.clear();
        desiredX = caretX();
    }

    @Override
    public void onGuiClosed() {
        repeat.clear();
        save();
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        cursorCounter++;
        updateRepeat();
    }

    private void updateRepeat() {
        if (!repeat.update()) return;
        repeating = true;
        try {
            keyTyped(repeat.typedChar(), repeat.keyCode());
        } catch (IOException ignored) {
            // The editor's key handler does not do I/O; keep the repeat loop alive regardless.
        } finally {
            repeating = false;
        }
    }

    private void rememberRepeat(char typedChar, int keyCode) {
        if (repeating || GuiScreen.isCtrlKeyDown()) return;
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
                || keyCode == Keyboard.KEY_TAB
                || (typedChar >= ' ' && ChatAllowedCharacters.isAllowedCharacter(typedChar))) {
            repeat.press(keyCode, typedChar);
        }
    }

    private int toLocalX(int screenX) {
        return (int) Math.floor((screenX - screenLeft) / guiScale);
    }

    private int toLocalY(int screenY) {
        return (int) Math.floor((screenY - screenTop) / guiScale);
    }

    // ------------------------------------------------------------ palette

    private void applyPalette() {
        if (darkMode) {
            FOLDER = DARK_FOLDER;
            FOLDER_DARK = DARK_FOLDER_DARK;
            FOLDER_LIGHT = DARK_FOLDER_LIGHT;
            FOLDER_EDGE = DARK_FOLDER_EDGE;
            PAPER = DARK_PAPER;
            PAPER_LIGHT = DARK_PAPER_LIGHT;
            PAPER_EDGE_LIGHT = DARK_PAPER_EDGE_LIGHT;
            PAPER_EDGE_DARK = DARK_PAPER_EDGE_DARK;
            INK = DARK_INK;
            MUTED = DARK_MUTED;
            RED = DARK_RED;
            RED_LIGHT = DARK_RED_LIGHT;
            GREY = DARK_GREY;
            GREY_DARK = DARK_GREY_DARK;
            PLACEHOLDER = DARK_PLACEHOLDER;
            NAME_PLACEHOLDER = DARK_NAME_PLACEHOLDER;
            SELECTION = DARK_SELECTION;
            TAB_BORDER = DARK_TAB_BORDER;
            TAB_TEXT = DARK_TAB_TEXT;
            BUTTON_BORDER = DARK_BUTTON_BORDER;
            BOX_BG = DARK_BOX_BG;
            BOX_BORDER = DARK_BOX_BORDER;
        } else {
            FOLDER = 0xFFA8A680;
            FOLDER_DARK = 0xFF8F8D6B;
            FOLDER_LIGHT = 0xFFBDBB9B;
            FOLDER_EDGE = 0xFF7E7C5E;
            PAPER = 0xFFC9C6AA;
            PAPER_LIGHT = 0xFFE0DDC0;
            PAPER_EDGE_LIGHT = 0xFFD4D1B5;
            PAPER_EDGE_DARK = 0xFFB8B598;
            INK = 0xFF1C1C1C;
            MUTED = 0xFF6F6D54;
            RED = 0xFF852E2E;
            RED_LIGHT = 0xFF914040;
            GREY = 0xFFABABAB;
            GREY_DARK = 0xFF969696;
            PLACEHOLDER = 0xFF8A8A8A;
            NAME_PLACEHOLDER = 0xFF8F8D6B;
            SELECTION = 0xFFB8B598;
            TAB_BORDER = 0xFF6F6D54;
            TAB_TEXT = 0xFFF0E8CC;
            BUTTON_BORDER = 0xFF6F6D54;
            BOX_BG = 0xFFFFFFFF;
            BOX_BORDER = 0xFF1C1C1C;
        }
    }

    private void toggleDarkMode() {
        darkMode = !darkMode;
        applyPalette();
        if (renaming) commitRename();
        save();
    }

    // ------------------------------------------------------------ saving

    /** Writes the pages, name and page marker locally, then sends the same data to the server. */
    private void save() {
        if (mc == null || mc.player == null) return;
        ItemStack stack = mc.player.getHeldItem(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemDocumentsFolder)) return;

        List<String> written = new ArrayList<>();
        for (String pageText : pages) written.add(pageText == null ? "" : pageText);
        List<String> clean = ItemDocumentsFolder.clean(written);
        int storedPage = clean.isEmpty() ? 0 : page;

        ItemDocumentsFolder.setCustomName(stack, folderName);
        ItemDocumentsFolder.setPages(stack, clean);
        ItemDocumentsFolder.setLastPage(stack, storedPage);
        ItemDocumentsFolder.setDarkMode(stack, darkMode);
        if (mc.getConnection() != null) {
            ModNetwork.INSTANCE.sendToServer(
                    new MessageDocumentsFolderPages(hand, storedPage, folderName, clean, darkMode));
        }
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int localX = toLocalX(mouseX);
        int localY = toLocalY(mouseY);

        GlStateManager.pushMatrix();
        GlStateManager.translate(screenLeft, screenTop, 0F);
        GlStateManager.scale(guiScale, guiScale, 1F);
        GlStateManager.color(1F, 1F, 1F, 1F);

        drawFolder();
        drawDarkModeToggle(localX, localY);
        drawPaper();
        drawPageTabs(localX, localY);
        drawBottomBar(localX, localY);
        drawHeader(localX, localY);
        drawWritingBox();
        drawText();
        drawCaret();

        super.drawScreen(localX, localY, partialTicks);
        GlStateManager.popMatrix();

        if (!renaming && inName(localX, localY)) {
            GuiContainerStyle.withTooltipState(() ->
                    drawHoveringText(Collections.singletonList("Click to rename"), mouseX, mouseY));
        }
    }

    private void drawFolder() {
        drawRect(left - 2, top + 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0x55000000);

        // The red index tab peeking above the folder body.
        drawRect(left + 8, top, left + 98, top + 17, FOLDER_DARK);
        drawRect(left + 9, top + 1, left + 97, top + 15, RED);
        drawRect(left + 10, top + 2, left + 96, top + 3, RED_LIGHT);
        GuiContainerStyle.drawFlatCenteredString(fontRenderer, "DOCUMENTS", left + 53, top + 4,
                TAB_TEXT);

        drawRect(left, top + 14, left + PANEL_WIDTH, top + PANEL_HEIGHT, FOLDER_DARK);
        drawRect(left + 1, top + 15, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, FOLDER);
        drawRect(left + 1, top + 15, left + PANEL_WIDTH - 1, top + 16, FOLDER_LIGHT);
        drawRect(left + 1, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1,
                FOLDER_EDGE);
    }

    private void drawDarkModeToggle(int mouseX, int mouseY) {
        drawButton(left + TOGGLE_X, top + TOGGLE_Y, TOGGLE_W, TOGGLE_H,
                darkMode ? "LIGHT" : "DARK", mouseX, mouseY);
    }

    private void drawPaper() {
        int paperLeft = left + PAPER_X;
        int paperTop = top + PAPER_Y;
        int paperRight = paperLeft + PAPER_W;
        int paperBottom = top + PAPER_BOTTOM;

        drawRect(paperLeft + 2, paperTop + 2, paperRight + 2, paperBottom + 2, 0x44000000);

        // The grey page stack along the right edge, as on the item sprite.
        drawRect(paperRight, paperTop + 1, paperRight + 6, paperBottom + 2, GREY);
        for (int y = paperTop + 3; y < paperBottom + 1; y += 3) {
            drawRect(paperRight, y, paperRight + 5, y + 1, GREY_DARK);
        }

        drawRect(paperLeft, paperTop, paperRight, paperBottom, PAPER);
        drawRect(paperLeft, paperTop, paperRight, paperTop + 1, PAPER_LIGHT);
        drawRect(paperLeft, paperTop, paperLeft + 1, paperBottom, PAPER_EDGE_LIGHT);
        drawRect(paperRight - 1, paperTop, paperRight, paperBottom, PAPER_EDGE_DARK);
        drawRect(paperLeft, paperBottom - 1, paperRight, paperBottom, PAPER_EDGE_DARK);
    }

    private void drawHeader(int mouseX, int mouseY) {
        int x = left + PAPER_X + 10;
        int width = PAPER_W - 20;
        int nameY = top + NAME_Y;

        if (renaming) {
            drawRect(x, nameY - 1, x + width, nameY + 10, PAPER_LIGHT);
            drawRect(x, nameY + 10, x + width, nameY + 11, RED);
            if (renameText.isEmpty()) {
                GuiContainerStyle.drawFlatString(fontRenderer, defaultName(), x + 2, nameY + 2,
                        NAME_PLACEHOLDER);
            } else {
                if (renameSelectAll) {
                    drawRect(x + 2, nameY + 1, x + 2 + fontRenderer.getStringWidth(renameText),
                            nameY + 9, SELECTION);
                }
                GuiContainerStyle.drawFlatString(fontRenderer, renameText, x + 2, nameY + 2, INK);
            }
            if (!renameSelectAll) {
                int caret = x + 2 + fontRenderer.getStringWidth(
                        renameText.substring(0, Math.min(renameCaret, renameText.length())));
                caret = Math.min(caret, x + width - 2);
                drawRect(caret, nameY + 1, caret + 1, nameY + 10, RED);
            }
        } else {
            String shown = folderName.isEmpty() ? defaultName() : folderName;
            int colour = folderName.isEmpty() ? MUTED : INK;
            if (fontRenderer.getStringWidth(shown) > width) {
                shown = fontRenderer.trimStringToWidth(shown,
                        width - fontRenderer.getStringWidth("...")) + "...";
            }
            int textX = x + width - fontRenderer.getStringWidth(shown);
            if (inName(mouseX, mouseY)) {
                drawRect(x, nameY - 1, x + width, nameY + 9, PAPER_LIGHT);
                drawRect(x, nameY + 9, x + width, nameY + 10, RED);
            }
            GuiContainerStyle.drawFlatString(fontRenderer, shown, textX, nameY, colour);
        }

        int pageY = top + PAGE_Y;
        String count = "PAGE " + (page + 1) + " / " + ItemDocumentsFolder.PAGE_COUNT;
        GuiContainerStyle.drawFlatString(fontRenderer, count, x, pageY, RED);
        if (wrap(pages[page]).size() >= MAX_LINES) {
            GuiContainerStyle.drawFlatString(fontRenderer, "PAGE FULL",
                    x + fontRenderer.getStringWidth(count) + 10, pageY, RED);
        }

        drawRect(x, top + RULE_Y, x + width, top + RULE_Y + 1, RED);
    }

    private void drawText() {
        String text = pages[page];
        if (text.isEmpty()) {
            GuiContainerStyle.drawFlatString(fontRenderer, "Click here and write",
                    left + TEXT_X, top + TEXT_Y, PLACEHOLDER);
            return;
        }

        List<Line> lines = wrap(text);
        for (int i = 0; i < lines.size() && i < MAX_LINES; i++) {
            Line line = lines.get(i);
            GuiContainerStyle.drawFlatString(fontRenderer, text.substring(line.start, line.end),
                    left + TEXT_X, top + TEXT_Y + i * LINE_HEIGHT, INK);
        }
    }

    /** White writing sheet with a one-pixel ink border, like a field on the paper. */
    private void drawWritingBox() {
        int x = left + BOX_X;
        int y = top + BOX_Y;
        int right = x + BOX_W;
        int bottom = y + BOX_H;

        drawRect(x, y, right, bottom, BOX_BG);
        drawRect(x, y, right, y + 1, BOX_BORDER);
        drawRect(x, bottom - 1, right, bottom, BOX_BORDER);
        drawRect(x, y, x + 1, bottom, BOX_BORDER);
        drawRect(right - 1, y, right, bottom, BOX_BORDER);
    }

    private void drawCaret() {
        if (cursorCounter / 6 % 2 != 0) return;
        int index = Math.min(lineIndexForCaret(), MAX_LINES - 1);
        int x = left + TEXT_X + caretX();
        int y = top + TEXT_Y + index * LINE_HEIGHT;
        drawRect(x, y - 1, x + 1, y + 9, INK);
    }

    private void drawPageTabs(int mouseX, int mouseY) {
        for (int i = 0; i < ItemDocumentsFolder.PAGE_COUNT; i++) {
            int x = left + TABS_X + i * (TAB_W + TAB_GAP);
            int y = top + TABS_Y;
            boolean current = i == page;
            boolean hovered = mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H;
            boolean written = !pages[i].isEmpty();

            drawRect(x, y, x + TAB_W, y + TAB_H, TAB_BORDER);
            int fill = current ? PAPER : hovered ? FOLDER_LIGHT : written ? FOLDER_DARK : FOLDER;
            drawRect(x + 1, y + 1, x + TAB_W - 1, y + TAB_H - 1, fill);
            if (current) {
                drawRect(x + 1, y + TAB_H - 3, x + TAB_W - 1, y + TAB_H - 1, RED);
            }

            String label = String.valueOf(i + 1);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + TAB_W / 2F, y + 3F, 0F);
            GlStateManager.scale(0.7F, 0.7F, 1F);
            int width = fontRenderer.getStringWidth(label);
            GuiContainerStyle.drawFlatString(fontRenderer, label, -width / 2, 0,
                    current ? INK : written ? TAB_TEXT : MUTED);
            GlStateManager.popMatrix();
        }
    }

    private void drawBottomBar(int mouseX, int mouseY) {
        int barTop = top + BAR_Y;
        int barBottom = top + PANEL_HEIGHT - 2;
        drawRect(left + 1, barTop, left + PANEL_WIDTH - 1, barTop + 1, FOLDER_DARK);
        drawRect(left + 1, barTop + 1, left + PANEL_WIDTH - 1, barBottom, FOLDER_LIGHT);

        drawButton(left + 8, top + BUTTON_Y, 14, 12, "<", mouseX, mouseY);
        drawButton(left + 24, top + BUTTON_Y, 14, 12, ">", mouseX, mouseY);
        drawButton(left + PANEL_WIDTH - 52, top + BUTTON_Y, 44, 12, "Close", mouseX, mouseY);

        String hint = renaming ? "Enter saves - Esc cancels" : "Esc also saves";
        GuiContainerStyle.drawFlatString(fontRenderer, hint, left + 44, top + BUTTON_Y + 2, MUTED);
    }

    private void drawButton(int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        drawRect(x, y, x + w, y + h, BUTTON_BORDER);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? PAPER : FOLDER);
        drawRect(x + 1, y + 1, x + w - 1, y + 2, hovered ? PAPER_LIGHT : FOLDER_LIGHT);
        GuiContainerStyle.drawFlatCenteredString(fontRenderer, label, x + w / 2, y + (h - 8) / 2, INK);
    }

    // ------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        repeat.clear();
        int localX = toLocalX(mouseX);
        int localY = toLocalY(mouseY);
        if (mouseButton == 0) {
            if (hit(localX, localY, left + TOGGLE_X, top + TOGGLE_Y, TOGGLE_W, TOGGLE_H)) {
                toggleDarkMode();
                return;
            }
            if (inName(localX, localY)) {
                if (!renaming) {
                    startRename();
                } else if (renameSelectAll) {
                    renameSelectAll = false;
                    renameCaret = renameText.length();
                }
                return;
            }
            if (renaming) {
                commitRename();
            }
            int tab = tabAt(localX, localY);
            if (tab >= 0) {
                switchPage(tab);
                return;
            }
            if (hit(localX, localY, left + 8, top + BUTTON_Y, 14, 12)) {
                switchPage(page - 1);
                return;
            }
            if (hit(localX, localY, left + 24, top + BUTTON_Y, 14, 12)) {
                switchPage(page + 1);
                return;
            }
            if (hit(localX, localY, left + PANEL_WIDTH - 52, top + BUTTON_Y, 44, 12)) {
                mc.displayGuiScreen(null);
                return;
            }
            if (inText(localX, localY)) {
                placeCaret(localX, localY);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (renaming) return;
        if (wheel > 0) {
            switchPage(page - 1);
        } else if (wheel < 0) {
            switchPage(page + 1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!repeating && Keyboard.isRepeatEvent()) return;
        if (renaming) {
            if (handleRenameKey(typedChar, keyCode)) {
                rememberRepeat(typedChar, keyCode);
                return;
            }
            super.keyTyped(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            moveCaret(-1);
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            moveCaret(1);
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            moveLine(-1);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            moveLine(1);
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            caretLineHint = lineIndexForCaret();
            caret = lineForCaret().start;
            desiredX = 0;
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            caretLineHint = lineIndexForCaret();
            caret = lineForCaret().end;
            desiredX = caretX();
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            backspace();
            rememberRepeat(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            delete();
            rememberRepeat(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            insert("\n");
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            insert("    ");
            rememberRepeat(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            switchPage(page - 1);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            switchPage(page + 1);
            return;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            paste();
            return;
        }
        if (typedChar >= ' ' && ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            insert(String.valueOf(typedChar));
            rememberRepeat(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    // ------------------------------------------------------------ editing

    private boolean hit(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean inText(int mouseX, int mouseY) {
        return mouseX >= left + BOX_X && mouseX < left + BOX_X + BOX_W
                && mouseY >= top + BOX_Y && mouseY < top + BOX_Y + BOX_H;
    }

    private int tabAt(int mouseX, int mouseY) {
        if (mouseY < top + TABS_Y || mouseY >= top + TABS_Y + TAB_H) return -1;
        for (int i = 0; i < ItemDocumentsFolder.PAGE_COUNT; i++) {
            int x = left + TABS_X + i * (TAB_W + TAB_GAP);
            if (mouseX >= x && mouseX < x + TAB_W) return i;
        }
        return -1;
    }

    private void switchPage(int target) {
        int next = Math.max(0, Math.min(ItemDocumentsFolder.PAGE_COUNT - 1, target));
        if (next == page) return;
        repeat.clear();
        page = next;
        caret = pages[page].length();
        caretLineHint = -1;
        desiredX = caretX();
    }

    private void moveCaret(int delta) {
        caret = Math.max(0, Math.min(pages[page].length(), caret + delta));
        caretLineHint = -1;
        desiredX = caretX();
    }

    private void moveLine(int delta) {
        List<Line> lines = wrap(pages[page]);
        int index = lineIndexForCaret() + delta;
        if (index < 0) {
            caret = 0;
            caretLineHint = 0;
            desiredX = 0;
            return;
        }
        if (index >= lines.size()) {
            caret = pages[page].length();
            caretLineHint = lines.size() - 1;
            desiredX = caretX();
            return;
        }
        caret = caretAtX(lines.get(index), desiredX);
        caretLineHint = index;
    }

    private void placeCaret(int mouseX, int mouseY) {
        List<Line> lines = wrap(pages[page]);
        int relY = mouseY - (top + TEXT_Y);
        int index = Math.max(0, Math.min(lines.size() - 1, relY / LINE_HEIGHT));
        caret = caretAtX(lines.get(index), mouseX - (left + TEXT_X));
        caretLineHint = index;
        desiredX = caretX();
    }

    /** Inserts as much of {@code text} as still fits on the page. */
    private void insert(String text) {
        for (int i = 0; i < text.length(); i++) {
            String current = pages[page];
            int at = Math.min(caret, current.length());
            String next = current.substring(0, at) + text.charAt(i) + current.substring(at);
            if (!fits(next)) {
                caretLineHint = -1;
                desiredX = caretX();
                return;
            }
            pages[page] = next;
            caret = at + 1;
        }
        caretLineHint = -1;
        desiredX = caretX();
    }

    private void backspace() {
        if (caret <= 0) return;
        String text = pages[page];
        pages[page] = text.substring(0, caret - 1) + text.substring(caret);
        caret--;
        caretLineHint = -1;
        desiredX = caretX();
    }

    private void delete() {
        String text = pages[page];
        if (caret >= text.length()) return;
        pages[page] = text.substring(0, caret) + text.substring(caret + 1);
        caretLineHint = -1;
        desiredX = caretX();
    }

    private void paste() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null || clip.isEmpty()) return;
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < clip.length(); i++) {
            char c = clip.charAt(i);
            if (c == '\r') continue;
            if (c == '\n' || ChatAllowedCharacters.isAllowedCharacter(c)) clean.append(c);
        }
        insert(clean.toString());
    }

    // ------------------------------------------------------------ folder name

    private void startRename() {
        repeat.clear();
        renaming = true;
        renameText = folderName;
        renameCaret = renameText.length();
        renameSelectAll = !renameText.isEmpty();
    }

    private void commitRename() {
        repeat.clear();
        folderName = ItemDocumentsFolder.cleanName(renameText);
        renaming = false;
        renameText = "";
        renameCaret = 0;
        renameSelectAll = false;

        // Apply immediately so the item tooltip is already correct when the screen closes.
        if (mc != null && mc.player != null) {
            ItemStack stack = mc.player.getHeldItem(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemDocumentsFolder) {
                ItemDocumentsFolder.setCustomName(stack, folderName);
            }
        }
    }

    private void cancelRename() {
        repeat.clear();
        renaming = false;
        renameText = "";
        renameCaret = 0;
        renameSelectAll = false;
    }

    private boolean handleRenameKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelRename();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            commitRename();
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (renameSelectAll) {
                renameText = "";
                renameCaret = 0;
                renameSelectAll = false;
            } else if (renameCaret > 0) {
                renameText = renameText.substring(0, renameCaret - 1) + renameText.substring(renameCaret);
                renameCaret--;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (renameSelectAll) {
                renameText = "";
                renameCaret = 0;
                renameSelectAll = false;
            } else if (renameCaret < renameText.length()) {
                renameText = renameText.substring(0, renameCaret) + renameText.substring(renameCaret + 1);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            renameSelectAll = false;
            renameCaret = Math.max(0, renameCaret - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            renameSelectAll = false;
            renameCaret = Math.min(renameText.length(), renameCaret + 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            renameSelectAll = false;
            renameCaret = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            renameSelectAll = false;
            renameCaret = renameText.length();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            renamePaste();
            return true;
        }
        if (typedChar >= ' ' && typedChar != 167 && ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            renameInsert(String.valueOf(typedChar));
            return true;
        }
        return false;
    }

    /** Inserts a single-line name, replacing a selected name on the first typed character. */
    private void renameInsert(String text) {
        if (text.isEmpty()) return;
        if (renameSelectAll) {
            renameText = "";
            renameCaret = 0;
            renameSelectAll = false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < ' ' || c == 127 || c == 167) continue;
            if (renameText.length() >= ItemDocumentsFolder.MAX_NAME_LENGTH) break;
            int at = Math.min(renameCaret, renameText.length());
            renameText = renameText.substring(0, at) + c + renameText.substring(at);
            renameCaret = at + 1;
        }
    }

    private void renamePaste() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null || clip.isEmpty()) return;
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < clip.length(); i++) {
            char c = clip.charAt(i);
            if (c == '\r' || c == '\n') continue;
            if (c >= ' ' && c != 127 && c != 167) clean.append(c);
        }
        renameInsert(clean.toString());
    }

    private String defaultName() {
        return I18n.format("item.ntmdoors.documents_folder.name");
    }

    private boolean inName(int mouseX, int mouseY) {
        return mouseX >= left + PAPER_X + 10 && mouseX < left + PAPER_X + PAPER_W - 10
                && mouseY >= top + NAME_Y - 2 && mouseY < top + NAME_Y + 10;
    }

    private boolean fits(String text) {
        return text.length() <= ItemDocumentsFolder.MAX_PAGE_LENGTH && wrap(text).size() <= MAX_LINES;
    }

    private int caretX() {
        Line line = lineForCaret();
        int end = Math.min(caret, line.end);
        if (end <= line.start) return 0;
        return fontRenderer.getStringWidth(pages[page].substring(line.start, end));
    }

    private int caretAtX(Line line, int x) {
        String text = pages[page];
        int best = line.start;
        int bestDistance = Integer.MAX_VALUE;
        int width = 0;
        for (int i = line.start; i <= line.end; i++) {
            int distance = Math.abs(width - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
            if (i < line.end) width += fontRenderer.getCharWidth(text.charAt(i));
        }
        return best;
    }

    private int lineIndexForCaret() {
        List<Line> lines = wrap(pages[page]);
        if (caretLineHint >= 0 && caretLineHint < lines.size()) {
            Line hint = lines.get(caretLineHint);
            if (caret >= hint.start && caret <= hint.end) return caretLineHint;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (caret <= lines.get(i).end) return i;
        }
        return Math.max(0, lines.size() - 1);
    }

    private Line lineForCaret() {
        List<Line> lines = wrap(pages[page]);
        return lines.get(lineIndexForCaret());
    }

    /**
     * Splits a page into visual lines. Hard newlines end a line immediately; anything wider than
     * the sheet wraps at the last space. A trailing newline leaves an empty line so the caret can
     * sit at the start of the next paragraph.
     */
    private List<Line> wrap(String text) {
        List<Line> lines = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            // A space that caused the wrap belongs to the previous line, not the next one.
            if (i > 0 && text.charAt(i) == ' ' && text.charAt(i - 1) != '\n') {
                i++;
                continue;
            }

            int start = i;
            int j = i;
            int width = 0;
            int lastSpace = -1;
            while (j < text.length()) {
                char c = text.charAt(j);
                if (c == '\n') break;
                int charWidth = fontRenderer.getCharWidth(c);
                if (width + charWidth > TEXT_W) break;
                if (c == ' ') lastSpace = j;
                width += charWidth;
                j++;
            }

            int end;
            int next;
            if (j < text.length() && text.charAt(j) == '\n') {
                end = j;
                next = j + 1;
            } else if (j < text.length() && lastSpace > start) {
                end = lastSpace;
                next = lastSpace + 1;
            } else {
                end = j;
                next = j;
            }

            lines.add(new Line(start, end));
            i = next > start ? next : start + 1;
        }

        if (text.isEmpty() || text.charAt(text.length() - 1) == '\n') {
            lines.add(new Line(text.length(), text.length()));
        }
        if (lines.isEmpty()) lines.add(new Line(0, 0));
        return lines;
    }

    private static final class Line {

        private final int start;
        private final int end;

        private Line(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
