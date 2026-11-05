package com.ntmdoors.client;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.BooleanSupplier;

/**
 * The legacy backup's card-read loop. It repeats at the machine while the synced backup state is
 * running and stops as soon as the server reports success or cancellation.
 */
@SideOnly(Side.CLIENT)
public class BackupLoopSound extends MovingSound {

    private final TileEntity tile;
    private final BooleanSupplier active;

    public BackupLoopSound(TileEntity tile, BooleanSupplier active, SoundEvent sound,
                           float volume) {
        super(sound, SoundCategory.BLOCKS);
        this.tile = tile;
        this.active = active;
        this.volume = volume;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void update() {
        if (tile == null || tile.isInvalid() || !tile.hasWorld() || !active.getAsBoolean()) {
            donePlaying = true;
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (tile == null || !tile.hasWorld()) return;
        xPosF = tile.getPos().getX() + 0.5F;
        yPosF = tile.getPos().getY() + 0.5F;
        zPosF = tile.getPos().getZ() + 0.5F;
    }
}
