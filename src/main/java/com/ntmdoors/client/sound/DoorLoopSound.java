package com.ntmdoors.client.sound;

import com.ntmdoors.block.entity.DoorBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Continuous looping sound synchronized with DoorBlockEntity movement ticks.
 * Automatically halts when the door completes its stroke or the block entity is unloaded.
 */
@Environment(EnvType.CLIENT)
public class DoorLoopSound extends MovingSoundInstance {

    private final DoorBlockEntity door;

    public DoorLoopSound(DoorBlockEntity door, SoundEvent sound, float volume) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.door = door;
        this.volume = volume;
        this.pitch = 1.0f;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (door == null || door.isRemoved() || !door.hasWorld() || !door.getState().isMoving()) {
            this.setDone();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (door == null || !door.hasWorld()) return;
        this.x = door.getPos().getX() + 0.5;
        this.y = door.getPos().getY() + 0.5;
        this.z = door.getPos().getZ() + 0.5;
    }
}
