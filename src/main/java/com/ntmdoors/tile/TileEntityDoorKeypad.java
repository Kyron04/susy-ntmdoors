package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Server-authoritative keypad.
 *
 * <p>A keypad stores its own 1-6 digit code. The first code set on a door binds that code to the
 * door core, and every keypad linked to the door must use the same code to open it. The binding
 * lives on the door, so destroying a keypad does not unlock the door. A screwdriver starts a reset
 * sequence that clears the code from the door and from every keypad linked to it.</p>
 *
 * <p>A keypad only links to a door when it is placed directly next to one of the door's cells.
 * This keeps a keypad from binding or auto-syncing to a door elsewhere in the room.</p>
 *
 * <p>Doors can also be bound from a distance: sneak + right click the keypad with a screwdriver to
 * enter distance-binding mode, then sneak + right click any closed door to link it. The link is
 * stored on the keypad, so the code typed there opens the remote doors too.</p>
 */
public class TileEntityDoorKeypad extends TileEntitySynced implements ITickable {

    public static final int CODE_LENGTH = 6;
    public static final int BUTTONS = 12;
    public static final int SEARCH_RADIUS = 8;
    /** How far a door may be from the keypad to be bound and operated remotely. */
    public static final double MAX_BIND_DISTANCE = 32.0;
    /** Bottom row of the keypad model: reset, zero, enter. */
    public static final int BUTTON_RESET = 9;
    public static final int BUTTON_ZERO = 10;
    public static final int BUTTON_ENTER = 11;

    /** What the keypad is waiting for. Synced so the client renders the same prompt. */
    public enum Mode {
        /** No code stored yet: Enter stores the typed code. */
        SET_CODE,
        /** A code is stored: Enter validates it and toggles the door. */
        ENTER_CODE,
        /** Screwdriver pressed once: a second press confirms the reset. */
        RESET_CONFIRM,
        /** Waiting for the current code before clearing the door and all linked keypads. */
        RESET_ENTER;

        static Mode byId(int id) {
            Mode[] v = values();
            return v[Math.floorMod(id, v.length)];
        }
    }

    /** Transient message on the LCD. */
    public enum Feedback {
        NONE, CODE_SET, RESET_DONE, SUCCESS, DENIED, CODE_SYNCED;

        static Feedback byId(int id) {
            Feedback[] v = values();
            return v[Math.floorMod(id, v.length)];
        }
    }

    /** A key cap's centre in keypad model space. */
    public static final class Button {
        public final float x, y;

        private Button(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Centre of every button quad, in id order (0..8 are 9..1, 9..11 are R, 0, E). The renderer
     * draws its labels on the same grid, so the clickable area and the visible keys cannot drift
     * apart.
     */
    public static final Button[] BUTTON_LAYOUT = {
            new Button(0.234375F, 0.62890625F), new Button(0F, 0.62890625F), new Button(-0.234375F, 0.62890625F),
            new Button(0.234375F, 0.453125F), new Button(0F, 0.453125F), new Button(-0.234375F, 0.453125F),
            new Button(0.234375F, 0.27734375F), new Button(0F, 0.27734375F), new Button(-0.234375F, 0.27734375F),
            new Button(0.234375F, 0.1015625F), new Button(0F, 0.1015625F), new Button(-0.234375F, 0.1015625F)
    };
    public static final float BUTTON_HALF_WIDTH = 0.078125F;
    public static final float BUTTON_HALF_HEIGHT = 0.05859375F;

    /** Digits typed but not submitted. Also the reset-code buffer. Synced for the LCD. */
    private String entry = "";
    /** The keypad's own code. Empty until one is set; never sent to clients. */
    private String storedCode = "";
    private Mode mode = Mode.SET_CODE;
    private Feedback feedback = Feedback.NONE;
    private int feedbackTicks;
    private int blackScreenTicks;
    private final byte[] cooldowns = new byte[BUTTONS];
    /** True while the player is linking remote doors; synced so the LCD can show the mode. */
    private boolean distanceMode;
    /** Cores controlled from a distance. Server-only; positions are validated when used. */
    private final List<BlockPos> boundDoors = new ArrayList<>();

    // ------------------------------------------------------------ state queries

    public Mode getMode() {
        return mode;
    }

    /** The digits typed so far, for the LCD. Empty when nothing has been entered. */
    public String getEntry() {
        return entry;
    }

    /** Whether a code has been set. Derived from the synced mode, so it works on both sides. */
    public boolean hasStoredCode() {
        return mode != Mode.SET_CODE;
    }

    /** The current LCD message, or {@link Feedback#NONE} when nothing is being shown. */
    public Feedback getFeedback() {
        return feedbackTicks > 0 ? feedback : Feedback.NONE;
    }

    public int getCooldown(int id) {
        return id >= 0 && id < BUTTONS ? cooldowns[id] : 0;
    }

    public int getBlackScreenTicks() {
        return blackScreenTicks;
    }

    /** Whether the keypad is waiting for doors to be linked at range. */
    public boolean isDistanceMode() {
        return distanceMode;
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void update() {
        for (int i = 0; i < BUTTONS; i++) {
            if (cooldowns[i] > 0) cooldowns[i]--;
        }
        if (feedbackTicks > 0 && --feedbackTicks == 0) {
            // The screen blanks for a moment after a code is accepted, then returns to idle.
            if (feedback == Feedback.CODE_SET) blackScreenTicks = 20;
            feedback = Feedback.NONE;
        }
        if (blackScreenTicks > 0) blackScreenTicks--;
    }

    // ------------------------------------------------------------ button input

    /** Resolves a local hit to a button id, or -1 if the click missed the keys. Read-only. */
    public int buttonAt(float hitX, float hitY, float hitZ) {
        if (world == null) return -1;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorKeypad)) return -1;
        EnumFacing f = state.getValue(BlockDoorKeypad.FACING);

        // World hit -> keypad model space. The model is centred on the block and scaled by the
        // same transform the renderer uses, so both stay in sync.
        double cx = hitX - 0.5;
        double cz = hitZ - 0.5;
        double xModel = Facings.localX(cx, cz, f) / BlockDoorKeypad.MODEL_SCALE_X;
        double yModel = (hitY - BlockDoorKeypad.MODEL_OFFSET_Y) / BlockDoorKeypad.MODEL_SCALE_Y;

        for (int id = 0; id < BUTTONS; id++) {
            Button button = BUTTON_LAYOUT[id];
            if (Math.abs(xModel - button.x) <= BUTTON_HALF_WIDTH
                    && Math.abs(yModel - button.y) <= BUTTON_HALF_HEIGHT) {
                return id;
            }
        }
        return -1;
    }

    /** Server side: processes a button press. */
    public void pressButton(int id) {
        if (world == null || world.isRemote) return;
        if (id < 0 || id >= BUTTONS) return;
        if (cooldowns[id] > 0) return;
        cooldowns[id] = 20;

        // Any key leaves distance-binding mode: the player is back at the pad to enter a code.
        distanceMode = false;

        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK,
                SoundCategory.BLOCKS, 0.5F, 0.9F + world.rand.nextFloat() * 0.2F);

        // Any key dismisses the "Reset code?" prompt; a digit then starts a normal entry.
        if (mode == Mode.RESET_CONFIRM) {
            mode = Mode.ENTER_CODE;
            entry = "";
            showFeedback(Feedback.NONE);
        }

        switch (id) {
            case BUTTON_RESET:
                pressReset();
                break;
            case BUTTON_ZERO:
                enterDigit(0);
                break;
            case BUTTON_ENTER:
                pressEnter();
                break;
            default: // digits 1-9: id 0 -> 9, id 8 -> 1
                enterDigit(9 - id);
        }
        sync();
    }

    /** R closes an open door when this keypad is authorised, otherwise it clears the entry. */
    private void pressReset() {
        if (mode == Mode.RESET_ENTER) {
            mode = Mode.ENTER_CODE;
            entry = "";
            showFeedback(Feedback.NONE);
            return;
        }
        for (TileEntityDoorGeneric door : controlDoors()) {
            if (door.getState() != DoorState.CLOSED && isAuthorized(door)) {
                door.request(false);
            }
        }
        entry = "";
        showFeedback(Feedback.NONE);
    }

    /** Enter stores a new code, validates an existing one, or completes a reset. */
    private void pressEnter() {
        if (entry.isEmpty()) return;
        String entered = entry;
        entry = "";
        List<TileEntityDoorGeneric> doors = controlDoors();
        switch (mode) {
            case SET_CODE:
                setCode(entered, doors);
                break;
            case RESET_ENTER:
                resetCode(entered, doors);
                break;
            default:
                openWithCode(entered, doors);
        }
    }

    /**
     * Stores the keypad's first code. Any code can be set; a keypad whose code differs from the
     * door's binding simply cannot open it (the door keeps the first code it was given).
     */
    private void setCode(String entered, List<TileEntityDoorGeneric> doors) {
        storedCode = entered;
        mode = Mode.ENTER_CODE;
        for (TileEntityDoorGeneric door : doors) {
            if (!door.isKeypadBound()) door.bindKeypadCode(entered);
        }
        showFeedback(Feedback.CODE_SET);
    }

    /** Clears the door and every linked keypad once the current code has been confirmed. */
    private void resetCode(String entered, List<TileEntityDoorGeneric> doors) {
        String expected = storedCode;
        for (TileEntityDoorGeneric door : doors) {
            if (door.isKeypadBound()) {
                expected = door.getKeypadCode();
                break;
            }
        }
        if (!expected.isEmpty() && expected.equals(entered)) {
            resetCodes(doors);
            showFeedback(Feedback.RESET_DONE);
        } else {
            showFeedback(Feedback.DENIED);
        }
    }

    /** Opens (or closes) every controlled door when both codes match. */
    private void openWithCode(String entered, List<TileEntityDoorGeneric> doors) {
        if (!entered.equals(storedCode)) {
            showFeedback(Feedback.DENIED);
            return;
        }
        boolean toggled = false;
        for (TileEntityDoorGeneric door : doors) {
            // A keypad that was configured before its door existed adopts the door on first use.
            if (!door.isKeypadBound()) door.bindKeypadCode(storedCode);
            if (!entered.equals(door.getKeypadCode())) continue;
            door.toggle();
            toggled = true;
        }
        showFeedback(toggled ? Feedback.SUCCESS : Feedback.DENIED);
    }

    private boolean isAuthorized(TileEntityDoorGeneric door) {
        return !storedCode.isEmpty() && storedCode.equals(door.getKeypadCode());
    }

    /** Server side: advances the screwdriver reset sequence. */
    public void onScrewdriverUse() {
        if (world == null || world.isRemote || !hasStoredCode()) return;
        entry = "";
        if (distanceMode) {
            // A plain click leaves distance mode first; a second click starts the reset.
            distanceMode = false;
            showFeedback(Feedback.NONE);
            sync();
            return;
        }
        switch (mode) {
            case RESET_CONFIRM:
                mode = Mode.RESET_ENTER;
                break;
            case RESET_ENTER:
                mode = Mode.ENTER_CODE; // a third click cancels
                break;
            default:
                mode = Mode.RESET_CONFIRM;
        }
        showFeedback(Feedback.NONE);
        sync();
    }

    /**
     * Server side: enters or leaves distance-binding mode. Returns the new state so the screwdriver
     * can remember which keypad is waiting for doors.
     */
    public boolean toggleDistanceMode(EntityPlayer player) {
        if (world == null || world.isRemote) return false;
        distanceMode = !distanceMode;
        entry = "";
        showFeedback(Feedback.NONE);
        msg(player, distanceMode ? "ntmdoors.msg.bind_mode_on" : "ntmdoors.msg.bind_mode_off");
        sync();
        return distanceMode;
    }

    /**
     * Server side: links a door while the player is in distance-binding mode. The door must be
     * closed, within range and not already controlled by a different code.
     */
    public boolean bindDoor(TileEntityDoorGeneric door, EntityPlayer player) {
        if (world == null || world.isRemote || door == null || !distanceMode) return false;
        if (door.getState() != DoorState.CLOSED) {
            msg(player, "ntmdoors.msg.bind_open");
            return false;
        }
        if (pos.distanceSq(door.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            msg(player, "ntmdoors.msg.bind_far");
            return false;
        }
        if (door.isKeypadBound() && (storedCode.isEmpty() || !storedCode.equals(door.getKeypadCode()))) {
            msg(player, "ntmdoors.msg.bind_code");
            return false;
        }
        if (!storedCode.isEmpty() && !door.isKeypadBound()) door.bindKeypadCode(storedCode);
        if (!isBoundTo(door)) {
            boundDoors.add(door.getPos());
            markDirty();
        }
        msg(player, "ntmdoors.msg.bind_success");
        return true;
    }

    private void enterDigit(int digit) {
        if (entry.length() >= CODE_LENGTH) return;
        showFeedback(Feedback.NONE);
        entry += digit;
    }

    private void showFeedback(Feedback value) {
        feedback = value;
        feedbackTicks = value == Feedback.NONE ? 0 : 20;
        blackScreenTicks = 0;
    }

    /** Drops the keypad back to "no code", without touching the door. */
    private void wipeCode() {
        storedCode = "";
        entry = "";
        mode = Mode.SET_CODE;
        feedback = Feedback.NONE;
        feedbackTicks = 0;
        blackScreenTicks = 0;
    }

    // ------------------------------------------------------------ door linking

    /**
     * The door this keypad is attached to, or null when no door cell touches the keypad. Only the
     * 26 neighbouring cells are considered: a keypad must sit next to the door to bind, auto-sync
     * or operate it.
     */
    @Nullable
    public TileEntityDoorGeneric findDoor() {
        if (world == null) return null;

        TileEntityDoorGeneric best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = pos.add(dx, dy, dz);
                    if (!world.isBlockLoaded(p)) continue;
                    TileEntity te = world.getTileEntity(p);
                    TileEntityDoorGeneric core;
                    if (te instanceof TileEntityDoorGeneric) {
                        core = (TileEntityDoorGeneric) te;
                    } else if (te instanceof TileEntityDoorPart) {
                        // Large doors put the core far from the keypad, so link through the
                        // adjacent door cell instead of requiring the core itself to touch.
                        BlockPos corePos = ((TileEntityDoorPart) te).corePos;
                        TileEntity coreTe = corePos == null ? null : world.getTileEntity(corePos);
                        if (!(coreTe instanceof TileEntityDoorGeneric)) continue;
                        core = (TileEntityDoorGeneric) coreTe;
                    } else {
                        continue;
                    }
                    double d2 = p.distanceSq(pos);
                    if (d2 < bestDist) {
                        bestDist = d2;
                        best = core;
                    }
                }
            }
        }
        return best;
    }

    private Iterable<? extends BlockPos> searchArea() {
        return BlockPos.getAllInBoxMutable(
                pos.add(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                pos.add(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS));
    }

    /**
     * Every door this keypad can operate: the adjacent door, plus each door bound at a distance.
     * Stale links (broken or replaced doors) are dropped as they are found.
     */
    public List<TileEntityDoorGeneric> controlDoors() {
        List<TileEntityDoorGeneric> doors = new ArrayList<>();
        TileEntityDoorGeneric adjacent = findDoor();
        if (adjacent != null) doors.add(adjacent);
        if (world == null) return doors;

        boolean dropped = false;
        for (Iterator<BlockPos> it = boundDoors.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (!world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileEntityDoorGeneric) {
                TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
                if (!containsDoor(doors, door.getPos())) doors.add(door);
            } else {
                it.remove();
                dropped = true;
            }
        }
        if (dropped) markDirty();
        return doors;
    }

    private boolean isBoundTo(TileEntityDoorGeneric door) {
        for (BlockPos p : boundDoors) {
            if (p.equals(door.getPos())) return true;
        }
        return false;
    }

    /** True when this keypad can operate the door, either next to it or through a distance link. */
    private boolean controls(TileEntityDoorGeneric door) {
        if (isBoundTo(door)) return true;
        TileEntityDoorGeneric adjacent = findDoor();
        return adjacent != null && adjacent.getPos().equals(door.getPos());
    }

    private static boolean containsDoor(List<TileEntityDoorGeneric> doors, BlockPos pos) {
        for (TileEntityDoorGeneric door : doors) {
            if (door.getPos().equals(pos)) return true;
        }
        return false;
    }

    /**
     * Called when a keypad is placed. If the adjacent door is already bound to a code, the new
     * keypad adopts it so both sides of the door work without re-entering the code.
     */
    public void syncWithDoor() {
        if (world == null || world.isRemote) return;
        TileEntityDoorGeneric door = findDoor();
        if (door == null || !door.isKeypadBound()) return;
        storedCode = door.getKeypadCode();
        mode = Mode.ENTER_CODE;
        entry = "";
        showFeedback(Feedback.CODE_SYNCED);
        sync();
    }

    /** Other keypads in range that are linked to the same door. */
    private List<TileEntityDoorKeypad> keypadsSharing(TileEntityDoorGeneric door) {
        List<TileEntityDoorKeypad> keypads = new ArrayList<>();
        if (world == null) return keypads;
        for (BlockPos p : searchArea()) {
            if (p.equals(pos) || !world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityDoorKeypad)) continue;
            TileEntityDoorKeypad other = (TileEntityDoorKeypad) te;
            if (other.controls(door)) keypads.add(other);
        }
        return keypads;
    }

    /** Unbinds every controlled door and clears the code from this keypad and its neighbours. */
    private void resetCodes(List<TileEntityDoorGeneric> doors) {
        for (TileEntityDoorGeneric door : doors) {
            for (TileEntityDoorKeypad other : keypadsSharing(door)) {
                other.wipeCode();
                other.sync();
            }
            door.clearKeypadCode();
        }
        wipeCode();
    }

    private static void msg(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    // ------------------------------------------------------------ persistence / sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("stored", storedCode);
        tag.setString("entry", entry);
        tag.setInteger("mode", mode.ordinal());
        tag.setInteger("feedback", feedback.ordinal());
        tag.setInteger("feedbackTicks", feedbackTicks);
        tag.setInteger("blackScreen", blackScreenTicks);
        tag.setByteArray("cooldowns", cooldowns.clone());
        tag.setBoolean("distance", distanceMode);
        NBTTagList links = new NBTTagList();
        for (BlockPos p : boundDoors) {
            NBTTagCompound link = new NBTTagCompound();
            link.setInteger("x", p.getX());
            link.setInteger("y", p.getY());
            link.setInteger("z", p.getZ());
            links.appendTag(link);
        }
        tag.setTag("boundDoors", links);
        return tag;
    }

    /** Update packets need the LCD state but never the stored code or the door links. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("stored");
        tag.removeTag("boundDoors");
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("mode")) {
            storedCode = tag.getString("stored");
            mode = Mode.byId(tag.getInteger("mode"));
        } else if (tag.hasKey("stored")) {
            // Pre-reset saves stored the code as an int (negative = none) and had no mode.
            int old = tag.getInteger("stored");
            storedCode = old < 0 ? "" : Integer.toString(old);
            mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE;
        } else {
            storedCode = "";
            mode = Mode.SET_CODE;
        }
        entry = tag.getString("entry");
        feedback = Feedback.byId(tag.getInteger("feedback"));
        feedbackTicks = tag.getInteger("feedbackTicks");
        blackScreenTicks = tag.getInteger("blackScreen");
        distanceMode = tag.getBoolean("distance");
        boundDoors.clear();
        NBTTagList links = tag.getTagList("boundDoors", 10);
        for (int i = 0; i < links.tagCount(); i++) {
            NBTTagCompound link = links.getCompoundTagAt(i);
            boundDoors.add(new BlockPos(link.getInteger("x"), link.getInteger("y"), link.getInteger("z")));
        }
        byte[] cd = tag.getByteArray("cooldowns");
        if (cd.length == BUTTONS) {
            System.arraycopy(cd, 0, cooldowns, 0, BUTTONS);
        } else {
            Arrays.fill(cooldowns, (byte) 0);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 16384D;
    }
}
