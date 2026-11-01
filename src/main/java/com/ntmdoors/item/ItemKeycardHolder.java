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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carried case for up to twelve keycards. Its contents live in the stack's own NBT, so a holder
 * keeps its cards through inventory moves, death drops and world saves. Right-clicking opens the
 * 3x4 storage grid; the holder itself is never a valid card, and cards are max stack size one, so
 * each slot holds exactly one card.
 */
public class ItemKeycardHolder extends Item {

    public static final int SLOT_COUNT = 12;

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

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Stores up to 12 keycards",
                "Right-click to open");
        int stored = count(stack);
        if (stored > 0) {
            tooltip.add(TextFormatting.GRAY + "Cards stored: " + stored + " / " + SLOT_COUNT);
        }
    }

    /** True for every keycard the holder accepts: colour, themed, biometric, operator and creative. */
    public static boolean isKeycard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemRfidCard;
    }

    /** Reads the stored cards into a slot list. Missing or malformed entries become empty slots. */
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

            ItemStack card = new ItemStack(entry.getCompoundTag(TAG_ITEM));
            if (!isKeycard(card)) continue;
            // Keycards no longer stack, so a malformed or edited entry can never load as a pile.
            if (card.getCount() > 1) card.setCount(1);
            items.set(slot, card);
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
            ItemStack card = items.get(i);
            if (card.isEmpty()) continue;

            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte(TAG_SLOT, (byte) i);
            entry.setTag(TAG_ITEM, card.writeToNBT(new NBTTagCompound()));
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

    /** Number of non-empty storage slots, used by the tooltip and the GUI header. */
    public static int count(ItemStack holder) {
        int count = 0;
        for (ItemStack card : readContents(holder)) {
            if (!card.isEmpty()) count++;
        }
        return count;
    }
}
