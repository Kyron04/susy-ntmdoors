package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntityKeycardScanner;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The scanner's "card read" loop. It repeats while the synced scanner state is SCANNING and stops
 * as soon as the server reports a result, so the sound always matches the screen.
 */
@SideOnly(Side.CLIENT)
public class ScannerLoopSound extends MovingSound {

    private final TileEntityKeycardScanner scanner;

    public ScannerLoopSound(TileEntityKeycardScanner scanner, SoundEvent sound, float volume) {
        super(sound, SoundCategory.BLOCKS);
        this.scanner = scanner;
        this.volume = volume;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void update() {
        if (scanner == null || scanner.isInvalid() || !scanner.hasWorld()
                || scanner.getState() != TileEntityKeycardScanner.State.SCANNING) {
            donePlaying = true;
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (scanner == null || !scanner.hasWorld()) return;
        xPosF = scanner.getPos().getX() + 0.5F;
        yPosF = scanner.getPos().getY() + 0.5F;
        zPosF = scanner.getPos().getZ() + 0.5F;
    }
}
