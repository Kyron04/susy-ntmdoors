package com.ntmdoors.test.door;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.DoorPart;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorPartBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorPlacementGuard;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical challenger stress tests for Milestone M2 Iteration 2:
 * 1. Teardown dismantling from dummy or core cell: removes all cells, stack depth <= 2, exactly 1 drop in survival.
 * 2. Creative mining: leaves exactly 0 item drops.
 * 3. Explosion multi-cell breaks: exactly 1 item drop overall, 0 duplicate items.
 * 4. Concurrent dismantle race conditions: atomic drop guarantee.
 * 5. Full parametric test across all 22 doors and all 4 horizontal orientations.
 */
public class ChallengerM2TeardownAndDropStressTest {

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

    @BeforeEach
    public void reset() {
        DoorPlacementGuard.clearActive();
    }

    /**
     * High-fidelity in-memory world simulating BlockDoorGeneric onBreak and onStateReplaced semantics.
     * Records dropped items cleanly without referencing unmapped vanilla EntityType / Items classes.
     */
    static class SimulatedDoorWorld {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, Object> blockEntities = new HashMap<>();
        final List<BlockDoorGeneric> droppedBlocks = Collections.synchronizedList(new ArrayList<>());
        final BlockDoorGeneric doorBlock;
        final DoorDecl decl;
        final Direction facing;
        final BlockPos corePos;
        final AtomicInteger maxCallDepth = new AtomicInteger(0);
        final AtomicInteger totalOnStateReplacedCalls = new AtomicInteger(0);
        private int currentDepth = 0;

        SimulatedDoorWorld(BlockDoorGeneric doorBlock, DoorDecl decl, Direction facing, BlockPos corePos) {
            this.doorBlock = doorBlock;
            this.decl = decl;
            this.facing = facing;
            this.corePos = corePos;

            // Setup core cell
            BlockState coreState = doorBlock.getDefaultState()
                    .with(BlockDoorGeneric.FACING, facing)
                    .with(BlockDoorGeneric.PART, DoorPart.CORE);
            blocks.put(corePos, coreState);
            DoorBlockEntity coreBe = new DoorBlockEntity(null, corePos, coreState);
            coreBe.setDecl(decl);
            coreBe.setSecurity(doorBlock.isSecurity());
            coreBe.setLocked(doorBlock.isLocked());
            blockEntities.put(corePos, coreBe);

            // Setup dummy cells
            BlockState dummyState = doorBlock.getDefaultState()
                    .with(BlockDoorGeneric.FACING, facing)
                    .with(BlockDoorGeneric.PART, DoorPart.DUMMY);
            for (BlockPos off : decl.getCells(facing)) {
                if (BlockPos.ORIGIN.equals(off)) continue;
                BlockPos p = corePos.add(off);
                blocks.put(p, dummyState);
                DoorPartBlockEntity partBe = new DoorPartBlockEntity(p, dummyState);
                partBe.setCorePos(corePos);
                blockEntities.put(p, partBe);
            }
        }

        DoorBlockEntity getCore(BlockPos pos, BlockState state) {
            if (state == null) return null;
            if (state.get(BlockDoorGeneric.PART) == DoorPart.CORE) {
                Object be = blockEntities.get(pos);
                return be instanceof DoorBlockEntity ? (DoorBlockEntity) be : null;
            }
            Object be = blockEntities.get(pos);
            if (be instanceof DoorPartBlockEntity part && part.getCorePos() != null) {
                Object coreBe = blockEntities.get(part.getCorePos());
                return coreBe instanceof DoorBlockEntity ? (DoorBlockEntity) coreBe : null;
            }
            return null;
        }

        /**
         * Simulates survival player mining block at pos.
         */
        void mineSurvival(BlockPos pos) {
            BlockState state = blocks.get(pos);
            if (state == null) return;
            // In survival, player.isCreative() is false, so creativeBroken remains false.
            // onBreak super does nothing to drops.
            // Minecraft then replaces block with AIR:
            setBlockState(pos, null);
        }

        /**
         * Simulates creative player mining block at pos.
         */
        void mineCreative(BlockPos pos) {
            BlockState state = blocks.get(pos);
            if (state == null) return;
            // BlockDoorGeneric.onBreak sets creativeBroken = true
            DoorBlockEntity core = getCore(pos, state);
            if (core != null) {
                core.setCreativeBroken(true);
            }
            setBlockState(pos, null);
        }

        /**
         * Simulates explosion destroying a collection of positions.
         */
        void triggerExplosion(Collection<BlockPos> blastPositions) {
            // Step 1: Vanilla calculates drops via getDroppedStacks for all destroyed blocks
            for (BlockPos p : blastPositions) {
                BlockState state = blocks.get(p);
                if (state != null && state.isOf(doorBlock)) {
                    // Must be empty list to eliminate duplicate drops
                    assertEquals(0, doorBlock.getDroppedStacks(state, null).size(),
                            "getDroppedStacks must strictly return emptyList() for all door blocks");
                }
            }

            // Step 2: Vanilla replaces each destroyed block with AIR
            for (BlockPos p : blastPositions) {
                setBlockState(p, null);
            }
        }

        /**
         * Simulates BlockDoorGeneric.onStateReplaced logic exactly.
         */
        void setBlockState(BlockPos pos, BlockState newState) {
            currentDepth++;
            totalOnStateReplacedCalls.incrementAndGet();
            if (currentDepth > maxCallDepth.get()) {
                maxCallDepth.set(currentDepth);
            }

            BlockState oldState = blocks.get(pos);

            if (oldState != null && (newState == null || !oldState.isOf(newState.getBlock()))) {
                DoorBlockEntity core = getCore(pos, oldState);
                if (core != null && core.beginDismantle()) {
                    core.unbindFromControllers();
                    if (!core.isCreativeBroken()) {
                        droppedBlocks.add(doorBlock);
                    }
                    BlockPos cPos = core.getPos();
                    Direction f = oldState.get(BlockDoorGeneric.FACING);
                    for (BlockPos off : decl.getCells(f)) {
                        BlockPos p = cPos.add(off);
                        BlockState s = blocks.get(p);
                        if (!p.equals(pos) && s != null && s.isOf(doorBlock)) {
                            setBlockState(p, null);
                        }
                    }
                }
                // Super onStateReplaced removes block entity
                blockEntities.remove(pos);
            }

            if (newState == null) {
                blocks.remove(pos);
            } else {
                blocks.put(pos, newState);
            }
            currentDepth--;
        }

        boolean isCompletelyCleanedUp() {
            for (BlockPos off : decl.getCells(facing)) {
                BlockPos p = corePos.add(off);
                if (blocks.containsKey(p) || blockEntities.containsKey(p)) {
                    return false;
                }
            }
            return true;
        }
    }

    // =========================================================================
    // 1. SURVIVAL DISMANTLING (CORE AND DUMMY CELLS)
    // =========================================================================

    @Test
    @DisplayName("STRESS-DROP-1: Survival mining CORE cell removes all cells and drops exactly 1 item across all 22 doors")
    public void testSurvivalDismantleFromCoreAcrossAllDoors() {
        for (DoorDecl decl : DoorDecls.ALL) {
            BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
            assertNotNull(doorBlock, "Door block must exist for " + decl.name);

            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockPos corePos = new BlockPos(10, 64, 10);
                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);

                world.mineSurvival(corePos);

                assertTrue(world.isCompletelyCleanedUp(),
                        "All cells must be removed when CORE is mined in " + decl.name + " facing " + facing);
                assertTrue(world.maxCallDepth.get() <= 2,
                        "Call depth must not exceed 2 (was " + world.maxCallDepth.get() + ")");
                assertEquals(1, world.droppedBlocks.size(),
                        "Survival mining CORE must drop exactly 1 item for " + decl.name + " facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("STRESS-DROP-2: Survival mining DUMMY cell removes all cells and drops exactly 1 item across all 22 doors")
    public void testSurvivalDismantleFromDummyAcrossAllDoors() {
        for (DoorDecl decl : DoorDecls.ALL) {
            BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
            assertNotNull(doorBlock);

            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockPos corePos = new BlockPos(100, 64, 100);
                BlockPos[] cells = decl.getCells(facing);

                // Test first dummy, middle dummy, and last dummy
                int[] sampleIndices = {1, cells.length / 2, cells.length - 1};
                for (int idx : sampleIndices) {
                    if (idx < 0 || idx >= cells.length) continue;
                    BlockPos off = cells[idx];
                    if (BlockPos.ORIGIN.equals(off)) continue; // skip core

                    SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
                    BlockPos dummyPos = corePos.add(off);

                    world.mineSurvival(dummyPos);

                    assertTrue(world.isCompletelyCleanedUp(),
                            "All cells must be removed when dummy #" + idx + " is mined in " + decl.name);
                    assertTrue(world.maxCallDepth.get() <= 2,
                            "Call depth must not exceed 2");
                    assertEquals(1, world.droppedBlocks.size(),
                            "Survival mining dummy #" + idx + " must drop exactly 1 item for " + decl.name);
                }
            }
        }
    }

    // =========================================================================
    // 2. CREATIVE MINING (0 ITEM DROPS)
    // =========================================================================

    @Test
    @DisplayName("STRESS-DROP-3: Creative mining CORE cell removes all cells and leaves 0 item drops across all 22 doors")
    public void testCreativeDismantleFromCoreAcrossAllDoors() {
        for (DoorDecl decl : DoorDecls.ALL) {
            BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
            assertNotNull(doorBlock);

            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockPos corePos = new BlockPos(0, 64, 0);
                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);

                world.mineCreative(corePos);

                assertTrue(world.isCompletelyCleanedUp(),
                        "Creative mining CORE must clean up all cells for " + decl.name);
                assertEquals(0, world.droppedBlocks.size(),
                        "Creative mining CORE must produce 0 drops for " + decl.name);
            }
        }
    }

    @Test
    @DisplayName("STRESS-DROP-4: Creative mining DUMMY cell marks core creativeBroken and leaves 0 item drops")
    public void testCreativeDismantleFromDummyAcrossAllDoors() {
        for (DoorDecl decl : DoorDecls.ALL) {
            BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
            assertNotNull(doorBlock);

            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockPos corePos = new BlockPos(0, 64, 0);
                BlockPos[] cells = decl.getCells(facing);

                // Pick last dummy cell
                BlockPos dummyPos = null;
                for (int i = cells.length - 1; i >= 0; i--) {
                    if (!BlockPos.ORIGIN.equals(cells[i])) {
                        dummyPos = corePos.add(cells[i]);
                        break;
                    }
                }
                if (dummyPos == null) continue;

                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
                world.mineCreative(dummyPos);

                assertTrue(world.isCompletelyCleanedUp(),
                        "Creative mining dummy must clean up all cells for " + decl.name);
                assertEquals(0, world.droppedBlocks.size(),
                        "Creative mining dummy must produce 0 drops for " + decl.name);
            }
        }
    }

    // =========================================================================
    // 3. EXPLOSIONS AND MULTI-CELL SIMULTANEOUS BREAKS
    // =========================================================================

    @Test
    @DisplayName("STRESS-DROP-5: Explosions destroying multiple cells drop exactly 1 item and never duplicate")
    public void testExplosionMultiCellDrops() {
        // Test small door (Fire Door 6 cells), medium door (Sliding Blast Door 28 cells), and giant door (Transition Seal 624 cells)
        DoorDecl[] testDecls = {DoorDecls.FIRE_DOOR, DoorDecls.VAULT_DOOR, DoorDecls.SLIDING_BLAST_DOOR, DoorDecls.TRANSITION_SEAL};

        for (DoorDecl decl : testDecls) {
            BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
            BlockPos corePos = new BlockPos(50, 64, 50);
            Direction facing = Direction.NORTH;
            BlockPos[] cells = decl.getCells(facing);

            // Sub-test A: Explosion destroying only 2 dummy cells
            {
                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
                List<BlockPos> blast = Arrays.asList(corePos.add(cells[cells.length - 1]), corePos.add(cells[cells.length - 2]));
                world.triggerExplosion(blast);

                assertTrue(world.isCompletelyCleanedUp());
                assertEquals(1, world.droppedBlocks.size(),
                        "Explosion hitting 2 cells must drop exactly 1 item in " + decl.name);
            }

            // Sub-test B: Explosion destroying CORE + 5 dummy cells
            {
                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
                List<BlockPos> blast = new ArrayList<>();
                blast.add(corePos);
                for (int i = 1; i < Math.min(cells.length, 6); i++) {
                    blast.add(corePos.add(cells[i]));
                }
                world.triggerExplosion(blast);

                assertTrue(world.isCompletelyCleanedUp());
                assertEquals(1, world.droppedBlocks.size(),
                        "Explosion hitting CORE + dummies must drop exactly 1 item in " + decl.name);
            }

            // Sub-test C: Massive explosion destroying ALL cells simultaneously
            {
                SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
                List<BlockPos> blast = new ArrayList<>();
                for (BlockPos off : cells) {
                    blast.add(corePos.add(off));
                }
                world.triggerExplosion(blast);

                assertTrue(world.isCompletelyCleanedUp());
                assertEquals(1, world.droppedBlocks.size(),
                        "Massive explosion destroying all " + cells.length + " cells must drop exactly 1 item (no dupes) in " + decl.name);
            }
        }
    }

    // =========================================================================
    // 4. CONCURRENT / RACE CONDITION DISMANTLE SIMULATION
    // =========================================================================

    @Test
    @DisplayName("STRESS-DROP-6: Concurrent dismantling across multiple threads guarantees exactly 1 drop")
    public void testConcurrentDismantleAtomicDrop() throws Exception {
        DoorDecl decl = DoorDecls.VAULT_DOOR; // 55 cells
        BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
        BlockPos corePos = new BlockPos(0, 64, 0);
        Direction facing = Direction.NORTH;
        BlockPos[] cells = decl.getCells(facing);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int trial = 0; trial < 20; trial++) {
            SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);
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
                    int cellIndex = (threadId * 5) % cells.length;
                    BlockPos target = corePos.add(cells[cellIndex]);
                    world.mineSurvival(target);
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }

            assertEquals(1, world.droppedBlocks.size(),
                    "Trial " + trial + ": Exactly 1 item drop must be produced despite 10 concurrent dismantling threads");
            assertTrue(world.isCompletelyCleanedUp(),
                    "Trial " + trial + ": World must be completely cleaned up");
        }

        executor.shutdown();
    }

    // =========================================================================
    // 5. CORRUPTED OR PARTIAL FOOTPRINT DISMANTLE
    // =========================================================================

    @Test
    @DisplayName("STRESS-DROP-7: Partially corrupted or pre-cleared multiblock dismantles safely without throwing")
    public void testPartialFootprintSafeTeardown() {
        DoorDecl decl = DoorDecls.ROUND_AIRLOCK_DOOR; // 35 cells
        BlockDoorGeneric doorBlock = ModDoors.DOORS.get(decl.name);
        BlockPos corePos = new BlockPos(20, 64, 20);
        Direction facing = Direction.SOUTH;
        BlockPos[] cells = decl.getCells(facing);

        SimulatedDoorWorld world = new SimulatedDoorWorld(doorBlock, decl, facing, corePos);

        // Pre-remove half the dummy blocks without calling dismantle (simulating partial corruption/voiding),
        // making sure the CORE cell at (0,0,0) remains intact.
        for (int i = 0; i < cells.length / 2; i++) {
            if (BlockPos.ORIGIN.equals(cells[i])) continue;
            BlockPos p = corePos.add(cells[i]);
            world.blocks.remove(p);
            world.blockEntities.remove(p);
        }

        // Now pick a remaining dummy cell that is not the core
        BlockPos remainingDummy = null;
        for (int i = cells.length - 1; i >= 0; i--) {
            if (!BlockPos.ORIGIN.equals(cells[i]) && world.blocks.containsKey(corePos.add(cells[i]))) {
                remainingDummy = corePos.add(cells[i]);
                break;
            }
        }
        assertNotNull(remainingDummy);

        final BlockPos targetPos = remainingDummy;
        assertDoesNotThrow(() -> world.mineSurvival(targetPos),
                "Teardown must not throw when multiblock is partially corrupted");

        assertEquals(1, world.droppedBlocks.size(),
                "Partially corrupted multiblock should still produce exactly 1 item drop on dismantle");
        assertTrue(world.isCompletelyCleanedUp(),
                "All remaining cells must be cleared");
    }
}
