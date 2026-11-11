package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.SafeBlockEntity;
import com.ntmdoors.network.ModMessages;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class SafeCodeScreen extends Screen {

    public static final int PANEL_WIDTH = 136;
    public static final int PANEL_HEIGHT = 168;

    private final BlockPos pos;
    private String code = "";
    private String statusMessage = "";
    private int statusColor = 0xFF55FF55;
    private int statusTicks = 0;

    public SafeCodeScreen(BlockPos pos) {
        super(Text.literal("Safe Security Code"));
        this.pos = pos;
    }

    private SafeBlockEntity safe() {
        if (client == null || client.world == null) return null;
        BlockEntity be = client.world.getBlockEntity(pos);
        return be instanceof SafeBlockEntity s ? s : null;
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

        // Digits 1-9
        for (int i = 0; i < 9; i++) {
            int digit = i + 1;
            int col = i % 3;
            int row = i / 3;
            addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(digit)), b -> addDigit(digit))
                    .dimensions(startX + col * 38, startY + row * 24, btnW, btnH).build());
        }

        // Row 4: Reset (R), 0, Enter (E)
        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> clearCode())
                .dimensions(startX, startY + 72, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("0"), b -> addDigit(0))
                .dimensions(startX + 38, startY + 72, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("E"), b -> submitCode())
                .dimensions(startX + 76, startY + 72, btnW, btnH).build());

        SafeBlockEntity s = safe();
        if (s != null && s.isLockedOut()) {
            showStatus("Safe locked out", 0xFFFF5555);
        }
    }

    private void addDigit(int digit) {
        if (code.length() < 6) {
            code += digit;
        }
    }

    private void clearCode() {
        code = "";
    }

    private void submitCode() {
        if (!code.isEmpty()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeString(code);
            ClientPlayNetworking.send(ModMessages.SAFE_CODE_ID, buf);
        }
    }

    public void onSafeResult(byte result) {
        switch (result) {
            case 0 -> showStatus("ACCESS GRANTED", 0xFF55FF55);
            case 1 -> showStatus("CODE SET", 0xFF55FF55);
            case 2 -> {
                showStatus("ACCESS DENIED", 0xFFFF5555);
                clearCode();
            }
            case 3 -> {
                showStatus("SAFE LOCKED OUT", 0xFFFF5555);
                clearCode();
            }
        }
    }

    private void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusTicks = 60;
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
            submitCode();
            return true;
        } else if (keyCode == 259) {
            if (!code.isEmpty()) code = code.substring(0, code.length() - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0 && (statusColor == 0xFF55FF55)) {
                // Closed automatically when container opens from server
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF2A2A2E);

        // Display LCD
        context.fill(left + 12, top + 10, left + PANEL_WIDTH - 12, top + 34, 0xFF08080A);
        context.fill(left + 14, top + 12, left + PANEL_WIDTH - 14, top + 32, 0xFF18181C);

        if (statusTicks > 0) {
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, left + PANEL_WIDTH / 2, top + 18, statusColor);
        } else {
            String display = code.isEmpty() ? (safe() != null && safe().hasSecurityCode() ? "ENTER CODE" : "SET PIN") : "*".repeat(code.length());
            int col = code.isEmpty() ? 0xFF888888 : 0xFFFFFFFF;
            context.drawCenteredTextWithShadow(textRenderer, display, left + PANEL_WIDTH / 2, top + 18, col);
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
