package com.ntmdoors.tile;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * Keycard programmer. The block is a desk console with a five-slot internal inventory:
 *
 * <ul>
 *     <li>0 / 2: relay keys for the two binding operations</li>
 *     <li>1: the operator keycard being bound</li>
 *     <li>3: the RFID card being bound on the first page</li>
 *     <li>4: the RFID card being re-coloured on the second page</li>
 * </ul>
 *
 * <p>The programming logic itself is deliberately left for later. The inventory and the cosmetic
 * colour swap are real, so cards survive a restart and are dropped when the console is broken.</p>
 */
public class TileEntityKeycardProgrammer extends TileEntity implements IInventory {

    public static final int SLOT_COUNT = 5;
    public static final int SLOT_RELAY_ONE = 0;
    public static final int SLOT_OPERATOR = 1;
    public static final int SLOT_RELAY_TWO = 2;
    public static final int SLOT_RFID_PAGE_ONE = 3;
    public static final int SLOT_RFID_PAGE_TWO = 4;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    public TileEntityKeycardProgrammer() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------ inventory

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < SLOT_COUNT ? slots[index] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = slots[index];
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken = stack.splitStack(count);
        if (stack.isEmpty()) slots[index] = ItemStack.EMPTY;
        markDirty();
        return taken;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = slots[index];
        slots[index] = ItemStack.EMPTY;
        if (!stack.isEmpty()) markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SLOT_COUNT) return;
        slots[index] = stack == null ? ItemStack.EMPTY : stack;
        if (!slots[index].isEmpty() && slots[index].getCount() > getInventoryStackLimit()) {
            slots[index].setCount(getInventoryStackLimit());
        }
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        if (world == null || world.getTileEntity(pos) != this) return false;
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (stack.isEmpty()) return false;
        switch (index) {
            case SLOT_RELAY_ONE:
            case SLOT_RELAY_TWO:
                return stack.getItem() instanceof ItemRelayKey;
            case SLOT_OPERATOR:
                return stack.getItem() instanceof ItemOperatorKeycard;
            case SLOT_RFID_PAGE_ONE:
            case SLOT_RFID_PAGE_TWO:
                return stack.getItem() instanceof ItemRfidCard
                        && !(stack.getItem() instanceof ItemOperatorKeycard)
                        && !(stack.getItem() instanceof ItemCreativeKeycard);
            default:
                return false;
        }
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        Arrays.fill(slots, ItemStack.EMPTY);
        markDirty();
    }

    @Override
    public String getName() {
        return "container.ntmdoors.keycard_programmer";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    /** Drops every stored card into the world; called when the block is broken. */
    public void dropContents(World world, BlockPos pos) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = slots[i];
            if (stack.isEmpty()) continue;
            InventoryHelper.spawnItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, stack);
            slots[i] = ItemStack.EMPTY;
        }
        markDirty();
    }

    // ------------------------------------------------------------ persistence

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            slots[i].writeToNBT(entry);
            items.appendTag(entry);
        }
        tag.setTag("Items", items);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        Arrays.fill(slots, ItemStack.EMPTY);
        NBTTagList items = tag.getTagList("Items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < SLOT_COUNT) {
                slots[slot] = new ItemStack(entry);
            }
        }
    }
}
