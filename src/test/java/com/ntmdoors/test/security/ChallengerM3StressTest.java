package com.ntmdoors.test.security;

import com.ntmdoors.block.KeycardProgrammerBlock;
import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.KeypadBlock;
import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemDeveloperKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone M3 (Security Hardware).
 * Stress-tests PIN lockouts, biometric checks, clearance matrices, corrupted NBT,
 * card programmer state boundaries, and distance constraints.
 */
public class ChallengerM3StressTest {

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
    }

    private KeypadBlockEntity createKeypad() {
        KeypadBlock block = new KeypadBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeypadBlockEntity(null, BlockPos.ORIGIN, state);
    }

    private KeycardScannerBlockEntity createScanner() {
        KeycardScannerBlock block = new KeycardScannerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeycardScannerBlockEntity(null, BlockPos.ORIGIN, state);
    }

    private KeycardProgrammerBlockEntity createProgrammer() {
        KeycardProgrammerBlock block = new KeycardProgrammerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeycardProgrammerBlockEntity(null, BlockPos.ORIGIN, state);
    }

    private void runScan(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.SCAN_TICKS; i++) {
            scanner.tick();
        }
    }

    private void resetScanner(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.RESULT_TICKS; i++) {
            scanner.tick();
        }
    }

    // =========================================================================
    // 1. PIN Lockouts & Passcode Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Challenge 1: Empty, null, non-numeric and boundary passcodes on Keypad and Scanner")
    public void testEmptyAndNullPasscodes() {
        KeypadBlockEntity keypad = createKeypad();

        // 1. Null / empty stored code
        keypad.setStoredCode(null);
        assertEquals("", keypad.getStoredCode());
        assertFalse(keypad.hasStoredCode());
        assertEquals(KeypadBlockEntity.Mode.SET_CODE, keypad.getMode());

        keypad.setStoredCode("");
        assertEquals("", keypad.getStoredCode());
        assertFalse(keypad.hasStoredCode());

        // 2. Pressing enter with empty entry during SET_CODE mode does not advance mode
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);
        assertEquals(KeypadBlockEntity.Mode.SET_CODE, keypad.getMode(), "Empty entry must not set code");
        assertEquals("", keypad.getStoredCode());

        // 3. Sanitization of non-numeric and mixed codes
        keypad.setStoredCode("abc!@#");
        assertEquals("", keypad.getStoredCode(), "All non-digits must sanitize to empty string");

        keypad.setStoredCode("1a2b3c4d5e6f");
        assertEquals("123456", keypad.getStoredCode(), "Mixed string must keep exactly first 6 digits");
        assertEquals(KeypadBlockEntity.Mode.ENTER_CODE, keypad.getMode());

        // 4. Overlong code truncation
        keypad.setStoredCode("1234567890123");
        assertEquals("123456", keypad.getStoredCode(), "Code must be truncated to 6 digits");

        // 5. Entering empty code in ENTER_CODE mode must result in DENIED
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);
        assertEquals(KeypadBlockEntity.Feedback.DENIED, keypad.getFeedback(), "Empty attempt must be DENIED");

        // 6. Security code on Keycard Scanner
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setSecurityCode(null);
        assertEquals("", scanner.getSecurityCode());
        scanner.setSecurityCode("secret999888");
        assertEquals("999888", scanner.getSecurityCode(), "Scanner security code must sanitize to 6 digits");
    }

    @Test
    @DisplayName("Challenge 2: Brute-force Lockout lifecycle: 3 strikes, immunity during lockout, and 6000 ticks timer")
    public void testPinBruteForceAndLockoutLifecycle() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setLockoutMode(true);
        scanner.assignKey("TopSecret", "9999999999");

        ItemStack wrongCard = new ItemStack(ModItems.CARD_RFID_RED);
        PrivateKeys.add(wrongCard, "Wrong", "1111111111");

        // Strike 1
        scanner.startScan(wrongCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(1, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());
        resetScanner(scanner);

        // Strike 2
        scanner.startScan(wrongCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(2, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());
        resetScanner(scanner);

        // Strike 3 -> LOCKOUT TRIGGERED
        scanner.startScan(wrongCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(3, scanner.getFailedAttempts());
        assertTrue(scanner.isLockedOut(), "Third failed attempt must trigger lockout");
        assertEquals(6000, scanner.getLockoutTicks(), "Lockout duration must be exactly 6000 ticks (5 minutes)");
        resetScanner(scanner);

        // Strike 4 during lockout: must deny immediately and NOT increase failedAttempts counter past 3
        scanner.startScan(wrongCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(3, scanner.getFailedAttempts(), "Failed attempts counter must not increment during active lockout");
        resetScanner(scanner);

        // Even presenting a valid card during lockout must be DENIED
        ItemStack validCard = new ItemStack(ModItems.CARD_RFID_GREEN);
        PrivateKeys.add(validCard, "TopSecret", "9999999999");
        scanner.startScan(validCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "Valid key must be denied while scanner is locked out");
        // Ticking down until lockout expires
        while (scanner.getLockoutTicks() > 1) {
            scanner.tick();
            assertTrue(scanner.isLockedOut(), "Scanner must remain locked out before timer reaches 0");
        }
        assertTrue(scanner.isLockedOut(), "Scanner must be locked out at tick 1");
        scanner.tick(); // final tick to 0
        assertFalse(scanner.isLockedOut(), "Lockout must expire after 6000 ticks");
        assertEquals(0, scanner.getFailedAttempts(), "Failed attempts must reset to 0 after lockout expiration");

        // Now valid card is GRANTED
        scanner.startScan(validCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Valid card must now be granted after lockout expiry");
    }

    @Test
    @DisplayName("Challenge 3: Lockout override permutations: Creative Keycard overrides lockout; Lockout disable clears state")
    public void testLockoutBypassPermutationMatrix() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setLockoutMode(true);
        scanner.recordFailure();
        scanner.recordFailure();
        scanner.recordFailure();
        assertTrue(scanner.isLockedOut());

        // Creative keycard overrides lockout
        ItemStack creative = new ItemStack(ModItems.CARD_CREATIVE);
        scanner.startScan(creative, ItemStack.EMPTY, true, UUID.randomUUID());
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Creative Keycard MUST override lockout");
        resetScanner(scanner);

        // Disabling lockoutMode resets failures and unlocks scanner
        scanner.setLockoutMode(false);
        assertFalse(scanner.isLockedOut(), "Disabling lockout mode must clear lockedOut state");
        assertEquals(0, scanner.getFailedAttempts(), "Disabling lockout mode must reset failures");
        assertEquals(0, scanner.getLockoutTicks());
    }

    // =========================================================================
    // 2. Corrupted NBT on Keycards, Relay Keys, and Block Entities
    // =========================================================================

    @Test
    @DisplayName("Challenge 4: Corrupted NBT tags on keycards and biometrics must never throw exceptions")
    public void testCorruptedNbtOnKeycardsAndRelayKeys() {
        // 1. PrivateKeys root is an integer instead of a List
        ItemStack corruptKeyType = new ItemStack(ModItems.CARD_RFID_RED);
        corruptKeyType.getOrCreateNbt().put(PrivateKeys.ROOT, NbtInt.of(9999));
        assertDoesNotThrow(() -> PrivateKeys.get(corruptKeyType));
        assertTrue(PrivateKeys.get(corruptKeyType).isEmpty());
        assertTrue(PrivateKeys.isBlank(corruptKeyType));
        assertEquals(0, PrivateKeys.count(corruptKeyType));

        // 2. PrivateKeys list containing malformed compound elements (empty compounds or missing serial)
        ItemStack corruptElements = new ItemStack(ModItems.CARD_RFID_BLUE);
        NbtList list = new NbtList();
        NbtCompound emptyCompound = new NbtCompound();
        list.add(emptyCompound);
        NbtCompound missingSerial = new NbtCompound();
        missingSerial.putString("Name", "NoSerialKey");
        list.add(missingSerial);
        corruptElements.getOrCreateNbt().put(PrivateKeys.ROOT, list);

        assertDoesNotThrow(() -> PrivateKeys.get(corruptElements));
        assertTrue(PrivateKeys.get(corruptElements).isEmpty(), "Malformed elements must be safely skipped");

        // 3. PrivateKeys list containing wrong tag type (e.g. NbtList of strings)
        ItemStack stringListStack = new ItemStack(ModItems.CARD_RFID_GREEN);
        NbtList stringList = new NbtList();
        stringList.add(NbtString.of("malicious_string_entry"));
        stringListStack.getOrCreateNbt().put(PrivateKeys.ROOT, stringList);
        assertDoesNotThrow(() -> PrivateKeys.get(stringListStack));
        assertTrue(PrivateKeys.get(stringListStack).isEmpty(), "String list must be safely ignored");

        // 3. Biometrics root is an int instead of a Compound
        ItemStack corruptBioType = new ItemStack(ModItems.CARD_BIOMETRIC);
        corruptBioType.getOrCreateNbt().put(Biometrics.ROOT, NbtInt.of(12345));
        assertDoesNotThrow(() -> Biometrics.isBound(corruptBioType));
        assertFalse(Biometrics.isBound(corruptBioType));
        assertEquals("", Biometrics.ownerUuid(corruptBioType));
        assertEquals("", Biometrics.ownerName(corruptBioType));

        // 4. Biometrics with non-UUID arbitrary string
        ItemStack nonUuidBio = new ItemStack(ModItems.CARD_BIOMETRIC);
        NbtCompound bioComp = new NbtCompound();
        bioComp.putString("Owner", "totally_not_a_valid_uuid_string_!@#$%");
        bioComp.putString("OwnerName", "Hacker");
        nonUuidBio.getOrCreateNbt().put(Biometrics.ROOT, bioComp);

        assertTrue(Biometrics.isBound(nonUuidBio));
        assertEquals("totally_not_a_valid_uuid_string_!@#$%", Biometrics.ownerUuid(nonUuidBio));

        // Scanner evaluation with malformed biometric string must safely deny without crashing
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setMatchHolder(true);
        scanner.assignId(UUID.randomUUID().toString(), "Admin");
        assertDoesNotThrow(() -> {
            scanner.startScan(nonUuidBio, ItemStack.EMPTY, false, UUID.randomUUID());
            runScan(scanner);
        });
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
    }

    @Test
    @DisplayName("Challenge 5: Scanner and Keypad NBT roundtrip resilience against negative / extreme values")
    public void testExtremeAndCorruptedNbtOnScannerAndKeypad() {
        KeycardScannerBlockEntity scanner = createScanner();
        NbtCompound tag = new NbtCompound();

        // Write normal NBT
        scanner.writeNbt(tag);

        // Inject extreme / corrupt values
        tag.putInt("ticks", -100);
        tag.putInt("lockoutTicks", -50);
        tag.putInt("failedAttempts", -5);
        tag.putString("securityCode", "non_numeric_text");

        assertDoesNotThrow(() -> scanner.readNbt(tag));
        assertEquals("non_numeric_text", scanner.getSecurityCode(), "Direct NBT loads stored string faithfully");

        // Keypad NBT resilience
        KeypadBlockEntity keypad = createKeypad();
        NbtCompound padTag = new NbtCompound();
        keypad.writeNbt(padTag);
        padTag.putString("storedCode", "987654321");
        padTag.putString("securityCode", "invalid_sec");

        assertDoesNotThrow(() -> keypad.readNbt(padTag));
        assertEquals("987654321", keypad.getStoredCode());
        assertEquals("invalid_sec", keypad.getSecurityCode());
    }

    // =========================================================================
    // 3. Exhaustive Keycard Clearance Matrix & Dual-Wield Permutations
    // =========================================================================

    @Test
    @DisplayName("Challenge 6: Exhaustive Clearance Matrix: Standard (7), Tarkov (5), SCP (7), Level (12) cards")
    public void testExhaustiveKeycardClearanceMatrix() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.assignKey("CommandDeck", "4444444444");

        List<ItemRfidCard> testCards = new ArrayList<>();
        // 7 Base colors
        testCards.add(ModItems.RED_RFID_CARD);
        testCards.add(ModItems.VIOLET_RFID_CARD);
        testCards.add(ModItems.GREEN_RFID_CARD);
        testCards.add(ModItems.BLUE_RFID_CARD);
        testCards.add(ModItems.BLACK_RFID_CARD);
        testCards.add(ModItems.ORANGE_RFID_CARD);
        testCards.add(ModItems.YELLOW_RFID_CARD);

        // 5 Tarkov cards
        testCards.add(ModItems.BLUE_MARKING_CARD);
        testCards.add(ModItems.LABYRINTH_KEYCARD);
        testCards.add(ModItems.ENGINE_ROOM_KEYCARD);
        testCards.add(ModItems.TERMINAL_KEYCARD);
        testCards.add(ModItems.TERRAGROUP_LABS_KEYCARD);

        // 7 SCP & Joke cards
        testCards.add(ModItems.SCP_LEVEL_0_KEYCARD);
        testCards.add(ModItems.SCP_LEVEL_1_KEYCARD);
        testCards.add(ModItems.SCP_LEVEL_2_KEYCARD);
        testCards.add(ModItems.SCP_LEVEL_3_KEYCARD);
        testCards.add(ModItems.SCP_LEVEL_4_KEYCARD);
        testCards.add(ModItems.SCP_LEVEL_5_KEYCARD);
        testCards.add(ModItems.GOOGA_SECURITY_KEYCARD);

        // 12 Numbered Level cards
        testCards.add(ModItems.LEVEL_1_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_2_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_3_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_4_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_5_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_6_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_7_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_8_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_9_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_10_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_11_ACCESS_KEYCARD);
        testCards.add(ModItems.LEVEL_12_ACCESS_KEYCARD);

        assertEquals(31, testCards.size());

        for (ItemRfidCard cardItem : testCards) {
            // A. Blank/Unassigned state: MUST be DENIED
            ItemStack unassigned = new ItemStack(cardItem);
            scanner.startScan(unassigned, ItemStack.EMPTY, false, UUID.randomUUID());
            runScan(scanner);
            assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(),
                    "Unassigned " + cardItem.getTranslationKey() + " must be DENIED");
            resetScanner(scanner);

            // B. Programmed with assigned key: MUST be GRANTED
            ItemStack programmed = new ItemStack(cardItem);
            PrivateKeys.add(programmed, "CommandDeck", "4444444444");
            scanner.startScan(programmed, ItemStack.EMPTY, false, UUID.randomUUID());
            runScan(scanner);
            assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(),
                    "Programmed " + cardItem.getTranslationKey() + " must be GRANTED");
            resetScanner(scanner);
        }
    }

    @Test
    @DisplayName("Challenge 7: Dual-wielding, Biometric holder matching, and clearance flags matrix")
    public void testDualWieldClearancePermutations() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.assignKey("SectorAlpha", "1111111111");
        scanner.assignKey("SectorBeta", "2222222222");

        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        ItemStack bobsBioCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(bobsBioCard, bob, "Bob");
        PrivateKeys.add(bobsBioCard, "SectorAlpha", "1111111111");

        ItemStack alicesBioCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(alicesBioCard, alice, "Alice");
        PrivateKeys.add(alicesBioCard, "SectorBeta", "2222222222");

        // 1. matchHolder = true: Alice scans Bob's card in main hand -> DENIED
        scanner.setMatchHolder(true);
        scanner.startScan(bobsBioCard, ItemStack.EMPTY, false, alice);
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "Alice scanning Bob's card must be DENIED");
        resetScanner(scanner);

        // 2. matchHolder = true: Alice scans Bob's card in main hand, but has her own valid card in off hand -> GRANTED
        scanner.startScan(bobsBioCard, alicesBioCard, false, alice);
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Valid off-hand card must grant access even if main hand is rejected");
        resetScanner(scanner);

        // 3. matchHolder = false: Alice scanning Bob's card -> GRANTED
        scanner.setMatchHolder(false);
        scanner.startScan(bobsBioCard, ItemStack.EMPTY, false, alice);
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "When matchHolder is false, biometric card with valid key must be GRANTED");
        resetScanner(scanner);

        // 4. ignoreKeys = true and ignoreIds = true: Neither keys nor IDs work -> only Creative card works
        scanner.setIgnoreKeys(true);
        scanner.setIgnoreIds(true);
        scanner.startScan(alicesBioCard, ItemStack.EMPTY, false, alice);
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "When both keys and IDs are ignored, access must be DENIED");
        resetScanner(scanner);

        ItemStack creative = new ItemStack(ModItems.CARD_CREATIVE);
        scanner.startScan(creative, ItemStack.EMPTY, true, alice);
        runScan(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Creative card overrides even when keys and IDs are ignored");
        resetScanner(scanner);
    }

    // =========================================================================
    // 4. Card Programmer States & Boundary Conditions
    // =========================================================================

    @Test
    @DisplayName("Challenge 8: Card Programmer slot rules, invalid source/target rejections, and NBT preservation")
    public void testCardProgrammerExhaustiveBoundaryConditions() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        // 1. Non-relay key source rejected
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, new ItemStack(Items.DIAMOND));
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR, new ItemStack(ModItems.CARD_OPERATOR));
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.NO_KEY, programmer.getBindFailure(0));
        assertFalse(programmer.bind(0));

        // 2. Blank relay key rejected (no key generated yet)
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, new ItemStack(ModItems.RELAY_KEY));
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.NO_KEY, programmer.getBindFailure(0));

        // 3. Valid relay key to invalid target (e.g. normal RFID card on Row 0)
        ItemStack validRelay = new ItemStack(ModItems.RELAY_KEY);
        PrivateKeys.add(validRelay, "ServerRoom", "7777777777");
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, validRelay);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR, new ItemStack(ModItems.CARD_RFID_RED));
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.INVALID_TARGET, programmer.getBindFailure(0));

        // 4. Row 1: Target cannot be BLANK_RFID_CARD
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_TWO, validRelay);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE, new ItemStack(ModItems.BLANK_RFID_CARD));
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.BLANK_CARD, programmer.getBindFailure(1));

        // 5. Palette recoloring boundary indices
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO, new ItemStack(ModItems.CARD_RFID_RED));
        assertFalse(programmer.applyColor(-1));
        assertFalse(programmer.applyColor(31));
        assertFalse(programmer.applyColor(999));

        // 6. Cannot recolor Operator Keycard or Biometric Keycard
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO, new ItemStack(ModItems.CARD_OPERATOR));
        assertFalse(programmer.applyColor(0));
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO, new ItemStack(ModItems.CARD_BIOMETRIC));
        assertFalse(programmer.applyColor(0));
    }

    @Test
    @DisplayName("Challenge 9: Card Programmer Backup mid-stream cancellation and strict target blankness")
    public void testCardProgrammerBackupCancellationAndEdgeCases() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        ItemStack source = new ItemStack(ModItems.CARD_OPERATOR);
        PrivateKeys.add(source, "SecretVault", "3333333333");
        ItemStack target = new ItemStack(ModItems.CARD_OPERATOR);

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT, source);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT, target);

        assertTrue(programmer.startBackup());
        assertTrue(programmer.isBackupActive());

        // Tick 50 times
        for (int i = 0; i < 50; i++) {
            programmer.tick();
        }
        assertEquals(50, programmer.getBackupTicks());

        // Player removes source card mid-backup -> MUST abort backup immediately
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT, ItemStack.EMPTY);
        programmer.tick();
        assertFalse(programmer.isBackupActive(), "Backup must abort immediately when source is removed");
        assertEquals(0, programmer.getBackupTicks());
        assertFalse(programmer.isBackupSuccess());

        // Target already containing a key cannot be used as backup destination
        ItemStack dirtyTarget = new ItemStack(ModItems.CARD_OPERATOR);
        PrivateKeys.add(dirtyTarget, "Existing", "1111111111");
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT, source);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT, dirtyTarget);
        assertEquals(KeycardProgrammerBlockEntity.BackupFailure.TARGET_FULL, programmer.getBackupFailure());
        assertFalse(programmer.startBackup());
    }

    // =========================================================================
    // 5. High-Volume Random Serial Generation & Distance Invariants
    // =========================================================================

    @Test
    @DisplayName("Challenge 10: 10,000 PrivateKey serial generations: uniqueness, length, and non-zero leading digit")
    public void testRelayKeySerialAndNameSanitizationStress() {
        Random rand = new Random(42);
        java.util.Set<String> serials = new java.util.HashSet<>();

        for (int i = 0; i < 10000; i++) {
            String s = PrivateKeys.createSerial(rand);
            assertEquals(10, s.length(), "Serial must be 10 characters");
            char c0 = s.charAt(0);
            assertTrue(c0 >= '1' && c0 <= '9', "Leading digit must be 1-9: " + s);
            for (char ch : s.toCharArray()) {
                assertTrue(Character.isDigit(ch), "Every char must be digit: " + s);
            }
            serials.add(s);
        }

        // Check high uniqueness entropy (collision probability in 10,000 samples of 9*10^9 space is negligible)
        assertEquals(10000, serials.size(), "10,000 serials should have zero collisions");

        // Extreme name sanitization: section signs are stripped, letters are kept
        assertEquals("abcd", PrivateKeys.sanitizeName("\u00a7a\u00a7b\u00a7c\u00a7d"));
        String hugeString = "A".repeat(1000);
        assertEquals(32, PrivateKeys.sanitizeName(hugeString).length());
    }

    public static class DummyPlayer extends net.minecraft.entity.player.PlayerEntity {
        public DummyPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Dummy"));
        }
        @Override
        public boolean isSpectator() { return false; }
        @Override
        public boolean isCreative() { return false; }
        @Override
        public void sendMessage(Text message, boolean overlay) {}
    }

    private net.minecraft.entity.player.PlayerEntity createDummyPlayer() {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            return (net.minecraft.entity.player.PlayerEntity) unsafe.allocateInstance(DummyPlayer.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DoorBlockEntity createDoor(BlockPos pos) {
        DoorBlockEntity door = new DoorBlockEntity(null, pos, null);
        door.setDecl(com.ntmdoors.door.DoorDecls.SLIDING_BLAST_DOOR);
        door.setSecurity(false);
        return door;
    }

    @Test
    @DisplayName("Challenge 11: Keypad multi-door limits (9 slots max) and distance bounding (32 blocks)")
    public void testKeypadMultiDoorLimitsAndDistanceBoundaries() {
        KeypadBlockEntity keypad = createKeypad();
        keypad.cycleBindMode(null); // DISTANCE
        keypad.cycleBindMode(null); // MULTI
        assertEquals(KeypadBlockEntity.BindMode.MULTI, keypad.getBindMode());

        net.minecraft.entity.player.PlayerEntity player = createDummyPlayer();

        // 1. Attempt to bind 10 doors (limit is 9)
        List<DoorBlockEntity> doors = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            doors.add(createDoor(new BlockPos(i, 0, 0)));
        }

        for (int i = 0; i < 9; i++) {
            keypad.bindDoor(doors.get(i), player);
        }
        assertEquals(9, keypad.getDoorCount(), "Keypad must accept up to 9 doors in MULTI mode");

        // 10th door must be rejected
        keypad.bindDoor(doors.get(9), player);
        assertEquals(9, keypad.getDoorCount(), "10th door must be rejected due to MULTI_SLOTS limit");

        // Toggling an existing door removes it
        keypad.bindDoor(doors.get(0), player);
        assertEquals(8, keypad.getDoorCount(), "Re-binding existing door in MULTI mode must toggle it off");

        // 2. Door beyond MAX_BIND_DISTANCE (32 blocks)
        DoorBlockEntity farDoor = createDoor(new BlockPos(100, 0, 0));
        keypad.bindDoor(farDoor, player);
        assertEquals(8, keypad.getDoorCount(), "Door farther than 32 blocks must be rejected");
    }
}
