package com.ntmdoors.tile;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared body of both safes: a 27-slot inventory, the two settings toggles, the lockout timer
 * and the short access session that lets a player use the container and its options page.
 *
 * <p>The code safe adds a six digit code. The biometric safe leaves the code fields unused and
 * overrides its access check. The code digits are never sent to clients; only whether one exists.</p>
 */
public class TileEntitySafe extends TileEntitySynced implements IInventory, ITickable {

    public static final int SLOT_COUNT = 27;
    public static final int CODE_LENGTH = 6;
    public static final int LOCKOUT_FAILURES = 3;
    public static final int LOCKOUT_TICKS = 20 * 60 * 5;
    public static final int ACCESS_SESSION_TICKS = 20 * 60 * 10;
    /** Safe-to-door distance allowed while linking. */
    public static final double MAX_BIND_DISTANCE = 32D;
    /** The success card stays on screen this long before the container opens. */
    public static final int OPEN_DELAY_TICKS = 25;
    /** A door-binding session the player walks away from expires after a minute. */
    private static final int BINDING_TICKS = 20 * 60;

    private static final float SOUND_VOLUME = 0.8F;

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];

    /** Server only. Empty until the first code is entered. */
    private String securityCode = "";
    /** Synced mirror of {@link #securityCode}; the digits themselves never leave the server. */
    private boolean codeSet;

    private boolean matchHolder = true;
    private boolean lockoutMode;
    /** When on, the safe can link doors and close them on lockout. Synced to clients. */
    private boolean bindingActivities = true;
    /** Synced mirror of whether the lockout timer is currently running. */
    private boolean lockedOut;

    /** Server only. */
    private int failedAttempts;
    private int lockoutTicks;

    /** Server only. Every player allowed to use the options page, with their remaining ticks. */
    private final Map<UUID, Integer> accessSessions = new HashMap<>();

    /** Server only. The player whose success frame is showing, and how long until the container opens. */
    @Nullable
    private UUID pendingOpenPlayer;
    private int pendingOpenTicks;

    /** Server only. Doors closed when this safe enters a lockout. */
    private final List<BlockPos> boundDoors = new ArrayList<>();
    /** Server only. The player currently linking doors to this safe, and the session clock. */
    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;

    public TileEntitySafe() {
        Arrays.fill(items, ItemStack.EMPTY);
    }

    /** True for the biometric variant; controls labels and the code-reset row. */
    public boolean isBiometric() {
        return false;
    }

    /** The code safe supports a keypad code; the biometric safe does not. */
    public boolean supportsCode() {
        return true;
    }

    // ------------------------------------------------------------ code

    public boolean hasSecurityCode() {
        return codeSet;
    }

    /** Digits only, at most {@link #CODE_LENGTH} characters. */
    public static String sanitiseCode(String raw) {
        return Codes.sanitise(raw, CODE_LENGTH);
    }

    /**
     * Server side: handles one keypad submission. The first non-empty code becomes the safe's code;
     * later submissions must match.
     */
    public int submitCode(EntityPlayer player, String raw) {
        if (world == null || world.isRemote || player == null || !supportsCode()) {
            return MessageSafeResult.DENIED;
        }
        if (isLockedOut()) {
            msg(player, "ntmdoors.msg.safe_locked_out");
            playFail();
            return MessageSafeResult.LOCKED_OUT;
        }

        String code = sanitiseCode(raw);
        if (code.isEmpty()) {
            playFail();
            return MessageSafeResult.DENIED;
        }

        if (!codeSet) {
            securityCode = code;
            codeSet = true;
            resetFailures();
            grantAccess(player);
            scheduleOpen(player);
            playSuccess();
            msg(player, "ntmdoors.msg.safe_code_set");
            sync();
            return MessageSafeResult.SET;
        }

        if (!securityCode.equals(code)) {
            recordFailure();
            playFail();
            msg(player, isLockedOut() ? "ntmdoors.msg.safe_locked_out" : "ntmdoors.msg.safe_code_denied");
            sync();
            return MessageSafeResult.DENIED;
        }

        resetFailures();
        grantAccess(player);
        scheduleOpen(player);
        playSuccess();
        msg(player, "ntmdoors.msg.safe_code_accepted");
        sync();
        return MessageSafeResult.ACCEPTED;
    }

    /** Server side: forgets the code so the next entry starts over. */
    public boolean resetCode() {
        if (world == null || world.isRemote || !supportsCode() || !codeSet) return false;
        securityCode = "";
        codeSet = false;
        resetFailures();
        clearLockout();
        sync();
        return true;
    }

    // ------------------------------------------------------------ toggles

    public boolean isMatchHolder() {
        return matchHolder;
    }

    /** Server side: only the card's owner may open the biometric safe when on. */
    public boolean setMatchHolder(boolean value) {
        if (world == null || world.isRemote || matchHolder == value) return false;
        matchHolder = value;
        sync();
        return true;
    }

    public boolean isLockoutMode() {
        return lockoutMode;
    }

    /** Server side: turning Lockout Mode off also clears a lockout that is already running. */
    public boolean setLockoutMode(boolean value) {
        if (world == null || world.isRemote || lockoutMode == value) return false;
        lockoutMode = value;
        if (!value) clearLockout();
        sync();
        return true;
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    /** Server side: one failed attempt. Three in a row start a lockout when Lockout Mode is on. */
    public void recordFailure() {
        if (!lockoutMode || lockedOut) return;
        if (++failedAttempts < LOCKOUT_FAILURES) return;
        failedAttempts = 0;
        lockoutTicks = LOCKOUT_TICKS;
        lockedOut = true;
        if (world != null) {
            world.playSound(null, pos, DoorDecl.sound("door_alarm"),
                    SoundCategory.BLOCKS, 1F, 1F);
        }
        sync();
        if (bindingActivities) closeBoundDoors();
    }

    public void resetFailures() {
        failedAttempts = 0;
    }

    private void clearLockout() {
        lockoutTicks = 0;
        lockedOut = false;
        failedAttempts = 0;
    }

    // ------------------------------------------------------------ door binding

    public boolean isBindingActivities() {
        return bindingActivities;
    }

    /** Server side: arms or disarms linking and the lockout-door response. */
    public boolean setBindingActivities(boolean value) {
        if (world == null || world.isRemote || bindingActivities == value) return false;
        bindingActivities = value;
        if (!value) stopBinding(null);
        sync();
        if (value && lockedOut) closeBoundDoors();
        return true;
    }

    public boolean isBindingFor(@Nullable EntityPlayer player) {
        return player != null && bindingPlayer != null && bindingPlayer.equals(player.getUniqueID());
    }

    /** Server side: starts or finishes the door-linking session for this player. */
    public void toggleBinding(EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return;
        if (!bindingActivities) {
            msg(player, "ntmdoors.msg.safe_binding_disabled");
            return;
        }
        if (isBindingFor(player)) {
            stopBinding(player);
            return;
        }
        if (bindingPlayer != null) {
            msg(player, "ntmdoors.msg.safe_binding_busy");
            return;
        }

        cancelOtherSessions(world, player);
        bindingPlayer = player.getUniqueID();
        bindingTicks = BINDING_TICKS;
        msg(player, "ntmdoors.msg.safe_binding_on");
    }

    public void stopBinding(@Nullable EntityPlayer player) {
        if (bindingPlayer == null) return;
        bindingPlayer = null;
        bindingTicks = 0;
        if (player != null) msg(player, "ntmdoors.msg.safe_binding_off");
    }

    /** Server side: links or unlinks one closed door for the active binding session. */
    public boolean bindDoor(EntityPlayer player, TileEntityDoorGeneric door) {
        if (world == null || world.isRemote || player == null || door == null) return false;
        if (!isBindingFor(player)) {
            msg(player, "ntmdoors.msg.safe_binding_required");
            return false;
        }
        if (door.getWorld() != world) return false;
        if (door.getState() != DoorState.CLOSED) {
            msg(player, "ntmdoors.msg.bind_open");
            return false;
        }
        if (pos.distanceSq(door.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            msg(player, "ntmdoors.msg.safe_bind_far");
            return false;
        }

        BlockPos doorPos = door.getPos().toImmutable();
        if (door.isBoundToSafe(pos)) {
            door.unbindFromSafe(pos);
            if (boundDoors.remove(doorPos)) markDirty();
            msg(player, "ntmdoors.msg.safe_unbound_door");
            world.playSound(null, doorPos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1F);
            return true;
        }

        BlockPos current = door.getBoundSafe();
        if (current != null) {
            if (!world.isBlockLoaded(current)) {
                msg(player, "ntmdoors.msg.safe_binding_other");
                return false;
            }
            TileEntity owner = world.getTileEntity(current);
            if (owner instanceof TileEntitySafe && ((TileEntitySafe) owner).isDoorLinked(doorPos)) {
                msg(player, "ntmdoors.msg.safe_binding_other");
                return false;
            }
            // The owning safe is gone, or no longer lists this door: drop the stale link.
            door.unbindFromSafe(current);
        }

        if (!door.bindToSafe(pos)) return false;
        if (!boundDoors.contains(doorPos)) {
            boundDoors.add(doorPos);
            markDirty();
        }
        msg(player, "ntmdoors.msg.safe_bound_door");
        world.playSound(null, doorPos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1F);
        return true;
    }

    /** True when this safe still lists {@code doorPos} as one of its linked doors. */
    public boolean isDoorLinked(BlockPos doorPos) {
        return doorPos != null && boundDoors.contains(doorPos);
    }

    /** Called by a broken door: forget its link without touching the door. */
    public void unbindDoor(BlockPos doorPos) {
        if (doorPos != null && boundDoors.remove(doorPos)) markDirty();
    }

    /** Called when this safe is broken: clear the reciprocal link on every loaded door. */
    public void unbindAllDoors() {
        if (world == null || world.isRemote) return;
        for (BlockPos doorPos : new ArrayList<>(boundDoors)) {
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                ((TileEntityDoorGeneric) te).unbindFromSafe(pos);
            }
        }
        boundDoors.clear();
        markDirty();
    }

    /** Closes every linked door once, when a lockout starts. */
    private void closeBoundDoors() {
        if (world == null || world.isRemote || boundDoors.isEmpty()) return;
        boolean changed = false;
        Iterator<BlockPos> it = boundDoors.iterator();
        while (it.hasNext()) {
            BlockPos doorPos = it.next();
            if (!world.isBlockLoaded(doorPos)) continue;
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
                if (door.isBoundToSafe(pos)) {
                    door.request(false);
                } else {
                    it.remove();
                    changed = true;
                }
            } else {
                it.remove();
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    /** A player may only hold one binding session at a time; starting a new one clears the old. */
    private static void cancelOtherSessions(World world, EntityPlayer player) {
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntitySafe)) continue;
            TileEntitySafe safe = (TileEntitySafe) te;
            if (safe.isBindingFor(player)) {
                safe.bindingPlayer = null;
                safe.bindingTicks = 0;
            }
        }
    }

    /** The safe the player is currently linking doors from, or null. */
    @Nullable
    public static TileEntitySafe findBindingSafe(World world, EntityPlayer player) {
        if (world == null || player == null) return null;
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof TileEntitySafe && ((TileEntitySafe) te).isBindingFor(player)) {
                return (TileEntitySafe) te;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ access session

    public void grantAccess(EntityPlayer player) {
        if (player == null) return;
        accessSessions.put(player.getUniqueID(), ACCESS_SESSION_TICKS);
    }

    /** True while this player may use the options page. */
    public boolean hasAccessSessionFor(@Nullable EntityPlayer player) {
        if (player == null) return false;
        Integer ticks = accessSessions.get(player.getUniqueID());
        return ticks != null && ticks > 0;
    }

    /** Server side: opens the container once the success card has had time to show. */
    protected void scheduleOpen(EntityPlayer player) {
        pendingOpenPlayer = player.getUniqueID();
        pendingOpenTicks = OPEN_DELAY_TICKS;
    }

    /** Server side: opens the container immediately, used by the Creative Keycard. */
    public void openContainer(EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return;
        grantAccess(player);
        player.openGui(NTMDoors.INSTANCE, GuiHandler.SAFE, world,
                pos.getX(), pos.getY(), pos.getZ());
    }

    protected void sendResult(EntityPlayer player, int result) {
        if (world == null || world.isRemote || !(player instanceof EntityPlayerMP)) return;
        ModNetwork.INSTANCE.sendTo(new MessageSafeResult(pos, result), (EntityPlayerMP) player);
    }

    protected void playSuccess() {
        if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON3,
                SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
    }

    protected void playFail() {
        if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8,
                SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
    }

    protected static void msg(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (pendingOpenPlayer != null && --pendingOpenTicks <= 0) {
            UUID id = pendingOpenPlayer;
            pendingOpenPlayer = null;
            EntityPlayer player = world.getPlayerEntityByUUID(id);
            if (player != null && player.isEntityAlive()
                    && player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D) <= 64D) {
                openContainer(player);
            }
        }

        if (lockoutTicks > 0 && --lockoutTicks <= 0) {
            lockedOut = false;
            sync();
        }

        if (!accessSessions.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = accessSessions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int ticks = entry.getValue() - 1;
                if (ticks <= 0) {
                    it.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }

        if (bindingPlayer != null) {
            EntityPlayer player = world.getPlayerEntityByUUID(bindingPlayer);
            if (player == null || --bindingTicks <= 0) stopBinding(null);
        }
    }

    // ------------------------------------------------------------ inventory

    @Override
    public int getSizeInventory() {
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
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < SLOT_COUNT ? items[index] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = items[index];
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken = stack.splitStack(count);
        if (stack.isEmpty()) items[index] = ItemStack.EMPTY;
        markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = items[index];
        items[index] = ItemStack.EMPTY;
        if (!stack.isEmpty()) markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SLOT_COUNT) return;
        items[index] = stack == null ? ItemStack.EMPTY : stack;
        if (!items[index].isEmpty() && items[index].getCount() > getInventoryStackLimit()) {
            items[index].setCount(getInventoryStackLimit());
        }
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        if (world == null || world.getTileEntity(pos) != this) return false;
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public String getName() {
        return isBiometric() ? "container.ntmdoors.biometric_safe" : "container.ntmdoors.safe";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        Arrays.fill(items, ItemStack.EMPTY);
        markDirty();
    }

    // ------------------------------------------------------------ persistence / sync

    private void writeOptions(NBTTagCompound tag) {
        tag.setBoolean("matchHolder", matchHolder);
        tag.setBoolean("lockoutMode", lockoutMode);
        tag.setBoolean("bindingActivities", bindingActivities);
        tag.setBoolean("lockedOut", lockedOut);
    }

    private void readOptions(NBTTagCompound tag) {
        // Holder matching and binding default on, so safes saved before these options keep working.
        matchHolder = !tag.hasKey("matchHolder") || tag.getBoolean("matchHolder");
        bindingActivities = !tag.hasKey("bindingActivities") || tag.getBoolean("bindingActivities");
        lockoutMode = tag.getBoolean("lockoutMode");
        lockedOut = tag.getBoolean("lockedOut");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (items[i].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            items[i].writeToNBT(entry);
            list.appendTag(entry);
        }
        tag.setTag("Items", list);
        tag.setString("securityCode", securityCode);
        tag.setBoolean("codeSet", codeSet);
        tag.setString("boundDoors", BlockPositions.encodeList(boundDoors));
        writeOptions(tag);
        tag.setInteger("failedAttempts", failedAttempts);
        tag.setInteger("lockoutTicks", lockoutTicks);
        return tag;
    }

    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        // TileEntity.readFromNBT rebuilds pos from x/y/z, so the sync tag must carry them.
        super.writeToNBT(tag);
        tag.setBoolean("codeSet", codeSet);
        tag.setBoolean("biometric", isBiometric());
        writeOptions(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        if (tag.hasKey("Items", 9)) {
            Arrays.fill(items, ItemStack.EMPTY);
            NBTTagList list = tag.getTagList("Items", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                int slot = entry.getByte("Slot") & 0xFF;
                if (slot >= 0 && slot < SLOT_COUNT) items[slot] = new ItemStack(entry);
            }
        }

        if (tag.hasKey("securityCode")) securityCode = sanitiseCode(tag.getString("securityCode"));
        codeSet = tag.hasKey("codeSet") ? tag.getBoolean("codeSet") : !securityCode.isEmpty();
        if (tag.hasKey("boundDoors")) {
            boundDoors.clear();
            boundDoors.addAll(BlockPositions.decodeList(tag.getString("boundDoors")));
        }
        readOptions(tag);
        if (tag.hasKey("failedAttempts")) failedAttempts = tag.getInteger("failedAttempts");
        if (tag.hasKey("lockoutTicks")) {
            lockoutTicks = tag.getInteger("lockoutTicks");
            lockedOut = lockoutTicks > 0;
        }
    }
}
