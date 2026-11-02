package com.ntmdoors.tile;

import com.ntmdoors.door.DoorDecl;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Speaker state and the redstone-driven alarm. The speaker has no inventory or access control;
 * it simply loops its selected sound while enabled and receiving redstone power. It can also be
 * linked to Redstone Gates with the Redstone Relay Key; starting the alarm pulses every linked gate.
 */
public class TileEntitySpeaker extends TileEntitySynced implements ITickable, RedstoneBindingHost {

    /** The lockout alarm is the first placeholder sound. */
    public static final int SOUND_ALARM = 0;
    public static final int SOUND_COUNT = 1;
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 64;
    public static final int DEFAULT_RANGE = 32;
    /** door_alarm.ogg is 1.892 s long; 38 ticks keeps the repeats close together. */
    private static final int ALARM_INTERVAL_TICKS = 38;
    /** Minecraft's sound range is roughly volume * 16 blocks. */
    private static final float SOUND_RANGE_PER_VOLUME = 16F;

    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private boolean enabled = true;
    private int sound = SOUND_ALARM;
    private int range = DEFAULT_RANGE;
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
        if (value && powered) redstoneBinding.fire();
        sync();
        return true;
    }

    public int getSound() {
        return sound;
    }

    public int getRange() {
        return range;
    }

    /** Server side: sets the alarm's audible range in blocks. */
    public boolean setRange(int value) {
        if (world == null || world.isRemote) return false;
        int next = clampRange(value);
        if (range == next) return false;
        range = next;
        sync();
        return true;
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
    public RedstoneBinding getRedstoneBinding() {
        return redstoneBinding;
    }

    @Override
    public void syncRedstoneState() {
        sync();
    }

    @Override
    public void onRedstoneBindingChanged() {
        // The speaker has no separate binding-event output; the link itself is enough.
        sync();
    }

    @Override
    public void sendRedstoneMessage(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
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
        redstoneBinding.update();
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
        if (powered && enabled) redstoneBinding.fire();
        sync();
    }

    private void playAlarm() {
        float volume = range / SOUND_RANGE_PER_VOLUME;
        world.playSound(null, pos, DoorDecl.sound("door_alarm"),
                SoundCategory.BLOCKS, volume, 1F);
    }

    private static int clampSound(int value) {
        return Math.floorMod(value, SOUND_COUNT);
    }

    private static int clampRange(int value) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("enabled", enabled);
        tag.setInteger("sound", sound);
        tag.setInteger("range", range);
        tag.setBoolean("powered", powered);
        redstoneBinding.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        enabled = !tag.hasKey("enabled") || tag.getBoolean("enabled");
        sound = clampSound(tag.getInteger("sound"));
        range = tag.hasKey("range") ? clampRange(tag.getInteger("range")) : DEFAULT_RANGE;
        powered = tag.getBoolean("powered");
        redstoneBinding.readFromNBT(tag);
        soundCooldown = 0;
    }
}
