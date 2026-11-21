// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.gui;

import com.ntmdoors.item.ItemKeycardHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.EnumHand;

/**
 * Container for the carried Keycard Holder. The top is two 4x4 grids, one for keycards and one for
 * relay/utility keys; the bottom is the player inventory. Both directions of shift-click are
 * supported.
 *
 * <p>The holder stack is the actual stack in the player's hand. Moving or dropping that stack while
 * the GUI is open would leave the container pointing at a detached copy, so the slot holding it is
 * click-proof while the screen is open.</p>
 */
public class ContainerKeycardHolder extends Container {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 212;

    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 4;
    public static final int KEYCARD_GRID_X = 12;
    public static final int RELAY_GRID_X = 92;
    public static final int GRID_Y = 44;

    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 128;
    public static final int HOTBAR_Y = 186;

    public static final int KEYCARD_SLOT_COUNT = ItemKeycardHolder.KEYCARD_SLOT_COUNT;
    public static final int RELAY_KEY_SLOT_START = ItemKeycardHolder.RELAY_KEY_SLOT_START;
    public static final int HOLDER_SLOT_COUNT = ItemKeycardHolder.SLOT_COUNT;
    public static final int PLAYER_SLOT_START = HOLDER_SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;
    /** InventoryPlayer index of the off-hand stack (36 main + 4 armour slots come first). */
    private static final int OFFHAND_INVENTORY_SLOT = 40;

    private final ItemStack holder;
    private final EnumHand hand;
    private final InventoryKeycardHolder holderInventory;

    public ContainerKeycardHolder(InventoryPlayer playerInventory, EntityPlayer player,
                                  ItemStack holder, EnumHand hand) {
        this.holder = holder;
        this.hand = hand;
        this.holderInventory = new InventoryKeycardHolder(holder, player);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                addSlotToContainer(new SlotKeycard(holderInventory, col + row * GRID_COLUMNS,
                        KEYCARD_GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                addSlotToContainer(new SlotKeycard(holderInventory,
                        RELAY_KEY_SLOT_START + col + row * GRID_COLUMNS,
                        RELAY_GRID_X + col * 18, GRID_Y + row * 18));
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

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // Identity, not equality: the holder's NBT changes as cards move, but the stack object in
        // the hand stays the same for as long as the screen is legitimately open.
        return !holder.isEmpty()
                && holder.getItem() instanceof ItemKeycardHolder
                && player.getHeldItem(hand) == holder;
    }

    public int getKeycardCount() {
        return countSection(0, KEYCARD_SLOT_COUNT);
    }

    public int getRelayKeyCount() {
        return countSection(RELAY_KEY_SLOT_START, HOLDER_SLOT_COUNT);
    }

    private int countSection(int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (!holderInventory.getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Never let the open holder be picked up, dropped or swapped; doing so would detach the
        // container from the stack whose NBT it is editing.
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            Slot slot = inventorySlots.get(slotId);
            if (slot != null && slot.getStack() == holder) {
                return player.inventory.getItemStack();
            }
        }

        // Number-key and offhand swaps bypass the slot's normal validity path in some vanilla
        // branches. Without this guard a hotbar or offhand item (including the holder itself)
        // could be forced into a card slot, and the holder could be replaced and lost.
        if (clickTypeIn == ClickType.SWAP && slotId >= 0 && slotId < HOLDER_SLOT_COUNT) {
            ItemStack swapped;
            if (dragType >= 0 && dragType < 9) {
                swapped = player.inventory.getStackInSlot(dragType);
            } else if (dragType == 40) {
                swapped = player.getHeldItemOffhand();
            } else {
                swapped = ItemStack.EMPTY;
            }
            if (!swapped.isEmpty() && !ItemKeycardHolder.isStorable(slotId, swapped)) {
                return player.inventory.getItemStack();
            }
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack before = stack.copy();

        if (index < HOLDER_SLOT_COUNT) {
            if (!mergeItemStack(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOT_END) {
            // Shift-clicking the open holder itself would close the screen mid-transfer.
            if (stack == holder) return ItemStack.EMPTY;
            if (!mergeItemStack(stack, 0, HOLDER_SLOT_COUNT, false)) return ItemStack.EMPTY;
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
        holderInventory.save();

        // A main-hand holder is one of this container's player slots, so the normal slot compare
        // sends its changed NBT to the client. The off-hand slot is not part of the container, so
        // push the updated stack explicitly or the client's copy (and its tooltip) stays stale.
        if (!player.world.isRemote
                && hand == EnumHand.OFF_HAND
                && player instanceof EntityPlayerMP
                && player.getHeldItem(EnumHand.OFF_HAND) == holder) {
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketSetSlot(-1,
                    OFFHAND_INVENTORY_SLOT, holder));
        }
    }

    /** Storage slot that accepts any keycard and only ever holds one. */
    private static class SlotKeycard extends Slot {

        SlotKeycard(IInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return ItemKeycardHolder.isStorable(getSlotIndex(), stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
