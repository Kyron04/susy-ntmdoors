package com.ntmdoors.screen;

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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

public class SafeScreenHandler extends ScreenHandler {

    public static final int SAFE_SLOT_COUNT = 27;
    public static final int PLAYER_SLOT_START = SAFE_SLOT_COUNT;
    public static final int PLAYER_SLOT_COUNT = 36;
    public static final int PLAYER_SLOT_END = PLAYER_SLOT_START + PLAYER_SLOT_COUNT;

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final BlockPos pos;
    private int page = 0;

    public SafeScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(SAFE_SLOT_COUNT), new ArrayPropertyDelegate(16), buf.readBlockPos());
    }

    public SafeScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, BlockPos pos) {
        super(ModScreenHandlerTypes.SAFE, syncId);
        checkSize(inventory, SAFE_SLOT_COUNT);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.pos = pos;
        addProperties(propertyDelegate);
        inventory.onOpen(playerInventory.player);

        // 27 safe slots (3 rows of 9) - only active on Storage page (page 0)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9, 8 + col * 18, 36 + row * 18) {
                    @Override
                    public boolean isEnabled() {
                        return getPage() == 0;
                    }
                });
            }
        }

        // 27 player inventory slots - active on pages 0 and 1
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 128 + row * 18) {
                    @Override
                    public boolean isEnabled() {
                        return getPage() != 2;
                    }
                });
            }
        }

        // 9 hotbar slots - active on pages 0 and 1
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 186) {
                @Override
                public boolean isEnabled() {
                    return getPage() != 2;
                }
            });
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(2, page));
        propertyDelegate.set(0, this.page);
    }

    public boolean isBiometric() {
        return propertyDelegate.get(1) != 0;
    }

    public boolean isCodeSet() {
        return propertyDelegate.get(2) != 0;
    }

    public boolean isLockedOut() {
        return propertyDelegate.get(3) != 0;
    }

    public boolean isMatchHolder() {
        return propertyDelegate.get(4) != 0;
    }

    public boolean isBindingActivities() {
        return propertyDelegate.get(5) != 0;
    }

    public boolean isLockoutMode() {
        return propertyDelegate.get(6) != 0;
    }

    public boolean isVoidOnDestroy() {
        return propertyDelegate.get(7) != 0;
    }

    public boolean isRedstoneEvent(int index) {
        if (index < 0 || index >= 8) return false;
        return propertyDelegate.get(8 + index) != 0;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (page == 2) return;
        if (page != 0 && slotIndex >= 0 && slotIndex < SAFE_SLOT_COUNT) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (page == 2) return ItemStack.EMPTY;

        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            itemStack = originalStack.copy();

            if (index < SAFE_SLOT_COUNT) {
                if (!this.insertItem(originalStack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < PLAYER_SLOT_END) {
                if (page != 0 || !this.insertItem(originalStack, 0, SAFE_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }

            if (originalStack.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTakeItem(player, originalStack);
        }

        return itemStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
    }
}
