package com.ntmdoors.sound;

import com.ntmdoors.NTMDoors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Registry constants for all 28 door, scanner, alarm, tool, and breaching sound events.
 */
public final class ModSounds {

    // Door movement sounds (17)
    public static final SoundEvent DOOR_VAULT_SCRAPE = register("door_vault_scrape");
    public static final SoundEvent DOOR_VAULT_THUD = register("door_vault_thud");
    public static final SoundEvent DOOR_SLIDING_OPENING = register("door_sliding_opening");
    public static final SoundEvent DOOR_SLIDING_OPENED = register("door_sliding_opened");
    public static final SoundEvent DOOR_SLIDING_SHUT = register("door_sliding_shut");
    public static final SoundEvent DOOR_WGH_START = register("door_wgh_start");
    public static final SoundEvent DOOR_WGH_STOP = register("door_wgh_stop");
    public static final SoundEvent DOOR_WGH_BIG_START = register("door_wgh_big_start");
    public static final SoundEvent DOOR_WGH_BIG_STOP = register("door_wgh_big_stop");
    public static final SoundEvent DOOR_GARAGE_MOVE = register("door_garage_move");
    public static final SoundEvent DOOR_GARAGE_STOP = register("door_garage_stop");
    public static final SoundEvent DOOR_SEAL_OPEN = register("door_seal_open");
    public static final SoundEvent DOOR_SEAL_STOP = register("door_seal_stop");
    public static final SoundEvent DOOR_TRANSITION_SEAL = register("door_transition_seal");
    public static final SoundEvent DOOR_ALARM = register("door_alarm");
    public static final SoundEvent DOOR_LEVER = register("door_lever");
    public static final SoundEvent DOOR_LATCH = register("door_latch");

    // Scanner & Security sounds (3)
    public static final SoundEvent SCANNER_BUTTON3 = register("scanner_button3");
    public static final SoundEvent SCANNER_BUTTON8 = register("scanner_button8");
    public static final SoundEvent SCANNER_BELL1 = register("scanner_bell1");

    // Tool & UI sounds (2)
    public static final SoundEvent BUTTON9 = register("button9");
    public static final SoundEvent GUI_CLICK = register("gui_click");

    // Speaker alarm tracks (4)
    public static final SoundEvent SPEAKER_AIR_RAID = register("speaker_air_raid");
    public static final SoundEvent SPEAKER_FACILITY = register("speaker_facility");
    public static final SoundEvent SPEAKER_INDUSTRIAL = register("speaker_industrial");
    public static final SoundEvent SPEAKER_EAS = register("speaker_eas");

    // Thermite breaching sounds (2)
    public static final SoundEvent THERMITE_BEEP = register("thermite_beep");
    public static final SoundEvent THERMITE_EXPLOSION = register("thermite_explosion");

    // Speaker track selection list (order matches GUI selector)
    public static final List<SoundEvent> SPEAKER_SOUNDS = List.of(
            SPEAKER_AIR_RAID,
            SPEAKER_FACILITY,
            SPEAKER_INDUSTRIAL,
            SPEAKER_EAS
    );

    // Complete list of all 28 registered sound events
    public static final List<SoundEvent> ALL_SOUNDS = List.of(
            DOOR_VAULT_SCRAPE, DOOR_VAULT_THUD, DOOR_SLIDING_OPENING, DOOR_SLIDING_OPENED, DOOR_SLIDING_SHUT,
            DOOR_WGH_START, DOOR_WGH_STOP, DOOR_WGH_BIG_START, DOOR_WGH_BIG_STOP, DOOR_GARAGE_MOVE,
            DOOR_GARAGE_STOP, DOOR_SEAL_OPEN, DOOR_SEAL_STOP, DOOR_TRANSITION_SEAL, DOOR_ALARM,
            DOOR_LEVER, DOOR_LATCH, SCANNER_BUTTON3, SCANNER_BUTTON8, SCANNER_BELL1, BUTTON9, GUI_CLICK,
            SPEAKER_AIR_RAID, SPEAKER_FACILITY, SPEAKER_INDUSTRIAL, SPEAKER_EAS, THERMITE_BEEP, THERMITE_EXPLOSION
    );

    private ModSounds() {}

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(NTMDoors.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {
        // Triggers static class loading and registration
    }
}
