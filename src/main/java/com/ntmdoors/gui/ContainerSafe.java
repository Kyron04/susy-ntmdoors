package com.ntmdoors.gui;

import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for both safes: a 3x9 storage grid and the player inventory. Page two only shows the
 * settings, so the server refuses clicks that would move items into or out of the hidden grid.
 */
public class ContainerSafe extends Container {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 232;

    public static final int SAFE_COLUMNS = 9;
    public static final int SAFE_ROWS = 3;
    public static final int SAFE_SLOT_COUNT = TileEntitySafe.SLOT_COUNT;
    public static final int SAFE_X = 8;
    public static final int SAFE_Y = 36;

    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 128;
    public static final int HOTBAR_Y = 186;

    public static final int PLAYER_SLOT_START = SAFE_SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;

    private final TileEntitySafe tile;
    private int page;

    public ContainerSafe(InventoryPlayer playerInventory, TileEntitySafe tile) {
        this.tile = tile;

        for (int row = 0; row < SAFE_ROWS; row++) {
            for (int col = 0; col < SAFE_COLUMNS; col++) {
                addSlotToContainer(new Slot(tile, col + row * SAFE_COLUMNS,
                        SAFE_X + col * 18, SAFE_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    public TileEntitySafe getTile() {
        return tile;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(1, page));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUsableByPlayer(player);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Page two hides the storage grid; never let a click move an item into a slot nobody can see.
        if (page != 0 && slotId >= 0 && slotId < SAFE_SLOT_COUNT) {
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

        if (index < SAFE_SLOT_COUNT) {
            if (!mergeItemStack(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOT_END) {
            // On the options page only the player inventory is visible, so shift-click stays there.
            if (page != 0 || !mergeItemStack(stack, 0, SAFE_SLOT_COUNT, false)) {
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

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        tile.closeInventory(player);
    }
}
