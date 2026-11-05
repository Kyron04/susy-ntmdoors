package com.ntmdoors.tile;

import com.ntmdoors.client.SpeakerLoopSound;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Speaker state and the redstone-driven alarm. The speaker has no inventory or access control;
 * it loops its selected sound while enabled and receiving redstone power. It can also be linked to
 * Redstone Gates with the Redstone Relay Key; starting the alarm pulses every linked gate.
 */
public class TileEntitySpeaker extends TileEntitySynced implements ITickable, RedstoneBindingHost {

    public static final int SOUND_AIR_RAID = 0;
    public static final int SOUND_COUNT = 4;

    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 64;
    public static final int DEFAULT_RANGE = 32;
    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;
    public static final int DEFAULT_VOLUME = 100;

    /** Minecraft's sound range is roughly volume * 16 blocks. */
    private static final float SOUND_RANGE_PER_VOLUME = 16F;
    private static final String[] SOUND_NAMES = {"Air Raid", "Facility", "Industrial", "EAS"};

    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private boolean enabled = true;
    private int sound = SOUND_AIR_RAID;
    private int range = DEFAULT_RANGE;
    private int volume = DEFAULT_VOLUME;
    private boolean powered;
    private boolean systemIdle;
    private boolean systemAlarm;

    @SideOnly(Side.CLIENT)
    private SpeakerLoopSound loopSound;
    @SideOnly(Side.CLIENT)
    private int loopSoundIndex;
    @SideOnly(Side.CLIENT)
    private float loopVolume;

    public boolean isEnabled() {
        return enabled;
    }

    /** Server side: enables or mutes the speaker without deleting its settings. */
    public boolean setEnabled(boolean value) {
        if (world == null || world.isRemote || enabled == value) return false;
        enabled = value;
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

    public int getVolume() {
        return volume;
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

    /** Server side: sets the alarm volume, 0-100 percent. */
    public boolean setVolume(int value) {
        if (world == null || world.isRemote) return false;
        int next = clampVolume(value);
        if (volume == next) return false;
        volume = next;
        sync();
        return true;
    }

    /** Server side: selects one of the speaker sounds. */
    public boolean setSound(int value) {
        if (world == null || world.isRemote) return false;
        int next = clampSound(value);
        if (sound == next) return false;
        sound = next;
        sync();
        return true;
    }

    public boolean isPowered() {
        return powered;
    }

    public float getEffectiveVolume() {
        return (range / SOUND_RANGE_PER_VOLUME) * (volume / (float) MAX_VOLUME);
    }

    public SoundEvent getSoundEvent() {
        return ModSounds.SPEAKER_SOUNDS.get(clampSound(sound));
    }

    public static String soundName(int sound) {
        return SOUND_NAMES[clampSound(sound)];
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
        if (SecurityEvents.isShutdown(world, pos)) return;
        updatePower(world.isBlockPowered(pos));
    }

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            updateClientSound();
            return;
        }

        if (SecurityEvents.isShutdown(world, pos)) {
            if (!systemIdle || systemAlarm || powered) {
                systemIdle = true;
                systemAlarm = false;
                redstoneBinding.clearHold();
                powered = false;
                sync();
            }
            return;
        }
        systemIdle = false;

        boolean alarm = SecurityEvents.isAlarm(world, pos);
        if (systemAlarm != alarm) {
            systemAlarm = alarm;
            sync();
        }

        updatePower(world.isBlockPowered(pos));
        redstoneBinding.update();
    }

    private void updatePower(boolean value) {
        if (powered == value) return;
        powered = value;
        if (powered && enabled) redstoneBinding.fire();
        sync();
    }

    @SideOnly(Side.CLIENT)
    private void updateClientSound() {
        if (!enabled || (!powered && !systemAlarm) || volume <= 0 || range <= 0) {
            stopClientSound();
            return;
        }

        if (loopSound != null && loopSound.isDonePlaying()) {
            loopSound = null;
            loopSoundIndex = -1;
            loopVolume = -1F;
        }

        float effectiveVolume = getEffectiveVolume();
        if (loopSound == null || loopSoundIndex != sound
                || Math.abs(loopVolume - effectiveVolume) > 0.001F) {
            stopClientSound();
            loopSoundIndex = sound;
            loopVolume = effectiveVolume;
            loopSound = new SpeakerLoopSound(this, getSoundEvent(), sound, effectiveVolume);
            Minecraft.getMinecraft().getSoundHandler().playSound(loopSound);
        } else {
            loopSound.updateVolume(effectiveVolume);
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopClientSound() {
        if (loopSound == null) return;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        if (handler != null) handler.stopSound(loopSound);
        loopSound = null;
        loopSoundIndex = -1;
        loopVolume = -1F;
    }

    @Override
    public void invalidate() {
        if (world != null && world.isRemote) stopClientSound();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && world.isRemote) stopClientSound();
        super.onChunkUnload();
    }

    private static int clampSound(int value) {
        return Math.floorMod(value, SOUND_COUNT);
    }

    private static int clampRange(int value) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    private static int clampVolume(int value) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, value));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("enabled", enabled);
        tag.setInteger("sound", sound);
        tag.setInteger("range", range);
        tag.setInteger("volume", volume);
        tag.setBoolean("powered", powered);
        tag.setBoolean("systemAlarm", systemAlarm);
        redstoneBinding.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        enabled = !tag.hasKey("enabled") || tag.getBoolean("enabled");
        sound = clampSound(tag.getInteger("sound"));
        range = tag.hasKey("range") ? clampRange(tag.getInteger("range")) : DEFAULT_RANGE;
        volume = tag.hasKey("volume") ? clampVolume(tag.getInteger("volume")) : DEFAULT_VOLUME;
        powered = tag.getBoolean("powered");
        systemAlarm = tag.getBoolean("systemAlarm");
        redstoneBinding.readFromNBT(tag);
    }
}
