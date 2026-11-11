package com.ntmdoors.screen;

import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
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
 * ScreenHandler for Keycard Programmer: coordinates 7 machine slots across 3 pages with player inventory.
 */
public class KeycardProgrammerScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final BlockPos blockPos;
    private KeycardProgrammerBlockEntity blockEntity;

    // Client-side constructor
    public KeycardProgrammerScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(KeycardProgrammerBlockEntity.INVENTORY_SIZE), new ArrayPropertyDelegate(4), buf.readBlockPos());
        if (playerInventory.player.getWorld().getBlockEntity(this.blockPos) instanceof KeycardProgrammerBlockEntity be) {
            this.blockEntity = be;
        }
    }

    // Server-side constructor
    public KeycardProgrammerScreenHandler(int syncId, PlayerInventory playerInventory, KeycardProgrammerBlockEntity be, PropertyDelegate delegate) {
        this(syncId, playerInventory, be, delegate, be.getPos());
        this.blockEntity = be;
    }

    private KeycardProgrammerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate delegate, BlockPos pos) {
        super(ModScreenHandlerTypes.KEYCARD_PROGRAMMER, syncId);
        checkSize(inventory, KeycardProgrammerBlockEntity.INVENTORY_SIZE);
        this.inventory = inventory;
        this.propertyDelegate = delegate;
        this.blockPos = pos;
        addProperties(delegate);
        inventory.onOpen(playerInventory.player);

        // Machine Slots (0..6)
        // Row 0 (Page 0)
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, 11, 38, this, 0));
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_OPERATOR, 94, 38, this, 0));
        // Row 1 (Page 0)
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_RELAY_TWO, 11, 78, this, 0));
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE, 94, 78, this, 0));
        // Page 1
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO, 80, 39, this, 1));
        // Page 2
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT, 24, 40, this, 2));
        this.addSlot(new ProgrammerSlot(inventory, KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT, 136, 40, this, 2));

        // Player Inventory (27 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        // Hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 168));
        }
    }

    public int getPage() {
        return propertyDelegate.get(0);
    }

    public void setPage(int page) {
        propertyDelegate.set(0, page);
        if (blockEntity != null) {
            blockEntity.setPage(page);
        }
    }

    public boolean isBackupActive() {
        return propertyDelegate.get(1) == 1;
    }

    public int getBackupTicks() {
        return propertyDelegate.get(2);
    }

    public boolean isBackupSuccess() {
        return propertyDelegate.get(3) == 1;
    }

    public KeycardProgrammerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < KeycardProgrammerBlockEntity.INVENTORY_SIZE) {
                // From machine slots to player inventory
                if (!this.insertItem(originalStack, KeycardProgrammerBlockEntity.INVENTORY_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player inventory to active page machine slots
                int activePage = getPage();
                boolean moved = false;
                if (activePage == 0) {
                    moved = this.insertItem(originalStack, 0, 4, false);
                } else if (activePage == 1) {
                    moved = this.insertItem(originalStack, 4, 5, false);
                } else if (activePage == 2) {
                    moved = this.insertItem(originalStack, 5, 7, false);
                }

                if (!moved) {
                    // Hotbar <-> inventory transfer
                    if (invSlot < KeycardProgrammerBlockEntity.INVENTORY_SIZE + 27) {
                        if (!this.insertItem(originalStack, KeycardProgrammerBlockEntity.INVENTORY_SIZE + 27, this.slots.size(), false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.insertItem(originalStack, KeycardProgrammerBlockEntity.INVENTORY_SIZE, KeycardProgrammerBlockEntity.INVENTORY_SIZE + 27, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return newStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    public static class ProgrammerSlot extends Slot {
        private final KeycardProgrammerScreenHandler handler;
        private final int requiredPage;

        public ProgrammerSlot(Inventory inventory, int index, int x, int y, KeycardProgrammerScreenHandler handler, int requiredPage) {
            super(inventory, index, x, y);
            this.handler = handler;
            this.requiredPage = requiredPage;
        }

        @Override
        public boolean isEnabled() {
            return handler.getPage() == requiredPage;
        }
    }
}
