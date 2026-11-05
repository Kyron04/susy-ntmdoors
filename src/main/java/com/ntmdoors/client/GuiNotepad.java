package com.ntmdoors.client;

import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.network.MessageMonitorDisplay;
import com.ntmdoors.network.MessageNotepadAction;
import com.ntmdoors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
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
 * Shared notepad for a monitor desk. The left column slides in and out; notes are stored on the
 * desk so every player using that terminal sees the same documents.
 */
@SideOnly(Side.CLIENT)
public class GuiNotepad extends GuiScreen {

    private enum Modal {
        NONE, DISCARD, RENAME, DELETE
    }

    private static final int ORANGE = 0xFFFF8A00;
    private static final int ORANGE_LIGHT = 0xFFFFB454;
    private static final int ORANGE_DIM = 0xFF7A3D00;
    private static final int TEXT = 0xFFFFD9A8;
    private static final int MUTED = 0xFF9A6A3A;
    private static final int PAPER = 0xFFE9E4D4;
    private static final int PAPER_DARK = 0xFFD5CEBA;
    private static final int INK = 0xFF242018;
    private static final int INK_MUTED = 0xFF706653;
    private static final int TITLE_BG = 0xFF0C0C0C;
    private static final int CASING = 0xFF141414;
    private static final int CASING_DARK = 0xFF080808;
    private static final int FIELD_BG = 0xFF020202;
    private static final int ROW_BG = 0xFF080808;
    private static final int ROW_HOVER = 0xFF241200;

    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_HEIGHT = 24;
    private static final int CLOSE_SIZE = 12;
    private static final int EXIT_BUTTON_WIDTH = 40;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;

    private final BlockPos origin;
    private final int screenIndex;
    private final boolean hasPassword;
    private final boolean passwordSkipped;
    private final boolean passive;

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;
    private int sidebarWidth;
    private int pageLeft;
    private int pageTop;
    private int pageRight;
    private int pageBottom;
    private int textX;
    private int textY;
    private int textWidth;
    private int visibleLines;

    private boolean sidebarOpen = true;
    private float sidebarProgress = 1F;
    private int docScroll;
    private int editorScroll;
    private int ticks;
    private boolean requested;
    private final GuiKeyRepeat repeat = new GuiKeyRepeat();
    private boolean repeating;

    private List<NotepadDocument> documents = new ArrayList<>();
    private int selectedId = -1;
    private String savedText = "";
    private final StringBuilder text = new StringBuilder();
    private List<Line> cachedLines;
    private String cachedLayoutText;
    private int cachedLayoutWidth = -1;
    private int cursor;
    private boolean dirty;
    private boolean saving;
    private boolean loading = true;
    private String notice = "";

    private Modal modal = Modal.NONE;
    private final StringBuilder modalInput = new StringBuilder();
    private int modalDocId = -1;
    private int pendingDocId = -1;
    private boolean closeToMenu;
    private boolean closeAfterSave;
    private boolean exiting;
    private int afterSaveDocId = -1;

    public GuiNotepad(BlockPos origin, int screenIndex, boolean hasPassword,
                      boolean passwordSkipped) {
        this(origin, screenIndex, hasPassword, passwordSkipped, false);
    }

    private GuiNotepad(BlockPos origin, int screenIndex, boolean hasPassword,
                       boolean passwordSkipped, boolean passive) {
        this.origin = origin;
        this.screenIndex = screenIndex;
        this.hasPassword = hasPassword;
        this.passwordSkipped = passwordSkipped;
        this.passive = passive;
    }

    /** A detached notepad page rendered onto a physical monitor screen. */
    public static GuiNotepad forDisplay(BlockPos origin, int screenIndex) {
        return new GuiNotepad(origin, screenIndex, true, true, true);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (!passive) {
            // The editor handles repeats itself; window-system repeat events are ignored below.
            repeat.clear();
        }
        if (passive) {
            guiWidth = width;
            guiHeight = height;
            guiLeft = 0;
            guiTop = 0;
            sidebarOpen = true;
            sidebarProgress = 1F;
        } else {
            int maxWidth = Math.max(1, width - 8);
            int maxHeight = Math.max(1, height - 8);
            guiWidth = Math.min(maxWidth, Math.max(220, width * 9 / 10));
            guiHeight = Math.min(maxHeight, Math.max(160, height * 9 / 10));
            guiLeft = (width - guiWidth) / 2;
            guiTop = (height - guiHeight) / 2;
        }
        sidebarWidth = Math.max(110, Math.min(170, guiWidth * 34 / 100));
        updateLayout();
        if (!passive && !requested) {
            requested = true;
            send(MessageNotepadAction.LIST, -1, "");
            publishNotepadDisplay();
        }
    }

    @Override
    public void onGuiClosed() {
        if (!passive) {
            repeat.clear();
        }
        super.onGuiClosed();
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
                || (typedChar >= 32 && typedChar != 127)) {
            repeat.press(keyCode, typedChar);
        }
    }

    private void updateLayout() {
        int offset = (int) (sidebarWidth * (1F - sidebarProgress));
        int sidebarRight = guiLeft + sidebarWidth - offset;
        pageLeft = Math.max(guiLeft + 8, sidebarRight + 10);
        pageRight = guiLeft + guiWidth - 10;
        pageTop = guiTop + TITLE_HEIGHT + 8;
        pageBottom = guiTop + guiHeight - (passive ? 8 : 34);
        textX = pageLeft + 10;
        textY = pageTop + 22;
        textWidth = Math.max(40, pageRight - textX - 10);
        visibleLines = Math.max(1, (pageBottom - textY - 8) / LINE_HEIGHT);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ticks++;
        if (passive) return;
        updateRepeat();
        float target = sidebarOpen ? 1F : 0F;
        if (Math.abs(sidebarProgress - target) > 0.02F) {
            sidebarProgress += (target - sidebarProgress) * 0.35F;
            updateLayout();
        } else if (sidebarProgress != target) {
            sidebarProgress = target;
            updateLayout();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!passive) {
            drawDefaultBackground();
            drawRect(guiLeft - 2, guiTop - 2, guiLeft + guiWidth + 2,
                    guiTop + guiHeight + 2, 0xFF000000);
        }
        drawRect(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, CASING);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + guiWidth - 2,
                guiTop + guiHeight - 2, CASING_DARK);
        drawRect(guiLeft + 4, guiTop + 4, guiLeft + guiWidth - 4,
                guiTop + TITLE_HEIGHT, TITLE_BG);
        drawRect(guiLeft + 4, guiTop + TITLE_HEIGHT - 1,
                guiLeft + guiWidth - 4, guiTop + TITLE_HEIGHT, ORANGE);
        drawString(fontRenderer, "NOTEPAD", guiLeft + 12, guiTop + 8, ORANGE_LIGHT);
        NotepadDocument selected = selectedDocument();
        String status = saving ? "SAVING" : dirty ? "UNSAVED" : "READY";
        int statusRight = passive ? guiLeft + guiWidth - 12
                : guiLeft + guiWidth - 66;
        drawString(fontRenderer, status, statusRight
                - fontRenderer.getStringWidth(status), guiTop + 8, MUTED);

        drawSidebar(mouseX, mouseY);
        drawPage(mouseX, mouseY);
        if (modal != Modal.NONE) drawModal(mouseX, mouseY);

        if (!passive) {
            drawButton("EXIT", exitButtonX(), exitButtonY(), EXIT_BUTTON_WIDTH, 12,
                    isHovering(mouseX, mouseY, exitButtonX(), exitButtonY(),
                            EXIT_BUTTON_WIDTH, 12));

            String close = "X";
            int cx = guiLeft + guiWidth - 16;
            int cy = guiTop + 6;
            boolean closeHover = isHovering(mouseX, mouseY, cx, cy, CLOSE_SIZE, CLOSE_SIZE);
            drawRect(cx, cy, cx + CLOSE_SIZE, cy + CLOSE_SIZE,
                    closeHover ? ROW_HOVER : ROW_BG);
            drawRect(cx, cy, cx + CLOSE_SIZE, cy + 1, closeHover ? ORANGE : ORANGE_DIM);
            drawRect(cx, cy + CLOSE_SIZE - 1, cx + CLOSE_SIZE, cy + CLOSE_SIZE,
                    closeHover ? ORANGE : ORANGE_DIM);
            drawRect(cx, cy, cx + 1, cy + CLOSE_SIZE, closeHover ? ORANGE : ORANGE_DIM);
            drawRect(cx + CLOSE_SIZE - 1, cy, cx + CLOSE_SIZE, cy + CLOSE_SIZE,
                    closeHover ? ORANGE : ORANGE_DIM);
            drawCenteredString(fontRenderer, close, cx + CLOSE_SIZE / 2, cy + 2,
                    closeHover ? ORANGE_LIGHT : ORANGE);

            int toggleX = sidebarOpen ? guiLeft + sidebarWidth - (int) (sidebarWidth
                    * (1F - sidebarProgress)) - 14 : guiLeft + 6;
            int toggleY = guiTop + TITLE_HEIGHT + 5;
            drawRect(toggleX, toggleY, toggleX + 10, toggleY + 16, ROW_BG);
            drawCenteredString(fontRenderer, sidebarOpen ? "<" : ">",
                    toggleX + 5, toggleY + 4, ORANGE);
        }

        if (!notice.isEmpty()) {
            drawString(fontRenderer, fontRenderer.trimStringToWidth(notice, pageRight - pageLeft - 20),
                    pageLeft, guiTop + guiHeight - 40, ORANGE);
        }
    }

    private void drawSidebar(int mouseX, int mouseY) {
        int offset = (int) (sidebarWidth * (1F - sidebarProgress));
        int left = guiLeft + 4 - offset;
        int right = guiLeft + sidebarWidth - offset;
        if (right <= guiLeft + 4) return;
        drawRect(left, guiTop + TITLE_HEIGHT + 4, right,
                guiTop + guiHeight - 6, 0xFF060606);
        drawRect(left, guiTop + TITLE_HEIGHT + 4, right,
                guiTop + TITLE_HEIGHT + 5, ORANGE_DIM);
        drawString(fontRenderer, "DOCUMENTS", left + 8, guiTop + TITLE_HEIGHT + 10, ORANGE);

        int listTop;
        if (passive) {
            listTop = guiTop + TITLE_HEIGHT + 24;
        } else {
            int createY = guiTop + TITLE_HEIGHT + 24;
            int createRight = right - 6;
            boolean createHover = isHovering(mouseX, mouseY, left + 6, createY,
                    createRight - left - 12, 18);
            drawRect(left + 6, createY, createRight, createY + 18,
                    createHover ? ROW_HOVER : ROW_BG);
            drawRect(left + 6, createY, createRight, createY + 1,
                    createHover ? ORANGE : ORANGE_DIM);
            drawRect(left + 6, createY + 17, createRight, createY + 18, ORANGE_DIM);
            drawCenteredString(fontRenderer, "Create document",
                    (left + 6 + createRight) / 2, createY + 5,
                    createHover ? ORANGE_LIGHT : TEXT);
            listTop = createY + 26;
        }

        int listBottom = guiTop + guiHeight - (passive ? 8 : 46);
        int visible = Math.max(1, (listBottom - listTop) / (ROW_HEIGHT + ROW_GAP));
        int maxScroll = Math.max(0, documents.size() - visible);
        if (docScroll > maxScroll) docScroll = maxScroll;
        if (documents.isEmpty()) {
            drawString(fontRenderer, "No documents", left + 12, listTop + 6, MUTED);
        }
        for (int i = 0; i < visible; i++) {
            int index = docScroll + i;
            if (index >= documents.size()) break;
            NotepadDocument document = documents.get(index);
            int y = listTop + i * (ROW_HEIGHT + ROW_GAP);
            boolean selected = document.id == selectedId;
            boolean hover = !passive && isHovering(mouseX, mouseY, left + 6, y,
                    right - left - 12, ROW_HEIGHT);
            drawRect(left + 6, y, right - 6, y + ROW_HEIGHT,
                    selected ? 0xFF2A1500 : hover ? ROW_HOVER : ROW_BG);
            if (selected) drawRect(left + 6, y, left + 8, y + ROW_HEIGHT, ORANGE);
            String name = fontRenderer.trimStringToWidth(document.getName(),
                    right - left - 26);
            drawString(fontRenderer, name, left + 12, y + 6,
                    selected || hover ? ORANGE_LIGHT : TEXT);
        }
        if (maxScroll > 0) {
            int barX = right - 5;
            drawRect(barX, listTop, barX + 2, listBottom, 0xFF050505);
            int barHeight = Math.max(10, (listBottom - listTop) * visible
                    / Math.max(visible + maxScroll, 1));
            int barY = listTop + (listBottom - listTop - barHeight) * docScroll
                    / Math.max(1, maxScroll);
            drawRect(barX, barY, barX + 2, barY + barHeight, ORANGE);
        }

        if (!passive && selectedDocument() != null) {
            int buttonY = guiTop + guiHeight - 38;
            int half = Math.max(40, (right - left - 18) / 2);
            drawButton("Rename", left + 6, buttonY, half, 16,
                    isHovering(mouseX, mouseY, left + 6, buttonY, half, 16));
            drawDangerButton("Delete", left + 10 + half, buttonY,
                    Math.max(34, right - left - 16 - half), 16,
                    isHovering(mouseX, mouseY, left + 10 + half, buttonY,
                            Math.max(34, right - left - 16 - half), 16));
        }
    }

    private void drawPage(int mouseX, int mouseY) {
        drawRect(pageLeft, pageTop, pageRight, pageBottom, PAPER_DARK);
        drawRect(pageLeft + 2, pageTop + 2, pageRight - 2, pageBottom - 2, PAPER);
        drawRect(pageLeft + 2, pageTop + 2, pageRight - 2, pageTop + 3, 0xFFB7AD93);

        NotepadDocument selected = selectedDocument();
        String title = selected == null ? "No document selected" : selected.getName();
        drawString(fontRenderer, fontRenderer.trimStringToWidth(title, pageRight - pageLeft - 24),
                pageLeft + 10, pageTop + 7, selected == null ? INK_MUTED : INK);
        drawRect(pageLeft + 10, pageTop + 18, pageRight - 10, pageTop + 19, 0xFFB7AD93);

        if (loading) {
            drawCenteredString(fontRenderer, "Loading notes...",
                    (pageLeft + pageRight) / 2, (pageTop + pageBottom) / 2, INK_MUTED);
        } else if (selected == null) {
            drawCenteredString(fontRenderer, passive
                            ? "No documents on this terminal."
                            : "Create a document to start writing.",
                    (pageLeft + pageRight) / 2, (pageTop + pageBottom) / 2 - 4, INK_MUTED);
        } else {
            drawEditor();
        }

        if (!passive) {
            int saveWidth = 70;
            int saveX = pageRight - saveWidth;
            int saveY = guiTop + guiHeight - 26;
            drawButton("Save", saveX, saveY, saveWidth, 18,
                    isHovering(mouseX, mouseY, saveX, saveY, saveWidth, 18));
        }
    }

    private int exitButtonX() {
        return guiLeft + guiWidth - 60;
    }

    private int exitButtonY() {
        return guiTop + 6;
    }

    private void drawEditor() {
        List<Line> lines = layoutLines();
        if (cursor < 0) cursor = 0;
        if (cursor > text.length()) cursor = text.length();
        int cursorLine = cursorLine(lines, cursor);
        if (cursorLine < editorScroll) editorScroll = cursorLine;
        if (cursorLine >= editorScroll + visibleLines) {
            editorScroll = cursorLine - visibleLines + 1;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        editorScroll = Math.max(0, Math.min(editorScroll, maxScroll));

        for (int i = 0; i < visibleLines; i++) {
            int index = editorScroll + i;
            if (index >= lines.size()) break;
            Line line = lines.get(index);
            String shown = text.substring(line.start, line.end);
            int y = textY + i * LINE_HEIGHT;
            drawString(fontRenderer, shown, textX, y, INK);
            if (!passive && index == cursorLine && (ticks / 10) % 2 == 0) {
                int prefixEnd = Math.max(line.start, Math.min(cursor, line.end));
                int cursorX = textX + fontRenderer.getStringWidth(
                        text.substring(line.start, prefixEnd));
                drawRect(cursorX, y - 1, cursorX + 1, y + LINE_HEIGHT - 1, INK);
            }
        }
        if (maxScroll > 0) {
            int barX = pageRight - 7;
            drawRect(barX, textY, barX + 2, textY + visibleLines * LINE_HEIGHT,
                    0xFFC9C0A9);
            int barHeight = Math.max(10, visibleLines * LINE_HEIGHT * visibleLines
                    / Math.max(1, lines.size()));
            int barY = textY + (visibleLines * LINE_HEIGHT - barHeight) * editorScroll
                    / Math.max(1, maxScroll);
            drawRect(barX, barY, barX + 2, barY + barHeight, 0xFF8C7A52);
        }
    }

    private void drawModal(int mouseX, int mouseY) {
        drawRect(0, 0, width, height, 0x99000000);
        int boxWidth = 280;
        int boxHeight = modal == Modal.DELETE ? 100 : modal == Modal.RENAME ? 110 : 120;
        int left = (width - boxWidth) / 2;
        int top = (height - boxHeight) / 2;
        drawRect(left - 2, top - 2, left + boxWidth + 2, top + boxHeight + 2, ORANGE_DIM);
        drawRect(left, top, left + boxWidth, top + boxHeight, 0xFF111111);

        if (modal == Modal.DISCARD) {
            drawCenteredString(fontRenderer, "Unsaved changes",
                    width / 2, top + 12, ORANGE_LIGHT);
            drawCenteredString(fontRenderer, "Save before closing this document?",
                    width / 2, top + 28, MUTED);
            int y = top + 62;
            drawButton("Save", left + 18, y, 76, 20,
                    isHovering(mouseX, mouseY, left + 18, y, 76, 20));
            drawButton("Discard", left + 102, y, 76, 20,
                    isHovering(mouseX, mouseY, left + 102, y, 76, 20));
            drawButton("Cancel", left + 186, y, 76, 20,
                    isHovering(mouseX, mouseY, left + 186, y, 76, 20));
        } else if (modal == Modal.RENAME) {
            drawCenteredString(fontRenderer, "Rename document",
                    width / 2, top + 12, ORANGE_LIGHT);
            int fieldY = top + 36;
            drawRect(left + 14, fieldY, left + boxWidth - 14, fieldY + 18, FIELD_BG);
            drawRect(left + 14, fieldY, left + boxWidth - 14, fieldY + 19, ORANGE_DIM);
            String shown = fontRenderer.trimStringToWidth(modalInput.toString(),
                    boxWidth - 40);
            drawString(fontRenderer, shown, left + 20, fieldY + 5, TEXT);
            int cursorX = left + 20 + fontRenderer.getStringWidth(shown);
            if ((ticks / 10) % 2 == 0) drawRect(cursorX, fieldY + 4, cursorX + 1,
                    fieldY + 15, ORANGE_LIGHT);
            int y = top + 70;
            drawButton("Save", left + 48, y, 80, 20,
                    isHovering(mouseX, mouseY, left + 48, y, 80, 20));
            drawButton("Cancel", left + 152, y, 80, 20,
                    isHovering(mouseX, mouseY, left + 152, y, 80, 20));
        } else if (modal == Modal.DELETE) {
            drawCenteredString(fontRenderer, "Delete document?",
                    width / 2, top + 14, ORANGE_LIGHT);
            drawCenteredString(fontRenderer, "This cannot be undone.",
                    width / 2, top + 30, MUTED);
            int y = top + 58;
            drawButton("Cancel", left + 48, y, 80, 20,
                    isHovering(mouseX, mouseY, left + 48, y, 80, 20));
            drawDangerButton("Delete", left + 152, y, 80, 20,
                    isHovering(mouseX, mouseY, left + 152, y, 80, 20));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        repeat.clear();
        if (mouseButton != 0) return;
        if (modal != Modal.NONE) {
            handleModalClick(mouseX, mouseY);
            return;
        }
        if (isHovering(mouseX, mouseY, guiLeft + guiWidth - 16, guiTop + 6,
                CLOSE_SIZE, CLOSE_SIZE)) {
            requestClose(true);
            return;
        }
        if (isHovering(mouseX, mouseY, exitButtonX(), exitButtonY(),
                EXIT_BUTTON_WIDTH, 12)) {
            requestExit();
            return;
        }
        int toggleX = sidebarOpen ? guiLeft + sidebarWidth - (int) (sidebarWidth
                * (1F - sidebarProgress)) - 14 : guiLeft + 6;
        if (isHovering(mouseX, mouseY, toggleX, guiTop + TITLE_HEIGHT + 5, 10, 16)) {
            sidebarOpen = !sidebarOpen;
            return;
        }

        int offset = (int) (sidebarWidth * (1F - sidebarProgress));
        int left = guiLeft + 4 - offset;
        int right = guiLeft + sidebarWidth - offset;
        if (sidebarProgress > 0.05F && mouseX >= left && mouseX <= right
                && mouseY >= guiTop + TITLE_HEIGHT + 4 && mouseY < guiTop + guiHeight - 6) {
            int createY = guiTop + TITLE_HEIGHT + 24;
            if (isHovering(mouseX, mouseY, left + 6, createY, right - left - 12, 18)) {
                if (dirty) {
                    notice = "Save before creating another document";
                    return;
                }
                send(MessageNotepadAction.CREATE, -1, "");
                notice = "Creating document...";
                return;
            }
            int listTop = createY + 26;
            int listBottom = guiTop + guiHeight - 46;
            int visible = Math.max(1, (listBottom - listTop) / (ROW_HEIGHT + ROW_GAP));
            for (int i = 0; i < visible; i++) {
                int index = docScroll + i;
                if (index >= documents.size()) break;
                int y = listTop + i * (ROW_HEIGHT + ROW_GAP);
                if (isHovering(mouseX, mouseY, left + 6, y, right - left - 12, ROW_HEIGHT)) {
                    selectDocument(documents.get(index).id);
                    return;
                }
            }
            if (selectedDocument() != null) {
                int buttonY = guiTop + guiHeight - 38;
                int half = Math.max(40, (right - left - 18) / 2);
                if (isHovering(mouseX, mouseY, left + 6, buttonY, half, 16)) {
                    if (dirty) {
                        notice = "Save before renaming";
                        return;
                    }
                    startRename();
                    return;
                }
                if (isHovering(mouseX, mouseY, left + 10 + half, buttonY,
                        Math.max(34, right - left - 16 - half), 16)) {
                    if (dirty) {
                        notice = "Save before deleting";
                        return;
                    }
                    modal = Modal.DELETE;
                    modalDocId = selectedId;
                    return;
                }
            }
            return;
        }

        int saveWidth = 70;
        int saveX = pageRight - saveWidth;
        int saveY = guiTop + guiHeight - 26;
        if (isHovering(mouseX, mouseY, saveX, saveY, saveWidth, 18)) {
            save(false);
            return;
        }
        if (selectedDocument() != null && isHovering(mouseX, mouseY, pageLeft, textY,
                pageRight - pageLeft, pageBottom - textY)) {
            placeCursor(mouseX, mouseY);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleModalClick(int mouseX, int mouseY) {
        int boxWidth = 280;
        int boxHeight = modal == Modal.DELETE ? 100 : modal == Modal.RENAME ? 110 : 120;
        int left = (width - boxWidth) / 2;
        int top = (height - boxHeight) / 2;
        if (modal == Modal.DISCARD) {
            int y = top + 62;
            if (isHovering(mouseX, mouseY, left + 18, y, 76, 20)) {
                int target = pendingDocId;
                pendingDocId = -1;
                modal = Modal.NONE;
                boolean close = closeToMenu || exiting;
                exiting = false;
                if (target >= 0) {
                    afterSaveDocId = target;
                    save(false);
                } else {
                    save(close);
                }
                return;
            }
            if (isHovering(mouseX, mouseY, left + 102, y, 76, 20)) {
                modal = Modal.NONE;
                int target = pendingDocId;
                pendingDocId = -1;
                exiting = false;
                if (target >= 0) openDocument(target);
                else closeInternal();
                return;
            }
            if (isHovering(mouseX, mouseY, left + 186, y, 76, 20)) {
                modal = Modal.NONE;
                pendingDocId = -1;
                exiting = false;
                closeToMenu = false;
            }
        } else if (modal == Modal.RENAME) {
            int y = top + 70;
            if (isHovering(mouseX, mouseY, left + 48, y, 80, 20)) {
                String name = NotepadDocument.cleanName(modalInput.toString());
                modal = Modal.NONE;
                send(MessageNotepadAction.RENAME, modalDocId, name);
            } else if (isHovering(mouseX, mouseY, left + 152, y, 80, 20)) {
                modal = Modal.NONE;
            }
        } else if (modal == Modal.DELETE) {
            int y = top + 58;
            if (isHovering(mouseX, mouseY, left + 48, y, 80, 20)) {
                modal = Modal.NONE;
            } else if (isHovering(mouseX, mouseY, left + 152, y, 80, 20)) {
                modal = Modal.NONE;
                send(MessageNotepadAction.DELETE, modalDocId, "");
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!repeating && Keyboard.isRepeatEvent()) return;
        if (modal != Modal.NONE) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                repeat.clear();
                modal = Modal.NONE;
                exiting = false;
                pendingDocId = -1;
                return;
            }
            if (modal == Modal.RENAME) {
                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    repeat.clear();
                    String name = NotepadDocument.cleanName(modalInput.toString());
                    modal = Modal.NONE;
                    send(MessageNotepadAction.RENAME, modalDocId, name);
                    return;
                }
                if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                    if (modalInput.length() > 0) {
                        modalInput.deleteCharAt(modalInput.length() - 1);
                    }
                    rememberRepeat(typedChar, keyCode);
                    return;
                }
                if (GuiScreen.isCtrlKeyDown()) {
                    if (keyCode == Keyboard.KEY_A) {
                        modalInput.setLength(0);
                        return;
                    }
                    if (keyCode == Keyboard.KEY_V) {
                        String clip = GuiScreen.getClipboardString();
                        if (clip != null) {
                            for (int i = 0; i < clip.length()
                                    && modalInput.length() < NotepadDocument.MAX_NAME_LENGTH; i++) {
                                char c = clip.charAt(i);
                                if (c >= 32 && c != 127) modalInput.append(c);
                            }
                        }
                    }
                    return;
                }
                if (typedChar >= 32 && typedChar != 127
                        && modalInput.length() < NotepadDocument.MAX_NAME_LENGTH) {
                    modalInput.append(typedChar);
                    rememberRepeat(typedChar, keyCode);
                }
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            requestClose(true);
            return;
        }
        if (selectedDocument() == null) {
            if (keyCode == Keyboard.KEY_TAB) {
                sidebarOpen = !sidebarOpen;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            insert("\n");
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (cursor > 0) {
                text.deleteCharAt(cursor - 1);
                cursor--;
                markDirty();
            }
            rememberRepeat(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (cursor < text.length()) {
                text.deleteCharAt(cursor);
                markDirty();
            }
            rememberRepeat(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            if (cursor > 0) cursor--;
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            if (cursor < text.length()) cursor++;
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            cursor = 0;
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            cursor = text.length();
            return;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            moveLine(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return;
        }
        if (GuiScreen.isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_V) pasteIntoText();
            return;
        }
        if (typedChar >= 32 && typedChar != 127 && text.length() < NotepadDocument.MAX_TEXT_LENGTH) {
            insert(String.valueOf(typedChar));
            rememberRepeat(typedChar, keyCode);
        }
    }

    private void insert(String value) {
        if (text.length() + value.length() > NotepadDocument.MAX_TEXT_LENGTH) return;
        text.insert(cursor, value);
        cursor += value.length();
        markDirty();
    }

    private void pasteIntoText() {
        String clip = GuiScreen.getClipboardString();
        if (clip == null) return;
        int room = NotepadDocument.MAX_TEXT_LENGTH - text.length();
        if (room <= 0) return;
        if (clip.length() > room) clip = clip.substring(0, room);
        text.insert(cursor, clip);
        cursor += clip.length();
        markDirty();
    }

    private void moveLine(int delta) {
        List<Line> lines = layoutLines();
        int current = cursorLine(lines, cursor);
        int target = Math.max(0, Math.min(lines.size() - 1, current + delta));
        if (target == current) return;
        Line from = lines.get(current);
        int column = cursor - from.start;
        Line to = lines.get(target);
        cursor = Math.min(to.end, to.start + Math.max(0, column));
    }

    private void markDirty() {
        dirty = !savedText.equals(text.toString());
        notice = "";
    }

    private void placeCursor(int mouseX, int mouseY) {
        List<Line> lines = layoutLines();
        int lineIndex = (mouseY - textY) / LINE_HEIGHT + editorScroll;
        if (lineIndex < 0 || lineIndex >= lines.size()) return;
        Line line = lines.get(lineIndex);
        int best = line.start;
        int x = textX;
        for (int i = line.start; i < line.end; i++) {
            int next = x + fontRenderer.getCharWidth(text.charAt(i));
            if (mouseX < next) {
                best = i;
                break;
            }
            best = i + 1;
            x = next;
        }
        cursor = best;
    }

    private void selectDocument(int id) {
        if (id == selectedId) return;
        if (dirty) {
            exiting = false;
            pendingDocId = id;
            closeToMenu = false;
            modal = Modal.DISCARD;
            return;
        }
        openDocument(id);
        publishNotepadDisplay();
    }

    private void openDocument(int id) {
        NotepadDocument document = findDocument(id);
        if (document == null) return;
        selectedId = id;
        savedText = document.getText();
        text.setLength(0);
        text.append(savedText);
        cursor = text.length();
        editorScroll = 0;
        dirty = false;
        notice = "";
        ensureDocumentVisible(id);
    }

    private void ensureDocumentVisible(int id) {
        int index = -1;
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).id == id) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        int listTop = guiTop + TITLE_HEIGHT + (passive ? 24 : 50);
        int listBottom = guiTop + guiHeight - (passive ? 8 : 46);
        int visible = Math.max(1, (listBottom - listTop) / (ROW_HEIGHT + ROW_GAP));
        if (index < docScroll) docScroll = index;
        if (index >= docScroll + visible) docScroll = index - visible + 1;
    }

    private void startRename() {
        NotepadDocument document = selectedDocument();
        if (document == null) return;
        modal = Modal.RENAME;
        modalDocId = document.id;
        modalInput.setLength(0);
        modalInput.append(document.getName());
    }

    private void save(boolean closeAfter) {
        if (selectedId < 0 || saving) return;
        saving = true;
        closeAfterSave = closeAfter;
        notice = "Saving...";
        send(MessageNotepadAction.SAVE, selectedId, text.toString());
    }

    private void requestClose(boolean toMenu) {
        exiting = false;
        if (dirty) {
            closeToMenu = toMenu;
            pendingDocId = -1;
            modal = Modal.DISCARD;
            return;
        }
        closeToMenu = toMenu;
        closeInternal();
    }

    /** Leaves the whole terminal, keeping this document on the physical screen. */
    private void requestExit() {
        if (dirty) {
            exiting = true;
            closeToMenu = false;
            pendingDocId = -1;
            modal = Modal.DISCARD;
            return;
        }
        exiting = false;
        closeToMenu = false;
        closeInternal();
    }

    private void closeInternal() {
        if (closeToMenu) {
            Minecraft.getMinecraft().displayGuiScreen(GuiMonitor.reopened(
                    origin, hasPassword, passwordSkipped, screenIndex));
        } else {
            publishNotepadDisplay();
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    /** Tells the desk to keep the current note visible on this physical screen. */
    private void publishNotepadDisplay() {
        if (passive) return;
        ModNetwork.INSTANCE.sendToServer(new MessageMonitorDisplay(origin, screenIndex,
                false, MonitorDisplay.STAGE_NOTEPAD, -1, selectedId,
                false, 0, 0, 0));
    }

    private void send(int action, int documentId, String value) {
        ModNetwork.INSTANCE.sendToServer(new MessageNotepadAction(
                origin, action, documentId, value));
    }

    /** Called by the client proxy when the server sends the shared notepad state. */
    public void handleData(BlockPos pos, int focusId, List<NotepadDocument> newDocuments) {
        if (pos == null || !pos.equals(origin)) return;
        documents = new ArrayList<>(newDocuments == null
                ? Collections.emptyList() : newDocuments);
        loading = false;
        if (selectedId >= 0 && findDocument(selectedId) == null) selectedId = -1;
        if (focusId >= 0) {
            openDocument(focusId);
        } else if (selectedDocument() == null) {
            if (!documents.isEmpty()) openDocument(documents.get(0).id);
        } else if (!dirty) {
            NotepadDocument selected = selectedDocument();
            String serverText = selected.getText();
            if (!serverText.equals(savedText)) {
                savedText = serverText;
                text.setLength(0);
                text.append(serverText);
                cursor = text.length();
            }
        }
        boolean closing = false;
        if (saving) {
            saving = false;
            dirty = false;
            savedText = text.toString();
            notice = "Saved";
            if (afterSaveDocId >= 0) {
                int target = afterSaveDocId;
                afterSaveDocId = -1;
                openDocument(target);
            }
            if (closeAfterSave) {
                closeAfterSave = false;
                closing = true;
                closeInternal();
            }
        }
        if (!closing) publishNotepadDisplay();
    }

    private NotepadDocument selectedDocument() {
        return selectedId < 0 ? null : findDocument(selectedId);
    }

    private NotepadDocument findDocument(int id) {
        for (NotepadDocument document : documents) {
            if (document.id == id) return document;
        }
        return null;
    }

    private List<Line> layoutLines() {
        String current = text.toString();
        if (cachedLines != null && cachedLayoutWidth == textWidth
                && current.equals(cachedLayoutText)) {
            return cachedLines;
        }
        List<Line> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                addWrapped(start, i, lines);
                start = i + 1;
            }
        }
        if (lines.isEmpty()) lines.add(new Line(0, 0));
        cachedLines = lines;
        cachedLayoutText = current;
        cachedLayoutWidth = textWidth;
        return lines;
    }

    private void addWrapped(int start, int end, List<Line> lines) {
        if (start >= end) {
            lines.add(new Line(start, end));
            return;
        }
        int from = start;
        while (from < end) {
            int fit = from;
            int lastSpace = -1;
            while (fit < end) {
                char c = text.charAt(fit);
                if (c == ' ') lastSpace = fit;
                if (fontRenderer.getStringWidth(text.substring(from, fit + 1)) > textWidth) {
                    break;
                }
                fit++;
            }
            if (fit == from) fit = from + 1;
            int breakAt = fit;
            if (fit < end && lastSpace > from) breakAt = lastSpace + 1;
            lines.add(new Line(from, breakAt));
            from = breakAt;
        }
    }

    private int cursorLine(List<Line> lines, int position) {
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (position >= line.start && position <= line.end) return i;
        }
        return Math.max(0, lines.size() - 1);
    }

    // ------------------------------------------------------------ passive display

    /** Keeps the detached page sized to the physical screen texture. */
    public void ensureDisplaySize(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        if (this.width != width || this.height != height
                || this.fontRenderer != mc.fontRenderer) {
            setWorldAndResolution(mc, width, height);
        }
    }

    public void setDisplayTicks(int ticks) {
        this.ticks = ticks;
    }

    /** Feeds the desk snapshot into the detached page shown on a physical screen. */
    public void setDisplayData(List<NotepadDocument> docs, int requestedId) {
        if (!passive) return;
        List<NotepadDocument> newDocuments = docs == null
                ? new ArrayList<>() : docs;
        NotepadDocument target = null;
        for (NotepadDocument document : newDocuments) {
            if (document.id == requestedId) {
                target = document;
                break;
            }
        }
        if (target == null && !newDocuments.isEmpty()) target = newDocuments.get(0);
        if (displayDataMatches(newDocuments, target)) return;

        documents = new ArrayList<>(newDocuments);
        loading = false;
        saving = false;
        dirty = false;
        sidebarOpen = true;
        sidebarProgress = 1F;
        if (target == null) {
            selectedId = -1;
            savedText = "";
            text.setLength(0);
            cursor = 0;
            editorScroll = 0;
            docScroll = 0;
        } else {
            selectedId = target.id;
            savedText = target.getText();
            text.setLength(0);
            text.append(savedText);
            cursor = 0;
            editorScroll = 0;
            ensureDocumentVisible(target.id);
        }
        updateLayout();
    }

    private boolean displayDataMatches(List<NotepadDocument> docs, NotepadDocument target) {
        if (loading) return false;
        int targetId = target == null ? -1 : target.id;
        String targetText = target == null ? "" : target.getText();
        NotepadDocument current = selectedDocument();
        String currentText = current == null ? "" : current.getText();
        if (targetId != selectedId || !targetText.equals(currentText)) return false;
        if (docs.size() != documents.size()) return false;
        for (int i = 0; i < docs.size(); i++) {
            NotepadDocument a = docs.get(i);
            NotepadDocument b = documents.get(i);
            if (a.id != b.id || !a.getName().equals(b.getName())) return false;
        }
        return true;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || modal != Modal.NONE) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (sidebarProgress > 0.05F && mouseX < guiLeft + sidebarWidth * sidebarProgress) {
            int visible = Math.max(1, (guiTop + guiHeight - 46
                    - (guiTop + TITLE_HEIGHT + 50)) / (ROW_HEIGHT + ROW_GAP));
            int max = Math.max(0, documents.size() - visible);
            docScroll = Math.max(0, Math.min(max, docScroll + (wheel > 0 ? -1 : 1)));
        } else {
            int max = Math.max(0, layoutLines().size() - visibleLines);
            editorScroll = Math.max(0, Math.min(max, editorScroll + (wheel > 0 ? -1 : 1)));
        }
    }

    private static final class Line {
        private final int start;
        private final int end;

        private Line(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private void drawButton(String label, int x, int y, int width, int height, boolean hover) {
        drawRect(x, y, x + width, y + height, hover ? ROW_HOVER : ROW_BG);
        int border = hover ? ORANGE : ORANGE_DIM;
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        drawCenteredString(fontRenderer, label, x + width / 2, y + (height - 8) / 2,
                hover ? ORANGE_LIGHT : TEXT);
    }

    private void drawDangerButton(String label, int x, int y, int width, int height,
                                  boolean hover) {
        drawRect(x, y, x + width, y + height, hover ? 0xFF2A0804 : ROW_BG);
        int border = hover ? 0xFFFF4A2E : 0xFF7A2A18;
        drawRect(x, y, x + width, y + 1, border);
        drawRect(x, y + height - 1, x + width, y + height, border);
        drawRect(x, y, x + 1, y + height, border);
        drawRect(x + width - 1, y, x + width, y + height, border);
        drawCenteredString(fontRenderer, label, x + width / 2,
                y + (height - 8) / 2, hover ? 0xFFFF7A5E : TEXT);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
