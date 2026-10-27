package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
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
 */
public class TileEntityDoorKeypad extends TileEntitySynced implements ITickable {

    public static final int CODE_LENGTH = 6;
    public static final int BUTTONS = 12;
    public static final int SEARCH_RADIUS = 8;
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
        TileEntityDoorGeneric door = findDoor();
        if (door != null && door.getState() != DoorState.CLOSED && isAuthorized(door)) {
            door.request(false);
        }
        entry = "";
        showFeedback(Feedback.NONE);
    }

    /** Enter stores a new code, validates an existing one, or completes a reset. */
    private void pressEnter() {
        if (entry.isEmpty()) return;
        String entered = entry;
        entry = "";
        TileEntityDoorGeneric door = findDoor();
        switch (mode) {
            case SET_CODE:
                setCode(entered, door);
                break;
            case RESET_ENTER:
                resetCode(entered, door);
                break;
            default:
                openWithCode(entered, door);
        }
    }

    /**
     * Stores the keypad's first code. Any code can be set; a keypad whose code differs from the
     * door's binding simply cannot open it (the door keeps the first code it was given).
     */
    private void setCode(String entered, @Nullable TileEntityDoorGeneric door) {
        storedCode = entered;
        mode = Mode.ENTER_CODE;
        if (door != null && !door.isKeypadBound()) door.bindKeypadCode(entered);
        showFeedback(Feedback.CODE_SET);
    }

    /** Clears the door and every linked keypad once the current code has been confirmed. */
    private void resetCode(String entered, @Nullable TileEntityDoorGeneric door) {
        String expected = door != null && door.isKeypadBound() ? door.getKeypadCode() : storedCode;
        if (!expected.isEmpty() && expected.equals(entered)) {
            resetCodes(door);
            showFeedback(Feedback.RESET_DONE);
        } else {
            showFeedback(Feedback.DENIED);
        }
    }

    /** Opens (or closes) the door when both the keypad code and the door's bound code match. */
    private void openWithCode(String entered, @Nullable TileEntityDoorGeneric door) {
        if (door == null || !entered.equals(storedCode)) {
            showFeedback(Feedback.DENIED);
            return;
        }
        // A keypad that was configured before its door existed adopts the door on first use.
        if (!door.isKeypadBound()) door.bindKeypadCode(storedCode);
        if (!entered.equals(door.getKeypadCode())) {
            showFeedback(Feedback.DENIED);
            return;
        }
        showFeedback(Feedback.SUCCESS);
        door.toggle();
    }

    private boolean isAuthorized(TileEntityDoorGeneric door) {
        return !storedCode.isEmpty() && storedCode.equals(door.getKeypadCode());
    }

    /** Server side: advances the screwdriver reset sequence. */
    public void onScrewdriverUse() {
        if (world == null || world.isRemote || !hasStoredCode()) return;
        entry = "";
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
            TileEntityDoorGeneric linked = other.findDoor();
            if (linked != null && linked.getPos().equals(door.getPos())) keypads.add(other);
        }
        return keypads;
    }

    /** Unbinds the door and clears the code from this keypad and every keypad linked to it. */
    private void resetCodes(@Nullable TileEntityDoorGeneric door) {
        if (door != null) {
            for (TileEntityDoorKeypad other : keypadsSharing(door)) {
                other.wipeCode();
                other.sync();
            }
            door.clearKeypadCode();
        }
        wipeCode();
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
        return tag;
    }

    /** Update packets need the LCD state but never the stored code itself. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("stored");
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
