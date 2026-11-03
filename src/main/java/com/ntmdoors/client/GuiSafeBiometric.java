package com.ntmdoors.client;

import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.tile.TileEntityBiometricSafe;
import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.util.Biometrics;
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
public class GuiSafeBiometric extends GuiPanelScreen {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 150;

    private static final int MUTED = 0xFFAFAFAF;
    private static final int CYAN = 0xFF22DCCA;
    private static final int CYAN_DIM = 0xFF147A70;
    private static final int RED = 0xFFFF5A5A;

    /** If no result arrives, close instead of leaving a dead screen open. */
    private static final int TIMEOUT_TICKS = 100;

    private final BlockPos pos;

    private int openTicks;

    public GuiSafeBiometric(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected int panelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int panelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    protected int successHoldTicks() {
        // The server opens the container on the success path; keep the card until it arrives.
        return 80;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tickResult()) return;
        if (++openTicks > TIMEOUT_TICKS) mc.displayGuiScreen(null);
    }

    private TileEntitySafe safe() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntitySafe ? (TileEntitySafe) te : null;
    }

    /** Called by the result packet. */
    public void onSafeResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        showResultCard(result == MessageSafeResult.ACCEPTED
                || result == MessageSafeResult.SET);
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
        if (isShowingResult()) {
            drawResult(isResultSuccess());
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

        int infoY = top + 84;
        if (safe instanceof TileEntityBiometricSafe) {
            TileEntityBiometricSafe biometric = (TileEntityBiometricSafe) safe;
            if (biometric.hasOwner()) {
                drawCenteredString(fontRenderer, Biometrics.label(biometric.getOwnerName()),
                        left + PANEL_WIDTH / 2, infoY, MUTED);
                infoY += 12;
            }
        }
        drawCenteredString(fontRenderer, "Hold your Biometric Operator",
                left + PANEL_WIDTH / 2, infoY, MUTED);
        drawCenteredString(fontRenderer, "Keycard to scan",
                left + PANEL_WIDTH / 2, infoY + 11, MUTED);
        drawCenteredString(fontRenderer, "ESC to cancel", left + PANEL_WIDTH / 2,
                top + PANEL_HEIGHT - 16, 0xFF6A6A6A);
    }
}
