package com.ntmdoors.block.entity;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.client.sound.DoorLoopSound;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.util.BlockPositions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * Core cell of a multiblock door.
 * Owns state machine, timer advancement, collision box caching, redstone checks,
 * security ownership, and network synchronization.
 */
public class DoorBlockEntity extends BlockEntity {

    private static final Box[] EMPTY_BOXES = new Box[0];

    private DoorState state = DoorState.CLOSED;
    private int timer;
    private boolean powered;
    private Boolean queuedOpen;
    private int skinIndex;

    // Server-only control fields
    private String keypadCode = "";
    private String scannerCode = "";
    private final DoorSecurityAccess security = new DoorSecurityAccess(this);
    private final List<BlockPos> boundScanners = new ArrayList<>();
    private BlockPos boundSafe;
    private int autoCloseTicks;

    private boolean dismantling;
    private boolean passUpdatePending;

    // Breach & Thermite
    private boolean breached;
    private final Set<BlockPos> thermiteCharges = new HashSet<>();

    // Cached collision geometry for current tick
    private int boxCacheTimer = Integer.MIN_VALUE;
    private DoorState boxCacheState;
    private Direction boxCacheFacing;
    private boolean boxCacheBreached;
    private Box[] boxCache = EMPTY_BOXES;

    // Client interpolation and audio
    public float prevOpenFraction;
    public float openFraction;

    @Environment(EnvType.CLIENT)
    private DoorLoopSound loopSound;
    @Environment(EnvType.CLIENT)
    private DoorLoopSound loopSound2;
    @Environment(EnvType.CLIENT)
    private DoorState clientSoundState;

    public DoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModDoors.DOOR_BLOCK_ENTITY, pos, state);
    }

    public DoorBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private DoorDecl testDecl;
    private Boolean testSecurity;
    private Boolean testLocked;

    public void setDecl(DoorDecl decl) {
        this.testDecl = decl;
    }

    public void setSecurity(boolean security) {
        this.testSecurity = security;
    }

    public void setLocked(boolean locked) {
        this.testLocked = locked;
    }

    public DoorSecurityAccess getSecurity() {
        return security;
    }

    public Boolean getQueuedOpen() {
        return queuedOpen;
    }

    public int getAutoCloseTicks() {
        return autoCloseTicks;
    }

    @Nullable
    public DoorDecl getDecl() {
        if (testDecl != null) return testDecl;
        if (getCachedState() == null) return null;
        Block b = getCachedState().getBlock();
        return b instanceof BlockDoorGeneric ? ((BlockDoorGeneric) b).decl : null;
    }

    public DoorState getState() { return state; }
    public int getTimer() { return timer; }
    public int getSkinIndex() { return skinIndex; }
    public boolean isBreached() { return breached; }

    public boolean beginDismantle() {
        if (dismantling) return false;
        dismantling = true;
        DoorPlacementGuard.unregisterActive(this);
        return true;
    }

    // --- State Machine Ticker ---

    public static void tick(World world, BlockPos pos, BlockState state, DoorBlockEntity be) {
        if (world != null && world.isClient) {
            be.tickClient();
        } else {
            be.tickServer();
        }
    }

    public void tickServer() {
        if (breached) return;

        // Auto close timer
        if (autoCloseTicks > 0 && state == DoorState.OPEN) {
            if (--autoCloseTicks <= 0) {
                autoCloseTicks = 0;
                request(false);
            }
        }

        // Silo hatch pass check on load
        if (passUpdatePending) {
            passUpdatePending = false;
            updatePassState(state == DoorState.OPEN);
        }

        if (!state.isMoving()) return;

        DoorDecl d = getDecl();
        if (d == null) return;

        timer++;
        playCues(d, state == DoorState.OPENING, timer);

        int duration = state == DoorState.OPENING ? d.openTicks : d.closeTicks;
        if (timer >= duration) {
            finishMove(d);
        }
    }

    public void tickClient() {
        prevOpenFraction = openFraction;

        if (breached) {
            stopClientSounds();
            openFraction = 0.0f;
            return;
        }

        DoorDecl d = getDecl();
        if (d != null && state.isMoving()) {
            timer++;
            int duration = state == DoorState.OPENING ? d.openTicks : d.closeTicks;
            if (timer >= duration) {
                state = state == DoorState.OPENING ? DoorState.OPEN : DoorState.CLOSED;
                timer = 0;
            }
        }

        openFraction = getOpenFraction(0.0f);
        updateClientSounds();
    }

    public float getOpenFraction(float tickDelta) {
        if (breached) return 0.0f;
        DoorDecl d = getDecl();
        if (d == null) return state == DoorState.OPEN ? 1.0f : 0.0f;

        switch (state) {
            case OPEN: return 1.0f;
            case OPENING: return Math.min(1.0f, (timer + tickDelta) / Math.max(1, d.openTicks));
            case CLOSING: return 1.0f - Math.min(1.0f, (timer + tickDelta) / Math.max(1, d.closeTicks));
            default: return 0.0f;
        }
    }

    public Box[] getCollisionBoxes(Direction facing) {
        if (boxCacheTimer == timer && boxCacheState == state && boxCacheFacing == facing
                && boxCacheBreached == breached) {
            return boxCache;
        }
        DoorDecl d = getDecl();
        if (d == null) return EMPTY_BOXES;
        boxCache = d.getBoxes(facing, breached ? 1.0f : getOpenFraction(1.0f), true, !breached);
        boxCacheTimer = timer;
        boxCacheState = state;
        boxCacheFacing = facing;
        boxCacheBreached = breached;
        return boxCache;
    }

    // --- State Transitions ---

    public void request(boolean wantOpen) {
        request(wantOpen, false);
    }

    public void request(boolean wantOpen, boolean override) {
        if ((world != null && world.isClient) || breached || isThermiteFused()) return;
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) {
            requestSelf(wantOpen);
            return;
        }
        for (DoorBlockEntity door : linkedGroup()) {
            door.requestSelf(wantOpen);
        }
    }

    private void requestSelf(boolean wantOpen) {
        if (state == DoorState.CLOSED && wantOpen) {
            startMove(DoorState.OPENING);
        } else if (state == DoorState.OPEN && !wantOpen) {
            startMove(DoorState.CLOSING);
        } else if (state.isMoving() && (state == DoorState.OPENING) != wantOpen) {
            queuedOpen = wantOpen;
        }
    }

    public void closeForSystem() {
        if ((world != null && world.isClient) || breached || isThermiteFused()) return;
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) {
            closeNow();
            return;
        }
        for (DoorBlockEntity door : linkedGroup()) door.closeNow();
    }

    public void openForSystem() {
        if ((world != null && world.isClient) || breached || isThermiteFused()) return;
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) {
            openNow();
            return;
        }
        for (DoorBlockEntity door : linkedGroup()) door.openNow();
    }

    public void closeNow() {
        queuedOpen = null;
        if (state == DoorState.OPEN || state == DoorState.OPENING) {
            startMove(DoorState.CLOSING);
        }
    }

    public void openNow() {
        queuedOpen = null;
        autoCloseTicks = 0;
        if (state == DoorState.CLOSED || state == DoorState.CLOSING) {
            startMove(DoorState.OPENING);
        }
    }

    private void startMove(DoorState moving) {
        DoorDecl d = getDecl();
        boolean opening = moving == DoorState.OPENING;
        state = moving;
        timer = 0;
        DoorPlacementGuard.registerActive(this);
        if (world != null && !world.isClient) updatePassState(false);
        if (d != null && world != null) {
            SoundEvent start = d.soundSet(opening).start;
            if (start != null) {
                world.playSound(null, pos, start, SoundCategory.BLOCKS, d.soundVolume, 1.0f);
            }
            playCues(d, opening, 0);
        }
        sync();
    }

    private void finishMove(DoorDecl d) {
        boolean opening = state == DoorState.OPENING;
        state = opening ? DoorState.OPEN : DoorState.CLOSED;
        timer = 0;
        if (state == DoorState.CLOSED) {
            DoorPlacementGuard.unregisterActive(this);
        }
        if (world != null && !world.isClient) {
            updatePassState(opening);
            SoundEvent end = d.soundSet(opening).end;
            if (end != null) {
                world.playSound(null, pos, end, SoundCategory.BLOCKS, d.soundVolume, 1.0f);
            }
        }
        sync();

        Boolean queued = queuedOpen;
        queuedOpen = null;
        if (queued != null) {
            requestSelf(queued);
        }
    }

    private void playCues(DoorDecl d, boolean opening, int tick) {
        if (world == null) return;
        for (DoorDecl.SoundCue cue : d.soundCues) {
            if (cue.tick != tick) continue;
            if (opening ? !cue.opening : !cue.closing) continue;
            world.playSound(null, pos, cue.sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    public void toggle() {
        if (state == DoorState.CLOSED) request(true);
        else if (state == DoorState.OPEN) request(false);
    }

    public boolean cycleSkin() {
        if (breached || isThermiteFused()) return false;
        DoorDecl d = getDecl();
        if (d == null || !d.hasSkinVariants()) return false;
        skinIndex = (skinIndex + 1) % d.skinCount();
        sync();
        return true;
    }

    public void breach() {
        if ((world != null && world.isClient) || breached) return;
        breached = true;
        state = DoorState.CLOSED;
        timer = 0;
        queuedOpen = null;
        powered = false;
        autoCloseTicks = 0;
        security.clear();
        thermiteCharges.clear();
        DoorPlacementGuard.unregisterActive(this);
        sync();
    }

    public void addThermiteCharge(BlockPos p) {
        if ((world != null && world.isClient) || breached) return;
        thermiteCharges.add(p.toImmutable());
        markDirty();
    }

    public void removeThermiteCharge(BlockPos p) {
        if (world != null && world.isClient) return;
        if (thermiteCharges.remove(p)) markDirty();
    }

    public boolean isThermiteFused() {
        return !thermiteCharges.isEmpty();
    }

    public void closeAfter(int ticks) {
        this.autoCloseTicks = Math.max(0, ticks);
    }

    public void unbindFromControllers() {
        security.clear();
        boundScanners.clear();
        boundSafe = null;
        DoorPlacementGuard.unregisterActive(this);
    }

    // --- Redstone & Interaction ---

    public boolean acceptsRedstone() {
        return security.acceptsRedstone(isSecurityDoor());
    }

    public boolean isSecurityDoor() {
        if (testSecurity != null) return testSecurity;
        if (getCachedState() == null) return false;
        Block b = getCachedState().getBlock();
        return b instanceof BlockDoorGeneric && ((BlockDoorGeneric) b).isSecurity();
    }

    public boolean isLockedDoor() {
        if (testLocked != null) return testLocked;
        if (getCachedState() == null) return false;
        Block b = getCachedState().getBlock();
        return b instanceof BlockDoorGeneric && ((BlockDoorGeneric) b).isLocked();
    }

    public boolean isKeypadBound() {
        return !keypadCode.isEmpty();
    }

    public String getKeypadCode() {
        return keypadCode;
    }

    public void bindKeypadCode(String code) {
        if (code != null && !code.isEmpty() && keypadCode.isEmpty()) {
            keypadCode = code;
            sync();
        }
    }

    public void clearKeypadCode() {
        if (!keypadCode.isEmpty()) {
            keypadCode = "";
            sync();
        }
    }

    public String getScannerCode() {
        return scannerCode;
    }

    public void setScannerCode(String code) {
        if (code != null && !code.isEmpty()) {
            scannerCode = code;
            markDirty();
        }
    }

    public void clearScannerCode() {
        if (!scannerCode.isEmpty()) {
            scannerCode = "";
            sync();
        }
    }

    public void setOwner(PlayerEntity player) {
        security.setOwner(world, player, isSecurityDoor());
    }

    public void clearPoweredState() {
        powered = false;
        markDirty();
    }

    public void checkRedstone() {
        if (world == null || world.isClient || breached || isThermiteFused()) return;
        if (!acceptsRedstone()) return;

        boolean p = isGroupReceivingPower();
        if (p == powered) return;
        powered = p;
        markDirty();
        request(p);
    }

    private boolean isReceivingPower() {
        if (!acceptsRedstone()) return false;
        DoorDecl d = getDecl();
        if (d == null || world == null) return false;
        Direction f = getCachedState().get(BlockDoorGeneric.FACING);
        for (BlockPos off : d.getCells(f)) {
            if (world.isReceivingRedstonePower(pos.add(off))) return true;
        }
        return false;
    }

    private boolean isGroupReceivingPower() {
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) return isReceivingPower();
        for (DoorBlockEntity door : linkedGroup()) {
            if (door.isReceivingPower()) return true;
        }
        return false;
    }

    private List<DoorBlockEntity> linkedGroup() {
        List<DoorBlockEntity> group = new ArrayList<>();
        DoorDecl d = getDecl();
        if (world == null || d == null || !d.linkAdjacent) {
            group.add(this);
            return group;
        }
        Block block = getCachedState().getBlock();
        Direction facing = getCachedState().get(BlockDoorGeneric.FACING);
        Direction width = facing.rotateYClockwise();
        Direction[] sides = {width, width.getOpposite()};
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        seen.add(pos);
        pending.add(pos);

        while (!pending.isEmpty() && group.size() < 128) {
            BlockPos p = pending.poll();
            BlockEntity te = world.getBlockEntity(p);
            if (!(te instanceof DoorBlockEntity doorTe)) continue;
            BlockState s = world.getBlockState(p);
            if (!s.isOf(block) || s.get(BlockDoorGeneric.FACING) != facing) continue;
            group.add(doorTe);
            for (Direction side : sides) {
                BlockPos next = p.offset(side);
                if (seen.add(next)) pending.add(next);
            }
        }
        if (group.isEmpty()) group.add(this);
        return group;
    }

    private void updatePassState(boolean pass) {
        DoorDecl d = getDecl();
        if (world == null || world.isClient || d == null || !d.passFallingWhenOpen) return;
        Block block = getCachedState().getBlock();
        int r = d.passRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos cell = pos.add(dx, 0, dz);
                BlockState s = world.getBlockState(cell);
                if (!s.isOf(block) || s.get(BlockDoorGeneric.PASS) == pass) continue;
                world.setBlockState(cell, s.with(BlockDoorGeneric.PASS, pass), Block.NOTIFY_LISTENERS);
            }
        }
    }

    public void onPlayerUse(PlayerEntity player) {
        if (getDecl() == null) return;
        if (breached) {
            player.sendMessage(Text.translatable("ntmdoors.msg.door_breached"), true);
            return;
        }
        if (isThermiteFused()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.door_thermite_fused"), true);
            return;
        }

        // Creative keycard override
        if (isCreativeKeycardHeld(player)) {
            toggle();
            player.sendMessage(Text.translatable("ntmdoors.msg.door_overridden"), true);
            return;
        }

        if (isSecurityDoor()) {
            rejectInteraction(player, "ntmdoors.msg.security");
            return;
        }

        if (isLockedDoor()) {
            rejectInteraction(player, "ntmdoors.msg.locked");
            return;
        }

        if (isKeypadBound()) {
            rejectInteraction(player, "ntmdoors.msg.locked");
            return;
        }

        toggle();
    }

    private void rejectInteraction(PlayerEntity player, String msgKey) {
        if (world != null) {
            world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCKS, 1.0f, 0.6f);
        }
        player.sendMessage(Text.translatable(msgKey), true);
    }

    private boolean isCreativeKeycardHeld(PlayerEntity player) {
        if (player.isCreative()) return true;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        return isKeycardItem(main) || isKeycardItem(off);
    }

    private boolean isKeycardItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return "creative_keycard".equals(path);
    }

    // --- S2C Network Synchronization ---

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
        tag.putInt("timer", timer);
        tag.putInt("skin", skinIndex);
        tag.putBoolean("breached", breached);
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        writeSyncNbt(tag);
        tag.putBoolean("powered", powered);
        tag.putInt("queued", queuedOpen == null ? -1 : (queuedOpen ? 1 : 0));
        tag.putString("keypadCode", keypadCode);
        tag.putString("scannerCode", scannerCode);
        tag.putString("boundScanners", BlockPositions.encodeList(boundScanners));
        tag.putString("boundSafe", BlockPositions.encode(boundSafe));
        tag.putInt("autoClose", autoCloseTicks);
        tag.putString("thermiteCharges", BlockPositions.encodeList(thermiteCharges));
        security.writeToNbt(tag, isSecurityDoor());
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        state = DoorState.byId(tag.getInt("state"));
        timer = tag.getInt("timer");
        skinIndex = tag.getInt("skin");
        breached = tag.getBoolean("breached");
        powered = tag.getBoolean("powered");
        int q = tag.contains("queued") ? tag.getInt("queued") : -1;
        queuedOpen = q < 0 ? null : (q == 1);
        keypadCode = tag.getString("keypadCode");
        scannerCode = tag.getString("scannerCode");
        autoCloseTicks = tag.getInt("autoClose");
        boundScanners.clear();
        boundScanners.addAll(BlockPositions.decodeList(tag.getString("boundScanners")));
        boundSafe = BlockPositions.decode(tag.getString("boundSafe"));
        thermiteCharges.clear();
        thermiteCharges.addAll(BlockPositions.decodeList(tag.getString("thermiteCharges")));
        security.readFromNbt(tag, world, breached);
        if (state != DoorState.CLOSED && !breached) {
            DoorPlacementGuard.registerActive(this);
        }
    }

    // --- Client Audio Loops ---

    @Environment(EnvType.CLIENT)
    private void updateClientSounds() {
        if (state == clientSoundState) return;
        clientSoundState = state;

        stopClientSounds();
        if (!state.isMoving()) return;

        DoorDecl d = getDecl();
        if (d == null) return;

        boolean opening = state == DoorState.OPENING;
        DoorDecl.Sounds sounds = d.soundSet(opening);

        if (sounds.loop != null) {
            loopSound = new DoorLoopSound(this, sounds.loop, d.soundVolume);
            MinecraftClient.getInstance().getSoundManager().play(loopSound);
        }
        if (sounds.loop2 != null) {
            loopSound2 = new DoorLoopSound(this, sounds.loop2, d.soundVolume);
            MinecraftClient.getInstance().getSoundManager().play(loopSound2);
        }
    }

    @Environment(EnvType.CLIENT)
    private void stopClientSounds() {
        if (loopSound != null) {
            MinecraftClient.getInstance().getSoundManager().stop(loopSound);
            loopSound = null;
        }
        if (loopSound2 != null) {
            MinecraftClient.getInstance().getSoundManager().stop(loopSound2);
            loopSound2 = null;
        }
    }

    @Override
    public void markRemoved() {
        DoorPlacementGuard.unregisterActive(this);
        if (world != null && world.isClient) {
            stopClientSounds();
        }
        super.markRemoved();
    }
}
