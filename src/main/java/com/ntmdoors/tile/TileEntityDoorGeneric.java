package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.client.DoorLoopSound;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemCreativeKeycard;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Core cell of a door. Owns the state machine, redstone handling, keypad binding and animation timing. */
public class TileEntityDoorGeneric extends TileEntitySynced implements ITickable {

    private static final AxisAlignedBB[] EMPTY_BOXES = new AxisAlignedBB[0];

    private DoorState state = DoorState.CLOSED;
    private int timer;
    private boolean powered;
    /** Opposite request received while moving; null when nothing is queued. */
    private Boolean queuedOpen;
    private int skinIndex;
    /** Code a keypad has bound to this door; empty when no keypad controls it. Server-only. */
    private String keypadCode = "";
    /**
     * Scanners controlling this door. Server-only, and kept when a scanner is broken so a
     * replacement placed in the same spot adopts the door again.
     */
    private final List<BlockPos> boundScanners = new ArrayList<>();
    /** Ticks until a scanner-opened door closes itself; 0 when no timer is running. */
    private int autoCloseTicks;

    /** Set while the structure is being torn down so parts don't recurse. */
    private boolean dismantling;

    /** Reconciles the silo hatch's pass-through cells on the first server tick after loading. */
    private boolean passUpdatePending;

    /** Cached collision geometry for the current tick, in door-local space. */
    private int boxCacheTimer = Integer.MIN_VALUE;
    private DoorState boxCacheState;
    private EnumFacing boxCacheFacing;
    private AxisAlignedBB[] boxCache = EMPTY_BOXES;

    /*
     * Client-side movement loops. The server plays the one-shot start/end sounds and timed cues,
     * but a one-shot cannot be stretched to the animation length. These looping sounds start and
     * stop with the client's copy of the state machine, so they always match the animation even
     * when the Ogg file is shorter (or longer) than the door's movement.
     */
    @SideOnly(Side.CLIENT)
    private DoorLoopSound loopSound;
    @SideOnly(Side.CLIENT)
    private DoorLoopSound loopSound2;
    @SideOnly(Side.CLIENT)
    private DoorState clientSoundState;

    @Nullable
    public DoorDecl getDecl() {
        Block b = getBlockType();
        return b instanceof BlockDoorGeneric ? ((BlockDoorGeneric) b).decl : null;
    }

    public DoorState getState() {
        return state;
    }

    /** Marks the door as being torn down. Returns false when the teardown is already running. */
    public boolean beginDismantle() {
        if (dismantling) return false;
        dismantling = true;
        return true;
    }

    public int getSkinIndex() {
        return skinIndex;
    }

    /** 0 = closed, 1 = open, smoothly interpolated while moving. */
    public float getOpenFraction(float partialTicks) {
        DoorDecl d = getDecl();
        if (d == null) return state == DoorState.OPEN ? 1F : 0F;
        switch (state) {
            case OPEN: return 1F;
            case OPENING: return Math.min(1F, (timer + partialTicks) / Math.max(1, d.openTicks));
            case CLOSING: return 1F - Math.min(1F, (timer + partialTicks) / Math.max(1, d.closeTicks));
            default: return 0F;
        }
    }

    /**
     * Collision boxes for the current tick, relative to the core cell's bottom centre. The boxes
     * are rebuilt once per tick (and per facing) and reused for every cell and entity query, so
     * following the animation does not put the model transform on the collision hot path.
     */
    public AxisAlignedBB[] getCollisionBoxes(EnumFacing facing) {
        if (boxCacheTimer == timer && boxCacheState == state && boxCacheFacing == facing) {
            return boxCache;
        }
        DoorDecl d = getDecl();
        if (d == null) return EMPTY_BOXES;
        boxCache = d.getBoxes(facing, getOpenFraction(1F), true);
        boxCacheTimer = timer;
        boxCacheState = state;
        boxCacheFacing = facing;
        return boxCache;
    }

    // ------------------------------------------------------------ ticking / state machine

    @Override
    public void update() {
        boolean client = world != null && world.isRemote;
        if (!client) tickAutoClose();
        if (passUpdatePending && !client) {
            passUpdatePending = false;
            updatePassState(state == DoorState.OPEN);
        }
        if (!state.isMoving()) {
            // The loop may still be running on the first tick after the server reports the final
            // state, so stop it here rather than waiting for the next movement.
            if (client) updateClientSounds();
            return;
        }
        DoorDecl d = getDecl();
        if (d == null) return;
        timer++;
        if (!client) playCues(d, state == DoorState.OPENING, timer);
        int dur = state == DoorState.OPENING ? d.openTicks : d.closeTicks;
        if (timer >= dur) finishMove(d);
        // Run after the movement so a loop starts on the first moving tick and stops on the same
        // tick that the animation finishes, keeping the audio exactly as long as the door travel.
        if (client) updateClientSounds();
    }

    /**
     * Runs the scanner's auto-close timer. It only counts while the door is fully open, so a door
     * somebody closed by hand simply drops the timer instead of being forced shut later.
     */
    private void tickAutoClose() {
        if (autoCloseTicks <= 0) return;
        if (state == DoorState.CLOSED) {
            autoCloseTicks = 0;
            return;
        }
        if (state != DoorState.OPEN) return;
        if (--autoCloseTicks <= 0) {
            autoCloseTicks = 0;
            request(false);
        }
    }

    private void finishMove(DoorDecl d) {
        boolean opening = state == DoorState.OPENING;
        state = opening ? DoorState.OPEN : DoorState.CLOSED;
        timer = 0;
        if (world.isRemote) return;
        // The hatch only becomes passable once it is fully open, so ordinary falling blocks
        // still land on the closed lid while it is moving.
        updatePassState(opening);
        SoundEvent end = d.soundSet(opening).end;
        if (end != null) world.playSound(null, pos, end, SoundCategory.BLOCKS, d.soundVolume, 1F);
        sync();
        Boolean queued = queuedOpen;
        queuedOpen = null;
        if (queued != null) requestSelf(queued);
    }

    private void startMove(DoorState moving) {
        DoorDecl d = getDecl();
        boolean opening = moving == DoorState.OPENING;
        state = moving;
        timer = 0;
        if (!world.isRemote) updatePassState(false);
        if (d != null) {
            SoundEvent start = d.soundSet(opening).start;
            if (start != null) world.playSound(null, pos, start, SoundCategory.BLOCKS, d.soundVolume, 1F);
            // HBM's vault door fires a cue on the very first tick of the movement.
            playCues(d, opening, 0);
        }
        sync();
    }

    /** Server side: plays the per-door timed one-shots (vault scrapes and thuds). */
    private void playCues(DoorDecl d, boolean opening, int tick) {
        for (DoorDecl.SoundCue cue : d.soundCues) {
            if (cue.tick != tick) continue;
            if (opening ? !cue.opening : !cue.closing) continue;
            world.playSound(null, pos, cue.sound, SoundCategory.BLOCKS, 1F, 1F);
        }
    }

    // ------------------------------------------------------------ client sounds

    @SideOnly(Side.CLIENT)
    private void updateClientSounds() {
        DoorState now = state;
        if (now == clientSoundState) return;
        clientSoundState = now;

        stopClientSounds();
        if (!now.isMoving()) return;
        DoorDecl d = getDecl();
        if (d == null) return;

        boolean opening = now == DoorState.OPENING;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        DoorDecl.Sounds sounds = d.soundSet(opening);
        if (sounds.loop != null) {
            loopSound = new DoorLoopSound(this, sounds.loop, d.soundVolume);
            handler.playSound(loopSound);
        }
        if (sounds.loop2 != null) {
            loopSound2 = new DoorLoopSound(this, sounds.loop2, d.soundVolume);
            handler.playSound(loopSound2);
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopClientSounds() {
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        if (loopSound != null) {
            handler.stopSound(loopSound);
            loopSound = null;
        }
        if (loopSound2 != null) {
            handler.stopSound(loopSound2);
            loopSound2 = null;
        }
    }

    @Override
    public void invalidate() {
        if (world != null && world.isRemote) stopClientSounds();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && world.isRemote) stopClientSounds();
        super.onChunkUnload();
    }

    /** Server side: ask the door to open (true) or close (false). Safe to call in any state. */
    public void request(boolean wantOpen) {
        if (world == null || world.isRemote) return;
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) {
            requestSelf(wantOpen);
            return;
        }
        for (TileEntityDoorGeneric door : linkedGroup()) door.requestSelf(wantOpen);
    }

    /**
     * Applies the request to one door. A movement in the opposite direction is queued so a
     * redstone pulse in the middle of a stroke reverses cleanly instead of being dropped.
     */
    private void requestSelf(boolean wantOpen) {
        if (state == DoorState.CLOSED && wantOpen) startMove(DoorState.OPENING);
        else if (state == DoorState.OPEN && !wantOpen) startMove(DoorState.CLOSING);
        else if (state.isMoving() && (state == DoorState.OPENING) != wantOpen) {
            queuedOpen = wantOpen;
        }
    }

    public void toggle() {
        if (state == DoorState.CLOSED) request(true);
        else if (state == DoorState.OPEN) request(false);
    }

    /** Switches to the next skin texture. Does nothing if this door has no alternates. */
    public boolean cycleSkin() {
        DoorDecl d = getDecl();
        if (d == null || !d.hasSkinVariants()) return false;
        skinIndex = (skinIndex + 1) % d.skinCount();
        sync();
        return true;
    }

    /** True while a keypad code controls this door. The door stays bound after a keypad is broken. */
    public boolean isKeypadBound() {
        return !keypadCode.isEmpty();
    }

    public String getKeypadCode() {
        return keypadCode;
    }

    /** Binds the first keypad code to this door. Later codes never replace it. */
    public void bindKeypadCode(String code) {
        if (code.isEmpty() || isKeypadBound()) return;
        keypadCode = code;
        sync();
    }

    /** Called by a confirmed keypad reset; the door can then be bound again. */
    public void clearKeypadCode() {
        if (keypadCode.isEmpty()) return;
        keypadCode = "";
        sync();
    }

    /** True when at least one scanner controls this door. The binding survives that scanner breaking. */
    public boolean isBoundToScanner() {
        return !boundScanners.isEmpty();
    }

    /** True when the scanner at {@code scannerPos} controls this door. */
    public boolean isBoundToScanner(BlockPos scannerPos) {
        return scannerPos != null && boundScanners.contains(scannerPos);
    }

    /** Adds a scanner to this door. A door may be controlled by several scanners. */
    public void bindToScanner(BlockPos scannerPos) {
        if (scannerPos == null) return;
        BlockPos scanner = scannerPos.toImmutable();
        if (boundScanners.contains(scanner)) return;
        boundScanners.add(scanner);
        sync();
    }

    /** Makes this door close itself after {@code ticks}, used for scanner-opened doors. */
    public void closeAfter(int ticks) {
        autoCloseTicks = Math.max(0, ticks);
    }

    /**
     * Collects this core and its neighbours along the door's width axis. Roller shutters are
     * built as a row of independent 1x7 blocks; HBM treats touching ones as a single door, so a
     * click or redstone pulse must start every segment on the same tick or they visibly tear.
     */
    private List<TileEntityDoorGeneric> linkedGroup() {
        List<TileEntityDoorGeneric> group = new ArrayList<>();
        DoorDecl d = getDecl();
        if (world == null || d == null || !d.linkAdjacent) {
            group.add(this);
            return group;
        }
        Block block = getBlockType();
        EnumFacing facing = world.getBlockState(pos).getValue(BlockDoorGeneric.FACING);
        EnumFacing width = facing.rotateY();
        EnumFacing[] sides = {width, width.getOpposite()};
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        seen.add(pos);
        pending.add(pos);
        while (!pending.isEmpty() && group.size() < 128) {
            BlockPos p = pending.poll();
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityDoorGeneric)) continue;
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() != block || s.getValue(BlockDoorGeneric.FACING) != facing) continue;
            group.add((TileEntityDoorGeneric) te);
            for (EnumFacing side : sides) {
                BlockPos next = p.offset(side);
                if (seen.add(next)) pending.add(next);
            }
        }
        if (group.isEmpty()) group.add(this);
        return group;
    }

    /**
     * Server side: makes the central cells of a pass-through door report air while it is fully
     * open. The radius is configurable per door so the large silo hatch can clear a 5x5 centre.
     */
    private void updatePassState(boolean pass) {
        DoorDecl d = getDecl();
        if (world == null || world.isRemote || d == null || !d.passFallingWhenOpen) return;
        Block block = getBlockType();
        int r = d.passRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos cell = pos.add(dx, 0, dz);
                IBlockState s = world.getBlockState(cell);
                if (s.getBlock() != block || s.getValue(BlockDoorGeneric.PASS) == pass) continue;
                world.setBlockState(cell, s.withProperty(BlockDoorGeneric.PASS, pass), 2);
            }
        }
    }

    // ------------------------------------------------------------ redstone

    private boolean isReceivingPower() {
        DoorDecl d = getDecl();
        if (d == null) return false;
        EnumFacing f = world.getBlockState(pos).getValue(BlockDoorGeneric.FACING);
        for (BlockPos off : d.getCells(f)) {
            if (world.isBlockPowered(pos.add(off))) return true;
        }
        return false;
    }

    /** True if any segment of the linked group is powered, so a row of shutters reacts as one. */
    private boolean isGroupReceivingPower() {
        DoorDecl d = getDecl();
        if (d == null || !d.linkAdjacent) return isReceivingPower();
        for (TileEntityDoorGeneric door : linkedGroup()) {
            if (door.isReceivingPower()) return true;
        }
        return false;
    }

    /** Called when any part of the door gets a neighbour update. Reacts to redstone edges only. */
    public void checkRedstone() {
        if (world == null || world.isRemote) return;
        // Security doors never react to redstone; only the Creative Keycard opens them.
        if (isSecurityDoor()) return;
        // A door under keypad control is code-driven, locked twins included.
        if (isKeypadBound()) return;
        boolean p = isGroupReceivingPower();
        if (p == powered) return;
        powered = p;
        markDirty();
        request(p);
    }

    // ------------------------------------------------------------ player interaction

    public void onPlayerUse(EntityPlayer player) {
        if (getDecl() == null) return;

        // Debug card: bypasses locks, security, keypad bindings and redstone alike.
        if (ItemCreativeKeycard.isHeldBy(player)) {
            boolean opening = state == DoorState.CLOSED;
            toggle();
            if (opening) {
                TextComponentTranslation text = new TextComponentTranslation("ntmdoors.msg.door_overridden");
                text.getStyle().setColor(TextFormatting.LIGHT_PURPLE);
                player.sendStatusMessage(text, true);
            }
            return;
        }

        if (isSecurityDoor()) {
            reject(player, "ntmdoors.msg.security");
            return;
        }

        if (isRedstoneOnly()) {
            reject(player, "ntmdoors.msg.locked");
            return;
        }

        // Skin changes never reach here: vanilla skips block activation for a sneaking player,
        // so ItemScrewdriver.onItemUseFirst owns that interaction.
        if (isKeypadBound()) {
            reject(player, "ntmdoors.msg.locked");
            return;
        }

        toggle();
    }

    /**
     * True for the {@code _locked} twins: they refuse every manual interaction and only react to
     * redstone, so keypads must never bind to them.
     */
    public boolean isRedstoneOnly() {
        Block block = getBlockType();
        return block instanceof BlockDoorGeneric && ((BlockDoorGeneric) block).locked;
    }

    /** True for the {@code _security} twins, which only the Creative Keycard opens. */
    public boolean isSecurityDoor() {
        Block block = getBlockType();
        return block instanceof BlockDoorGeneric && ((BlockDoorGeneric) block).isSecurity();
    }

    /** True when a keypad may bind and drive this door; only security doors are excluded. */
    public boolean acceptsKeypad() {
        Block block = getBlockType();
        return block instanceof BlockDoorGeneric && !((BlockDoorGeneric) block).isSecurity();
    }

    private void reject(EntityPlayer player, String key) {
        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_ON, SoundCategory.BLOCKS, 1F, 0.6F);
        msg(player, key);
    }

    private static void msg(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    // ------------------------------------------------------------ persistence / sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("state", state.ordinal());
        tag.setInteger("timer", timer);
        tag.setBoolean("powered", powered);
        tag.setInteger("queued", queuedOpen == null ? -1 : queuedOpen ? 1 : 0);
        tag.setInteger("skin", skinIndex);
        tag.setString("keypadCode", keypadCode);
        tag.setString("boundScanners", encodeScanners());
        tag.setInteger("autoClose", autoCloseTicks);
        return tag;
    }

    /** Keypad codes and scanner links stay on the server; clients only need the visual state. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("keypadCode");
        tag.removeTag("boundScanners");
        tag.removeTag("autoClose");
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        state = DoorState.byId(tag.getInteger("state"));
        timer = tag.getInteger("timer");
        powered = tag.getBoolean("powered");
        int queued = tag.hasKey("queued") ? tag.getInteger("queued") : -1;
        queuedOpen = queued < 0 ? null : queued == 1;
        skinIndex = tag.hasKey("skin") ? tag.getInteger("skin") : 0;
        keypadCode = tag.getString("keypadCode");
        autoCloseTicks = tag.getInteger("autoClose");
        boundScanners.clear();
        String scanners = tag.getString("boundScanners");
        if (!scanners.isEmpty()) {
            for (String part : scanners.split(";")) {
                String[] xyz = part.split(",");
                if (xyz.length != 3) continue;
                boundScanners.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
                        Integer.parseInt(xyz[2])));
            }
        }
    }

    /** Scanner positions are stored as "x,y,z;x,y,z". */
    private String encodeScanners() {
        StringBuilder out = new StringBuilder();
        for (BlockPos scanner : boundScanners) {
            if (out.length() > 0) out.append(';');
            out.append(scanner.getX()).append(',').append(scanner.getY()).append(',').append(scanner.getZ());
        }
        return out.toString();
    }

    /**
     * Schedules the pass-through cell check for the next server tick. Doing it here (instead of
     * directly in onLoad) avoids modifying block states while the chunk is still being loaded.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        passUpdatePending = world != null && !world.isRemote;
    }

    // ------------------------------------------------------------ rendering hints

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        DoorDecl d = getDecl();
        if (d == null) return super.getRenderBoundingBox();
        // The vault track extends 6.5 blocks from the core and the transition seal's arm rises far
        // above its footprint, so keep a generous margin or TESR culling can make parts vanish.
        double r = Math.max(d.renderRadius,
                3 + Math.max(Math.max(d.maxX - d.minX, d.maxY - d.minY), d.maxZ - d.minZ));
        return new AxisAlignedBB(pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                pos.getX() + r + 1, pos.getY() + r + 1, pos.getZ() + r + 1);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public double getMaxRenderDistanceSquared() {
        return 16384D;
    }
}
