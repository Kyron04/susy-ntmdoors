package com.ntmdoors.test.security;

import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityEventData;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.Bootstrap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalPasswordAndSecurityNetTest {

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
    @DisplayName("Verify SHA-256 hex hashing, format validation, and constant-time matching")
    public void testSha256PasswordHashing() {
        String password = "CorrectHorseBatteryStaple!";
        String hash = MonitorPasswords.hash(password);

        assertNotNull(hash, "Password hash must not be null");
        assertEquals(64, hash.length(), "SHA-256 hex string must be exactly 64 characters");
        assertTrue(MonitorPasswords.isValidHash(hash), "Hash should pass 64-char lowercase hex validation");

        // Constant-time matching on SHA-256 hashes
        String candidateHash = MonitorPasswords.hash(password);
        assertTrue(MonitorPasswords.matches(hash, candidateHash), "Matching password hash should authenticate");

        String wrongHash = MonitorPasswords.hash("WrongPassword");
        assertFalse(MonitorPasswords.matches(hash, wrongHash), "Wrong password hash must fail");
        assertFalse(MonitorPasswords.matches(hash, ""), "Empty hash must fail against valid hash");
        assertFalse(MonitorPasswords.matches(hash, null), "Null candidate hash must fail");

        // Edge case: null or invalid inputs
        assertEquals("", MonitorPasswords.hash(null), "Hashing null should yield empty string");
        assertFalse(MonitorPasswords.matches(null, candidateHash), "Null stored hash must fail");
    }

    @Test
    @DisplayName("Verify password hash syntax validation edge cases")
    public void testPasswordHashValidationEdgeCases() {
        // Valid 64-char lowercase hex
        String valid = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertTrue(MonitorPasswords.isValidHash(valid));

        // Too short (63 chars)
        assertFalse(MonitorPasswords.isValidHash(valid.substring(1)));

        // Too long (65 chars)
        assertFalse(MonitorPasswords.isValidHash(valid + "a"));

        // Uppercase letters rejected
        assertFalse(MonitorPasswords.isValidHash(valid.toUpperCase()));

        // Non-hex character
        String nonHex = valid.substring(0, 63) + "z";
        assertFalse(MonitorPasswords.isValidHash(nonHex));

        // Null and empty
        assertFalse(MonitorPasswords.isValidHash(null));
        assertFalse(MonitorPasswords.isValidHash(""));
    }

    @Test
    @DisplayName("Verify SecurityNet device management and display naming")
    public void testSecurityNetDeviceManagement() {
        SecurityNet net = new SecurityNet(4, "Sector B Armory");
        assertEquals(4, net.id);
        assertEquals("Sector B Armory", net.getName());
        assertEquals("Sector B Armory", net.displayName());

        SecurityNet unnamed = new SecurityNet(0, "");
        assertEquals("Security net 1", unnamed.displayName());

        List<MonitorDevice> devices = new ArrayList<>();
        devices.add(new MonitorDevice(MonitorDevice.Type.DOOR, new BlockPos(10, 64, 20), "Blast Door"));
        devices.add(new MonitorDevice(MonitorDevice.Type.KEYPAD, new BlockPos(11, 64, 20), "Access Keypad"));
        devices.add(new MonitorDevice(MonitorDevice.Type.SCANNER, new BlockPos(12, 64, 20), "Iris Scanner"));
        devices.add(new MonitorDevice(MonitorDevice.Type.MOTION_SENSOR, new BlockPos(10, 66, 20), "Overhead Sensor"));
        devices.add(new MonitorDevice(MonitorDevice.Type.REDSTONE_GATE, new BlockPos(9, 64, 20), "Isolation Gate"));
        devices.add(new MonitorDevice(MonitorDevice.Type.SPEAKER, new BlockPos(10, 68, 20), "Klaxon"));

        net.setDevices(devices);
        assertEquals(6, net.getDevices().size());
        assertEquals("Blast Door", net.getDevices().get(0).name);
        assertEquals(MonitorDevice.Type.DOOR, net.getDevices().get(0).type);
        assertEquals(new BlockPos(10, 64, 20), net.getDevices().get(0).pos);
    }

    @Test
    @DisplayName("Verify SecurityNet event bitmask encoding and toggling")
    public void testSecurityNetEventMask() {
        SecurityNet net = new SecurityNet(1, "Test Net");
        assertEquals(0, net.getEventMask(), "Initial event mask should be 0");

        net.setEventEnabled(SecurityNet.EVENT_SYSTEM_SHUTDOWN, true);
        assertEquals(1, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_SYSTEM_LOCKDOWN, true);
        assertEquals(0x03, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_TRIGGER_ALARMS, true);
        assertEquals(0x07, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_DISABLE_KEYPADS, true);
        assertEquals(0x0F, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_DISABLE_SCANNERS, true);
        assertEquals(0x1F, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_DISABLE_SENSORS, true);
        assertEquals(0x3F, net.getEventMask());

        net.setEventEnabled(SecurityNet.EVENT_DISABLE_REDSTONE_GATES, true);
        assertEquals(0x7F, net.getEventMask());

        // Toggle one off
        net.setEventEnabled(SecurityNet.EVENT_TRIGGER_ALARMS, false);
        assertEquals(0x7B, net.getEventMask());
        assertFalse(net.getEventEnabled(SecurityNet.EVENT_TRIGGER_ALARMS));
        assertTrue(net.getEventEnabled(SecurityNet.EVENT_SYSTEM_SHUTDOWN));
    }

    @Test
    @DisplayName("Verify SecurityNet NBT serialization and deserialization roundtrip")
    public void testSecurityNetNbtRoundtrip() {
        SecurityNet original = new SecurityNet(7, "Cryo Bay Net");
        original.linkKey("RELAY-KEY-999", "Security Team Alpha");
        original.setEventEnabled(SecurityNet.EVENT_SYSTEM_LOCKDOWN, true);
        original.setEventEnabled(SecurityNet.EVENT_DISABLE_KEYPADS, true);

        List<MonitorDevice> devs = new ArrayList<>();
        devs.add(new MonitorDevice(MonitorDevice.Type.DOOR, new BlockPos(100, 70, -200), "Cryo Vault Door"));
        devs.add(new MonitorDevice(MonitorDevice.Type.SCANNER, new BlockPos(101, 70, -200), "Biometric Scanner", true, false));
        original.setDevices(devs);

        NbtCompound tag = original.toNbt();
        assertNotNull(tag);

        SecurityNet restored = SecurityNet.fromNbt(tag);
        assertNotNull(restored);
        assertEquals(original.id, restored.id);
        assertEquals(original.getName(), restored.getName());
        assertEquals("RELAY-KEY-999", restored.getLinkedKeyId());
        assertEquals("Security Team Alpha", restored.getLinkedKeyName());
        assertTrue(restored.hasLinkedKey());
        assertTrue(restored.getEventEnabled(SecurityNet.EVENT_SYSTEM_LOCKDOWN));
        assertTrue(restored.getEventEnabled(SecurityNet.EVENT_DISABLE_KEYPADS));
        assertFalse(restored.getEventEnabled(SecurityNet.EVENT_SYSTEM_SHUTDOWN));

        assertEquals(2, restored.getDevices().size());
        assertEquals("Cryo Vault Door", restored.getDevices().get(0).name);
        assertEquals(new BlockPos(100, 70, -200), restored.getDevices().get(0).pos);
        assertEquals(MonitorDevice.Type.DOOR, restored.getDevices().get(0).type);
    }

    @Test
    @DisplayName("Verify SecurityEventData device bit aggregation and NBT persistence")
    public void testSecurityEventDataAggregationAndNbt() {
        SecurityEventData data = new SecurityEventData();

        BlockPos desk1 = new BlockPos(0, 64, 0);
        BlockPos desk2 = new BlockPos(50, 64, 50);

        BlockPos doorPos = new BlockPos(10, 64, 10);
        BlockPos keypadPos = new BlockPos(11, 64, 10);

        // Populate raw data using NBT to test persistence & state aggregation
        NbtCompound tag = new NbtCompound();
        // Construct desks list
        NbtCompound desk1Tag = new NbtCompound();
        desk1Tag.putLong("Desk", desk1.asLong());
        net.minecraft.nbt.NbtList devList1 = new net.minecraft.nbt.NbtList();

        NbtCompound d1 = new NbtCompound();
        d1.putLong("Pos", doorPos.asLong());
        d1.putInt("Flags", SecurityEvents.LOCKDOWN);
        devList1.add(d1);

        NbtCompound d2 = new NbtCompound();
        d2.putLong("Pos", keypadPos.asLong());
        d2.putInt("Flags", SecurityEvents.DISABLE_KEYPADS);
        devList1.add(d2);

        desk1Tag.put("Devices", devList1);

        NbtCompound desk2Tag = new NbtCompound();
        desk2Tag.putLong("Desk", desk2.asLong());
        net.minecraft.nbt.NbtList devList2 = new net.minecraft.nbt.NbtList();

        NbtCompound d3 = new NbtCompound();
        d3.putLong("Pos", doorPos.asLong());
        d3.putInt("Flags", SecurityEvents.SHUTDOWN); // Desk 2 also flags shutdown on the door
        devList2.add(d3);

        desk2Tag.put("Devices", devList2);

        net.minecraft.nbt.NbtList desksTag = new net.minecraft.nbt.NbtList();
        desksTag.add(desk1Tag);
        desksTag.add(desk2Tag);
        tag.put("Desks", desksTag);

        data.readNbt(tag);

        // Combined state on door should be LOCKDOWN | SHUTDOWN
        int doorState = data.state(doorPos);
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.SHUTDOWN, doorState);

        // Keypad state should be DISABLE_KEYPADS
        int keypadState = data.state(keypadPos);
        assertEquals(SecurityEvents.DISABLE_KEYPADS, keypadState);

        // Non-existent device should return 0
        assertEquals(0, data.state(new BlockPos(999, 999, 999)));

        // Roundtrip serialization
        NbtCompound outTag = new NbtCompound();
        data.writeNbt(outTag);

        SecurityEventData restored = SecurityEventData.fromNbt(outTag);
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.SHUTDOWN, restored.state(doorPos));
        assertEquals(SecurityEvents.DISABLE_KEYPADS, restored.state(keypadPos));
    }
}
