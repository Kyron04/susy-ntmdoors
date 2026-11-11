package com.ntmdoors.client.screen;

import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Numeric PIN pad for entering security codes to configure keypads.
 */
@Environment(EnvType.CLIENT)
public class KeypadSecurityCodeScreen extends Screen {

    public static final int PANEL_WIDTH = 136;
    public static final int PANEL_HEIGHT = 168;

    private final BlockPos pos;
    private String code = "";

    public KeypadSecurityCodeScreen(BlockPos pos) {
        super(Text.literal("Keypad Security Code"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        int btnW = 32;
        int btnH = 20;
        int startX = left + 14;
        int startY = top + 42;

        for (int i = 0; i < 9; i++) {
            int digit = i + 1;
            int col = i % 3;
            int row = i / 3;
            addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(digit)), b -> addDigit(digit))
                    .dimensions(startX + col * 38, startY + row * 24, btnW, btnH).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> code = "")
                .dimensions(startX, startY + 72, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("0"), b -> addDigit(0))
                .dimensions(startX + 38, startY + 72, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("E"), b -> submit())
                .dimensions(startX + 76, startY + 72, btnW, btnH).build());
    }

    private void addDigit(int digit) {
        if (code.length() < 6) {
            code += digit;
        }
    }

    private void submit() {
        if (!code.isEmpty()) {
            ModMessages.sendKeypadSecurityCode(pos, code);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= 48 && keyCode <= 57) {
            addDigit(keyCode - 48);
            return true;
        } else if (keyCode >= 320 && keyCode <= 329) {
            addDigit(keyCode - 320);
            return true;
        } else if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        } else if (keyCode == 259) {
            if (!code.isEmpty()) code = code.substring(0, code.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFFC8C8C8);
        context.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + 34, 0xFF0A0A0C);

        String display = code.isEmpty() ? "---" : "*".repeat(code.length());
        context.drawCenteredTextWithShadow(textRenderer, display, left + PANEL_WIDTH / 2, top + 15, 0xFF75FF82);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
