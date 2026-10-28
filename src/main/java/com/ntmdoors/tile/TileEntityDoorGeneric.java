package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.client.DoorLoopSound;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorState;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Set while the structure is being torn down so parts don't recurse. */
    private boolean dismantling;

    /** Reconciles the silo hatch's pass-through cells on the first server tick after loading. */
    private boolean passUpdatePending;

    /** Cached collision geometry for the current tick, in door-local space. */
    private int boxCacheTimer = Integer.MIN_VALUE;
    private DoorState boxCacheState;
    private EnumFacing boxCacheFacing;
    private AxisAlignedBB[] boxCache = EMPTY_BOXES;

    /** Per-cell light sealing, rebuilt while the door moves. Key is {@link BlockPos#toLong()}. */
    private Map<Long, Boolean> lightCells;
    /** Set on load so the first tick can correct light that was computed before the tile existed. */
    private boolean lightUpdatePending;
    /** Ticks between seal refreshes while moving; huge doors use a slower cadence. */
    private int lightRefreshInterval = 4;

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

    // ------------------------------------------------------------ light sealing

    /** True when this door cell currently holds collision geometry and therefore blocks light. */
    public boolean blocksLightAt(BlockPos cell) {
        DoorDecl d = getDecl();
        if (d != null && !d.sealsLight) return false;
        Map<Long, Boolean> cells = lightCells;
        if (cells == null) return true;
        Boolean solid = cells.get(cell.toLong());
        return solid != null && solid;
    }

    /**
     * Rebuilds the per-cell sealing map and asks the light engine to recheck the cells whose
     * solidity changed. Runs on both sides: the server keeps the gameplay light correct, and the
     * client keeps its own light arrays (which are not resent for a tile entity change) correct.
     */
    private void updateLightSeal(boolean force) {
        if (world == null) return;
        DoorDecl d = getDecl();
        if (d == null) return;
        Block block = getBlockType();
        IBlockState own = world.getBlockState(pos);
        if (own.getBlock() != block) return;
        EnumFacing facing = own.getValue(BlockDoorGeneric.FACING);

        Map<Long, Boolean> previous = lightCells;
        Map<Long, Boolean> current = new HashMap<>();
        boolean seals = d.sealsLight;
        BlockPos[] cells = d.getCells(facing);
        // A 27x24 door changes hundreds of opacity cells. Rechecking all of them every 4 ticks
        // costs far more than the visible difference; large doors refresh every 10 ticks.
        lightRefreshInterval = cells.length > 128 ? 10 : 4;
        for (BlockPos offset : cells) {
            BlockPos cell = pos.add(offset);
            IBlockState state = world.getBlockState(cell);
            boolean solid = seals && state.getBlock() == block
                    && ((BlockDoorGeneric) block).hasCollisionInCell(state, world, cell);
            current.put(cell.toLong(), solid);
        }

        // Publish first: the light engine asks blocksLightAt while it recalculates.
        lightCells = current;
        for (Map.Entry<Long, Boolean> entry : current.entrySet()) {
            if (!force && previous != null && entry.getValue().equals(previous.get(entry.getKey()))) {
                continue;
            }
            world.checkLight(BlockPos.fromLong(entry.getKey()));
        }
    }

    // ------------------------------------------------------------ ticking / state machine

    @Override
    public void update() {
        boolean client = world != null && world.isRemote;
        if (passUpdatePending && !client) {
            passUpdatePending = false;
            updatePassState(state == DoorState.OPEN);
        }
        if (lightUpdatePending) {
            lightUpdatePending = false;
            updateLightSeal(true);
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
        if (timer >= dur) {
            finishMove(d);
        } else if (timer % lightRefreshInterval == 0) {
            // Follow the clearing hitboxes, so light enters a doorway as the panel leaves it.
            updateLightSeal(false);
        }
        // Run after the movement so a loop starts on the first moving tick and stops on the same
        // tick that the animation finishes, keeping the audio exactly as long as the door travel.
        if (client) updateClientSounds();
    }

    private void finishMove(DoorDecl d) {
        boolean opening = state == DoorState.OPENING;
        state = opening ? DoorState.OPEN : DoorState.CLOSED;
        timer = 0;
        updateLightSeal(false);
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
        updateLightSeal(false);
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
        if (isRedstoneOnly()) {
            reject(player);
            return;
        }

        // Skin changes never reach here: vanilla skips block activation for a sneaking player,
        // so ItemScrewdriver.onItemUseFirst owns that interaction.
        if (isKeypadBound()) {
            reject(player);
            return;
        }

        toggle();
    }

    private boolean isRedstoneOnly() {
        Block block = getBlockType();
        return block instanceof BlockDoorGeneric && ((BlockDoorGeneric) block).locked;
    }

    private void reject(EntityPlayer player) {
        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_ON, SoundCategory.BLOCKS, 1F, 0.6F);
        msg(player, "ntmdoors.msg.locked");
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
        return tag;
    }

    /** Keypad codes stay on the server; clients only need the visual state. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("keypadCode");
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
    }

    /**
     * Schedules the pass-through cell check for the next server tick. Doing it here (instead of
     * directly in onLoad) avoids modifying block states while the chunk is still being loaded.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        passUpdatePending = world != null && !world.isRemote;
        // Light may have been computed before this tile entity existed (or with an older state),
        // so recompute the sealed cells once on the first tick.
        lightUpdatePending = world != null;
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
