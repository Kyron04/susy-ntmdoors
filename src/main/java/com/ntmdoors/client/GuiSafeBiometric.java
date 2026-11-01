package com.ntmdoors.client;

import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Short card-read screen for the Biometric Safe. It uses the same metal case and result card as the
 * code keypad, but shows the scanning state instead of keys.
 */
@SideOnly(Side.CLIENT)
public class GuiSafeBiometric extends GuiScreen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 150;

    private static final int MUTED = 0xFFAFAFAF;
    private static final int CYAN = 0xFF22DCCA;
    private static final int CYAN_DIM = 0xFF147A70;
    private static final int RED = 0xFFFF5A5A;

    /** The result holds on screen for a little over a second before the GUI closes. */
    private static final int RESULT_TICKS = 30;
    /** If no result arrives, close instead of leaving a dead screen open. */
    private static final int TIMEOUT_TICKS = 100;

    private final BlockPos pos;

    private int left;
    private int top;
    private int openTicks;
    private boolean showingResult;
    private boolean resultSuccess;
    private int resultTicks;
    /** Keeps a successful card up until the container replaces it. */
    private int holdTicks;

    public GuiSafeBiometric(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (showingResult) {
            if (resultSuccess) {
                // The server opens the container on the success path; keep the card until it arrives.
                if (--holdTicks <= 0) mc.displayGuiScreen(null);
            } else if (--resultTicks <= 0) {
                mc.displayGuiScreen(null);
            }
            return;
        }
        if (++openTicks > TIMEOUT_TICKS) mc.displayGuiScreen(null);
    }

    private TileEntitySafe safe() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntitySafe ? (TileEntitySafe) te : null;
    }

    /** Called by the result packet. */
    public void onSafeResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        showingResult = true;
        resultSuccess = result == MessageSafeResult.ACCEPTED
                || result == MessageSafeResult.SET;
        if (resultSuccess) {
            holdTicks = 80;
        } else {
            resultTicks = RESULT_TICKS;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCase();
        if (showingResult) {
            drawResult();
        } else {
            drawScanning();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScanning() {
        boolean locked = false;
        TileEntitySafe safe = safe();
        if (safe != null) locked = safe.isLockedOut();

        drawCenteredString(fontRenderer, "BIOMETRIC SAFE", left + PANEL_WIDTH / 2,
                top + 12, 0xFFF2F2F2);
        drawCenteredString(fontRenderer, locked ? "Safe locked out" : "Reading card...",
                left + PANEL_WIDTH / 2, top + 30, locked ? RED : CYAN);

        int x = left + (PANEL_WIDTH - 120) / 2;
        int y = top + 50;
        drawRect(x - 1, y - 1, x + 121, y + 21, 0xFF080808);
        drawRect(x, y, x + 120, y + 20, CYAN_DIM);
        drawRect(x + 2, y + 2, x + 118, y + 18, 0xFF061512);
        drawCenteredString(fontRenderer, "SCANNING...", left + PANEL_WIDTH / 2, y + 6, CYAN);

        drawCenteredString(fontRenderer, "Hold your Biometric Operator",
                left + PANEL_WIDTH / 2, top + 84, MUTED);
        drawCenteredString(fontRenderer, "Keycard to scan",
                left + PANEL_WIDTH / 2, top + 95, MUTED);
        drawCenteredString(fontRenderer, "ESC to cancel", left + PANEL_WIDTH / 2,
                top + PANEL_HEIGHT - 16, 0xFF6A6A6A);
    }

    private void drawResult() {
        String text = resultSuccess ? "SUCCESS!!!" : "FAILED!!!";
        int fill = resultSuccess ? 0xFF75FF82 : 0xFFFF7777;
        int glow = resultSuccess ? 0x6620FF46 : 0x66FF2525;
        int stroke = resultSuccess ? 0xFF173D1C : 0xFF4A1111;
        int cx = left + PANEL_WIDTH / 2;
        int cy = top + PANEL_HEIGHT / 2;

        drawBigText(text, cx, cy, 2.15F, 0, 0, glow);
        drawBigText(text, cx, cy, 2F, -1, 0, stroke);
        drawBigText(text, cx, cy, 2F, 1, 0, stroke);
        drawBigText(text, cx, cy, 2F, 0, -1, stroke);
        drawBigText(text, cx, cy, 2F, 0, 1, stroke);
        drawBigText(text, cx, cy, 2F, 0, 0, fill);
    }

    private void drawBigText(String text, int cx, int cy, float scale, int dx, int dy, int colour) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx + dx, cy + dy, 0F);
        GlStateManager.scale(scale, scale, 1F);
        fontRenderer.drawString(text, -fontRenderer.getStringWidth(text) / 2, -4, colour);
        GlStateManager.popMatrix();
    }

    private void drawCase() {
        drawRect(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0xFF555555);
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF777777);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFFC8C8C8);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 2, 0xFFF1F1F1);
        drawRect(left + 1, top + 1, left + 2, top + PANEL_HEIGHT - 1, 0xFFF1F1F1);
        drawRect(left + 1, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF8A8A8A);
        drawRect(left + PANEL_WIDTH - 2, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF8A8A8A);

        drawRect(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFFF1F1F1);
        drawRect(left + 5, top + 5, left + PANEL_WIDTH - 5, top + PANEL_HEIGHT - 5, 0xFF090909);
    }
}
