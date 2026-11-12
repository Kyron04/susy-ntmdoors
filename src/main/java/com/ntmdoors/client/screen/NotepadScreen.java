package com.ntmdoors.client.screen;

import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Fabric 1.20.1 Client Screen for Multi-Document Notepad editing.
 * Enforces 16 documents maximum and 8,192 characters per document limit.
 */
@Environment(EnvType.CLIENT)
public class NotepadScreen extends Screen {

    public static final int MAX_DOCUMENTS = 16;
    public static final int MAX_TEXT_LENGTH = 8192;

    private final BlockPos deskPos;
    private final List<NotepadDocument> documents = new ArrayList<>();
    private int currentDocId;
    private String currentText = "";
    private int cursorPosition = 0;

    private TextFieldWidget titleField;
    private ButtonWidget newNoteButton;
    private ButtonWidget deleteNoteButton;
    private ButtonWidget saveButton;
    private ButtonWidget closeButton;

    public NotepadScreen(BlockPos deskPos, int initialDocId, List<NotepadDocument> initialDocs) {
        super(Text.literal("Terminal Notepad"));
        this.deskPos = deskPos;
        this.currentDocId = initialDocId;
        if (initialDocs != null) {
            this.documents.addAll(initialDocs);
        }
        loadSelectedDocument();
    }

    private void loadSelectedDocument() {
        NotepadDocument current = getDoc(currentDocId);
        if (current != null) {
            this.currentText = current.getText();
        } else if (!documents.isEmpty()) {
            this.currentDocId = documents.get(0).getId();
            this.currentText = documents.get(0).getText();
        } else {
            this.currentDocId = -1;
            this.currentText = "";
        }
        this.cursorPosition = Math.min(cursorPosition, currentText.length());
    }

    private NotepadDocument getDoc(int id) {
        for (NotepadDocument doc : documents) {
            if (doc.getId() == id) return doc;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = width / 2 - 190;
        int topY = height / 2 - 110;

        // Title Field
        this.titleField = new TextFieldWidget(textRenderer, leftX + 110, topY + 10, 180, 16, Text.literal("Title"));
        this.titleField.setMaxLength(48);
        NotepadDocument cur = getDoc(currentDocId);
        if (cur != null) {
            this.titleField.setText(cur.getName());
        }
        addSelectableChild(this.titleField);

        // Sidebar Buttons
        this.newNoteButton = addDrawableChild(ButtonWidget.builder(Text.literal("+ New Note"), b -> {
            if (documents.size() < MAX_DOCUMENTS) {
                ModMessages.sendNotepadAction(deskPos, (byte) 1, 0, ""); // CREATE
            }
        }).dimensions(leftX + 10, topY + 10, 90, 16).build());

        this.deleteNoteButton = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> {
            if (currentDocId >= 0) {
                ModMessages.sendNotepadAction(deskPos, (byte) 4, currentDocId, ""); // DELETE
            }
        }).dimensions(leftX + 10, topY + 195, 90, 16).build());

        // Editor Buttons
        this.saveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
            if (currentDocId >= 0) {
                String newName = titleField.getText().trim();
                if (!newName.isEmpty()) {
                    ModMessages.sendNotepadAction(deskPos, (byte) 3, currentDocId, newName); // RENAME
                }
                ModMessages.sendNotepadAction(deskPos, (byte) 2, currentDocId, currentText); // SAVE
            }
        }).dimensions(leftX + 300, topY + 10, 40, 16).build());

        this.closeButton = addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(leftX + 345, topY + 10, 40, 16).build());
    }

    public void updateDocuments(List<NotepadDocument> newDocs, int focusId) {
        this.documents.clear();
        if (newDocs != null) {
            this.documents.addAll(newDocs);
        }
        this.currentDocId = focusId;
        loadSelectedDocument();
        if (titleField != null) {
            NotepadDocument cur = getDoc(currentDocId);
            if (cur != null) titleField.setText(cur.getName());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftX = width / 2 - 190;
        int topY = height / 2 - 110;

        // Check document list click
        if (mouseX >= leftX + 10 && mouseX <= leftX + 100) {
            int startY = topY + 32;
            for (int i = 0; i < documents.size(); i++) {
                int itemY = startY + i * 14;
                if (mouseY >= itemY && mouseY < itemY + 14) {
                    currentDocId = documents.get(i).getId();
                    loadSelectedDocument();
                    if (titleField != null) titleField.setText(documents.get(i).getName());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (titleField != null && titleField.isFocused()) {
            return titleField.charTyped(chr, modifiers);
        }
        if (currentDocId >= 0 && chr >= 32 && chr != 127 && currentText.length() < MAX_TEXT_LENGTH) {
            currentText = currentText.substring(0, cursorPosition) + chr + currentText.substring(cursorPosition);
            cursorPosition++;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (titleField != null && titleField.isFocused()) {
            return titleField.keyPressed(keyCode, scanCode, modifiers);
        }

        if (currentDocId >= 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursorPosition > 0) {
                currentText = currentText.substring(0, cursorPosition - 1) + currentText.substring(cursorPosition);
                cursorPosition--;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && cursorPosition < currentText.length()) {
                currentText = currentText.substring(0, cursorPosition) + currentText.substring(cursorPosition + 1);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
                cursorPosition--;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT && cursorPosition < currentText.length()) {
                cursorPosition++;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                cursorPosition = 0;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                cursorPosition = currentText.length();
                return true;
            } else if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && currentText.length() < MAX_TEXT_LENGTH) {
                currentText = currentText.substring(0, cursorPosition) + "\n" + currentText.substring(cursorPosition);
                cursorPosition++;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                // Clipboard Paste
                if (client != null && client.keyboard != null) {
                    String clip = client.keyboard.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        int remaining = MAX_TEXT_LENGTH - currentText.length();
                        if (remaining > 0) {
                            String insert = clip.length() > remaining ? clip.substring(0, remaining) : clip;
                            currentText = currentText.substring(0, cursorPosition) + insert + currentText.substring(cursorPosition);
                            cursorPosition += insert.length();
                        }
                    }
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int leftX = width / 2 - 190;
        int topY = height / 2 - 110;
        int totalWidth = 380;
        int totalHeight = 220;

        // Background Metal Casing
        context.fill(leftX, topY, leftX + totalWidth, topY + totalHeight, 0xFF18181A);
        context.fill(leftX + 2, topY + 2, leftX + totalWidth - 2, topY + totalHeight - 2, 0xFF35363A);

        // Sidebar Panel
        context.fill(leftX + 6, topY + 30, leftX + 104, topY + 190, 0xFF0B0B0D);
        for (int i = 0; i < documents.size(); i++) {
            NotepadDocument doc = documents.get(i);
            int itemY = topY + 32 + i * 14;
            boolean selected = (doc.getId() == currentDocId);
            if (selected) {
                context.fill(leftX + 7, itemY - 1, leftX + 103, itemY + 13, 0xFF7A3D00);
            }
            context.drawText(textRenderer, textRenderer.trimToWidth(doc.getName(), 90),
                    leftX + 10, itemY + 2, selected ? 0xFFFFB454 : 0xFFA0A0A0, false);
        }

        // Editor Area Panel
        context.fill(leftX + 108, topY + 30, leftX + totalWidth - 6, topY + 190, 0xFF050505);

        // Character counter
        String counter = currentText.length() + " / " + MAX_TEXT_LENGTH;
        context.drawText(textRenderer, counter, leftX + 112, topY + 195, 0xFF888888, false);

        // Text display & cursor
        int textX = leftX + 114;
        int textY = topY + 36;
        String[] lines = currentText.split("\n", -1);
        int lineOffset = 0;
        for (int i = 0; i < lines.length && (textY + i * 11) < (topY + 180); i++) {
            String line = lines[i];
            context.drawText(textRenderer, line, textX, textY + i * 11, 0xFFFF8A00, false);

            if (cursorPosition >= lineOffset && cursorPosition <= lineOffset + line.length()) {
                int col = cursorPosition - lineOffset;
                String beforeCursor = line.substring(0, col);
                int cx = textX + textRenderer.getWidth(beforeCursor);
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    context.fill(cx, textY + i * 11 - 1, cx + 1, textY + i * 11 + 9, 0xFFFFB454);
                }
            }
            lineOffset += line.length() + 1; // +1 for newline
        }

        if (titleField != null) titleField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
