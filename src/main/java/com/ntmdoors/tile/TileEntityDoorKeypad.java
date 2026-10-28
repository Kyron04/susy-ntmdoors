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
 * cycle its binding mode: off, distance (one door, one code) and multi (up to nine doors, one code
 * each, picked with the 1-9 keys). In either binding mode, sneak + right click closed doors to
 * link them. Once the pad has a code or a linked door the mode is fixed and the screwdriver can
 * only reset it.</p>
 */
public class TileEntityDoorKeypad extends TileEntitySynced implements ITickable {

    public static final int CODE_LENGTH = 6;
    public static final int BUTTONS = 12;
    public static final int SEARCH_RADIUS = 8;
    /** How far a door may be from the keypad to be bound and operated remotely. */
    public static final double MAX_BIND_DISTANCE = 32.0;
    /** Number of independently coded doors a multi-binding keypad can hold. */
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
        /** Up to nine doors anywhere within range, each with its own code. */
        MULTI;

        static BindMode byId(int id) {
            BindMode[] v = values();
            return v[Math.floorMod(id, v.length)];
        }

        BindMode next() {
            BindMode[] v = values();
            return v[(ordinal() + 1) % v.length];
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

        static Mode byId(int id) {
            Mode[] v = values();
            return v[Math.floorMod(id, v.length)];
        }
    }

    /** Transient message on the LCD. */
    public enum Feedback {
        NONE, CODE_SET, RESET_DONE, SUCCESS, DENIED, CODE_SYNCED, NO_DOOR;

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

    /** Binding behaviour chosen with the screwdriver before the pad is used. Synced for the LCD. */
    private BindMode bindMode = BindMode.NONE;
    /** True while the player is walking around linking doors. Synced so the client agrees. */
    private boolean linking;
    /** Door of the single distance binding. Server-only; validated when used. */
    private final List<BlockPos> boundDoors = new ArrayList<>();
    /** Multi-binding slots. Positions are server-only, the codes never leave the server. */
    private final BlockPos[] slotDoors = new BlockPos[MULTI_SLOTS];
    private final String[] slotCodes = new String[MULTI_SLOTS];
    /** Slot picked with a digit key in multi mode, or -1. Synced for the LCD. */
    private int selectedSlot = -1;
    /** Synced flag so clients know a multi pad is already coded without receiving the codes. */
    private boolean anySlotCode;

    public TileEntityDoorKeypad() {
        Arrays.fill(slotCodes, "");
    }

    // ------------------------------------------------------------ state queries

    public Mode getMode() {
        return mode;
    }

    /** The digits typed so far, for the LCD. Empty when nothing has been entered. */
    public String getEntry() {
        return entry;
    }

    /** Whether a code has been set, on either the single-code path or a multi slot. */
    public boolean hasStoredCode() {
        return mode != Mode.SET_CODE || anySlotCode;
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

    /** Selected multi slot, or -1. */
    public int getSelectedSlot() {
        return selectedSlot;
    }

    /** True once the pad has a code or at least one linked door. Locks the binding mode. */
    public boolean isBound() {
        return hasStoredCode() || hasLinks();
    }

    private boolean hasLinks() {
        if (!boundDoors.isEmpty()) return true;
        for (BlockPos p : slotDoors) {
            if (p != null) return true;
        }
        return false;
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

        // Any key ends the linking session: from here on the player is entering codes.
        linking = false;

        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK,
                SoundCategory.BLOCKS, 0.5F, 0.9F + world.rand.nextFloat() * 0.2F);

        // Any key dismisses the "Reset code?" prompt; the key then acts normally.
        if (mode == Mode.RESET_CONFIRM) {
            mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE;
            entry = "";
            selectedSlot = -1;
            showFeedback(Feedback.NONE);
        }

        boolean slotKey = bindMode == BindMode.MULTI && selectedSlot < 0 && mode != Mode.RESET_ENTER;
        if (slotKey && id < MULTI_SLOTS) {
            // In multi mode the first digit picks the door; the following digits are its code.
            int slot = MULTI_SLOTS - id; // digit 1..9
            if (slotDoors[slot - 1] == null) {
                showFeedback(Feedback.NO_DOOR);
            } else {
                selectedSlot = slot - 1;
                entry = "";
                showFeedback(Feedback.NONE);
            }
        } else if (slotKey && (id == BUTTON_ZERO || id == BUTTON_ENTER)) {
            // Nothing selected yet: these keys have no meaning.
        } else {
            switch (id) {
                case BUTTON_RESET:
                    pressReset();
                    break;
                case BUTTON_ZERO:
                    enterDigit(0);
                    break;
                case BUTTON_ENTER:
                    if (bindMode == BindMode.MULTI && selectedSlot >= 0) {
                        pressMultiEnter();
                    } else {
                        pressEnter();
                    }
                    break;
                default: // digits 1-9: id 0 -> 9, id 8 -> 1
                    enterDigit(9 - id);
            }
        }
        sync();
    }

    /** R closes an open door when this keypad is authorised, otherwise it clears the entry. */
    private void pressReset() {
        if (mode == Mode.RESET_ENTER) {
            mode = storedCode.isEmpty() ? Mode.SET_CODE : Mode.ENTER_CODE;
            entry = "";
            selectedSlot = -1;
            showFeedback(Feedback.NONE);
            return;
        }
        if (bindMode == BindMode.MULTI) {
            if (selectedSlot >= 0) {
                closeSlot(selectedSlot);
                selectedSlot = -1;
            } else {
                for (int i = 0; i < MULTI_SLOTS; i++) closeSlot(i);
            }
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
        if (bindMode == BindMode.MULTI) return; // multi codes go through pressMultiEnter
        List<TileEntityDoorGeneric> doors = controlDoors();
        if (mode == Mode.SET_CODE) {
            setCode(entered, doors);
        } else {
            openWithCode(entered, doors);
        }
    }

    /** Enter in multi mode: the selected door's code is stored on first use, then validated. */
    private void pressMultiEnter() {
        if (selectedSlot < 0 || entry.isEmpty()) {
            selectedSlot = -1;
            return;
        }
        int slot = selectedSlot;
        String entered = entry;
        entry = "";
        selectedSlot = -1;

        BlockPos doorPos = slotDoors[slot];
        if (doorPos == null) return;
        boolean loaded = world != null && world.isBlockLoaded(doorPos);
        TileEntityDoorGeneric door = slotDoor(slot);
        if (loaded && door == null) {
            // The door is gone; slotDoor has just freed the slot.
            showFeedback(Feedback.DENIED);
            return;
        }

        if (slotCodes[slot].isEmpty()) {
            if (door != null && door.isKeypadBound() && !entered.equals(door.getKeypadCode())) {
                showFeedback(Feedback.DENIED);
                return;
            }
            slotCodes[slot] = entered;
            anySlotCode = true;
            if (door != null && !door.isKeypadBound()) door.bindKeypadCode(entered);
            showFeedback(Feedback.CODE_SET);
            markDirty();
            return;
        }

        if (door == null || !entered.equals(slotCodes[slot])) {
            showFeedback(Feedback.DENIED);
            return;
        }
        if (!door.isKeypadBound()) door.bindKeypadCode(slotCodes[slot]);
        if (!slotCodes[slot].equals(door.getKeypadCode())) {
            showFeedback(Feedback.DENIED);
            return;
        }
        showFeedback(Feedback.SUCCESS);
        door.toggle();
    }

    /** Closes the multi door in a slot, if its code authorises it. */
    private void closeSlot(int slot) {
        String code = slotCodes[slot];
        if (code.isEmpty()) return;
        TileEntityDoorGeneric door = slotDoor(slot);
        if (door != null && door.getState() != DoorState.CLOSED && code.equals(door.getKeypadCode())) {
            door.request(false);
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
    private void resetCode(String entered) {
        if (isResetCode(entered)) {
            resetCodes(controlDoors());
            showFeedback(Feedback.RESET_DONE);
        } else {
            showFeedback(Feedback.DENIED);
        }
    }

    /** The reset accepts any code the pad knows: the single code or any multi slot code. */
    private boolean isResetCode(String entered) {
        if (!storedCode.isEmpty() && storedCode.equals(entered)) return true;
        for (String code : slotCodes) {
            if (!code.isEmpty() && code.equals(entered)) return true;
        }
        for (TileEntityDoorGeneric door : controlDoors()) {
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
        selectedSlot = -1;
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
        selectedSlot = -1;
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

        if (bindMode == BindMode.MULTI) {
            pruneSlots();
            int existing = slotOf(door);
            if (existing >= 0) {
                TileEntityDoorGeneric linked = slotDoor(existing);
                boolean valid = linked != null
                        && (slotCodes[existing].isEmpty() || slotCodes[existing].equals(linked.getKeypadCode()));
                if (valid) {
                    msg(player, "ntmdoors.msg.bind_multi_success", existing + 1);
                    return true;
                }
                // The door at that position was replaced: the old slot code no longer applies.
                slotDoors[existing] = null;
                slotCodes[existing] = "";
                anySlotCode = hasAnySlotCode();
            }
            if (door.isKeypadBound()) {
                msg(player, "ntmdoors.msg.bind_code");
                return false;
            }
            int free = firstFreeSlot();
            if (free < 0) {
                msg(player, "ntmdoors.msg.bind_full");
                return false;
            }
            slotDoors[free] = door.getPos();
            markDirty();
            msg(player, "ntmdoors.msg.bind_multi_success", free + 1);
            return true;
        }

        // Distance mode: exactly one door.
        if (isBoundTo(door)) {
            msg(player, "ntmdoors.msg.bind_success");
            return true;
        }
        if (!boundDoors.isEmpty()) {
            msg(player, "ntmdoors.msg.bind_one");
            return false;
        }
        if (door.isKeypadBound() && (storedCode.isEmpty() || !storedCode.equals(door.getKeypadCode()))) {
            msg(player, "ntmdoors.msg.bind_code");
            return false;
        }
        boundDoors.add(door.getPos());
        if (!storedCode.isEmpty() && !door.isKeypadBound()) door.bindKeypadCode(storedCode);
        markDirty();
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

    /** Drops the keypad back to "unconfigured", without touching any door. */
    private void clearBindings() {
        storedCode = "";
        entry = "";
        mode = Mode.SET_CODE;
        feedback = Feedback.NONE;
        feedbackTicks = 0;
        blackScreenTicks = 0;
        selectedSlot = -1;
        linking = false;
        bindMode = BindMode.NONE;
        boundDoors.clear();
        Arrays.fill(slotDoors, null);
        Arrays.fill(slotCodes, "");
        anySlotCode = false;
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
     * Every door this keypad can operate. Unconfigured pads use the adjacent door, distance pads
     * use their single link and multi pads use their numbered slots. Stale links are dropped as
     * they are found.
     */
    public List<TileEntityDoorGeneric> controlDoors() {
        List<TileEntityDoorGeneric> doors = new ArrayList<>();
        if (world == null) return doors;

        if (bindMode == BindMode.MULTI) {
            for (int i = 0; i < MULTI_SLOTS; i++) {
                TileEntityDoorGeneric door = slotDoor(i);
                if (door != null && !containsDoor(doors, door.getPos())) doors.add(door);
            }
            return doors;
        }
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
        if (dropped) markDirty();
        return doors;
    }

    /** The door in a multi slot, or null. Frees the slot if the door has been destroyed. */
    @Nullable
    private TileEntityDoorGeneric slotDoor(int slot) {
        BlockPos p = slotDoors[slot];
        if (p == null || world == null || !world.isBlockLoaded(p)) return null;
        TileEntity te = world.getTileEntity(p);
        if (te instanceof TileEntityDoorGeneric) return (TileEntityDoorGeneric) te;
        slotDoors[slot] = null;
        slotCodes[slot] = "";
        if (selectedSlot == slot) selectedSlot = -1;
        anySlotCode = hasAnySlotCode();
        markDirty();
        return null;
    }

    private boolean hasAnySlotCode() {
        for (String code : slotCodes) {
            if (!code.isEmpty()) return true;
        }
        return false;
    }

    /** Drops slots whose doors have been destroyed. */
    private void pruneSlots() {
        for (int i = 0; i < MULTI_SLOTS; i++) {
            slotDoor(i);
        }
    }

    private int slotOf(TileEntityDoorGeneric door) {
        for (int i = 0; i < MULTI_SLOTS; i++) {
            if (door.getPos().equals(slotDoors[i])) return i;
        }
        return -1;
    }

    private int firstFreeSlot() {
        for (int i = 0; i < MULTI_SLOTS; i++) {
            if (slotDoors[i] == null) return i;
        }
        return -1;
    }

    private boolean isBoundTo(TileEntityDoorGeneric door) {
        for (BlockPos p : boundDoors) {
            if (p.equals(door.getPos())) return true;
        }
        return false;
    }

    /** True when this keypad can operate the door in its current configuration. */
    private boolean controls(TileEntityDoorGeneric door) {
        switch (bindMode) {
            case MULTI:
                return slotOf(door) >= 0;
            case DISTANCE:
                return isBoundTo(door);
            default:
                TileEntityDoorGeneric adjacent = findDoor();
                return adjacent != null && adjacent.getPos().equals(door.getPos());
        }
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
        tag.setInteger("selected", selectedSlot);
        tag.setBoolean("anySlotCode", anySlotCode);

        NBTTagList links = new NBTTagList();
        for (BlockPos p : boundDoors) {
            NBTTagCompound link = new NBTTagCompound();
            link.setInteger("x", p.getX());
            link.setInteger("y", p.getY());
            link.setInteger("z", p.getZ());
            links.appendTag(link);
        }
        tag.setTag("boundDoors", links);

        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < MULTI_SLOTS; i++) {
            if (slotDoors[i] == null && slotCodes[i].isEmpty()) continue;
            NBTTagCompound slot = new NBTTagCompound();
            slot.setByte("slot", (byte) i);
            if (slotDoors[i] != null) {
                slot.setInteger("x", slotDoors[i].getX());
                slot.setInteger("y", slotDoors[i].getY());
                slot.setInteger("z", slotDoors[i].getZ());
            }
            slot.setString("code", slotCodes[i]);
            slots.appendTag(slot);
        }
        tag.setTag("slots", slots);
        return tag;
    }

    /** Update packets need the LCD state but never the codes or the door positions. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("stored");
        tag.removeTag("boundDoors");
        tag.removeTag("slots");
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

        if (tag.hasKey("bindMode")) {
            bindMode = BindMode.byId(tag.getInteger("bindMode"));
        } else if (tag.getBoolean("distance") || tag.getTagList("boundDoors", 10).tagCount() > 0) {
            // 1.0.20 saves only had the distance toggle.
            bindMode = BindMode.DISTANCE;
        } else {
            bindMode = BindMode.NONE;
        }
        linking = tag.getBoolean("linking");
        selectedSlot = tag.hasKey("selected") ? tag.getInteger("selected") : -1;
        anySlotCode = tag.getBoolean("anySlotCode");

        boundDoors.clear();
        NBTTagList links = tag.getTagList("boundDoors", 10);
        for (int i = 0; i < links.tagCount(); i++) {
            NBTTagCompound link = links.getCompoundTagAt(i);
            boundDoors.add(new BlockPos(link.getInteger("x"), link.getInteger("y"), link.getInteger("z")));
        }

        Arrays.fill(slotDoors, null);
        Arrays.fill(slotCodes, "");
        NBTTagList slots = tag.getTagList("slots", 10);
        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slot = slots.getCompoundTagAt(i);
            int index = slot.getByte("slot");
            if (index < 0 || index >= MULTI_SLOTS) continue;
            if (slot.hasKey("x")) {
                slotDoors[index] = new BlockPos(slot.getInteger("x"), slot.getInteger("y"), slot.getInteger("z"));
            }
            slotCodes[index] = slot.getString("code");
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
