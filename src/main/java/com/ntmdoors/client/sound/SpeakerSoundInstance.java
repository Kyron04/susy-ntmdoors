package com.ntmdoors.client.sound;

import com.ntmdoors.block.entity.SpeakerBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

@Environment(EnvType.CLIENT)
public class SpeakerSoundInstance extends MovingSoundInstance {

    private final SpeakerBlockEntity speaker;
    private final int soundIndex;

    public SpeakerSoundInstance(SpeakerBlockEntity speaker, SoundEvent sound, int soundIndex) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.speaker = speaker;
        this.soundIndex = soundIndex;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.NONE;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            updatePosition(client.player);
        }
    }

    @Override
    public void tick() {
        if (speaker == null || speaker.isRemoved() || !speaker.hasWorld()
                || !speaker.isEnabled() || !speaker.isPowered()
                || speaker.getSound() != soundIndex
                || speaker.getVolume() <= 0 || speaker.getRange() <= 0) {
            this.setDone();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            this.setDone();
            return;
        }
        PlayerEntity player = client.player;

        double dx = (speaker.getPos().getX() + 0.5) - player.getX();
        double dy = (speaker.getPos().getY() + 0.5) - player.getEyeY();
        double dz = (speaker.getPos().getZ() + 0.5) - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float range = speaker.getRange();
        if (distance >= range) {
            this.setDone();
            return;
        }

        float fade = 1.0F - (float) (distance / range);
        this.volume = Math.max(0.0001F, Math.min(1.0F, fade * (speaker.getVolume() / (float) SpeakerBlockEntity.MAX_VOLUME)));
        updatePosition(player);
    }

    private void updatePosition(PlayerEntity player) {
        if (player == null) return;
        this.x = player.getX();
        this.y = player.getEyeY();
        this.z = player.getZ();
    }
}
