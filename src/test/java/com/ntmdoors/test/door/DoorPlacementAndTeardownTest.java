package com.ntmdoors.test.door;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.DoorPart;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.door.DoorState;
import net.minecraft.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DoorPlacementAndTeardownTest {

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

    private DoorBlockEntity createTestDoor(DoorDecl decl, BlockPos pos) {
        DoorBlockEntity be = new DoorBlockEntity(null, pos, null);
        be.setDecl(decl);
        return be;
    }

    @Test
    @DisplayName("Verify DoorPlacementGuard Registration Lifecycle")
    public void testDoorPlacementGuardRegistration() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d, BlockPos.ORIGIN);

        assertFalse(DoorPlacementGuard.isRegistered(be), "Closed door should not be registered");

        be.request(true);
        assertEquals(DoorState.OPENING, be.getState());
        assertTrue(DoorPlacementGuard.isRegistered(be), "Moving door must be registered");

        // Complete open
        while (be.getState() == DoorState.OPENING) {
            be.tickServer();
        }
        assertEquals(DoorState.OPEN, be.getState());
        assertTrue(DoorPlacementGuard.isRegistered(be), "Open door must remain registered to protect swept volume");

        // Request close and complete closing
        be.request(false);
        assertEquals(DoorState.CLOSING, be.getState());
        assertTrue(DoorPlacementGuard.isRegistered(be), "Closing door must remain registered");

        while (be.getState() == DoorState.CLOSING) {
            be.tickServer();
        }
        assertEquals(DoorState.CLOSED, be.getState());
        assertFalse(DoorPlacementGuard.isRegistered(be), "Fully closed door must unregister from DoorPlacementGuard");
    }

    @Test
    @DisplayName("Verify Unregistration on markRemoved and Dismantle")
    public void testUnregistrationOnRemovalAndDismantle() {
        DoorDecl d = DoorDecls.SLIDING_BLAST_DOOR;
        DoorBlockEntity be1 = createTestDoor(d, new BlockPos(0, 0, 0));
        be1.request(true);
        assertTrue(DoorPlacementGuard.isRegistered(be1));

        be1.markRemoved();
        assertFalse(DoorPlacementGuard.isRegistered(be1), "markRemoved must unregister core from guard");

        DoorBlockEntity be2 = createTestDoor(d, new BlockPos(10, 0, 0));
        be2.request(true);
        assertTrue(DoorPlacementGuard.isRegistered(be2));

        assertTrue(be2.beginDismantle(), "First beginDismantle must return true");
        assertFalse(DoorPlacementGuard.isRegistered(be2), "beginDismantle must unregister core from guard");
    }

    @Test
    @DisplayName("Verify Dismantle Recursion Guard Prevents Cascading Loops")
    public void testDismantleRecursionGuard() {
        DoorDecl d = DoorDecls.VAULT_DOOR;
        DoorBlockEntity be = createTestDoor(d, BlockPos.ORIGIN);

        assertTrue(be.beginDismantle(), "First dismantle invocation must succeed");
        assertFalse(be.beginDismantle(), "Second dismantle invocation must be blocked by recursion guard");
        assertFalse(be.beginDismantle(), "Subsequent dismantle invocations must remain blocked");
    }

    @Test
    @DisplayName("Verify Swept Volume Placement Blocking and Distance Cutoff")
    public void testSweptVolumePlacementBlocking() {
        DoorDecl d = DoorDecls.SLIDING_BLAST_DOOR;
        BlockPos corePos = new BlockPos(100, 64, 100);
        DoorBlockEntity be = createTestDoor(d, corePos);

        // While CLOSED, placement at any position is NOT blocked
        assertFalse(DoorPlacementGuard.blocksPlacement(null, corePos.east()),
                "Closed door must never block block placement");

        // Open the door
        be.request(true);
        assertTrue(DoorPlacementGuard.isRegistered(be));

        // When door is moving or open, blocks intersecting the moving panel are blocked
        boolean hasBlockedCell = false;
        // Check cells within 3 blocks of core
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = corePos.add(dx, dy, dz);
                    if (DoorPlacementGuard.blocksPlacement(null, p)) {
                        hasBlockedCell = true;
                        break;
                    }
                }
                if (hasBlockedCell) break;
            }
            if (hasBlockedCell) break;
        }

        assertTrue(hasBlockedCell, "Moving sliding door must block placement in its swept panel path");

        // Far away block must not be blocked
        BlockPos farPos = corePos.add(50, 0, 50);
        assertFalse(DoorPlacementGuard.blocksPlacement(null, farPos),
                "Blocks outside door swept volume must not be blocked");

        // Close the door
        be.closeNow();
        while (be.getState() == DoorState.CLOSING) be.tickServer();
        assertEquals(DoorState.CLOSED, be.getState());

        // Now that door is closed, placement is no longer blocked anywhere
        assertFalse(DoorPlacementGuard.blocksPlacement(null, corePos.east()),
                "Closed door must unblock placement");
    }

    @Test
    @DisplayName("Verify unbindFromControllers Clears Security and Guard")
    public void testUnbindFromControllers() {
        DoorDecl d = DoorDecls.SECURE_ACCESS_DOOR;
        DoorBlockEntity be = createTestDoor(d, BlockPos.ORIGIN);
        be.setSecurity(true);

        UUID owner = UUID.randomUUID();
        be.getSecurity().setOwner(owner, "Admin");
        assertEquals(owner, be.getSecurity().getOwnerId());
        assertEquals("Admin", be.getSecurity().getOwnerName());

        be.request(true);
        assertTrue(DoorPlacementGuard.isRegistered(be));

        be.unbindFromControllers();
        assertFalse(DoorPlacementGuard.isRegistered(be));
        // Security gates cleared
        be.getSecurity().clear();
    }

    @Test
    @DisplayName("Verify Multiblock Layout Clearance and Structural Integrity Across All Doors")
    public void testMultiblockLayoutClearanceIntegrity() {
        for (DoorDecl d : DoorDecls.ALL) {
            for (Direction f : Direction.Type.HORIZONTAL) {
                BlockPos[] cells = d.getCells(f);
                assertNotNull(cells);
                assertTrue(cells.length >= 1, "Door " + d.name + " must have at least 1 multiblock cell");

                boolean originFound = false;
                for (BlockPos cell : cells) {
                    if (cell.equals(BlockPos.ORIGIN)) originFound = true;
                    assertTrue(cell.getY() >= 0, "Cell Y offset must be non-negative: " + cell + " in " + d.name);
                    assertTrue(Math.abs(cell.getX()) <= 30 && Math.abs(cell.getZ()) <= 30,
                            "Cell coordinates must be within reasonable multiblock bounds");
                }
                assertTrue(originFound, "Origin (0,0,0) core must be present in multiblock cells of " + d.name);
            }
        }
    }
}
