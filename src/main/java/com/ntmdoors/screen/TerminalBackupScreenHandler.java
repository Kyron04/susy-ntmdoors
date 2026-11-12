package com.ntmdoors.screen;

import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.item.ItemTerminalBackupKey;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/**
 * ScreenHandler for the monitor terminal's backup console.
 * Initiate mode has two USB key slots (source and target); restore mode has one key slot.
 */
public class TerminalBackupScreenHandler extends ScreenHandler {

    public static final int SLOT_LEFT_X = 24;
    public static final int SLOT_LEFT_Y = 40;
    public static final int SLOT_RIGHT_X = 136;
    public static final int SLOT_RIGHT_Y = 40;

    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 148;
    public static final int HOTBAR_Y = 208;

    public static final int PLAYER_SLOT_COUNT = 36;

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final BlockPos pos;
    private final boolean restoreMode;
    private final int machineSlotCount;
    private final int playerSlotStart;
    private final int playerSlotEnd;

    public TerminalBackupScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, buf.readBlockPos(), buf.readBoolean());
    }

    private TerminalBackupScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos, boolean restoreMode) {
        this(syncId, playerInventory, new SimpleInventory(restoreMode ? 1 : 2), new ArrayPropertyDelegate(5), pos, restoreMode);
    }

    public TerminalBackupScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                       PropertyDelegate propertyDelegate, BlockPos pos, boolean restoreMode) {
        super(ModScreenHandlerTypes.TERMINAL_BACKUP, syncId);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.pos = pos;
        this.restoreMode = restoreMode;
        this.machineSlotCount = restoreMode ? 1 : 2;
        this.playerSlotStart = machineSlotCount;
        this.playerSlotEnd = playerSlotStart + PLAYER_SLOT_COUNT;

        addProperties(propertyDelegate);
        inventory.onOpen(playerInventory.player);

        // Machine Slots
        addSlot(new SlotBackup(inventory, 0, SLOT_LEFT_X, SLOT_LEFT_Y));
        if (!restoreMode) {
            addSlot(new SlotBackup(inventory, 1, SLOT_RIGHT_X, SLOT_RIGHT_Y));
        }

        // Player Inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isRestoreMode() {
        return restoreMode || (propertyDelegate.get(4) != 0);
    }

    public int getBackupTicks() {
        return propertyDelegate.get(0);
    }

    public boolean isBackupActive() {
        return propertyDelegate.get(1) != 0;
    }

    public boolean isBackupSuccess() {
        return propertyDelegate.get(2) != 0;
    }

    public int getBackupFailure() {
        return propertyDelegate.get(3);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (player.getWorld().isClient()) return true;
        if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return false;

        if (player.getWorld().getBlockEntity(pos) instanceof MonitorDeskBlockEntity desk) {
            switch (id) {
                case 0 -> desk.uploadBackupToDrive();
                case 1 -> desk.startBackup();
                case 2 -> desk.startRestore();
                case 3 -> desk.cancelBackup();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.squaredDistanceTo(pos.toCenterPos()) <= 64.0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack before = stack.copy();

        if (index < machineSlotCount) {
            // Machine slot to player inventory
            if (!insertItem(stack, playerSlotStart, playerSlotEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= playerSlotStart && index < playerSlotEnd) {
            // Player inventory to machine slot
            if (stack.getItem() instanceof ItemTerminalBackupKey) {
                if (!insertItem(stack, 0, machineSlotCount, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, stack);
        return before;
    }

    private static class SlotBackup extends Slot {
        public SlotBackup(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemTerminalBackupKey;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }
}
