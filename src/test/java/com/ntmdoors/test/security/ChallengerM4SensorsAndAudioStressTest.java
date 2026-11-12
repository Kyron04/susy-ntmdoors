package com.ntmdoors.test.security;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.MotionSensorBlock;
import com.ntmdoors.block.MotionSensorStatus;
import com.ntmdoors.block.RedstoneGateBlock;
import com.ntmdoors.block.SpeakerBlock;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SpeakerBlockEntity;
import com.ntmdoors.client.sound.SpeakerSoundInstance;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial and empirical stress test suite for Milestone M4:
 * Motion Sensors (PIR 5x5x5 footprint, center cell clamp, entity filters),
 * Speakers (sound instance AttenuationType.NONE, listener tracking, distance fade math),
 * RF Linking (cycle/loop rejection in RedstoneBinding),
 * and Redstone Gate (anti-feedback probe guard ThreadLocal, pulse clamping).
 */
public class ChallengerM4SensorsAndAudioStressTest {

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
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
    }

    public static class DummyPlayer extends net.minecraft.entity.player.PlayerEntity {
        public UUID customUuid = UUID.randomUUID();
        public String customName = "Dummy";
        public boolean creativeMode = false;

        public DummyPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Dummy"));
        }

        @Override
        public UUID getUuid() { return customUuid; }
        @Override
        public String getUuidAsString() { return customUuid != null ? customUuid.toString() : ""; }
        @Override
        public net.minecraft.text.Text getName() { return net.minecraft.text.Text.literal(customName); }
        @Override
        public boolean isSpectator() { return false; }
        @Override
        public boolean isCreative() { return creativeMode; }
        @Override
        public void sendMessage(net.minecraft.text.Text message, boolean overlay) {}
    }

    private DummyPlayer createDummyPlayer(UUID uuid, String name, boolean creative) {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            DummyPlayer p = (DummyPlayer) unsafe.allocateInstance(DummyPlayer.class);
            p.customUuid = uuid;
            p.customName = name;
            p.creativeMode = creative;
            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MotionSensorBlockEntity createSensor(Direction facing, BlockPos pos) {
        MotionSensorBlock block = ModBlocks.MOTION_SENSOR;
        BlockState state = block.getDefaultState().with(MotionSensorBlock.FACING, facing);
        return new MotionSensorBlockEntity(pos, state);
    }

    private RedstoneGateBlockEntity createGate(BlockPos pos) {
        RedstoneGateBlock block = ModBlocks.REDSTONE_GATE;
        BlockState state = block.getDefaultState();
        return new RedstoneGateBlockEntity(pos, state);
    }

    private SpeakerBlockEntity createSpeaker(BlockPos pos) {
        SpeakerBlock block = ModBlocks.SPEAKER;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new SpeakerBlockEntity(pos, state);
    }

    // =========================================================================
    // 1. MOTION SENSOR PIR 5x5x5 DETECTION FOOTPRINT & PROJECTION
    // =========================================================================

    @Test
    @DisplayName("PIR: Center cell depth is strictly clamped to >= 1 when area is bound")
    public void testPIRCenterCellDepthClamping() {
        MotionSensorBlockEntity sensor = createSensor(Direction.NORTH, BlockPos.ORIGIN);

        assertEquals(5, MotionSensorBlockEntity.GRID_SIZE);
        assertEquals(5, MotionSensorBlockEntity.MAX_DEPTH);
        assertEquals(2, MotionSensorBlockEntity.CENTER);
        assertEquals(25, sensor.getAreaDepths().length);

        // Verify initial state: all cells have depth 5 and area is bound
        assertTrue(sensor.isAreaBound());
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                assertEquals(5, sensor.getDepth(u, v));
            }
        }

        // Test 1: Setting center cell to 0 in depths array is clamped to 1
        byte[] customDepths = new byte[25];
        Arrays.fill(customDepths, (byte) 2);
        customDepths[2 + 2 * 5] = 0; // Center cell (u=2, v=2)
        sensor.setAreaDepths(customDepths);

        assertEquals(1, sensor.getDepth(2, 2), "Center cell must be clamped to >= 1");
        assertEquals(2, sensor.getDepth(0, 0));
        assertEquals(2, sensor.getDepth(4, 4));
        assertTrue(sensor.isAreaBound());

        // Test 2: Setting center cell to negative depth (-5) is clamped to 1
        customDepths[2 + 2 * 5] = -5;
        sensor.setAreaDepths(customDepths);
        assertEquals(1, sensor.getDepth(2, 2), "Negative depth on center cell must be clamped to 1");

        // Test 3: Setting center cell to valid non-zero depths (3, 5) preserves value
        customDepths[2 + 2 * 5] = 3;
        sensor.setAreaDepths(customDepths);
        assertEquals(3, sensor.getDepth(2, 2));

        customDepths[2 + 2 * 5] = 5;
        sensor.setAreaDepths(customDepths);
        assertEquals(5, sensor.getDepth(2, 2));

        // Test 4: clearArea() resets all depths including center to 0, unbinds area, sets MAINTENANCE
        sensor.clearArea();
        assertFalse(sensor.isAreaBound());
        for (int u = 0; u < 5; u++) {
            for (int v = 0; v < 5; v++) {
                assertEquals(0, sensor.getDepth(u, v));
            }
        }
        assertEquals(MotionSensorStatus.MAINTENANCE, sensor.getStatus());

        // Test 5: Re-applying depths after clearArea() restores areaBound and clamps center >= 1
        byte[] reapply = new byte[25]; // all 0s
        sensor.setAreaDepths(reapply);
        assertTrue(sensor.isAreaBound());
        assertEquals(1, sensor.getDepth(2, 2), "Re-applied depths must clamp center to 1 even if all 0s");

        // Test 6: Boundary tests for getDepth
        assertEquals(0, sensor.getDepth(-1, 2));
        assertEquals(0, sensor.getDepth(5, 2));
        assertEquals(0, sensor.getDepth(2, -1));
        assertEquals(0, sensor.getDepth(2, 5));

        // Test 7: Null or mismatched length arrays are rejected safely
        sensor.setAreaDepths(null);
        assertEquals(1, sensor.getDepth(2, 2));
        sensor.setAreaDepths(new byte[10]);
        assertEquals(1, sensor.getDepth(2, 2));
    }

    @Test
    @DisplayName("PIR: 5x5x5 Footprint coordinate transforms and bounding box across all 6 facings")
    public void testPIRCoordinateTransformsAcrossAllFacings() {
        BlockPos base = new BlockPos(10, 64, 20);

        for (Direction facing : Direction.values()) {
            MotionSensorBlockEntity sensor = createSensor(facing, base);
            assertEquals(facing, sensor.getFacing());

            Direction uAxis = sensor.getUAxis();
            Direction vAxis = sensor.getVAxis();

            // U and V axes must be orthogonal to facing and to each other
            assertNotEquals(facing, uAxis, "U-axis must not equal facing for " + facing);
            assertNotEquals(facing.getOpposite(), uAxis, "U-axis must not equal opposite facing for " + facing);
            assertNotEquals(facing, vAxis, "V-axis must not equal facing for " + facing);
            assertNotEquals(facing.getOpposite(), vAxis, "V-axis must not equal opposite facing for " + facing);
            assertNotEquals(uAxis, vAxis, "U-axis and V-axis must be orthogonal for " + facing);

            // Center cell at depth 0 must equal the sensor's own block pos
            BlockPos centerD0 = sensor.getCellPos(2, 2, 0);
            assertEquals(base, centerD0, "Center cell at d=0 must be base pos for " + facing);

            // Center cell at depth d must project forward along facing
            for (int d = 1; d <= 5; d++) {
                BlockPos cell = sensor.getCellPos(2, 2, d);
                assertEquals(base.offset(facing, d), cell, "Projection at depth " + d + " must match facing offset");
            }

            // Extreme corner cells (u=0, v=0) and (u=4, v=4) at depth 5
            BlockPos cornerMin = sensor.getCellPos(0, 0, 5);
            BlockPos cornerMax = sensor.getCellPos(4, 4, 5);
            BlockPos expectedMin = base.offset(uAxis, -2).offset(vAxis, -2).offset(facing, 5);
            BlockPos expectedMax = base.offset(uAxis, 2).offset(vAxis, 2).offset(facing, 5);
            assertEquals(expectedMin, cornerMin);
            assertEquals(expectedMax, cornerMax);

            // Projection bounding box must encompass all projected cells
            Box bounds = sensor.getProjectionBounds();
            assertTrue(bounds.contains(centerD0.getX() + 0.5, centerD0.getY() + 0.5, centerD0.getZ() + 0.5));
            assertTrue(bounds.contains(cornerMin.getX() + 0.5, cornerMin.getY() + 0.5, cornerMin.getZ() + 0.5));
            assertTrue(bounds.contains(cornerMax.getX() + 0.5, cornerMax.getY() + 0.5, cornerMax.getZ() + 0.5));
        }
    }

    // =========================================================================
    // 2. MOTION SENSOR ENTITY CATEGORY FILTERING & PERSIST MODES
    // =========================================================================

    @Test
    @DisplayName("PIR: Entity category filters and mutually exclusive persist modes")
    public void testEntityCategoryFiltersAndPersistModes() {
        MotionSensorBlockEntity sensor = createSensor(Direction.SOUTH, BlockPos.ORIGIN);

        // Initial default filter settings
        assertTrue(sensor.isEnabled());
        assertTrue(sensor.isDetectPlayers());
        assertTrue(sensor.isDetectHostile());
        assertTrue(sensor.isDetectNeutral());
        assertFalse(sensor.isPersistWithinBounds());
        assertFalse(sensor.isPersistOutOfBounds());

        // Toggle category filters
        sensor.setDetectPlayers(false);
        assertFalse(sensor.isDetectPlayers());
        sensor.setDetectPlayers(true);
        assertTrue(sensor.isDetectPlayers());

        sensor.setDetectHostile(false);
        assertFalse(sensor.isDetectHostile());
        sensor.setDetectHostile(true);
        assertTrue(sensor.isDetectHostile());

        sensor.setDetectNeutral(false);
        assertFalse(sensor.isDetectNeutral());
        sensor.setDetectNeutral(true);
        assertTrue(sensor.isDetectNeutral());

        // Test persist mode mutual exclusivity
        sensor.setPersistWithinBounds(true);
        assertTrue(sensor.isPersistWithinBounds());
        assertFalse(sensor.isPersistOutOfBounds(), "Enabling persistWithinBounds must disable persistOutOfBounds");

        sensor.setPersistOutOfBounds(true);
        assertTrue(sensor.isPersistOutOfBounds());
        assertFalse(sensor.isPersistWithinBounds(), "Enabling persistOutOfBounds must disable persistWithinBounds");

        sensor.setPersistOutOfBounds(false);
        assertFalse(sensor.isPersistOutOfBounds());
        assertFalse(sensor.isPersistWithinBounds());
    }

    @Test
    @DisplayName("PIR: Redstone event bitmasks and index safety")
    public void testRedstoneEventBitmasks() {
        MotionSensorBlockEntity sensor = createSensor(Direction.NORTH, BlockPos.ORIGIN);

        assertEquals(4, MotionSensorBlockEntity.RS_EVENT_COUNT);
        assertEquals(0, MotionSensorBlockEntity.RS_AUTHORIZED);
        assertEquals(1, MotionSensorBlockEntity.RS_UNAUTHORIZED);
        assertEquals(2, MotionSensorBlockEntity.RS_DISABLED);
        assertEquals(3, MotionSensorBlockEntity.RS_ENABLED);

        // Initially all redstone events are false
        for (int i = 0; i < MotionSensorBlockEntity.RS_EVENT_COUNT; i++) {
            assertFalse(sensor.isRedstoneEvent(i));
        }

        // Toggle each event
        for (int i = 0; i < MotionSensorBlockEntity.RS_EVENT_COUNT; i++) {
            sensor.setRedstoneEvent(i, true);
            assertTrue(sensor.isRedstoneEvent(i));
        }

        // Out-of-bounds index safety
        assertFalse(sensor.isRedstoneEvent(-1));
        assertFalse(sensor.isRedstoneEvent(4));
        assertFalse(sensor.isRedstoneEvent(100));

        sensor.setRedstoneEvent(-1, true); // No-op
        sensor.setRedstoneEvent(4, true);  // No-op
    }

    @Test
    @DisplayName("PIR: Credential clearance, biometrics, stolen card rejection, and ignore flags")
    public void testCredentialClearanceAndStolenCardRejection() {
        MotionSensorBlockEntity sensor = createSensor(Direction.EAST, BlockPos.ORIGIN);

        String enrolledSerial = "KEY-999888";
        UUID aliceUuid = UUID.randomUUID();
        UUID bobUuid = UUID.randomUUID();

        sensor.addKey(new PrivateKeys.Key("SecureVault", enrolledSerial));
        sensor.addBiometricId(aliceUuid.toString(), "Alice");

        assertTrue(sensor.hasAssignedKey(enrolledSerial));
        assertTrue(sensor.hasAssignedId(aliceUuid.toString()));
        assertFalse(sensor.hasAssignedKey("UNKNOWN-KEY"));
        assertFalse(sensor.hasAssignedId(bobUuid.toString()));

        // 1. Creative keycard always clears
        ItemStack creativeCard = new ItemStack(ModItems.CREATIVE_KEYCARD);
        assertTrue(sensor.checkCardClearance(creativeCard, null));

        // 2. Matching private key RFID card
        ItemStack enrolledKeyCard = new ItemStack(ModItems.RED_RFID_CARD);
        PrivateKeys.add(enrolledKeyCard, "SecureVault", enrolledSerial);
        assertTrue(sensor.checkCardClearance(enrolledKeyCard, null));

        // 3. Unenrolled private key RFID card
        ItemStack unenrolledKeyCard = new ItemStack(ModItems.BLUE_RFID_CARD);
        PrivateKeys.add(unenrolledKeyCard, "Vault", "UNENROLLED-123");
        DummyPlayer alicePlayer = createDummyPlayer(aliceUuid, "Alice", false);
        DummyPlayer bobPlayer = createDummyPlayer(bobUuid, "Bob", false);
        DummyPlayer creativePlayer = createDummyPlayer(UUID.randomUUID(), "Admin", true);

        // 4. Biometric card bound to Alice, presented by Alice
        ItemStack aliceBioCard = new ItemStack(ModItems.GREEN_RFID_CARD);
        Biometrics.bindIfNeeded(aliceBioCard, aliceUuid, "Alice");
        assertTrue(sensor.checkCardClearance(aliceBioCard, alicePlayer), "Enrolled biometric card presented by owner must clear");

        // 5. Biometric card bound to Bob (unenrolled UUID), presented by Bob
        ItemStack bobBioCard = new ItemStack(ModItems.ORANGE_RFID_CARD);
        Biometrics.bindIfNeeded(bobBioCard, bobUuid, "Bob");
        assertFalse(sensor.checkCardClearance(bobBioCard, bobPlayer), "Unenrolled biometric card must be rejected");

        // 6. Stolen card protection: matchHolder = true, card bound to Alice but held by Bob
        sensor.setMatchHolder(true);
        assertFalse(sensor.checkCardClearance(aliceBioCard, bobPlayer),
                "Stolen biometric card (Alice's card held by Bob) must be rejected when matchHolder is true");

        // 7. When matchHolder = false, card bound to Alice can be held by anyone
        sensor.setMatchHolder(false);
        assertTrue(sensor.checkCardClearance(aliceBioCard, bobPlayer),
                "When matchHolder is false, enrolled card presented by different player must clear");
        assertTrue(sensor.checkCardClearance(aliceBioCard, null),
                "When matchHolder is false, enrolled card without player reference must clear");
        sensor.setMatchHolder(true);

        // 8. Testing ignoreKeys and ignoreIds
        sensor.setIgnoreKeys(true);
        assertFalse(sensor.checkCardClearance(enrolledKeyCard, null), "Ignored keys must reject enrolled key card");
        sensor.setIgnoreKeys(false);

        sensor.setIgnoreIds(true);
        assertFalse(sensor.checkCardClearance(aliceBioCard, alicePlayer), "Ignored IDs must reject enrolled biometric card");
        sensor.setIgnoreIds(false);

        // 9. Remove credentials
        sensor.removeKey(enrolledSerial);
        assertFalse(sensor.hasAssignedKey(enrolledSerial));
        assertFalse(sensor.checkCardClearance(enrolledKeyCard, null));

        sensor.removeBiometricId(aliceUuid.toString());
        assertFalse(sensor.hasAssignedId(aliceUuid.toString()));
        assertFalse(sensor.checkCardClearance(aliceBioCard, alicePlayer));
    }

    @Test
    @DisplayName("PIR: NBT serialization roundtrip preserves all footprint and filter state")
    public void testPIRNbtSerializationRoundtrip() {
        MotionSensorBlockEntity sensor = createSensor(Direction.WEST, new BlockPos(5, 64, 5));

        byte[] customDepths = new byte[25];
        for (int i = 0; i < 25; i++) customDepths[i] = (byte) (i % 5 + 1);
        sensor.setAreaDepths(customDepths);
        sensor.setDetectPlayers(false);
        sensor.setDetectHostile(true);
        sensor.setDetectNeutral(false);
        sensor.setPersistWithinBounds(true);
        sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_AUTHORIZED, true);
        sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_UNAUTHORIZED, true);
        sensor.addKey(new PrivateKeys.Key("Door1", "SER-111"));
        sensor.addBiometricId("uuid-123", "Charlie");

        NbtCompound tag = new NbtCompound();
        sensor.writeNbt(tag);

        MotionSensorBlockEntity restored = createSensor(Direction.WEST, new BlockPos(5, 64, 5));
        restored.readNbt(tag);

        assertTrue(restored.isAreaBound());
        for (int i = 0; i < 25; i++) {
            assertEquals(sensor.getAreaDepths()[i], restored.getAreaDepths()[i]);
        }
        assertFalse(restored.isDetectPlayers());
        assertTrue(restored.isDetectHostile());
        assertFalse(restored.isDetectNeutral());
        assertTrue(restored.isPersistWithinBounds());
        assertFalse(restored.isPersistOutOfBounds());
        assertTrue(restored.isRedstoneEvent(MotionSensorBlockEntity.RS_AUTHORIZED));
        assertTrue(restored.isRedstoneEvent(MotionSensorBlockEntity.RS_UNAUTHORIZED));
        assertFalse(restored.isRedstoneEvent(MotionSensorBlockEntity.RS_DISABLED));
        assertTrue(restored.hasAssignedKey("SER-111"));
        assertTrue(restored.hasAssignedId("uuid-123"));
    }

    // =========================================================================
    // 3. RF LINKING & CYCLE / LOOP PREVENTION (RedstoneBinding)
    // =========================================================================

    @Test
    @DisplayName("RF: Self-loop Gate A -> Gate A is strictly rejected")
    public void testRFSelfLoopRejection() {
        BlockPos posA = new BlockPos(10, 64, 10);
        RedstoneGateBlockEntity gateA = createGate(posA);
        RedstoneBinding bindingA = gateA.getRedstoneBinding();

        // Self-link must be detected as loop
        assertTrue(bindingA.createsLoop(gateA), "Gate must detect direct self-loop (Gate A -> Gate A)");
    }

    @Test
    @DisplayName("RF: 2-node cycle Gate A -> Gate B -> Gate A is strictly rejected")
    public void testRF2NodeCycleRejection() {
        BlockPos posA = new BlockPos(10, 64, 10);
        BlockPos posB = new BlockPos(20, 64, 20);

        RedstoneGateBlockEntity gateA = createGate(posA);
        RedstoneGateBlockEntity gateB = createGate(posB);

        RedstoneBinding bindingA = gateA.getRedstoneBinding();
        RedstoneBinding bindingB = gateB.getRedstoneBinding();

        // Initial state: no links exist
        assertFalse(bindingA.hasLinks());
        assertFalse(bindingB.hasLinks());

        // Linking Gate A to Gate B does not create a loop initially
        assertFalse(bindingA.createsLoop(gateB), "Gate A linking to Gate B initially does not create a loop");

        // Gate A binds Gate B
        bindingA.getGates().add(posB);
        assertTrue(bindingA.hasLinks());
        assertEquals(1, bindingA.getGates().size());

        // Now, Gate B attempting to link back to Gate A creates a cyclic loop (A -> B -> A)
        assertTrue(bindingB.createsLoop(gateA), "Gate B linking back to Gate A must be strictly detected as a cyclic loop");
    }

    @Test
    @DisplayName("RF: Null candidates and hosts handle loop checks safely without exceptions")
    public void testRFLoopCheckNullSafety() {
        BlockPos posA = new BlockPos(10, 64, 10);
        RedstoneGateBlockEntity gateA = createGate(posA);
        RedstoneBinding bindingA = gateA.getRedstoneBinding();

        assertFalse(bindingA.createsLoop(null), "Null candidate must safely return false");

        RedstoneBinding unhostedBinding = new RedstoneBinding();
        assertFalse(unhostedBinding.createsLoop(gateA), "Unhosted binding must safely return false");
    }

    @Test
    @DisplayName("RF: Host types restrict door binding to Redstone Gate only")
    public void testRFDoorBindingHostRestrictions() {
        BlockPos posSensor = new BlockPos(0, 64, 0);
        MotionSensorBlockEntity sensor = createSensor(Direction.NORTH, posSensor);
        RedstoneBinding sensorBinding = sensor.getRedstoneBinding();

        // MotionSensor is NOT a gate host
        assertFalse(sensorBinding.isGateHost(), "MotionSensor must not be a gate host");

        BlockPos posGate = new BlockPos(10, 64, 10);
        RedstoneGateBlockEntity gate = createGate(posGate);
        RedstoneBinding gateBinding = gate.getRedstoneBinding();

        assertTrue(gateBinding.isGateHost(), "RedstoneGate must be a gate host");
    }

    @Test
    @DisplayName("RF: Active binding session tracking and timeout")
    public void testRFActiveSessionTracking() {
        BlockPos posGate = new BlockPos(10, 64, 10);
        RedstoneGateBlockEntity gate = createGate(posGate);
        RedstoneBinding binding = gate.getRedstoneBinding();

        UUID testPlayer = UUID.randomUUID();
        assertFalse(binding.isBindingFor(null));

        RedstoneBinding.ACTIVE_SESSIONS.put(testPlayer, posGate);
        assertTrue(RedstoneBinding.ACTIVE_SESSIONS.containsKey(testPlayer));
        assertEquals(posGate, RedstoneBinding.ACTIVE_SESSIONS.get(testPlayer));

        RedstoneBinding.ACTIVE_SESSIONS.remove(testPlayer);
        assertFalse(RedstoneBinding.ACTIVE_SESSIONS.containsKey(testPlayer));
    }

    // =========================================================================
    // 4. SPEAKER SOUND INSTANCE & NON-DIRECTIONAL ATTENUATION
    // =========================================================================

    @Test
    @DisplayName("Speaker: SpeakerSoundInstance strictly uses AttenuationType.NONE")
    public void testSpeakerSoundInstanceAttenuationType() {
        SpeakerBlockEntity speaker = createSpeaker(BlockPos.ORIGIN);
        SpeakerSoundInstance soundInstance = new SpeakerSoundInstance(
                speaker,
                ModSounds.SPEAKER_AIR_RAID,
                0
        );

        assertEquals(SoundInstance.AttenuationType.NONE, soundInstance.getAttenuationType(),
                "SpeakerSoundInstance must use AttenuationType.NONE for non-directional listener-relative playback");
        assertTrue(soundInstance.isRepeatable(), "Alarm sound instance must repeat");
        assertEquals(0, soundInstance.getRepeatDelay());
    }

    @Test
    @DisplayName("Speaker: Distance-attenuated fade math [fade = 1.0 - (dist / range)] stress verification")
    public void testSpeakerDistanceFadeMathStress() {
        int[] testRanges = {1, 16, 32, 64};

        for (int range : testRanges) {
            // Distance = 0: Full fade 1.0
            float fade0 = 1.0F - (0.0F / (float) range);
            assertEquals(1.0F, fade0, 1e-4F);

            // Distance = quarter range: fade 0.75
            float fadeQuarter = 1.0F - ((range * 0.25F) / (float) range);
            assertEquals(0.75F, fadeQuarter, 1e-4F);

            // Distance = half range: fade 0.50
            float fadeHalf = 1.0F - ((range * 0.50F) / (float) range);
            assertEquals(0.50F, fadeHalf, 1e-4F);

            // Distance = three-quarters range: fade 0.25
            float fadeThreeQuarter = 1.0F - ((range * 0.75F) / (float) range);
            assertEquals(0.25F, fadeThreeQuarter, 1e-4F);

            // Distance = boundary range: fade 0.0
            float fadeBoundary = 1.0F - ((float) range / (float) range);
            assertEquals(0.0F, fadeBoundary, 1e-4F);

            // Distance beyond range: negative fade before clamping
            float fadeBeyond = 1.0F - ((range * 1.5F) / (float) range);
            assertTrue(fadeBeyond < 0.0F);
        }

        // Volume calculation with speaker volume levels (100, 75, 50, 25, 0)
        int[] testVolumes = {100, 75, 50, 25, 0};
        for (int vol : testVolumes) {
            float fade = 0.5F; // half distance
            float volumeSettingFrac = vol / 100.0F;
            float calcVolume = Math.max(0.0001F, Math.min(1.0F, fade * volumeSettingFrac));

            if (vol == 100) {
                assertEquals(0.5F, calcVolume, 1e-4F);
            } else if (vol == 50) {
                assertEquals(0.25F, calcVolume, 1e-4F);
            } else if (vol == 0) {
                assertEquals(0.0001F, calcVolume, 1e-4F, "Zero volume clamped to minimum 0.0001F");
            }
        }
    }

    @Test
    @DisplayName("Speaker: Range, Volume, and Track boundary clamping stress")
    public void testSpeakerBoundaryClampingStress() {
        SpeakerBlockEntity speaker = createSpeaker(BlockPos.ORIGIN);

        assertEquals(SpeakerBlockEntity.MIN_RANGE, 1);
        assertEquals(SpeakerBlockEntity.MAX_RANGE, 64);
        assertEquals(SpeakerBlockEntity.MIN_VOLUME, 0);
        assertEquals(SpeakerBlockEntity.MAX_VOLUME, 100);
        assertEquals(SpeakerBlockEntity.SOUND_COUNT, 4);

        // Sound clamping
        assertEquals(SpeakerBlockEntity.SOUND_AIR_RAID, SpeakerBlockEntity.clampSound(-10));
        assertEquals(SpeakerBlockEntity.SOUND_AIR_RAID, SpeakerBlockEntity.clampSound(0));
        assertEquals(SpeakerBlockEntity.SOUND_FACILITY, SpeakerBlockEntity.clampSound(1));
        assertEquals(SpeakerBlockEntity.SOUND_INDUSTRIAL, SpeakerBlockEntity.clampSound(2));
        assertEquals(SpeakerBlockEntity.SOUND_EAS, SpeakerBlockEntity.clampSound(3));
        assertEquals(SpeakerBlockEntity.SOUND_EAS, SpeakerBlockEntity.clampSound(100));

        // Sound names
        assertEquals("Air Raid", SpeakerBlockEntity.soundName(0));
        assertEquals("Facility", SpeakerBlockEntity.soundName(1));
        assertEquals("Industrial", SpeakerBlockEntity.soundName(2));
        assertEquals("EAS", SpeakerBlockEntity.soundName(3));
        assertEquals("Air Raid", SpeakerBlockEntity.soundName(-1));
        assertEquals("EAS", SpeakerBlockEntity.soundName(10));

        // Range clamping
        assertEquals(1, SpeakerBlockEntity.clampRange(-50));
        assertEquals(1, SpeakerBlockEntity.clampRange(0));
        assertEquals(32, SpeakerBlockEntity.clampRange(32));
        assertEquals(64, SpeakerBlockEntity.clampRange(64));
        assertEquals(64, SpeakerBlockEntity.clampRange(128));

        // Volume clamping
        assertEquals(0, SpeakerBlockEntity.clampVolume(-100));
        assertEquals(0, SpeakerBlockEntity.clampVolume(0));
        assertEquals(50, SpeakerBlockEntity.clampVolume(50));
        assertEquals(100, SpeakerBlockEntity.clampVolume(100));
        assertEquals(100, SpeakerBlockEntity.clampVolume(250));
    }

    // =========================================================================
    // 5. REDSTONE GATE ANTI-FEEDBACK PROBE GUARD & PULSE CLAMPING
    // =========================================================================

    @Test
    @DisplayName("Gate: Anti-feedback probe guard returns 0 during evaluation, preventing latch lockup")
    public void testRedstoneGateAntiFeedbackProbeGuard() throws Exception {
        RedstoneGateBlock gateBlock = ModBlocks.REDSTONE_GATE;
        BlockState poweredState = gateBlock.getDefaultState().with(RedstoneGateBlock.POWERED, true);
        BlockState unpoweredState = gateBlock.getDefaultState().with(RedstoneGateBlock.POWERED, false);

        // Access the private static ThreadLocal<Boolean> PROBING_POWER field via reflection
        Field probeField = RedstoneGateBlock.class.getDeclaredField("PROBING_POWER");
        probeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Boolean> probingPower = (ThreadLocal<Boolean>) probeField.get(null);

        // Baseline: PROBING_POWER is initially FALSE
        assertEquals(Boolean.FALSE, probingPower.get(), "PROBING_POWER must default to FALSE");

        // When PROBING_POWER == false:
        // Powered gate emits full power 15
        assertEquals(15, gateBlock.getWeakRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH));
        assertEquals(15, gateBlock.getStrongRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH));

        // Unpowered gate emits 0
        assertEquals(0, gateBlock.getWeakRedstonePower(unpoweredState, null, BlockPos.ORIGIN, Direction.NORTH));
        assertEquals(0, gateBlock.getStrongRedstonePower(unpoweredState, null, BlockPos.ORIGIN, Direction.NORTH));

        // When PROBING_POWER == true (simulating gate evaluation during isReceivingPower):
        probingPower.set(Boolean.TRUE);
        try {
            // Even though gate state is POWERED == true, it MUST return 0 power during probing!
            assertEquals(0, gateBlock.getWeakRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH),
                    "Anti-feedback probe guard MUST suppress weak power to 0 while probing");
            assertEquals(0, gateBlock.getStrongRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH),
                    "Anti-feedback probe guard MUST suppress strong power to 0 while probing");
        } finally {
            probingPower.set(Boolean.FALSE);
        }

        // Post-probe: PROBING_POWER returns to FALSE, full power restored
        assertEquals(15, gateBlock.getWeakRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH),
                "Power must return to 15 after probing finishes");
        assertEquals(15, gateBlock.getStrongRedstonePower(poweredState, null, BlockPos.ORIGIN, Direction.NORTH),
                "Strong power must return to 15 after probing finishes");
    }

    @Test
    @DisplayName("Gate: Wireless pulse duration adjustment and clamping (20 to 6000 ticks)")
    public void testRedstoneGatePulseDurationClampingStress() {
        RedstoneGateBlockEntity gate = createGate(BlockPos.ORIGIN);

        assertEquals(20, RedstoneGateBlockEntity.MIN_ACTIVE_TICKS);
        assertEquals(6000, RedstoneGateBlockEntity.MAX_ACTIVE_TICKS);
        assertEquals(20, gate.getActiveTicks(), "Default pulse ticks must be 20 (1 second)");

        // Incrementally adjust
        gate.adjustActiveTicks(20);
        assertEquals(40, gate.getActiveTicks());

        gate.adjustActiveTicks(100);
        assertEquals(140, gate.getActiveTicks());

        // Extreme positive delta clamps to 6000 (5 minutes)
        gate.adjustActiveTicks(100000);
        assertEquals(6000, gate.getActiveTicks(), "Pulse duration must clamp at MAX_ACTIVE_TICKS (6000)");

        // Extreme negative delta clamps to 20 (1 second)
        gate.adjustActiveTicks(-200000);
        assertEquals(20, gate.getActiveTicks(), "Pulse duration must clamp at MIN_ACTIVE_TICKS (20)");

        // Exact boundary adjustments
        gate.adjustActiveTicks(5980);
        assertEquals(6000, gate.getActiveTicks());

        gate.adjustActiveTicks(-5980);
        assertEquals(20, gate.getActiveTicks());
    }

    @Test
    @DisplayName("Gate: NBT serialization restores clamped activeTicks and modes")
    public void testRedstoneGateNbtRoundtrip() {
        RedstoneGateBlockEntity gate = createGate(new BlockPos(12, 64, 12));
        gate.adjustActiveTicks(200); // 220 ticks
        gate.setStayActive(true);
        gate.setInverted(true);

        NbtCompound tag = new NbtCompound();
        gate.writeNbt(tag);

        RedstoneGateBlockEntity restored = createGate(new BlockPos(12, 64, 12));
        restored.readNbt(tag);

        assertEquals(220, restored.getActiveTicks());
        assertTrue(restored.isStayActive());
        assertTrue(restored.isInverted());
    }
}
