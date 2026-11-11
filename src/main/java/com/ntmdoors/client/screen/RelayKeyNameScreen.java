package com.ntmdoors.client.screen;

import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Screen for naming a blank Relay Key before creating its permanent random serial.
 */
@Environment(EnvType.CLIENT)
public class RelayKeyNameScreen extends Screen {

    public static final int PANEL_WIDTH = 180;
    public static final int PANEL_HEIGHT = 100;

    private final Hand hand;
    private TextFieldWidget nameField;

    public RelayKeyNameScreen(Hand hand) {
        super(Text.literal("Name Relay Key"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        nameField = new TextFieldWidget(textRenderer, left + 14, top + 34, 152, 20, Text.literal("Key Name"));
        nameField.setMaxLength(32);
        nameField.setText("Sector 1");
        addSelectableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Create"), b -> submit())
                .dimensions(left + 14, top + 64, 72, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(left + 94, top + 64, 72, 20).build());
    }

    private void submit() {
        String name = nameField.getText().trim();
        if (!name.isEmpty()) {
            ModMessages.sendRelayKeyName(name, hand);
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            submit();
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
        context.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + 24, 0xFF2A2B2E);
        context.fill(left + 4, top + 26, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF1B1B1E);

        context.drawText(textRenderer, title, left + 10, top + 9, 0xFFE8E8E8, false);

        nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
