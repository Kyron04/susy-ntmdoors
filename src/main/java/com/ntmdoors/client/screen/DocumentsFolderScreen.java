package com.ntmdoors.client.screen;

import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modern Fabric 1.20.1 Client Screen for 20-page Documents Folder.
 * Manila folder styling with tab bar, in-place title rename, word-wrapped text area, and caret editing.
 */
@Environment(EnvType.CLIENT)
public class DocumentsFolderScreen extends Screen {

    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 212;

    private static final int PAPER_X = 14;
    private static final int PAPER_Y = 22;
    private static final int PAPER_W = 212;
    private static final int PAPER_H = 152;

    private static final int TEXT_X = 22;
    private static final int TEXT_Y = 46;
    private static final int TEXT_W = 196;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = 11;

    private static final int TABS_Y = 178;
    private static final int TAB_W = 10;
    private static final int TAB_H = 12;
    private static final int TAB_GAP = 1;
    private static final int TABS_X = (PANEL_WIDTH - (ItemDocumentsFolder.PAGE_COUNT * TAB_W
            + (ItemDocumentsFolder.PAGE_COUNT - 1) * TAB_GAP)) / 2;

    // Palette: Manila folder tan, paper sheet, ink, red tab
    private static final int FOLDER = 0xFFA8A680;
    private static final int FOLDER_DARK = 0xFF8F8D6B;
    private static final int FOLDER_LIGHT = 0xFFBDBB9B;
    private static final int PAPER = 0xFFD8D5BA;
    private static final int PAPER_LIGHT = 0xFFECE9D2;
    private static final int INK = 0xFF1C1C1C;
    private static final int MUTED = 0xFF6F6D54;
    private static final int RED = 0xFF852E2E;
    private static final int RED_LIGHT = 0xFF9E3A3A;
    private static final int GREY = 0xFFABABAB;
    private static final int GREY_DARK = 0xFF969696;

    private final Hand hand;
    private final ItemStack holderStack;
    private final String[] pages = new String[ItemDocumentsFolder.PAGE_COUNT];

    private String folderName = "";
    private boolean renaming = false;
    private String renameText = "";

    private int currentPage = 0;
    private int caret = 0;
    private int cursorCounter = 0;

    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget closeButton;

    public DocumentsFolderScreen(Hand hand, ItemStack stack) {
        super(Text.literal("Documents Folder"));
        this.hand = hand;
        this.holderStack = stack;

        List<String> loaded = ItemDocumentsFolder.getPages(stack);
        for (int i = 0; i < ItemDocumentsFolder.PAGE_COUNT; i++) {
            this.pages[i] = (i < loaded.size() && loaded.get(i) != null) ? loaded.get(i) : "";
        }
        this.folderName = ItemDocumentsFolder.getCustomName(stack);
        this.currentPage = ItemDocumentsFolder.getLastPage(stack);
        this.caret = this.pages[currentPage].length();
    }

    @Override
    protected void init() {
        super.init();

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        this.prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> prevPage())
                .dimensions(left + 14, top + 194, 20, 14).build());

        this.nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> nextPage())
                .dimensions(left + PANEL_WIDTH - 34, top + 194, 20, 14).build());

        this.closeButton = addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(left + (PANEL_WIDTH - 48) / 2, top + 194, 48, 14).build());

        updateButtons();
    }

    private void updateButtons() {
        if (prevButton != null) prevButton.active = (currentPage > 0);
        if (nextButton != null) nextButton.active = (currentPage < ItemDocumentsFolder.PAGE_COUNT - 1);
    }

    private void setPage(int newPage) {
        if (newPage < 0 || newPage >= ItemDocumentsFolder.PAGE_COUNT || newPage == currentPage) return;
        this.currentPage = newPage;
        this.caret = Math.min(caret, pages[currentPage].length());
        playPageTurnSound();
        updateButtons();
    }

    private void prevPage() {
        if (currentPage > 0) setPage(currentPage - 1);
    }

    private void nextPage() {
        if (currentPage < ItemDocumentsFolder.PAGE_COUNT - 1) setPage(currentPage + 1);
    }

    private void playPageTurnSound() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public void tick() {
        super.tick();
        cursorCounter++;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) {
            nextPage();
            return true;
        } else if (amount > 0) {
            prevPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        if (button == 0) {
            // Check Tab clicks
            for (int i = 0; i < ItemDocumentsFolder.PAGE_COUNT; i++) {
                int tx = left + TABS_X + i * (TAB_W + TAB_GAP);
                int ty = top + TABS_Y;
                if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H) {
                    setPage(i);
                    return true;
                }
            }

            // Check Title click for Rename
            int titleY = top + 8;
            if (mouseX >= left + 14 && mouseX < left + PANEL_WIDTH - 14 && mouseY >= titleY && mouseY < titleY + 14) {
                renaming = true;
                renameText = folderName;
                return true;
            } else if (renaming) {
                commitRename();
            }

            // Check Paper Area click to reposition Caret
            if (mouseX >= left + TEXT_X && mouseX < left + TEXT_X + TEXT_W
                    && mouseY >= top + TEXT_Y && mouseY < top + TEXT_Y + MAX_LINES * LINE_HEIGHT) {
                int lineClicked = (int) ((mouseY - (top + TEXT_Y)) / LINE_HEIGHT);
                setCaretToClick(lineClicked, (int) (mouseX - (left + TEXT_X)));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void commitRename() {
        renaming = false;
        folderName = ItemDocumentsFolder.cleanName(renameText);
    }

    private void setCaretToClick(int lineIndex, int clickX) {
        String text = pages[currentPage];
        List<String> lines = wrapText(text, TEXT_W);
        if (lineIndex >= lines.size()) {
            caret = text.length();
            return;
        }

        int charOffset = 0;
        for (int i = 0; i < lineIndex; i++) {
            charOffset += lines.get(i).length();
            if (charOffset < text.length() && text.charAt(charOffset) == '\n') charOffset++;
            else if (charOffset < text.length() && text.charAt(charOffset) == ' ') charOffset++;
        }

        String line = lines.get(lineIndex);
        int lineChar = 0;
        int accumulatedWidth = 0;
        for (int i = 0; i < line.length(); i++) {
            int w = textRenderer.getWidth(line.substring(i, i + 1));
            if (accumulatedWidth + w / 2 >= clickX) break;
            accumulatedWidth += w;
            lineChar++;
        }

        caret = Math.min(text.length(), charOffset + lineChar);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renaming) {
            if (renameText.length() < ItemDocumentsFolder.MAX_NAME_LENGTH && chr >= ' ' && chr != 127 && chr != '§') {
                renameText += chr;
                return true;
            }
            return false;
        }

        if (chr >= ' ' && chr != 127 && chr != '§') {
            insertText(String.valueOf(chr));
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                commitRename();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!renameText.isEmpty()) renameText = renameText.substring(0, renameText.length() - 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            deleteAtCaret(-1);
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteAtCaret(1);
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            insertText("\n");
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (caret > 0) caret--;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (caret < pages[currentPage].length()) caret++;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            prevPage();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            nextPage();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            caret = 0;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            caret = pages[currentPage].length();
            return true;
        } else if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            if (client != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) insertText(clip);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void insertText(String text) {
        String cur = pages[currentPage];
        if (cur.length() >= ItemDocumentsFolder.MAX_PAGE_LENGTH) return;

        int available = ItemDocumentsFolder.MAX_PAGE_LENGTH - cur.length();
        if (text.length() > available) text = text.substring(0, available);

        caret = Math.min(Math.max(0, caret), cur.length());
        pages[currentPage] = cur.substring(0, caret) + text + cur.substring(caret);
        caret += text.length();
    }

    private void deleteAtCaret(int direction) {
        String cur = pages[currentPage];
        if (cur.isEmpty()) return;

        if (direction < 0 && caret > 0) {
            pages[currentPage] = cur.substring(0, caret - 1) + cur.substring(caret);
            caret--;
        } else if (direction > 0 && caret < cur.length()) {
            pages[currentPage] = cur.substring(0, caret) + cur.substring(caret + 1);
        }
    }

    @Override
    public void close() {
        save();
        super.close();
    }

    private void save() {
        if (renaming) commitRename();

        List<String> list = new ArrayList<>(Arrays.asList(pages));
        List<String> cleanPages = ItemDocumentsFolder.clean(list);

        // Update local stack copy
        ItemDocumentsFolder.setPages(holderStack, cleanPages);
        ItemDocumentsFolder.setCustomName(holderStack, folderName);
        ItemDocumentsFolder.setLastPage(holderStack, cleanPages.isEmpty() ? 0 : currentPage);

        // Transmit C2S sync packet
        ModMessages.sendDocumentsFolderPages(hand, currentPage, folderName, cleanPages);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        // Folder casing
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, FOLDER);
        context.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, FOLDER_LIGHT);
        context.fill(left + 1, top + 1, left + 2, top + PANEL_HEIGHT - 1, FOLDER_LIGHT);
        context.fill(left + 1, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, FOLDER_DARK);
        context.fill(left + PANEL_WIDTH - 2, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, FOLDER_DARK);

        // Paper sheet
        context.fill(left + PAPER_X, top + PAPER_Y, left + PAPER_X + PAPER_W, top + PAPER_Y + PAPER_H, PAPER_LIGHT);
        context.fill(left + PAPER_X + 1, top + PAPER_Y + 1, left + PAPER_X + PAPER_W - 1, top + PAPER_Y + PAPER_H - 1, PAPER);

        // Title Header
        String displayTitle = renaming ? (renameText + ((cursorCounter / 10) % 2 == 0 ? "_" : ""))
                : (folderName.isEmpty() ? "Documents Folder" : folderName);
        context.drawText(textRenderer, Text.literal(displayTitle), left + 18, top + 8, INK, false);

        // Page Indicator
        String pageStr = "Page " + (currentPage + 1) + " / " + ItemDocumentsFolder.PAGE_COUNT;
        context.drawText(textRenderer, Text.literal(pageStr), left + PANEL_WIDTH - 18 - textRenderer.getWidth(pageStr), top + 8, MUTED, false);

        // Text Content & Word Wrapping
        renderPageText(context, left + TEXT_X, top + TEXT_Y);

        // 20 Bottom Tab Buttons
        for (int i = 0; i < ItemDocumentsFolder.PAGE_COUNT; i++) {
            int tx = left + TABS_X + i * (TAB_W + TAB_GAP);
            int ty = top + TABS_Y;
            boolean isCur = (i == currentPage);
            context.fill(tx, ty, tx + TAB_W, ty + TAB_H, isCur ? RED : GREY);
            context.fill(tx, ty, tx + TAB_W, ty + 1, isCur ? RED_LIGHT : GREY_DARK);
            String tabNum = String.valueOf(i + 1);
            int numWidth = textRenderer.getWidth(tabNum);
            context.drawText(textRenderer, Text.literal(tabNum), tx + (TAB_W - numWidth) / 2, ty + 2, isCur ? 0xFFFFFFFF : 0xFF222222, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPageText(DrawContext context, int x, int y) {
        String text = pages[currentPage];
        List<String> lines = wrapText(text, TEXT_W);

        int charCount = 0;
        int drawnLines = Math.min(MAX_LINES, lines.size());
        boolean cursorDrawn = false;

        for (int i = 0; i < drawnLines; i++) {
            String line = lines.get(i);
            int ly = y + i * LINE_HEIGHT;
            context.drawText(textRenderer, Text.literal(line), x, ly, INK, false);

            if (!renaming && (cursorCounter / 10) % 2 == 0 && !cursorDrawn) {
                int lineEndChar = charCount + line.length();
                if (caret >= charCount && caret <= lineEndChar) {
                    int subLen = Math.max(0, Math.min(line.length(), caret - charCount));
                    int cx = x + textRenderer.getWidth(line.substring(0, subLen));
                    context.fill(cx, ly - 1, cx + 1, ly + LINE_HEIGHT - 1, INK);
                    cursorDrawn = true;
                }
            }

            charCount += line.length();
            if (charCount < text.length() && (text.charAt(charCount) == '\n' || text.charAt(charCount) == ' ')) {
                charCount++;
            }
        }

        if (!renaming && (cursorCounter / 10) % 2 == 0 && !cursorDrawn) {
            int ly = y + drawnLines * LINE_HEIGHT;
            context.fill(x, ly - 1, x + 1, ly + LINE_HEIGHT - 1, INK);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        String[] rawLines = text.split("\n", -1);
        for (String raw : rawLines) {
            if (raw.isEmpty()) {
                result.add("");
                continue;
            }

            String remaining = raw;
            while (!remaining.isEmpty()) {
                int fitChars = textRenderer.trimToWidth(remaining, maxWidth).length();
                if (fitChars >= remaining.length()) {
                    result.add(remaining);
                    break;
                }

                // Break at last space if possible
                int lastSpace = remaining.substring(0, fitChars).lastIndexOf(' ');
                if (lastSpace > 0) {
                    result.add(remaining.substring(0, lastSpace));
                    remaining = remaining.substring(lastSpace + 1);
                } else {
                    result.add(remaining.substring(0, fitChars));
                    remaining = remaining.substring(fitChars);
                }
            }
        }
        return result;
    }
}
