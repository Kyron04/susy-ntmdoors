package com.ntmdoors.tile;

import com.ntmdoors.door.DoorDecl;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;

/**
 * Speaker state and the redstone-driven alarm. The speaker has no inventory or access control;
 * it simply loops its selected sound while enabled and receiving redstone power.
 */
public class TileEntitySpeaker extends TileEntitySynced implements ITickable {

    /** The lockout alarm is the first placeholder sound. */
    public static final int SOUND_ALARM = 0;
    public static final int SOUND_COUNT = 1;
    /** door_alarm.ogg is 1.892 s long; 38 ticks keeps the repeats close together. */
    private static final int ALARM_INTERVAL_TICKS = 38;
    private static final float ALARM_VOLUME = 1.5F;

    private boolean enabled = true;
    private int sound = SOUND_ALARM;
    private boolean powered;
    private int soundCooldown;

    public boolean isEnabled() {
        return enabled;
    }

    /** Server side: enables or mutes the speaker without deleting its settings. */
    public boolean setEnabled(boolean value) {
        if (world == null || world.isRemote || enabled == value) return false;
        enabled = value;
        soundCooldown = 0;
        sync();
        return true;
    }

    public int getSound() {
        return sound;
    }

    /** Server side: selects one of the placeholder sounds. */
    public boolean setSound(int value) {
        if (world == null || world.isRemote) return false;
        int next = clampSound(value);
        if (sound == next) return false;
        sound = next;
        soundCooldown = 0;
        sync();
        return true;
    }

    public boolean isPowered() {
        return powered;
    }

    public static String soundName(int sound) {
        return sound == SOUND_ALARM ? "Alarm" : "Unknown";
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) {
            powered = world.isBlockPowered(pos);
        }
    }

    /** Server side: called by the block when a neighbouring redstone component changes. */
    public void onNeighborChanged() {
        if (world == null || world.isRemote) return;
        updatePower(world.isBlockPowered(pos));
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        updatePower(world.isBlockPowered(pos));
        if (!enabled || !powered) {
            soundCooldown = 0;
            return;
        }

        if (soundCooldown > 0) {
            soundCooldown--;
            return;
        }

        playAlarm();
        soundCooldown = ALARM_INTERVAL_TICKS;
    }

    private void updatePower(boolean value) {
        if (powered == value) return;
        powered = value;
        soundCooldown = 0;
        sync();
    }

    private void playAlarm() {
        world.playSound(null, pos, DoorDecl.sound("door_alarm"),
                SoundCategory.BLOCKS, ALARM_VOLUME, 1F);
    }

    private static int clampSound(int value) {
        return Math.floorMod(value, SOUND_COUNT);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("enabled", enabled);
        tag.setInteger("sound", sound);
        tag.setBoolean("powered", powered);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        enabled = !tag.hasKey("enabled") || tag.getBoolean("enabled");
        sound = clampSound(tag.getInteger("sound"));
        powered = tag.getBoolean("powered");
        soundCooldown = 0;
    }
}
