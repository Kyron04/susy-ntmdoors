package com.ntmdoors.tile;

import com.ntmdoors.client.BackupLoopSound;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Keycard programmer. The block is a desk console with a seven-slot internal inventory:
 *
 * <ul>
 *     <li>0 / 2: relay keys for the two binding operations</li>
 *     <li>1: the operator keycard being bound</li>
 *     <li>3: the RFID card being bound on the first page</li>
 *     <li>4: the RFID card being re-coloured on the second page</li>
 *     <li>5 / 6: the left and right legacy backup slots; both accept Relay Keys and Operator
 *     Keycards</li>
 * </ul>
 *
 * <p>Page three backs up the NBT on a used key or card in the left slot onto a fresh one in the
 * right slot. The backup takes ten seconds, can copy a Relay Key to either card type, and can copy
 * an Operator Keycard only to another Operator Keycard. Binding and private-key storage remain
 * separate, later features.</p>
 */
public class TileEntityKeycardProgrammer extends TileEntitySynced implements IInventory, ITickable {

    public static final int SLOT_COUNT = 7;
    public static final int SLOT_RELAY_ONE = 0;
    public static final int SLOT_OPERATOR = 1;
    public static final int SLOT_RELAY_TWO = 2;
    public static final int SLOT_RFID_PAGE_ONE = 3;
    public static final int SLOT_RFID_PAGE_TWO = 4;
    public static final int SLOT_LEGACY_LEFT = 5;
    public static final int SLOT_LEGACY_RIGHT = 6;

    /** Ten seconds of backup time, matching the progress frames. */
    public static final int BACKUP_TICKS = 20 * 10;
    /** How long the full bar remains visible after a successful backup. */
    private static final int SUCCESS_HOLD_TICKS = 10;
    private static final float SOUND_VOLUME = 0.8F;

    /** Why the current backup slots cannot start a backup. */
    public enum BackupFailure {
        NONE,
        EMPTY,
        INVALID_ITEM,
        INVALID_DIRECTION,
        NO_DATA,
        TARGET_NOT_FRESH
    }

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    private boolean backupActive;
    private int backupTicks;
    private boolean backupSuccess;
    private int successTicks;
    /** Last frame sent to clients, so a backup only syncs when the bar changes. */
    private int lastSyncedFrame = -1;

    @SideOnly(Side.CLIENT)
    @Nullable
    private BackupLoopSound backupSound;

    public TileEntityKeycardProgrammer() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------ inventory

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < SLOT_COUNT ? slots[index] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = slots[index];
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken = stack.splitStack(count);
        if (stack.isEmpty()) slots[index] = ItemStack.EMPTY;
        if (!taken.isEmpty() && isBackupSlot(index)) onBackupSlotChanged();
        markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = slots[index];
        slots[index] = ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            if (isBackupSlot(index)) onBackupSlotChanged();
            markDirty();
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SLOT_COUNT) return;
        slots[index] = stack == null ? ItemStack.EMPTY : stack;
        if (!slots[index].isEmpty() && slots[index].getCount() > getInventoryStackLimit()) {
            slots[index].setCount(getInventoryStackLimit());
        }
        if (isBackupSlot(index)) onBackupSlotChanged();
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
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
        if (stack.isEmpty()) return false;
        switch (index) {
            case SLOT_RELAY_ONE:
            case SLOT_RELAY_TWO:
                return stack.getItem() instanceof ItemRelayKey;
            case SLOT_OPERATOR:
                return stack.getItem() instanceof ItemOperatorKeycard;
            case SLOT_RFID_PAGE_ONE:
            case SLOT_RFID_PAGE_TWO:
                return stack.getItem() instanceof ItemRfidCard
                        && !(stack.getItem() instanceof ItemOperatorKeycard)
                        && !(stack.getItem() instanceof ItemCreativeKeycard);
            case SLOT_LEGACY_LEFT:
            case SLOT_LEGACY_RIGHT:
                return stack.getItem() instanceof ItemRelayKey
                        || stack.getItem() instanceof ItemOperatorKeycard;
            default:
                return false;
        }
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
        boolean hadBackupItem = !slots[SLOT_LEGACY_LEFT].isEmpty() || !slots[SLOT_LEGACY_RIGHT].isEmpty();
        Arrays.fill(slots, ItemStack.EMPTY);
        if (hadBackupItem) onBackupSlotChanged();
        markDirty();
    }

    @Override
    public String getName() {
        return "container.ntmdoors.keycard_programmer";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // ------------------------------------------------------------ backup

    public boolean isBackingUp() {
        return backupActive;
    }

    /** 0..1 progress, including the brief full-bar state after success. */
    public float getBackupProgress() {
        if (backupSuccess) return 1F;
        if (!backupActive) return 0F;
        return 1F - (float) backupTicks / (float) BACKUP_TICKS;
    }

    /** Index into the five supplied GUI frames: 0, 25, 50, 75 or 100 percent. */
    public int getBackupFrame() {
        int frame = (int) (getBackupProgress() * 4F);
        return Math.max(0, Math.min(4, frame));
    }

    /** Checks the current slot pair without starting or changing anything. */
    public BackupFailure getBackupFailure() {
        ItemStack source = slots[SLOT_LEGACY_LEFT];
        ItemStack target = slots[SLOT_LEGACY_RIGHT];
        if (source.isEmpty() || target.isEmpty()) return BackupFailure.EMPTY;

        boolean sourceRelay = source.getItem() instanceof ItemRelayKey;
        boolean sourceOperator = source.getItem() instanceof ItemOperatorKeycard;
        boolean targetRelay = target.getItem() instanceof ItemRelayKey;
        boolean targetOperator = target.getItem() instanceof ItemOperatorKeycard;
        if ((!sourceRelay && !sourceOperator) || (!targetRelay && !targetOperator)) {
            return BackupFailure.INVALID_ITEM;
        }
        // Relay Keys can seed either fresh card type; Operator cards cannot seed a Relay Key.
        if (sourceOperator && targetRelay) return BackupFailure.INVALID_DIRECTION;

        NBTTagCompound sourceTag = source.getTagCompound();
        if (sourceTag == null || sourceTag.isEmpty()) return BackupFailure.NO_DATA;
        NBTTagCompound targetTag = target.getTagCompound();
        if (targetTag != null && !targetTag.isEmpty()) return BackupFailure.TARGET_NOT_FRESH;
        return BackupFailure.NONE;
    }

    /** Server side: starts the ten-second backup if both slots are valid. */
    public boolean startBackup() {
        if (world == null || world.isRemote || backupActive) return false;
        if (getBackupFailure() != BackupFailure.NONE) return false;

        backupActive = true;
        backupSuccess = false;
        backupTicks = BACKUP_TICKS;
        successTicks = 0;
        lastSyncedFrame = 0;
        sync();
        return true;
    }

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            updateClientSound();
            return;
        }

        if (backupActive) {
            if (backupTicks > 0) backupTicks--;
            if (backupTicks <= 0) {
                completeBackup();
            } else {
                int frame = getBackupFrame();
                if (frame != lastSyncedFrame) {
                    lastSyncedFrame = frame;
                    sync();
                }
            }
        } else if (backupSuccess) {
            if (successTicks > 0) successTicks--;
            if (successTicks <= 0) {
                backupSuccess = false;
                lastSyncedFrame = -1;
                sync();
            }
        }
    }

    private void completeBackup() {
        if (getBackupFailure() != BackupFailure.NONE) {
            cancelBackup(true);
            return;
        }

        ItemStack source = slots[SLOT_LEGACY_LEFT];
        ItemStack target = slots[SLOT_LEGACY_RIGHT];
        NBTTagCompound sourceTag = source.getTagCompound();
        if (sourceTag == null || sourceTag.isEmpty()) {
            cancelBackup(true);
            return;
        }

        target.setTagCompound(sourceTag.copy());
        markDirty();

        backupActive = false;
        backupTicks = 0;
        backupSuccess = true;
        successTicks = SUCCESS_HOLD_TICKS;
        lastSyncedFrame = 4;
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
        sync();
    }

    /** Cancels a running backup; playSound is false when the block is being removed. */
    private void cancelBackup(boolean playSound) {
        if (!backupActive && !backupSuccess) return;
        boolean wasActive = backupActive;
        backupActive = false;
        backupTicks = 0;
        backupSuccess = false;
        successTicks = 0;
        lastSyncedFrame = -1;
        if (playSound && wasActive && world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
        }
        markDirty();
        if (world != null && !world.isRemote) sync();
    }

    private void onBackupSlotChanged() {
        if (world == null || world.isRemote) return;
        if (backupActive) {
            cancelBackup(true);
        } else if (backupSuccess) {
            backupSuccess = false;
            successTicks = 0;
            lastSyncedFrame = -1;
            sync();
        }
    }

    private static boolean isBackupSlot(int index) {
        return index == SLOT_LEGACY_LEFT || index == SLOT_LEGACY_RIGHT;
    }

    // ------------------------------------------------------------ client sound

    @SideOnly(Side.CLIENT)
    private void updateClientSound() {
        if (backupActive) {
            if (backupSound == null) {
                backupSound = new BackupLoopSound(this, ModSounds.SCANNER_BELL1, SOUND_VOLUME);
                Minecraft.getMinecraft().getSoundHandler().playSound(backupSound);
            }
        } else {
            stopClientSound();
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopClientSound() {
        if (backupSound == null) return;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        if (handler != null) handler.stopSound(backupSound);
        backupSound = null;
    }

    @Override
    public void invalidate() {
        if (world != null && world.isRemote) stopClientSound();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && world.isRemote) stopClientSound();
        super.onChunkUnload();
    }

    // ------------------------------------------------------------ drops / persistence

    /** Drops every stored card into the world; called when the block is broken. */
    public void dropContents(World world, BlockPos pos) {
        cancelBackup(false);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = slots[i];
            if (stack.isEmpty()) continue;
            InventoryHelper.spawnItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, stack);
            slots[i] = ItemStack.EMPTY;
        }
        markDirty();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            slots[i].writeToNBT(entry);
            items.appendTag(entry);
        }
        tag.setTag("Items", items);
        tag.setBoolean("backupActive", backupActive);
        tag.setInteger("backupTicks", backupTicks);
        tag.setBoolean("backupSuccess", backupSuccess);
        tag.setInteger("successTicks", successTicks);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        Arrays.fill(slots, ItemStack.EMPTY);
        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < SLOT_COUNT) {
                slots[slot] = new ItemStack(entry);
            }
        }
        backupActive = tag.getBoolean("backupActive");
        backupTicks = tag.getInteger("backupTicks");
        backupSuccess = tag.getBoolean("backupSuccess");
        successTicks = tag.getInteger("successTicks");
        lastSyncedFrame = -1;
    }
}
