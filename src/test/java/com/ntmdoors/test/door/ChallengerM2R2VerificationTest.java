package com.ntmdoors.test.door;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.DoorVoxelShapes;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.door.DoorState;
import net.minecraft.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Verification & Stress Test Suite for Milestone M2 Iteration 2
 * Executed by Challenger M2-R2-1 on branch fabric-1.20.1 (commit 1e36116).
 *
 * Focus:
 * 1. Dynamic collision box slicing under all 4 facings (NORTH, SOUTH, EAST, WEST) and sub-tick fractions across ALL DoorDecls.
 * 2. Emergency overrides openNow() and closeNow(): immediate abort & transition on intact doors, strict rejection on thermite-fused and breached doors.
 * 3. Swept-volume placement guard across multiblock footprints.
 */
public class ChallengerM2R2VerificationTest {

    private static final double EPS = 1e-5;
    private static final Direction[] ALL_FACINGS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private static final float[] SUBTICK_FRACTIONS = {
            0.0f, 0.001f, 0.1f, 0.25f, 0.333f, 0.5f, 0.666f, 0.75f, 0.9f, 0.999f, 1.0f
    };

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
        DoorVoxelShapes.clearCache();
    }

    private DoorBlockEntity createDoor(DoorDecl decl, BlockPos pos) {
        DoorBlockEntity be = new DoorBlockEntity(null, pos, null);
        be.setDecl(decl);
        return be;
    }

    // =========================================================================
    // FOCUS AREA 1: Collision Slicing Across All 22 Doors, 4 Facings & Sub-ticks
    // =========================================================================

    @Test
    @DisplayName("Verify collision boxes geometry & per-cell slicing for ALL 22 DoorDecls across 4 facings & 11 sub-tick fractions")
    public void testAllDoorsCollisionSlicingMatrix() {
        List<DoorDecl> allDoors = DoorDecls.ALL;
        assertFalse(allDoors.isEmpty(), "DoorDecls.ALL must not be empty");
        assertTrue(allDoors.size() >= 19, "Must contain at least 19 canonical door types");

        int totalEvaluations = 0;
        int totalClippedShapes = 0;

        for (DoorDecl decl : allDoors) {
            for (Direction facing : ALL_FACINGS) {
                BlockPos[] cells = decl.getCells(facing);
                assertNotNull(cells, "Cells array must not be null for " + decl.name + " " + facing);
                assertTrue(cells.length > 0, "Door must have at least 1 cell: " + decl.name);

                for (float frac : SUBTICK_FRACTIONS) {
                    Box[] boxes = decl.getBoxes(facing, frac, true, true);
                    assertNotNull(boxes, "Boxes array must not be null for " + decl.name + " " + facing + " frac=" + frac);

                    for (Box b : boxes) {
                        assertFalse(Double.isNaN(b.minX) || Double.isNaN(b.maxX), "Box minX/maxX is NaN in " + decl.name);
                        assertFalse(Double.isNaN(b.minY) || Double.isNaN(b.maxY), "Box minY/maxY is NaN in " + decl.name);
                        assertFalse(Double.isNaN(b.minZ) || Double.isNaN(b.maxZ), "Box minZ/maxZ is NaN in " + decl.name);
                        assertFalse(Double.isInfinite(b.minX) || Double.isInfinite(b.maxX), "Box minX/maxX is Infinite in " + decl.name);
                        assertFalse(Double.isInfinite(b.minY) || Double.isInfinite(b.maxY), "Box minY/maxY is Infinite in " + decl.name);
                        assertFalse(Double.isInfinite(b.minZ) || Double.isInfinite(b.maxZ), "Box minZ/maxZ is Infinite in " + decl.name);

                        assertTrue(b.maxX >= b.minX - EPS, "Box inverted X in " + decl.name);
                        assertTrue(b.maxY >= b.minY - EPS, "Box inverted Y in " + decl.name);
                        assertTrue(b.maxZ >= b.minZ - EPS, "Box inverted Z in " + decl.name);
                    }

                    // Clip boxes to every cell in footprint
                    for (BlockPos cell : cells) {
                        double ox = -cell.getX() + 0.5;
                        double oy = -cell.getY();
                        double oz = -cell.getZ() + 0.5;

                        VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
                        assertNotNull(shape, "Clipped shape must not be null");

                        if (!shape.isEmpty()) {
                            totalClippedShapes++;
                            Box bb = shape.getBoundingBox();
                            assertTrue(bb.minX >= -EPS && bb.maxX <= 1.0 + EPS, "Clipped minX/maxX out of unit cell bounds");
                            assertTrue(bb.minY >= -EPS && bb.maxY <= 1.0 + EPS, "Clipped minY/maxY out of unit cell bounds");
                            assertTrue(bb.minZ >= -EPS && bb.maxZ <= 1.0 + EPS, "Clipped minZ/maxZ out of unit cell bounds");
                        }
                    }
                    totalEvaluations++;
                }

                // Verify Static Cache equivalence
                for (BlockPos cell : cells) {
                    VoxelShape staticClosed = DoorVoxelShapes.getStaticShape(decl, facing, cell, DoorVoxelShapes.ShapeState.CLOSED);
                    VoxelShape staticOpen = DoorVoxelShapes.getStaticShape(decl, facing, cell, DoorVoxelShapes.ShapeState.OPEN);
                    VoxelShape staticBreached = DoorVoxelShapes.getStaticShape(decl, facing, cell, DoorVoxelShapes.ShapeState.BREACHED);

                    assertNotNull(staticClosed);
                    assertNotNull(staticOpen);
                    assertNotNull(staticBreached);

                    // Breached doors must exclude moving parts
                    Box[] breachedBoxes = decl.getBoxes(facing, 1.0f, true, false);
                    double ox = -cell.getX() + 0.5;
                    double oy = -cell.getY();
                    double oz = -cell.getZ() + 0.5;
                    VoxelShape computedBreached = DoorVoxelShapes.clipBoxesToCell(breachedBoxes, ox, oy, oz);
                    assertEquals(computedBreached.isEmpty(), staticBreached.isEmpty(),
                            "Breached static shape must match non-moving parts for " + decl.name);
                }
            }
        }

        assertTrue(totalEvaluations >= 22 * 4 * 11, "Must perform complete matrix evaluations");
        assertTrue(totalClippedShapes > 0, "Must have generated non-empty clipped shapes");
    }

    // =========================================================================
    // FOCUS AREA 2: openNow() and closeNow() Emergency Overrides
    // =========================================================================

    @Test
    @DisplayName("Verify openNow() and closeNow() state transitions on intact doors")
    public void testOverridesOnIntactDoor() {
        DoorDecl decl = DoorDecls.VAULT_DOOR; // 120 ticks
        DoorBlockEntity door = createDoor(decl, BlockPos.ORIGIN);

        // 1. In CLOSED: openNow() -> transitions to OPENING immediately
        assertEquals(DoorState.CLOSED, door.getState());
        door.openNow();
        assertEquals(DoorState.OPENING, door.getState(), "openNow() on CLOSED must transition to OPENING");
        assertEquals(0, door.getTimer(), "Timer must be reset to 0");

        // 2. In OPENING: closeNow() -> aborts opening and transitions to CLOSING immediately
        door.tickServer();
        door.tickServer();
        door.tickServer();
        assertEquals(3, door.getTimer());
        door.closeNow();
        assertEquals(DoorState.CLOSING, door.getState(), "closeNow() on OPENING must abort and transition to CLOSING");
        assertEquals(0, door.getTimer(), "Timer must be reset to 0 upon override");

        // 3. In CLOSING: openNow() -> aborts closing and transitions to OPENING immediately
        door.tickServer();
        door.tickServer();
        assertEquals(2, door.getTimer());
        door.openNow();
        assertEquals(DoorState.OPENING, door.getState(), "openNow() on CLOSING must abort and transition to OPENING");
        assertEquals(0, door.getTimer(), "Timer must be reset to 0 upon override");

        // 4. In OPEN: closeNow() -> transitions to CLOSING
        while (door.getState() == DoorState.OPENING) {
            door.tickServer();
        }
        assertEquals(DoorState.OPEN, door.getState());
        door.closeNow();
        assertEquals(DoorState.CLOSING, door.getState(), "closeNow() on OPEN must transition to CLOSING");
        assertEquals(0, door.getTimer());
    }

    @Test
    @DisplayName("Verify mid-stroke override sweeps across all ticks for all doors")
    public void testMidStrokeOverridesExhaustive() {
        for (DoorDecl decl : DoorDecls.ALL) {
            DoorBlockEntity door = createDoor(decl, BlockPos.ORIGIN);

            // Test overriding at various points in stroke
            door.openNow();
            assertEquals(DoorState.OPENING, door.getState());

            int midPoint = Math.max(1, decl.openTicks / 2);
            for (int t = 0; t < midPoint; t++) {
                door.tickServer();
            }

            // Reverse to closeNow()
            door.closeNow();
            assertEquals(DoorState.CLOSING, door.getState(), "Override to closeNow must succeed mid-stroke for " + decl.name);
            assertEquals(0, door.getTimer());

            int closeMidPoint = Math.max(1, decl.closeTicks / 2);
            for (int t = 0; t < closeMidPoint; t++) {
                door.tickServer();
            }

            // Reverse back to openNow()
            door.openNow();
            assertEquals(DoorState.OPENING, door.getState(), "Override to openNow must succeed mid-stroke for " + decl.name);
            assertEquals(0, door.getTimer());
        }
    }

    @Test
    @DisplayName("Verify openNow() and closeNow() are strictly rejected on thermite-fused doors in all states")
    public void testThermiteFusedStrictRejectionInAllStates() {
        DoorDecl decl = DoorDecls.FIRE_DOOR;
        BlockPos thermitePos = new BlockPos(0, 65, 0);

        // State: CLOSED
        DoorBlockEntity door1 = createDoor(decl, BlockPos.ORIGIN);
        door1.addThermiteCharge(thermitePos);
        assertTrue(door1.isThermiteFused());

        door1.openNow();
        assertEquals(DoorState.CLOSED, door1.getState(), "Thermite fused CLOSED door must reject openNow()");
        door1.closeNow();
        assertEquals(DoorState.CLOSED, door1.getState(), "Thermite fused CLOSED door must reject closeNow()");
        door1.request(true);
        assertEquals(DoorState.CLOSED, door1.getState(), "Thermite fused CLOSED door must reject request(true)");
        door1.openForSystem();
        assertEquals(DoorState.CLOSED, door1.getState(), "Thermite fused CLOSED door must reject openForSystem()");
        door1.closeForSystem();
        assertEquals(DoorState.CLOSED, door1.getState(), "Thermite fused CLOSED door must reject closeForSystem()");

        // State: OPENING when thermite is attached
        DoorBlockEntity door2 = createDoor(decl, BlockPos.ORIGIN);
        door2.openNow();
        door2.tickServer();
        assertEquals(DoorState.OPENING, door2.getState());
        int timerBefore = door2.getTimer();

        door2.addThermiteCharge(thermitePos);
        assertTrue(door2.isThermiteFused());

        door2.closeNow();
        assertEquals(DoorState.OPENING, door2.getState(), "Thermite fused OPENING door must reject closeNow()");
        assertEquals(timerBefore, door2.getTimer(), "Timer must not be reset");
        door2.openNow();
        assertEquals(DoorState.OPENING, door2.getState(), "Thermite fused OPENING door must reject openNow()");

        // State: OPEN when thermite is attached
        DoorBlockEntity door3 = createDoor(decl, BlockPos.ORIGIN);
        door3.openNow();
        while (door3.getState() == DoorState.OPENING) {
            door3.tickServer();
        }
        assertEquals(DoorState.OPEN, door3.getState());

        door3.addThermiteCharge(thermitePos);
        assertTrue(door3.isThermiteFused());

        door3.closeNow();
        assertEquals(DoorState.OPEN, door3.getState(), "Thermite fused OPEN door must reject closeNow()");
        door3.openNow();
        assertEquals(DoorState.OPEN, door3.getState(), "Thermite fused OPEN door must reject openNow()");
        door3.request(false);
        assertEquals(DoorState.OPEN, door3.getState(), "Thermite fused OPEN door must reject request(false)");

        // Removing thermite charge restores functionality
        door3.removeThermiteCharge(thermitePos);
        assertFalse(door3.isThermiteFused());
        door3.closeNow();
        assertEquals(DoorState.CLOSING, door3.getState(), "Removing thermite charge must restore closeNow() ability");
    }

    @Test
    @DisplayName("Verify openNow() and closeNow() are strictly rejected on breached doors in all states")
    public void testBreachedStrictRejectionInAllStates() {
        DoorDecl decl = DoorDecls.SLIDING_BLAST_DOOR;

        // State: CLOSED when breached
        DoorBlockEntity door1 = createDoor(decl, BlockPos.ORIGIN);
        door1.breach();
        assertTrue(door1.isBreached());

        door1.openNow();
        assertEquals(DoorState.CLOSED, door1.getState(), "Breached CLOSED door must reject openNow()");
        door1.closeNow();
        assertEquals(DoorState.CLOSED, door1.getState(), "Breached CLOSED door must reject closeNow()");
        door1.request(true);
        assertEquals(DoorState.CLOSED, door1.getState(), "Breached CLOSED door must reject request(true)");
        door1.openForSystem();
        assertEquals(DoorState.CLOSED, door1.getState(), "Breached CLOSED door must reject openForSystem()");
        door1.closeForSystem();
        assertEquals(DoorState.CLOSED, door1.getState(), "Breached CLOSED door must reject closeForSystem()");

        // State: OPENING when breached
        DoorBlockEntity door2 = createDoor(decl, BlockPos.ORIGIN);
        door2.openNow();
        door2.tickServer();
        assertEquals(DoorState.OPENING, door2.getState());

        door2.breach();
        assertTrue(door2.isBreached());
        assertEquals(DoorState.CLOSED, door2.getState(), "Breach resets state machine to CLOSED with breached flag");

        door2.closeNow();
        assertEquals(DoorState.CLOSED, door2.getState(), "Breached door must reject closeNow()");
        door2.openNow();
        assertEquals(DoorState.CLOSED, door2.getState(), "Breached door must reject openNow()");
        door2.request(true);
        assertEquals(DoorState.CLOSED, door2.getState(), "Breached door must reject request(true)");

        // State: OPEN when breached
        DoorBlockEntity door3 = createDoor(decl, BlockPos.ORIGIN);
        door3.openNow();
        while (door3.getState() == DoorState.OPENING) {
            door3.tickServer();
        }
        assertEquals(DoorState.OPEN, door3.getState());

        door3.breach();
        assertTrue(door3.isBreached());
        assertEquals(DoorState.CLOSED, door3.getState(), "Breach resets state machine to CLOSED with breached flag");

        door3.closeNow();
        assertEquals(DoorState.CLOSED, door3.getState(), "Breached door must reject closeNow()");
        door3.openNow();
        assertEquals(DoorState.CLOSED, door3.getState(), "Breached door must reject openNow()");
        door3.request(false);
        assertEquals(DoorState.CLOSED, door3.getState(), "Breached door must reject request(false)");
    }

    // =========================================================================
    // FOCUS AREA 3: Swept-Volume Placement Guard Across Footprints
    // =========================================================================

    @Test
    @DisplayName("Verify DoorPlacementGuard detects swept volume across multiblock candidate door footprints")
    public void testSweptVolumePlacementGuardAcrossFootprints() {
        // Test with Sliding Blast Door (panel translates sideways during open)
        DoorDecl activeDecl = DoorDecls.SLIDING_BLAST_DOOR;
        BlockPos activeCorePos = new BlockPos(100, 64, 100);
        DoorBlockEntity activeDoor = createDoor(activeDecl, activeCorePos);

        // Put active door into OPENING motion
        activeDoor.openNow();
        assertEquals(DoorState.OPENING, activeDoor.getState());
        assertTrue(DoorPlacementGuard.isRegistered(activeDoor), "Active moving door must be registered in DoorPlacementGuard");

        // Identify swept block positions
        BlockPos sweptPos = null;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = activeCorePos.add(dx, dy, dz);
                    if (DoorPlacementGuard.blocksPlacement(null, p)) {
                        sweptPos = p;
                        break;
                    }
                }
                if (sweptPos != null) break;
            }
            if (sweptPos != null) break;
        }
        assertNotNull(sweptPos, "Active moving sliding blast door must produce swept positions");

        // Candidate Door 1: A 2x3 Fire Door whose dummy cell (1, 0, 0) falls on sweptPos, but core does not
        DoorDecl candidateDecl = DoorDecls.FIRE_DOOR; // 2x3 footprint
        Direction candidateFacing = Direction.NORTH;
        BlockPos candidateOrigin = sweptPos.add(-1, 0, 0);

        // The core position of candidate is outside swept volume:
        // (If candidateOrigin happens to also be swept, choose sweptPos.add(0, -1, 0) or verify loop)
        boolean originBlocked = DoorPlacementGuard.blocksPlacement(null, candidateOrigin);

        // Evaluate candidate footprint clearance loop
        boolean placementRejected = false;
        for (BlockPos off : candidateDecl.getCells(candidateFacing)) {
            BlockPos cell = candidateOrigin.add(off);
            if (DoorPlacementGuard.blocksPlacement(null, cell)) {
                placementRejected = true;
                break;
            }
        }
        assertTrue(placementRejected, "Candidate door placement MUST be rejected if any dummy cell intersects swept volume");

        // Candidate Door 2: Placed far away (e.g. 50 blocks away)
        BlockPos distantOrigin = activeCorePos.add(50, 0, 50);
        boolean distantRejected = false;
        for (BlockPos off : candidateDecl.getCells(candidateFacing)) {
            BlockPos cell = distantOrigin.add(off);
            if (DoorPlacementGuard.blocksPlacement(null, cell)) {
                distantRejected = true;
                break;
            }
        }
        assertFalse(distantRejected, "Distant candidate door must NOT be rejected by swept volume guard");

        // Lifecycle: When active door finishes closing, swept volume must be unregistered
        activeDoor.closeNow();
        while (activeDoor.getState() == DoorState.CLOSING) {
            activeDoor.tickServer();
        }
        assertEquals(DoorState.CLOSED, activeDoor.getState());
        assertFalse(DoorPlacementGuard.isRegistered(activeDoor), "Closed door must be unregistered from DoorPlacementGuard");
        assertFalse(DoorPlacementGuard.blocksPlacement(null, sweptPos),
                "Swept volume must be completely cleared after door reaches CLOSED state");
    }

    @Test
    @DisplayName("Verify DoorPlacementGuard unregisters on breach and dismantle")
    public void testPlacementGuardLifecycleUnregister() {
        DoorDecl decl = DoorDecls.VAULT_DOOR;
        BlockPos pos = new BlockPos(200, 64, 200);

        // Breach unregistration
        DoorBlockEntity door = createDoor(decl, pos);
        door.openNow();
        assertTrue(DoorPlacementGuard.isRegistered(door));
        door.breach();
        assertFalse(DoorPlacementGuard.isRegistered(door), "Breached door must unregister from DoorPlacementGuard");

        // Dismantle unregistration
        DoorBlockEntity door2 = createDoor(decl, pos);
        door2.openNow();
        assertTrue(DoorPlacementGuard.isRegistered(door2));
        door2.beginDismantle();
        assertFalse(DoorPlacementGuard.isRegistered(door2), "Dismantled door must unregister from DoorPlacementGuard");
    }
}
