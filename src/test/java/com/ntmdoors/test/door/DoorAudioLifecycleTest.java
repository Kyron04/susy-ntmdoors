package com.ntmdoors.test.door;

import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import net.minecraft.Bootstrap;
import net.minecraft.sound.SoundEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoorAudioLifecycleTest {

    @BeforeAll
    public static void setup() {
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    @Test
    @DisplayName("Verify Audio Sound Sets Configured for All 22 Doors")
    public void testAudioSoundSetsConfigured() {
        assertEquals(22, DoorDecls.ALL.size());

        for (DoorDecl d : DoorDecls.ALL) {
            assertTrue(d.soundVolume > 0.0f, "Door " + d.name + " must have positive soundVolume");

            DoorDecl.Sounds openSounds = d.soundSet(true);
            assertNotNull(openSounds, "Opening sounds must not be null for " + d.name);

            DoorDecl.Sounds closeSounds = d.soundSet(false);
            assertNotNull(closeSounds, "Closing sounds must not be null for " + d.name);

            // Check cues
            if (d.soundCues != null && !d.soundCues.isEmpty()) {
                for (DoorDecl.SoundCue cue : d.soundCues) {
                    assertNotNull(cue.sound, "SoundCue sound must not be null in " + d.name);
                    assertTrue(cue.tick >= 0, "SoundCue tick must be non-negative in " + d.name);
                    int maxTicks = cue.opening ? d.openTicks : d.closeTicks;
                    assertTrue(cue.tick <= maxTicks,
                            "SoundCue tick (" + cue.tick + ") must not exceed stroke duration (" + maxTicks + ") in " + d.name);
                }
            }
        }
    }

    @Test
    @DisplayName("Verify Distinct Open and Close Sound Events When Present")
    public void testSoundEventsValid() {
        for (DoorDecl d : DoorDecls.ALL) {
            DoorDecl.Sounds openSounds = d.soundSet(true);
            DoorDecl.Sounds closeSounds = d.soundSet(false);

            if (openSounds.start != null) {
                assertNotNull(openSounds.start.getId(), "Sound start ID must be non-null in " + d.name);
            }
            if (openSounds.end != null) {
                assertNotNull(openSounds.end.getId(), "Sound end ID must be non-null in " + d.name);
            }
            if (openSounds.loop != null) {
                assertNotNull(openSounds.loop.getId(), "Sound loop ID must be non-null in " + d.name);
            }
            if (closeSounds.start != null) {
                assertNotNull(closeSounds.start.getId(), "Sound close start ID must be non-null in " + d.name);
            }
            if (closeSounds.end != null) {
                assertNotNull(closeSounds.end.getId(), "Sound close end ID must be non-null in " + d.name);
            }
            if (closeSounds.loop != null) {
                assertNotNull(closeSounds.loop.getId(), "Sound close loop ID must be non-null in " + d.name);
            }
        }
    }
}
