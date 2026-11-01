package com.ntmdoors.client;

import com.ntmdoors.network.MessageSafeCode;
import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Keypad screen for the Code Safe. The first accepted code sets the safe's code; later entries must
 * match it.
 */
@SideOnly(Side.CLIENT)
public class GuiSafeCode extends GuiCodePad {

    private final BlockPos pos;

    public GuiSafeCode(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (lockedOut()) showStatus("Safe locked out", RED);
    }

    @Override
    protected int codeLength() {
        return TileEntitySafe.CODE_LENGTH;
    }

    @Override
    protected String heading() {
        return codeSet() ? "Please input security code" : "Please set a safe code";
    }

    @Override
    protected String prompt() {
        return codeSet() ? "Enter your security code" : "Enter a new safe code";
    }

    @Override
    protected int successHoldTicks() {
        // The container packet replaces this screen; only close if it never arrives.
        return 80;
    }

    @Override
    protected void sendCode(String code) {
        ModNetwork.INSTANCE.sendToServer(new MessageSafeCode(pos, code));
    }

    @Override
    protected boolean isSuccess(int result) {
        return result == MessageSafeResult.ACCEPTED || result == MessageSafeResult.SET;
    }

    /** Called by the result packet. */
    public void onSafeResult(BlockPos resultPos, int result) {
        if (resultPos == null || !resultPos.equals(pos)) return;
        showResult(isSuccess(result));
    }

    private TileEntitySafe safe() {
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        return te instanceof TileEntitySafe ? (TileEntitySafe) te : null;
    }

    private boolean codeSet() {
        TileEntitySafe safe = safe();
        return safe != null && safe.hasSecurityCode();
    }

    private boolean lockedOut() {
        TileEntitySafe safe = safe();
        return safe != null && safe.isLockedOut();
    }
}
