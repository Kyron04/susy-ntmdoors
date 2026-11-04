package com.ntmdoors.util;

import com.ntmdoors.NTMDoors;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sound events that are not tied to a door declaration. The keycard scanner owns its own set so
 * the door sound cache stays focused on movement audio; button9 is the generic tool click.
 */
public final class ModSounds {

    public static final SoundEvent SCANNER_BUTTON3 = create("scanner_button3");
    public static final SoundEvent SCANNER_BUTTON8 = create("scanner_button8");
    public static final SoundEvent SCANNER_BELL1 = create("scanner_bell1");
    /** Screwdriver click, played once for every interaction the tool handles. */
    public static final SoundEvent BUTTON9 = create("button9");
    /** Monitor terminal UI click. */
    public static final SoundEvent GUI_CLICK = create("gui_click");

    public static final SoundEvent SPEAKER_AIR_RAID = create("speaker_air_raid");
    public static final SoundEvent SPEAKER_FACILITY = create("speaker_facility");
    public static final SoundEvent SPEAKER_INDUSTRIAL = create("speaker_industrial");
    public static final SoundEvent SPEAKER_EAS = create("speaker_eas");
    /** Speaker sound choices, in the order shown by the screen. */
    public static final List<SoundEvent> SPEAKER_SOUNDS = Collections.unmodifiableList(Arrays.asList(
            SPEAKER_AIR_RAID, SPEAKER_FACILITY, SPEAKER_INDUSTRIAL, SPEAKER_EAS));

    /** Everything that has to be registered with Forge during sound registration. */
    public static final List<SoundEvent> ALL = Collections.unmodifiableList(Arrays.asList(
            SCANNER_BUTTON3, SCANNER_BUTTON8, SCANNER_BELL1, BUTTON9, GUI_CLICK,
            SPEAKER_AIR_RAID, SPEAKER_FACILITY, SPEAKER_INDUSTRIAL, SPEAKER_EAS));

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation location = new ResourceLocation(NTMDoors.MODID, name);
        SoundEvent event = new SoundEvent(location);
        event.setRegistryName(location);
        return event;
    }
}
