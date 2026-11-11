package com.ntmdoors.client.screen;

import com.ntmdoors.block.entity.BiometricSafeBlockEntity;
import com.ntmdoors.util.Biometrics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class SafeBiometricScreen extends Screen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 150;

    private static final int MUTED = 0xFFAFAFAF;
    private static final int CYAN = 0xFF22DCCA;
    private static final int CYAN_DIM = 0xFF147A70;
    private static final int RED = 0xFFFF5A5A;

    private final BlockPos pos;
    private String statusMessage = "";
    private int statusColor = CYAN;
    private int statusTicks = 0;
    private int openTicks = 0;

    public SafeBiometricScreen(BlockPos pos) {
        super(Text.literal("Biometric Safe"));
        this.pos = pos;
    }

    private BiometricSafeBlockEntity safe() {
        if (client == null || client.world == null) return null;
        BlockEntity be = client.world.getBlockEntity(pos);
        return be instanceof BiometricSafeBlockEntity s ? s : null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void onSafeResult(byte result) {
        switch (result) {
            case 0, 1 -> {
                statusMessage = "ACCESS GRANTED";
                statusColor = 0xFF55FF55;
                statusTicks = 60;
            }
            case 2 -> {
                statusMessage = "ACCESS DENIED";
                statusColor = RED;
                statusTicks = 60;
            }
            case 3 -> {
                statusMessage = "SAFE LOCKED OUT";
                statusColor = RED;
                statusTicks = 60;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
        if (++openTicks > 100 && statusTicks == 0) {
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF141416);
        context.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF2B2B2E);

        BiometricSafeBlockEntity s = safe();
        boolean locked = s != null && s.isLockedOut();

        context.drawCenteredTextWithShadow(textRenderer, "BIOMETRIC SAFE", left + PANEL_WIDTH / 2, top + 12, 0xFFF2F2F2);
        context.drawCenteredTextWithShadow(textRenderer, locked ? "Safe locked out" : "Reading card...", left + PANEL_WIDTH / 2, top + 30, locked ? RED : CYAN);

        int bx = left + (PANEL_WIDTH - 120) / 2;
        int by = top + 50;
        context.fill(bx - 1, by - 1, bx + 121, by + 21, 0xFF080808);
        context.fill(bx, by, bx + 120, by + 20, CYAN_DIM);
        context.fill(bx + 2, by + 2, bx + 118, by + 18, 0xFF061512);

        String middleText = statusTicks > 0 ? statusMessage : "SCANNING...";
        int middleColor = statusTicks > 0 ? statusColor : CYAN;
        context.drawCenteredTextWithShadow(textRenderer, middleText, left + PANEL_WIDTH / 2, by + 6, middleColor);

        int infoY = top + 84;
        if (s != null && s.hasOwner()) {
            context.drawCenteredTextWithShadow(textRenderer, Biometrics.label(s.getOwnerName()), left + PANEL_WIDTH / 2, infoY, MUTED);
            infoY += 12;
        }

        context.drawCenteredTextWithShadow(textRenderer, "Hold your Biometric Operator", left + PANEL_WIDTH / 2, infoY, MUTED);
        context.drawCenteredTextWithShadow(textRenderer, "Keycard to scan", left + PANEL_WIDTH / 2, infoY + 11, MUTED);
        context.drawCenteredTextWithShadow(textRenderer, "ESC to cancel", left + PANEL_WIDTH / 2, top + PANEL_HEIGHT - 16, 0xFF6A6A6A);

        super.render(context, mouseX, mouseY, delta);
    }
}
