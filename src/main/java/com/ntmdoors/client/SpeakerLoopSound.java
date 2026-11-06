package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
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

    public SpeakerLoopSound(TileEntitySpeaker speaker, SoundEvent sound, int soundIndex) {
        super(sound, SoundCategory.BLOCKS);
        this.speaker = speaker;
        this.soundIndex = soundIndex;
        this.volume = 1F;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        // The alarm is centred on the listener and faded manually by distance. Keeping the
        // source at the speaker made the perceived loudness depend on where the player looked.
        this.attenuationType = ISound.AttenuationType.NONE;
        updatePosition(Minecraft.getMinecraft().player);
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

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            donePlaying = true;
            return;
        }

        double dx = speaker.getPos().getX() + 0.5D - player.posX;
        double dy = speaker.getPos().getY() + 0.5D - (player.posY + player.getEyeHeight());
        double dz = speaker.getPos().getZ() + 0.5D - player.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float range = speaker.getRange();
        if (distance >= range) {
            donePlaying = true;
            return;
        }

        float fade = 1.0F - (float) (distance / range);
        this.volume = Math.max(0.0001F, Math.min(1.0F,
                fade * (speaker.getVolume() / (float) TileEntitySpeaker.MAX_VOLUME)));
        updatePosition(player);
    }

    private void updatePosition(EntityPlayer player) {
        if (speaker == null || !speaker.hasWorld() || player == null) return;
        // Keep the source on the listener so OpenAL does not pan the alarm as the player turns.
        xPosF = (float) player.posX;
        yPosF = (float) (player.posY + player.getEyeHeight());
        zPosF = (float) player.posZ;
    }
}
