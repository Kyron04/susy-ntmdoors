package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The legacy backup's card-read loop. It repeats at the programmer while the synced backup state
 * is running and stops as soon as the server reports success or cancellation.
 */
@SideOnly(Side.CLIENT)
public class BackupLoopSound extends MovingSound {

    private final TileEntityKeycardProgrammer programmer;

    public BackupLoopSound(TileEntityKeycardProgrammer programmer, SoundEvent sound, float volume) {
        super(sound, SoundCategory.BLOCKS);
        this.programmer = programmer;
        this.volume = volume;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void update() {
        if (programmer == null || programmer.isInvalid() || !programmer.hasWorld()
                || !programmer.isBackingUp()) {
            donePlaying = true;
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (programmer == null || !programmer.hasWorld()) return;
        xPosF = programmer.getPos().getX() + 0.5F;
        yPosF = programmer.getPos().getY() + 0.5F;
        zPosF = programmer.getPos().getZ() + 0.5F;
    }
}
