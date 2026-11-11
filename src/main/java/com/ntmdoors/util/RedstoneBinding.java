package com.ntmdoors.util;

import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.RedstoneBindingHost;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles wireless RF Redstone Gate links, pulse dispatch, and hold management for devices and gates.
 */
public class RedstoneBinding {

    public static final int BINDING_TICKS = 20 * 60; // 60 seconds
    public static final int LINK_TIMEOUT_TICKS = 100;

    public static final Map<UUID, BlockPos> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    public static final Set<RedstoneBinding> ACTIVE_BINDINGS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public enum HoldMode {
        NONE,
        PRESENCE,
        VACANCY
    }

    private BlockEntity host;
    private RedstoneBindingHost callbacks;
    private final List<BlockPos> gates = new ArrayList<>();
    private final List<BlockPos> doors = new ArrayList<>();

    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;

    private HoldMode holdMode = HoldMode.NONE;
    private boolean holdActive;

    private boolean onFail = true;
    private boolean onSuccess = true;

    public RedstoneBinding() {
        this.host = null;
        this.callbacks = null;
        ACTIVE_BINDINGS.add(this);
    }

    public RedstoneBinding(BlockEntity host) {
        this.host = host;
        if (host instanceof RedstoneBindingHost hostCallbacks) {
            this.callbacks = hostCallbacks;
        }
        ACTIVE_BINDINGS.add(this);
    }

    public void setHost(BlockEntity host) {
        this.host = host;
        if (host instanceof RedstoneBindingHost hostCallbacks) {
            this.callbacks = hostCallbacks;
        }
        ACTIVE_BINDINGS.add(this);
    }

    public BlockEntity getHost() {
        return host;
    }

    public boolean isGateHost() {
        return host instanceof RedstoneGateBlockEntity;
    }

    public List<BlockPos> getGates() {
        return gates;
    }

    public List<BlockPos> getDoors() {
        return doors;
    }

    public boolean hasLinks() {
        return !gates.isEmpty() || !doors.isEmpty();
    }

    public boolean isOnFail() {
        return onFail;
    }

    public void setOnFail(boolean onFail) {
        this.onFail = onFail;
    }

    public boolean isOnSuccess() {
        return onSuccess;
    }

    public void setOnSuccess(boolean onSuccess) {
        this.onSuccess = onSuccess;
    }

    public boolean isBindingFor(@Nullable PlayerEntity player) {
        return player != null && bindingPlayer != null && bindingPlayer.equals(player.getUuid());
    }

    public void toggle(PlayerEntity player) {
        if (player == null) return;
        World world = host != null ? host.getWorld() : player.getWorld();
        if (world != null && world.isClient) return;

        if (isBindingFor(player)) {
            stop(player);
            return;
        }

        BlockPos self = host != null ? host.getPos() : null;
        if (self != null) {
            ACTIVE_SESSIONS.put(player.getUuid(), self.toImmutable());
            bindingPlayer = player.getUuid();
            bindingTicks = BINDING_TICKS;
            boolean isGate = isGateHost();
            player.sendMessage(Text.translatable(isGate ? "ntmdoors.msg.redstone_gate_binding_on" : "ntmdoors.msg.redstone_binding_on"), true);
        } else {
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_binding_on"), true);
        }
    }

    public void stop(@Nullable PlayerEntity player) {
        if (bindingPlayer != null) {
            ACTIVE_SESSIONS.remove(bindingPlayer);
            if (player != null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.redstone_binding_off"), true);
            }
            bindingPlayer = null;
            bindingTicks = 0;
        }
    }

    public void cancelIf(PlayerEntity player) {
        if (isBindingFor(player)) {
            bindingPlayer = null;
            bindingTicks = 0;
        }
    }

    @Nullable
    public static RedstoneBinding findSession(World world, PlayerEntity player) {
        if (world == null || player == null) return null;
        BlockPos pos = ACTIVE_SESSIONS.get(player.getUuid());
        if (pos == null) return null;
        if (!world.isChunkLoaded(pos)) return null;
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof RedstoneBindingHost host) {
            RedstoneBinding b = host.getRedstoneBinding();
            if (b != null && b.isBindingFor(player)) return b;
        }
        return null;
    }

    public boolean createsLoop(RedstoneGateBlockEntity candidate) {
        if (candidate == null || host == null) return false;
        BlockPos target = host.getPos();
        if (candidate.getPos().equals(target)) return true;
        World world = host.getWorld();
        if (world != null && world.isClient) return false;
        return reaches(candidate, target, world, new HashSet<>());
    }

    private static boolean reaches(RedstoneGateBlockEntity from, BlockPos target, World world, Set<BlockPos> seen) {
        if (!seen.add(from.getPos())) return false;
        for (BlockPos next : from.getRedstoneBinding().getGates()) {
            if (next.equals(target)) return true;
            if (world == null || !world.isChunkLoaded(next)) continue;
            BlockEntity te = world.getBlockEntity(next);
            if (te instanceof RedstoneGateBlockEntity gate && reaches(gate, target, world, seen)) {
                return true;
            }
        }
        return false;
    }

    public void bindGate(PlayerEntity player, RedstoneGateBlockEntity gate) {
        if (host == null || gate == null || player == null) return;
        World world = host.getWorld();
        if (world == null || world.isClient) return;
        BlockPos gatePos = gate.getPos().toImmutable();

        if (gates.contains(gatePos)) {
            gates.remove(gatePos);
            gate.unbindDevice(host.getPos());
            if (callbacks != null) callbacks.onRedstoneBindingChanged();
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_gate_unlinked"), true);
        } else {
            if (isGateHost() && createsLoop(gate)) {
                player.sendMessage(Text.translatable("ntmdoors.msg.redstone_gate_loop"), true);
                return;
            }
            gates.add(gatePos);
            gate.bindDevice(host.getPos());
            if (callbacks != null) callbacks.onRedstoneBindingChanged();
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_gate_linked"), true);
        }
        if (callbacks != null) callbacks.syncRedstoneState();
    }

    public void bindDoor(PlayerEntity player, DoorBlockEntity door) {
        if (host == null || door == null || player == null) return;
        if (!isGateHost()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_gate_only"), true);
            return;
        }
        World world = host.getWorld();
        if (world == null || world.isClient) return;

        if (door.isBreached()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_breached"), true);
            return;
        }
        if (door.isSecurityDoor() && !door.getSecurity().isOwnedBy(player)) {
            player.sendMessage(Text.translatable("ntmdoors.msg.security_redstone_link_owner_only"), true);
            return;
        }
        if (!door.acceptsRedstone()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_security"), true);
            return;
        }

        BlockPos doorPos = door.getPos().toImmutable();
        if (doors.contains(doorPos)) {
            doors.remove(doorPos);
            door.setLinkedPower(host.getPos(), false);
            door.onGateUnlinked(host.getPos());
            if (callbacks != null) callbacks.onRedstoneBindingChanged();
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_unlinked"), true);
        } else {
            doors.add(doorPos);
            door.onGateLinked(host.getPos());
            if (host instanceof RedstoneGateBlockEntity gate && gate.isPoweredOn()) {
                door.setLinkedPower(host.getPos(), true);
            }
            if (callbacks != null) callbacks.onRedstoneBindingChanged();
            player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_linked"), true);
        }
        if (callbacks != null) callbacks.syncRedstoneState();
    }

    public void hold(BlockPos source, boolean active) {
        setHold(HoldMode.PRESENCE, active);
    }

    public void setHold(HoldMode mode, boolean active) {
        if (host == null || host.getWorld() == null || host.getWorld().isClient) return;
        World world = host.getWorld();
        this.holdMode = mode;
        this.holdActive = active;
        BlockPos self = host.getPos();
        for (BlockPos gatePos : gates) {
            if (!world.isChunkLoaded(gatePos)) continue;
            BlockEntity te = world.getBlockEntity(gatePos);
            if (te instanceof RedstoneGateBlockEntity gate) {
                if (mode == HoldMode.PRESENCE) {
                    if (active) gate.setHeld(self, true);
                    else gate.releaseHold(self);
                } else if (mode == HoldMode.VACANCY) {
                    gate.setVacancy(self, active);
                }
            }
        }
    }

    public void applyGateOutput(boolean powered) {
        if (host == null || host.getWorld() == null || host.getWorld().isClient) return;
        World world = host.getWorld();
        BlockPos self = host.getPos();
        for (BlockPos gatePos : gates) {
            if (!world.isChunkLoaded(gatePos)) continue;
            BlockEntity te = world.getBlockEntity(gatePos);
            if (te instanceof RedstoneGateBlockEntity gate) {
                gate.setLinkedPower(self, powered);
            }
        }
        for (BlockPos doorPos : doors) {
            if (!world.isChunkLoaded(doorPos)) continue;
            BlockEntity te = world.getBlockEntity(doorPos);
            if (te instanceof DoorBlockEntity door) {
                door.setLinkedPower(self, powered);
            }
        }
    }

    public void fireSuccess(World world, BlockPos sourcePos) {
        if (!onSuccess) return;
        fire(world, sourcePos);
    }

    public void fireFail(World world, BlockPos sourcePos) {
        if (!onFail) return;
        fire(world, sourcePos);
    }

    public void fire() {
        World world = host != null ? host.getWorld() : null;
        BlockPos source = host != null ? host.getPos() : null;
        fire(world, source);
    }

    public void fire(World world, BlockPos sourcePos) {
        if (world == null || world.isClient || gates.isEmpty()) return;
        Iterator<BlockPos> it = gates.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            BlockPos gp = it.next();
            if (!world.isChunkLoaded(gp)) continue;
            BlockEntity be = world.getBlockEntity(gp);
            if (be instanceof RedstoneGateBlockEntity gate) {
                gate.pulse();
            } else {
                it.remove();
                changed = true;
            }
        }
        if (changed && callbacks != null) callbacks.syncRedstoneState();
    }

    public void clearGates() {
        clear();
    }

    public void clear() {
        World world = host != null ? host.getWorld() : null;
        if (world == null || world.isClient) {
            gates.clear();
            doors.clear();
            return;
        }
        BlockPos source = host.getPos();
        for (BlockPos gatePos : new ArrayList<>(gates)) {
            if (!world.isChunkLoaded(gatePos)) continue;
            BlockEntity te = world.getBlockEntity(gatePos);
            if (te instanceof RedstoneGateBlockEntity gate) {
                gate.releaseHold(source);
                gate.setLinkedPower(source, false);
                gate.unbindDevice(source);
            }
        }
        for (BlockPos doorPos : new ArrayList<>(doors)) {
            if (!world.isChunkLoaded(doorPos)) continue;
            BlockEntity te = world.getBlockEntity(doorPos);
            if (te instanceof DoorBlockEntity door) {
                door.setLinkedPower(source, false);
                door.onGateUnlinked(source);
            }
        }
        gates.clear();
        doors.clear();
        holdMode = HoldMode.NONE;
        holdActive = false;
        if (callbacks != null) callbacks.syncRedstoneState();
    }

    public void onGateRemoved(BlockPos gatePos) {
        if (gatePos != null && gates.remove(gatePos) && callbacks != null) {
            callbacks.syncRedstoneState();
        }
    }

    public void onDoorRemoved(BlockPos doorPos) {
        if (doorPos != null && doors.remove(doorPos) && callbacks != null) {
            callbacks.syncRedstoneState();
        }
    }

    public void update() {
        if (bindingPlayer == null) return;
        World world = host != null ? host.getWorld() : null;
        PlayerEntity player = world == null ? null : world.getPlayerByUuid(bindingPlayer);
        if (player == null || --bindingTicks <= 0) stop(null);
    }

    public void writeNbt(NbtCompound tag) {
        tag.putBoolean("rsOnFail", onFail);
        tag.putBoolean("rsOnSuccess", onSuccess);
        if (!gates.isEmpty()) tag.putString("redstoneGates", BlockPositions.encodeList(gates));
        if (!doors.isEmpty()) tag.putString("redstoneDoors", BlockPositions.encodeList(doors));
    }

    public void writeToNbt(NbtCompound tag) {
        writeNbt(tag);
    }

    public void writeSyncTag(NbtCompound tag) {
        if (!gates.isEmpty()) tag.putString("redstoneGates", BlockPositions.encodeList(gates));
        if (!doors.isEmpty()) tag.putString("redstoneDoors", BlockPositions.encodeList(doors));
    }

    public void readNbt(NbtCompound tag) {
        if (tag.contains("rsOnFail")) onFail = tag.getBoolean("rsOnFail");
        if (tag.contains("rsOnSuccess")) onSuccess = tag.getBoolean("rsOnSuccess");
        gates.clear();
        doors.clear();
        holdMode = HoldMode.NONE;
        holdActive = false;
        if (tag.contains("redstoneGates")) {
            gates.addAll(BlockPositions.decodeList(tag.getString("redstoneGates")));
        } else if (tag.contains("rsGates", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("rsGates", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                gates.add(BlockPos.fromLong(list.getCompound(i).getLong("pos")));
            }
        }
        if (tag.contains("redstoneDoors")) {
            doors.addAll(BlockPositions.decodeList(tag.getString("redstoneDoors")));
        }
    }

    public void readFromNbt(NbtCompound tag) {
        readNbt(tag);
    }
}
