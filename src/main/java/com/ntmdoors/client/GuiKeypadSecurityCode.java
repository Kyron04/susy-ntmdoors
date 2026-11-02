package com.ntmdoors.client;

import com.ntmdoors.network.MessageKeypadSecurityCode;
import com.ntmdoors.network.MessageKeypadSecurityCodeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Security-code gate for the keypad's Redstone Relay Key screen. The first entry sets a code;
 * later entries must repeat it before the keypad GUI opens.
 */
@SideOnly(Side.CLIENT)
public class GuiKeypadSecurityCode extends GuiCodePad {

    private final BlockPos pos;

    public GuiKeypadSecurityCode(BlockPos pos) {
        this.pos = pos;
    }

    private TileEntityDoorKeypad keypad() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntityDoorKeypad ? (TileEntityDoorKeypad) te : null;
    }

    @Override
    protected int codeLength() {
        return TileEntityDoorKeypad.CODE_LENGTH;
    }

    @Override
    protected String heading() {
        return "KEYPAD";
    }

    @Override
    protected String prompt() {
        TileEntityDoorKeypad keypad = keypad();
        return keypad != null && keypad.hasSecurityCode()
                ? "Enter your security code" : "Set a keypad security code";
    }

    @Override
    protected void sendCode(String code) {
        ModNetwork.INSTANCE.sendToServer(new MessageKeypadSecurityCode(pos, code));
    }

    @Override
    protected boolean isSuccess(int result) {
        return result == MessageKeypadSecurityCodeResult.ACCEPTED
                || result == MessageKeypadSecurityCodeResult.SET;
    }

    @Override
    protected void finishResult(boolean success) {
        mc.displayGuiScreen(success ? new GuiKeypad(pos) : null);
    }

    /** Called by the result packet. */
    public void onResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        showResult(isSuccess(result));
    }
}
