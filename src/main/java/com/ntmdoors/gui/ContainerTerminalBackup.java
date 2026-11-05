package com.ntmdoors.gui;

import com.ntmdoors.tile.TileEntityMonitorDesk;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for the monitor terminal's backup console. The initiate mode has the programmer's two
 * terminal-key slots; the restore mode has one key slot. Upload, Backup and Restore are synthetic
 * slot clicks handled by the tile entity, exactly like the programmer's action slots.
 */
public class ContainerTerminalBackup extends Container {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 232;

    public static final int SLOT_LEFT_X = 24;
    public static final int SLOT_LEFT_Y = 40;
    public static final int SLOT_RIGHT_X = 136;
    public static final int SLOT_RIGHT_Y = 40;

    public static final int UPLOAD_BUTTON_X = 14;
    public static final int UPLOAD_BUTTON_Y = 60;
    public static final int UPLOAD_BUTTON_WIDTH = 44;
    public static final int UPLOAD_BUTTON_HEIGHT = 16;

    public static final int BACKUP_BUTTON_X = 65;
    public static final int BACKUP_BUTTON_Y = 40;
    public static final int BACKUP_BUTTON_WIDTH = 46;
    public static final int BACKUP_BUTTON_HEIGHT = 16;

    public static final int BAR_X = 24;
    public static final int BAR_Y = 84;
    public static final int BAR_WIDTH = 128;
    public static final int BAR_HEIGHT = 12;

    public static final int BACK_BUTTON_X = 8;
    public static final int BACK_BUTTON_Y = 5;
    public static final int BACK_BUTTON_WIDTH = 30;
    public static final int BACK_BUTTON_HEIGHT = 12;

    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 148;
    public static final int HOTBAR_Y = 208;

    public static final int UPLOAD_ACTION_SLOT = -2;
    public static final int BACKUP_ACTION_SLOT = -3;
    public static final int RESTORE_ACTION_SLOT = -4;

    public static final int PLAYER_SLOT_COUNT = 36;

    private final TileEntityMonitorDesk tile;
    private final boolean restore;
    private final int machineSlotCount;
    private final int playerSlotStart;
    private final int playerSlotEnd;

    public ContainerTerminalBackup(InventoryPlayer playerInventory, TileEntityMonitorDesk tile,
                                   boolean restore) {
        this.tile = tile;
        this.restore = restore;
        this.machineSlotCount = restore ? 1 : TileEntityMonitorDesk.BACKUP_SLOT_COUNT;
        this.playerSlotStart = machineSlotCount;
        this.playerSlotEnd = playerSlotStart + PLAYER_SLOT_COUNT;

        addSlotToContainer(new SlotBackup(tile.getBackupInventory(),
                TileEntityMonitorDesk.BACKUP_SLOT_LEFT, SLOT_LEFT_X, SLOT_LEFT_Y));
        if (!restore) {
            addSlotToContainer(new SlotBackup(tile.getBackupInventory(),
                    TileEntityMonitorDesk.BACKUP_SLOT_RIGHT, SLOT_RIGHT_X, SLOT_RIGHT_Y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col,
                    PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.getBackupInventory().isUsableByPlayer(player);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!restore && slotId == UPLOAD_ACTION_SLOT) {
            if (!player.world.isRemote) tile.uploadBackupToDrive();
            // Returning the client's carried stack confirms the synthetic click without moving it.
            return player.inventory.getItemStack();
        }
        if (!restore && slotId == BACKUP_ACTION_SLOT) {
            if (!player.world.isRemote) tile.startBackup();
            return player.inventory.getItemStack();
        }
        if (restore && slotId == RESTORE_ACTION_SLOT) {
            if (!player.world.isRemote) tile.startRestore();
            return player.inventory.getItemStack();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack before = stack.copy();

        if (isMachineSlot(index)) {
            if (!mergeItemStack(stack, playerSlotStart, playerSlotEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= playerSlotStart && index < playerSlotEnd) {
            if (!mergeInto(stack, TileEntityMonitorDesk.BACKUP_SLOT_LEFT)
                    && (restore || !mergeInto(stack, TileEntityMonitorDesk.BACKUP_SLOT_RIGHT))) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }

    private boolean isMachineSlot(int index) {
        return index >= 0 && index < machineSlotCount;
    }

    private boolean mergeInto(ItemStack stack, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || slot.getHasStack() || !slot.isItemValid(stack)) return false;
        return mergeItemStack(stack, index, index + 1, false);
    }

    /** A backup slot that accepts only Terminal Backup Keys. */
    private static class SlotBackup extends Slot {

        SlotBackup(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return TileEntityMonitorDesk.isBackupKey(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
