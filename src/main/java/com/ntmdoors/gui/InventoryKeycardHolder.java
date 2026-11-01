package com.ntmdoors.gui;

import com.ntmdoors.item.ItemKeycardHolder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * The twelve storage slots of a Keycard Holder, backed by the holder stack's NBT.
 *
 * <p>Vanilla's {@link InventoryBasic} already provides the slot bookkeeping and change listener;
 * this class only adds loading, saving and the keycard whitelist. Every mutation writes the stack
 * immediately so a crash between clicks cannot lose a card.</p>
 */
public class InventoryKeycardHolder extends InventoryBasic {

    private final ItemStack holder;
    private final EntityPlayer owner;
    private boolean loading;

    public InventoryKeycardHolder(ItemStack holder, EntityPlayer owner) {
        super("container.ntmdoors.keycard_holder", false, ItemKeycardHolder.SLOT_COUNT);
        this.holder = holder;
        this.owner = owner;
        load();
    }

    private void load() {
        loading = true;
        NonNullList<ItemStack> stored = ItemKeycardHolder.readContents(holder);
        for (int i = 0; i < stored.size(); i++) {
            super.setInventorySlotContents(i, stored.get(i));
        }
        loading = false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return ItemKeycardHolder.isKeycard(stack);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        super.setInventorySlotContents(index, stack);
        save();
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack result = super.decrStackSize(index, count);
        save();
        return result;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack result = super.removeStackFromSlot(index);
        save();
        return result;
    }

    @Override
    public void clear() {
        super.clear();
        save();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        save();
    }

    /** Writes the current slots back onto the holder stack and flags the player inventory for sync. */
    public void save() {
        if (loading) return;

        NonNullList<ItemStack> items = NonNullList.withSize(getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < getSizeInventory(); i++) {
            items.set(i, getStackInSlot(i));
        }
        ItemKeycardHolder.writeContents(holder, items);

        if (owner != null && !owner.world.isRemote) {
            // The holder is a player-inventory stack, so the open container's normal slot compare
            // notices the changed NBT and sends it to the client on the next tick.
            owner.inventory.markDirty();
        }
    }
}
