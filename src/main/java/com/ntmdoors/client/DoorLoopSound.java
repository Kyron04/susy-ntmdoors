package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntityDoorGeneric;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A repeating door movement sound anchored to the core cell of a door.
 *
 * <p>HBM starts its loop sounds together with the animation and stops them on the transition to
 * OPEN or CLOSED. The Ogg files themselves are short, so a plain one-shot would either end before
 * the door does or keep playing after it has stopped. This sound repeats for exactly as long as the
 * client's copy of the door state machine says the door is moving.</p>
 */
@SideOnly(Side.CLIENT)
public class DoorLoopSound extends MovingSound {

    private final TileEntityDoorGeneric door;

    public DoorLoopSound(TileEntityDoorGeneric door, SoundEvent sound, float volume) {
        super(sound, SoundCategory.BLOCKS);
        this.door = door;
        this.volume = volume;
        this.pitch = 1F;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.LINEAR;
        updatePosition();
    }

    @Override
    public void update() {
        if (door == null || door.isInvalid() || !door.hasWorld() || !door.getState().isMoving()) {
            donePlaying = true;
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        if (door == null || !door.hasWorld()) return;
        xPosF = door.getPos().getX() + 0.5F;
        yPosF = door.getPos().getY() + 0.5F;
        zPosF = door.getPos().getZ() + 0.5F;
    }
}
