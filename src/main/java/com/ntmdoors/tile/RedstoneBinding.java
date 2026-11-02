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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A safe's or scanner's wireless link to one or more Redstone Gates. The link is stored on the
 * device and the gate, so either side can be broken without leaving a live link behind. A device
 * can broadcast to several gates and a gate can receive from several devices.
 */
public final class RedstoneBinding {

    /** A binding session the player walks away from expires after a minute. */
    private static final int BINDING_TICKS = 20 * 60;

    private final TileEntity host;
    private final RedstoneBindingHost callbacks;
    private final List<BlockPos> gates = new ArrayList<>();

    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;

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

    public int getGateCount() {
        return gates.size();
    }

    public List<BlockPos> getGates() {
        return Collections.unmodifiableList(gates);
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
        callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_binding_on");
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
        if (gate.getWorld() != world) return false;

        BlockPos gatePos = gate.getPos().toImmutable();
        if (gates.remove(gatePos)) {
            gate.unbindDevice(host.getPos());
            callbacks.onRedstoneBindingChanged();
            callbacks.syncRedstoneState();
            callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_unlinked");
            return true;
        }

        if (!gates.contains(gatePos)) gates.add(gatePos);
        gate.bindDevice(host.getPos());
        callbacks.onRedstoneBindingChanged();
        callbacks.syncRedstoneState();
        callbacks.sendRedstoneMessage(player, "ntmdoors.msg.redstone_gate_linked");
        return true;
    }

    /** Server side: pulses every loaded gate in this link. */
    public void fire() {
        World world = host.getWorld();
        if (world == null || world.isRemote || gates.isEmpty()) return;

        boolean changed = false;
        Iterator<BlockPos> it = gates.iterator();
        while (it.hasNext()) {
            BlockPos gatePos = it.next();
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                ((TileEntityRedstoneGate) te).pulse();
            } else {
                it.remove();
                changed = true;
            }
        }
        if (changed) callbacks.syncRedstoneState();
    }

    /** Server side: removes every reciprocal gate link. Used when this device is broken. */
    public void clearGates() {
        World world = host.getWorld();
        if (world == null || world.isRemote) {
            gates.clear();
            return;
        }
        for (BlockPos gatePos : new ArrayList<>(gates)) {
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                ((TileEntityRedstoneGate) te).unbindDevice(host.getPos());
            }
        }
        gates.clear();
        callbacks.syncRedstoneState();
    }

    /** Called by a broken gate: drop the stale link without touching the gate. */
    public void onGateRemoved(BlockPos gatePos) {
        if (gatePos != null && gates.remove(gatePos)) callbacks.syncRedstoneState();
    }

    public void update() {
        if (bindingPlayer == null) return;
        World world = host.getWorld();
        EntityPlayer player = world == null ? null : world.getPlayerEntityByUUID(bindingPlayer);
        if (player == null || --bindingTicks <= 0) stop(null);
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("redstoneGates", BlockPositions.encodeList(gates));
    }

    public void writeSyncTag(NBTTagCompound tag) {
        tag.setString("redstoneGates", BlockPositions.encodeList(gates));
    }

    public void readFromNBT(NBTTagCompound tag) {
        gates.clear();
        if (tag.hasKey("redstoneGates")) {
            gates.addAll(BlockPositions.decodeList(tag.getString("redstoneGates")));
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
