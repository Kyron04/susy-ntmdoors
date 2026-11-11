package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 2-Page configuration screen for Keypads:
 * Page 0: Set Door Code
 * Page 1: Options (ON_FAIL, ON_SUCCESS, Reset Code, Override Door Locks, Force Close)
 */
@Environment(EnvType.CLIENT)
public class KeypadConfigScreen extends Screen {

    public static final int PANEL_WIDTH = 200;
    public static final int PANEL_HEIGHT = 160;

    private final BlockPos pos;
    private int activePage = 0;
    private TextFieldWidget codeField;

    public KeypadConfigScreen(BlockPos pos) {
        super(Text.literal("Keypad Configuration"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Set Code"), b -> { activePage = 0; rebuildWidgets(); })
                .dimensions(left + 8, top + 16, 88, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Options"), b -> { activePage = 1; rebuildWidgets(); })
                .dimensions(left + 104, top + 16, 88, 16).build());

        if (activePage == 0) {
            codeField = new TextFieldWidget(textRenderer, left + 20, top + 50, 160, 20, Text.literal("Code"));
            codeField.setMaxLength(6);
            addSelectableChild(codeField);

            addDrawableChild(ButtonWidget.builder(Text.literal("Save Code"), b -> {
                String val = codeField.getText().trim();
                if (!val.isEmpty()) {
                    ModMessages.sendKeypadCode(pos, val);
                    close();
                }
            }).dimensions(left + 50, top + 80, 100, 20).build());
        } else {
            KeypadBlockEntity keypad = getKeypad();
            boolean onFail = keypad != null && keypad.getRedstoneBinding().isOnFail();
            boolean onSuccess = keypad != null && keypad.getRedstoneBinding().isOnSuccess();

            addDrawableChild(ButtonWidget.builder(Text.literal("On Fail: " + (onFail ? "ON" : "OFF")), b -> {
                ModMessages.sendKeypadOption(pos, (byte) 0, !onFail);
            }).dimensions(left + 14, top + 42, 80, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("On Success: " + (onSuccess ? "ON" : "OFF")), b -> {
                ModMessages.sendKeypadOption(pos, (byte) 1, !onSuccess);
            }).dimensions(left + 106, top + 42, 80, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Override Locks"), b -> {
                ModMessages.sendKeypadOption(pos, (byte) 3, true);
            }).dimensions(left + 14, top + 68, 80, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Force Close"), b -> {
                ModMessages.sendKeypadOption(pos, (byte) 4, true);
            }).dimensions(left + 106, top + 68, 80, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Reset Cluster Codes"), b -> {
                ModMessages.sendKeypadOption(pos, (byte) 2, true);
                close();
            }).dimensions(left + 30, top + 98, 140, 20).build());
        }
    }

    private KeypadBlockEntity getKeypad() {
        if (client != null && client.world != null) {
            BlockEntity be = client.world.getBlockEntity(pos);
            if (be instanceof KeypadBlockEntity k) return k;
        }
        return null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFFC8C8C8);
        context.fill(left + 4, top + 34, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF222226);

        context.drawText(textRenderer, title, left + 10, top + 6, 0xFF2A2B2E, false);

        if (activePage == 0 && codeField != null) {
            codeField.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
