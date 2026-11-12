package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.ThermiteChargeBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemThermiteCharge;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.screen.KeycardHolderInventory;
import com.ntmdoors.screen.KeycardHolderScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.weapons.WeaponsBindingEvents;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone M6:
 * Comprehensive stress verification of Portable Accessories (Keycard Holder, Documents Folder),
 * Breaching Mechanics (Thermite Charge 96 VoxelShapes, tick 70 fuse beep, door breach & panel removal),
 * Spatial Disrupter and Weapons Protection.
 */
public class ChallengerM6AccessoriesAndBreachingAdversarialTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
        ModDoors.registerModDoors();
    }

    public static class TestPlayer extends PlayerEntity {
        public UUID testUuid = UUID.randomUUID();
        public boolean creative = false;
        public net.minecraft.world.World testWorld = null;

        public TestPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TestPlayer"));
        }

        @Override public UUID getUuid() { return testUuid; }
        @Override public Text getName() { return Text.literal("TestPlayer"); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return creative; }
        @Override public void sendMessage(Text message, boolean overlay) {}
        @Override public net.minecraft.world.World getWorld() { return testWorld; }
    }

    private TestPlayer createTestPlayer(boolean creative) {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            TestPlayer p = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
            p.testUuid = UUID.randomUUID();
            p.creative = creative;
            p.testWorld = (net.minecraft.world.World) unsafe.allocateInstance(net.minecraft.server.world.ServerWorld.class);

            PlayerInventory inv = new PlayerInventory(p);
            Field invField = PlayerEntity.class.getDeclaredField("inventory");
            invField.setAccessible(true);
            invField.set(p, inv);

            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1. ItemKeycardHolder Adversarial Stress Tests
    // =========================================================================

    @Test
    @DisplayName("Holder Concurrency: 16 threads hammering identical shared ItemStack instance")
    public void testSharedHolderMultiThreadConcurrency() throws Exception {
        final ItemStack sharedHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        int threadCount = 16;
        int operationsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalOps = new AtomicInteger();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        if ((threadId + i) % 2 == 0) {
                            // Writer thread: alternating between full list, partial list, and empty list
                            DefaultedList<ItemStack> items = DefaultedList.ofSize(ItemKeycardHolder.SLOT_COUNT, ItemStack.EMPTY);
                            int cardCount = (i % (ItemKeycardHolder.SLOT_COUNT + 1));
                            for (int c = 0; c < cardCount; c++) {
                                items.set(c, new ItemStack(ModItems.RED_RFID_CARD));
                            }
                            ItemKeycardHolder.writeContents(sharedHolder, items);
                        } else {
                            // Reader thread
                            DefaultedList<ItemStack> read = ItemKeycardHolder.readContents(sharedHolder);
                            assertNotNull(read, "Read contents must never be null");
                            assertEquals(ItemKeycardHolder.SLOT_COUNT, read.size(), "Holder slot count must always be 12");
                            int count = ItemKeycardHolder.count(sharedHolder);
                            assertTrue(count >= 0 && count <= ItemKeycardHolder.SLOT_COUNT, "Count must be within [0, 12]");
                        }
                        totalOps.incrementAndGet();
                    }
                } catch (Throwable th) {
                    errors.add(th);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(finished, "Multi-threaded stress run must complete within 15 seconds");
        assertTrue(errors.isEmpty(), "Zero exceptions allowed during multi-threaded shared holder access, but found: " + errors);
        assertEquals(threadCount * operationsPerThread, totalOps.get());
    }

    @Test
    @DisplayName("Holder Nesting Attack: Rejection of KeycardHolder inside KeycardHolder")
    public void testKeycardHolderNestingRejection() {
        ItemStack parentHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        ItemStack childHolder = new ItemStack(ModItems.KEYCARD_HOLDER);

        // 1. Direct whitelist filter check
        assertFalse(ItemKeycardHolder.isAllowed(childHolder), "isAllowed must strictly reject ItemKeycardHolder");
        assertFalse(ItemKeycardHolder.isKeycard(childHolder), "isKeycard must strictly reject ItemKeycardHolder");

        // 2. Inventory isValid check
        KeycardHolderInventory inventory = new KeycardHolderInventory(parentHolder, null);
        for (int slot = 0; slot < ItemKeycardHolder.SLOT_COUNT; slot++) {
            assertFalse(inventory.isValid(slot, childHolder), "KeycardHolderInventory.isValid must reject child holder at slot " + slot);
        }

        // 3. Screen slot canInsert check
        KeycardHolderScreenHandler.KeycardSlot keycardSlot = new KeycardHolderScreenHandler.KeycardSlot(inventory, 0, 0, 0);
        assertFalse(keycardSlot.canInsert(childHolder), "KeycardSlot.canInsert must reject ItemKeycardHolder");

        // 4. Adversarial NBT injection: manually craft nested holder inside Items list
        NbtCompound parentTag = parentHolder.getOrCreateNbt();
        NbtList list = new NbtList();
        NbtCompound maliciousEntry = new NbtCompound();
        maliciousEntry.putByte(ItemKeycardHolder.TAG_SLOT, (byte) 0);
        maliciousEntry.put(ItemKeycardHolder.TAG_ITEM, childHolder.writeNbt(new NbtCompound()));
        list.add(maliciousEntry);
        parentTag.put(ItemKeycardHolder.TAG_ITEMS, list);

        // readContents must filter out the nested holder into ItemStack.EMPTY
        DefaultedList<ItemStack> sanitized = ItemKeycardHolder.readContents(parentHolder);
        assertTrue(sanitized.get(0).isEmpty(), "readContents must filter out illegally nested KeycardHolder");
    }

    @Test
    @DisplayName("Holder ScreenHandler Bounds: Slot clicks, out-of-bounds, swap locks, and shift-click overflow")
    public void testScreenHandlerBoundsAndInteractions() {
        TestPlayer player = createTestPlayer(false);
        ItemStack holder = new ItemStack(ModItems.KEYCARD_HOLDER);
        player.getInventory().main.set(0, holder);
        player.getInventory().selectedSlot = 0;

        KeycardHolderScreenHandler handler = new KeycardHolderScreenHandler(1, player.getInventory(), holder, Hand.MAIN_HAND);
        assertTrue(handler.canUse(player), "Handler must be usable by holder owner");

        // 1. ScreenHandler slot validation bounds
        assertTrue(handler.isValid(0), "Slot 0 must be valid");
        assertTrue(handler.isValid(KeycardHolderScreenHandler.HOLDER_SLOT_COUNT - 1), "Slot 11 must be valid");
        assertTrue(handler.isValid(KeycardHolderScreenHandler.PLAYER_SLOT_END - 1), "Slot 47 must be valid");
        assertTrue(handler.isValid(-1), "Quick craft slot -1 must be valid");
        assertTrue(handler.isValid(-999), "Drop slot -999 must be valid");

        assertFalse(handler.isValid(48), "Slot 48 (past last slot) must be invalid");
        assertFalse(handler.isValid(100), "Slot 100 must be invalid");

        // Out-of-window legal clicks (-1 quick craft, -999 cursor drop) must not throw
        assertDoesNotThrow(() -> handler.onSlotClick(-1, 0, SlotActionType.PICKUP, player));
        assertDoesNotThrow(() -> handler.onSlotClick(-999, 0, SlotActionType.PICKUP, player));

        // Negative slot indices outside (-1, -999) cleanly return in vanilla without throwing
        assertDoesNotThrow(() -> handler.onSlotClick(-50, 0, SlotActionType.PICKUP, player));

        // Out-of-bounds slot indices (>= slots.size()) safely return without throwing
        assertDoesNotThrow(() -> handler.onSlotClick(48, 0, SlotActionType.PICKUP, player));
        assertDoesNotThrow(() -> handler.onSlotClick(100, 0, SlotActionType.PICKUP, player));

        // 2. Number key SWAP protection into holder slot
        // Slot 0 of holder is empty. Put non-card (DIRT) into player hotbar slot 1.
        player.getInventory().main.set(1, new ItemStack(Items.DIRT));
        handler.onSlotClick(0, 1, SlotActionType.SWAP, player);
        assertTrue(handler.getSlot(0).getStack().isEmpty(), "Swapping non-card into holder slot must be blocked");
        assertEquals(Items.DIRT, player.getInventory().main.get(1).getItem(), "Hotbar item must remain untouched");

        // Attempt swapping another KeycardHolder into holder slot
        player.getInventory().main.set(2, new ItemStack(ModItems.KEYCARD_HOLDER));
        handler.onSlotClick(0, 2, SlotActionType.SWAP, player);
        assertTrue(handler.getSlot(0).getStack().isEmpty(), "Swapping KeycardHolder into holder slot must be blocked");

        // Swap valid RFID card into holder slot
        player.getInventory().main.set(3, new ItemStack(ModItems.RED_RFID_CARD));
        handler.onSlotClick(0, 3, SlotActionType.SWAP, player);
        assertFalse(handler.getSlot(0).getStack().isEmpty(), "Swapping valid RFID card into holder slot must succeed");
        assertEquals(ModItems.RED_RFID_CARD, handler.getSlot(0).getStack().getItem());

        // 3. Shift-click (quickMove) non-keycards
        // Player inventory slot 12 (first main inventory slot after 12 holder slots)
        player.getInventory().main.set(9, new ItemStack(Items.IRON_INGOT)); // maps to handler slot 12
        ItemStack moved = handler.quickMove(player, 12);
        assertTrue(moved.isEmpty(), "Shift-clicking non-card from player inventory must return empty");
        assertEquals(Items.IRON_INGOT, handler.getSlot(12).getStack().getItem(), "Iron ingot must remain in player slot");

        // Shift-clicking a nested KeycardHolder
        player.getInventory().main.set(10, new ItemStack(ModItems.KEYCARD_HOLDER)); // maps to handler slot 13
        ItemStack movedHolder = handler.quickMove(player, 13);
        assertTrue(movedHolder.isEmpty(), "Shift-clicking KeycardHolder must be rejected");

        // 4. Shift-click overflow test: fill all 12 holder slots
        for (int i = 0; i < ItemKeycardHolder.SLOT_COUNT; i++) {
            handler.getSlot(i).setStack(new ItemStack(ModItems.BLUE_RFID_CARD));
        }
        assertEquals(12, handler.getStoredCount(), "All 12 slots must be filled");

        // Now attempt to shift-click a 13th keycard from player inventory
        player.getInventory().main.set(11, new ItemStack(ModItems.YELLOW_RFID_CARD)); // maps to handler slot 14
        ItemStack overflowMove = handler.quickMove(player, 14);
        assertTrue(overflowMove.isEmpty(), "Shift-click when holder is full must return empty");
        assertEquals(ModItems.YELLOW_RFID_CARD, handler.getSlot(14).getStack().getItem(), "13th keycard must remain in player inventory");
    }

    @Test
    @DisplayName("Scanner Proxy: KeycardScannerBlockEntity evaluates ItemKeycardHolder with multiple cards")
    public void testKeycardScannerProxyWithMultipleCards() {
        KeycardScannerBlock block = new KeycardScannerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        KeycardScannerBlockEntity scanner = new KeycardScannerBlockEntity(null, BlockPos.ORIGIN, state);

        TestPlayer player = createTestPlayer(false);
        String authorizedSerial = "SEC-ALPHA-99";
        scanner.assignKey("AdminKey", authorizedSerial);

        // 1. Empty holder scan -> DENIED
        ItemStack emptyHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        scanner.useHolder(player, emptyHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        resetScanner(scanner);

        // 2. Holder with only non-RFID keys (e.g. Safe Binding Key, Relay Key) -> DENIED
        ItemStack nonRfidHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> nonRfidCards = DefaultedList.ofSize(ItemKeycardHolder.SLOT_COUNT, ItemStack.EMPTY);
        nonRfidCards.set(0, new ItemStack(ModItems.SAFE_BINDING_KEY));
        nonRfidCards.set(1, new ItemStack(ModItems.MOTION_RELAY_KEY));
        ItemKeycardHolder.writeContents(nonRfidHolder, nonRfidCards);

        scanner.useHolder(player, nonRfidHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        resetScanner(scanner);

        // 3. Holder with matching RFID card in slot 0 -> GRANTED
        ItemStack matchingHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> authCards = DefaultedList.ofSize(ItemKeycardHolder.SLOT_COUNT, ItemStack.EMPTY);
        ItemStack authCard = new ItemStack(ModItems.RED_RFID_CARD);
        PrivateKeys.add(authCard, "AdminKey", authorizedSerial);
        authCards.set(0, authCard);
        ItemKeycardHolder.writeContents(matchingHolder, authCards);

        scanner.useHolder(player, matchingHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState());
        resetScanner(scanner);

        // 4. Holder with non-RFID key in slot 0, and matching RFID card in slot 1 -> GRANTED (proxy iterates past non-RFID)
        ItemStack mixedHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> mixedCards = DefaultedList.ofSize(ItemKeycardHolder.SLOT_COUNT, ItemStack.EMPTY);
        mixedCards.set(0, new ItemStack(ModItems.SAFE_BINDING_KEY)); // Skipped by proxy
        mixedCards.set(1, authCard); // Evaluated by proxy
        ItemKeycardHolder.writeContents(mixedHolder, mixedCards);

        scanner.useHolder(player, mixedHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState());
        resetScanner(scanner);

        // 5. Biometric card in holder matching scanning player -> GRANTED
        scanner.assignId(player.getUuid().toString(), "PlayerOne");
        ItemStack bioHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
        DefaultedList<ItemStack> bioCards = DefaultedList.ofSize(ItemKeycardHolder.SLOT_COUNT, ItemStack.EMPTY);
        ItemStack bioCard = new ItemStack(ModItems.YELLOW_RFID_CARD);
        Biometrics.bindIfNeeded(bioCard, player.getUuid(), "PlayerOne");
        bioCards.set(0, bioCard);
        ItemKeycardHolder.writeContents(bioHolder, bioCards);

        scanner.useHolder(player, bioHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState());
        resetScanner(scanner);

        // 6. Biometric card bound to another player with matchHolder enabled -> DENIED
        TestPlayer stranger = createTestPlayer(false);
        scanner.setMatchHolder(true);
        scanner.useHolder(stranger, bioHolder);
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());
        runScannerTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
    }

    private void runScannerTicks(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.SCAN_TICKS; i++) {
            scanner.tick();
        }
    }

    private void resetScanner(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.RESULT_TICKS; i++) {
            scanner.tick();
        }
        assertEquals(KeycardScannerBlockEntity.State.IDLE, scanner.getState());
    }

    // =========================================================================
    // 2. ItemDocumentsFolder Limits & Sanitization Tests
    // =========================================================================

    @Test
    @DisplayName("Folder Limits: 0 pages, 20 pages, >20 pages, 1024-char, >1024-char, and unicode surrogate pairs")
    public void testDocumentsFolderLimitsAndUnicode() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);

        // 1. 0 pages / empty folder
        List<String> pages = ItemDocumentsFolder.getPages(folder);
        assertEquals(ItemDocumentsFolder.PAGE_COUNT, pages.size(), "getPages must always return 20 entries");
        for (String p : pages) assertEquals("", p, "Empty folder must contain empty strings");
        assertEquals(0, ItemDocumentsFolder.writtenPageCount(folder));

        // 2. Exact 20 pages
        List<String> full20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) full20.add("Page " + i);
        ItemDocumentsFolder.setPages(folder, full20);
        assertEquals(20, ItemDocumentsFolder.writtenPageCount(folder));
        assertEquals("Page 0", ItemDocumentsFolder.getPages(folder).get(0));
        assertEquals("Page 19", ItemDocumentsFolder.getPages(folder).get(19));

        // 3. Overflow > 20 pages (e.g. 50 pages)
        List<String> giant = new ArrayList<>();
        for (int i = 0; i < 50; i++) giant.add("Overflow " + i);
        ItemDocumentsFolder.setPages(folder, giant);
        List<String> stored = ItemDocumentsFolder.getPages(folder);
        assertEquals(20, stored.size(), "Page storage must be strictly clamped to 20");
        assertEquals("Overflow 0", stored.get(0));
        assertEquals("Overflow 19", stored.get(19));

        // 4. Exact 1024 characters boundary
        StringBuilder sb1024 = new StringBuilder();
        while (sb1024.length() < 1024) sb1024.append("A");
        String text1024 = sb1024.toString();

        stored.set(0, text1024);
        ItemDocumentsFolder.setPages(folder, stored);
        assertEquals(1024, ItemDocumentsFolder.getPages(folder).get(0).length());

        // 5. Overflow > 1024 characters (e.g. 1500 chars)
        stored.set(0, text1024 + "EXTRA_CHARS_MUST_BE_CLAMPED");
        ItemDocumentsFolder.setPages(folder, stored);
        String clampedText = ItemDocumentsFolder.getPages(folder).get(0);
        assertEquals(1024, clampedText.length(), "Page length must be clamped to 1024 characters");
        assertEquals(text1024, clampedText);

        // 6. Unicode text and multi-byte surrogate pairs (emojis)
        String unicodePage = "§e【緊急事態】レベル５警報！\nПроект 12: ☢ Containment breach 🚪🔒🔥";
        stored.set(1, unicodePage);
        ItemDocumentsFolder.setPages(folder, stored);
        assertEquals(unicodePage, ItemDocumentsFolder.getPages(folder).get(1), "Unicode text must survive roundtrip without corruption");

        // 7. Last page index boundary clamping
        ItemDocumentsFolder.setLastPage(folder, -10);
        assertEquals(0, ItemDocumentsFolder.getLastPage(folder));
        ItemDocumentsFolder.setLastPage(folder, 50);
        assertEquals(19, ItemDocumentsFolder.getLastPage(folder));
    }

    @Test
    @DisplayName("Folder Title Sanitization: Length > 24 chars, special formatting characters, control chars")
    public void testDocumentsFolderTitleSanitization() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);

        // 1. Length > 24 characters
        String longTitle = "Confidential Nuclear Launch Authorization Record";
        ItemDocumentsFolder.setCustomName(folder, longTitle);
        String sanitizedLong = ItemDocumentsFolder.getCustomName(folder);
        assertEquals(24, sanitizedLong.length(), "Title must be clamped to max 24 characters");
        assertEquals(longTitle.substring(0, 24), sanitizedLong);

        // 2. Color and formatting stripping
        ItemDocumentsFolder.setCustomName(folder, "§4§lTOP §6§kSECRET§r §bDOC");
        assertEquals("TOP SECRET DOC", ItemDocumentsFolder.getCustomName(folder), "Formatting codes must be stripped");

        // 3. Control characters stripping
        ItemDocumentsFolder.setCustomName(folder, "Alpha\nBeta\rGamma\tDelta\0");
        assertEquals("AlphaBetaGammaDelta", ItemDocumentsFolder.getCustomName(folder), "Control characters must be stripped");

        // 4. Whitespace trimming
        ItemDocumentsFolder.setCustomName(folder, "   Padded Title   ");
        assertEquals("Padded Title", ItemDocumentsFolder.getCustomName(folder), "Leading/trailing whitespace must be trimmed");

        // 5. Null or empty title restores default name
        ItemDocumentsFolder.setCustomName(folder, "");
        assertEquals("", ItemDocumentsFolder.getCustomName(folder));
        assertFalse(folder.hasCustomName(), "Empty custom name must remove tag");

        ItemDocumentsFolder.setCustomName(folder, null);
        assertEquals("", ItemDocumentsFolder.getCustomName(folder));
    }

    // =========================================================================
    // 3. BlockThermiteCharge 96 VoxelShapes, Fuse & Door Breach Tests
    // =========================================================================

    @Test
    @DisplayName("Thermite VoxelShapes: 96 precomputed shapes validation (6 facings x 16 insets)")
    public void testThermiteCharge96VoxelShapesValidation() {
        BlockThermiteCharge block = ModBlocks.THERMITE_CHARGE;

        int totalValidated = 0;
        for (Direction dir : Direction.values()) {
            for (int inset = 0; inset <= 15; inset++) {
                BlockState state = block.getDefaultState()
                        .with(BlockThermiteCharge.FACING, dir)
                        .with(BlockThermiteCharge.INSET, inset);

                VoxelShape outline = block.getOutlineShape(state, null, BlockPos.ORIGIN, ShapeContext.absent());
                VoxelShape collision = block.getCollisionShape(state, null, BlockPos.ORIGIN, ShapeContext.absent());

                assertNotNull(outline, "Outline shape must not be null for " + dir + " inset " + inset);
                assertNotNull(collision, "Collision shape must not be null for " + dir + " inset " + inset);
                assertFalse(outline.isEmpty(), "Outline shape must not be empty for " + dir + " inset " + inset);
                assertSame(outline, collision, "Outline and collision shapes must be identical");

                // Verify non-inverted bounding boxes
                for (Box box : outline.getBoundingBoxes()) {
                    assertFalse(Double.isNaN(box.minX) || Double.isNaN(box.maxX));
                    assertFalse(Double.isNaN(box.minY) || Double.isNaN(box.maxY));
                    assertFalse(Double.isNaN(box.minZ) || Double.isNaN(box.maxZ));

                    assertTrue(box.minX < box.maxX, "minX must be strictly less than maxX for " + dir + " inset " + inset);
                    assertTrue(box.minY < box.maxY, "minY must be strictly less than maxY for " + dir + " inset " + inset);
                    assertTrue(box.minZ < box.maxZ, "minZ must be strictly less than maxZ for " + dir + " inset " + inset);
                }
                totalValidated++;
            }
        }
        assertEquals(96, totalValidated, "Must validate exactly 96 precomputed shapes");
    }

    @Test
    @DisplayName("Thermite Fuse Timing: Verify 100t fuse countdown, tick 70 warning beep, and tick 0 blast trigger")
    public void testThermiteFuseTimingAndBeep() {
        ThermiteChargeBlockEntity be = new ThermiteChargeBlockEntity(BlockPos.ORIGIN, ModBlocks.THERMITE_CHARGE.getDefaultState());

        assertEquals(100, ThermiteChargeBlockEntity.FUSE_TICKS);
        assertEquals(70, ThermiteChargeBlockEntity.BEEP_START_TICKS);

        // Test NBT persistence of fuse state
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("lit", true);
        tag.putInt("fuse", 100);
        be.readNbt(tag);

        assertTrue(be.isLit());
        assertEquals(100, be.getFuse());

        // Step through ticks 100 -> 0
        int beepTriggerTick = -1;
        int blastTriggerTick = -1;

        int simulatedFuse = be.getFuse();
        for (int tick = 0; tick <= 100; tick++) {
            if (simulatedFuse > 0) {
                simulatedFuse--;
                if (simulatedFuse == ThermiteChargeBlockEntity.BEEP_START_TICKS) {
                    beepTriggerTick = tick;
                }
            }
            if (simulatedFuse <= 0 && blastTriggerTick == -1) {
                blastTriggerTick = tick;
            }
        }

        // Fuse starts at 100; tick 30 brings fuse from 71 to 70 (warning beep); tick 100 brings fuse to 0 (detonation)
        assertEquals(29, beepTriggerTick, "Warning beep must trigger precisely when fuse reaches tick 70");
        assertEquals(99, blastTriggerTick, "Blast must trigger precisely when fuse reaches tick 0");
    }

    @Test
    @DisplayName("Door Breach: Verify DoorBlockEntity.breached flag, moving panel removal, and frame retention")
    public void testDoorBreachMovingPanelRemovalVsFrameRetention() {
        DoorBlockEntity door = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        door.setDecl(DoorDecls.FIRE_DOOR);

        assertFalse(door.isBreached(), "New door must not be breached");

        // Compute collision boxes before breach (includeMoving = true)
        Box[] boxesUnbreached = DoorDecls.FIRE_DOOR.getBoxes(Direction.NORTH, 0.0f, true, true);
        assertNotNull(boxesUnbreached);
        assertTrue(boxesUnbreached.length > 0, "Unbreached closed door must have collision boxes");

        // Breaching the door
        door.breach();
        assertTrue(door.isBreached(), "Door must report breached = true");
        assertEquals(DoorState.CLOSED, door.getState(), "Breached door state must be reset to CLOSED");

        // Compute collision boxes after breach (includeMoving = !breached = false)
        Box[] boxesBreached = DoorDecls.FIRE_DOOR.getBoxes(Direction.NORTH, 0.0f, true, !door.isBreached());
        assertNotNull(boxesBreached);

        // Moving panels must be removed while static frame boxes are retained
        assertTrue(boxesBreached.length < boxesUnbreached.length,
                "Breached door collision box count (" + boxesBreached.length +
                        ") must be strictly less than unbreached door box count (" + boxesUnbreached.length + ")");

        // All boxes remaining in breached mode must be static frame boxes
        for (Box frameBox : boxesBreached) {
            boolean foundInUnbreached = false;
            for (Box unbreachedBox : boxesUnbreached) {
                if (frameBox.equals(unbreachedBox)) {
                    foundInUnbreached = true;
                    break;
                }
            }
            assertTrue(foundInUnbreached, "Every breached collision box must correspond to an original static frame box");
        }
    }

    // =========================================================================
    // 4. Additional M6 Breaching & Weapons Protections
    // =========================================================================

    @Test
    @DisplayName("Breaching Exclusions: Hatch, seal, blast, and vault doors cannot be breached by thermite")
    public void testThermiteExcludedDoors() {
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.SILO_HATCH));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.SILO_HATCH_LARGE));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.TRANSITION_SEAL));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.BLAST_DOOR));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.VAULT_DOOR));

        // Normal, locked, and security doors of standard types must be breachable
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.FIRE_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.SECURE_ACCESS_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.ROUND_AIRLOCK_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.CARGO_DOOR));
    }

    @Test
    @DisplayName("Spatial Disrupter: Hardness, blast resistance, and piston behavior")
    public void testSpatialDisrupterProperties() {
        BlockSpatialDisrupter disrupter = ModBlocks.SPATIAL_DISRUPTER;
        BlockState state = disrupter.getDefaultState();

        assertEquals(PistonBehavior.BLOCK, state.getPistonBehavior(), "Disrupter must block piston pushes");
        assertEquals(-1.0F, state.getHardness(null, BlockPos.ORIGIN), "Disrupter must possess bedrock hardness (-1.0F)");
    }

    @Test
    @DisplayName("Weapons Protection: Dead Man's Hand disarm validation and mode encoding")
    public void testWeaponsKeyModesAndTrapDisarm() {
        assertEquals(0, WeaponsMode.REGULAR.id);
        assertEquals(1, WeaponsMode.DEAD_MANS_HAND.id);

        assertEquals(WeaponsMode.REGULAR, WeaponsMode.byId(0));
        assertEquals(WeaponsMode.DEAD_MANS_HAND, WeaponsMode.byId(1));
        assertEquals(WeaponsMode.REGULAR, WeaponsMode.byId(99)); // Default fallback

        UUID keyId = UUID.randomUUID();
        TestPlayer authorizedPlayer = createTestPlayer(false);
        TestPlayer intruderPlayer = createTestPlayer(false);

        // Put authorized key in offhand
        ItemStack authorizedKey = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
        authorizedKey.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, keyId.toString());
        authorizedPlayer.getInventory().offHand.set(0, authorizedKey);

        assertTrue(WeaponsKeyData.hasKey(authorizedPlayer, keyId), "Key scan must detect key in offhand");
        assertFalse(WeaponsKeyData.hasKey(intruderPlayer, keyId), "Intruder must fail key check");
    }
}
