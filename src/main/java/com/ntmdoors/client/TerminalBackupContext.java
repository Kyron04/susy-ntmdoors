package com.ntmdoors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Remembers which monitor terminal opened the backup console, so Escape or the Back button returns
 * to that terminal's Terminal Backup page instead of dropping the player into the world.
 */
@SideOnly(Side.CLIENT)
public final class TerminalBackupContext {

    @Nullable
    private static BlockPos terminal;
    private static int terminalScreenIndex;

    public static void set(BlockPos origin, int screenIndex) {
        terminal = origin == null ? null : origin.toImmutable();
        terminalScreenIndex = screenIndex;
    }

    /** Returns true when the terminal was reopened with its current lock state. */
    public static boolean reopenTerminal(boolean hasPassword, boolean passwordSkipped) {
        if (terminal == null) return false;
        BlockPos origin = terminal;
        int screenIndex = terminalScreenIndex;
        clear();
        Minecraft.getMinecraft().displayGuiScreen(
                GuiMonitor.forBackup(origin, hasPassword, passwordSkipped, screenIndex));
        return true;
    }

    /** A console closed by something other than Back/Escape; do not leak the return context. */
    public static void clearIfNoScreen() {
        if (terminal != null && Minecraft.getMinecraft().currentScreen == null) {
            terminal = null;
        }
    }

    public static void clear() {
        terminal = null;
    }

    private TerminalBackupContext() {
    }
}
