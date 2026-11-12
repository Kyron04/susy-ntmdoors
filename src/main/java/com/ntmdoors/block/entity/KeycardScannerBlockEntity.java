package com.ntmdoors.block.entity;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.ntmdoors.block.entity.RedstoneBindingHost;

/**
 * Keycard Scanner block entity: state machine, clearance evaluation, lockout alarm, door auto-close.
 */
public class KeycardScannerBlockEntity extends BlockEntity implements RedstoneBindingHost {

    public enum State {
        IDLE,
        BINDING,
        SCANNING,
        GRANTED,
        DENIED,
        SYNCED
    }

    public static final int SCAN_TICKS = 20;
    public static final int RESULT_TICKS = 40;
    public static final int BINDING_TICKS = 1200;
    public static final int AUTO_CLOSE_TICKS = 200;
    public static final int LOCKOUT_FAILURES = 3;
    public static final int LOCKOUT_TICKS = 6000; // 5 minutes
    public static final double MAX_BIND_DISTANCE = 32.0;
    public static final int MAX_BOUND_DOORS = 9;

    // Redstone event constants
    public static final int RS_SCAN_FAIL = 0;
    public static final int RS_SCAN_SUCCESS = 1;
    public static final int RS_MATCH_FAIL = 2;
    public static final int RS_MATCH_SUCCESS = 3;
    public static final int RS_KEY_FAIL = 4;
    public static final int RS_KEY_SUCCESS = 5;
    public static final int RS_BIOMETRIC_FAIL = 6;
    public static final int RS_BIOMETRIC_SUCCESS = 7;
    public static final int RS_LOCKOUT = 8;

    // State machine
    private State state = State.IDLE;
    private int ticks;
    private int bindingTicks;

    // Door binding
    private final List<BlockPos> boundDoors = new ArrayList<>();
    private final List<BlockPos> pendingDoors = new ArrayList<>();
    private UUID bindingPlayerId;

    // Security & Clearance configuration
    private final List<PrivateKeys.Key> assignedKeys = new ArrayList<>();
    private final List<String> assignedIds = new ArrayList<>(); // UUID strings
    private final List<String> assignedIdNames = new ArrayList<>(); // Cached names
    private String securityCode = ""; // Server only
    private boolean matchHolder = true;
    private boolean lockdown = false;
    private boolean lockoutMode = true;
    private boolean ignoreKeys = false;
    private boolean ignoreIds = false;
    private boolean enabled = true;
    private final boolean[] redstoneEvents = new boolean[9];
    private final RedstoneBinding redstoneBinding = new RedstoneBinding();

    // Lockout tracking
    private boolean lockedOut = false;
    private int failedAttempts = 0;
    private int lockoutTicks = 0;

    // Active scan snapshot
    private ItemStack scanMain = ItemStack.EMPTY;
    private ItemStack scanOff = ItemStack.EMPTY;
    private boolean scanCreative = false;
    private UUID scanningPlayerId;

    public KeycardScannerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.KEYCARD_SCANNER, pos, state);
    }

    public KeycardScannerBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        redstoneBinding.setHost(this);
    }

    @Override
    public RedstoneBinding getRedstoneBinding() {
        return redstoneBinding;
    }

    @Override
    public void onRedstoneBindingChanged() {
        markDirty();
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, KeycardScannerBlockEntity be) {
        be.tick();
    }

    public void tick() {
        if (lockedOut) {
            lockoutTicks--;
            if (lockoutTicks <= 0) {
                lockedOut = false;
                lockoutTicks = 0;
                failedAttempts = 0;
                sync();
            }
        }

        if (state == State.SCANNING) {
            ticks--;
            if (ticks <= 0) {
                evaluateScan();
            }
        } else if (state == State.GRANTED || state == State.DENIED || state == State.SYNCED) {
            ticks--;
            if (ticks <= 0) {
                state = State.IDLE;
                sync();
            }
        } else if (state == State.BINDING) {
            bindingTicks--;
            if (bindingTicks <= 0) {
                cancelBinding();
            }
        }
    }

    public void useCard(PlayerEntity player) {
        if (player == null) return;
        startScan(player.getMainHandStack(), player.getOffHandStack(), ItemCreativeKeycard.isHeldBy(player), player.getUuid());
    }

    public void useHolder(PlayerEntity player, ItemStack holder) {
        if (holder == null) return;
        boolean creative = player != null && ItemCreativeKeycard.isHeldBy(player);
        UUID playerId = player != null ? player.getUuid() : null;
        DefaultedList<ItemStack> cards = ItemKeycardHolder.readContents(holder);
        for (ItemStack card : cards) {
            if (!card.isEmpty() && card.getItem() instanceof ItemRfidCard) {
                startScan(card, ItemStack.EMPTY, creative, playerId);
                return;
            }
        }
        startScan(ItemStack.EMPTY, ItemStack.EMPTY, creative, playerId);
    }

    public void startScan(ItemStack main, ItemStack off, boolean creative, UUID playerId) {
        if (state != State.IDLE) return;

        scanMain = main == null ? ItemStack.EMPTY : main.copy();
        scanOff = off == null ? ItemStack.EMPTY : off.copy();
        scanCreative = creative;
        scanningPlayerId = playerId;

        state = State.SCANNING;
        ticks = SCAN_TICKS;
        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
        sync();
    }

    private void evaluateScan() {
        // 1. Creative Keycard check
        if (scanCreative) {
            grantAccess();
            return;
        }

        // 2. System guards
        if (!enabled || lockdown || lockedOut) {
            denyAccess(false);
            return;
        }

        boolean mainValid = true;
        boolean offValid = true;

        // 3. Biometric Holder Matching (matchHolder)
        if (matchHolder && !ignoreIds) {
            if (Biometrics.isBound(scanMain) && (scanningPlayerId == null || !Biometrics.ownerUuid(scanMain).equals(scanningPlayerId.toString()))) {
                mainValid = false; // The entire card is rejected!
                fireRedstoneEvent(RS_MATCH_FAIL);
            }
            if (Biometrics.isBound(scanOff) && (scanningPlayerId == null || !Biometrics.ownerUuid(scanOff).equals(scanningPlayerId.toString()))) {
                offValid = false;
                fireRedstoneEvent(RS_MATCH_FAIL);
            }
        }

        boolean granted = false;

        // 4. Private Key Clearance
        if (!ignoreKeys) {
            if (mainValid) {
                for (PrivateKeys.Key k : PrivateKeys.get(scanMain)) {
                    if (hasAssignedKey(k.serial)) {
                        granted = true;
                        fireRedstoneEvent(RS_KEY_SUCCESS);
                        break;
                    }
                }
            }
            if (!granted && offValid) {
                for (PrivateKeys.Key k : PrivateKeys.get(scanOff)) {
                    if (hasAssignedKey(k.serial)) {
                        granted = true;
                        fireRedstoneEvent(RS_KEY_SUCCESS);
                        break;
                    }
                }
            }
        }

        // 5. Biometric Identity Clearance
        if (!granted && !ignoreIds) {
            if (mainValid && Biometrics.isBound(scanMain) && hasAssignedId(Biometrics.ownerUuid(scanMain))) {
                granted = true;
                fireRedstoneEvent(RS_BIOMETRIC_SUCCESS);
            }
            if (!granted && offValid && Biometrics.isBound(scanOff) && hasAssignedId(Biometrics.ownerUuid(scanOff))) {
                granted = true;
                fireRedstoneEvent(RS_BIOMETRIC_SUCCESS);
            }
        }

        if (granted) {
            grantAccess();
        } else {
            denyAccess(true);
        }
    }

    private void grantAccess() {
        state = State.GRANTED;
        ticks = RESULT_TICKS;
        failedAttempts = 0;
        fireRedstoneEvent(RS_SCAN_SUCCESS);
        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }

        // Dispatch 10s auto-close to all bound doors
        for (DoorBlockEntity door : getLoadedBoundDoors()) {
            door.request(true, scanCreative);
            door.closeAfter(AUTO_CLOSE_TICKS);
        }
        sync();
    }

    private void denyAccess(boolean recordFailure) {
        state = State.DENIED;
        ticks = RESULT_TICKS;
        fireRedstoneEvent(RS_SCAN_FAIL);
        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }

        if (recordFailure) {
            recordFailure();
        }
        sync();
    }

    public void recordFailure() {
        failedAttempts++;
        if (lockoutMode && failedAttempts >= LOCKOUT_FAILURES) {
            lockedOut = true;
            lockoutTicks = LOCKOUT_TICKS;
            fireRedstoneEvent(RS_LOCKOUT);
            if (world != null) {
                world.playSound(null, pos, ModSounds.DOOR_ALARM, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    public void resetFailures() {
        failedAttempts = 0;
        lockedOut = false;
        lockoutTicks = 0;
        sync();
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void fireRedstoneEvent(int eventId) {
        if (eventId >= 0 && eventId < redstoneEvents.length && redstoneEvents[eventId]) {
            redstoneBinding.fire(world, pos);
        }
    }

    public boolean hasAssignedKey(String serial) {
        for (PrivateKeys.Key k : assignedKeys) {
            if (k.serial.equals(serial)) return true;
        }
        return false;
    }

    public boolean hasAssignedId(String uuid) {
        return assignedIds.contains(uuid);
    }

    public void assignKey(String name, String serial) {
        if (!hasAssignedKey(serial)) {
            assignedKeys.add(new PrivateKeys.Key(name, serial));
            sync();
        }
    }

    public void unassignKey(String serial) {
        assignedKeys.removeIf(k -> k.serial.equals(serial));
        sync();
    }

    public void assignId(String uuid, String name) {
        if (!assignedIds.contains(uuid)) {
            assignedIds.add(uuid);
            assignedIdNames.add(name);
            sync();
        }
    }

    public void unassignId(String uuid) {
        int idx = assignedIds.indexOf(uuid);
        if (idx >= 0) {
            assignedIds.remove(idx);
            if (idx < assignedIdNames.size()) assignedIdNames.remove(idx);
            sync();
        }
    }

    // --- Screwdriver Door Binding ---

    public void toggleBinding(PlayerEntity player) {
        if (isBindingFor(player)) {
            confirm(player);
        } else {
            bindingPlayerId = player.getUuid();
            pendingDoors.clear();
            pendingDoors.addAll(boundDoors);
            state = State.BINDING;
            bindingTicks = BINDING_TICKS;
            player.sendMessage(Text.translatable("ntmdoors.msg.scanner_binding_on"), true);
            sync();
        }
    }

    public boolean isBindingFor(PlayerEntity player) {
        return state == State.BINDING && bindingPlayerId != null && bindingPlayerId.equals(player.getUuid());
    }

    public void selectDoor(DoorBlockEntity door, PlayerEntity player) {
        if (!isBindingFor(player)) return;

        if (pos.getSquaredDistance(door.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_far"), true);
            return;
        }

        if (pendingDoors.contains(door.getPos())) {
            pendingDoors.remove(door.getPos());
            door.removeBoundScanner(pos);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_unbound_door"), true);
        } else if (pendingDoors.size() >= MAX_BOUND_DOORS) {
            player.sendMessage(Text.translatable("ntmdoors.msg.scanner_full"), true);
        } else {
            pendingDoors.add(door.getPos().toImmutable());
            door.addBoundScanner(pos);
            player.sendMessage(Text.translatable("ntmdoors.msg.scanner_bound"), true);
        }
        sync();
    }

    public void confirm(PlayerEntity player) {
        if (!isBindingFor(player)) return;

        boundDoors.clear();
        boundDoors.addAll(pendingDoors);
        pendingDoors.clear();
        bindingPlayerId = null;

        for (DoorBlockEntity d : getLoadedBoundDoors()) {
            d.addBoundScanner(pos);
        }

        state = State.SYNCED;
        ticks = RESULT_TICKS;
        player.sendMessage(Text.translatable("ntmdoors.msg.scanner_confirmed"), true);
        sync();
    }

    public void cancelBinding() {
        pendingDoors.clear();
        bindingPlayerId = null;
        state = State.IDLE;
        sync();
    }

    public List<DoorBlockEntity> getLoadedBoundDoors() {
        List<DoorBlockEntity> list = new ArrayList<>();
        if (world == null) return list;
        for (BlockPos p : boundDoors) {
            if (world.isChunkLoaded(p)) {
                BlockEntity be = world.getBlockEntity(p);
                if (be instanceof DoorBlockEntity d) {
                    list.add(d);
                }
            }
        }
        return list;
    }

    public boolean hasBoundDoors() {
        return !boundDoors.isEmpty();
    }

    public List<BlockPos> getBoundDoors() {
        return Collections.unmodifiableList(boundDoors);
    }

    // Getters and Setters
    public State getState() {
        return state;
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public int getLockoutTicks() {
        return lockoutTicks;
    }

    public boolean isLockdown() {
        return lockdown;
    }

    public void setLockdown(boolean lockdown) {
        this.lockdown = lockdown;
        sync();
    }

    public boolean isMatchHolder() {
        return matchHolder;
    }

    public void setMatchHolder(boolean matchHolder) {
        this.matchHolder = matchHolder;
        sync();
    }

    public boolean isLockoutMode() {
        return lockoutMode;
    }

    public void setLockoutMode(boolean lockoutMode) {
        this.lockoutMode = lockoutMode;
        if (!lockoutMode) {
            resetFailures();
        } else {
            sync();
        }
    }

    public boolean isIgnoreKeys() {
        return ignoreKeys;
    }

    public void setIgnoreKeys(boolean ignoreKeys) {
        this.ignoreKeys = ignoreKeys;
        sync();
    }

    public boolean isIgnoreIds() {
        return ignoreIds;
    }

    public void setIgnoreIds(boolean ignoreIds) {
        this.ignoreIds = ignoreIds;
        sync();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        sync();
    }

    public boolean getRedstoneEvent(int id) {
        return (id >= 0 && id < redstoneEvents.length) ? redstoneEvents[id] : false;
    }

    public void setRedstoneEvent(int id, boolean value) {
        if (id >= 0 && id < redstoneEvents.length) {
            redstoneEvents[id] = value;
            markDirty();
        }
    }

    public String getSecurityCode() {
        return securityCode;
    }

    public void setSecurityCode(String securityCode) {
        this.securityCode = Codes.sanitise(securityCode, 6);
        markDirty();
    }

    public List<PrivateKeys.Key> getAssignedKeys() {
        return Collections.unmodifiableList(assignedKeys);
    }

    public List<String> getAssignedIds() {
        return Collections.unmodifiableList(assignedIds);
    }

    public List<String> getAssignedIdNames() {
        return Collections.unmodifiableList(assignedIdNames);
    }

    public int getVisibleCount() {
        return state == State.BINDING ? pendingDoors.size() : boundDoors.size();
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
        writeSyncNbt(tag);
        return tag;
    }

    public void writeSyncNbt(NbtCompound tag) {
        tag.putInt("state", state.ordinal());
        tag.putInt("ticks", ticks);
        tag.putBoolean("lockedOut", lockedOut);
        tag.putInt("lockoutTicks", lockoutTicks);
        tag.putBoolean("matchHolder", matchHolder);
        tag.putBoolean("lockdown", lockdown);
        tag.putBoolean("lockoutMode", lockoutMode);
        tag.putBoolean("ignoreKeys", ignoreKeys);
        tag.putBoolean("ignoreIds", ignoreIds);
        tag.putBoolean("enabled", enabled);
        tag.putInt("doorCount", getVisibleCount());

        NbtList keysList = new NbtList();
        for (PrivateKeys.Key k : assignedKeys) {
            NbtCompound c = new NbtCompound();
            c.putString("name", k.name);
            c.putString("serial", k.serial);
            keysList.add(c);
        }
        tag.put("assignedKeys", keysList);

        NbtList idList = new NbtList();
        for (int i = 0; i < assignedIds.size(); i++) {
            NbtCompound c = new NbtCompound();
            c.putString("uuid", assignedIds.get(i));
            c.putString("name", i < assignedIdNames.size() ? assignedIdNames.get(i) : "");
            idList.add(c);
        }
        tag.put("assignedIds", idList);
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        writeSyncNbt(tag);
        tag.putString("securityCode", securityCode);
        tag.putInt("failedAttempts", failedAttempts);

        NbtList doorList = new NbtList();
        for (BlockPos p : boundDoors) {
            NbtCompound c = new NbtCompound();
            c.putLong("pos", p.asLong());
            doorList.add(c);
        }
        tag.put("boundDoors", doorList);

        byte[] rs = new byte[redstoneEvents.length];
        for (int i = 0; i < redstoneEvents.length; i++) rs[i] = (byte) (redstoneEvents[i] ? 1 : 0);
        tag.putByteArray("rsEvents", rs);

        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        if (tag.contains("state")) state = State.values()[Math.min(tag.getInt("state"), State.values().length - 1)];
        ticks = tag.getInt("ticks");
        lockedOut = tag.getBoolean("lockedOut");
        lockoutTicks = tag.getInt("lockoutTicks");
        if (tag.contains("matchHolder")) matchHolder = tag.getBoolean("matchHolder");
        if (tag.contains("lockdown")) lockdown = tag.getBoolean("lockdown");
        if (tag.contains("lockoutMode")) lockoutMode = tag.getBoolean("lockoutMode");
        if (tag.contains("ignoreKeys")) ignoreKeys = tag.getBoolean("ignoreKeys");
        if (tag.contains("ignoreIds")) ignoreIds = tag.getBoolean("ignoreIds");
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");

        assignedKeys.clear();
        if (tag.contains("assignedKeys", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("assignedKeys", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                assignedKeys.add(new PrivateKeys.Key(c.getString("name"), c.getString("serial")));
            }
        }

        assignedIds.clear();
        assignedIdNames.clear();
        if (tag.contains("assignedIds", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("assignedIds", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                assignedIds.add(c.getString("uuid"));
                assignedIdNames.add(c.getString("name"));
            }
        }

        if (tag.contains("securityCode")) securityCode = tag.getString("securityCode");
        failedAttempts = tag.getInt("failedAttempts");

        boundDoors.clear();
        if (tag.contains("boundDoors", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("boundDoors", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                boundDoors.add(BlockPos.fromLong(list.getCompound(i).getLong("pos")));
            }
        }

        if (tag.contains("rsEvents")) {
            byte[] rs = tag.getByteArray("rsEvents");
            for (int i = 0; i < Math.min(rs.length, redstoneEvents.length); i++) {
                redstoneEvents[i] = rs[i] == 1;
            }
        }

        redstoneBinding.readNbt(tag);
    }
}
