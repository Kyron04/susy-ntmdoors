package com.ntmdoors.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/** Helpers for items carried in either hand, and for pushing NBT changes back to the client. */
public final class HeldItems {

    /** The first stack in either hand whose item is an instance of {@code type}, else empty. */
    public static ItemStack find(@Nullable EntityPlayer player, Class<? extends Item> type) {
        if (player == null) return ItemStack.EMPTY;

        ItemStack main = player.getHeldItemMainhand();
        if (type.isInstance(main.getItem())) return main;

        ItemStack off = player.getHeldItemOffhand();
        return type.isInstance(off.getItem()) ? off : ItemStack.EMPTY;
    }

    /** True when either hand holds an item of {@code type}. */
    public static boolean holds(@Nullable EntityPlayer player, Class<? extends Item> type) {
        return !find(player, type).isEmpty();
    }

    /**
     * The first matching stack in the main inventory (including the hotbar) or the offhand. Used
     * by the Motion Sensor console, where the sensor key occupies a hand and the Operator Keycard
     * that supplies the assignable entries sits elsewhere in the inventory.
     */
    public static ItemStack findInInventory(@Nullable EntityPlayer player, Class<? extends Item> type) {
        if (player == null) return ItemStack.EMPTY;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (type.isInstance(stack.getItem())) return stack;
        }
        ItemStack off = player.getHeldItemOffhand();
        return type.isInstance(off.getItem()) ? off : ItemStack.EMPTY;
    }

    /**
     * Flags the player's inventory dirty and sends changed held stacks to their client. On the
     * integrated server the player is not an {@link EntityPlayerMP}, so this is a no-op there.
     */
    public static void sync(@Nullable EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        player.inventory.markDirty();
        ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
    }

    private HeldItems() {
    }
}
