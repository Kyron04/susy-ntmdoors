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
 * <p>Doors can also be bound from a distance. Sneak + right click the keypad with a screwdriver to
 * cycle its binding mode: off, distance (one door) and multi (up to nine doors). Both range modes
 * use a single code. In either mode, sneak + right click closed doors to link them; the pad then
 * asks to confirm the links before accepting the code. Once the pad has a code or a linked door
 * the mode is fixed and the screwdriver can only reset it.</p>
 */
public class TileEntityDoorKeypad extends TileEntitySynced implements ITickable {

    public static final int CODE_LENGTH = 6;
    public static final int BUTTONS = 12;
    public static final int SEARCH_RADIUS = 8;
    /** How far a door may be from the keypad to be bound and operated remotely. */
    public static final double MAX_BIND_DISTANCE = 32.0;
    /** Maximum number of doors a multi-binding keypad can hold. */
    public static final int MULTI_SLOTS = 9;
    /** Bottom row of the keypad model: reset, zero, enter. */
    public static final int BUTTON_RESET = 9;
    public static final int BUTTON_ZERO = 10;
    public static final int BUTTON_ENTER = 11;

    /** How the keypad handles doors that are not the one it is mounted next to. */
    public enum BindMode {
        /** Adjacent door only, single code (the classic keypad). */
        NONE,
        /** One door anywhere within range, single code. */
        DISTANCE,
        /** Up to nine doors anywhere within range, one shared code. */
        MULTI;

        private static final BindMode[] VALUES = values();

        static BindMode byId(int id) {
            return VALUES[Math.floorMod(id, VALUES.length)];
        }

        BindMode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }

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

        private static final Mode[] VALUES = values();

        static Mode byId(int id) {
            return VALUES[Math.floorMod(id, VALUES.length)];
        }
    }

    /** Transient message on the LCD. */
    public enum Feedback {
        NONE, CODE_SET, RESET_DONE, SUCCESS, DENIED, CODE_SYNCED;

        private static final Feedback[] VALUES = values();

        static Feedback byId(int id) {
            return VALUES[Math.floorMod(id, VALUES.length)];
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

    /** Binding behaviour chosen with the screwdriver before the pad is used. Synced for the LCD. */
    private BindMode bindMode = BindMode.NONE;
    /** True while the player is walking around linking doors. Synced so the client agrees. */
    private boolean linking;
    /** True after linking, until the player accepts the links with Enter. Synced for the LCD. */
    private boolean pendingConfirm;
    /** Doors linked in distance or multi mode. Server-only; validated when used. */
    private final List<BlockPos> boundDoors = new ArrayList<>();
    /** Synced link count so clients can draw the confirmation without receiving positions. */
    private int doorCount;

    // ------------------------------------------------------------ state queries

    public Mode getMode() {
        return mode;
    }

    /** The digits typed so far, for the LCD. Empty when nothing has been entered. */
    public String getEntry() {
        return entry;
    }

    /** Whether a code has been set. */
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

    public BindMode getBindMode() {
        return bindMode;
    }

    /** True while doors may be linked: the pad is in a binding mode and the session is armed. */
    public boolean isLinking() {
        return linking;
    }

    /** True after linking, until Enter accepts the linked doors. */
    public boolean isPendingConfirm() {
        return pendingConfirm;
    }

    /** Number of doors linked in distance or multi mode. */
    public int getDoorCount() {
        return doorCount;
    }

    /** True once the pad has a code or at least one linked door. Locks the binding mode. */
    public boolean isBound() {
        return hasStoredCode() || doorCount > 0;
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

        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK,
                SoundCategory.BLOCKS, 0.5F, 0.9F + world.rand.nextFloat() * 0.2F);

        // Any key dismisses the "Reset code?" prompt; the key then acts normally.
        if (mode == Mode.RESET_CONFIRM) {
            mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE;
            entry = "";
            showFeedback(Feedback.NONE);
        }

        // Linked doors must be accepted with Enter before the pad asks for their code. Other keys
        // are ignored so the player can still walk away and link more doors.
        if (pendingConfirm) {
            if (id == BUTTON_ENTER) {
                linking = false;
                pendingConfirm = false;
                entry = "";
                showFeedback(Feedback.NONE);
            }
            sync();
            return;
        }

        // Any other key ends the linking session: from here on the player is entering codes.
        linking = false;

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
            mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE;
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
        if (mode == Mode.RESET_ENTER) {
            resetCode(entered);
            return;
        }
        List<TileEntityDoorGeneric> doors = controlDoors();
        if (mode == Mode.SET_CODE) {
            setCode(entered, doors);
        } else {
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
        pendingConfirm = false;
        for (TileEntityDoorGeneric door : doors) {
            if (!door.isKeypadBound()) door.bindKeypadCode(entered);
        }
        showFeedback(Feedback.CODE_SET);
    }

    /** Clears the door and every linked keypad once the current code has been confirmed. */
    private void resetCode(String entered) {
        List<TileEntityDoorGeneric> doors = controlDoors();
        if (isResetCode(entered, doors)) {
            resetCodes(doors);
            showFeedback(Feedback.RESET_DONE);
        } else {
            showFeedback(Feedback.DENIED);
        }
    }

    /** The reset accepts the pad's code or any code one of its doors still carries. */
    private boolean isResetCode(String entered, List<TileEntityDoorGeneric> doors) {
        if (!storedCode.isEmpty() && storedCode.equals(entered)) return true;
        for (TileEntityDoorGeneric door : doors) {
            if (door.isKeypadBound() && door.getKeypadCode().equals(entered)) return true;
        }
        return false;
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
        linking = false;
        advanceReset();
        showFeedback(Feedback.NONE);
        sync();
    }

    /**
     * Server side: sneak + right click with the screwdriver. Cycles the binding mode while the pad
     * is unused; once it is bound, the only screwdriver action is the reset sequence.
     */
    public void onScrewdriverSneak(EntityPlayer player) {
        if (world == null || world.isRemote) return;
        entry = "";
        showFeedback(Feedback.NONE);
        if (isBound()) {
            linking = false;
            if (!hasStoredCode()) {
                // Links without a code were never usable: the screwdriver clears them.
                clearBindings();
                msg(player, "ntmdoors.msg.bind_cleared");
            } else {
                advanceReset();
            }
            sync();
            return;
        }
        bindMode = bindMode.next();
        linking = bindMode != BindMode.NONE;
        pendingConfirm = false;
        msg(player, modeMessage(bindMode));
        sync();
    }

    /** Walks the reset prompt: confirm, then current code, then cancel. */
    private void advanceReset() {
        switch (mode) {
            case RESET_CONFIRM:
                mode = Mode.RESET_ENTER;
                break;
            case RESET_ENTER:
                mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE; // cancel
                break;
            default:
                mode = Mode.RESET_CONFIRM;
        }
    }

    /**
     * Server side: links a door while the player is linking. The door must be closed, within range
     * and not already locked to another keypad's code.
     */
    public boolean bindDoor(TileEntityDoorGeneric door, EntityPlayer player) {
        if (world == null || world.isRemote || door == null) return false;
        if (!linking || bindMode == BindMode.NONE) return false;
        if (mode == Mode.RESET_CONFIRM || mode == Mode.RESET_ENTER) return false;
        if (door.getState() != DoorState.CLOSED) {
            msg(player, "ntmdoors.msg.bind_open");
            return false;
        }
        if (pos.distanceSq(door.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            msg(player, "ntmdoors.msg.bind_far");
            return false;
        }

        int existing = boundDoors.indexOf(door.getPos());
        if (existing >= 0) {
            pendingConfirm = true;
            sync();
            if (bindMode == BindMode.MULTI) {
                msgMultiBound(player, boundDoors.size());
            } else {
                msg(player, "ntmdoors.msg.bind_success");
            }
            return true;
        }

        if (door.isKeypadBound()) {
            msg(player, "ntmdoors.msg.bind_code");
            return false;
        }

        if (bindMode == BindMode.DISTANCE) {
            if (!boundDoors.isEmpty()) {
                msg(player, "ntmdoors.msg.bind_one");
                return false;
            }
        } else if (boundDoors.size() >= MULTI_SLOTS) {
            msg(player, "ntmdoors.msg.bind_full");
            return false;
        }

        boundDoors.add(door.getPos());
        doorCount = boundDoors.size();
        pendingConfirm = true;
        sync();
        if (bindMode == BindMode.MULTI) {
            msgMultiBound(player, boundDoors.size());
        } else {
            msg(player, "ntmdoors.msg.bind_success");
        }
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

    /** Drops the keypad back to "unconfigured", without touching any door. */
    private void clearBindings() {
        storedCode = "";
        entry = "";
        mode = Mode.SET_CODE;
        showFeedback(Feedback.NONE);
        linking = false;
        pendingConfirm = false;
        bindMode = BindMode.NONE;
        boundDoors.clear();
        doorCount = 0;
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
     * Every door this keypad can operate. Unconfigured pads use the adjacent door; distance and
     * multi pads use their linked doors. Stale links are dropped as they are found.
     */
    public List<TileEntityDoorGeneric> controlDoors() {
        List<TileEntityDoorGeneric> doors = new ArrayList<>();
        if (world == null) return doors;

        if (bindMode == BindMode.NONE) {
            TileEntityDoorGeneric adjacent = findDoor();
            if (adjacent != null) doors.add(adjacent);
            return doors;
        }

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
        if (dropped) {
            doorCount = boundDoors.size();
            markDirty();
        }
        return doors;
    }

    private static boolean containsDoor(List<TileEntityDoorGeneric> doors, BlockPos pos) {
        for (TileEntityDoorGeneric door : doors) {
            if (door.getPos().equals(pos)) return true;
        }
        return false;
    }

    /**
     * Called when a keypad is placed. If the adjacent door is already bound to a code, the new
     * keypad adopts it so both sides of the door work without re-entering the code. Auto-sync is
     * exempt from the confirmation prompt: the code is already known.
     */
    public void syncWithDoor() {
        if (world == null || world.isRemote) return;
        TileEntityDoorGeneric door = findDoor();
        if (door == null || !door.isKeypadBound()) return;
        storedCode = door.getKeypadCode();
        mode = Mode.ENTER_CODE;
        entry = "";
        pendingConfirm = false;
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

    /** True when this keypad can operate the door in its current configuration. */
    private boolean controls(TileEntityDoorGeneric door) {
        if (bindMode == BindMode.NONE) {
            TileEntityDoorGeneric adjacent = findDoor();
            return adjacent != null && adjacent.getPos().equals(door.getPos());
        }
        for (BlockPos p : boundDoors) {
            if (p.equals(door.getPos())) return true;
        }
        return false;
    }

    /** Unbinds every controlled door and clears the code from this keypad and its neighbours. */
    private void resetCodes(List<TileEntityDoorGeneric> doors) {
        for (TileEntityDoorGeneric door : doors) {
            for (TileEntityDoorKeypad other : keypadsSharing(door)) {
                other.clearBindings();
                other.sync();
            }
            door.clearKeypadCode();
        }
        clearBindings();
    }

    private static String modeMessage(BindMode mode) {
        switch (mode) {
            case DISTANCE:
                return "ntmdoors.msg.mode_distance";
            case MULTI:
                return "ntmdoors.msg.mode_multi";
            default:
                return "ntmdoors.msg.mode_off";
        }
    }

    /** Chat feedback for a multi link, worded for the number of doors now on the pad. */
    private static void msgMultiBound(EntityPlayer player, int count) {
        if (count == 1) {
            msg(player, "ntmdoors.msg.bind_multi_success_one");
        } else {
            msg(player, "ntmdoors.msg.bind_multi_success", count);
        }
    }

    private static void msg(EntityPlayer player, String key, Object... args) {
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
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
        tag.setInteger("bindMode", bindMode.ordinal());
        tag.setBoolean("linking", linking);
        tag.setBoolean("pendingConfirm", pendingConfirm);
        tag.setInteger("doorCount", doorCount);

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

    /** Update packets need the LCD state but never the code or the door positions. */
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

        NBTTagList links = tag.getTagList("boundDoors", 10);
        if (tag.hasKey("bindMode")) {
            bindMode = BindMode.byId(tag.getInteger("bindMode"));
        } else if (tag.getBoolean("distance") || links.tagCount() > 0) {
            // 1.0.20 saves only had the distance toggle.
            bindMode = BindMode.DISTANCE;
        } else {
            bindMode = BindMode.NONE;
        }
        linking = tag.getBoolean("linking");
        pendingConfirm = tag.getBoolean("pendingConfirm");

        boundDoors.clear();
        for (int i = 0; i < links.tagCount(); i++) {
            NBTTagCompound link = links.getCompoundTagAt(i);
            boundDoors.add(new BlockPos(link.getInteger("x"), link.getInteger("y"), link.getInteger("z")));
        }
        doorCount = tag.hasKey("doorCount") ? tag.getInteger("doorCount") : boundDoors.size();

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
