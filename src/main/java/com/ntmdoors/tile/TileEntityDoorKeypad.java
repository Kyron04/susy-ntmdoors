package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.door.DoorState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Server-authoritative keypad. The linked sliding blast door can be opened with the stored code
 * or locked by setting a new one. Breaking the keypad removes the lock from the door.
 */
public class TileEntityDoorKeypad extends TileEntitySynced implements ITickable {

    public static final int CODE_LENGTH = 6;
    public static final int BUTTONS = 12;
    public static final int SEARCH_RADIUS = 8;
    /** Bottom row of the keypad model: reset, zero, enter. */
    public static final int BUTTON_RESET = 9;
    public static final int BUTTON_ZERO = 10;
    public static final int BUTTON_ENTER = 11;

    private final byte[] code = new byte[CODE_LENGTH];
    private int storedCode = -1;
    /** Mirrors {@code storedCode >= 0} on the client, which never receives the code itself. */
    private boolean syncedHasCode;
    private boolean settingCode = true;
    private int successTicks;
    private int failTicks;
    private int codeSetTicks;
    private int blackScreenTicks;
    private final byte[] cooldowns = new byte[BUTTONS];

    @Nullable
    private BlockPos doorPos;

    public TileEntityDoorKeypad() {
        Arrays.fill(code, (byte) -1);
    }

    // ------------------------------------------------------------ state queries

    public boolean isSettingCode() {
        return settingCode;
    }

    /** Whether a code has been set. Available on the client, where the code value itself is hidden. */
    public boolean hasStoredCode() {
        return storedCode >= 0 || syncedHasCode;
    }

    public int getSuccessTicks() {
        return successTicks;
    }

    public int getFailTicks() {
        return failTicks;
    }

    public int getCooldown(int id) {
        return id >= 0 && id < BUTTONS ? cooldowns[id] : 0;
    }

    public int getCodeSetTicks() {
        return codeSetTicks;
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
        if (successTicks > 0) successTicks--;
        if (failTicks > 0) failTicks--;
        if (codeSetTicks > 0) {
            codeSetTicks--;
            if (codeSetTicks == 0) {
                blackScreenTicks = 20;
            }
        }
        if (blackScreenTicks > 0) blackScreenTicks--;
    }

    // ------------------------------------------------------------ button input

    /** Server side: resolves the local hit to a button id, or -1 if the click missed the keys. */
    public int buttonAt(float hitX, float hitY, float hitZ) {
        if (world == null) return -1;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorKeypad)) return -1;
        EnumFacing f = state.getValue(BlockDoorKeypad.FACING);

        double cx = hitX - 0.5;
        double cz = hitZ - 0.5;
        double mx, mz;
        switch (f) {
            case EAST:
                mx = cz;
                mz = -cx;
                break;
            case SOUTH:
                mx = -cx;
                mz = -cz;
                break;
            case WEST:
                mx = -cz;
                mz = cx;
                break;
            default:
                mx = cx;
                mz = cz;
        }
        double xModel = mx / 0.35;
        double yModel = (hitY - 0.29375) / 0.4125;

        for (int x = 0; x < 3; x++) {
            double x1 = 0.15625 - 0.234375 * x;
            double x2 = 0.3125 - 0.234375 * x;
            if (xModel < x1 || xModel > x2) continue;
            for (int y = 0; y < 4; y++) {
                double y1 = 0.042969 + 0.175781 * y;
                double y2 = 0.160156 + 0.175781 * y;
                if (yModel >= y1 && yModel <= y2) {
                    return (3 - y) * 3 + x;
                }
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

    /** R closes an open door, or re-enters code entry when the typed code matches the stored one. */
    private void pressReset() {
        TileEntityDoorGeneric door = findDoor();
        if (door != null && door.getState() != DoorState.CLOSED) {
            door.toggle();
        } else if (storedCode == buildIntCode()) {
            settingCode = true;
        }
        clearCode();
    }

    /** Enter stores a new code while setting one, otherwise it validates the entered code. */
    private void pressEnter() {
        int entered = buildIntCode();
        if (settingCode || storedCode < 0) {
            storeCode(entered);
        } else if (storedCode == entered) {
            TileEntityDoorGeneric door = findDoor();
            if (door != null) {
                successTicks = 20;
                failTicks = 0;
                door.toggle();
            } else {
                successTicks = 0;
                failTicks = 20;
            }
        } else {
            successTicks = 0;
            failTicks = 20;
        }
        clearCode();
    }

    private void storeCode(int entered) {
        if (entered < 0) return;
        storedCode = entered;
        successTicks = 0;
        failTicks = 0;
        settingCode = false;
        codeSetTicks = 20;
        blackScreenTicks = 0;
        TileEntityDoorGeneric door = findDoor();
        if (door != null) {
            door.setKeypadLocked(true);
            propagateCode(door);
        }
    }

    private void enterDigit(int digit) {
        if (code[code.length - 1] >= 0) return; // full
        successTicks = 0;
        failTicks = 0;
        for (int i = code.length - 1; i > 0; i--) {
            code[i] = code[i - 1];
            code[i - 1] = -1;
        }
        code[0] = (byte) digit;
    }

    public int buildIntCode() {
        if (code[0] < 0) return -1;
        int num = 0;
        for (int i = code.length - 1; i >= 0; i--) {
            if (code[i] < 0) continue;
            num = num * 10 + code[i];
        }
        return num;
    }

    private void clearCode() {
        Arrays.fill(code, (byte) -1);
    }

    // ------------------------------------------------------------ door linking

    @Nullable
    public TileEntityDoorGeneric findDoor() {
        if (world == null) return null;
        if (doorPos != null) {
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                return (TileEntityDoorGeneric) te;
            }
            doorPos = null;
        }

        TileEntityDoorGeneric best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : searchArea()) {
            if (!world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            TileEntityDoorGeneric core;
            if (te instanceof TileEntityDoorGeneric) {
                core = (TileEntityDoorGeneric) te;
            } else if (te instanceof TileEntityDoorPart) {
                // Large doors put the core far from the keypad, so measure the distance to the
                // nearest cell instead of the nearest core. Otherwise a smaller door nearby wins.
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
        if (best != null) doorPos = best.getPos();
        return best;
    }

    private Iterable<? extends BlockPos> searchArea() {
        return BlockPos.getAllInBoxMutable(
                pos.add(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                pos.add(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS));
    }

    /** Copies the newly set code to every other keypad linked to the same door. */
    private void propagateCode(TileEntityDoorGeneric door) {
        if (world == null) return;
        for (BlockPos p : searchArea()) {
            if (p.equals(pos) || !world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityDoorKeypad)) continue;
            TileEntityDoorKeypad other = (TileEntityDoorKeypad) te;
            TileEntityDoorGeneric linked = other.findDoor();
            if (linked != null && linked.getPos().equals(door.getPos())) {
                other.adoptCode(storedCode);
            }
        }
    }

    private void adoptCode(int code) {
        storedCode = code;
        settingCode = false;
        clearCode();
        successTicks = 0;
        failTicks = 0;
        sync();
    }

    // ------------------------------------------------------------ lifecycle

    /**
     * Removes the keypad lock from the linked door when the last configured keypad is destroyed.
     * An unconfigured keypad never locked anything, and other keypads sharing the door must keep
     * it locked.
     */
    public void onKeypadBroken() {
        if (!hasStoredCode()) return;
        TileEntityDoorGeneric door = findDoor();
        if (door == null) return;
        for (BlockPos p : searchArea()) {
            if (p.equals(pos) || !world.isBlockLoaded(p)) continue;
            TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityDoorKeypad)) continue;
            TileEntityDoorKeypad other = (TileEntityDoorKeypad) te;
            if (!other.hasStoredCode()) continue;
            TileEntityDoorGeneric linked = other.findDoor();
            if (linked != null && linked.getPos().equals(door.getPos())) return;
        }
        door.setKeypadLocked(false);
    }

    // ------------------------------------------------------------ persistence / sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByteArray("code", code.clone());
        tag.setInteger("stored", storedCode);
        tag.setBoolean("setting", settingCode);
        tag.setInteger("success", successTicks);
        tag.setInteger("fail", failTicks);
        tag.setInteger("codeSet", codeSetTicks);
        tag.setInteger("blackScreen", blackScreenTicks);
        tag.setByteArray("cooldowns", cooldowns.clone());
        if (doorPos != null) tag.setLong("door", doorPos.toLong());
        return tag;
    }

    /** Update packets only need the flag; the code itself is never resent after it is set. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        writeToNBT(tag);
        tag.removeTag("stored");
        tag.setBoolean("hasCode", storedCode >= 0);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        byte[] c = tag.getByteArray("code");
        if (c.length == CODE_LENGTH) {
            System.arraycopy(c, 0, code, 0, CODE_LENGTH);
        } else {
            clearCode();
        }
        if (tag.hasKey("stored")) storedCode = tag.getInteger("stored");
        syncedHasCode = tag.hasKey("hasCode") && tag.getBoolean("hasCode");
        settingCode = !tag.hasKey("setting") || tag.getBoolean("setting");
        successTicks = tag.getInteger("success");
        failTicks = tag.getInteger("fail");
        codeSetTicks = tag.getInteger("codeSet");
        blackScreenTicks = tag.getInteger("blackScreen");
        byte[] cd = tag.getByteArray("cooldowns");
        if (cd.length == BUTTONS) {
            System.arraycopy(cd, 0, cooldowns, 0, BUTTONS);
        } else {
            Arrays.fill(cooldowns, (byte) 0);
        }
        doorPos = tag.hasKey("door") ? BlockPos.fromLong(tag.getLong("door")) : null;
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
