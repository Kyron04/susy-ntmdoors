package com.ntmdoors.test.security;

import com.ntmdoors.block.KeycardProgrammerBlock;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.registry.ModContent;
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

public class KeycardProgrammerTest {

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

    private KeycardProgrammerBlockEntity createProgrammer() {
        KeycardProgrammerBlock block = new KeycardProgrammerBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeycardProgrammerBlockEntity(null, BlockPos.ORIGIN, state);
    }

    @Test
    @DisplayName("Verify Row 0 Key Binding: Relay Key to Operator Keycard")
    public void testRow0KeyBinding() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        // 1. Empty slots fail
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.EMPTY, programmer.getBindFailure(0));
        assertFalse(programmer.bind(0));

        // 2. Relay key with key
        ItemStack relayKey = new ItemStack(ModItems.RELAY_KEY);
        PrivateKeys.add(relayKey, "Lab", "1234567890");

        ItemStack operatorCard = new ItemStack(ModItems.CARD_OPERATOR);

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, relayKey);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR, operatorCard);

        assertEquals(KeycardProgrammerBlockEntity.BindFailure.NONE, programmer.getBindFailure(0));
        assertTrue(programmer.bind(0));

        // Operator card now has the key
        ItemStack resultOperator = programmer.getStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR);
        assertTrue(PrivateKeys.hasSerial(resultOperator, "1234567890"));
        assertEquals("Lab", PrivateKeys.getKeys(resultOperator).get(0).name);
    }

    @Test
    @DisplayName("Verify Row 1 Key Binding: Blank RFID Card Rejected, Valid RFID Accepted")
    public void testRow1KeyBinding() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        ItemStack relayKey = new ItemStack(ModItems.RELAY_KEY);
        PrivateKeys.add(relayKey, "Sector 7", "9876543210");
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_TWO, relayKey);

        // Blank RFID card must be rejected on Row 1
        ItemStack blankRfid = new ItemStack(ModItems.BLANK_RFID_CARD);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE, blankRfid);
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.BLANK_CARD, programmer.getBindFailure(1));
        assertFalse(programmer.bind(1));

        // Colored RFID card must be accepted
        ItemStack redCard = new ItemStack(ModItems.CARD_RFID_RED);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE, redCard);
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.NONE, programmer.getBindFailure(1));
        assertTrue(programmer.bind(1));

        ItemStack resultCard = programmer.getStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_ONE);
        assertTrue(PrivateKeys.hasSerial(resultCard, "9876543210"));
    }

    @Test
    @DisplayName("Verify Biometric transfer from Biometric Relay Key")
    public void testBiometricTransfer() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        UUID steveId = UUID.randomUUID();
        ItemStack bioRelay = new ItemStack(ModItems.BIOMETRIC_RELAY_KEY);
        Biometrics.bindIfNeeded(bioRelay, steveId, "Steve");

        ItemStack operatorCard = new ItemStack(ModItems.CARD_OPERATOR);

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, bioRelay);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR, operatorCard);

        assertEquals(KeycardProgrammerBlockEntity.BindFailure.NONE, programmer.getBindFailure(0));
        assertTrue(programmer.bind(0));

        ItemStack resultOperator = programmer.getStack(KeycardProgrammerBlockEntity.SLOT_OPERATOR);
        assertTrue(Biometrics.isBound(resultOperator));
        assertEquals(steveId.toString(), Biometrics.ownerUuid(resultOperator));
        assertEquals("Steve", Biometrics.ownerName(resultOperator));

        // Attempting to overwrite existing biometric identity must fail
        UUID alexId = UUID.randomUUID();
        ItemStack alexRelay = new ItemStack(ModItems.BIOMETRIC_RELAY_KEY);
        Biometrics.bindIfNeeded(alexRelay, alexId, "Alex");

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RELAY_ONE, alexRelay);
        assertEquals(KeycardProgrammerBlockEntity.BindFailure.BIOMETRIC_TAKEN, programmer.getBindFailure(0));
        assertFalse(programmer.bind(0));
    }

    @Test
    @DisplayName("Verify 31-Card Palette Recoloring strictly preserves 100% of NBT data")
    public void testPaletteRecoloringPreservesNbt() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        // Prepare source card with rich NBT
        ItemStack initialCard = new ItemStack(ModItems.CARD_RFID_RED);
        PrivateKeys.add(initialCard, "MasterKey", "5555555555");
        UUID ownerId = UUID.randomUUID();
        Biometrics.bindIfNeeded(initialCard, ownerId, "Commander");
        initialCard.getOrCreateNbt().putInt("CustomTag", 42);

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO, initialCard);

        // Iterate through all 31 cards in PROGRAMMER_CARDS palette
        for (int i = 0; i < ModContent.PROGRAMMER_CARDS.size(); i++) {
            ItemRfidCard targetCard = ModContent.PROGRAMMER_CARDS.get(i);
            assertTrue(programmer.applyColor(i), "Recoloring to index " + i + " must succeed");

            ItemStack current = programmer.getStack(KeycardProgrammerBlockEntity.SLOT_RFID_PAGE_TWO);
            assertSame(targetCard, current.getItem(), "Item type must match palette target at index " + i);

            // Verify NBT preservation
            assertTrue(current.hasNbt());
            assertTrue(PrivateKeys.hasSerial(current, "5555555555"), "Private keys must be preserved");
            assertEquals("MasterKey", PrivateKeys.getKeys(current).get(0).name);

            assertTrue(Biometrics.isBound(current), "Biometrics must be preserved");
            assertEquals(ownerId.toString(), Biometrics.ownerUuid(current));
            assertEquals("Commander", Biometrics.ownerName(current));

            assertEquals(42, current.getNbt().getInt("CustomTag"), "Custom NBT tags must be preserved");
        }
    }

    @Test
    @DisplayName("Verify Legacy Backup: 200 ticks duration, failure modes, and exact NBT cloning")
    public void testLegacyBackup() {
        KeycardProgrammerBlockEntity programmer = createProgrammer();

        // 1. Missing items
        assertEquals(KeycardProgrammerBlockEntity.BackupFailure.EMPTY, programmer.getBackupFailure());
        assertFalse(programmer.startBackup());

        // 2. Setup valid source and target
        ItemStack sourceCard = new ItemStack(ModItems.CARD_OPERATOR);
        PrivateKeys.add(sourceCard, "Core Vault", "8888888888");
        UUID chiefId = UUID.randomUUID();
        Biometrics.bindIfNeeded(sourceCard, chiefId, "Chief");
        sourceCard.getOrCreateNbt().putString("SecretCode", "XY-90");

        ItemStack targetCard = new ItemStack(ModItems.CARD_OPERATOR);

        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_LEFT, sourceCard);
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT, targetCard);

        assertEquals(KeycardProgrammerBlockEntity.BackupFailure.NONE, programmer.getBackupFailure());
        assertTrue(programmer.startBackup());
        assertTrue(programmer.isBackupActive());
        assertEquals(0, programmer.getBackupTicks());

        // Tick 199 times -> not complete yet
        for (int i = 0; i < KeycardProgrammerBlockEntity.BACKUP_TICKS_TOTAL - 1; i++) {
            programmer.tick();
            assertTrue(programmer.isBackupActive());
        }

        // Tick 200 -> completed
        programmer.tick();
        assertFalse(programmer.isBackupActive(), "Backup must finish at 200 ticks");
        assertTrue(programmer.isBackupSuccess(), "Backup success flag must be set");

        // Verify cloned target
        ItemStack cloned = programmer.getStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT);
        assertTrue(cloned.hasNbt());
        assertTrue(PrivateKeys.hasSerial(cloned, "8888888888"));
        assertEquals("Core Vault", PrivateKeys.getKeys(cloned).get(0).name);
        assertTrue(Biometrics.isBound(cloned));
        assertEquals(chiefId.toString(), Biometrics.ownerUuid(cloned));
        assertEquals("Chief", Biometrics.ownerName(cloned));
        assertEquals("XY-90", cloned.getNbt().getString("SecretCode"));

        // 3. Trying backup to non-empty target must fail with TARGET_FULL
        programmer.setStack(KeycardProgrammerBlockEntity.SLOT_LEGACY_RIGHT, cloned);
        assertEquals(KeycardProgrammerBlockEntity.BackupFailure.TARGET_FULL, programmer.getBackupFailure());
    }
}
