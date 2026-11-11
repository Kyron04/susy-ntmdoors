package com.ntmdoors.test.security;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.MotionSensorBlock;
import com.ntmdoors.block.MotionSensorStatus;
import com.ntmdoors.block.RedstoneGateBlock;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MotionSensorAndLinkingTest {

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
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
    }

    private MotionSensorBlockEntity createSensor(Direction facing) {
        MotionSensorBlock block = ModBlocks.MOTION_SENSOR;
        BlockState state = block.getDefaultState().with(MotionSensorBlock.FACING, facing);
        return new MotionSensorBlockEntity(BlockPos.ORIGIN, state);
    }

    private RedstoneGateBlockEntity createGate(BlockPos pos) {
        RedstoneGateBlock block = ModBlocks.REDSTONE_GATE;
        BlockState state = block.getDefaultState();
        return new RedstoneGateBlockEntity(pos, state);
    }

    @Test
    @DisplayName("Verify 5x5x5 PIR footprint grid dimensions and center cell depth preservation")
    public void testMotionSensorPIRFootprintCoordinates() {
        MotionSensorBlockEntity sensor = createSensor(Direction.NORTH);

        assertEquals(5, MotionSensorBlockEntity.GRID_SIZE);
        assertEquals(2, MotionSensorBlockEntity.CENTER);
        assertEquals(25, sensor.getAreaDepths().length);

        // Initial default depth is 5 across all 25 cells
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                assertEquals(5, sensor.getDepth(u, v));
            }
        }

        // Apply custom depths with center cell depth = 0
        byte[] custom = new byte[25];
        Arrays.fill(custom, (byte) 3);
        custom[2 + 2 * 5] = 0; // Attempt to clear center cell

        sensor.setAreaDepths(custom);

        // Center cell must be clamped to at least depth 1 while area is bound
        assertEquals(1, sensor.getDepth(2, 2), "Center cell (u=2, v=2) must preserve minimum depth 1");
        assertEquals(3, sensor.getDepth(0, 0));
        assertEquals(3, sensor.getDepth(4, 4));

        // Coordinate transforms: verify getCellPos projects forward based on facing
        BlockPos centerD0 = sensor.getCellPos(2, 2, 0);
        assertEquals(BlockPos.ORIGIN, centerD0);

        BlockPos centerD3 = sensor.getCellPos(2, 2, 3);
        assertEquals(new BlockPos(0, 0, -3), centerD3, "North facing projection moves in negative Z");

        // Clear area sets all depths to 0 and marks status MAINTENANCE
        sensor.clearArea();
        assertFalse(sensor.isAreaBound());
        assertEquals(0, sensor.getDepth(2, 2));
        assertEquals(MotionSensorStatus.MAINTENANCE, sensor.getStatus());
    }

    @Test
    @DisplayName("Verify motion sensor entity category filters and mutually exclusive persist modes")
    public void testMotionSensorEntityFilteringFlags() {
        MotionSensorBlockEntity sensor = createSensor(Direction.SOUTH);

        // Defaults
        assertTrue(sensor.isEnabled());
        assertTrue(sensor.isDetectPlayers());
        assertTrue(sensor.isDetectHostile());
        assertTrue(sensor.isDetectNeutral());
        assertFalse(sensor.isPersistWithinBounds());
        assertFalse(sensor.isPersistOutOfBounds());

        // Toggle detection flags
        sensor.setDetectPlayers(false);
        assertFalse(sensor.isDetectPlayers());

        sensor.setDetectHostile(false);
        assertFalse(sensor.isDetectHostile());

        sensor.setDetectNeutral(false);
        assertFalse(sensor.isDetectNeutral());

        // Persist mode exclusivity: enabling persistWithinBounds disables persistOutOfBounds
        sensor.setPersistWithinBounds(true);
        assertTrue(sensor.isPersistWithinBounds());
        assertFalse(sensor.isPersistOutOfBounds());

        sensor.setPersistOutOfBounds(true);
        assertTrue(sensor.isPersistOutOfBounds());
        assertFalse(sensor.isPersistWithinBounds(), "Enabling persistOutOfBounds must disable persistWithinBounds");
    }

    @Test
    @DisplayName("Verify motion sensor card credential clearance, owner matching, and stolen card rejection")
    public void testMotionSensorCredentialClearance() {
        MotionSensorBlockEntity sensor = createSensor(Direction.NORTH);

        String enrolledSerial = "9876543210";
        String enrolledUuid = UUID.randomUUID().toString();

        sensor.addKey(new PrivateKeys.Key("SecureVault", enrolledSerial));
        sensor.addBiometricId(enrolledUuid, "Alice");

        assertTrue(sensor.hasAssignedKey(enrolledSerial));
        assertTrue(sensor.hasAssignedId(enrolledUuid));

        // 1. Creative Keycard override
        ItemStack creativeCard = new ItemStack(ModItems.CREATIVE_KEYCARD);
        assertTrue(sensor.checkCardClearance(creativeCard, null), "Creative keycard must always clear");

        // 2. Matching Private Key RFID card
        ItemStack matchingKeyCard = new ItemStack(ModItems.RED_RFID_CARD);
        PrivateKeys.add(matchingKeyCard, "SecureVault", enrolledSerial);
        assertTrue(sensor.checkCardClearance(matchingKeyCard, null), "Matching private key card must clear");

        // 3. Unenrolled Private Key RFID card
        ItemStack strangerKeyCard = new ItemStack(ModItems.BLUE_RFID_CARD);
        PrivateKeys.add(strangerKeyCard, "OtherDoor", "1111222233");
        assertFalse(sensor.checkCardClearance(strangerKeyCard, null), "Unenrolled private key card must be rejected");

        // 4. Ignore keys option
        sensor.setIgnoreKeys(true);
        assertFalse(sensor.checkCardClearance(matchingKeyCard, null), "Matching private key must be ignored when ignoreKeys is true");
        sensor.setIgnoreKeys(false);

        // 5. Ignore IDs option
        sensor.setIgnoreIds(true);
        ItemStack matchingBioCard = new ItemStack(ModItems.GREEN_RFID_CARD);
        Biometrics.bindIfNeeded(matchingBioCard, UUID.fromString(enrolledUuid), "Alice");
        assertFalse(sensor.checkCardClearance(matchingBioCard, null), "Matching biometric ID must be ignored when ignoreIds is true");
        sensor.setIgnoreIds(false);
    }

    @Test
    @DisplayName("Verify Redstone Gate wireless RF linking loop detection")
    public void testRedstoneBindingLoopDetection() {
        BlockPos posA = new BlockPos(10, 64, 10);
        BlockPos posB = new BlockPos(20, 64, 20);

        RedstoneGateBlockEntity gateA = createGate(posA);
        RedstoneGateBlockEntity gateB = createGate(posB);

        RedstoneBinding bindingA = gateA.getRedstoneBinding();
        RedstoneBinding bindingB = gateB.getRedstoneBinding();

        // Gate cannot link to itself
        assertTrue(bindingA.createsLoop(gateA), "Gate must detect self-loop");

        // Linking Gate A to Gate B does not create a loop initially
        assertFalse(bindingA.createsLoop(gateB));
    }
}
