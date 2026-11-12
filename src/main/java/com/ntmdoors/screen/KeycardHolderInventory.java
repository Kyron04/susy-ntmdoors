package com.ntmdoors.screen;

import com.ntmdoors.item.ItemKeycardHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * The twelve storage slots of a Keycard Holder, backed by the holder stack's NBT.
 * Every mutation writes the stack immediately to guarantee persistence.
 */
public class KeycardHolderInventory extends SimpleInventory {

    private final ItemStack holder;
    private final PlayerEntity owner;
    private boolean loading;

    public KeycardHolderInventory(ItemStack holder, PlayerEntity owner) {
        super(ItemKeycardHolder.SLOT_COUNT);
        this.holder = holder;
        this.owner = owner;
        load();
    }

    private void load() {
        loading = true;
        DefaultedList<ItemStack> stored = ItemKeycardHolder.readContents(holder);
        for (int i = 0; i < stored.size() && i < size(); i++) {
            super.setStack(i, stored.get(i));
        }
        loading = false;
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return ItemKeycardHolder.isAllowed(stack);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        super.setStack(slot, stack);
        save();
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = super.removeStack(slot, amount);
        save();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = super.removeStack(slot);
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

    /** Writes current slots back onto the holder stack and flags player inventory for sync. */
    public void save() {
        if (loading) return;

        DefaultedList<ItemStack> items = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        for (int i = 0; i < size(); i++) {
            items.set(i, getStack(i));
        }
        ItemKeycardHolder.writeContents(holder, items);

        if (owner != null && !owner.getWorld().isClient()) {
            owner.getInventory().markDirty();
        }
    }
}
