package com.ntmdoors.tile;

import com.ntmdoors.block.BlockRedstoneGate;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.TimedSources;
import com.ntmdoors.weapons.WeaponsBindings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gate logic. Redstone input and wireless events share the block's POWERED state. Wireless events
 * activate the gate for the configured duration; the next pulse extends it. The gate can be
 * inverted and can be held active until the setting is turned off from its screen.
 *
 * <p>A gate is also a binding host: a session started on it links other gates and doors, and its
 * output is mirrored to those receivers until the link is removed.</p>
 */
public class TileEntityRedstoneGate extends TileEntitySynced implements ITickable, RedstoneBindingHost {

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

    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);
    private final List<BlockPos> boundDevices = new ArrayList<>();
    /** Source gates currently driving this gate. */
    private final TimedSources linkedInputs = new TimedSources();
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
    private boolean systemIdle;
    /** Temporary off-state used to test whether an inverted gate has a real external input. */
    private int invertProbeTicks;
    /** Ignore the neighbor updates caused by our own output changing. */
    private int ignoreNeighborTicks;

    /** Security-net switch. Off forces the output low and ignores input and wireless pulses. */
    private boolean enabled = true;
    /** Hold the gate active until this setting is turned off from the gate screen. */
    private boolean stayActive;
    /** Powered when there is no input, unpowered when an input is present. */
    private boolean invert;
    /** Configured wireless activation window, in ticks. */
    private int activeTicks = WIRELESS_PULSE_TICKS;

    // ------------------------------------------------------------ binding host

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
    public void sendRedstoneMessage(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    /**
     * Server side: a linked gate reports its output. Only powered sources are stored; a false
     * report removes the source. Expiry lets a receiver recover if the source disappears without
     * sending its falling edge.
     */
    public void setLinkedPower(BlockPos source, boolean powered) {
        World world = getWorld();
        if (world == null || world.isRemote || source == null) return;
        TimedSources.Update change = linkedInputs.apply(source, powered, world.getTotalWorldTime(),
                RedstoneBinding.LINK_TIMEOUT_TICKS);
        if (change == TimedSources.Update.NONE || change == TimedSources.Update.REFRESHED) return;
        inputSettleTicks = 0;
        invertProbeTicks = 0;
        ignoreNeighborTicks = 1;
        markDirty();
        refreshPower();
    }

    private boolean isLinkedPowered() {
        World world = getWorld();
        return world != null && linkedInputs.isActive(world.getTotalWorldTime());
    }

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
            WeaponsBindings.onGateRemoved(world, pos);
            for (BlockPos devicePos : new ArrayList<>(boundDevices)) {
                if (!world.isBlockLoaded(devicePos)) continue;
                TileEntity te = world.getTileEntity(devicePos);
                if (te instanceof RedstoneBindingHost) {
                    ((RedstoneBindingHost) te).getRedstoneBinding().onGateRemoved(pos);
                }
            }
            redstoneBinding.clearGates();
        }
        boundDevices.clear();
        holds.clear();
        linkedInputs.clear();
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
            // Latch only a gate that already has an input. Enabling the setting must not
            // power an idle gate by itself.
            refreshPower();
        } else if (invert && isPoweredOn()) {
            startInvertProbe();
        } else {
            // Drop the output first and let the attached wire settle for a tick. Reading the input
            // while the gate is still powering its own top dust would keep it latched on.
            inputSettleTicks = INPUT_SETTLE_TICKS;
            ignoreNeighborTicks = 1;
            setPowered(false);
        }
        return true;
    }

    /** Server side: flips the gate between normal and inverted operation. */
    public boolean setInverted(boolean value) {
        if (getWorld() == null || getWorld().isRemote || invert == value) return false;
        invert = value;
        sync();
        if (stayActive) {
            refreshPower();
            ignoreNeighborTicks = 1;
        } else if (value && isPoweredOn()) {
            startInvertProbe();
        } else if (!value && isPoweredOn()) {
            // Turning inversion off while the gate is powered is the same feedback risk as
            // leaving Stay active: drop the output before re-reading the wire.
            inputSettleTicks = INPUT_SETTLE_TICKS;
            ignoreNeighborTicks = 1;
            setPowered(false);
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

    public boolean isEnabled() {
        return enabled;
    }

    /** Server side: switches the gate on or off from the Security net. */
    public boolean setEnabled(boolean value) {
        if (getWorld() == null || getWorld().isRemote || enabled == value) return false;
        enabled = value;
        if (!value) {
            pulseEnd = 0;
            holds.clear();
            inputSettleTicks = 0;
            invertProbeTicks = 0;
            ignoreNeighborTicks = 1;
            systemIdle = true;
            setPowered(false);
        } else {
            ignoreNeighborTicks = 1;
            refreshPower();
        }
        sync();
        return true;
    }

    /** Server side: starts a wireless activation window. */
    public void pulse() {
        if (getWorld() == null || getWorld().isRemote || stayActive) return;
        if (isSystemIdle()) return;
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

    private boolean isSystemIdle() {
        World world = getWorld();
        return world != null && (!enabled || SecurityEvents.isShutdown(world, pos)
                || SecurityEvents.isGateDisabled(world, pos));
    }

    public void onNeighborChanged() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (isSystemIdle()) return;
        if (inputSettleTicks > 0 || invertProbeTicks > 0 || ignoreNeighborTicks > 0) return;
        if (stayActive && !invert) {
            // Keep a latched gate on, but only switch an idle gate on when a real input arrives.
            if (BlockRedstoneGate.isReceivingPower(getWorld(), pos) || isPulsing() || isHeld()
                    || isLinkedPowered()) {
                setPowered(true);
            }
            return;
        }
        if (isPulsing() || isHeld() || isLinkedPowered()) return;

        if (invert) {
            if (isPoweredOn()) startInvertProbe();
            else refreshPower();
        } else {
            boolean powered = BlockRedstoneGate.isReceivingPower(getWorld(), pos);
            if (!powered && isPoweredOn()) {
                // Drop the output first and ignore the neighbour feedback for a moment. Dust
                // sitting directly on the gate can otherwise keep the old output alive long
                // enough to feed the gate back on.
                inputSettleTicks = INPUT_SETTLE_TICKS;
                ignoreNeighborTicks = 1;
                setPowered(false);
            } else {
                setPowered(powered);
            }
        }
    }

    @Override
    public void update() {
        World world = getWorld();
        if (world == null || world.isRemote) return;
        redstoneBinding.update();

        if (isSystemIdle()) {
            if (!systemIdle) {
                systemIdle = true;
                pulseEnd = 0;
                inputSettleTicks = 0;
                invertProbeTicks = 0;
                holds.clear();
                linkedInputs.clear();
            }
            if (isPoweredOn()) setPowered(false);
            return;
        }
        if (systemIdle) {
            systemIdle = false;
            refreshPower();
            ignoreNeighborTicks = 1;
        }

        if (!holds.isEmpty()) {
            long now = world.getTotalWorldTime();
            boolean changed = holds.values().removeIf(hold -> hold.expiry < now);
            if (changed) {
                refreshPower();
                ignoreNeighborTicks = 1;
            }
        }
        if (linkedInputs.prune(world.getTotalWorldTime())) {
            refreshPower();
            ignoreNeighborTicks = 1;
        }

        if (ignoreNeighborTicks > 0) ignoreNeighborTicks--;

        // Keep receivers alive while this gate is on so a chunk that loads after the output edge
        // still adopts the signal, and a stale receiver times out only after the source is gone.
        if (redstoneBinding.hasLinks() && isPoweredOn()
                && (world.getTotalWorldTime() & 19L) == 0L) {
            redstoneBinding.applyGateOutput(true);
        }

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
        if (isHeld() || isLinkedPowered()) {
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
        if (!enabled) {
            systemIdle = true;
            setPowered(false);
        } else if (invert && !isPulsing()) {
            // The saved gate may be powering its own attached wire; probe before trusting input.
            startInvertProbe();
        } else {
            refreshPower();
        }
        // Seed receivers from the saved output state; persisted inputs cover chunk-load order.
        redstoneBinding.applyGateOutput(isPoweredOn());
    }

    public boolean isPoweredOn() {
        World world = getWorld();
        if (world == null) return false;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockRedstoneGate
                && state.getValue(BlockRedstoneGate.POWERED);
    }

    private void refreshPower() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (isSystemIdle()) {
            setPowered(false);
            return;
        }
        boolean active = isPulsing() || isHeld() || isLinkedPowered()
                || BlockRedstoneGate.isReceivingPower(getWorld(), pos);
        if (stayActive && !invert) {
            // A latched gate stays on while its input is gone. An idle gate only turns on when
            // an input actually arrives.
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

    private void setPowered(boolean powered) {
        World world = getWorld();
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRedstoneGate)) return;
        if (state.getValue(BlockRedstoneGate.POWERED) == powered) return;
        world.setBlockState(pos, state.withProperty(BlockRedstoneGate.POWERED, powered), 3);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        // Dust sitting on the gate's top face is the one cell that can keep a stale power value
        // through the generic neighbour pass, so give it a direct recalculation after the state
        // change. The update cascades through the rest of the connected wire.
        BlockPos above = pos.up();
        if (world.isBlockLoaded(above)) {
            world.neighborChanged(above, state.getBlock(), pos);
        }
        // Weapons Relay Key links fire on the rising edge of the gate output.
        if (powered) WeaponsBindings.onGatePowered(world, pos);
        // Linked gates and doors follow every output edge.
        redstoneBinding.applyGateOutput(powered);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("boundDevices", BlockPositions.encodeList(boundDevices));
        tag.setLong("wirelessPulseEnd", pulseEnd);
        tag.setBoolean("enabled", enabled);
        tag.setBoolean("stayActive", stayActive);
        tag.setBoolean("invert", invert);
        tag.setInteger("activeTicks", activeTicks);
        tag.setString("linkedInputs", BlockPositions.encodeList(linkedInputs.keys()));
        redstoneBinding.writeToNBT(tag);
        return tag;
    }

    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        super.writeSyncTag(tag);
        // Gate and door link lists stay in the sync tag so the Redstone Monocle can draw them.
        tag.removeTag("linkedInputs");
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        boundDevices.clear();
        boundDevices.addAll(BlockPositions.decodeList(tag.getString("boundDevices")));
        linkedInputs.clear();
        if (tag.hasKey("linkedInputs") && getWorld() != null) {
            linkedInputs.load(BlockPositions.decodeList(tag.getString("linkedInputs")),
                    getWorld().getTotalWorldTime() + RedstoneBinding.LINK_TIMEOUT_TICKS);
        }
        redstoneBinding.readFromNBT(tag);
        if (tag.hasKey("wirelessPulseEnd")) {
            pulseEnd = tag.getLong("wirelessPulseEnd");
        } else if (tag.hasKey("wirelessTicks") && getWorld() != null) {
            // Migrate the old remaining-ticks field from worlds saved by earlier builds.
            pulseEnd = getWorld().getTotalWorldTime() + tag.getInteger("wirelessTicks");
        } else {
            pulseEnd = 0;
        }
        enabled = !tag.hasKey("enabled") || tag.getBoolean("enabled");
        stayActive = tag.getBoolean("stayActive");
        invert = tag.getBoolean("invert");
        activeTicks = tag.hasKey("activeTicks")
                ? tag.getInteger("activeTicks") : WIRELESS_PULSE_TICKS;
        activeTicks = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_ACTIVE_TICKS, activeTicks));
    }
}
