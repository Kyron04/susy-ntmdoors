package com.ntmdoors.test.security;

import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class KeycardScannerClearanceTest {

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

    private KeycardScannerBlockEntity createScanner() {
        KeycardScannerBlock block = new KeycardScannerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeycardScannerBlockEntity(null, BlockPos.ORIGIN, state);
    }

    private void runScanTicks(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.SCAN_TICKS; i++) {
            scanner.tick();
        }
    }

    private void resetToIdle(KeycardScannerBlockEntity scanner) {
        for (int i = 0; i < KeycardScannerBlockEntity.RESULT_TICKS; i++) {
            scanner.tick();
        }
        assertEquals(KeycardScannerBlockEntity.State.IDLE, scanner.getState());
    }

    @Test
    @DisplayName("Verify Creative Keycard override always grants access even in lockdown/lockout")
    public void testCreativeCardOverride() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setLockdown(true);
        scanner.setEnabled(false);

        // Creative card presented
        ItemStack creativeCard = new ItemStack(ModItems.CARD_CREATIVE);
        scanner.startScan(creativeCard, ItemStack.EMPTY, true, UUID.randomUUID());
        assertEquals(KeycardScannerBlockEntity.State.SCANNING, scanner.getState());

        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Creative card must override all restrictions");
    }

    @Test
    @DisplayName("Verify system lockdown and disabled states block standard cards")
    public void testSystemLockdownAndDisabled() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.assignKey("Armory", "1234567890");

        ItemStack validCard = new ItemStack(ModItems.CARD_RFID_RED);
        PrivateKeys.add(validCard, "Armory", "1234567890");

        // 1. Lockdown enabled
        scanner.setLockdown(true);
        scanner.startScan(validCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "Lockdown must deny access");

        resetToIdle(scanner);
        scanner.setLockdown(false);

        // 2. Disabled
        scanner.setEnabled(false);
        scanner.startScan(validCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "Disabled scanner must deny access");
    }

    @Test
    @DisplayName("Verify Biometric matchHolder rejection: non-owner biometric card rejected even with matching key")
    public void testBiometricMatchHolderEnforcement() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setMatchHolder(true);
        scanner.assignKey("Vault", "9998887776");

        UUID bobId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();

        // Card bound to Bob, carrying valid key
        ItemStack bobsCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(bobsCard, bobId, "Bob");
        PrivateKeys.add(bobsCard, "Vault", "9998887776");

        // Alice scans Bob's card
        scanner.startScan(bobsCard, ItemStack.EMPTY, false, aliceId);
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState(), "Non-matching biometric holder must reject card");

        resetToIdle(scanner);

        // Bob scans his own card
        scanner.startScan(bobsCard, ItemStack.EMPTY, false, bobId);
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Matching biometric holder must be granted access");
    }

    @Test
    @DisplayName("Verify Private Key clearance evaluation")
    public void testPrivateKeyClearance() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.assignKey("Control Room", "5555555555");

        // Valid card
        ItemStack validCard = new ItemStack(ModItems.CARD_RFID_GREEN);
        PrivateKeys.add(validCard, "Control Room", "5555555555");
        scanner.startScan(validCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState());

        resetToIdle(scanner);

        // Invalid card
        ItemStack invalidCard = new ItemStack(ModItems.CARD_RFID_BLUE);
        PrivateKeys.add(invalidCard, "Cafeteria", "1111111111");
        scanner.startScan(invalidCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
    }

    @Test
    @DisplayName("Verify Biometric ID clearance evaluation")
    public void testBiometricIdClearance() {
        KeycardScannerBlockEntity scanner = createScanner();
        UUID commanderId = UUID.randomUUID();
        scanner.assignId(commanderId.toString(), "Commander");

        // Card with assigned Commander biometric identity
        ItemStack commanderCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(commanderCard, commanderId, "Commander");

        scanner.startScan(commanderCard, ItemStack.EMPTY, false, commanderId);
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState());

        resetToIdle(scanner);

        // Card with unassigned biometric identity
        UUID gruntId = UUID.randomUUID();
        ItemStack gruntCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(gruntCard, gruntId, "Grunt");

        scanner.startScan(gruntCard, ItemStack.EMPTY, false, gruntId);
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
    }

    @Test
    @DisplayName("Verify 3 consecutive failures trigger 5-minute (6000 ticks) lockout")
    public void testThreeFailuresTriggerLockout() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setLockoutMode(true);
        assertEquals(0, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());

        ItemStack emptyCard = new ItemStack(ModItems.CARD_RFID_WHITE);

        // Attempt 1
        scanner.startScan(emptyCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(1, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());
        resetToIdle(scanner);

        // Attempt 2
        scanner.startScan(emptyCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(2, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());
        resetToIdle(scanner);

        // Attempt 3 -> triggers Lockout
        scanner.startScan(emptyCard, ItemStack.EMPTY, false, UUID.randomUUID());
        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.DENIED, scanner.getState());
        assertEquals(3, scanner.getFailedAttempts());
        assertTrue(scanner.isLockedOut(), "3 failures must trigger lockout");
        assertEquals(KeycardScannerBlockEntity.LOCKOUT_TICKS, scanner.getLockoutTicks(), "Lockout duration must be 6000 ticks");

        // Tick down lockout
        for (int i = 0; i < KeycardScannerBlockEntity.LOCKOUT_TICKS; i++) {
            scanner.tick();
        }
        assertFalse(scanner.isLockedOut(), "Lockout should expire after 6000 ticks");
        assertEquals(0, scanner.getLockoutTicks());
        assertEquals(0, scanner.getFailedAttempts());
    }

    @Test
    @DisplayName("Verify card snapshot preservation during 20-tick scan")
    public void testCardSnapshotPreservation() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.assignKey("HQ", "7777777777");

        ItemStack mutableCard = new ItemStack(ModItems.CARD_RFID_YELLOW);
        PrivateKeys.add(mutableCard, "HQ", "7777777777");

        scanner.startScan(mutableCard, ItemStack.EMPTY, false, UUID.randomUUID());

        // Alter original stack mid-scan
        mutableCard.setCount(0);

        runScanTicks(scanner);
        assertEquals(KeycardScannerBlockEntity.State.GRANTED, scanner.getState(), "Snapshot must isolate scan from item stack mutations");
    }
}
