package com.ntmdoors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Remembers that a device screen was opened from a Security terminal. Escape in that device
 * screen returns to the same desk's Security list instead of dropping back to the world.
 */
@SideOnly(Side.CLIENT)
public final class MonitorReturnContext {

    @Nullable
    private static BlockPos terminal;
    private static boolean terminalHasPassword;
    private static int terminalScreenIndex;
    private static int terminalNetId = -1;
    private static String terminalNetName = "";

    public static void set(BlockPos origin, boolean hasPassword, int screenIndex,
                           int netId, String netName) {
        terminal = origin == null ? null : origin.toImmutable();
        terminalHasPassword = hasPassword;
        terminalScreenIndex = screenIndex;
        terminalNetId = netId;
        terminalNetName = netName == null ? "" : netName;
    }

    /** Returns true when the escape was consumed and the terminal was reopened. */
    public static boolean handleEscape() {
        if (terminal == null) return false;
        BlockPos origin = terminal;
        boolean hasPassword = terminalHasPassword;
        int screenIndex = terminalScreenIndex;
        int netId = terminalNetId;
        String netName = terminalNetName;
        terminal = null;
        Minecraft.getMinecraft().displayGuiScreen(
                GuiMonitor.forSecurity(origin, hasPassword, screenIndex, netId, netName));
        return true;
    }

    /** A device screen closed by something other than Escape; do not leak the return context. */
    public static void clearIfNoScreen() {
        if (terminal != null && Minecraft.getMinecraft().currentScreen == null) {
            terminal = null;
        }
    }

    public static void clear() {
        terminal = null;
    }

    private MonitorReturnContext() {
    }
}
