package com.ntmdoors.test.security;

import com.ntmdoors.item.ModItems;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PrivateKeysAndBiometricsTest {

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

    @Test
    @DisplayName("Verify 10-digit serial generation constraints")
    public void testSerialGeneration() {
        Random rand = new Random(12345);
        for (int i = 0; i < 50; i++) {
            String serial = PrivateKeys.createSerial(rand);
            assertNotNull(serial);
            assertEquals(10, serial.length(), "Serial must be exactly 10 digits");
            char first = serial.charAt(0);
            assertTrue(first >= '1' && first <= '9', "First digit must be 1-9");
            for (char c : serial.toCharArray()) {
                assertTrue(Character.isDigit(c), "Serial must contain only digits: " + serial);
            }
        }
    }

    @Test
    @DisplayName("Verify name sanitization: truncation at 32 chars and formatting/control stripping")
    public void testNameSanitization() {
        assertEquals("", PrivateKeys.sanitizeName(null));
        assertEquals("", PrivateKeys.sanitizeName(""));

        // Normal name
        assertEquals("Alpha", PrivateKeys.sanitizeName("Alpha"));

        // Strip section sign (§) color codes and control characters (< 32)
        String dirty = "\u00a7cRed\u00a7r \tKey\n\u0001";
        String clean = PrivateKeys.sanitizeName(dirty);
        assertFalse(clean.contains("\u00a7"), "Must strip section signs");
        assertFalse(clean.contains("\n"), "Must strip newlines");
        assertFalse(clean.contains("\t"), "Must strip tabs");

        // Truncate at 32 characters
        String longName = "1234567890123456789012345678901234567890";
        String truncated = PrivateKeys.sanitizeName(longName);
        assertEquals(32, truncated.length());
        assertEquals("12345678901234567890123456789012", truncated);
    }

    @Test
    @DisplayName("Verify PrivateKeys list operations: add, duplicate rejection, and NBT conversion")
    public void testPrivateKeysListOperations() {
        ItemStack stack = new ItemStack(ModItems.CARD_RFID_RED);

        // Initially empty
        assertTrue(PrivateKeys.getKeys(stack).isEmpty());
        assertFalse(PrivateKeys.hasSerial(stack, "1234567890"));

        // Add key 1
        PrivateKeys.Key key1 = new PrivateKeys.Key("Lab Alpha", "1234567890");
        PrivateKeys.add(stack, key1);
        assertEquals(1, PrivateKeys.getKeys(stack).size());
        assertTrue(PrivateKeys.hasSerial(stack, "1234567890"));
        assertTrue(PrivateKeys.hasKey(stack, key1));

        // Add duplicate serial - must not create duplicate entry
        PrivateKeys.Key keyDuplicate = new PrivateKeys.Key("Different Name", "1234567890");
        PrivateKeys.add(stack, keyDuplicate);
        assertEquals(1, PrivateKeys.getKeys(stack).size(), "Duplicate serial must not increase list size");

        // Add key 2
        PrivateKeys.Key key2 = new PrivateKeys.Key("Vault Beta", "9876543210");
        PrivateKeys.add(stack, key2);
        assertEquals(2, PrivateKeys.getKeys(stack).size());
        assertTrue(PrivateKeys.hasSerial(stack, "9876543210"));

        // Test addAll
        ItemStack stack2 = new ItemStack(ModItems.CARD_RFID_BLUE);
        List<PrivateKeys.Key> bulk = new ArrayList<>();
        bulk.add(new PrivateKeys.Key("One", "1111111111"));
        bulk.add(new PrivateKeys.Key("Two", "2222222222"));
        bulk.add(new PrivateKeys.Key("One Duplicate", "1111111111"));
        PrivateKeys.addAll(stack2, bulk);
        assertEquals(2, PrivateKeys.getKeys(stack2).size(), "addAll must deduplicate keys");

        // Test fromList / toList roundtrip
        NbtList nbtList = PrivateKeys.toList(bulk);
        List<PrivateKeys.Key> restored = PrivateKeys.fromList(nbtList);
        assertEquals(3, restored.size());
        assertEquals("1111111111", restored.get(0).serial);
        assertEquals("One", restored.get(0).name);
    }

    @Test
    @DisplayName("Verify Biometrics binding and immutability")
    public void testBiometricsBindingAndImmutability() {
        ItemStack stack = new ItemStack(ModItems.CARD_BIOMETRIC);
        assertFalse(Biometrics.hasOwner(stack));
        assertNull(Biometrics.getOwner(stack));
        assertEquals("", Biometrics.getOwnerName(stack));

        // Bind first owner
        UUID aliceId = UUID.randomUUID();
        Biometrics.bindIfNeeded(stack, aliceId, "Alice");
        assertTrue(Biometrics.hasOwner(stack));
        assertEquals(aliceId, Biometrics.getOwner(stack));
        assertEquals("Alice", Biometrics.getOwnerName(stack));

        // Attempt to rebind to Bob - MUST be immutable and ignored
        UUID bobId = UUID.randomUUID();
        Biometrics.bindIfNeeded(stack, bobId, "Bob");
        assertEquals(aliceId, Biometrics.getOwner(stack), "Owner UUID must remain Alice");
        assertEquals("Alice", Biometrics.getOwnerName(stack), "Owner Name must remain Alice");

        // Test matches
        assertTrue(Biometrics.matches(stack, aliceId));
        assertFalse(Biometrics.matches(stack, bobId));
    }

    @Test
    @DisplayName("Verify Biometrics copyOwner semantics")
    public void testBiometricsCopyOwner() {
        ItemStack source = new ItemStack(ModItems.CARD_BIOMETRIC);
        UUID charlieId = UUID.randomUUID();
        Biometrics.bindIfNeeded(source, charlieId, "Charlie");

        // Target unbound
        ItemStack target = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.copyOwner(source, target);
        assertTrue(Biometrics.hasOwner(target));
        assertEquals(charlieId, Biometrics.getOwner(target));
        assertEquals("Charlie", Biometrics.getOwnerName(target));

        // Attempt copy to already bound target
        ItemStack alreadyBound = new ItemStack(ModItems.CARD_BIOMETRIC);
        UUID daveId = UUID.randomUUID();
        Biometrics.bindIfNeeded(alreadyBound, daveId, "Dave");

        Biometrics.copyOwner(source, alreadyBound);
        assertEquals(daveId, Biometrics.getOwner(alreadyBound), "Target already bound must not be overwritten");
        assertEquals("Dave", Biometrics.getOwnerName(alreadyBound));
    }

    @Test
    @DisplayName("Verify Developer Keycard tooltip contains redacted text")
    public void testDeveloperKeycardRedacted() {
        ItemStack devCard = new ItemStack(ModItems.CARD_DEVELOPER);
        PrivateKeys.add(devCard, "ClassifiedSector", "1234567890");
        UUID agentId = UUID.randomUUID();
        Biometrics.bindIfNeeded(devCard, agentId, "SpecialAgent");

        List<Text> tooltip = new ArrayList<>();
        devCard.getItem().appendTooltip(devCard, null, tooltip, null);
        assertFalse(tooltip.isEmpty(), "Developer card should have tooltip");

        // Verify keys and biometrics are redacted as "Unknown", rather than revealing secret name/serial
        boolean hasUnknownKey = tooltip.stream().anyMatch(t -> t.getString().equals("Unknown"));
        boolean hasUnknownBio = tooltip.stream().anyMatch(t -> t.getString().contains("Biometric ID (Unknown)"));
        boolean hasSecretName = tooltip.stream().anyMatch(t -> t.getString().contains("ClassifiedSector"));
        boolean hasSecretSerial = tooltip.stream().anyMatch(t -> t.getString().contains("1234567890"));

        assertTrue(hasUnknownKey, "Developer card keys must be redacted as 'Unknown'");
        assertTrue(hasUnknownBio, "Developer card biometrics must be redacted as 'Biometric ID (Unknown)'");
        assertFalse(hasSecretName, "Developer card must not leak secret key name in tooltip");
        assertFalse(hasSecretSerial, "Developer card must not leak secret serial in tooltip");
    }

    @Test
    @DisplayName("Verify ModContent.PROGRAMMER_CARDS contains exactly 31 palette items")
    public void testProgrammerCardsPaletteCount() {
        assertNotNull(ModContent.PROGRAMMER_CARDS, "PROGRAMMER_CARDS must not be null");
        assertEquals(31, ModContent.PROGRAMMER_CARDS.size(), "PROGRAMMER_CARDS must contain exactly 31 palette items");
        for (int i = 0; i < ModContent.PROGRAMMER_CARDS.size(); i++) {
            assertNotNull(ModContent.PROGRAMMER_CARDS.get(i), "Palette card at index " + i + " must not be null");
        }
    }

    @Test
    @DisplayName("Verify Codes decimal sanitization")
    public void testCodesSanitization() {
        assertEquals("", Codes.sanitizeDecimal(null, 6));
        assertEquals("123456", Codes.sanitizeDecimal("123456789", 6));
        assertEquals("12345", Codes.sanitizeDecimal("12a3b4c5", 6));
        assertEquals("", Codes.sanitizeDecimal("abcdef", 6));
        assertEquals("987", Codes.sanitizeDecimal("987", 3));
    }
}
