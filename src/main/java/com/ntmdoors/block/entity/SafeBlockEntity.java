package com.ntmdoors.block.entity;

import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.SafeScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.RedstoneBinding;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SafeBlockEntity extends BlockEntity implements SidedInventory, ExtendedScreenHandlerFactory, RedstoneBindingHost {

    public static final int SLOT_COUNT = 27;
    private static final int[] NO_SLOTS = new int[0];
    public static final int CODE_LENGTH = 6;
    public static final int LOCKOUT_FAILURES = 3;
    public static final int LOCKOUT_TICKS = 20 * 60 * 5; // 6000 ticks = 5 min
    public static final int ACCESS_SESSION_TICKS = 20 * 60 * 10; // 10 min
    public static final double MAX_BIND_DISTANCE = 32.0;
    public static final int OPEN_DELAY_TICKS = 25;

    public static final int RS_CODE_FAIL = 0;
    public static final int RS_CODE_SUCCESS = 1;
    public static final int RS_BINDING = 2;
    public static final int RS_LOCKOUT = 3;
    public static final int RS_SCAN_FAIL = 4;
    public static final int RS_SCAN_SUCCESS = 5;
    public static final int RS_MATCH_FAIL = 6;
    public static final int RS_MATCH_SUCCESS = 7;
    public static final int RS_EVENT_COUNT = 8;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
    private final boolean[] redstoneEvents = new boolean[RS_EVENT_COUNT];
    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private String securityCode = "";
    private boolean codeSet;
    private boolean matchHolder = true;
    private boolean lockoutMode = true;
    private boolean voidOnDestroy;
    private boolean bindingActivities = true;
    private boolean lockedOut;
    private int failedAttempts;
    private int lockoutTicks;

    private final Map<UUID, Integer> accessSessions = new HashMap<>();
    @Nullable
    private UUID pendingOpenPlayer;
    private int pendingOpenTicks;

    private final List<BlockPos> boundDoors = new ArrayList<>();

    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return 0; // page managed client/server side
                case 1: return isBiometric() ? 1 : 0;
                case 2: return (codeSet || !securityCode.isEmpty()) ? 1 : 0;
                case 3: return lockedOut ? 1 : 0;
                case 4: return matchHolder ? 1 : 0;
                case 5: return bindingActivities ? 1 : 0;
                case 6: return lockoutMode ? 1 : 0;
                case 7: return voidOnDestroy ? 1 : 0;
                default:
                    if (index >= 8 && index < 16) {
                        return redstoneEvents[index - 8] ? 1 : 0;
                    }
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 4: matchHolder = value != 0; break;
                case 5: bindingActivities = value != 0; break;
                case 6: lockoutMode = value != 0; break;
                case 7: voidOnDestroy = value != 0; break;
                default:
                    if (index >= 8 && index < 16) {
                        redstoneEvents[index - 8] = value != 0;
                    }
                    break;
            }
        }

        @Override
        public int size() {
            return 16;
        }
    };

    public SafeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAFE, pos, state);
    }

    public SafeBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isBiometric() {
        return false;
    }

    public boolean hasSecurityCode() {
        return codeSet || !securityCode.isEmpty();
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public boolean isLockoutMode() {
        return lockoutMode;
    }

    public boolean isVoidOnDestroy() {
        return voidOnDestroy;
    }

    public boolean isBindingActivities() {
        return bindingActivities;
    }

    public boolean isMatchHolder() {
        return matchHolder;
    }

    public boolean isRedstoneEvent(int index) {
        if (index < 0 || index >= RS_EVENT_COUNT) return false;
        return redstoneEvents[index];
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public int getLockoutTicks() {
        return lockoutTicks;
    }

    public void setLockoutMode(boolean val) {
        this.lockoutMode = val;
        markDirty();
    }

    public void setVoidOnDestroy(boolean val) {
        this.voidOnDestroy = val;
        markDirty();
    }

    public void setBindingActivities(boolean val) {
        this.bindingActivities = val;
        markDirty();
    }

    public void setRedstoneEvent(int index, boolean val) {
        if (index >= 0 && index < RS_EVENT_COUNT) {
            redstoneEvents[index] = val;
            markDirty();
        }
    }

    public void tickServer() {
        if (lockoutTicks > 0) {
            if (--lockoutTicks <= 0) {
                lockedOut = false;
                failedAttempts = 0;
                markDirty();
                sync();
            }
        }
        if (pendingOpenPlayer != null) {
            if (--pendingOpenTicks <= 0) {
                if (world != null) {
                    ServerPlayerEntity p = (ServerPlayerEntity) world.getPlayerByUuid(pendingOpenPlayer);
                    pendingOpenPlayer = null;
                    if (p != null) {
                        p.openHandledScreen(this);
                    }
                }
            }
        }
        redstoneBinding.update();
        if (bindingPlayer != null) {
            if (--bindingTicks <= 0) {
                stopBinding(null);
            }
        }
        if (!accessSessions.isEmpty()) {
            accessSessions.entrySet().removeIf(e -> e.setValue(e.getValue() - 1) <= 0);
        }
    }

    public List<BlockPos> getBoundDoors() {
        return Collections.unmodifiableList(boundDoors);
    }

    // --- Access & Code Checking ---

    public void grantAccess(PlayerEntity player) {
        if (player != null) {
            accessSessions.put(player.getUuid(), ACCESS_SESSION_TICKS);
        }
    }

    public boolean hasAccessSessionFor(PlayerEntity player) {
        if (player == null) return false;
        if (player.isCreative()) return true;
        Integer remaining = accessSessions.get(player.getUuid());
        return remaining != null && remaining > 0;
    }

    public void scheduleOpen(ServerPlayerEntity player) {
        this.pendingOpenPlayer = player.getUuid();
        this.pendingOpenTicks = OPEN_DELAY_TICKS;
    }

    public void submitCode(ServerPlayerEntity player, String code) {
        if (world == null || world.isClient || isBiometric()) return;

        if (lockedOut) {
            fireRedstoneEvent(RS_CODE_FAIL);
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            sendResult(player, (byte) 3); // LOCKED_OUT
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_locked_out"), true);
            return;
        }

        String clean = Codes.sanitise(code, CODE_LENGTH);
        if (clean.length() < 1 || clean.length() > CODE_LENGTH) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            sendResult(player, (byte) 2); // DENIED
            return;
        }

        if (!hasSecurityCode()) {
            securityCode = clean;
            codeSet = true;
            resetFailures();
            grantAccess(player);
            scheduleOpen(player);
            playSuccess();
            fireRedstoneEvent(RS_CODE_SUCCESS);
            sendResult(player, (byte) 1); // SET
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_code_set"), true);
            markDirty();
            sync();
        } else if (securityCode.equals(clean)) {
            resetFailures();
            grantAccess(player);
            scheduleOpen(player);
            playSuccess();
            fireRedstoneEvent(RS_CODE_SUCCESS);
            sendResult(player, (byte) 0); // ACCEPTED
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_code_accepted"), true);
            markDirty();
            sync();
        } else {
            recordFailure();
            playFail();
            fireRedstoneEvent(RS_CODE_FAIL);
            sendResult(player, (byte) (lockedOut ? 3 : 2));
            player.sendMessage(Text.translatable(lockedOut ? "ntmdoors.msg.safe_locked_out" : "ntmdoors.msg.safe_code_denied"), true);
        }
    }

    public void recordFailure() {
        failedAttempts++;
        if (lockoutMode && failedAttempts >= LOCKOUT_FAILURES) {
            lockoutTicks = LOCKOUT_TICKS;
            lockedOut = true;
            if (world != null) {
                world.playSound(null, pos, ModSounds.DOOR_ALARM, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            fireRedstoneEvent(RS_LOCKOUT);
            if (bindingActivities) {
                closeBoundDoors();
            }
        }
        markDirty();
        sync();
    }

    public void resetFailures() {
        failedAttempts = 0;
        lockoutTicks = 0;
        lockedOut = false;
        markDirty();
        sync();
    }

    public void fireRedstoneEvent(int eventIndex) {
        if (eventIndex >= 0 && eventIndex < RS_EVENT_COUNT && redstoneEvents[eventIndex]) {
            redstoneBinding.fire();
        }
    }

    public void sendResult(ServerPlayerEntity player, byte result) {
        PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(result);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, ModMessages.SAFE_RESULT_ID, buf);
    }

    public void playSuccess() {
        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
    }

    public void playFail() {
        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
    }

    // --- Option Changes ---

    public void setOption(ServerPlayerEntity player, byte option, boolean value) {
        if (!hasAccessSessionFor(player)) return;

        switch (option) {
            case 0 -> bindingActivities = value;
            case 1 -> lockoutMode = value;
            case 2 -> {
                if (!isBiometric()) {
                    securityCode = "";
                    codeSet = false;
                    player.sendMessage(Text.translatable("ntmdoors.msg.safe_code_reset"), true);
                }
            }
            case 3 -> voidOnDestroy = value;
            case 4 -> matchHolder = value;
            default -> {
                int event = option - 5;
                if (event >= 0 && event < RS_EVENT_COUNT) {
                    redstoneEvents[event] = value;
                }
            }
        }
        markDirty();
        sync();
    }

    // --- Door Linking ---

    public static final Map<UUID, BlockPos> ACTIVE_SAFE_SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final int BINDING_TICKS = 20 * 60;
    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;

    public void toggleBinding(PlayerEntity player) {
        if (world == null || world.isClient || player == null) return;
        if (!bindingActivities) {
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_disabled"), true);
            return;
        }
        if (isBindingFor(player)) {
            stopBinding(player);
            return;
        }
        if (bindingPlayer != null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_busy"), true);
            return;
        }
        ACTIVE_SAFE_SESSIONS.put(player.getUuid(), pos.toImmutable());
        bindingPlayer = player.getUuid();
        bindingTicks = BINDING_TICKS;
        player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_on"), true);
    }

    public void stopBinding(@Nullable PlayerEntity player) {
        if (bindingPlayer != null) {
            ACTIVE_SAFE_SESSIONS.remove(bindingPlayer);
            bindingPlayer = null;
            bindingTicks = 0;
            if (player != null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_off"), true);
            }
        }
    }

    public boolean isBindingFor(PlayerEntity player) {
        return player != null && bindingPlayer != null && bindingPlayer.equals(player.getUuid());
    }

    @Nullable
    public static SafeBlockEntity findBindingSafe(World world, PlayerEntity player) {
        if (world == null || player == null) return null;
        BlockPos pos = ACTIVE_SAFE_SESSIONS.get(player.getUuid());
        if (pos == null || !world.isChunkLoaded(pos)) return null;
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof SafeBlockEntity safe && safe.isBindingFor(player) ? safe : null;
    }

    public void bindDoor(PlayerEntity player, DoorBlockEntity door) {
        if (world == null || world.isClient || door == null || player == null) return;

        if (door.getPos().getSquaredDistance(pos) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_bind_far"), true);
            return;
        }

        if (door.getBoundSafe() != null && !door.isBoundToSafe(pos)) {
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_other"), true);
            return;
        }

        BlockPos dp = door.getPos().toImmutable();
        if (boundDoors.contains(dp)) {
            boundDoors.remove(dp);
            door.unbindFromSafe(pos);
            fireRedstoneEvent(RS_BINDING);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_unbound_door"), true);
        } else {
            boundDoors.add(dp);
            door.bindToSafe(pos);
            fireRedstoneEvent(RS_BINDING);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_bound_door"), true);
        }
        markDirty();
        sync();
    }

    public void closeBoundDoors() {
        if (world == null || world.isClient) return;
        for (BlockPos dp : boundDoors) {
            if (!world.isChunkLoaded(dp)) continue;
            BlockEntity be = world.getBlockEntity(dp);
            if (be instanceof DoorBlockEntity door) {
                door.request(false);
            }
        }
    }

    public void unbindAllDoors() {
        if (world == null || world.isClient) return;
        for (BlockPos dp : new ArrayList<>(boundDoors)) {
            if (!world.isChunkLoaded(dp)) continue;
            BlockEntity be = world.getBlockEntity(dp);
            if (be instanceof DoorBlockEntity door) {
                door.unbindFromSafe(pos);
            }
        }
        boundDoors.clear();
        markDirty();
    }

    // --- Tick ---

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;

        if (lockoutTicks > 0) {
            if (--lockoutTicks <= 0) {
                lockedOut = false;
                failedAttempts = 0;
                markDirty();
                sync();
            }
        }

        if (pendingOpenPlayer != null) {
            if (--pendingOpenTicks <= 0) {
                ServerPlayerEntity p = (ServerPlayerEntity) world.getPlayerByUuid(pendingOpenPlayer);
                pendingOpenPlayer = null;
                if (p != null) {
                    p.openHandledScreen(this);
                }
            }
        }

        redstoneBinding.update();

        if (bindingPlayer != null) {
            if (--bindingTicks <= 0) {
                stopBinding(null);
            }
        }

        // Prune expired access sessions
        if (!accessSessions.isEmpty()) {
            accessSessions.entrySet().removeIf(e -> e.setValue(e.getValue() - 1) <= 0);
        }
    }

    // --- Inventory & SidedInventory ---

    @Override
    public int size() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // --- ExtendedScreenHandlerFactory ---

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(isBiometric() ? "container.ntmdoors.biometric_safe" : "container.ntmdoors.safe");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new SafeScreenHandler(syncId, playerInventory, this, this.propertyDelegate, pos);
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

    // --- NBT Serialization ---

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        Inventories.writeNbt(tag, items);
        tag.putString("securityCode", securityCode);
        tag.putBoolean("codeSet", codeSet);
        tag.putBoolean("matchHolder", matchHolder);
        tag.putBoolean("lockoutMode", lockoutMode);
        tag.putBoolean("voidOnDestroy", voidOnDestroy);
        tag.putBoolean("bindingActivities", bindingActivities);
        tag.putBoolean("lockedOut", lockedOut);
        tag.putInt("failedAttempts", failedAttempts);
        tag.putInt("lockoutTicks", lockoutTicks);

        byte[] events = new byte[RS_EVENT_COUNT];
        for (int i = 0; i < RS_EVENT_COUNT; i++) events[i] = (byte) (redstoneEvents[i] ? 1 : 0);
        tag.putByteArray("redstoneEvents", events);

        if (!boundDoors.isEmpty()) tag.putString("boundDoors", BlockPositions.encodeList(boundDoors));
        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        items.clear();
        Inventories.readNbt(tag, items);
        securityCode = tag.getString("securityCode");
        codeSet = tag.contains("codeSet") ? tag.getBoolean("codeSet") : !securityCode.isEmpty();
        matchHolder = !tag.contains("matchHolder") || tag.getBoolean("matchHolder");
        bindingActivities = !tag.contains("bindingActivities") || tag.getBoolean("bindingActivities");
        voidOnDestroy = tag.contains("voidOnDestroy") && tag.getBoolean("voidOnDestroy");
        lockoutMode = !tag.contains("lockoutMode") || tag.getBoolean("lockoutMode");
        lockedOut = tag.getBoolean("lockedOut");
        failedAttempts = tag.getInt("failedAttempts");
        lockoutTicks = tag.getInt("lockoutTicks");

        Arrays.fill(redstoneEvents, false);
        byte[] events = tag.getByteArray("redstoneEvents");
        for (int i = 0; i < Math.min(events.length, RS_EVENT_COUNT); i++) {
            redstoneEvents[i] = events[i] != 0;
        }

        boundDoors.clear();
        if (tag.contains("boundDoors")) {
            boundDoors.addAll(BlockPositions.decodeList(tag.getString("boundDoors")));
        }
        redstoneBinding.readNbt(tag);
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
