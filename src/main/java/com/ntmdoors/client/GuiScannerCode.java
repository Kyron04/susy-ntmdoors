package com.ntmdoors.client;

import com.ntmdoors.network.MessageScannerCode;
import com.ntmdoors.network.MessageScannerCodeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Security code screen for a scanner that is already bound to doors. The first Operator Keycard
 * visit chooses the code; later visits must repeat it.
 */
@SideOnly(Side.CLIENT)
public class GuiScannerCode extends GuiCodePad {

    private final BlockPos pos;

    public GuiScannerCode(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    protected int codeLength() {
        return TileEntityKeycardScanner.CODE_LENGTH;
    }

    @Override
    protected String heading() {
        return "Please input security code";
    }

    @Override
    protected String prompt() {
        return "Enter your security code";
    }

    @Override
    protected void sendCode(String code) {
        ModNetwork.INSTANCE.sendToServer(new MessageScannerCode(pos, code));
    }

    @Override
    protected boolean isSuccess(int result) {
        return result == MessageScannerCodeResult.ACCEPTED
                || result == MessageScannerCodeResult.SET;
    }

    @Override
    protected void finishResult(boolean success) {
        // A correct code goes on to the key assignment screen; a failure just closes.
        mc.displayGuiScreen(success ? new GuiScannerKeys(pos) : null);
    }

    /** Called by the result packet. */
    public void onResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        showResult(isSuccess(result));
    }
}
