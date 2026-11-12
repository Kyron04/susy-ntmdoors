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
import net.minecraft.util.math.BlockPos;

/**
 * Screen for naming a device when binding it to a terminal security net.
 */
@Environment(EnvType.CLIENT)
public class DeviceNameScreen extends Screen {

    public static final int PANEL_WIDTH = 220;
    public static final int PANEL_HEIGHT = 110;

    private final Hand hand;
    private final BlockPos pos;
    private final String defaultName;
    private final String typeName;
    private TextFieldWidget nameField;

    public DeviceNameScreen(Hand hand, BlockPos pos, String defaultName, String typeName) {
        super(Text.literal("Name Device: " + typeName));
        this.hand = hand;
        this.pos = pos;
        this.defaultName = defaultName == null ? "" : defaultName;
        this.typeName = typeName == null ? "Device" : typeName;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        nameField = new TextFieldWidget(textRenderer, left + 14, top + 38, 192, 20, Text.literal("Device Name"));
        nameField.setMaxLength(48);
        nameField.setText(defaultName);
        addSelectableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Bind"), b -> submit())
                .dimensions(left + 14, top + 68, 92, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(left + 114, top + 68, 92, 20).build());
    }

    private void submit() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = defaultName.isEmpty() ? typeName : defaultName;
        }
        ModMessages.sendTerminalBind(hand, pos, name);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / NumPad Enter
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

        // Terminal frame
        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFFC8C8C8);
        context.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + 26, 0xFF2A2B2E);
        context.fill(left + 4, top + 28, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF1B1B1E);

        context.drawText(textRenderer, title, left + 10, top + 10, 0xFFFF8A00, false);

        nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
