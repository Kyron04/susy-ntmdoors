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

    /** Everything that has to be registered with Forge during sound registration. */
    public static final List<SoundEvent> ALL = Collections.unmodifiableList(Arrays.asList(
            SCANNER_BUTTON3, SCANNER_BUTTON8, SCANNER_BELL1, BUTTON9));

    private ModSounds() {
    }

    private static SoundEvent create(String name) {
        ResourceLocation location = new ResourceLocation(NTMDoors.MODID, name);
        SoundEvent event = new SoundEvent(location);
        event.setRegistryName(location);
        return event;
    }
}
