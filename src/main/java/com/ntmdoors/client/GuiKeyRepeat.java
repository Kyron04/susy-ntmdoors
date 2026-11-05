package com.ntmdoors.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Explicit hold-to-repeat for the mod's text editors.
 *
 * <p>LWJGL's repeat events depend on the windowing system and are also switched off by some
 * screens, so the editors track the held key themselves. The first press is handled normally;
 * after a short delay the same key is replayed at a fixed tick interval until it is released.</p>
 */
@SideOnly(Side.CLIENT)
final class GuiKeyRepeat {

    /** Roughly 400 ms before the first repeat. */
    private static final int DELAY_TICKS = 8;
    /** Ten characters per second while held. */
    private static final int INTERVAL_TICKS = 2;

    private int keyCode = -1;
    private char typedChar;
    private int ticks;

    /** Starts (or restarts) the repeat timer for one key press. */
    void press(int keyCode, char typedChar) {
        this.keyCode = keyCode;
        this.typedChar = typedChar;
        this.ticks = DELAY_TICKS;
    }

    void clear() {
        keyCode = -1;
        ticks = 0;
    }

    /** Returns true when the held key should be replayed this tick. */
    boolean update() {
        if (keyCode < 0) return false;
        if (!Keyboard.isKeyDown(keyCode)) {
            clear();
            return false;
        }
        if (ticks > 0) {
            ticks--;
            return false;
        }
        ticks = INTERVAL_TICKS;
        return true;
    }

    int keyCode() {
        return keyCode;
    }

    char typedChar() {
        return typedChar;
    }
}
