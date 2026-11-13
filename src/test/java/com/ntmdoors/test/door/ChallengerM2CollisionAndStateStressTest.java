package com.ntmdoors.test.door;

import com.ntmdoors.block.door.DoorVoxelShapes;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.util.Facings;
import net.minecraft.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial stress testing for Milestone M2 by Challenger M2-1:
 * Focus Areas:
 * 1. Dynamic collision box slicing under all 4 facings (NORTH, SOUTH, EAST, WEST) across non-trivial doors.
 * 2. Swept-volume collision and bounding box overlap under sub-tick open fractions (0.0, 0.25, 0.5, 0.75, 1.0).
 * 3. Concurrency / rapid state toggle stress: rapid open/close triggering, emergency overrides, mid-stroke reversals.
 */
public class ChallengerM2CollisionAndStateStressTest {

    private static final double EPS = 1e-5;

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

    private DoorBlockEntity createTestDoor(DoorDecl decl) {
        DoorBlockEntity be = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        be.setDecl(decl);
        return be;
    }

    // =========================================================================
    // FOCUS AREA 1: Dynamic Collision Box Slicing across All 4 Facings
    // =========================================================================

    @Test
    @DisplayName("Verify Vault Door collision box slicing across all 4 facings and sub-tick fractions")
    public void testVaultDoorAllFacingsSlicing() {
        DoorDecl vault = DoorDecls.VAULT_DOOR;
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        for (Direction f : facings) {
            BlockPos[] cells = vault.getCells(f);
            assertEquals(55, cells.length, "Vault door must have exactly 55 cells for facing " + f);

            for (float frac : fractions) {
                Box[] boxes = vault.getBoxes(f, frac, true, true);
                assertNotNull(boxes, "Boxes must not be null for facing " + f + " at fraction " + frac);
                assertTrue(boxes.length > 0, "Vault door must have collision boxes at fraction " + frac);

                for (Box b : boxes) {
                    assertFalse(Double.isNaN(b.minX) || Double.isNaN(b.maxX), "Box X must not be NaN");
                    assertFalse(Double.isNaN(b.minY) || Double.isNaN(b.maxY), "Box Y must not be NaN");
                    assertFalse(Double.isNaN(b.minZ) || Double.isNaN(b.maxZ), "Box Z must not be NaN");
                    assertTrue(b.maxX >= b.minX - EPS, "Box maxX >= minX");
                    assertTrue(b.maxY >= b.minY - EPS, "Box maxY >= minY");
                    assertTrue(b.maxZ >= b.minZ - EPS, "Box maxZ >= minZ");
                }

                // Slicing across all 55 cells
                int solidCells = 0;
                for (BlockPos cell : cells) {
                    double ox = -cell.getX() + 0.5;
                    double oy = -cell.getY();
                    double oz = -cell.getZ() + 0.5;

                    VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
                    if (!shape.isEmpty()) {
                        solidCells++;
                        Box bb = shape.getBoundingBox();
                        assertTrue(bb.minX >= -EPS && bb.maxX <= 1.0 + EPS, "Clipped X within [0,1]");
                        assertTrue(bb.minY >= -EPS && bb.maxY <= 1.0 + EPS, "Clipped Y within [0,1]");
                        assertTrue(bb.minZ >= -EPS && bb.maxZ <= 1.0 + EPS, "Clipped Z within [0,1]");
                    }
                }
                assertTrue(solidCells > 0, "There must be solid cells for facing " + f + " at fraction " + frac);
            }

            // At CLOSED (0.0): Center cell (x=0, y=2 in local space rotated) must be blocked
            int cx = (int) Math.round(Facings.worldX(0, 0, f));
            int cz = (int) Math.round(Facings.worldZ(0, 0, f));
            BlockPos centerPos = new BlockPos(cx, 2, cz);
            VoxelShape closedCenter = DoorVoxelShapes.getStaticShape(vault, f, centerPos, DoorVoxelShapes.ShapeState.CLOSED);
            assertFalse(closedCenter.isEmpty(), "Vault door center cell must be blocked when CLOSED for facing " + f);

            // At OPEN (1.0): Center cell must be passable (empty shape)
            VoxelShape openCenter = DoorVoxelShapes.getStaticShape(vault, f, centerPos, DoorVoxelShapes.ShapeState.OPEN);
            assertTrue(openCenter.isEmpty(), "Vault door center cell must be passable when OPEN for facing " + f);

            // Frame rail must remain solid when OPEN
            int rx = (int) Math.round(Facings.worldX(-2, 0, f));
            int rz = (int) Math.round(Facings.worldZ(-2, 0, f));
            BlockPos railPos = new BlockPos(rx, 0, rz);
            VoxelShape openRail = DoorVoxelShapes.getStaticShape(vault, f, railPos, DoorVoxelShapes.ShapeState.OPEN);
            assertFalse(openRail.isEmpty(), "Vault door lower rail must remain solid when OPEN for facing " + f);
        }
    }

    @Test
    @DisplayName("Verify Transition Seal collision box slicing across all 4 facings and sub-tick fractions")
    public void testTransitionSealAllFacingsSlicing() {
        DoorDecl seal = DoorDecls.TRANSITION_SEAL;
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        for (Direction f : facings) {
            BlockPos[] cells = seal.getCells(f);
            assertEquals(624, cells.length, "Transition seal must have 624 cells for facing " + f);

            int prevBlockedRows = Integer.MAX_VALUE;
            for (float frac : fractions) {
                Box[] boxes = seal.getBoxes(f, frac, true, true);
                assertNotNull(boxes);

                int activeBoxes = boxes.length;
                if (frac == 0.0f) {
                    assertTrue(activeBoxes >= 20, "At 0.0f, all rows must be blocked");
                } else if (frac == 1.0f) {
                    assertEquals(14, activeBoxes, "At 1.0f, transition seal frame remains solid (14 frame boxes)");
                }
                assertTrue(activeBoxes <= prevBlockedRows, "Active collision boxes must decrease monotonically with lift");
                prevBlockedRows = activeBoxes;

                // Slicing across all 624 cells
                for (BlockPos cell : cells) {
                    double ox = -cell.getX() + 0.5;
                    double oy = -cell.getY();
                    double oz = -cell.getZ() + 0.5;

                    VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
                    if (!shape.isEmpty()) {
                        Box bb = shape.getBoundingBox();
                        assertTrue(bb.minX >= -EPS && bb.maxX <= 1.0 + EPS);
                        assertTrue(bb.minY >= -EPS && bb.maxY <= 1.0 + EPS);
                        assertTrue(bb.minZ >= -EPS && bb.maxZ <= 1.0 + EPS);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Verify Large Silo Hatch horizontal collision across all 4 facings and sub-tick fractions")
    public void testSiloHatchLargeAllFacingsSlicing() {
        DoorDecl hatch = DoorDecls.SILO_HATCH_LARGE;
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        for (Direction f : facings) {
            BlockPos[] cells = hatch.getCells(f);
            assertEquals(49, cells.length, "Large silo hatch must have 49 cells for facing " + f);

            for (float frac : fractions) {
                Box[] boxes = hatch.getBoxes(f, frac, true, true);
                assertNotNull(boxes);
                assertTrue(boxes.length > 0, "Hatch must have collision boxes at fraction " + frac);

                for (Box b : boxes) {
                    assertFalse(Double.isNaN(b.minX) || Double.isNaN(b.maxX));
                    assertFalse(Double.isNaN(b.minY) || Double.isNaN(b.maxY));
                    assertFalse(Double.isNaN(b.minZ) || Double.isNaN(b.maxZ));
                    assertTrue(b.maxX >= b.minX - EPS);
                    assertTrue(b.maxY >= b.minY - EPS);
                    assertTrue(b.maxZ >= b.minZ - EPS);
                }

                for (BlockPos cell : cells) {
                    double ox = -cell.getX() + 0.5;
                    double oy = -cell.getY();
                    double oz = -cell.getZ() + 0.5;

                    VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
                    if (!shape.isEmpty()) {
                        Box bb = shape.getBoundingBox();
                        assertTrue(bb.minX >= -EPS && bb.maxX <= 1.0 + EPS);
                        assertTrue(bb.minY >= -EPS && bb.maxY <= 1.0 + EPS);
                        assertTrue(bb.minZ >= -EPS && bb.maxZ <= 1.0 + EPS);
                    }
                }
            }

            // Closed state: center cell (0, 0, 0) must be solid
            BlockPos centerPos = BlockPos.ORIGIN;
            VoxelShape closedShape = DoorVoxelShapes.getStaticShape(hatch, f, centerPos, DoorVoxelShapes.ShapeState.CLOSED);
            assertFalse(closedShape.isEmpty(), "Closed silo hatch center must be solid for facing " + f);

            // Open state: center cell (0, 0, 0) must be passable
            VoxelShape openShape = DoorVoxelShapes.getStaticShape(hatch, f, centerPos, DoorVoxelShapes.ShapeState.OPEN);
            assertTrue(openShape.isEmpty(), "Open silo hatch center must be empty/passable for facing " + f);

            // Outer rim cell (-3, 0, -3) must remain solid when open
            int rimX = (int) Math.round(Facings.worldX(-3, -3, f));
            int rimZ = (int) Math.round(Facings.worldZ(-3, -3, f));
            BlockPos rimPos = new BlockPos(rimX, 0, rimZ);
            VoxelShape rimShape = DoorVoxelShapes.getStaticShape(hatch, f, rimPos, DoorVoxelShapes.ShapeState.OPEN);
            assertFalse(rimShape.isEmpty(), "Open silo hatch rim must remain solid for facing " + f);
        }
    }

    @Test
    @DisplayName("Verify Large Vehicle Door collision slicing across all 4 facings and sub-tick fractions")
    public void testLargeVehicleDoorAllFacingsSlicing() {
        DoorDecl vehicle = DoorDecls.LARGE_VEHICLE_DOOR;
        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};

        for (Direction f : facings) {
            BlockPos[] cells = vehicle.getCells(f);
            assertEquals(42, cells.length, "Large vehicle door must have 42 cells for facing " + f);

            for (float frac : fractions) {
                Box[] boxes = vehicle.getBoxes(f, frac, true, true);
                assertNotNull(boxes);
                assertTrue(boxes.length > 0);

                for (Box b : boxes) {
                    assertFalse(Double.isNaN(b.minX) || Double.isNaN(b.maxX));
                    assertFalse(Double.isNaN(b.minY) || Double.isNaN(b.maxY));
                    assertFalse(Double.isNaN(b.minZ) || Double.isNaN(b.maxZ));
                    assertTrue(b.maxX >= b.minX - EPS);
                    assertTrue(b.maxY >= b.minY - EPS);
                    assertTrue(b.maxZ >= b.minZ - EPS);
                }

                for (BlockPos cell : cells) {
                    double ox = -cell.getX() + 0.5;
                    double oy = -cell.getY();
                    double oz = -cell.getZ() + 0.5;

                    VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
                    if (!shape.isEmpty()) {
                        Box bb = shape.getBoundingBox();
                        assertTrue(bb.minX >= -EPS && bb.maxX <= 1.0 + EPS);
                        assertTrue(bb.minY >= -EPS && bb.maxY <= 1.0 + EPS);
                        assertTrue(bb.minZ >= -EPS && bb.maxZ <= 1.0 + EPS);
                    }
                }
            }

            // Closed state: center doorway (0, 2, 0) must be solid
            int cx = (int) Math.round(Facings.worldX(0, 0, f));
            int cz = (int) Math.round(Facings.worldZ(0, 0, f));
            BlockPos centerPos = new BlockPos(cx, 2, cz);
            VoxelShape closedShape = DoorVoxelShapes.getStaticShape(vehicle, f, centerPos, DoorVoxelShapes.ShapeState.CLOSED);
            assertFalse(closedShape.isEmpty(), "Closed vehicle door center must be solid for facing " + f);

            // Open state: center doorway must be passable
            VoxelShape openShape = DoorVoxelShapes.getStaticShape(vehicle, f, centerPos, DoorVoxelShapes.ShapeState.OPEN);
            assertTrue(openShape.isEmpty(), "Open vehicle door center must be passable for facing " + f);

            // Side pillar cell (-3, 2, 0) must remain solid when open
            int px = (int) Math.round(Facings.worldX(-3, 0, f));
            int pz = (int) Math.round(Facings.worldZ(-3, 0, f));
            BlockPos pillarPos = new BlockPos(px, 2, pz);
            VoxelShape pillarShape = DoorVoxelShapes.getStaticShape(vehicle, f, pillarPos, DoorVoxelShapes.ShapeState.OPEN);
            assertFalse(pillarShape.isEmpty(), "Vehicle door side pillar must remain solid when open for facing " + f);
        }
    }

    // =========================================================================
    // FOCUS AREA 2: Swept-Volume Collision and Boundary Stress
    // =========================================================================

    @Test
    @DisplayName("Verify Swept-Volume Collision and Clamping under Out-of-Bounds & Extreme Fractions")
    public void testSweptVolumeExtremeFractions() {
        DoorDecl door = DoorDecls.SLIDING_BLAST_DOOR;
        Direction facing = Direction.NORTH;

        float[] validFractions = {0.0f, 0.001f, 0.25f, 0.5f, 0.75f, 0.999f, 1.0f};
        for (float frac : validFractions) {
            Box[] boxes = door.getBoxes(facing, frac, true, true);
            assertNotNull(boxes);
            assertTrue(boxes.length > 0);
            for (Box b : boxes) {
                assertTrue(b.maxX >= b.minX);
                assertTrue(b.maxY >= b.minY);
                assertTrue(b.maxZ >= b.minZ);
            }
        }

        Box[] boxesNegative = door.getBoxes(facing, -10.0f, true, true);
        Box[] boxesZero = door.getBoxes(facing, 0.0f, true, true);
        assertEquals(boxesZero.length, boxesNegative.length, "Negative fraction must clamp to 0.0f");

        Box[] boxesExcess = door.getBoxes(facing, 10.0f, true, true);
        Box[] boxesOne = door.getBoxes(facing, 1.0f, true, true);
        assertEquals(boxesOne.length, boxesExcess.length, "Excess fraction must clamp to 1.0f");
    }

    @Test
    @DisplayName("Verify DoorPlacementGuard swept volume collision detects moving panels across all 4 facings")
    public void testDoorPlacementGuardSweptVolumeAllFacings() {
        DoorDecl vault = DoorDecls.VAULT_DOOR;

        for (Direction f : Direction.Type.HORIZONTAL) {
            boolean foundSweptCell = false;
            for (BlockPos cell : vault.getCells(f)) {
                Box target = new Box(cell);
                if (DoorPlacementGuard.intersectsMovingPanel(vault, BlockPos.ORIGIN, f, target)) {
                    foundSweptCell = true;
                    break;
                }
            }
            assertTrue(foundSweptCell, "DoorPlacementGuard must detect swept volume for vault door facing " + f);

            // Target far away must not intersect
            Box farTarget = new Box(new BlockPos(50, 2, 50));
            assertFalse(DoorPlacementGuard.intersectsMovingPanel(vault, BlockPos.ORIGIN, f, farTarget),
                    "Far target must not intersect moving panel");
        }
    }

    // =========================================================================
    // FOCUS AREA 3: Concurrency and Rapid State Toggle Stress
    // =========================================================================

    @Test
    @DisplayName("Verify Mid-Stroke Rapid Reversals and Queue Resolution")
    public void testRapidMidStrokeReversals() {
        DoorDecl d = DoorDecls.VAULT_DOOR; // 120 ticks
        DoorBlockEntity be = createTestDoor(d);

        // Start opening
        be.request(true);
        assertEquals(DoorState.OPENING, be.getState());

        // Repeated rapid toggles mid-stroke
        for (int i = 0; i < 20; i++) {
            be.tickServer();
            be.request(false);
            assertEquals(Boolean.FALSE, be.getQueuedOpen());
            be.request(true);
            be.request(false);
            assertEquals(Boolean.FALSE, be.getQueuedOpen());
        }

        // Finish opening stroke
        while (be.getState() == DoorState.OPENING) {
            be.tickServer();
        }

        // Upon reaching OPENING completion, queuedOpen=false must trigger immediate CLOSING
        assertEquals(DoorState.CLOSING, be.getState(), "Should transition to CLOSING after completing stroke");
        assertNull(be.getQueuedOpen());

        // Queue open while closing
        be.tickServer();
        be.request(true);
        assertEquals(Boolean.TRUE, be.getQueuedOpen());

        while (be.getState() == DoorState.CLOSING) {
            be.tickServer();
        }

        // Upon reaching CLOSING completion, queuedOpen=true must trigger immediate OPENING
        assertEquals(DoorState.OPENING, be.getState(), "Should transition to OPENING after completing stroke");
        assertNull(be.getQueuedOpen());
    }

    @Test
    @DisplayName("Verify Rapid Emergency Override Interleaving Mid-Stroke")
    public void testRapidEmergencyOverrides() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        for (int cycle = 0; cycle < 50; cycle++) {
            be.openNow();
            assertEquals(DoorState.OPENING, be.getState());
            assertEquals(0, be.getTimer());

            be.tickServer();
            be.tickServer();

            // Emergency close mid-stroke
            be.closeNow();
            assertEquals(DoorState.CLOSING, be.getState());
            assertEquals(0, be.getTimer());

            be.tickServer();

            // Emergency open mid-closing
            be.openNow();
            assertEquals(DoorState.OPENING, be.getState());
            assertEquals(0, be.getTimer());

            // Emergency close again immediately
            be.closeNow();
            assertEquals(DoorState.CLOSING, be.getState());
            assertEquals(0, be.getTimer());
        }
    }

    @Test
    @DisplayName("Multi-threaded Concurrency Stress Test on Door State Machine and Cache")
    public void testMultiThreadedConcurrencyStress() throws InterruptedException {
        int threads = 16;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger errorCount = new AtomicInteger(0);

        DoorDecl[] testDoors = {
            DoorDecls.VAULT_DOOR,
            DoorDecls.TRANSITION_SEAL,
            DoorDecls.SILO_HATCH_LARGE,
            DoorDecls.LARGE_VEHICLE_DOOR
        };

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random rng = new Random(threadId);

                    for (int op = 0; op < operationsPerThread; op++) {
                        DoorDecl decl = testDoors[rng.nextInt(testDoors.length)];
                        Direction f = Direction.fromHorizontal(rng.nextInt(4));
                        DoorBlockEntity be = createTestDoor(decl);

                        // Concurrent state machine operations
                        int action = rng.nextInt(6);
                        switch (action) {
                            case 0 -> be.request(true);
                            case 1 -> be.request(false);
                            case 2 -> be.toggle();
                            case 3 -> be.closeNow();
                            case 4 -> be.openNow();
                            case 5 -> be.tickServer();
                        }

                        // Concurrent static shape cache queries
                        BlockPos offset = new BlockPos(rng.nextInt(5) - 2, rng.nextInt(3), rng.nextInt(5) - 2);
                        DoorVoxelShapes.ShapeState state = DoorVoxelShapes.ShapeState.values()[rng.nextInt(3)];
                        VoxelShape shape = DoorVoxelShapes.getStaticShape(decl, f, offset, state);
                        assertNotNull(shape);

                        // Occasional cache clear
                        if (rng.nextInt(100) == 0) {
                            DoorVoxelShapes.clearCache();
                        }
                    }
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                    t.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent tasks must finish within timeout");
        assertEquals(0, errorCount.get(), "No exceptions or race condition errors permitted during concurrent execution");
    }
}
