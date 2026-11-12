package com.ntmdoors.screen;

import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.util.HeldItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/**
 * ScreenHandler for the carried Keycard Holder.
 * Grid layout: 3x4 card slots at (61, 36); player inventory at (8, 128); hotbar at (8, 186).
 * Enforces strict anti-duplication and swap protection.
 */
public class KeycardHolderScreenHandler extends ScreenHandler {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 212;

    public static final int GRID_COLUMNS = 3;
    public static final int GRID_ROWS = 4;
    public static final int GRID_X = 61;
    public static final int GRID_Y = 36;

    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 128;
    public static final int HOTBAR_Y = 186;

    public static final int HOLDER_SLOT_COUNT = ItemKeycardHolder.SLOT_COUNT;
    public static final int PLAYER_SLOT_START = HOLDER_SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;

    private final ItemStack holder;
    private final Hand hand;
    private final KeycardHolderInventory holderInventory;

    // Client-side constructor
    public KeycardHolderScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, buf.readBoolean());
    }

    private KeycardHolderScreenHandler(int syncId, PlayerInventory playerInventory, boolean offHand) {
        this(syncId, playerInventory,
                playerInventory.player.getStackInHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND),
                offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
    }

    // Server-side constructor
    public KeycardHolderScreenHandler(int syncId, PlayerInventory playerInventory, ItemStack holder, Hand hand) {
        super(ModScreenHandlerTypes.KEYCARD_HOLDER, syncId);
        this.holder = holder;
        this.hand = hand;
        this.holderInventory = new KeycardHolderInventory(holder, playerInventory.player);

        // 12 Holder slots (0..11)
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                addSlot(new KeycardSlot(holderInventory, col + row * GRID_COLUMNS,
                        GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // 27 Player inventory slots (12..38)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }

        // 9 Hotbar slots (39..47)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !holder.isEmpty()
                && holder.getItem() instanceof ItemKeycardHolder
                && player.getStackInHand(hand) == holder;
    }

    public int getStoredCount() {
        int count = 0;
        for (int i = 0; i < HOLDER_SLOT_COUNT; i++) {
            if (!holderInventory.getStack(i).isEmpty()) count++;
        }
        return count;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            // Allow legal vanilla out-of-window clicks (-1 for quickcraft/external, -999 for cursor drop)
            if (slotIndex == -1 || slotIndex == -999) {
                super.onSlotClick(slotIndex, button, actionType, player);
            }
            return;
        }

        // Never let the open holder be picked up, dropped or swapped
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.getStack() == holder) {
            return;
        }

        // Number-key swaps bypass normal slot validation; prevent forcing non-cards or swapping holder away
        if (actionType == SlotActionType.SWAP && slotIndex < HOLDER_SLOT_COUNT) {
            ItemStack hotbar = button >= 0 && button < 9
                    ? player.getInventory().getStack(button)
                    : ItemStack.EMPTY;
            if (!hotbar.isEmpty() && !ItemKeycardHolder.isAllowed(hotbar)) {
                return;
            }
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack before = stack.copy();

        if (index < HOLDER_SLOT_COUNT) {
            if (!insertItem(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOT_END) {
            if (stack == holder) return ItemStack.EMPTY;
            if (!insertItem(stack, 0, HOLDER_SLOT_COUNT, false)) return ItemStack.EMPTY;
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

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        holderInventory.save();
        HeldItems.sync(player);
    }

    /** Slot that accepts keycards and only ever holds a single item. */
    public static class KeycardSlot extends Slot {
        public KeycardSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return ItemKeycardHolder.isAllowed(stack);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }
}
