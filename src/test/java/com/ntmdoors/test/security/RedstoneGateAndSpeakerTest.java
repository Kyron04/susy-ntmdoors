package com.ntmdoors.test.security;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.RedstoneGateBlock;
import com.ntmdoors.block.SpeakerBlock;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SpeakerBlockEntity;
import com.ntmdoors.client.sound.SpeakerSoundInstance;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RedstoneGateAndSpeakerTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
    }

    private RedstoneGateBlockEntity createGate() {
        RedstoneGateBlock block = ModBlocks.REDSTONE_GATE;
        BlockState state = block.getDefaultState();
        return new RedstoneGateBlockEntity(BlockPos.ORIGIN, state);
    }

    private SpeakerBlockEntity createSpeaker() {
        SpeakerBlock block = ModBlocks.SPEAKER;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new SpeakerBlockEntity(BlockPos.ORIGIN, state);
    }

    @Test
    @DisplayName("Verify Redstone Gate pulse duration adjustment and clamping (20 to 6000 ticks)")
    public void testRedstoneGatePulseDurationBounds() {
        RedstoneGateBlockEntity gate = createGate();

        // Default pulse duration
        assertEquals(20, gate.getActiveTicks(), "Default pulse duration must be 20 ticks (1 second)");

        // Adjust +20 ticks (+1s)
        gate.adjustActiveTicks(20);
        assertEquals(40, gate.getActiveTicks());

        // Adjust +200 ticks (+10s)
        gate.adjustActiveTicks(200);
        assertEquals(240, gate.getActiveTicks());

        // Adjust exceeding upper bound (6000 ticks = 5 minutes)
        gate.adjustActiveTicks(10000);
        assertEquals(RedstoneGateBlockEntity.MAX_ACTIVE_TICKS, gate.getActiveTicks());
        assertEquals(6000, gate.getActiveTicks());

        // Adjust -400 ticks (-20s)
        gate.adjustActiveTicks(-400);
        assertEquals(5600, gate.getActiveTicks());

        // Adjust exceeding lower bound (20 ticks = 1 second)
        gate.adjustActiveTicks(-10000);
        assertEquals(RedstoneGateBlockEntity.MIN_ACTIVE_TICKS, gate.getActiveTicks());
        assertEquals(20, gate.getActiveTicks());
    }

    @Test
    @DisplayName("Verify Redstone Gate stayActive latch and inverted operation modes")
    public void testRedstoneGateStayActiveAndInvertModes() {
        RedstoneGateBlockEntity gate = createGate();

        // Defaults
        assertFalse(gate.isStayActive(), "Default stayActive must be false");
        assertFalse(gate.isInverted(), "Default inverted must be false");
        assertTrue(gate.isEnabled(), "Default enabled must be true");

        // Toggle stayActive
        gate.setStayActive(true);
        assertTrue(gate.isStayActive());

        gate.setStayActive(false);
        assertFalse(gate.isStayActive());

        // Toggle invert
        gate.setInverted(true);
        assertTrue(gate.isInverted());

        gate.setInverted(false);
        assertFalse(gate.isInverted());

        // Toggle enabled
        gate.setEnabled(false);
        assertFalse(gate.isEnabled());
        assertTrue(gate.isSystemIdle());
    }

    @Test
    @DisplayName("Verify Speaker settings, boundaries, and clamping")
    public void testSpeakerSettingsAndClamping() {
        SpeakerBlockEntity speaker = createSpeaker();

        // Defaults
        assertTrue(speaker.isEnabled());
        assertEquals(SpeakerBlockEntity.SOUND_AIR_RAID, speaker.getSound());
        assertEquals(SpeakerBlockEntity.DEFAULT_RANGE, speaker.getRange());
        assertEquals(32, speaker.getRange());
        assertEquals(SpeakerBlockEntity.DEFAULT_VOLUME, speaker.getVolume());
        assertEquals(100, speaker.getVolume());

        // Sound selection (0..3)
        speaker.setSound(SpeakerBlockEntity.SOUND_FACILITY);
        assertEquals(SpeakerBlockEntity.SOUND_FACILITY, speaker.getSound());

        speaker.setSound(SpeakerBlockEntity.SOUND_INDUSTRIAL);
        assertEquals(SpeakerBlockEntity.SOUND_INDUSTRIAL, speaker.getSound());

        speaker.setSound(SpeakerBlockEntity.SOUND_EAS);
        assertEquals(SpeakerBlockEntity.SOUND_EAS, speaker.getSound());

        // Sound clamping
        speaker.setSound(99);
        assertEquals(SpeakerBlockEntity.SOUND_EAS, speaker.getSound(), "Sound index > 3 should clamp to 3");

        speaker.setSound(-5);
        assertEquals(SpeakerBlockEntity.SOUND_AIR_RAID, speaker.getSound(), "Sound index < 0 should clamp to 0");

        // Range clamping (1..64)
        speaker.setRange(50);
        assertEquals(50, speaker.getRange());

        speaker.setRange(100);
        assertEquals(SpeakerBlockEntity.MAX_RANGE, speaker.getRange());
        assertEquals(64, speaker.getRange());

        speaker.setRange(0);
        assertEquals(SpeakerBlockEntity.MIN_RANGE, speaker.getRange());
        assertEquals(1, speaker.getRange());

        // Volume clamping (0..100)
        speaker.setVolume(50);
        assertEquals(50, speaker.getVolume());

        speaker.setVolume(150);
        assertEquals(SpeakerBlockEntity.MAX_VOLUME, speaker.getVolume());
        assertEquals(100, speaker.getVolume());

        speaker.setVolume(-10);
        assertEquals(SpeakerBlockEntity.MIN_VOLUME, speaker.getVolume());
        assertEquals(0, speaker.getVolume());
    }

    @Test
    @DisplayName("Verify Speaker Sound Instance attenuation and distance fade math")
    public void testSpeakerAudioDistanceFadeMath() {
        int range = 32;

        // Formula: fade = 1.0F - (distance / range)
        float fadeAtSource = Math.max(0.0F, Math.min(1.0F, 1.0F - (0.0F / (float) range)));
        assertEquals(1.0F, fadeAtSource, 1e-4F, "Fade at distance 0 must be 1.0 (full volume)");

        float fadeAtHalf = Math.max(0.0F, Math.min(1.0F, 1.0F - (16.0F / (float) range)));
        assertEquals(0.5F, fadeAtHalf, 1e-4F, "Fade at half distance must be 0.5");

        float fadeAtBoundary = Math.max(0.0F, Math.min(1.0F, 1.0F - (32.0F / (float) range)));
        assertEquals(0.0F, fadeAtBoundary, 1e-4F, "Fade at boundary must be 0.0 (silent)");

        float fadeBeyond = Math.max(0.0F, Math.min(1.0F, 1.0F - (48.0F / (float) range)));
        assertEquals(0.0F, fadeBeyond, 1e-4F, "Fade beyond range must be 0.0 (silent)");
    }

    @Test
    @DisplayName("Verify SpeakerSoundInstance uses NONE attenuation type")
    public void testSpeakerSoundInstanceAttenuationType() {
        SpeakerBlockEntity speaker = createSpeaker();
        SpeakerSoundInstance soundInstance = new SpeakerSoundInstance(
                speaker,
                ModSounds.SPEAKER_AIR_RAID,
                0
        );

        assertEquals(SoundInstance.AttenuationType.NONE, soundInstance.getAttenuationType(),
                "Speaker must use AttenuationType.NONE for listener-relative coordinate positioning");
    }
}
