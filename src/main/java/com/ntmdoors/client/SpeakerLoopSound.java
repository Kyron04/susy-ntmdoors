package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Repeating alarm anchored to a speaker. The loop is client-side so turning off redstone or
 * Enabled stops the sound immediately, even when the selected alarm is long.
 */
@SideOnly(Side.CLIENT)
public class SpeakerLoopSound extends MovingSound {

    private final TileEntitySpeaker speaker;
    private final int soundIndex;

    public SpeakerLoopSound(TileEntitySpeaker speaker, SoundEvent sound, int soundIndex, float volume) {
        super(sound, SoundCategory.BLOCKS);
        this.speaker = speaker;
        this.soundIndex = soundIndex;
        this.volume = volume;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        updatePosition();
    }

    public void updateVolume(float value) {
        this.volume = value;
    }

    @Override
    public void update() {
        if (speaker == null || speaker.isInvalid() || !speaker.hasWorld()
                || !speaker.isEnabled() || !speaker.isPowered()
                || speaker.getSound() != soundIndex
                || speaker.getVolume() <= 0 || speaker.getRange() <= 0) {
            donePlaying = true;
            return;
        }
        this.volume = speaker.getEffectiveVolume();
        updatePosition();
    }

    private void updatePosition() {
        if (speaker == null || !speaker.hasWorld()) return;
        xPosF = speaker.getPos().getX() + 0.5F;
        yPosF = speaker.getPos().getY() + 0.5F;
        zPosF = speaker.getPos().getZ() + 0.5F;
    }
}
