package com.ntmdoors.block.entity;

import com.ntmdoors.client.sound.SpeakerSoundInstance;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.RedstoneBinding;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SpeakerBlockEntity extends BlockEntity implements RedstoneBindingHost {

    public static final int SOUND_AIR_RAID = 0;
    public static final int SOUND_FACILITY = 1;
    public static final int SOUND_INDUSTRIAL = 2;
    public static final int SOUND_EAS = 3;
    public static final int SOUND_COUNT = 4;

    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 64;
    public static final int DEFAULT_RANGE = 32;
    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;
    public static final int DEFAULT_VOLUME = 100;

    private static final String[] SOUND_NAMES = {"Air Raid", "Facility", "Industrial", "EAS"};

    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private boolean enabled = true;
    private int sound = SOUND_AIR_RAID;
    private int range = DEFAULT_RANGE;
    private int volume = DEFAULT_VOLUME;
    private boolean powered;

    @Environment(EnvType.CLIENT)
    private SpeakerSoundInstance loopSound;
    @Environment(EnvType.CLIENT)
    private int loopSoundIndex;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER, pos, state);
    }

    // --- RedstoneBindingHost ---

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
        sync();
    }

    @Override
    public void sendRedstoneMessage(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }

    // --- Properties ---

    public boolean isEnabled() {
        return enabled;
    }

    public boolean setEnabled(boolean value) {
        if (world != null && world.isClient) return false;
        if (enabled == value) return false;
        enabled = value;
        if (value && powered) redstoneBinding.fire();
        sync();
        return true;
    }

    public int getSound() {
        return sound;
    }

    public boolean setSound(int value) {
        if (world != null && world.isClient) return false;
        int next = clampSound(value);
        if (sound == next) return false;
        sound = next;
        sync();
        return true;
    }

    public int getRange() {
        return range;
    }

    public boolean setRange(int value) {
        if (world != null && world.isClient) return false;
        int next = clampRange(value);
        if (range == next) return false;
        range = next;
        sync();
        return true;
    }

    public int getVolume() {
        return volume;
    }

    public boolean setVolume(int value) {
        if (world != null && world.isClient) return false;
        int next = clampVolume(value);
        if (volume == next) return false;
        volume = next;
        sync();
        return true;
    }

    public boolean isPowered() {
        return powered;
    }

    public SoundEvent getSoundEvent() {
        return ModSounds.SPEAKER_SOUNDS.get(clampSound(sound));
    }

    public static String soundName(int sound) {
        return SOUND_NAMES[clampSound(sound)];
    }

    public static int clampSound(int value) {
        return Math.max(0, Math.min(SOUND_COUNT - 1, value));
    }

    public static int clampRange(int value) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    public static int clampVolume(int value) {
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, value));
    }

    // --- Redstone & Tick ---

    public void onNeighborChanged() {
        if (world == null || world.isClient) return;
        updatePower(isRedstonePowered());
    }

    public boolean isRedstonePowered() {
        if (world == null) return false;
        if (world.isReceivingRedstonePower(pos)) return true;
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.offset(side);
            BlockState s = world.getBlockState(neighbour);
            if (s.isOf(Blocks.REDSTONE_WIRE) && s.get(RedstoneWireBlock.POWER) > 0) {
                return true;
            }
        }
        return false;
    }

    private void updatePower(boolean value) {
        if (powered == value) return;
        powered = value;
        if (powered && enabled) redstoneBinding.fire();
        sync();
    }

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) {
            updateClientSound();
            return;
        }
        updatePower(isRedstonePowered());
        redstoneBinding.update();
    }

    // --- Client Sound Management ---

    @Environment(EnvType.CLIENT)
    private void updateClientSound() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (!enabled || !powered || volume <= 0 || range <= 0 || !isPlayerInRange(player)) {
            stopClientSound();
            return;
        }

        if (loopSound != null && loopSound.isDone()) {
            loopSound = null;
            loopSoundIndex = -1;
        }

        if (loopSound == null || loopSoundIndex != sound) {
            stopClientSound();
            loopSoundIndex = sound;
            loopSound = new SpeakerSoundInstance(this, getSoundEvent(), sound);
            MinecraftClient.getInstance().getSoundManager().play(loopSound);
        }
    }

    @Environment(EnvType.CLIENT)
    private boolean isPlayerInRange(PlayerEntity player) {
        if (player == null || !hasWorld()) return false;
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getEyeY() - (pos.getY() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz < (double) range * range;
    }

    @Environment(EnvType.CLIENT)
    public void stopClientSound() {
        if (loopSound != null) {
            MinecraftClient.getInstance().getSoundManager().stop(loopSound);
            loopSound = null;
            loopSoundIndex = -1;
        }
    }

    @Override
    public void markRemoved() {
        if (world != null && world.isClient) {
            stopClientSound();
        }
        super.markRemoved();
    }

    // --- Sync & NBT ---

    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound tag = new NbtCompound();
        writeNbt(tag);
        return tag;
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putBoolean("enabled", enabled);
        tag.putInt("sound", sound);
        tag.putInt("range", range);
        tag.putInt("volume", volume);
        tag.putBoolean("powered", powered);
        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        sound = clampSound(tag.getInt("sound"));
        range = tag.contains("range") ? clampRange(tag.getInt("range")) : DEFAULT_RANGE;
        volume = tag.contains("volume") ? clampVolume(tag.getInt("volume")) : DEFAULT_VOLUME;
        powered = tag.getBoolean("powered");
        redstoneBinding.readNbt(tag);
    }
}
