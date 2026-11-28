// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.tile;

import com.ntmdoors.util.BlockPositions;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A wireless link owned by a device or a Redstone Gate. Device hosts broadcast events to one or
 * more gates; a gate host mirrors its output to linked gates and doors. The link is stored on the
 * device and the target where applicable, so either side can be broken without leaving a live link
 * behind. A host can drive several receivers and a gate can receive from several hosts.
 */
public final class RedstoneBinding {

    /** A binding session the player walks away from expires after a minute. */
    private static final int BINDING_TICKS = 20 * 60;
    /** Ticks a linked gate or door stays driven without a refresh from its source gate. */
    public static final int LINK_TIMEOUT_TICKS = 100;

    private final TileEntity host;
    private final RedstoneBindingHost callbacks;
    private final List<BlockPos> gates = new ArrayList<>();
    /** Doors linked from a gate host. Device hosts never fill this list. */
    private final List<BlockPos> doors = new ArrayList<>();
    /** Emancipation Grills linked from a gate host. */
    private final List<BlockPos> grills = new ArrayList<>();

    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;
    /** How the host currently holds its linked gates. */
    private enum HoldMode { NONE, PRESENCE, VACANCY }

    private HoldMode holdMode = HoldMode.NONE;
    private boolean holdActive;

    public RedstoneBinding(TileEntity host) {
        if (!(host instanceof RedstoneBindingHost)) {
            throw new IllegalArgumentException("RedstoneBinding host must implement RedstoneBindingHost");
        }
        this.host = host;
        this.callbacks = (RedstoneBindingHost) host;
    }

    public boolean isBindingFor(@Nullable EntityPlayer player) {
        return player != null && bindingPlayer != null && bindingPlayer.equals(player.getUniqueID());
    }

    /** True when this binding belongs to a Redstone Gate and can drive other gates and doors. */
    public boolean isGateHost() {
        return host instanceof TileEntityRedstoneGate;
    }

    public boolean isHost(TileEntity tile) {
        return tile == host;
    }

    public boolean hasLinks() {
        return !gates.isEmpty() || !doors.isEmpty() || !grills.isEmpty();
    }

    /** Read-only view of the gates this host drives, used by the Redstone Monocle client overlay. */
    public List<BlockPos> getGates() {
        return Collections.unmodifiableList(gates);
    }

    /** Read-only view of the doors this gate host drives, used by the Redstone Monocle overlay. */
    public List<BlockPos> getDoors() {
        return Collections.unmodifiableList(doors);
    }

    /** Read-only view of the grills this gate host drives. */
    public List<BlockPos> getGrills() {
        return Collections.unmodifiableList(grills);
    }

    /**
     * True when linking this host to {@code candidate} would close a loop. Gate links are
     * directional, so a cycle would let a receiver hold its own source on forever.
     */
    public boolean createsLoop(TileEntityRedstoneGate candidate) {
        World world = host.getWorld();
        if (world == null || world.isRemote || candidate == null) return false;
        BlockPos target = host.getPos();
        if (candidate.getPos().equals(target)) return true;
        return reaches(candidate, target, world, new HashSet<>());
    }

    private static boolean reaches(TileEntityRedstoneGate from, BlockPos target, World world,
                                   HashSet<BlockPos> seen) {
        if (!seen.add(from.getPos())) return false;
        for (BlockPos next : from.getRedstoneBinding().gates) {
            if (next.equals(target)) return true;
            if (!world.isBlockLoaded(next)) continue;
            TileEntity te = world.getTileEntity(next);
            if (te instanceof TileEntityRedstoneGate
                    && reaches((TileEntityRedstoneGate) te, target, world, seen)) {
                return true;
            }
        }
        return false;
    }

    /** Server side: starts or finishes the wireless binding session for this player. */
    public void toggle(EntityPlayer player) {
        if (host.getWorld() == null || host.getWorld().isRemote || player == null) return;
        if (isBindingFor(player)) {
            stop(player);
            return;
        }
        if (bindingPlayer != null) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_busy");
            return;
        }

        cancelOtherSessions(host.getWorld(), player, host);
        bindingPlayer = player.getUniqueID();
        bindingTicks = BINDING_TICKS;
        callbacks.sendRedstoneMessage(player, isGateHost()
                ? "ntmdoors.msg.redstone_gate_binding_on"
                : "ntmdoors.msg.redstone_binding_on");
    }

    /** Ends the session, dropping nothing; already linked gates stay linked. */
    public void stop(@Nullable EntityPlayer player) {
        if (bindingPlayer == null) return;
        bindingPlayer = null;
        bindingTicks = 0;
        if (player != null) callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_off");
    }

    /** Links or unlinks one gate for the active binding session. */
    public boolean link(EntityPlayer player, TileEntityRedstoneGate gate) {
        World world = host.getWorld();
        if (world == null || world.isRemote || player == null || gate == null) return false;
        if (!isBindingFor(player)) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_required");
            return false;
        }
        if (gate == host) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_self");
            return false;
        }
        if (gate.getWorld() != world) return false;

        BlockPos gatePos = gate.getPos().toImmutable();
        if (gates.remove(gatePos)) {
            gate.setLinkedPower(host.getPos(), false);
            gate.unbindDevice(host.getPos());
            callbacks.onRedstoneBindingChanged();
            callbacks.syncRedstoneState();
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_unlinked");
            return true;
        }

        if (!gates.contains(gatePos)) gates.add(gatePos);
        gate.bindDevice(host.getPos());
        if (isGateHost()) {
            if (createsLoop(gate)) {
                gates.remove(gatePos);
                gate.unbindDevice(host.getPos());
                callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_loop");
                return false;
            }
            gate.setLinkedPower(host.getPos(), ((TileEntityRedstoneGate) host).isPoweredOn());
        } else {
            applyHoldTo(gate);
        }
        callbacks.onRedstoneBindingChanged();
        callbacks.syncRedstoneState();
        callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_linked");
        completeLinkSession();
        return true;
    }

    /** Links or unlinks one door for an active gate-host session. */
    public boolean linkDoor(EntityPlayer player, TileEntityDoorGeneric door) {
        World world = host.getWorld();
        if (world == null || world.isRemote || player == null || door == null) return false;
        if (!isGateHost()) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_door_gate_only");
            return false;
        }
        if (!isBindingFor(player)) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_required");
            return false;
        }
        if (door.getWorld() != world) return false;
        if (door.isSecurityDoor() && !door.isOwnedBy(player)) {
            callbacks.sendRedstoneMessage(player, door.getOwnerId() == null
                    ? "ntmdoors.msg.security_redstone_no_owner"
                    : "ntmdoors.msg.security_redstone_link_owner_only");
            return false;
        }
        if (!door.acceptsRedstone()) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_door_security");
            return false;
        }
        if (door.isBreached()) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_door_breached");
            return false;
        }

        BlockPos doorPos = door.getPos().toImmutable();
        if (doors.remove(doorPos)) {
            door.setLinkedPower(host.getPos(), false);
            door.onGateUnlinked(host.getPos());
            callbacks.onRedstoneBindingChanged();
            callbacks.syncRedstoneState();
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_door_unlinked");
            return true;
        }

        if (!doors.contains(doorPos)) doors.add(doorPos);
        door.onGateLinked(host.getPos());
        door.setLinkedPower(host.getPos(), ((TileEntityRedstoneGate) host).isPoweredOn());
        callbacks.onRedstoneBindingChanged();
        callbacks.syncRedstoneState();
        callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_door_linked");
        completeLinkSession();
        return true;
    }

    /** Links or unlinks one Emancipation Grill for an active gate-host session. */
    public boolean linkGrill(EntityPlayer player, TileEntityEmancipationGrill grill) {
        World world = host.getWorld();
        if (world == null || world.isRemote || player == null || grill == null) return false;
        if (!isGateHost()) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_grill_gate_only");
            return false;
        }
        if (!isBindingFor(player)) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_required");
            return false;
        }

        TileEntityEmancipationGrill core = grill.getSecurityCore();
        if (core.getWorld() != world) return false;
        if (core.getOwnerId() != null && !core.isOwnedBy(player)) {
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_grill_owner_only");
            return false;
        }

        BlockPos grillPos = core.getPos().toImmutable();
        if (grills.remove(grillPos)) {
            core.setLinkedPower(host.getPos(), false);
            core.onGateUnlinked(host.getPos());
            callbacks.onRedstoneBindingChanged();
            callbacks.syncRedstoneState();
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_grill_unlinked");
            return true;
        }

        if (!grills.contains(grillPos)) grills.add(grillPos);
        core.onGateLinked(host.getPos(), ((TileEntityRedstoneGate) host).isPoweredOn());
        callbacks.onRedstoneBindingChanged();
        callbacks.syncRedstoneState();
        callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_grill_linked");
        completeLinkSession();
        return true;
    }

    /**
     * A successful link is one action: end the session so the next click starts a fresh bind
     * instead of being treated as part of the old one. Existing links are untouched.
     */
    private void completeLinkSession() {
        stop(null);
    }

    /**
     * Server side: mirrors the host gate's output onto every linked gate, door and grill. Called on
     * each output edge and periodically while the source is on so a receiver loaded after the edge
     * still picks the signal up.
     */
    public void applyGateOutput(boolean powered) {
        World world = host.getWorld();
        if (world == null || world.isRemote || !isGateHost()) return;

        BlockPos source = host.getPos();
        boolean changed = false;
        Iterator<BlockPos> gateIterator = gates.iterator();
        while (gateIterator.hasNext()) {
            BlockPos gatePos = gateIterator.next();
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                ((TileEntityRedstoneGate) te).setLinkedPower(source, powered);
            } else {
                gateIterator.remove();
                changed = true;
            }
        }
        Iterator<BlockPos> doorIterator = doors.iterator();
        while (doorIterator.hasNext()) {
            BlockPos doorPos = doorIterator.next();
            if (!world.isBlockLoaded(doorPos)) continue;
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
                if (door.acceptsRedstone() && !door.isBreached()) {
                    door.setLinkedPower(source, powered);
                } else {
                    door.setLinkedPower(source, false);
                    door.onGateUnlinked(source);
                    doorIterator.remove();
                    changed = true;
                }
            } else {
                doorIterator.remove();
                changed = true;
            }
        }
        Iterator<BlockPos> grillIterator = grills.iterator();
        while (grillIterator.hasNext()) {
            BlockPos grillPos = grillIterator.next();
            if (!world.isBlockLoaded(grillPos)) continue;
            TileEntity te = world.getTileEntity(grillPos);
            if (te instanceof TileEntityEmancipationGrill) {
                ((TileEntityEmancipationGrill) te).setLinkedPower(source, powered);
            } else {
                grillIterator.remove();
                changed = true;
            }
        }
        if (changed) callbacks.syncRedstoneState();
    }

    /**
     * Server side: presence hold. Reapplying the same state is intentional; it lets a gate that
     * was just loaded adopt the current hold again.
     */
    public void setHeld(boolean held) {
        if (held) {
            holdMode = HoldMode.PRESENCE;
            holdActive = true;
        } else if (holdMode == HoldMode.PRESENCE) {
            holdMode = HoldMode.NONE;
            holdActive = false;
        } else {
            return;
        }
        applyHold();
    }

    /** Server side: vacancy hold, where every linked out-of-bounds sensor must be empty. */
    public void setVacancy(boolean empty) {
        holdMode = HoldMode.VACANCY;
        holdActive = empty;
        applyHold();
    }

    /** Server side: releases any hold this device owns. */
    public void clearHold() {
        holdMode = HoldMode.NONE;
        holdActive = false;
        applyHold();
    }

    private void applyHold() {
        World world = host.getWorld();
        if (world == null || world.isRemote) return;

        for (BlockPos gatePos : gates) {
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) applyHoldTo((TileEntityRedstoneGate) te);
        }
    }

    private void applyHoldTo(TileEntityRedstoneGate gate) {
        BlockPos self = host.getPos();
        if (holdMode == HoldMode.NONE) {
            gate.releaseHold(self);
        } else if (holdMode == HoldMode.PRESENCE) {
            if (holdActive) gate.setHeld(self, true);
            else gate.releaseHold(self);
        } else {
            gate.setVacancy(self, holdActive);
        }
    }

    /** Server side: pulses every loaded gate in this link. */
    public void fire() {
        pulseGates(false);
    }

    /** Server side: pulses every loaded gate for exactly one tick. */
    public void fireOneTick() {
        pulseGates(true);
        pulseFieldControllerGates();
    }

    /**
     * A gate that drives this grill's field is also a linked gate, so event outputs should reach it
     * even when the player linked it from the gate's own binding session.
     */
    private void pulseFieldControllerGates() {
        World world = host.getWorld();
        if (world == null || world.isRemote) return;
        if (!(host instanceof TileEntityEmancipationGrill)) return;
        BlockPos grillPos = host.getPos().toImmutable();
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (!(te instanceof TileEntityRedstoneGate)) continue;
            TileEntityRedstoneGate gate = (TileEntityRedstoneGate) te;
            if (!gate.getRedstoneBinding().getGrills().contains(grillPos)) continue;
            if (gates.contains(gate.getPos())) continue;
            gate.pulseOnce();
        }
    }

    private void pulseGates(boolean oneTick) {
        World world = host.getWorld();
        if (world == null || world.isRemote || gates.isEmpty()) return;

        boolean changed = false;
        Iterator<BlockPos> it = gates.iterator();
        while (it.hasNext()) {
            BlockPos gatePos = it.next();
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                TileEntityRedstoneGate gate = (TileEntityRedstoneGate) te;
                if (oneTick) gate.pulseOnce();
                else gate.pulse();
            } else {
                it.remove();
                changed = true;
            }
        }
        if (changed) callbacks.syncRedstoneState();
    }

    /** Server side: removes every reciprocal gate, door and grill link. Used when this device is broken. */
    public void clearGates() {
        World world = host.getWorld();
        if (world == null || world.isRemote) {
            gates.clear();
            doors.clear();
            grills.clear();
            return;
        }
        BlockPos source = host.getPos();
        for (BlockPos gatePos : new ArrayList<>(gates)) {
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                TileEntityRedstoneGate gate = (TileEntityRedstoneGate) te;
                gate.releaseHold(source);
                gate.setLinkedPower(source, false);
                gate.unbindDevice(source);
            }
        }
        for (BlockPos doorPos : new ArrayList<>(doors)) {
            if (!world.isBlockLoaded(doorPos)) continue;
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
                door.setLinkedPower(source, false);
                door.onGateUnlinked(source);
            }
        }
        for (BlockPos grillPos : new ArrayList<>(grills)) {
            if (!world.isBlockLoaded(grillPos)) continue;
            TileEntity te = world.getTileEntity(grillPos);
            if (te instanceof TileEntityEmancipationGrill) {
                TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
                grill.setLinkedPower(source, false);
                grill.onGateUnlinked(source);
            }
        }
        gates.clear();
        doors.clear();
        grills.clear();
        holdMode = HoldMode.NONE;
        holdActive = false;
        callbacks.syncRedstoneState();
    }

    /** Called by a broken gate: drop the stale link without touching the gate. */
    public void onGateRemoved(BlockPos gatePos) {
        if (gatePos != null && gates.remove(gatePos)) callbacks.syncRedstoneState();
    }

    /** Called by a broken door: drop its link from this gate binding. */
    public void onDoorRemoved(BlockPos doorPos) {
        if (doorPos != null && doors.remove(doorPos)) callbacks.syncRedstoneState();
    }

    /** Called by a broken grill: drop its link from this gate binding. */
    public void onGrillRemoved(BlockPos grillPos) {
        if (grillPos != null && grills.remove(grillPos)) callbacks.syncRedstoneState();
    }

    /** Removes a broken grill from every loaded gate's sink list. */
    public static void removeGrillLinks(World world, BlockPos grillPos) {
        if (world == null || grillPos == null || world.isRemote) return;
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityRedstoneGate)) continue;
            ((TileEntityRedstoneGate) te).getRedstoneBinding().onGrillRemoved(grillPos);
        }
    }

    /** Restores a loaded grill's saved gate link, regardless of chunk load order. */
    public static void restoreGrillLinks(World world, BlockPos grillPos) {
        if (world == null || grillPos == null || world.isRemote) return;
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityRedstoneGate)) continue;
            TileEntityRedstoneGate gate = (TileEntityRedstoneGate) te;
            if (!gate.getRedstoneBinding().grills.contains(grillPos)) continue;
            TileEntity grillTe = world.getTileEntity(grillPos);
            if (grillTe instanceof TileEntityEmancipationGrill) {
                ((TileEntityEmancipationGrill) grillTe)
                        .restoreGateLink(gate.getPos(), gate.isPoweredOn());
            }
        }
    }

    public void update() {
        if (bindingPlayer == null) return;
        World world = host.getWorld();
        EntityPlayer player = world == null ? null : world.getPlayerEntityByUUID(bindingPlayer);
        if (player == null || --bindingTicks <= 0) stop(null);
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("redstoneGates", BlockPositions.encodeList(gates));
        tag.setString("redstoneDoors", BlockPositions.encodeList(doors));
        tag.setString("redstoneGrills", BlockPositions.encodeList(grills));
    }

    public void writeSyncTag(NBTTagCompound tag) {
        tag.setString("redstoneGates", BlockPositions.encodeList(gates));
        tag.setString("redstoneDoors", BlockPositions.encodeList(doors));
        tag.setString("redstoneGrills", BlockPositions.encodeList(grills));
    }

    public void readFromNBT(NBTTagCompound tag) {
        gates.clear();
        doors.clear();
        grills.clear();
        holdMode = HoldMode.NONE;
        holdActive = false;
        if (tag.hasKey("redstoneGates")) {
            gates.addAll(BlockPositions.decodeList(tag.getString("redstoneGates")));
        }
        if (tag.hasKey("redstoneDoors")) {
            doors.addAll(BlockPositions.decodeList(tag.getString("redstoneDoors")));
        }
        if (tag.hasKey("redstoneGrills")) {
            grills.addAll(BlockPositions.decodeList(tag.getString("redstoneGrills")));
        }
    }

    private void cancelIf(EntityPlayer player) {
        if (isBindingFor(player)) {
            bindingPlayer = null;
            bindingTicks = 0;
        }
    }

    /** A player may only hold one wireless binding session at a time. */
    private static void cancelOtherSessions(World world, EntityPlayer player, TileEntity except) {
        for (TileEntity te : world.loadedTileEntityList) {
            if (te == except || !(te instanceof RedstoneBindingHost)) continue;
            ((RedstoneBindingHost) te).getRedstoneBinding().cancelIf(player);
        }
    }

    /** The device whose wireless binding session belongs to this player, or null. */
    @Nullable
    public static RedstoneBinding findSession(World world, EntityPlayer player) {
        if (world == null || player == null) return null;
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof RedstoneBindingHost)) continue;
            RedstoneBinding binding = ((RedstoneBindingHost) te).getRedstoneBinding();
            if (binding.isBindingFor(player)) return binding;
        }
        return null;
    }
}
