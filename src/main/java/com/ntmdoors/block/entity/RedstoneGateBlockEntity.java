package com.ntmdoors.block.entity;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.RedstoneGateBlock;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.RedstoneBinding;
import com.ntmdoors.util.TimedSources;
import com.ntmdoors.weapons.WeaponsBindings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RedstoneGateBlockEntity extends BlockEntity implements RedstoneBindingHost {

    public static final int WIRELESS_PULSE_TICKS = 20;
    public static final int MIN_ACTIVE_TICKS = 20;
    public static final int MAX_ACTIVE_TICKS = 20 * 60 * 5; // 6000 ticks = 5 min
    private static final int INPUT_SETTLE_TICKS = 1;
    private static final int HOLD_TIMEOUT_TICKS = 60;

    public static final class Hold {
        public final boolean vacancy;
        public boolean active;
        public long expiry;

        public Hold(boolean vacancy, boolean active, long expiry) {
            this.vacancy = vacancy;
            this.active = active;
            this.expiry = expiry;
        }
    }

    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);
    private final List<BlockPos> boundDevices = new ArrayList<>();
    private final TimedSources linkedInputs = new TimedSources();
    private final Map<BlockPos, Hold> holds = new HashMap<>();

    private long pulseEnd;
    private int inputSettleTicks;
    private boolean systemIdle;
    private int invertProbeTicks;
    private int ignoreNeighborTicks;

    private boolean enabled = true;
    private boolean stayActive;
    private boolean invert;
    private boolean manualPower;
    private int activeTicks = WIRELESS_PULSE_TICKS;

    public RedstoneGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_GATE, pos, state);
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
        markDirty();
    }

    @Override
    public void sendRedstoneMessage(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }

    public void setLinkedPower(BlockPos source, boolean powered) {
        if (world == null || world.isClient || source == null) return;
        TimedSources.Update change = linkedInputs.apply(source, powered, world.getTime(), RedstoneBinding.LINK_TIMEOUT_TICKS);
        if (change == TimedSources.Update.NONE || change == TimedSources.Update.REFRESHED) return;
        refreshPower();
    }

    public boolean isLinkedPowered() {
        return world != null && linkedInputs.isActive(world.getTime());
    }

    public void setHeld(BlockPos source, boolean active) {
        if (world == null || world.isClient || source == null) return;
        holds.put(source.toImmutable(), new Hold(false, active, world.getTime() + HOLD_TIMEOUT_TICKS));
        refreshPower();
    }

    public void setVacancy(BlockPos source, boolean active) {
        if (world == null || world.isClient || source == null) return;
        holds.put(source.toImmutable(), new Hold(true, active, world.getTime() + HOLD_TIMEOUT_TICKS));
        refreshPower();
    }

    public void releaseHold(BlockPos source) {
        if (source != null && holds.remove(source) != null) {
            refreshPower();
        }
    }

    public boolean isHeld() {
        if (holds.isEmpty()) return false;
        boolean hasVacancy = false;
        boolean vacancyAllActive = true;
        long time = world != null ? world.getTime() : 0L;
        for (Hold hold : holds.values()) {
            if (hold.expiry < time) continue;
            if (hold.vacancy) {
                hasVacancy = true;
                if (!hold.active) vacancyAllActive = false;
            } else {
                if (hold.active) return true;
            }
        }
        return hasVacancy && vacancyAllActive;
    }

    private boolean pruneHolds(long time) {
        boolean changed = false;
        Iterator<Map.Entry<BlockPos, Hold>> it = holds.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiry < time) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    public void bindDevice(BlockPos source) {
        if (source != null && !boundDevices.contains(source)) {
            boundDevices.add(source.toImmutable());
            markDirty();
        }
    }

    public void unbindDevice(BlockPos source) {
        if (source != null && boundDevices.remove(source)) {
            markDirty();
        }
    }

    public void clearDevices() {
        redstoneBinding.clearGates();
        boundDevices.clear();
        holds.clear();
        linkedInputs.clear();
        WeaponsBindings.onGateRemoved(world, pos);
        markDirty();
    }

    public List<BlockPos> getBoundDevices() {
        return Collections.unmodifiableList(boundDevices);
    }

    // --- Control and Settings ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        refreshPower();
        markDirty();
    }

    public boolean isManualPower() {
        return manualPower;
    }

    public void setManualPower(boolean manualPower) {
        if (this.manualPower == manualPower) return;
        this.manualPower = manualPower;
        refreshPower();
        markDirty();
        sync();
    }

    public boolean isStayActive() {
        return stayActive;
    }

    public void setStayActive(boolean val) {
        if (stayActive == val) return;
        stayActive = val;
        if (!stayActive) {
            if (invert) startInvertProbe();
            else {
                inputSettleTicks = INPUT_SETTLE_TICKS;
                setPowered(false);
            }
        } else {
            refreshPower();
        }
        markDirty();
        sync();
    }

    public boolean isInverted() {
        return invert;
    }

    public void setInverted(boolean val) {
        if (invert == val) return;
        invert = val;
        if (invert) startInvertProbe();
        else refreshPower();
        markDirty();
        sync();
    }

    public int getActiveTicks() {
        return activeTicks;
    }

    public void adjustActiveTicks(int delta) {
        this.activeTicks = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, this.activeTicks + delta));
        markDirty();
        sync();
    }

    public boolean isPulsing() {
        return pulseEnd > 0 && world != null && world.getTime() < pulseEnd;
    }

    public boolean isSystemIdle() {
        return !enabled || systemIdle;
    }

    public void pulse() {
        if (world == null || world.isClient || stayActive || isSystemIdle()) return;
        long end = world.getTime() + activeTicks;
        if (end > pulseEnd) pulseEnd = end;
        inputSettleTicks = 0;
        invertProbeTicks = 0;
        ignoreNeighborTicks = 1;
        setPowered(!invert);
        markDirty();
    }

    public void onNeighborChanged() {
        if (world == null || world.isClient) return;
        if (ignoreNeighborTicks > 0) return;
        refreshPower();
    }

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        redstoneBinding.update();

        if (ignoreNeighborTicks > 0) ignoreNeighborTicks--;
        if (inputSettleTicks > 0) {
            if (--inputSettleTicks == 0) refreshPower();
            return;
        }
        if (invertProbeTicks > 0) {
            if (--invertProbeTicks == 0) {
                refreshPower();
                ignoreNeighborTicks = 1;
            }
            return;
        }

        boolean holdsChanged = pruneHolds(world.getTime());
        if (pulseEnd <= 0 || world.getTime() < pulseEnd) {
            if (holdsChanged) refreshPower();
            return;
        }

        pulseEnd = 0;
        if (invert) {
            refreshPower();
            ignoreNeighborTicks = 1;
        } else {
            inputSettleTicks = INPUT_SETTLE_TICKS;
            setPowered(false);
        }
    }

    public boolean isPoweredOn() {
        if (world == null) return false;
        BlockState state = world.getBlockState(pos);
        return state.isOf(ModBlocks.REDSTONE_GATE) && state.get(RedstoneGateBlock.POWERED);
    }

    private void refreshPower() {
        if (world == null || world.isClient) return;
        if (isSystemIdle()) {
            setPowered(false);
            return;
        }
        boolean active = isPulsing() || isHeld() || isLinkedPowered() || manualPower
                || RedstoneGateBlock.isReceivingPower(world, pos);
        if (stayActive && !invert) {
            if (active) setPowered(true);
            return;
        }
        if (invert) ignoreNeighborTicks = 1;
        setPowered(invert ? !active : active);
    }

    private void startInvertProbe() {
        invertProbeTicks = 1;
        ignoreNeighborTicks = 0;
        setPowered(false);
    }

    public void setPowered(boolean powered) {
        if (world == null || world.isClient) return;
        BlockState state = getCachedState();
        if (!state.isOf(ModBlocks.REDSTONE_GATE)) return;
        if (state.get(RedstoneGateBlock.POWERED) == powered) return;

        world.setBlockState(pos, state.with(RedstoneGateBlock.POWERED, powered), Block.NOTIFY_ALL);
        world.updateNeighbors(pos, state.getBlock());
        BlockPos above = pos.up();
        if (world.isChunkLoaded(above)) {
            world.updateNeighbors(above, state.getBlock());
        }
        redstoneBinding.applyGateOutput(powered);
        if (powered) {
            WeaponsBindings.onGatePowered(world, pos);
        }
    }

    // --- NBT Serialization ---

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (!boundDevices.isEmpty()) tag.putString("boundDevices", BlockPositions.encodeList(boundDevices));
        tag.putLong("wirelessPulseEnd", pulseEnd);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("stayActive", stayActive);
        tag.putBoolean("invert", invert);
        tag.putBoolean("manualPower", manualPower);
        tag.putInt("activeTicks", activeTicks);
        if (!linkedInputs.isEmpty()) tag.putString("linkedInputs", BlockPositions.encodeList(linkedInputs.keys()));
        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        boundDevices.clear();
        boundDevices.addAll(BlockPositions.decodeList(tag.getString("boundDevices")));
        linkedInputs.clear();
        if (tag.contains("linkedInputs") && world != null) {
            linkedInputs.load(BlockPositions.decodeList(tag.getString("linkedInputs")),
                    world.getTime() + RedstoneBinding.LINK_TIMEOUT_TICKS);
        }
        redstoneBinding.readNbt(tag);
        pulseEnd = tag.getLong("wirelessPulseEnd");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        stayActive = tag.getBoolean("stayActive");
        invert = tag.getBoolean("invert");
        manualPower = tag.getBoolean("manualPower");
        activeTicks = tag.contains("activeTicks") ? tag.getInt("activeTicks") : WIRELESS_PULSE_TICKS;
        activeTicks = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, activeTicks));
    }

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
}
