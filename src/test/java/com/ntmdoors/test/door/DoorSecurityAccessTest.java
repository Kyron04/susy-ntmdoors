package com.ntmdoors.test.door;

import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorSecurityAccess;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import net.minecraft.Bootstrap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DoorSecurityAccessTest {

    @BeforeAll
    public static void setup() {
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    private DoorBlockEntity createSecurityDoor(DoorDecl decl) {
        DoorBlockEntity be = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        be.setDecl(decl);
        be.setSecurity(true);
        return be;
    }

    private DoorBlockEntity createNormalDoor(DoorDecl decl) {
        DoorBlockEntity be = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        be.setDecl(decl);
        be.setSecurity(false);
        return be;
    }

    @Test
    @DisplayName("Verify Security Door Ownership Initialization and Non-Overwritability")
    public void testSecurityDoorOwnership() {
        DoorBlockEntity secDoor = createSecurityDoor(DoorDecls.SECURE_ACCESS_DOOR);
        DoorSecurityAccess access = secDoor.getSecurity();

        assertNull(access.getOwnerId());
        assertEquals("", access.getOwnerName());

        UUID ownerId = UUID.randomUUID();
        access.setOwner(ownerId, "Commander");
        assertEquals(ownerId, access.getOwnerId());
        assertEquals("Commander", access.getOwnerName());

        // Attempting to overwrite existing owner must be ignored
        UUID impostorId = UUID.randomUUID();
        access.setOwner(impostorId, "Impostor");
        assertEquals(ownerId, access.getOwnerId(), "Existing owner must not be overwritten");
        assertEquals("Commander", access.getOwnerName());
    }

    @Test
    @DisplayName("Verify Normal Doors Do Not Accept Security Ownership")
    public void testNormalDoorDoesNotAcceptSecurityOwnership() {
        DoorBlockEntity normDoor = createNormalDoor(DoorDecls.FIRE_DOOR);
        DoorSecurityAccess access = normDoor.getSecurity();

        // Normal door accepts redstone by default
        assertTrue(normDoor.acceptsRedstone(), "Normal doors must accept redstone by default");

        // Setting owner on normal door via player call ignores ownership
        access.setOwner(null, null, false);
        assertNull(access.getOwnerId());
    }

    @Test
    @DisplayName("Verify Security Door Redstone Access Toggle and Opt-in Redstone")
    public void testSecurityDoorRedstoneToggle() {
        DoorBlockEntity secDoor = createSecurityDoor(DoorDecls.SECURE_ACCESS_DOOR);
        DoorSecurityAccess access = secDoor.getSecurity();

        // Security door without owner does NOT accept redstone
        assertFalse(secDoor.acceptsRedstone(), "Security door must not accept redstone by default");
        assertFalse(access.isRedstoneEnabled());

        UUID ownerId = UUID.randomUUID();
        access.setOwner(ownerId, "Officer");

        // Still does not accept redstone until owner explicitly enables it
        assertFalse(secDoor.acceptsRedstone(), "Owned security door must not accept redstone until opted in");

        // Owner enables redstone access
        access.setRedstoneEnabled(true);
        assertTrue(access.isRedstoneEnabled());
        assertTrue(secDoor.acceptsRedstone(), "Security door must accept redstone when enabled by owner");

        // Disable redstone access
        access.setRedstoneEnabled(false);
        assertFalse(access.isRedstoneEnabled());
        assertFalse(secDoor.acceptsRedstone());
    }

    @Test
    @DisplayName("Verify Network Sync NBT Sanitization: Security and Passwords Never Leaked to Client")
    public void testSyncNbtSanitization() {
        DoorBlockEntity door = createSecurityDoor(DoorDecls.VAULT_DOOR);
        door.getSecurity().setOwner(UUID.randomUUID(), "SuperAdmin");
        door.getSecurity().setRedstoneEnabled(true);
        door.bindKeypadCode("SUPER_SECRET_PIN_9988");
        door.setScannerCode("CONFIDENTIAL_BIOMETRIC_TOKEN");

        // Full server-side NBT save
        NbtCompound fullTag = new NbtCompound();
        door.writeNbt(fullTag);

        assertTrue(fullTag.contains("owner"), "Full server NBT must store owner");
        assertTrue(fullTag.contains("ownerName"), "Full server NBT must store ownerName");
        assertTrue(fullTag.contains("keypadCode"), "Full server NBT must store keypadCode");
        assertTrue(fullTag.contains("scannerCode"), "Full server NBT must store scannerCode");
        assertEquals("SUPER_SECRET_PIN_9988", fullTag.getString("keypadCode"));
        assertEquals("CONFIDENTIAL_BIOMETRIC_TOKEN", fullTag.getString("scannerCode"));

        // Client network sync packet NBT
        NbtCompound clientTag = door.toInitialChunkDataNbt();

        // Must contain basic visual sync fields
        assertTrue(clientTag.contains("state"));
        assertTrue(clientTag.contains("timer"));
        assertTrue(clientTag.contains("skin"));
        assertTrue(clientTag.contains("breached"));

        // MUST STRICTLY NOT CONTAIN any sensitive security or code fields
        assertFalse(clientTag.contains("owner"), "Client sync NBT must NEVER leak owner UUID");
        assertFalse(clientTag.contains("ownerName"), "Client sync NBT must NEVER leak owner name");
        assertFalse(clientTag.contains("keypadCode"), "Client sync NBT must NEVER leak keypad passcode");
        assertFalse(clientTag.contains("scannerCode"), "Client sync NBT must NEVER leak scanner passcode");
        assertFalse(clientTag.contains("boundScanners"), "Client sync NBT must NEVER leak bound scanners");
        assertFalse(clientTag.contains("linkedGates"), "Client sync NBT must NEVER leak linked gates");
    }

    @Test
    @DisplayName("Verify Full NBT Roundtrip Deserialization")
    public void testNbtRoundtrip() {
        DoorBlockEntity door1 = createSecurityDoor(DoorDecls.VAULT_DOOR);
        UUID ownerId = UUID.randomUUID();
        door1.getSecurity().setOwner(ownerId, "MasterKey");
        door1.getSecurity().setRedstoneEnabled(true);
        door1.bindKeypadCode("7741");
        door1.setScannerCode("CARD_GAMMA");
        door1.closeAfter(20);

        NbtCompound tag = new NbtCompound();
        door1.writeNbt(tag);

        DoorBlockEntity door2 = createSecurityDoor(DoorDecls.VAULT_DOOR);
        door2.readNbt(tag);

        assertEquals(ownerId, door2.getSecurity().getOwnerId());
        assertEquals("MasterKey", door2.getSecurity().getOwnerName());
        assertTrue(door2.getSecurity().isRedstoneEnabled());
        assertEquals("7741", door2.getKeypadCode());
        assertEquals("CARD_GAMMA", door2.getScannerCode());
        assertEquals(20, door2.getAutoCloseTicks());
    }

    @Test
    @DisplayName("Verify Keypad and Scanner Binding and Clearing")
    public void testKeypadAndScannerBinding() {
        DoorBlockEntity door = createNormalDoor(DoorDecls.FIRE_DOOR);

        assertFalse(door.isKeypadBound());
        assertEquals("", door.getKeypadCode());
        assertEquals("", door.getScannerCode());

        door.bindKeypadCode("1234");
        assertTrue(door.isKeypadBound());
        assertEquals("1234", door.getKeypadCode());

        // Overwrite blocked
        door.bindKeypadCode("9999");
        assertEquals("1234", door.getKeypadCode(), "Keypad code must not be overwritten when already bound");

        door.clearKeypadCode();
        assertFalse(door.isKeypadBound());
        assertEquals("", door.getKeypadCode());

        // Scanner code
        door.setScannerCode("CARD_ALPHA");
        assertEquals("CARD_ALPHA", door.getScannerCode());

        door.clearScannerCode();
        assertEquals("", door.getScannerCode());
    }
}
