package com.ntmdoors.tile;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.util.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The shared body of both safes: a 27-slot inventory, the two settings toggles, the lockout timer
 * and the short access session that lets a player use the container and its options page.
 *
 * <p>The code safe adds a six digit code and an owner. The biometric safe leaves the code fields
 * unused and overrides its access check. The code digits are never sent to clients; only whether
 * one exists.</p>
 */
public class TileEntitySafe extends TileEntitySynced implements IInventory, ITickable {

    public static final int SLOT_COUNT = 27;
    public static final int CODE_LENGTH = 6;
    public static final int LOCKOUT_FAILURES = 3;
    public static final int LOCKOUT_TICKS = 20 * 60 * 5;
    public static final int ACCESS_SESSION_TICKS = 20 * 60 * 10;
    /** The success card stays on screen this long before the container opens. */
    public static final int OPEN_DELAY_TICKS = 40;

    private static final float SOUND_VOLUME = 0.8F;

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];

    /** Server only. Empty until the first code is entered. */
    private String securityCode = "";
    /** Synced mirror of {@link #securityCode}; the digits themselves never leave the server. */
    private boolean codeSet;
    /** Server only. The player who set the current code, used by Match user. */
    @Nullable
    private UUID owner;
    private String ownerName = "";

    private boolean matchHolder = true;
    private boolean lockoutMode;
    /** Synced mirror of {@link #lockoutTicks > 0}. */
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

    /** True once a player has set the current code and is remembered as its owner. */
    public boolean hasOwner() {
        return owner != null;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** Digits only, at most {@link #CODE_LENGTH} characters. */
    public static String sanitiseCode(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < CODE_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    /**
     * Server side: handles one keypad submission. The first non-empty code becomes the safe's code
     * and records the player as its owner; later submissions must match.
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
            owner = player.getUniqueID();
            ownerName = player.getName();
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

        if (matchHolder && owner != null && !owner.equals(player.getUniqueID())) {
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

    /** Server side: forgets the code and its owner so the next entry starts over. */
    public boolean resetCode() {
        if (world == null || world.isRemote || !supportsCode() || !codeSet) return false;
        securityCode = "";
        codeSet = false;
        owner = null;
        ownerName = "";
        resetFailures();
        clearLockout();
        sync();
        return true;
    }

    // ------------------------------------------------------------ toggles

    public boolean isMatchHolder() {
        return matchHolder;
    }

    /** Server side: only the owner may unlock this safe when on. */
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
    }

    public void resetFailures() {
        failedAttempts = 0;
    }

    private void clearLockout() {
        lockoutTicks = 0;
        lockedOut = false;
        failedAttempts = 0;
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
                grantAccess(player);
                player.openGui(NTMDoors.INSTANCE, GuiHandler.SAFE, world,
                        pos.getX(), pos.getY(), pos.getZ());
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
        tag.setBoolean("lockedOut", lockedOut);
    }

    private void readOptions(NBTTagCompound tag) {
        // Holder matching defaults on, so a safe saved before this option existed keeps it.
        matchHolder = !tag.hasKey("matchHolder") || tag.getBoolean("matchHolder");
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
        if (owner != null) tag.setString("owner", owner.toString());
        tag.setString("ownerName", ownerName);
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
        tag.setString("ownerName", ownerName);
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
        if (tag.hasKey("owner")) {
            try {
                owner = UUID.fromString(tag.getString("owner"));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        if (tag.hasKey("ownerName")) ownerName = tag.getString("ownerName");
        readOptions(tag);
        if (tag.hasKey("failedAttempts")) failedAttempts = tag.getInteger("failedAttempts");
        if (tag.hasKey("lockoutTicks")) {
            lockoutTicks = tag.getInteger("lockoutTicks");
            lockedOut = lockoutTicks > 0;
        }
    }
}
