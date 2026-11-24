// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.util.Names;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carried case with two 4x4 storage sections: one for keycards and one for every relay/utility key.
 * Its contents live in the stack's own NBT, so a holder keeps its items through inventory moves,
 * death drops and world saves. Right-clicking opens the 8x4 combined grid; each slot holds exactly
 * one item.
 */
public class ItemKeycardHolder extends Item {

    public static final int KEYCARD_SLOT_COUNT = 16;
    public static final int RELAY_KEY_SLOT_START = KEYCARD_SLOT_COUNT;
    public static final int RELAY_KEY_SLOT_COUNT = 16;
    public static final int SLOT_COUNT = KEYCARD_SLOT_COUNT + RELAY_KEY_SLOT_COUNT;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_ITEM = "Item";

    public ItemKeycardHolder() {
        setRegistryName(NTMDoors.MODID, "keycard_holder");
        setTranslationKey(NTMDoors.MODID + ".keycard_holder");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // The server opens the container; vanilla then sends the open-window packet to the client,
        // which builds the matching GUI from its own copy of the same held stack.
        if (!world.isRemote) {
            player.openGui(NTMDoors.INSTANCE, GuiHandler.KEYCARD_HOLDER, world,
                    hand.ordinal(), 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Stores up to 16 keycards and 16 relay keys.",
                "Also holds Relay Keys",
                "Right-click to open.");
        int cards = countKeycards(stack);
        int keys = countRelayKeys(stack);
        if (cards > 0 || keys > 0) {
            tooltip.add(TextFormatting.GRAY + "Stored: " + cards + "/" + KEYCARD_SLOT_COUNT
                    + " cards, " + keys + "/" + RELAY_KEY_SLOT_COUNT + " keys");
        }
    }

    /** True for every keycard the holder accepts: colour, themed, biometric, operator and creative. */
    public static boolean isKeycard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemRfidCard;
    }

    /** True for every relay and utility key the holder accepts. */
    public static boolean isRelayKey(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ItemRelayKey
                || item instanceof ItemRedstoneRelayKey
                || item instanceof ItemMotionRelayKey
                || item instanceof ItemTerminalRelayKey
                || item instanceof ItemWeaponsRelayKey
                || item instanceof ItemSafeBindingKey
                || item instanceof ItemTerminalBackupKey
                || item instanceof ItemUndefinedRelayKey
                || item instanceof ItemBreakerRelayKey;
    }

    /** Checks the slot's section: keycards first, relay/utility keys second. */
    public static boolean isStorable(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        return slot < RELAY_KEY_SLOT_START ? isKeycard(stack) : isRelayKey(stack);
    }

    /** Reads the stored items into a slot list. Missing or malformed entries become empty slots. */
    public static NonNullList<ItemStack> readContents(ItemStack holder) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        if (holder.isEmpty() || !holder.hasTagCompound()) return items;

        NBTTagList list = holder.getTagCompound().getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasKey(TAG_SLOT, Constants.NBT.TAG_BYTE)
                    || !entry.hasKey(TAG_ITEM, Constants.NBT.TAG_COMPOUND)) continue;
            int slot = entry.getByte(TAG_SLOT) & 0xFF;
            if (slot >= SLOT_COUNT) continue;

            ItemStack stored = new ItemStack(entry.getCompoundTag(TAG_ITEM));
            if (!isStorable(slot, stored)) continue;
            // Stored item families are max stack size one; sanitise edited NBT piles.
            if (stored.getCount() > 1) stored.setCount(1);
            items.set(slot, stored);
        }
        return items;
    }

    /** Writes the given slots back onto the holder stack. Empty slots are simply omitted. */
    public static void writeContents(ItemStack holder, NonNullList<ItemStack> items) {
        if (holder.isEmpty()) return;

        NBTTagCompound tag = holder.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT && i < items.size(); i++) {
            ItemStack stored = items.get(i);
            if (stored.isEmpty()) continue;

            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte(TAG_SLOT, (byte) i);
            entry.setTag(TAG_ITEM, stored.writeToNBT(new NBTTagCompound()));
            list.appendTag(entry);
        }

        if (list.tagCount() == 0) {
            tag.removeTag(TAG_ITEMS);
            holder.setTagCompound(tag.getSize() == 0 ? null : tag);
        } else {
            tag.setTag(TAG_ITEMS, list);
            holder.setTagCompound(tag);
        }
    }

    public static int countKeycards(ItemStack holder) {
        return countSection(holder, 0, RELAY_KEY_SLOT_START);
    }

    public static int countRelayKeys(ItemStack holder) {
        return countSection(holder, RELAY_KEY_SLOT_START, SLOT_COUNT);
    }

    private static int countSection(ItemStack holder, int start, int end) {
        int count = 0;
        NonNullList<ItemStack> items = readContents(holder);
        for (int i = start; i < end && i < items.size(); i++) {
            if (!items.get(i).isEmpty()) count++;
        }
        return count;
    }
}
