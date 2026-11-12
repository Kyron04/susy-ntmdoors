package com.ntmdoors.test.security;

import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.screen.KeycardHolderInventory;
import com.ntmdoors.screen.KeycardHolderScreenHandler;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeycardHolderTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
        com.ntmdoors.sound.ModSounds.registerModSounds();
        com.ntmdoors.block.ModBlocks.registerModBlocks();
        com.ntmdoors.block.entity.ModBlockEntities.registerModBlockEntities();
        com.ntmdoors.item.ModItems.registerModItems();
    }

    @Test
    @DisplayName("Verify Keycard Holder has 12 slots and default empty count")
    public void test12SlotsCapacityAndDefaultEmpty() {
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        assertEquals(12, ItemKeycardHolder.SLOT_COUNT);
        assertEquals(0, ItemKeycardHolder.count(holder));

        DefaultedList<ItemStack> contents = ItemKeycardHolder.readContents(holder);
        assertEquals(12, contents.size());
        for (int i = 0; i < 12; i++) {
            assertTrue(contents.get(i).isEmpty(), "Slot " + i + " must initially be empty");
        }
    }

    @Test
    @DisplayName("Verify NBT serialization roundtrip preserves slot indices, items, and clamps count to 1")
    public void testNbtSerializationRoundtrip() {
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(12, ItemStack.EMPTY);

        ItemStack redCard = new ItemStack(ModItems.RED_RFID_CARD, 5); // Pile of 5
        ItemStack blueCard = new ItemStack(ModItems.BLUE_RFID_CARD, 1);
        ItemStack level5Card = new ItemStack(ModItems.LEVEL_5_ACCESS_KEYCARD, 1);

        items.set(0, redCard);
        items.set(5, blueCard);
        items.set(11, level5Card);

        ItemKeycardHolder.writeContents(holder, items);
        assertEquals(3, ItemKeycardHolder.count(holder));

        // Read back into fresh list
        DefaultedList<ItemStack> readBack = ItemKeycardHolder.readContents(holder);
        assertEquals(12, readBack.size());

        assertFalse(readBack.get(0).isEmpty());
        assertEquals(ModItems.RED_RFID_CARD, readBack.get(0).getItem());
        assertEquals(1, readBack.get(0).getCount(), "Card count must be clamped to 1");

        assertTrue(readBack.get(1).isEmpty());
        assertTrue(readBack.get(4).isEmpty());

        assertFalse(readBack.get(5).isEmpty());
        assertEquals(ModItems.BLUE_RFID_CARD, readBack.get(5).getItem());
        assertEquals(1, readBack.get(5).getCount());

        assertFalse(readBack.get(11).isEmpty());
        assertEquals(ModItems.LEVEL_5_ACCESS_KEYCARD, readBack.get(11).getItem());
        assertEquals(1, readBack.get(11).getCount());
    }

    @Test
    @DisplayName("Verify clearing all slots purges Items NBT tag completely")
    public void testEmptyNbtPurge() {
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(12, ItemStack.EMPTY);
        items.set(2, new ItemStack(ModItems.RED_RFID_CARD));

        ItemKeycardHolder.writeContents(holder, items);
        assertNotNull(holder.getNbt());
        assertTrue(holder.getNbt().contains(ItemKeycardHolder.TAG_ITEMS));

        // Clear slots and write
        DefaultedList<ItemStack> empty = DefaultedList.ofSize(12, ItemStack.EMPTY);
        ItemKeycardHolder.writeContents(holder, empty);

        if (holder.hasNbt()) {
            assertFalse(holder.getNbt().contains(ItemKeycardHolder.TAG_ITEMS), "Items tag must be purged when empty");
        }
        assertEquals(0, ItemKeycardHolder.count(holder));
    }

    @Test
    @DisplayName("Verify whitelist accepts all cards and keys, but strictly rejects ItemKeycardHolder and foreign items")
    public void testWhitelistFiltering() {
        // 1. Accepts RFID cards
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.BLANK_RFID_CARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.RED_RFID_CARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.BLUE_RFID_CARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.LEVEL_12_ACCESS_KEYCARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.OPERATOR_KEYCARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.CREATIVE_KEYCARD)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.BIOMETRIC_KEYCARD)));

        // 2. Accepts security relay keys
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.RELAY_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.BIOMETRIC_RELAY_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.SAFE_BINDING_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.MOTION_RELAY_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.REDSTONE_RELAY_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.TERMINAL_BACKUP_KEY)));
        assertTrue(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.WEAPONS_RELAY_KEY)));

        // 3. Strictly rejects ItemKeycardHolder (nesting prevention)
        assertFalse(ItemKeycardHolder.isAllowed(new ItemStack(ModItems.KEYCARD_HOLDER)), "Holder must never accept another holder");

        // 4. Strictly rejects foreign items and blocks
        assertFalse(ItemKeycardHolder.isAllowed(new ItemStack(Items.IRON_INGOT)));
        assertFalse(ItemKeycardHolder.isAllowed(new ItemStack(Items.DIRT)));
        assertFalse(ItemKeycardHolder.isAllowed(new ItemStack(Items.STICK)));
        assertFalse(ItemKeycardHolder.isAllowed(ItemStack.EMPTY));
    }

    @Test
    @DisplayName("Verify KeycardHolderInventory auto-saves to ItemStack NBT upon mutation")
    public void testInventoryAutoSave() {
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        KeycardHolderInventory inventory = new KeycardHolderInventory(holder, null);

        assertEquals(12, inventory.size());
        assertEquals(1, inventory.getMaxCountPerStack());

        // Set stack in slot 3
        inventory.setStack(3, new ItemStack(ModItems.GREEN_RFID_CARD));

        // Underlying holder NBT must immediately be updated
        assertEquals(1, ItemKeycardHolder.count(holder));
        DefaultedList<ItemStack> contents = ItemKeycardHolder.readContents(holder);
        assertEquals(ModItems.GREEN_RFID_CARD, contents.get(3).getItem());

        // Remove stack
        inventory.removeStack(3);
        assertEquals(0, ItemKeycardHolder.count(holder));
        assertTrue(ItemKeycardHolder.readContents(holder).get(3).isEmpty());
    }

    @Test
    @DisplayName("Verify KeycardSlot canInsert respects whitelist")
    public void testKeycardSlotCanInsert() {
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        KeycardHolderInventory inventory = new KeycardHolderInventory(holder, null);
        KeycardHolderScreenHandler.KeycardSlot slot = new KeycardHolderScreenHandler.KeycardSlot(inventory, 0, 0, 0);

        assertTrue(slot.canInsert(new ItemStack(ModItems.RED_RFID_CARD)));
        assertTrue(slot.canInsert(new ItemStack(ModItems.WEAPONS_RELAY_KEY)));
        assertFalse(slot.canInsert(new ItemStack(ModItems.KEYCARD_HOLDER)));
        assertFalse(slot.canInsert(new ItemStack(Items.DIAMOND)));
        assertEquals(1, slot.getMaxItemCount());
    }

    @Test
    @DisplayName("Verify KeycardScanner proxy scanning with Keycard Holder")
    public void testScannerProxyWithHolder() {
        KeycardScannerBlock block = new KeycardScannerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        KeycardScannerBlockEntity scanner = new KeycardScannerBlockEntity(null, BlockPos.ORIGIN, state);

        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        KeycardHolderInventory inv = new KeycardHolderInventory(holder, null);
        inv.setStack(0, new ItemStack(ModItems.YELLOW_RFID_CARD));

        // Proxy scan with holder
        scanner.useHolder(null, holder);
        // Scanner should enter SCANNING state
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
    }
}
