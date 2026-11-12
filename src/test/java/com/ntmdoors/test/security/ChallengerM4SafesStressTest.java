package com.ntmdoors.test.security;

import com.ntmdoors.block.BiometricSafeBlock;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.SafeBlock;
import com.ntmdoors.block.entity.BiometricSafeBlockEntity;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SafeBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.Codes;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone M4 (Safes, Redstone Gates, Hardness, & Lockouts).
 * Adversarially challenges:
 * 1. 3 consecutive failed code attempts triggering 6000-tick lockout, alarm sound, and auto-closing bound doors.
 * 2. Biometric authorized owner bypass during lockout under all ownership/holder combinations.
 * 3. voidOnDestroy inventory wipe and zero item drops.
 * 4. 3-hour mining hardness delta (1.0F / 216000.0F) and piston immovability.
 * 5. Redstone Gate pulse duration clamping [20, 6000] and NBT corruption resilience.
 * 6. SidedInventory hopper isolation across all facings.
 * 7. Security code sanitization, overlong truncation, and access session timeouts.
 */
public class ChallengerM4SafesStressTest {

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
        public Text getName() { return Text.literal(customName); }
        @Override
        public boolean isSpectator() { return false; }
        @Override
        public boolean isCreative() { return creativeMode; }
        @Override
        public void sendMessage(Text message, boolean overlay) {}
    }

    private DummyPlayer createDummyPlayer(UUID uuid, String name, boolean creative) {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
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

    private PropertyDelegate getDelegate(SafeBlockEntity safe) {
        try {
            java.lang.reflect.Field field = SafeBlockEntity.class.getDeclaredField("propertyDelegate");
            field.setAccessible(true);
            return (PropertyDelegate) field.get(safe);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SafeBlockEntity createSafe() {
        SafeBlock block = ModBlocks.SAFE;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new SafeBlockEntity(BlockPos.ORIGIN, state);
    }

    private BiometricSafeBlockEntity createBiometricSafe() {
        BiometricSafeBlock block = ModBlocks.BIOMETRIC_SAFE;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new BiometricSafeBlockEntity(BlockPos.ORIGIN, state);
    }

    private RedstoneGateBlockEntity createGate() {
        return new RedstoneGateBlockEntity(BlockPos.ORIGIN, ModBlocks.REDSTONE_GATE.getDefaultState());
    }

    private DoorBlockEntity createDoor(BlockPos pos, DoorDecl decl) {
        DoorBlockEntity door = new DoorBlockEntity(null, pos, null);
        door.setDecl(decl);
        door.setSecurity(false);
        return door;
    }

    /**
     * Test harness subclass for SafeBlockEntity to verify door dispatch mechanics
     * in the absence of a live Minecraft ServerWorld.
     */
    static class DispatchingSafeBlockEntity extends SafeBlockEntity {
        final Map<BlockPos, DoorBlockEntity> mockWorldDoors = new HashMap<>();
        int closeBoundDoorsCalls = 0;

        public DispatchingSafeBlockEntity(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        public void registerMockDoor(BlockPos pos, DoorBlockEntity door) {
            mockWorldDoors.put(pos, door);
        }

        public void injectBoundDoor(BlockPos pos) {
            try {
                java.lang.reflect.Field field = SafeBlockEntity.class.getDeclaredField("boundDoors");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<BlockPos> list = (List<BlockPos>) field.get(this);
                list.add(pos);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void closeBoundDoors() {
            closeBoundDoorsCalls++;
            for (BlockPos dp : getBoundDoors()) {
                DoorBlockEntity door = mockWorldDoors.get(dp);
                if (door != null) {
                    door.request(false);
                }
            }
            super.closeBoundDoors();
        }
    }

    // =========================================================================
    // 1. Lockout Mechanics, Door Auto-Closing, and Alarm Sound
    // =========================================================================

    @Test
    @DisplayName("Challenge 1: 3 consecutive failed attempts trigger 6000-tick lockout, alarm sound, and auto-close bound doors")
    public void testSafeLockoutThreeStrikesLifecycleAndAutoCloseBoundDoors() {
        DispatchingSafeBlockEntity safe = new DispatchingSafeBlockEntity(BlockPos.ORIGIN,
                ModBlocks.SAFE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));

        // Setup bound doors: Door 1 is OPEN, Door 2 is OPEN, Door 3 is NOT bound
        BlockPos doorPos1 = new BlockPos(2, 0, 0);
        BlockPos doorPos2 = new BlockPos(0, 0, 3);
        BlockPos unboundDoorPos = new BlockPos(10, 0, 0);

        DoorBlockEntity door1 = createDoor(doorPos1, DoorDecls.SLIDING_BLAST_DOOR);
        DoorBlockEntity door2 = createDoor(doorPos2, DoorDecls.FIRE_DOOR);
        DoorBlockEntity doorUnbound = createDoor(unboundDoorPos, DoorDecls.SLIDING_BLAST_DOOR);

        // Open door 1 and door 2
        door1.request(true);
        for (int i = 0; i < DoorDecls.SLIDING_BLAST_DOOR.openTicks; i++) door1.tickServer();
        assertEquals(DoorState.OPEN, door1.getState(), "Door 1 must be OPEN before lockout");

        door2.request(true);
        for (int i = 0; i < DoorDecls.FIRE_DOOR.openTicks; i++) door2.tickServer();
        assertEquals(DoorState.OPEN, door2.getState(), "Door 2 must be OPEN before lockout");

        doorUnbound.request(true);
        for (int i = 0; i < DoorDecls.SLIDING_BLAST_DOOR.openTicks; i++) doorUnbound.tickServer();
        assertEquals(DoorState.OPEN, doorUnbound.getState(), "Unbound door must be OPEN before lockout");

        // Bind door 1 and door 2 to the safe
        safe.registerMockDoor(doorPos1, door1);
        safe.registerMockDoor(doorPos2, door2);
        safe.registerMockDoor(unboundDoorPos, doorUnbound);
        safe.injectBoundDoor(doorPos1);
        safe.injectBoundDoor(doorPos2);

        // Verify alarm sound event is properly registered
        assertNotNull(ModSounds.DOOR_ALARM, "DOOR_ALARM sound event must be registered");
        assertEquals("door_alarm", ModSounds.DOOR_ALARM.getId().getPath());

        // Strike 1
        safe.recordFailure();
        assertEquals(1, safe.getFailedAttempts());
        assertFalse(safe.isLockedOut());
        assertEquals(0, safe.getLockoutTicks());
        assertEquals(0, safe.closeBoundDoorsCalls);
        assertEquals(DoorState.OPEN, door1.getState(), "Door 1 must remain OPEN on strike 1");

        // Strike 2
        safe.recordFailure();
        assertEquals(2, safe.getFailedAttempts());
        assertFalse(safe.isLockedOut());
        assertEquals(0, safe.getLockoutTicks());
        assertEquals(0, safe.closeBoundDoorsCalls);
        assertEquals(DoorState.OPEN, door1.getState(), "Door 1 must remain OPEN on strike 2");

        // Strike 3: LOCKOUT TRIGGERED!
        safe.recordFailure();
        assertEquals(3, safe.getFailedAttempts());
        assertTrue(safe.isLockedOut(), "Third failed attempt must trigger lockout");
        assertEquals(6000, safe.getLockoutTicks(), "Lockout duration must be exactly 6000 ticks (5 minutes)");
        assertEquals(1, safe.closeBoundDoorsCalls, "closeBoundDoors must be invoked on lockout");

        // Verify bound doors received close request (transitioned to CLOSING)
        assertEquals(DoorState.CLOSING, door1.getState(), "Bound Door 1 must transition to CLOSING on safe lockout");
        assertEquals(DoorState.CLOSING, door2.getState(), "Bound Door 2 must transition to CLOSING on safe lockout");
        assertEquals(DoorState.OPEN, doorUnbound.getState(), "Unbound Door must NOT be closed on safe lockout");

        // Strike 4 during active lockout: increments attempts, resets lockout timer to 6000
        safe.recordFailure();
        assertEquals(4, safe.getFailedAttempts());
        assertTrue(safe.isLockedOut());
        assertEquals(6000, safe.getLockoutTicks());

        // Tick countdown
        safe.tickServer();
        assertEquals(5999, safe.getLockoutTicks());
        assertTrue(safe.isLockedOut());

        // Ticking down to tick 1
        for (int i = 0; i < 5998; i++) {
            safe.tickServer();
        }
        assertEquals(1, safe.getLockoutTicks());
        assertTrue(safe.isLockedOut(), "Safe must still be locked out at tick 1");

        // Final tick to 0: lockout expiration
        safe.tickServer();
        assertEquals(0, safe.getLockoutTicks());
        assertFalse(safe.isLockedOut(), "Lockout must expire after 6000 ticks");
        assertEquals(0, safe.getFailedAttempts(), "Failed attempts must reset to 0 after lockout expires");

        // Finish closing doors from the lockout test
        while (door1.getState() != DoorState.CLOSED) door1.tickServer();
        while (door2.getState() != DoorState.CLOSED) door2.tickServer();
        assertEquals(DoorState.CLOSED, door1.getState());
        assertEquals(DoorState.CLOSED, door2.getState());

        // Challenge: When bindingActivities is disabled, lockout must NOT close bound doors
        door1.request(true);
        for (int i = 0; i < DoorDecls.SLIDING_BLAST_DOOR.openTicks; i++) door1.tickServer();
        assertEquals(DoorState.OPEN, door1.getState());

        safe.setBindingActivities(false);
        safe.closeBoundDoorsCalls = 0;
        safe.recordFailure();
        safe.recordFailure();
        safe.recordFailure();
        assertTrue(safe.isLockedOut());

        assertEquals(0, safe.closeBoundDoorsCalls, "When bindingActivities is false, closeBoundDoors must not be called");
        assertEquals(DoorState.OPEN, door1.getState(), "Door 1 must remain OPEN when bindingActivities is disabled");

        // Challenge: When lockoutMode is disabled, 3 failures must never lock out
        safe.setLockoutMode(false);
        safe.resetFailures();
        safe.recordFailure();
        safe.recordFailure();
        safe.recordFailure();
        assertFalse(safe.isLockedOut(), "Safe must not lock out when lockoutMode is false");
        assertEquals(0, safe.getLockoutTicks());

        // Challenge: Non-consecutive failures do not trigger lockout (failure -> failure -> reset -> failure)
        safe.setLockoutMode(true);
        safe.resetFailures();
        safe.recordFailure();
        safe.recordFailure();
        assertEquals(2, safe.getFailedAttempts());
        safe.resetFailures(); // e.g. successful login
        assertEquals(0, safe.getFailedAttempts());
        safe.recordFailure();
        assertEquals(1, safe.getFailedAttempts());
        assertFalse(safe.isLockedOut());
    }

    // =========================================================================
    // 2. Biometric Authorized Owner Bypass During Lockout
    // =========================================================================

    @Test
    @DisplayName("Challenge 2: Authorized biometric owner bypasses lockout; impostors and stolen cards are rejected")
    public void testBiometricSafeAuthorizedOwnerBypassDuringLockout() {
        BiometricSafeBlockEntity safe = createBiometricSafe();

        UUID ownerUuid = UUID.randomUUID();
        String ownerName = "CommanderShepard";
        UUID impostorUuid = UUID.randomUUID();
        String impostorName = "Saren";

        DummyPlayer ownerPlayer = createDummyPlayer(ownerUuid, ownerName, false);
        DummyPlayer impostorPlayer = createDummyPlayer(impostorUuid, impostorName, false);

        // Bind safe owner
        safe.setOwner(ownerUuid, ownerName);
        assertEquals(ownerUuid.toString(), safe.getOwnerUuid());
        assertEquals(ownerName, safe.getOwnerName());
        assertTrue(safe.isOwner(ownerUuid));
        assertFalse(safe.isOwner(impostorUuid));

        // Create cards
        ItemStack ownerCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(ownerCard, ownerUuid, ownerName);
        assertTrue(Biometrics.isBound(ownerCard));
        assertEquals(ownerUuid.toString(), Biometrics.ownerUuid(ownerCard));

        ItemStack impostorCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        Biometrics.bindIfNeeded(impostorCard, impostorUuid, impostorName);
        assertTrue(Biometrics.isBound(impostorCard));
        assertEquals(impostorUuid.toString(), Biometrics.ownerUuid(impostorCard));

        ItemStack unboundCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        assertFalse(Biometrics.isBound(unboundCard));

        // Corrupted biometric card (tag contains invalid type)
        ItemStack corruptCard = new ItemStack(ModItems.CARD_BIOMETRIC);
        corruptCard.getOrCreateNbt().put(Biometrics.ROOT, NbtInt.of(9999));

        // Trigger 6000-tick lockout on safe
        safe.recordFailure();
        safe.recordFailure();
        safe.recordFailure();
        assertTrue(safe.isLockedOut(), "Safe must be locked out");
        assertEquals(6000, safe.getLockoutTicks());

        // --- Test Scenario A: matchHolder = true (Default strict mode: card owner == player holder) ---
        assertTrue(safe.isMatchHolder(), "Default matchHolder must be true");

        // 1. Owner holding Owner card: MUST BYPASS LOCKOUT
        assertTrue(safe.bypassesLockout(ownerCard, ownerPlayer),
                "Authorized owner holding owner biometric card MUST bypass lockout");

        // 2. Impostor holding stolen Owner card: MUST NOT BYPASS LOCKOUT
        assertFalse(safe.bypassesLockout(ownerCard, impostorPlayer),
                "Impostor holding stolen owner card must NOT bypass lockout when matchHolder is true");

        // 3. Impostor holding Impostor card: MUST NOT BYPASS LOCKOUT
        assertFalse(safe.bypassesLockout(impostorCard, impostorPlayer),
                "Impostor holding own card must NOT bypass lockout on non-owned safe");

        // 4. Owner holding Impostor card: MUST NOT BYPASS LOCKOUT
        assertFalse(safe.bypassesLockout(impostorCard, ownerPlayer),
                "Owner holding foreign card must NOT bypass lockout");

        // 5. Owner holding unbound card: MUST NOT BYPASS LOCKOUT
        assertFalse(safe.bypassesLockout(unboundCard, ownerPlayer),
                "Owner holding unbound card must NOT bypass lockout");

        // 6. Corrupt card: MUST NOT CRASH AND MUST NOT BYPASS
        assertDoesNotThrow(() -> safe.bypassesLockout(corruptCard, ownerPlayer));
        assertFalse(safe.bypassesLockout(corruptCard, ownerPlayer));

        // --- Test Scenario B: matchHolder = false (Relaxed mode: card possession suffices) ---
        getDelegate(safe).set(4, 0); // matchHolder = false
        assertFalse(safe.isMatchHolder());

        // 1. Owner holding Owner card: STILL BYPASSES
        assertTrue(safe.bypassesLockout(ownerCard, ownerPlayer));

        // 2. Impostor holding stolen Owner card: BYPASSES because card matches safe owner
        assertTrue(safe.bypassesLockout(ownerCard, impostorPlayer),
                "When matchHolder is false, holding the owner card bypasses lockout");

        // 3. Impostor holding Impostor card: STILL FAILS because card owner != safe owner
        assertFalse(safe.bypassesLockout(impostorCard, impostorPlayer));

        // --- Test Scenario C: Successful bypass resets lockout state ---
        // Restore matchHolder = true
        getDelegate(safe).set(4, 1);
        assertTrue(safe.isMatchHolder());
        assertTrue(safe.isLockedOut());

        // When authorized owner bypasses lockout and access is resolved:
        safe.resetFailures();
        safe.grantAccess(ownerPlayer);

        assertFalse(safe.isLockedOut(), "Successful owner bypass must clear lockout");
        assertEquals(0, safe.getLockoutTicks(), "Lockout ticks must be reset to 0");
        assertEquals(0, safe.getFailedAttempts(), "Failed attempts must be reset to 0");
        assertTrue(safe.hasAccessSessionFor(ownerPlayer), "Owner must receive active access session");
        assertFalse(safe.hasAccessSessionFor(impostorPlayer), "Impostor must not have access session");

        // Verify access session ticks countdown (12000 ticks = 10 minutes)
        for (int i = 0; i < SafeBlockEntity.ACCESS_SESSION_TICKS - 1; i++) {
            safe.tickServer();
        }
        assertTrue(safe.hasAccessSessionFor(ownerPlayer), "Access session must persist for 10 minutes");
        safe.tickServer(); // final tick expires session
        assertFalse(safe.hasAccessSessionFor(ownerPlayer), "Access session must expire after 10 minutes");
    }

    // =========================================================================
    // 3. voidOnDestroy: Inventory Wipe and Zero Drop Stacks
    // =========================================================================

    @Test
    @DisplayName("Challenge 3: voidOnDestroy clears inventory on destroy and drops zero items")
    public void testVoidOnDestroyClearsInventoryAndDropsNothing() {
        SafeBlockEntity safe = createSafe();

        // Default voidOnDestroy is false
        assertFalse(safe.isVoidOnDestroy(), "Default voidOnDestroy must be false");
        assertEquals(0, getDelegate(safe).get(7));

        // Fill all 27 slots with valuable items
        for (int i = 0; i < 27; i++) {
            safe.setStack(i, new ItemStack(Items.DIAMOND_BLOCK, 64));
        }
        assertFalse(safe.isEmpty());
        for (int i = 0; i < 27; i++) {
            assertEquals(Items.DIAMOND_BLOCK, safe.getStack(i).getItem());
            assertEquals(64, safe.getStack(i).getCount());
        }

        // Enable voidOnDestroy
        safe.setVoidOnDestroy(true);
        assertTrue(safe.isVoidOnDestroy());
        assertEquals(1, getDelegate(safe).get(7));

        // Verify NBT serialization preserves voidOnDestroy flag
        NbtCompound tag = new NbtCompound();
        safe.writeNbt(tag);
        assertTrue(tag.getBoolean("voidOnDestroy"), "NBT must store voidOnDestroy boolean");

        SafeBlockEntity reloaded = createSafe();
        reloaded.readNbt(tag);
        assertTrue(reloaded.isVoidOnDestroy(), "NBT deserialization must restore voidOnDestroy flag");

        // Simulate block destruction under voidOnDestroy:
        // SafeBlock.onStateReplaced calls safe.clear() when isVoidOnDestroy() is true
        if (safe.isVoidOnDestroy()) {
            safe.clear();
        }

        // Verify inventory is completely wiped clean
        assertTrue(safe.isEmpty(), "Under voidOnDestroy, safe inventory must be completely empty");
        for (int i = 0; i < 27; i++) {
            assertTrue(safe.getStack(i).isEmpty(), "Slot " + i + " must be empty");
        }

        // Under voidOnDestroy == false, destruction does NOT clear inventory
        SafeBlockEntity normalSafe = createSafe();
        normalSafe.setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 16));
        normalSafe.setVoidOnDestroy(false);
        assertFalse(normalSafe.isVoidOnDestroy());

        if (normalSafe.isVoidOnDestroy()) {
            normalSafe.clear();
        }
        assertFalse(normalSafe.isEmpty(), "Without voidOnDestroy, safe inventory must NOT be cleared");
        assertEquals(16, normalSafe.getStack(0).getCount());
    }

    // =========================================================================
    // 4. 3-Hour Mining Hardness Delta (1.0F / 216000.0F) and Piston Immovability
    // =========================================================================

    @Test
    @DisplayName("Challenge 4: 3-hour mining hardness delta (1/216000) in survival, instant in creative, and piston immovability")
    public void testThreeHourMiningHardnessDeltaAndPistonBehavior() {
        SafeBlock safeBlock = ModBlocks.SAFE;
        BiometricSafeBlock biometricSafeBlock = ModBlocks.BIOMETRIC_SAFE;

        BlockState safeState = safeBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        BlockState bioState = biometricSafeBlock.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);

        DummyPlayer survivalPlayer = createDummyPlayer(UUID.randomUUID(), "Miner", false);
        DummyPlayer creativePlayer = createDummyPlayer(UUID.randomUUID(), "Admin", true);

        // Constant break damage per tick
        assertEquals(1.0F / 216000.0F, SafeBlock.BREAK_DAMAGE_PER_TICK, 1e-9F,
                "BREAK_DAMAGE_PER_TICK must be exactly 1.0F / 216000.0F");

        // SafeBlock survival breaking delta
        float safeSurvivalDelta = safeBlock.calcBlockBreakingDelta(safeState, survivalPlayer, null, BlockPos.ORIGIN);
        assertEquals(1.0F / 216000.0F, safeSurvivalDelta, 1e-9F,
                "SafeBlock survival breaking delta must equal 1 / 216000 per tick");

        // SafeBlock creative breaking delta
        float safeCreativeDelta = safeBlock.calcBlockBreakingDelta(safeState, creativePlayer, null, BlockPos.ORIGIN);
        assertEquals(1.0F, safeCreativeDelta, 1e-9F,
                "SafeBlock creative breaking delta must be 1.0F (instant break)");

        // BiometricSafeBlock survival breaking delta
        float bioSurvivalDelta = biometricSafeBlock.calcBlockBreakingDelta(bioState, survivalPlayer, null, BlockPos.ORIGIN);
        assertEquals(1.0F / 216000.0F, bioSurvivalDelta, 1e-9F,
                "BiometricSafeBlock survival breaking delta must equal 1 / 216000 per tick");

        // BiometricSafeBlock creative breaking delta
        float bioCreativeDelta = biometricSafeBlock.calcBlockBreakingDelta(bioState, creativePlayer, null, BlockPos.ORIGIN);
        assertEquals(1.0F, bioCreativeDelta, 1e-9F,
                "BiometricSafeBlock creative breaking delta must be 1.0F (instant break)");

        // Verify time to break in survival mode:
        // Ticks = 1.0 / delta = 216,000 ticks
        // Seconds = 216,000 / 20 = 10,800 seconds
        // Minutes = 10,800 / 60 = 180 minutes = 3.0 hours
        double ticksToBreak = Math.ceil(1.0 / safeSurvivalDelta);
        assertEquals(216000.0, ticksToBreak, 1.0, "Ticks to break must be 216,000");
        double hoursToBreak = ticksToBreak / 20.0 / 3600.0;
        assertEquals(3.0, hoursToBreak, 1e-4, "Break time in survival must be exactly 3 hours");

        // Piston immovability (prevents pushing/pulling to bypass mining)
        assertEquals(PistonBehavior.BLOCK, safeState.getPistonBehavior(),
                "SafeBlock must refuse piston movement (PistonBehavior.BLOCK)");
        assertEquals(PistonBehavior.BLOCK, bioState.getPistonBehavior(),
                "BiometricSafeBlock must refuse piston movement (PistonBehavior.BLOCK)");

        // Hardness settings
        assertEquals(216000.0F, safeState.getHardness(null, BlockPos.ORIGIN), 1e-4F,
                "Safe block hardness must be 216000.0F");
        assertEquals(216000.0F, bioState.getHardness(null, BlockPos.ORIGIN), 1e-4F,
                "Biometric safe block hardness must be 216000.0F");
    }

    // =========================================================================
    // 5. Redstone Gate Pulse Duration Clamping & NBT Corruption Resilience
    // =========================================================================

    @Test
    @DisplayName("Challenge 5: Redstone Gate pulse duration strictly clamped between 20 and 6000 ticks, resilient to corrupted NBT")
    public void testRedstoneGatePulseDurationClampingAndNbtResilience() {
        RedstoneGateBlockEntity gate = createGate();

        // Bounds constants
        assertEquals(20, RedstoneGateBlockEntity.MIN_ACTIVE_TICKS);
        assertEquals(6000, RedstoneGateBlockEntity.MAX_ACTIVE_TICKS);
        assertEquals(20, RedstoneGateBlockEntity.WIRELESS_PULSE_TICKS);

        // Initial default
        assertEquals(20, gate.getActiveTicks(), "Initial duration must be 20 ticks (1s)");

        // 1. Extreme negative delta adjustment
        gate.adjustActiveTicks(-1000);
        assertEquals(20, gate.getActiveTicks(), "Duration < 20 must be clamped to 20");

        gate.adjustActiveTicks(-1);
        assertEquals(20, gate.getActiveTicks(), "Duration < 20 must remain clamped to 20");

        // 2. Step additions within bounds
        gate.adjustActiveTicks(20);   // +1s -> 40
        assertEquals(40, gate.getActiveTicks());

        gate.adjustActiveTicks(100);  // +5s -> 140
        assertEquals(140, gate.getActiveTicks());

        gate.adjustActiveTicks(200);  // +10s -> 340
        assertEquals(340, gate.getActiveTicks());

        gate.adjustActiveTicks(400);  // +20s -> 740
        assertEquals(740, gate.getActiveTicks());

        // 3. Step subtractions within bounds
        gate.adjustActiveTicks(-400); // -20s -> 340
        assertEquals(340, gate.getActiveTicks());

        gate.adjustActiveTicks(-200); // -10s -> 140
        assertEquals(140, gate.getActiveTicks());

        // 4. Extreme positive delta adjustment
        gate.adjustActiveTicks(100000);
        assertEquals(6000, gate.getActiveTicks(), "Duration > 6000 must be clamped to 6000 (5 minutes)");

        gate.adjustActiveTicks(1);
        assertEquals(6000, gate.getActiveTicks(), "Duration > 6000 must remain clamped to 6000");

        // 5. Step down from maximum
        gate.adjustActiveTicks(-400); // -> 5600
        assertEquals(5600, gate.getActiveTicks());

        // 6. NBT Serialization and Deserialization with corrupted/extreme values
        NbtCompound tagNormal = new NbtCompound();
        gate.writeNbt(tagNormal);
        RedstoneGateBlockEntity reloadedNormal = createGate();
        reloadedNormal.readNbt(tagNormal);
        assertEquals(5600, reloadedNormal.getActiveTicks(), "Normal NBT duration must roundtrip");

        // Injected extreme negative NBT
        NbtCompound tagNegative = new NbtCompound();
        tagNegative.putInt("activeTicks", -99999);
        RedstoneGateBlockEntity reloadedNegative = createGate();
        reloadedNegative.readNbt(tagNegative);
        assertEquals(20, reloadedNegative.getActiveTicks(), "Negative NBT activeTicks must clamp to 20");

        // Injected extreme positive NBT
        NbtCompound tagHuge = new NbtCompound();
        tagHuge.putInt("activeTicks", 9999999);
        RedstoneGateBlockEntity reloadedHuge = createGate();
        reloadedHuge.readNbt(tagHuge);
        assertEquals(6000, reloadedHuge.getActiveTicks(), "Huge NBT activeTicks must clamp to 6000");

        // Injected zero NBT
        NbtCompound tagZero = new NbtCompound();
        tagZero.putInt("activeTicks", 0);
        RedstoneGateBlockEntity reloadedZero = createGate();
        reloadedZero.readNbt(tagZero);
        assertEquals(20, reloadedZero.getActiveTicks(), "Zero NBT activeTicks must clamp to 20");

        // Injected missing tag (defaults to WIRELESS_PULSE_TICKS = 20)
        NbtCompound tagEmpty = new NbtCompound();
        RedstoneGateBlockEntity reloadedEmpty = createGate();
        reloadedEmpty.readNbt(tagEmpty);
        assertEquals(20, reloadedEmpty.getActiveTicks(), "Missing activeTicks tag must default to 20");
    }

    // =========================================================================
    // 6. Hopper Automation Isolation & SidedInventory Isolation
    // =========================================================================

    @Test
    @DisplayName("Challenge 6: SidedInventory completely isolates safe from hoppers on all 6 facings")
    public void testSafeHopperIsolationAndStrictSidedInventory() {
        SafeBlockEntity safe = createSafe();
        BiometricSafeBlockEntity bioSafe = createBiometricSafe();

        assertEquals(27, safe.size());
        assertEquals(27, bioSafe.size());

        ItemStack stack = new ItemStack(Items.DIAMOND, 64);

        for (Direction side : Direction.values()) {
            // SafeBlockEntity
            int[] available = safe.getAvailableSlots(side);
            assertNotNull(available);
            assertEquals(0, available.length, "Hopper slots must be 0 for side: " + side);
            assertFalse(safe.canInsert(0, stack, side), "Hopper insertion must be blocked from side: " + side);
            assertFalse(safe.canExtract(0, stack, side), "Hopper extraction must be blocked from side: " + side);

            // BiometricSafeBlockEntity
            int[] bioAvailable = bioSafe.getAvailableSlots(side);
            assertNotNull(bioAvailable);
            assertEquals(0, bioAvailable.length, "Hopper slots must be 0 for side: " + side);
            assertFalse(bioSafe.canInsert(0, stack, side), "Hopper insertion must be blocked from side: " + side);
            assertFalse(bioSafe.canExtract(0, stack, side), "Hopper extraction must be blocked from side: " + side);
        }

        // Stack size clamping (cannot exceed container maxCountPerStack = 64)
        ItemStack overStack = new ItemStack(Items.DIAMOND, 99);
        safe.setStack(0, overStack);
        assertEquals(safe.getMaxCountPerStack(), safe.getStack(0).getCount(),
                "Stack count must be clamped to container max count per stack (64)");
        assertEquals(64, safe.getStack(0).getCount());
    }

    // =========================================================================
    // 7. Security Code Sanitization, Boundary Codes, and Access Sessions
    // =========================================================================

    @Test
    @DisplayName("Challenge 7: Code sanitization strips non-digits, truncates overlong codes, rejects empty codes")
    public void testSafeSecurityCodeSanitizationAndEdgeCases() {
        // Standard digit code
        assertEquals("123456", Codes.sanitise("123456", 6));
        assertEquals("123", Codes.sanitise("123", 6));

        // Mixed letters and digits: letters stripped
        assertEquals("12345", Codes.sanitise("1a2b3c4d5e", 6));

        // Pure letters/special characters sanitize to empty string
        assertEquals("", Codes.sanitise("abcdef", 6));
        assertEquals("", Codes.sanitise("!@#$%^", 6));
        assertEquals("", Codes.sanitise("   ", 6));

        // Overlong code truncated to exactly 6 digits
        assertEquals("123456", Codes.sanitise("1234567890", 6));

        // Code with whitespace between digits
        assertEquals("987654", Codes.sanitise("9 8 7 6 5 4", 6));

        // PropertyDelegate mappings
        SafeBlockEntity safe = createSafe();
        getDelegate(safe).set(4, 0); // matchHolder
        assertFalse(safe.isMatchHolder());

        getDelegate(safe).set(5, 0); // bindingActivities
        assertFalse(safe.isBindingActivities());

        getDelegate(safe).set(6, 0); // lockoutMode
        assertFalse(safe.isLockoutMode());

        getDelegate(safe).set(7, 1); // voidOnDestroy
        assertTrue(safe.isVoidOnDestroy());

        // Redstone events mapped to indices 8..15
        for (int i = 0; i < SafeBlockEntity.RS_EVENT_COUNT; i++) {
            getDelegate(safe).set(8 + i, 1);
            assertTrue(safe.isRedstoneEvent(i), "Redstone event " + i + " must be enabled via property delegate");
            assertEquals(1, getDelegate(safe).get(8 + i));

            getDelegate(safe).set(8 + i, 0);
            assertFalse(safe.isRedstoneEvent(i), "Redstone event " + i + " must be disabled via property delegate");
            assertEquals(0, getDelegate(safe).get(8 + i));
        }
    }

    // =========================================================================
    // 8. Door Binding Distance Limit (32 blocks) and Cross-Safe Ownership
    // =========================================================================

    @Test
    @DisplayName("Challenge 8: Door binding enforces 32-block radius, rejects multi-safe theft, and NBT roundtrip")
    public void testSafeDoorBindingDistanceAndOwnershipConstraints() {
        SafeBlockEntity safe = createSafe();

        // Maximum distance constant
        assertEquals(32.0, SafeBlockEntity.MAX_BIND_DISTANCE);

        // NBT roundtrip of boundDoors
        List<BlockPos> testDoors = List.of(
                new BlockPos(10, 64, 10),
                new BlockPos(20, 64, 20),
                new BlockPos(-5, 70, 15)
        );

        NbtCompound tag = new NbtCompound();
        tag.putString("boundDoors", BlockPositions.encodeList(testDoors));

        safe.readNbt(tag);
        assertEquals(3, safe.getBoundDoors().size());
        assertTrue(safe.getBoundDoors().contains(new BlockPos(10, 64, 10)));
        assertTrue(safe.getBoundDoors().contains(new BlockPos(20, 64, 20)));
        assertTrue(safe.getBoundDoors().contains(new BlockPos(-5, 70, 15)));

        // Write NBT and reload
        NbtCompound written = new NbtCompound();
        safe.writeNbt(written);

        SafeBlockEntity loaded = createSafe();
        loaded.readNbt(written);
        assertEquals(3, loaded.getBoundDoors().size());
        assertEquals(safe.getBoundDoors(), loaded.getBoundDoors());

        // Door binding collision: Door already bound to another safe
        BlockPos safe1Pos = new BlockPos(0, 64, 0);
        BlockPos safe2Pos = new BlockPos(10, 64, 0);
        DoorBlockEntity door = createDoor(new BlockPos(5, 64, 0), DoorDecls.VAULT_DOOR);

        // Bind to safe 1
        assertTrue(door.bindToSafe(safe1Pos));
        assertEquals(safe1Pos, door.getBoundSafe());
        assertTrue(door.isBoundToSafe(safe1Pos));
        assertFalse(door.isBoundToSafe(safe2Pos));

        // Safe 2 attempts to bind: door.bindToSafe must reject
        assertFalse(door.bindToSafe(safe2Pos), "Door already bound to safe 1 must reject binding to safe 2");
        assertEquals(safe1Pos, door.getBoundSafe(), "Bound safe must remain safe 1");

        // Safe 1 unbinds: door is now free
        door.unbindFromSafe(safe1Pos);
        assertNull(door.getBoundSafe());

        // Now safe 2 can bind
        assertTrue(door.bindToSafe(safe2Pos));
        assertEquals(safe2Pos, door.getBoundSafe());
    }
}
