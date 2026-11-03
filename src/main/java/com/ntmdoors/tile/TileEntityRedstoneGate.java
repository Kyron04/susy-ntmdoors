package com.ntmdoors.tile;

import com.ntmdoors.block.BlockRedstoneGate;
import com.ntmdoors.util.BlockPositions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gate logic. Redstone input and wireless events share the block's POWERED state. Wireless events
 * activate the gate for the configured duration; the next pulse extends it. The gate can be
 * inverted and can be held active until the setting is turned off from its screen.
 */
public class TileEntityRedstoneGate extends TileEntitySynced implements ITickable {

    /** Default wireless activation window, in ticks. */
    public static final int WIRELESS_PULSE_TICKS = 20;
    /** Shortest selectable activation window: one second. */
    public static final int MIN_ACTIVE_TICKS = 20;
    /** Longest selectable activation window: five minutes. */
    public static final int MAX_ACTIVE_TICKS = 20 * 60 * 5;
    /** Ticks the gate stays off so attached redstone can settle before input is checked again. */
    private static final int INPUT_SETTLE_TICKS = 1;
    /** A hold expires if its sensor stops refreshing it, e.g. because its chunk unloaded. */
    private static final int HOLD_TIMEOUT_TICKS = 60;

    private final List<BlockPos> boundDevices = new ArrayList<>();
    /**
     * Per-device hold state. A presence hold powers the gate while active; vacancy holds are an
     * AND group, so a gate linked to several out-of-bounds sensors only powers while every one of
     * them is empty.
     */
    private final Map<BlockPos, Hold> holds = new HashMap<>();

    private static final class Hold {
        final boolean vacancy;
        boolean active;
        long expiry;

        Hold(boolean vacancy, boolean active, long expiry) {
            this.vacancy = vacancy;
            this.active = active;
            this.expiry = expiry;
        }
    }
    /** Absolute world time at which the current wireless pulse ends; 0 when idle. */
    private long pulseEnd;
    /** While positive, neighbor changes are ignored so the gate cannot read its own output back. */
    private int inputSettleTicks;
    /** Temporary off-state used to test whether an inverted gate has a real external input. */
    private int invertProbeTicks;
    /** Ignore the neighbor updates caused by our own output changing. */
    private int ignoreNeighborTicks;

    /** Hold the gate active until this setting is turned off from the gate screen. */
    private boolean stayActive;
    /** Powered when there is no input, unpowered when an input is present. */
    private boolean invert;
    /** Configured wireless activation window, in ticks. */
    private int activeTicks = WIRELESS_PULSE_TICKS;

    /** Server side: adds a safe, scanner or keypad to this gate's wireless link. */
    public void bindDevice(BlockPos devicePos) {
        if (devicePos == null) return;
        BlockPos device = devicePos.toImmutable();
        if (boundDevices.contains(device)) return;
        boundDevices.add(device);
        markDirty();
    }

    /** Server side: removes one device from this gate's wireless link. */
    public void unbindDevice(BlockPos devicePos) {
        if (devicePos == null) return;
        boolean unbound = boundDevices.remove(devicePos);
        boolean unheld = holds.remove(devicePos) != null;
        if (unbound || unheld) {
            markDirty();
            if (unheld) {
                inputSettleTicks = 0;
                invertProbeTicks = 0;
                ignoreNeighborTicks = 1;
                refreshPower();
            }
        }
    }

    /**
     * Server side: a within-bounds sensor holds this gate while a qualifying entity is inside.
     * Releasing a presence hold removes it; vacancy hold state is left alone.
     */
    public void setHeld(BlockPos source, boolean held) {
        if (getWorld() == null || getWorld().isRemote || source == null) return;
        BlockPos key = source.toImmutable();
        Hold current = holds.get(key);
        if (held) {
            holds.put(key, new Hold(false, true, getWorld().getTotalWorldTime() + HOLD_TIMEOUT_TICKS));
        } else if (current != null && !current.vacancy) {
            holds.remove(key);
        } else {
            return;
        }
        applyHoldChange();
    }

    /**
     * Server side: an out-of-bounds sensor reports whether its area is empty. All vacancy sensors
     * on one gate must be empty for the gate to stay powered.
     */
    public void setVacancy(BlockPos source, boolean empty) {
        if (getWorld() == null || getWorld().isRemote || source == null) return;
        BlockPos key = source.toImmutable();
        long expiry = getWorld().getTotalWorldTime() + HOLD_TIMEOUT_TICKS;
        Hold current = holds.get(key);
        if (current == null || !current.vacancy) {
            holds.put(key, new Hold(true, empty, expiry));
        } else {
            current.active = empty;
            current.expiry = expiry;
        }
        applyHoldChange();
    }

    /** Server side: removes any hold owned by this source. */
    public void releaseHold(BlockPos source) {
        if (getWorld() == null || getWorld().isRemote || source == null) return;
        if (holds.remove(source.toImmutable()) == null) return;
        applyHoldChange();
    }

    private void applyHoldChange() {
        inputSettleTicks = 0;
        invertProbeTicks = 0;
        ignoreNeighborTicks = 1;
        markDirty();
        refreshPower();
    }

    /** True when the current holds should power the gate. */
    public boolean isHeld() {
        boolean presence = false;
        boolean hasVacancy = false;
        boolean allVacancyEmpty = true;
        long now = getWorld() == null ? 0 : getWorld().getTotalWorldTime();
        for (Hold hold : holds.values()) {
            if (hold.expiry < now) continue;
            if (hold.vacancy) {
                hasVacancy = true;
                if (!hold.active) allVacancyEmpty = false;
            } else if (hold.active) {
                presence = true;
            }
        }
        return presence || (hasVacancy && allVacancyEmpty);
    }

    /** Server side: clears both sides of every link before the gate is broken. */
    public void clearDevices() {
        World world = getWorld();
        if (world != null && !world.isRemote) {
            for (BlockPos devicePos : new ArrayList<>(boundDevices)) {
                if (!world.isBlockLoaded(devicePos)) continue;
                TileEntity te = world.getTileEntity(devicePos);
                if (te instanceof RedstoneBindingHost) {
                    ((RedstoneBindingHost) te).getRedstoneBinding().onGateRemoved(pos);
                }
            }
        }
        boundDevices.clear();
        holds.clear();
        markDirty();
    }

    // ------------------------------------------------------------ settings

    public boolean isStayActive() {
        return stayActive;
    }

    public boolean isInverted() {
        return invert;
    }

    public int getActiveSeconds() {
        return activeTicks / 20;
    }

    /** Server side: holds the gate on until switched off, ignoring signals while active. */
    public boolean setStayActive(boolean value) {
        if (getWorld() == null || getWorld().isRemote || stayActive == value) return false;
        stayActive = value;
        sync();
        if (value) {
            pulseEnd = 0;
            invertProbeTicks = 0;
            ignoreNeighborTicks = 0;
            setPowered(true);
        } else if (invert && isPoweredOn()) {
            startInvertProbe();
        } else {
            refreshPower();
            ignoreNeighborTicks = 1;
        }
        return true;
    }

    /** Server side: flips the gate between normal and inverted operation. */
    public boolean setInverted(boolean value) {
        if (getWorld() == null || getWorld().isRemote || invert == value) return false;
        invert = value;
        sync();
        if (stayActive) {
            setPowered(true);
        } else if (value && isPoweredOn()) {
            startInvertProbe();
        } else {
            refreshPower();
            ignoreNeighborTicks = 1;
        }
        return true;
    }

    /** Server side: adds or removes ticks from the activation window. */
    public boolean adjustActiveTicks(int delta) {
        if (getWorld() == null || getWorld().isRemote || delta == 0) return false;
        int next = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, activeTicks + delta));
        if (next == activeTicks) return false;
        activeTicks = next;
        if (!stayActive && isPulsing()) pulseEnd = getWorld().getTotalWorldTime() + activeTicks;
        sync();
        return true;
    }

    // ------------------------------------------------------------ activation

    /** Server side: starts a wireless activation window. */
    public void pulse() {
        if (getWorld() == null || getWorld().isRemote || stayActive) return;
        long end = getWorld().getTotalWorldTime() + activeTicks;
        if (end > pulseEnd) pulseEnd = end;
        inputSettleTicks = 0;
        invertProbeTicks = 0;
        ignoreNeighborTicks = 1;
        // An inverted gate is off while the input is present.
        setPowered(!invert);
        markDirty();
    }

    private boolean isPulsing() {
        return getWorld() != null && pulseEnd > getWorld().getTotalWorldTime();
    }

    public void onNeighborChanged() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (inputSettleTicks > 0 || invertProbeTicks > 0 || ignoreNeighborTicks > 0) return;
        if (stayActive) {
            setPowered(true);
            return;
        }
        if (isPulsing() || isHeld()) return;

        if (invert) {
            if (isPoweredOn()) startInvertProbe();
            else refreshPower();
        } else {
            setPowered(BlockRedstoneGate.isReceivingPower(getWorld(), pos));
        }
    }

    @Override
    public void update() {
        World world = getWorld();
        if (world == null || world.isRemote) return;

        if (!holds.isEmpty()) {
            long now = world.getTotalWorldTime();
            boolean changed = holds.values().removeIf(hold -> hold.expiry < now);
            if (changed) {
                refreshPower();
                ignoreNeighborTicks = 1;
            }
        }

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
        if (pulseEnd <= 0 || isPulsing()) return;

        pulseEnd = 0;
        if (isHeld()) {
            refreshPower();
            ignoreNeighborTicks = 1;
            return;
        }
        if (invert) {
            // The gate was already off during the pulse; the attached wire has settled.
            refreshPower();
            ignoreNeighborTicks = 1;
        } else {
            // Turn off first so any wire the gate was powering updates before we look for a real
            // external input. Reading the input while still powered lets a wire latch it on.
            inputSettleTicks = INPUT_SETTLE_TICKS;
            setPowered(false);
        }
    }

    @Override
    public void onLoad() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (stayActive) {
            setPowered(true);
        } else if (invert && !isPulsing()) {
            // The saved gate may be powering its own attached wire; probe before trusting input.
            startInvertProbe();
        } else {
            refreshPower();
        }
    }

    private boolean isPoweredOn() {
        World world = getWorld();
        if (world == null) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockRedstoneGate
                && state.getValue(BlockRedstoneGate.POWERED);
    }

    private void refreshPower() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (stayActive) {
            setPowered(true);
            return;
        }
        boolean active = isPulsing() || isHeld()
                || BlockRedstoneGate.isReceivingPower(getWorld(), pos);
        if (invert) ignoreNeighborTicks = 1;
        setPowered(invert ? !active : active);
    }

    private void startInvertProbe() {
        invertProbeTicks = 1;
        ignoreNeighborTicks = 0;
        setPowered(false);
    }

    private void setPowered(boolean powered) {
        World world = getWorld();
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRedstoneGate)) return;
        if (state.getValue(BlockRedstoneGate.POWERED) == powered) return;
        world.setBlockState(pos, state.withProperty(BlockRedstoneGate.POWERED, powered), 3);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("boundDevices", BlockPositions.encodeList(boundDevices));
        tag.setLong("wirelessPulseEnd", pulseEnd);
        tag.setBoolean("stayActive", stayActive);
        tag.setBoolean("invert", invert);
        tag.setInteger("activeTicks", activeTicks);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boundDevices.clear();
        boundDevices.addAll(BlockPositions.decodeList(tag.getString("boundDevices")));
        if (tag.hasKey("wirelessPulseEnd")) {
            pulseEnd = tag.getLong("wirelessPulseEnd");
        } else if (tag.hasKey("wirelessTicks") && getWorld() != null) {
            // Migrate the old remaining-ticks field from worlds saved by earlier builds.
            pulseEnd = getWorld().getTotalWorldTime() + tag.getInteger("wirelessTicks");
        } else {
            pulseEnd = 0;
        }
        stayActive = tag.getBoolean("stayActive");
        invert = tag.getBoolean("invert");
        activeTicks = tag.hasKey("activeTicks")
                ? tag.getInteger("activeTicks") : WIRELESS_PULSE_TICKS;
        activeTicks = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, activeTicks));
    }
}
