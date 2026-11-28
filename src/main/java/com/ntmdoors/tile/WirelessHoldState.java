// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.tile;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Receiver-side wireless hold shared by the Emancipation Grill and the Speaker.
 *
 * <p>A wireless input can either follow the signal, remain active for a timer after the signal
 * falls, or latch until the device is switched off manually. The timer starts when the input
 * falls, so a short sensor pulse can still sound an alarm or hold a field for longer.</p>
 */
public final class WirelessHoldState {

    public static final int MIN_ACTIVE_TICKS = 20;
    public static final int MAX_ACTIVE_TICKS = 20 * 60 * 5;
    public static final int DEFAULT_ACTIVE_TICKS = 20;
    private static final int[] PRESETS = {
            20, 40, 100, 200, 300, 600, 1200, 1800, 3600, 6000
    };

    private boolean remainActive;
    private int activeTicks = DEFAULT_ACTIVE_TICKS;
    private boolean inputActive;
    private boolean latched;
    private boolean manualOff;
    private long holdUntil;

    public boolean isRemainActive() {
        return remainActive;
    }

    /** Server side: enables or disables the indefinite latch. */
    public boolean setRemainActive(boolean value, long now) {
        if (remainActive == value) return false;
        remainActive = value;
        if (!value && latched) {
            latched = false;
            if (!inputActive) holdUntil = now + activeTicks;
        }
        return true;
    }

    public int getActiveTicks() {
        return activeTicks;
    }

    public int getActiveSeconds() {
        return Math.max(1, activeTicks / 20);
    }

    /** Server side: sets the post-signal timer in ticks. */
    public boolean setActiveTicks(int value, long now) {
        int next = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, value));
        if (activeTicks == next) return false;
        activeTicks = next;
        if (holdUntil > now) holdUntil = now + activeTicks;
        return true;
    }

    /** Server side: steps through the preset timer values. Negative reverses. */
    public boolean cycleActiveTicks(int direction, long now) {
        int next;
        if (direction >= 0) {
            next = PRESETS[0];
            for (int preset : PRESETS) {
                if (preset > activeTicks) {
                    next = preset;
                    break;
                }
            }
        } else {
            next = PRESETS[PRESETS.length - 1];
            for (int i = PRESETS.length - 1; i >= 0; i--) {
                if (PRESETS[i] < activeTicks) {
                    next = PRESETS[i];
                    break;
                }
            }
        }
        return setActiveTicks(next, now);
    }

    /**
     * Server side: reports the current wireless input. A rising edge clears any manual-off state;
     * a falling edge starts the latch or timer.
     */
    public boolean setInput(boolean active, long now) {
        if (inputActive == active) return false;
        inputActive = active;
        if (active) {
            manualOff = false;
            holdUntil = 0;
            return true;
        }
        if (manualOff) {
            manualOff = false;
            holdUntil = 0;
            return true;
        }
        if (remainActive) {
            latched = true;
            holdUntil = 0;
        } else {
            holdUntil = now + activeTicks;
        }
        return true;
    }

    /** Server side: expires a finished timer. */
    public boolean tick(long now) {
        if (holdUntil <= 0 || holdUntil > now) return false;
        holdUntil = 0;
        return true;
    }

    public boolean isActive(long now) {
        return !manualOff && (inputActive || latched || holdUntil > now);
    }

    public boolean isInputActive() {
        return inputActive;
    }

    public boolean isLatched() {
        return latched;
    }

    /** Server side: a manual off suppresses the current signal until it falls. */
    public void manualOff() {
        manualOff = true;
        latched = false;
        holdUntil = 0;
    }

    /** Server side: a manual on cancels an earlier manual-off suppression. */
    public void clearManualOff() {
        manualOff = false;
    }

    public void writeToNBT(NBTTagCompound tag, String prefix) {
        tag.setBoolean(prefix + "RemainActive", remainActive);
        tag.setInteger(prefix + "ActiveTicks", activeTicks);
        tag.setBoolean(prefix + "Latched", latched);
        tag.setBoolean(prefix + "ManualOff", manualOff);
        tag.setLong(prefix + "HoldUntil", holdUntil);
    }

    public void readFromNBT(NBTTagCompound tag, String prefix, long now) {
        remainActive = tag.getBoolean(prefix + "RemainActive");
        activeTicks = tag.hasKey(prefix + "ActiveTicks")
                ? Math.max(MIN_ACTIVE_TICKS,
                        Math.min(MAX_ACTIVE_TICKS, tag.getInteger(prefix + "ActiveTicks")))
                : DEFAULT_ACTIVE_TICKS;
        latched = tag.getBoolean(prefix + "Latched");
        manualOff = tag.getBoolean(prefix + "ManualOff");
        holdUntil = tag.getLong(prefix + "HoldUntil");
        inputActive = false;
        if (holdUntil < now) holdUntil = 0;
    }
}
