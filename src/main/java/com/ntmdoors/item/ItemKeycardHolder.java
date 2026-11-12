package com.ntmdoors.item;

import com.ntmdoors.screen.KeycardHolderScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Carried case for up to twelve keycards. Its contents live in the stack's own NBT, so a holder
 * keeps its cards through inventory moves, death drops and world saves. Right-clicking opens the
 * 3x4 storage grid; the holder itself is never a valid card, and cards are max stack size one, so
 * each slot holds exactly one card.
 */
public class ItemKeycardHolder extends Item {

    public static final int SLOT_COUNT = 12;

    public static final String TAG_ITEMS = "Items";
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_ITEM = "Item";

    public ItemKeycardHolder() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                    buf.writeBoolean(hand == Hand.OFF_HAND);
                }

                @Override
                public Text getDisplayName() {
                    return Text.translatable("container.ntmdoors.keycard_holder");
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                    return new KeycardHolderScreenHandler(syncId, inv, stack, hand);
                }
            });
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Stores up to 12 keycards.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click to open.").formatted(Formatting.GRAY));
        int stored = count(stack);
        if (stored > 0) {
            tooltip.add(Text.literal("Cards stored: " + stored + " / " + SLOT_COUNT).formatted(Formatting.GRAY));
        }
    }

    /**
     * Whitelist filter: accepts RFID keycards, relay keys, safe binding keys, motion keys, redstone keys,
     * terminal keys, and weapons keys; strictly rejects empty stacks and ItemKeycardHolder itself.
     */
    public static boolean isAllowed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ItemKeycardHolder) return false;
        return item instanceof ItemRfidCard
                || item instanceof ItemRelayKey
                || item instanceof ItemSafeBindingKey
                || item instanceof ItemMotionRelayKey
                || item instanceof ItemRedstoneRelayKey
                || item instanceof ItemTerminalBackupKey
                || item instanceof ItemWeaponsRelayKey;
    }

    /** True for every keycard or security key the holder accepts. */
    public static boolean isKeycard(ItemStack stack) {
        return isAllowed(stack);
    }

    /** Reads the stored cards into a slot list. Missing or malformed entries become empty slots. */
    public static DefaultedList<ItemStack> readContents(ItemStack holder) {
        DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
        if (holder.isEmpty() || !holder.hasNbt()) return items;

        synchronized (holder) {
            NbtCompound tag = holder.getNbt();
            if (tag == null || !tag.contains(TAG_ITEMS, NbtElement.LIST_TYPE)) return items;

            NbtList list = tag.getList(TAG_ITEMS, NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound entry = list.getCompound(i);
                if (!entry.contains(TAG_SLOT, NbtElement.BYTE_TYPE)
                        || !entry.contains(TAG_ITEM, NbtElement.COMPOUND_TYPE)) continue;
                int slot = entry.getByte(TAG_SLOT) & 0xFF;
                if (slot >= SLOT_COUNT) continue;

                ItemStack card = ItemStack.fromNbt(entry.getCompound(TAG_ITEM));
                if (!isAllowed(card)) continue;
                if (card.getCount() > 1) card.setCount(1);
                items.set(slot, card);
            }
            return items;
        }
    }

    /** Writes the given slots back onto the holder stack. Empty slots are simply omitted. */
    public static void writeContents(ItemStack holder, List<ItemStack> items) {
        if (holder.isEmpty()) return;

        synchronized (holder) {
            NbtCompound tag = holder.getOrCreateNbt();
            NbtList list = new NbtList();
            for (int i = 0; i < SLOT_COUNT && i < items.size(); i++) {
                ItemStack card = items.get(i);
                if (card.isEmpty()) continue;

                NbtCompound entry = new NbtCompound();
                entry.putByte(TAG_SLOT, (byte) i);
                entry.put(TAG_ITEM, card.writeNbt(new NbtCompound()));
                list.add(entry);
            }

            if (list.isEmpty()) {
                tag.remove(TAG_ITEMS);
                if (tag.isEmpty()) {
                    holder.setNbt(null);
                }
            } else {
                tag.put(TAG_ITEMS, list);
            }
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
