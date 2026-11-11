package com.ntmdoors.test.security;

import com.ntmdoors.block.KeycardScannerBlock;
import com.ntmdoors.block.KeypadBlock;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorSecurityAccess;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.util.RedstoneBinding;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ChallengerM3NetworkingAndHardwareStressTest {

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

    private KeypadBlockEntity createKeypad() {
        KeypadBlock block = new KeypadBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new KeypadBlockEntity(null, BlockPos.ORIGIN, state);
    }

    private DoorBlockEntity createDoor(boolean security) {
        DoorBlockEntity door = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        door.setDecl(DoorDecls.VAULT_DOOR);
        door.setSecurity(security);
        return door;
    }

    // =========================================================================
    // 1. PacketByteBuf Limits & Fuzzing
    // =========================================================================

    @Test
    @DisplayName("Verify RelayKeyName packet limits (32 chars) and sanitization")
    public void testRelayKeyNameCodecLimitsAndMaliciousStrings() {
        // Valid length <= 32
        PacketByteBuf validBuf = PacketByteBufs.create();
        validBuf.writeString("A".repeat(32), 32);
        validBuf.writeEnumConstant(Hand.MAIN_HAND);
        assertEquals("A".repeat(32), validBuf.readString(32));
        assertEquals(Hand.MAIN_HAND, validBuf.readEnumConstant(Hand.class));

        // Oversized string > 32 must throw DecoderException
        PacketByteBuf overflowBuf = PacketByteBufs.create();
        overflowBuf.writeString("A".repeat(33)); // writes length 33
        assertThrows(DecoderException.class, () -> overflowBuf.readString(32),
                "Reading 33-char string with limit 32 must throw DecoderException");

        // Sanitization fuzzing
        assertEquals("Sector 7", PrivateKeys.sanitiseName("   Sector 7   "));
        assertEquals("clRedAlert", PrivateKeys.sanitiseName("\u00a7c\u00a7lRedAlert"));
        assertEquals("Line1Line2", PrivateKeys.sanitiseName("Line1\nLine2\0\r\t"));
        assertEquals("B".repeat(32), PrivateKeys.sanitiseName("B".repeat(50)));
        assertEquals("", PrivateKeys.sanitiseName(null));
        assertEquals("", PrivateKeys.sanitiseName(""));
        assertEquals("", PrivateKeys.sanitiseName("   \t\n   "));
    }

    @Test
    @DisplayName("Verify ScannerCode packet limits (6 chars) and numeric sanitization")
    public void testScannerCodeCodecLimitsAndSanitization() {
        // Valid code <= 6
        PacketByteBuf validBuf = PacketByteBufs.create();
        validBuf.writeBlockPos(new BlockPos(10, 20, 30));
        validBuf.writeString("123456", 6);
        assertEquals(new BlockPos(10, 20, 30), validBuf.readBlockPos());
        assertEquals("123456", validBuf.readString(6));

        // Oversized code > 6 must throw DecoderException
        PacketByteBuf overflowBuf = PacketByteBufs.create();
        overflowBuf.writeString("1234567");
        assertThrows(DecoderException.class, () -> overflowBuf.readString(6),
                "Reading 7-char string with limit 6 must throw DecoderException");

        // Sanitization fuzzing
        assertEquals("123456", Codes.sanitise("123456", 6));
        assertEquals("123456", Codes.sanitise("1234567890", 6));
        assertEquals("1234", Codes.sanitise("12ab!@#34", 6));
        assertEquals("55", Codes.sanitise("-55", 6));
        assertEquals("", Codes.sanitise("abcdef", 6));
        assertEquals("", Codes.sanitise(null, 6));
        assertEquals("", Codes.sanitise("", 6));
    }

    @Test
    @DisplayName("Verify ScannerKey packet limits (64 value, 32 name)")
    public void testScannerKeyCodecLimitsAndBiometrics() {
        String uuidStr = UUID.randomUUID().toString(); // 36 chars <= 64
        String nameStr = "Security Officer Vance"; // 22 chars <= 32

        PacketByteBuf validBuf = PacketByteBufs.create();
        validBuf.writeBlockPos(new BlockPos(-10, -20, -30));
        validBuf.writeString(uuidStr, 64);
        validBuf.writeBoolean(true);
        validBuf.writeBoolean(false);
        validBuf.writeString(nameStr, 32);

        assertEquals(new BlockPos(-10, -20, -30), validBuf.readBlockPos());
        assertEquals(uuidStr, validBuf.readString(64));
        assertTrue(validBuf.readBoolean());
        assertFalse(validBuf.readBoolean());
        assertEquals(nameStr, validBuf.readString(32));

        // Oversized value > 64 must throw DecoderException
        PacketByteBuf overflowValBuf = PacketByteBufs.create();
        overflowValBuf.writeString("V".repeat(65));
        assertThrows(DecoderException.class, () -> overflowValBuf.readString(64));

        // Oversized name > 32 must throw DecoderException
        PacketByteBuf overflowNameBuf = PacketByteBufs.create();
        overflowNameBuf.writeString("N".repeat(33));
        assertThrows(DecoderException.class, () -> overflowNameBuf.readString(32));
    }

    @Test
    @DisplayName("Verify ScannerOption packet boundary options (-128 to 127)")
    public void testScannerOptionCodecBoundaries() {
        byte[] testOptions = new byte[]{-128, -1, 0, 1, 7, 8, 16, 17, 127};
        for (byte opt : testOptions) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(BlockPos.ORIGIN);
            buf.writeByte(opt);
            buf.writeBoolean(true);

            assertEquals(BlockPos.ORIGIN, buf.readBlockPos());
            assertEquals(opt, buf.readByte());
            assertTrue(buf.readBoolean());
        }

        // Buffer underflow throws IndexOutOfBoundsException
        PacketByteBuf truncated = PacketByteBufs.create();
        truncated.writeByte(1);
        assertThrows(IndexOutOfBoundsException.class, truncated::readBlockPos);
    }

    @Test
    @DisplayName("Verify Keypad Code and Security Code packet limits (6 chars)")
    public void testKeypadPacketsCodecLimits() {
        PacketByteBuf codeBuf = PacketByteBufs.create();
        codeBuf.writeString("9999999"); // 7 chars
        assertThrows(DecoderException.class, () -> codeBuf.readString(6));

        PacketByteBuf secBuf = PacketByteBufs.create();
        secBuf.writeString("0000000"); // 7 chars
        assertThrows(DecoderException.class, () -> secBuf.readString(6));
    }

    // =========================================================================
    // 2. RedstoneBinding & BlockPositions Coordinate Serialization
    // =========================================================================

    @Test
    @DisplayName("Verify RedstoneBinding NBT roundtrip with negative coordinates and boundary coordinates")
    public void testRedstoneBindingNbtSerializationStress() {
        RedstoneBinding binding = new RedstoneBinding();
        binding.setOnFail(false);
        binding.setOnSuccess(true);

        List<BlockPos> testGates = List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(-100, -64, -200), // 1.20.1 minimum world height
                new BlockPos(30000000, 320, -30000000), // World boundary & maximum height
                new BlockPos(-29999999, 128, 29999999) // Negative world border
        );
        binding.getGates().addAll(testGates);

        NbtCompound tag = new NbtCompound();
        binding.writeNbt(tag);

        RedstoneBinding loaded = new RedstoneBinding();
        loaded.readNbt(tag);

        assertFalse(loaded.isOnFail());
        assertTrue(loaded.isOnSuccess());
        assertEquals(testGates.size(), loaded.getGates().size());
        for (int i = 0; i < testGates.size(); i++) {
            assertEquals(testGates.get(i), loaded.getGates().get(i));
        }
    }

    @Test
    @DisplayName("Verify RedstoneBinding corrupt NBT handling without throwing")
    public void testRedstoneBindingCorruptNbtHandling() {
        RedstoneBinding binding = new RedstoneBinding();

        // 1. Empty tag
        binding.readNbt(new NbtCompound());
        assertTrue(binding.getGates().isEmpty());
        assertTrue(binding.isOnFail()); // default
        assertTrue(binding.isOnSuccess()); // default

        // 2. Tag with wrong types (rsGates as string)
        NbtCompound wrongTypeTag = new NbtCompound();
        wrongTypeTag.putString("rsGates", "corrupt_data");
        binding.readNbt(wrongTypeTag);
        assertTrue(binding.getGates().isEmpty());

        // 3. Tag with list containing compounds missing 'pos'
        NbtCompound missingPosTag = new NbtCompound();
        NbtList list = new NbtList();
        list.add(new NbtCompound()); // empty compound without 'pos'
        missingPosTag.put("rsGates", list);
        binding.readNbt(missingPosTag);
        assertEquals(1, binding.getGates().size());
        assertEquals(new BlockPos(0, 0, 0), binding.getGates().get(0)); // defaults to pos 0L (0,0,0)
    }

    @Test
    @DisplayName("Verify BlockPositions encode/decode fuzzing and loop safety")
    public void testBlockPositionsEncodeDecodeFuzzing() {
        // Null / Empty
        assertEquals("", BlockPositions.encode(null));
        assertNull(BlockPositions.decode(null));
        assertNull(BlockPositions.decode(""));
        assertEquals("", BlockPositions.encodeList(List.of()));
        assertTrue(BlockPositions.decodeList(null).isEmpty());
        assertTrue(BlockPositions.decodeList("").isEmpty());

        // Malformed strings
        assertNull(BlockPositions.decode("1,2"));
        assertNull(BlockPositions.decode("1,2,3,4"));
        assertNull(BlockPositions.decode("a,b,c"));
        assertNull(BlockPositions.decode("99999999999999999999999999,0,0")); // integer overflow

        // Fuzzed list with redundant delimiters
        String fuzzed = ";;;1,2,3;;;4,5,-64;;;corrupt;;;7,8,9;;;";
        List<BlockPos> decoded = BlockPositions.decodeList(fuzzed);
        assertEquals(3, decoded.size());
        assertEquals(new BlockPos(1, 2, 3), decoded.get(0));
        assertEquals(new BlockPos(4, 5, -64), decoded.get(1));
        assertEquals(new BlockPos(7, 8, 9), decoded.get(2));

        // Roundtrip with multiple items
        List<BlockPos> original = List.of(
                new BlockPos(-500, -64, 1200),
                new BlockPos(0, 0, 0),
                new BlockPos(123456, 255, -654321)
        );
        String encoded = BlockPositions.encodeList(original);
        List<BlockPos> recovered = BlockPositions.decodeList(encoded);
        assertEquals(original, recovered);
    }

    // =========================================================================
    // 3. Door Interaction Hooks & Security Clearance Stress
    // =========================================================================

    @Test
    @DisplayName("Verify Keypad Code Sanitization, Stored Code and Bound Doors NBT")
    public void testKeypadNbtAndBoundarySerialization() {
        KeypadBlockEntity keypad = createKeypad();

        // Stored code accepts numeric <= 6
        keypad.setStoredCode("123456");
        assertEquals("123456", keypad.getStoredCode());
        assertTrue(keypad.hasStoredCode());
        assertEquals(KeypadBlockEntity.Mode.ENTER_CODE, keypad.getMode());

        // Security code sanitization
        keypad.setSecurityCode("987654321"); // longer than 6
        assertEquals("987654", keypad.getSecurityCode());

        // Bound doors NBT serialization
        NbtCompound tag = new NbtCompound();
        keypad.writeNbt(tag);

        KeypadBlockEntity loaded = createKeypad();
        loaded.readNbt(tag);

        assertEquals("123456", loaded.getStoredCode());
        assertEquals("987654", loaded.getSecurityCode());
    }

    @Test
    @DisplayName("Verify Scanner clearance, lockout alarm, and failure resetting")
    public void testKeycardScannerClearanceAndLockoutStress() {
        KeycardScannerBlockEntity scanner = createScanner();
        scanner.setLockoutMode(true);

        assertEquals(0, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());

        // Failure 1
        scanner.recordFailure();
        assertEquals(1, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());

        // Failure 2
        scanner.recordFailure();
        assertEquals(2, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());

        // Failure 3 -> Lockout triggered
        scanner.recordFailure();
        assertEquals(3, scanner.getFailedAttempts());
        assertTrue(scanner.isLockedOut());
        assertEquals(KeycardScannerBlockEntity.LOCKOUT_TICKS, scanner.getLockoutTicks());

        // Reset failures
        scanner.resetFailures();
        assertEquals(0, scanner.getFailedAttempts());
        assertFalse(scanner.isLockedOut());
        assertEquals(0, scanner.getLockoutTicks());

        // Disabling lockoutMode prevents locking out
        scanner.setLockoutMode(false);
        for (int i = 0; i < 10; i++) {
            scanner.recordFailure();
        }
        assertFalse(scanner.isLockedOut(), "Scanner must not lock out when lockoutMode is false");
    }

    @Test
    @DisplayName("Verify Door Security Access ownership and redstone gating")
    public void testDoorSecurityAccessRules() {
        DoorBlockEntity normalDoor = createDoor(false);
        DoorBlockEntity securityDoor = createDoor(true);

        // Normal door accepts redstone by default without owner
        assertTrue(normalDoor.acceptsRedstone());

        // Security door rejects redstone when unowned
        assertFalse(securityDoor.acceptsRedstone());

        // Set owner
        UUID owner = UUID.randomUUID();
        securityDoor.getSecurity().setOwner(owner, "Commander Shepard");
        assertEquals(owner, securityDoor.getSecurity().getOwnerId());
        assertEquals("Commander Shepard", securityDoor.getSecurity().getOwnerName());

        // Still rejects redstone until redstoneEnabled is turned on
        assertFalse(securityDoor.acceptsRedstone());

        // Turn redstone on
        securityDoor.getSecurity().setRedstoneEnabled(true);
        assertTrue(securityDoor.acceptsRedstone());

        // Turn redstone off
        securityDoor.getSecurity().setRedstoneEnabled(false);
        assertFalse(securityDoor.acceptsRedstone());

        // Test NBT persistence
        NbtCompound tag = new NbtCompound();
        securityDoor.getSecurity().setRedstoneEnabled(true);
        securityDoor.getSecurity().writeToNbt(tag, true);

        DoorBlockEntity loadedSecurityDoor = createDoor(true);
        loadedSecurityDoor.getSecurity().readFromNbt(tag, null, false);
        assertEquals(owner, loadedSecurityDoor.getSecurity().getOwnerId());
        assertEquals("Commander Shepard", loadedSecurityDoor.getSecurity().getOwnerName());
        assertTrue(loadedSecurityDoor.acceptsRedstone());
    }

    @Test
    @DisplayName("Verify Door acceptsKeypad and boundScanners lifecycle")
    public void testDoorAcceptsKeypadAndScannerConstraints() {
        DoorBlockEntity normalDoor = createDoor(false);
        DoorBlockEntity securityDoor = createDoor(true);

        // Normal door accepts keypad; Security door rejects keypad
        assertTrue(normalDoor.acceptsKeypad());
        assertFalse(securityDoor.acceptsKeypad());

        // Bound scanner list lifecycle
        assertFalse(normalDoor.isBoundToScanner());
        BlockPos scannerPos = new BlockPos(15, 64, 25);
        normalDoor.addBoundScanner(scannerPos);
        assertTrue(normalDoor.isBoundToScanner());
        assertTrue(normalDoor.isBoundToScanner(scannerPos));

        // Duplicate addition is ignored
        normalDoor.addBoundScanner(scannerPos);
        assertTrue(normalDoor.isBoundToScanner(scannerPos));

        // Removal
        normalDoor.removeBoundScanner(scannerPos);
        assertFalse(normalDoor.isBoundToScanner());

        // Auto close timer
        normalDoor.closeAfter(200);
        assertEquals(200, normalDoor.getAutoCloseTicks());
    }
}
