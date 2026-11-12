package com.ntmdoors.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.function.BooleanSupplier;

/**
 * The legacy backup's card-read loop sound. It repeats at the machine while the synced backup state is
 * running and stops as soon as the server reports success or cancellation.
 */
@Environment(EnvType.CLIENT)
public class BackupLoopSound extends MovingSoundInstance {

    private final BlockEntity blockEntity;
    private final BooleanSupplier active;

    public BackupLoopSound(BlockEntity blockEntity, BooleanSupplier active, SoundEvent sound, float volume) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.blockEntity = blockEntity;
        this.active = active;
        this.volume = volume;
        this.pitch = 1.0F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (blockEntity == null || blockEntity.isRemoved() || !blockEntity.hasWorld() || !active.getAsBoolean()) {
            this.setDone();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (blockEntity == null || !blockEntity.hasWorld()) return;
        this.x = blockEntity.getPos().getX() + 0.5;
        this.y = blockEntity.getPos().getY() + 0.5;
        this.z = blockEntity.getPos().getZ() + 0.5;
    }
}
