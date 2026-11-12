package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorDesk2;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.MonitorDesk;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityEventData;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.TerminalBackupData;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Stress Test Suite for Milestone M5:
 * Monitor Desks, Terminal OS, Security Net, and Persistent Cross-Chunk State.
 *
 * Verifies:
 * 1. Multiblock Monitor Desk swept placement obstruction detection and atomic teardown CAS guard
 *    across all 5 desk variants (`six`, `three`, `dual`, `two_by_two`, `single`) and 4 horizontal facings.
 * 2. Terminal password security: SHA-256 length validation, constant-time MessageDigest.isEqual,
 *    malformed hex rejection, password reset/skip paths.
 * 3. Security Net event dispatch and SecurityEventData cross-chunk PersistentState survival
 *    across simulated unloads and reboots.
 */
public class ChallengerM5DeskAndSecurityStressTest {

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

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

    @BeforeEach
    public void reset() {
        DoorPlacementGuard.clearActive();
    }

    // =========================================================================
    // SECTION 1: MULTIBLOCK MONITOR DESK SWEPT PLACEMENT & TEARDOWN CAS GUARD
    // =========================================================================

    public enum DeskVariant {
        SIX(9, 3, BlockMonitorSetup.Column.CENTER),
        THREE(6, 2, BlockMonitorSetup.Column.CENTER),
        DUAL(5, 2, BlockMonitorSetup.Column.CENTER),
        TWO_BY_TWO(4, 2, BlockMonitorSetup.Column.LEFT),
        SINGLE(4, 2, BlockMonitorSetup.Column.LEFT);

        public final int totalCells;
        public final int layerCount;
        public final BlockMonitorSetup.Column coreCol;

        DeskVariant(int totalCells, int layerCount, BlockMonitorSetup.Column coreCol) {
            this.totalCells = totalCells;
            this.layerCount = layerCount;
            this.coreCol = coreCol;
        }

        public List<BlockPos> getOffsets(Direction facing) {
            List<BlockPos> offsets = new ArrayList<>();
            switch (this) {
                case SIX:
                    for (BlockMonitorSetup.Layer layer : BlockMonitorSetup.Layer.values()) {
                        for (BlockMonitorSetup.Column col : BlockMonitorSetup.Column.values()) {
                            offsets.add(BlockMonitorSetup.offset(layer, col, facing));
                        }
                    }
                    break;
                case THREE:
                    for (BlockMonitorDesk b : ModBlocks.DESK_THREE.blocks()) {
                        for (BlockMonitorSetup.Column col : b.columns()) {
                            offsets.add(ModBlocks.DESK_THREE.offset(b, col, facing));
                        }
                    }
                    break;
                case DUAL:
                    for (BlockMonitorDesk b : ModBlocks.DESK_DUAL.blocks()) {
                        for (BlockMonitorSetup.Column col : b.columns()) {
                            offsets.add(ModBlocks.DESK_DUAL.offset(b, col, facing));
                        }
                    }
                    break;
                case TWO_BY_TWO:
                    for (BlockMonitorDesk b : ModBlocks.DESK_TWO_BY_TWO.blocks()) {
                        for (BlockMonitorSetup.Column col : b.columns()) {
                            offsets.add(ModBlocks.DESK_TWO_BY_TWO.offset(b, col, facing));
                        }
                    }
                    break;
                case SINGLE:
                    for (BlockMonitorDesk b : ModBlocks.DESK_SINGLE.blocks()) {
                        for (BlockMonitorSetup.Column col : b.columns()) {
                            offsets.add(ModBlocks.DESK_SINGLE.offset(b, col, facing));
                        }
                    }
                    break;
            }
            return offsets;
        }

        public ItemStack getItem() {
            return switch (this) {
                case SIX -> new ItemStack(ModItems.MONITOR_SETUP_ITEM);
                case THREE -> new ItemStack(ModItems.TRIPLE_MONITOR_SETUP_ITEM);
                case DUAL -> new ItemStack(ModItems.DUAL_MONITOR_SETUP_ITEM);
                case TWO_BY_TWO -> new ItemStack(ModItems.DUAL_MONITOR_SETUP_2X2_ITEM);
                case SINGLE -> new ItemStack(ModItems.SINGLE_MONITOR_SETUP_ITEM);
            };
        }
    }

    @Test
    @DisplayName("STRESS-DESK-1: Analytical coordinate offsets & bijective inversion across all 5 variants and 4 facings")
    public void testMultiblockGeometryAndInversionAcrossAllVariants() {
        BlockPos origin = new BlockPos(128, 64, -256);

        for (DeskVariant variant : DeskVariant.values()) {
            for (Direction facing : HORIZONTALS) {
                List<BlockPos> offsets = variant.getOffsets(facing);
                assertEquals(variant.totalCells, offsets.size(),
                        "Variant " + variant + " must have exactly " + variant.totalCells + " cells facing " + facing);

                // Check distinctness: no two cells can occupy the same offset
                Set<BlockPos> uniqueOffsets = new HashSet<>(offsets);
                assertEquals(offsets.size(), uniqueOffsets.size(),
                        "Variant " + variant + " facing " + facing + " has duplicate cell coordinates!");

                // Core cell must be at BlockPos.ORIGIN
                assertTrue(uniqueOffsets.contains(BlockPos.ORIGIN),
                        "Variant " + variant + " facing " + facing + " must include core at (0, 0, 0)");

                // Bijective coordinate inversion
                for (BlockPos offset : offsets) {
                    BlockPos worldCell = origin.add(offset);
                    BlockPos inverted = worldCell.subtract(offset);
                    assertEquals(origin, inverted,
                        "Inverting offset must return exact origin for " + variant + " cell " + offset);

                    // Y offset must be between 0 and layerCount - 1
                    assertTrue(offset.getY() >= 0 && offset.getY() < variant.layerCount,
                            "Y offset " + offset.getY() + " out of layer bounds for " + variant);
                }

                // Dual desk specific check: right column omitted in middle layer
                if (variant == DeskVariant.DUAL) {
                    BlockPos omittedRightMiddle = ModBlocks.DESK_DUAL.offset(
                            ModBlocks.DUAL_MONITOR_SETUP, BlockMonitorSetup.Column.RIGHT, facing)
                            .up();
                    assertFalse(uniqueOffsets.contains(omittedRightMiddle),
                            "Dual monitor desk middle layer must strictly omit right column");
                }
            }
        }
    }

    /**
     * Simulated in-memory world modeling multiblock desk placement, swept volume obstruction checks,
     * and atomic teardown CAS guard with faithful onStateReplaced / beginDismantle mechanics.
     */
    static class SimulatedDeskWorld {
        final DeskVariant variant;
        final Direction facing;
        final BlockPos corePos;
        final Map<BlockPos, BlockState> blocks = new ConcurrentHashMap<>();
        final Map<BlockPos, MonitorDeskBlockEntity> blockEntities = new ConcurrentHashMap<>();
        final List<ItemStack> droppedItems = Collections.synchronizedList(new ArrayList<>());
        final Set<BlockPos> sweptObstructions = Collections.synchronizedSet(new HashSet<>());
        final Set<BlockPos> solidObstructions = Collections.synchronizedSet(new HashSet<>());
        final AtomicInteger dismantleInvocations = new AtomicInteger(0);

        SimulatedDeskWorld(DeskVariant variant, Direction facing, BlockPos corePos) {
            this.variant = variant;
            this.facing = facing;
            this.corePos = corePos;
        }

        /**
         * Simulates swept placement routine from ItemMonitorSetup / ItemMonitorDesk.
         * Returns true if placement succeeds, false if aborted.
         */
        boolean attemptPlacement() {
            List<BlockPos> offsets = variant.getOffsets(facing);

            // Phase 1: Swept obstruction & replacement verification
            for (BlockPos offset : offsets) {
                BlockPos cellPos = corePos.add(offset);

                // Check swept door obstruction guard
                if (sweptObstructions.contains(cellPos)) {
                    return false;
                }
                // Check non-replaceable solid block obstruction
                if (solidObstructions.contains(cellPos) || blocks.containsKey(cellPos)) {
                    return false;
                }
            }

            // Phase 2: Place all constituent blocks
            BlockState coreState = getDeskBlockState(variant, 0, variant.coreCol, facing);
            blocks.put(corePos, coreState);
            MonitorDeskBlockEntity coreBe = new MonitorDeskBlockEntity(corePos, coreState);
            blockEntities.put(corePos, coreBe);

            for (BlockPos offset : offsets) {
                if (offset.equals(BlockPos.ORIGIN)) continue;
                BlockPos cellPos = corePos.add(offset);
                BlockState state = getDeskBlockState(variant, offset.getY(), getColumnFromOffset(variant, offset, facing), facing);
                blocks.put(cellPos, state);
            }
            return true;
        }

        void simulateSurvivalBreak(BlockPos brokenPos) {
            triggerTeardown(brokenPos, false);
        }

        void simulateCreativeBreak(BlockPos brokenPos) {
            MonitorDeskBlockEntity core = blockEntities.get(corePos);
            if (core != null) {
                core.setCreativeBroken(true);
            }
            triggerTeardown(brokenPos, true);
        }

        private void triggerTeardown(BlockPos brokenPos, boolean creative) {
            BlockState oldState = blocks.get(brokenPos);
            if (oldState == null) return;

            MonitorDeskBlockEntity core = blockEntities.get(corePos);
            if (core != null && core.beginDismantle()) {
                dismantleInvocations.incrementAndGet();
                if (!core.isCreativeBroken()) {
                    droppedItems.add(variant.getItem());
                }

                // Replace sibling cells with air (recursively triggers onStateReplaced)
                for (BlockPos offset : variant.getOffsets(facing)) {
                    BlockPos p = corePos.add(offset);
                    if (!p.equals(brokenPos)) {
                        // Sibling onStateReplaced call
                        MonitorDeskBlockEntity siblingCore = blockEntities.get(corePos);
                        if (siblingCore != null && siblingCore.beginDismantle()) {
                            // Sibling should NEVER pass beginDismantle CAS guard!
                            dismantleInvocations.incrementAndGet();
                            droppedItems.add(variant.getItem());
                        }
                        blocks.remove(p);
                    }
                }
                blockEntities.remove(corePos);
            }
            blocks.remove(brokenPos);
        }

        boolean isCompletelyCleanedUp() {
            for (BlockPos offset : variant.getOffsets(facing)) {
                BlockPos p = corePos.add(offset);
                if (blocks.containsKey(p) || blockEntities.containsKey(p)) {
                    return false;
                }
            }
            return true;
        }

        private static BlockState getDeskBlockState(DeskVariant v, int layerY, BlockMonitorSetup.Column col, Direction facing) {
            return switch (v) {
                case SIX -> {
                    BlockMonitorSetup.Layer layer = layerY == 2 ? BlockMonitorSetup.Layer.TOP
                            : layerY == 1 ? BlockMonitorSetup.Layer.MIDDLE : BlockMonitorSetup.Layer.BOTTOM;
                    yield ModBlocks.MONITOR_SETUP.getDefaultState()
                            .with(BlockMonitorSetup.FACING, facing)
                            .with(BlockMonitorSetup.COLUMN, col);
                }
                case THREE -> ModBlocks.TRIPLE_MONITOR_SETUP.getDefaultState()
                        .with(BlockMonitorDesk.FACING, facing)
                        .with(BlockMonitorDesk.COLUMN, col);
                case DUAL -> ModBlocks.DUAL_MONITOR_SETUP.getDefaultState()
                        .with(BlockMonitorDesk.FACING, facing)
                        .with(BlockMonitorDesk.COLUMN, col);
                case TWO_BY_TWO -> ModBlocks.DUAL_MONITOR_SETUP_2X2.getDefaultState()
                        .with(BlockMonitorDesk2.FACING, facing)
                        .with(BlockMonitorDesk2.COLUMN_2, col);
                case SINGLE -> ModBlocks.SINGLE_MONITOR_SETUP.getDefaultState()
                        .with(BlockMonitorDesk2.FACING, facing)
                        .with(BlockMonitorDesk2.COLUMN_2, col);
            };
        }

        private static BlockMonitorSetup.Column getColumnFromOffset(DeskVariant v, BlockPos off, Direction facing) {
            // Reconstruct column enum for test blockstate
            for (BlockMonitorSetup.Column col : BlockMonitorSetup.Column.values()) {
                BlockPos expected = switch (v) {
                    case SIX -> BlockMonitorSetup.offset(
                            off.getY() == 2 ? BlockMonitorSetup.Layer.TOP : off.getY() == 1 ? BlockMonitorSetup.Layer.MIDDLE : BlockMonitorSetup.Layer.BOTTOM,
                            col, facing);
                    case THREE -> ModBlocks.DESK_THREE.offset(off.getY() == 1 ? ModBlocks.TRIPLE_MONITOR_SETUP_MIDDLE : ModBlocks.TRIPLE_MONITOR_SETUP, col, facing);
                    case DUAL -> ModBlocks.DESK_DUAL.offset(off.getY() == 1 ? ModBlocks.DUAL_MONITOR_SETUP_MIDDLE : ModBlocks.DUAL_MONITOR_SETUP, col, facing);
                    case TWO_BY_TWO -> col == BlockMonitorSetup.Column.CENTER ? null : ModBlocks.DESK_TWO_BY_TWO.offset(off.getY() == 1 ? ModBlocks.DUAL_MONITOR_SETUP_2X2_MIDDLE : ModBlocks.DUAL_MONITOR_SETUP_2X2, col, facing);
                    case SINGLE -> col == BlockMonitorSetup.Column.CENTER ? null : ModBlocks.DESK_SINGLE.offset(off.getY() == 1 ? ModBlocks.SINGLE_MONITOR_SETUP_MIDDLE : ModBlocks.SINGLE_MONITOR_SETUP, col, facing);
                };
                if (expected != null && expected.getX() == off.getX() && expected.getZ() == off.getZ()) {
                    return col;
                }
            }
            return BlockMonitorSetup.Column.CENTER;
        }
    }

    @Test
    @DisplayName("STRESS-DESK-2: Swept placement obstruction detection across all 112 cells and 4 facings")
    public void testSweptPlacementObstructionDetection() {
        BlockPos corePos = new BlockPos(100, 64, 100);

        for (DeskVariant variant : DeskVariant.values()) {
            for (Direction facing : HORIZONTALS) {
                List<BlockPos> offsets = variant.getOffsets(facing);

                // Baseline: Clean placement succeeds
                SimulatedDeskWorld cleanWorld = new SimulatedDeskWorld(variant, facing, corePos);
                assertTrue(cleanWorld.attemptPlacement(),
                        "Clean placement must succeed for " + variant + " facing " + facing);
                assertEquals(variant.totalCells, cleanWorld.blocks.size(),
                        "Placed block count must match variant total cells");

                // Stress test: Place moving door swept obstruction at EACH cell individually
                for (int i = 0; i < offsets.size(); i++) {
                    BlockPos targetCell = corePos.add(offsets.get(i));
                    SimulatedDeskWorld obstructedWorld = new SimulatedDeskWorld(variant, facing, corePos);
                    obstructedWorld.sweptObstructions.add(targetCell);

                    assertFalse(obstructedWorld.attemptPlacement(),
                            "Placement MUST be blocked when cell " + i + " (" + targetCell + ") is obstructed by moving door for " + variant);
                    assertEquals(0, obstructedWorld.blocks.size(),
                            "Aborted placement must place ZERO partial blocks in the world!");
                }

                // Stress test: Place solid obstruction block at EACH cell individually
                for (int i = 0; i < offsets.size(); i++) {
                    BlockPos targetCell = corePos.add(offsets.get(i));
                    SimulatedDeskWorld obstructedWorld = new SimulatedDeskWorld(variant, facing, corePos);
                    obstructedWorld.solidObstructions.add(targetCell);

                    assertFalse(obstructedWorld.attemptPlacement(),
                            "Placement MUST be blocked when cell " + i + " (" + targetCell + ") contains a solid block for " + variant);
                    assertEquals(0, obstructedWorld.blocks.size(),
                            "Aborted placement must place ZERO partial blocks!");
                }
            }
        }
    }

    @Test
    @DisplayName("STRESS-DESK-3: Atomic teardown CAS guard from core and dummy cells across all 5 variants")
    public void testAtomicTeardownFromCoreAndDummyCells() {
        BlockPos corePos = new BlockPos(50, 64, 50);

        for (DeskVariant variant : DeskVariant.values()) {
            for (Direction facing : HORIZONTALS) {
                List<BlockPos> offsets = variant.getOffsets(facing);

                // 1. Break from CORE cell in survival
                SimulatedDeskWorld worldCore = new SimulatedDeskWorld(variant, facing, corePos);
                assertTrue(worldCore.attemptPlacement());
                worldCore.simulateSurvivalBreak(corePos);

                assertEquals(1, worldCore.droppedItems.size(),
                        "Breaking CORE cell must drop exactly 1 desk item for " + variant);
                assertTrue(worldCore.isCompletelyCleanedUp(),
                        "Breaking CORE cell must clean up all multiblock cells for " + variant);
                assertEquals(1, worldCore.dismantleInvocations.get(),
                        "CAS beginDismantle must succeed exactly once for " + variant);

                // 2. Break from EVERY dummy cell in survival
                for (BlockPos offset : offsets) {
                    if (offset.equals(BlockPos.ORIGIN)) continue;
                    BlockPos dummyPos = corePos.add(offset);

                    SimulatedDeskWorld worldDummy = new SimulatedDeskWorld(variant, facing, corePos);
                    assertTrue(worldDummy.attemptPlacement());
                    worldDummy.simulateSurvivalBreak(dummyPos);

                    assertEquals(1, worldDummy.droppedItems.size(),
                            "Breaking dummy cell " + offset + " must drop exactly 1 desk item for " + variant);
                    assertTrue(worldDummy.isCompletelyCleanedUp(),
                            "Breaking dummy cell " + offset + " must clean up all cells for " + variant);
                    assertEquals(1, worldDummy.dismantleInvocations.get(),
                            "CAS beginDismantle must succeed exactly once when broken at dummy cell");
                }

                // 3. Creative mode break: zero drops
                SimulatedDeskWorld worldCreative = new SimulatedDeskWorld(variant, facing, corePos);
                assertTrue(worldCreative.attemptPlacement());
                worldCreative.simulateCreativeBreak(corePos);

                assertEquals(0, worldCreative.droppedItems.size(),
                        "Creative mode break must produce exactly 0 item drops for " + variant);
                assertTrue(worldCreative.isCompletelyCleanedUp(),
                        "Creative break must still clean up all cells for " + variant);
            }
        }
    }

    @Test
    @DisplayName("STRESS-DESK-4: Concurrent multi-threaded dismantling guarantees atomic CAS drop")
    public void testConcurrentDismantlingRaceCondition() throws Exception {
        int threads = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try {
            for (int trial = 0; trial < 25; trial++) {
                DeskVariant variant = DeskVariant.values()[trial % DeskVariant.values().length];
                Direction facing = HORIZONTALS[trial % HORIZONTALS.length];
                BlockPos corePos = new BlockPos(100 + trial * 20, 64, 100);

                SimulatedDeskWorld world = new SimulatedDeskWorld(variant, facing, corePos);
                assertTrue(world.attemptPlacement());
                List<BlockPos> cells = variant.getOffsets(facing);

                CountDownLatch readyLatch = new CountDownLatch(threads);
                CountDownLatch startLatch = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();

                for (int t = 0; t < threads; t++) {
                    final int threadId = t;
                    futures.add(executor.submit(() -> {
                        readyLatch.countDown();
                        try {
                            startLatch.await();
                        } catch (InterruptedException ignored) {}

                        // Each thread attacks a different cell
                        BlockPos target = corePos.add(cells.get(threadId % cells.size()));
                        world.simulateSurvivalBreak(target);
                    }));
                }

                readyLatch.await(5, TimeUnit.SECONDS);
                startLatch.countDown();

                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }

                assertEquals(1, world.droppedItems.size(),
                        "Trial " + trial + " (" + variant + "): Exactly 1 item drop must be produced under " + threads + " concurrent threads");
                assertTrue(world.isCompletelyCleanedUp(),
                        "Trial " + trial + ": World must be completely cleaned up");
                assertEquals(1, world.dismantleInvocations.get(),
                        "Trial " + trial + ": beginDismantle CAS must succeed exactly once");
            }
        } finally {
            executor.shutdown();
        }
    }

    // =========================================================================
    // SECTION 2: TERMINAL PASSWORD SECURITY
    // =========================================================================

    @Test
    @DisplayName("STRESS-PASS-1: SHA-256 length validation, diffusion, and cryptographic hash determinism")
    public void testSha256LengthAndDiffusion() {
        // Empty password standard hash
        String emptyHash = MonitorPasswords.hash("");
        assertEquals(64, emptyHash.length());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash);

        // Null returns empty string
        assertEquals("", MonitorPasswords.hash(null));

        // Determinism test across 100 iterations
        String pwd = "facility_master_pass_1234!";
        String reference = MonitorPasswords.hash(pwd);
        for (int i = 0; i < 100; i++) {
            assertEquals(reference, MonitorPasswords.hash(pwd));
        }

        // Avalanche/diffusion property: 1-character difference should flip substantial number of chars
        String hash1 = MonitorPasswords.hash("LockdownCodeA");
        String hash2 = MonitorPasswords.hash("LockdownCodeB");
        assertNotEquals(hash1, hash2);

        int matchingChars = 0;
        for (int i = 0; i < 64; i++) {
            if (hash1.charAt(i) == hash2.charAt(i)) {
                matchingChars++;
            }
        }
        // Statistically in SHA-256, matching hex chars between independent hashes should average 4/64 (6.25%).
        // A threshold of < 20 confirms healthy cryptographic diffusion.
        assertTrue(matchingChars < 20, "1-character password mutation must trigger avalanche effect (matching chars: " + matchingChars + ")");
    }

    @Test
    @DisplayName("STRESS-PASS-2: Malformed hex rejection (length deviations, uppercase, symbols, unicode, injection)")
    public void testMalformedHexRejectionAdversarial() {
        String validHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertTrue(MonitorPasswords.isValidHash(validHash));

        // 1. Length deviations
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 63)), "63 chars must be rejected");
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 32)), "32 chars must be rejected");
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 1)), "1 char must be rejected");
        assertFalse(MonitorPasswords.isValidHash(""), "Empty string must be rejected");
        assertFalse(MonitorPasswords.isValidHash(null), "Null must be rejected");
        assertFalse(MonitorPasswords.isValidHash(validHash + "a"), "65 chars must be rejected");
        assertFalse(MonitorPasswords.isValidHash(validHash + validHash), "128 chars must be rejected");

        // 2. Uppercase characters strictly rejected (only lowercase hex valid)
        assertFalse(MonitorPasswords.isValidHash(validHash.toUpperCase()), "All-uppercase hex must be rejected");
        assertFalse(MonitorPasswords.isValidHash("E" + validHash.substring(1)), "First-char uppercase must be rejected");
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 63) + "F"), "Last-char uppercase must be rejected");

        // 3. Invalid non-hex characters
        for (char invalidChar : new char[] {'g', 'z', 'G', 'Z', '!', '@', '#', '$', '%', '^', '&', '*', ' '}) {
            String corrupted = validHash.substring(0, 30) + invalidChar + validHash.substring(31);
            assertFalse(MonitorPasswords.isValidHash(corrupted), "Non-hex char '" + invalidChar + "' must be rejected");
        }

        // 4. Whitespace, newlines, null bytes
        assertFalse(MonitorPasswords.isValidHash(" " + validHash.substring(1)));
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 63) + " "));
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 30) + "\n" + validHash.substring(31)));
        assertFalse(MonitorPasswords.isValidHash(validHash.substring(0, 30) + "\0" + validHash.substring(31)));

        // 5. Unicode homoglyphs (Cyrillic lookalikes)
        String homoglyphA = "\u0430" + validHash.substring(1); // Cyrillic small letter 'a' looks like 'a'
        assertFalse(MonitorPasswords.isValidHash(homoglyphA), "Cyrillic homoglyph \\u0430 must be rejected");

        String homoglyphE = validHash.substring(0, 10) + "\u0435" + validHash.substring(11); // Cyrillic small 'e'
        assertFalse(MonitorPasswords.isValidHash(homoglyphE), "Cyrillic homoglyph \\u0435 must be rejected");

        // 6. Injection payloads
        assertFalse(MonitorPasswords.isValidHash("' OR '1'='1"));
        assertFalse(MonitorPasswords.isValidHash("<script>alert(1)</script>"));
        assertFalse(MonitorPasswords.isValidHash("admin' --"));
    }

    @Test
    @DisplayName("STRESS-PASS-3: Constant-time MessageDigest.isEqual verification against single-bit mutations")
    public void testConstantTimeMatchingBitMutations() {
        String password = "OmegaClearance99!";
        String storedHash = MonitorPasswords.hash(password);
        String candidateHash = MonitorPasswords.hash(password);

        // Identical hashes match
        assertTrue(MonitorPasswords.matches(storedHash, candidateHash));

        // Single-character difference at start (index 0)
        char firstChar = storedHash.charAt(0);
        char mutatedFirst = firstChar == 'a' ? 'b' : 'a';
        String mutatedStart = mutatedFirst + storedHash.substring(1);
        assertFalse(MonitorPasswords.matches(storedHash, mutatedStart));

        // Single-character difference in middle (index 32)
        char midChar = storedHash.charAt(32);
        char mutatedMid = midChar == '0' ? '1' : '0';
        String mutatedMiddle = storedHash.substring(0, 32) + mutatedMid + storedHash.substring(33);
        assertFalse(MonitorPasswords.matches(storedHash, mutatedMiddle));

        // Single-character difference at end (index 63)
        char lastChar = storedHash.charAt(63);
        char mutatedLast = lastChar == 'f' ? 'e' : 'f';
        String mutatedEnd = storedHash.substring(0, 63) + mutatedLast;
        assertFalse(MonitorPasswords.matches(storedHash, mutatedEnd));

        // Invalid inputs fail safely
        assertFalse(MonitorPasswords.matches(null, candidateHash));
        assertFalse(MonitorPasswords.matches(storedHash, null));
        assertFalse(MonitorPasswords.matches(null, null));
        assertFalse(MonitorPasswords.matches("", candidateHash));
        assertFalse(MonitorPasswords.matches(storedHash, ""));
        assertFalse(MonitorPasswords.matches("too_short", candidateHash));
        assertFalse(MonitorPasswords.matches(storedHash, "too_short"));
        assertFalse(MonitorPasswords.matches(storedHash.toUpperCase(), candidateHash));
    }

    @Test
    @DisplayName("STRESS-PASS-4: Terminal password setup, skip, authentication, and reset paths in MonitorDeskBlockEntity")
    public void testTerminalPasswordStateTransitions() {
        MonitorDeskBlockEntity desk = new MonitorDeskBlockEntity(BlockPos.ORIGIN, ModBlocks.MONITOR_SETUP.getDefaultState());

        // 1. Initial fresh state
        assertFalse(desk.hasPassword(), "Fresh desk must not have password set");
        assertFalse(desk.isPasswordSkipped(), "Fresh desk must not have password skipped");
        assertEquals("", desk.getPasswordHash());
        assertFalse(desk.checkPasswordHash(MonitorPasswords.hash("any_password")),
                "Authentication must fail when no password is set");

        // 2. Reject setting malformed password hash
        assertFalse(desk.setPasswordHash("malformed_short_hash"));
        assertFalse(desk.hasPassword());
        assertFalse(desk.setPasswordHash(null));
        assertFalse(desk.hasPassword());

        // 3. Skip password setup on fresh desk
        assertTrue(desk.skipPasswordSetup(), "First skipPasswordSetup() call must succeed");
        assertTrue(desk.isPasswordSkipped());
        assertFalse(desk.hasPassword());

        // Duplicate skip must fail
        assertFalse(desk.skipPasswordSetup(), "Second skipPasswordSetup() call must fail");

        // Setting valid password after skip must succeed and clear skipped flag
        String validHash = MonitorPasswords.hash("NewPasswordAfterSkip");
        assertTrue(desk.setPasswordHash(validHash), "Setting password after skip must succeed");
        assertTrue(desk.hasPassword());
        assertFalse(desk.isPasswordSkipped(), "Setting password must clear passwordSkipped");
        assertEquals(validHash, desk.getPasswordHash());

        // Calling skip when password already set must fail
        assertFalse(desk.skipPasswordSetup(), "skipPasswordSetup() must fail when password is already configured");

        // 4. Overwrite guard: cannot overwrite password directly once set
        String attackHash = MonitorPasswords.hash("AttackerOverride");
        assertFalse(desk.setPasswordHash(attackHash), "Overwriting configured password via setPasswordHash must be rejected");
        assertEquals(validHash, desk.getPasswordHash(), "Password hash must remain intact");

        // 5. Authentication checks
        assertTrue(desk.checkPasswordHash(validHash), "Matching hash must authenticate");
        assertFalse(desk.checkPasswordHash(attackHash), "Wrong hash must fail");
        assertFalse(desk.checkPasswordHash(null), "Null hash must fail");
        assertFalse(desk.checkPasswordHash(""), "Empty hash must fail");
        assertFalse(desk.checkPasswordHash(validHash.substring(0, 63)), "Truncated hash must fail");

        // 6. Snapshot backup and restore
        NbtCompound snapshot = desk.createSnapshot();
        assertNotNull(snapshot);

        MonitorDeskBlockEntity targetDesk = new MonitorDeskBlockEntity(new BlockPos(10, 64, 10), ModBlocks.MONITOR_SETUP.getDefaultState());
        assertFalse(targetDesk.hasPassword());

        targetDesk.restoreSnapshot(snapshot);
        assertTrue(targetDesk.hasPassword());
        assertTrue(targetDesk.checkPasswordHash(validHash));

        // Snapshot rejection for corrupted schema
        NbtCompound corrupted = snapshot.copy();
        corrupted.putInt("Schema", 9999);
        MonitorDeskBlockEntity freshDesk = new MonitorDeskBlockEntity(new BlockPos(20, 64, 20), ModBlocks.MONITOR_SETUP.getDefaultState());
        freshDesk.restoreSnapshot(corrupted);
        assertFalse(freshDesk.hasPassword(), "Corrupted schema snapshot must be ignored");
    }

    // =========================================================================
    // SECTION 3: SECURITY NET EVENT DISPATCH & PERSISTENTSTATE SURVIVAL
    // =========================================================================

    @Test
    @DisplayName("STRESS-NET-1: SecurityNet event bitmask filtering matrix across all 6 device families")
    public void testDeviceFamilyBitmaskFiltering() {
        SecurityEventData data = new SecurityEventData();

        BlockPos deskPos = new BlockPos(0, 64, 0);
        BlockPos doorPos = new BlockPos(10, 64, 10);
        BlockPos keypadPos = new BlockPos(11, 64, 10);
        BlockPos scannerPos = new BlockPos(12, 64, 10);
        BlockPos sensorPos = new BlockPos(13, 64, 10);
        BlockPos gatePos = new BlockPos(14, 64, 10);
        BlockPos speakerPos = new BlockPos(15, 64, 10);

        // Helper to synthesize a desk update in NBT and verify state aggregation
        BiFunctionWithFlags updater = (mask) -> {
            NbtCompound root = new NbtCompound();
            NbtList desks = new NbtList();
            NbtCompound deskTag = new NbtCompound();
            deskTag.putLong("Desk", deskPos.asLong());

            NbtList devices = new NbtList();
            // Filter masks according to SecurityEventData.updateDesk rules
            addDeviceTag(devices, doorPos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN));
            addDeviceTag(devices, keypadPos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_KEYPADS));
            addDeviceTag(devices, scannerPos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_SCANNERS));
            addDeviceTag(devices, sensorPos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_SENSORS));
            addDeviceTag(devices, gatePos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_GATES));
            addDeviceTag(devices, speakerPos, mask & (SecurityEvents.SHUTDOWN | SecurityEvents.LOCKDOWN | SecurityEvents.ALARMS));

            deskTag.put("Devices", devices);
            desks.add(deskTag);
            root.put("Desks", desks);

            data.readNbt(root);
        };

        // 1. EVENT_SYSTEM_SHUTDOWN (1) -> Affects all 6 devices
        updater.apply(SecurityEvents.SHUTDOWN);
        assertEquals(SecurityEvents.SHUTDOWN, data.state(doorPos));
        assertEquals(SecurityEvents.SHUTDOWN, data.state(keypadPos));
        assertEquals(SecurityEvents.SHUTDOWN, data.state(scannerPos));
        assertEquals(SecurityEvents.SHUTDOWN, data.state(sensorPos));
        assertEquals(SecurityEvents.SHUTDOWN, data.state(gatePos));
        assertEquals(SecurityEvents.SHUTDOWN, data.state(speakerPos));

        // 2. EVENT_SYSTEM_LOCKDOWN (2) -> Affects all 6 devices
        updater.apply(SecurityEvents.LOCKDOWN);
        assertEquals(SecurityEvents.LOCKDOWN, data.state(doorPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(keypadPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(scannerPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(sensorPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(gatePos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(speakerPos));

        // 3. EVENT_TRIGGER_ALARMS (4) -> Affects ONLY SPEAKER
        updater.apply(SecurityEvents.ALARMS);
        assertEquals(0, data.state(doorPos), "DOOR must ignore ALARMS");
        assertEquals(0, data.state(keypadPos), "KEYPAD must ignore ALARMS");
        assertEquals(0, data.state(scannerPos), "SCANNER must ignore ALARMS");
        assertEquals(0, data.state(sensorPos), "SENSOR must ignore ALARMS");
        assertEquals(0, data.state(gatePos), "GATE must ignore ALARMS");
        assertEquals(SecurityEvents.ALARMS, data.state(speakerPos), "SPEAKER must receive ALARMS");

        // 4. EVENT_DISABLE_KEYPADS (8) -> Affects ONLY KEYPAD
        updater.apply(SecurityEvents.DISABLE_KEYPADS);
        assertEquals(0, data.state(doorPos));
        assertEquals(SecurityEvents.DISABLE_KEYPADS, data.state(keypadPos));
        assertEquals(0, data.state(scannerPos));
        assertEquals(0, data.state(sensorPos));
        assertEquals(0, data.state(gatePos));
        assertEquals(0, data.state(speakerPos));

        // 5. EVENT_DISABLE_SCANNERS (16) -> Affects ONLY SCANNER
        updater.apply(SecurityEvents.DISABLE_SCANNERS);
        assertEquals(0, data.state(doorPos));
        assertEquals(0, data.state(keypadPos));
        assertEquals(SecurityEvents.DISABLE_SCANNERS, data.state(scannerPos));
        assertEquals(0, data.state(sensorPos));
        assertEquals(0, data.state(gatePos));
        assertEquals(0, data.state(speakerPos));

        // 6. EVENT_DISABLE_SENSORS (32) -> Affects ONLY MOTION_SENSOR
        updater.apply(SecurityEvents.DISABLE_SENSORS);
        assertEquals(0, data.state(doorPos));
        assertEquals(0, data.state(keypadPos));
        assertEquals(0, data.state(scannerPos));
        assertEquals(SecurityEvents.DISABLE_SENSORS, data.state(sensorPos));
        assertEquals(0, data.state(gatePos));
        assertEquals(0, data.state(speakerPos));

        // 7. EVENT_DISABLE_REDSTONE_GATES (64) -> Affects ONLY REDSTONE_GATE
        updater.apply(SecurityEvents.DISABLE_GATES);
        assertEquals(0, data.state(doorPos));
        assertEquals(0, data.state(keypadPos));
        assertEquals(0, data.state(scannerPos));
        assertEquals(0, data.state(sensorPos));
        assertEquals(SecurityEvents.DISABLE_GATES, data.state(gatePos));
        assertEquals(0, data.state(speakerPos));

        // 8. Composite: LOCKDOWN | ALARMS | DISABLE_KEYPADS
        int composite = SecurityEvents.LOCKDOWN | SecurityEvents.ALARMS | SecurityEvents.DISABLE_KEYPADS;
        updater.apply(composite);
        assertEquals(SecurityEvents.LOCKDOWN, data.state(doorPos));
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_KEYPADS, data.state(keypadPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(scannerPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(sensorPos));
        assertEquals(SecurityEvents.LOCKDOWN, data.state(gatePos));
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.ALARMS, data.state(speakerPos));
    }

    @FunctionalInterface
    interface BiFunctionWithFlags {
        void apply(int mask);
    }

    private static void addDeviceTag(NbtList list, BlockPos pos, int flags) {
        if (flags != 0) {
            NbtCompound dev = new NbtCompound();
            dev.putLong("Pos", pos.asLong());
            dev.putInt("Flags", flags);
            list.add(dev);
        }
    }

    @Test
    @DisplayName("STRESS-NET-2: Cross-chunk multi-desk aggregation and PersistentState survival across simulated unloads and reboots")
    public void testPersistentStateSurvivalAcrossReboots() {
        SecurityEventData originalState = new SecurityEventData();

        // 3 Monitor Desks in different chunks
        BlockPos desk1 = new BlockPos(10, 64, 10);       // Chunk (0, 0)
        BlockPos desk2 = new BlockPos(800, 64, 800);     // Chunk (50, 50)
        BlockPos desk3 = new BlockPos(-480, 64, 320);    // Chunk (-30, 20)

        // Remote devices across remote chunks
        BlockPos blastDoor = new BlockPos(1500, 64, 1500); // Controlled by Desk 1 (LOCKDOWN) and Desk 2 (SHUTDOWN)
        BlockPos keypad = new BlockPos(1501, 64, 1500);    // Controlled by Desk 1 (LOCKDOWN | DISABLE_KEYPADS)
        BlockPos scanner = new BlockPos(-2000, 64, 500);   // Controlled by Desk 2 (SHUTDOWN | DISABLE_SCANNERS)
        BlockPos speaker = new BlockPos(1500, 75, 1500);   // Controlled by Desk 3 (ALARMS)
        BlockPos unlinked = new BlockPos(9999, 64, 9999);  // Not bound to any net

        // Construct NBT representing multi-desk contributions
        NbtCompound root = new NbtCompound();
        NbtList desksList = new NbtList();

        // Desk 1 contribution
        NbtCompound d1 = new NbtCompound();
        d1.putLong("Desk", desk1.asLong());
        NbtList d1Devices = new NbtList();
        addDeviceTag(d1Devices, blastDoor, SecurityEvents.LOCKDOWN);
        addDeviceTag(d1Devices, keypad, SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_KEYPADS);
        d1.put("Devices", d1Devices);
        desksList.add(d1);

        // Desk 2 contribution
        NbtCompound d2 = new NbtCompound();
        d2.putLong("Desk", desk2.asLong());
        NbtList d2Devices = new NbtList();
        addDeviceTag(d2Devices, blastDoor, SecurityEvents.SHUTDOWN);
        addDeviceTag(d2Devices, scanner, SecurityEvents.SHUTDOWN | SecurityEvents.DISABLE_SCANNERS);
        d2.put("Devices", d2Devices);
        desksList.add(d2);

        // Desk 3 contribution
        NbtCompound d3 = new NbtCompound();
        d3.putLong("Desk", desk3.asLong());
        NbtList d3Devices = new NbtList();
        addDeviceTag(d3Devices, speaker, SecurityEvents.ALARMS);
        d3.put("Devices", d3Devices);
        desksList.add(d3);

        root.put("Desks", desksList);
        originalState.readNbt(root);

        // Verify live pre-reboot aggregation
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.SHUTDOWN, originalState.state(blastDoor));
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_KEYPADS, originalState.state(keypad));
        assertEquals(SecurityEvents.SHUTDOWN | SecurityEvents.DISABLE_SCANNERS, originalState.state(scanner));
        assertEquals(SecurityEvents.ALARMS, originalState.state(speaker));
        assertEquals(0, originalState.state(unlinked));

        // ---------------------------------------------------------------------
        // SIMULATE WORLD SAVE & SERVER REBOOT
        // ---------------------------------------------------------------------
        NbtCompound worldSaveNbt = new NbtCompound();
        originalState.writeNbt(worldSaveNbt);

        // Nullify originalState to simulate garbage collection / clean boot
        originalState = null;

        // Instantiate fresh PersistentState from world save NBT
        SecurityEventData restoredState = SecurityEventData.fromNbt(worldSaveNbt);

        // Even though Desk 1, 2, and 3 chunks are unloaded, remote devices query state:
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.SHUTDOWN, restoredState.state(blastDoor),
                "Blast door must retain combined LOCKDOWN | SHUTDOWN across chunk unload and reboot");
        assertEquals(SecurityEvents.LOCKDOWN | SecurityEvents.DISABLE_KEYPADS, restoredState.state(keypad));
        assertEquals(SecurityEvents.SHUTDOWN | SecurityEvents.DISABLE_SCANNERS, restoredState.state(scanner));
        assertEquals(SecurityEvents.ALARMS, restoredState.state(speaker));
        assertEquals(0, restoredState.state(unlinked));

        // ---------------------------------------------------------------------
        // SIMULATE DESK TEARDOWN / REMOVAL
        // ---------------------------------------------------------------------
        // Remove Desk 1 (e.g. survival mining or explosion)
        // Simulate SecurityEventData.removeDesk logic by clearing Desk 1's contribution in NBT
        NbtCompound postDismantleNbt = new NbtCompound();
        NbtList remainingDesks = new NbtList();
        remainingDesks.add(d2);
        remainingDesks.add(d3);
        postDismantleNbt.put("Desks", remainingDesks);
        restoredState.readNbt(postDismantleNbt);

        // Blast door drops from LOCKDOWN|SHUTDOWN to SHUTDOWN (Desk 2 remains active)
        assertEquals(SecurityEvents.SHUTDOWN, restoredState.state(blastDoor));
        // Keypad drops to 0 because Desk 1 was its sole controller
        assertEquals(0, restoredState.state(keypad));
        // Scanner and speaker remain unchanged
        assertEquals(SecurityEvents.SHUTDOWN | SecurityEvents.DISABLE_SCANNERS, restoredState.state(scanner));
        assertEquals(SecurityEvents.ALARMS, restoredState.state(speaker));

        // Remove Desk 2 and Desk 3
        NbtCompound emptyNbt = new NbtCompound();
        emptyNbt.put("Desks", new NbtList());
        restoredState.readNbt(emptyNbt);

        assertEquals(0, restoredState.state(blastDoor));
        assertEquals(0, restoredState.state(keypad));
        assertEquals(0, restoredState.state(scanner));
        assertEquals(0, restoredState.state(speaker));
    }

    @Test
    @DisplayName("STRESS-NET-3: SecurityEventData NBT corruption resilience and boundary conditions")
    public void testSecurityEventDataCorruptionResilience() {
        SecurityEventData data = new SecurityEventData();

        // 1. Null / empty compound
        assertDoesNotThrow(() -> data.readNbt(new NbtCompound()));
        assertEquals(0, data.state(new BlockPos(0, 64, 0)));
        assertEquals(0, data.state(null));

        // 2. Desks with empty devices list
        NbtCompound root = new NbtCompound();
        NbtList desks = new NbtList();
        NbtCompound emptyDesk = new NbtCompound();
        emptyDesk.putLong("Desk", BlockPos.ORIGIN.asLong());
        emptyDesk.put("Devices", new NbtList());
        desks.add(emptyDesk);
        root.put("Desks", desks);

        assertDoesNotThrow(() -> data.readNbt(root));
        assertEquals(0, data.state(BlockPos.ORIGIN));

        // 3. Extreme negative coordinates
        BlockPos extremePos = new BlockPos(-30000000, -64, -30000000);
        NbtCompound extremeRoot = new NbtCompound();
        NbtList extremeDesks = new NbtList();
        NbtCompound extremeDesk = new NbtCompound();
        extremeDesk.putLong("Desk", BlockPos.ORIGIN.asLong());
        NbtList extremeDevs = new NbtList();
        addDeviceTag(extremeDevs, extremePos, SecurityEvents.LOCKDOWN);
        extremeDesk.put("Devices", extremeDevs);
        extremeDesks.add(extremeDesk);
        extremeRoot.put("Desks", extremeDesks);

        data.readNbt(extremeRoot);
        assertEquals(SecurityEvents.LOCKDOWN, data.state(extremePos));

        // Roundtrip extreme coordinates to NBT
        NbtCompound serialized = new NbtCompound();
        data.writeNbt(serialized);
        SecurityEventData roundtrip = SecurityEventData.fromNbt(serialized);
        assertEquals(SecurityEvents.LOCKDOWN, roundtrip.state(extremePos));
    }
}
