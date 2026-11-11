package com.ntmdoors.test.door;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.util.Facings;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DoorMultiblockLayoutTest {

    @BeforeAll
    public static void setup() {
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Force Registries classloading before DoorDecls
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    @Test
    @DisplayName("Verify All 22 Doors Registered in DoorDecls")
    public void testAll22DoorsRegistered() {
        assertEquals(22, DoorDecls.ALL.size(), "DoorDecls.ALL must contain exactly 22 door declarations");

        String[] expectedNames = {
            "vault_door",
            "sliding_blast_door",
            "qe_sliding_door",
            "qe_sliding_panel",
            "fire_door",
            "secure_access_door",
            "sliding_seal_door",
            "round_airlock_door",
            "silo_hatch",
            "silo_hatch_large",
            "cargo_door",
            "large_vehicle_door",
            "qe_containment",
            "blast_door",
            "water_door",
            "transition_seal",
            "blast_shutter",
            "black_mesa_door",
            "office_door_1_double",
            "office_door_2",
            "office_door_2_double",
            "test_door"
        };

        Set<String> registeredNames = new HashSet<>();
        for (DoorDecl decl : DoorDecls.ALL) {
            registeredNames.add(decl.name);
        }

        for (String expected : expectedNames) {
            assertTrue(registeredNames.contains(expected), "Missing door declaration: " + expected);
        }
    }

    @Test
    @DisplayName("Verify 66 Door Variants in ModDoors")
    public void testModDoorsVariants() {
        // 22 doors * 3 variants = 66
        assertEquals(66, ModDoors.DOORS.size(), "ModDoors.DOORS must contain 66 door blocks");
        assertEquals(66, ModDoors.DOOR_ITEMS.size(), "ModDoors.DOOR_ITEMS must contain 66 door items");
        assertEquals(66, ModDoors.ALL_DOORS.size(), "ModDoors.ALL_DOORS must contain 66 blocks");

        for (DoorDecl decl : DoorDecls.ALL) {
            String normalId = decl.name;
            String lockedId = decl.name + "_locked";
            String secId = decl.name + "_security";

            assertTrue(ModDoors.DOORS.containsKey(normalId), "Missing normal door: " + normalId);
            assertTrue(ModDoors.DOORS.containsKey(lockedId), "Missing locked door: " + lockedId);
            assertTrue(ModDoors.DOORS.containsKey(secId), "Missing security door: " + secId);

            BlockDoorGeneric normalBlock = ModDoors.DOORS.get(normalId);
            BlockDoorGeneric lockedBlock = ModDoors.DOORS.get(lockedId);
            BlockDoorGeneric secBlock = ModDoors.DOORS.get(secId);

            assertFalse(normalBlock.isLocked());
            assertFalse(normalBlock.isSecurity());

            assertTrue(lockedBlock.isLocked());
            assertFalse(lockedBlock.isSecurity());

            assertFalse(secBlock.isLocked());
            assertTrue(secBlock.isSecurity());
        }
    }

    @Test
    @DisplayName("Verify Footprint Clearance and Core Cell at (0,0,0)")
    public void testFootprintContainsOrigin() {
        for (DoorDecl decl : DoorDecls.ALL) {
            assertTrue(decl.minX <= 0 && decl.maxX >= 0, decl.name + " minX/maxX must enclose 0");
            assertTrue(decl.minY <= 0 && decl.maxY >= 0, decl.name + " minY/maxY must enclose 0");
            assertTrue(decl.minZ <= 0 && decl.maxZ >= 0, decl.name + " minZ/maxZ must enclose 0");

            for (Direction f : Direction.Type.HORIZONTAL) {
                BlockPos[] cells = decl.getCells(f);
                assertNotNull(cells);
                assertTrue(cells.length > 0);

                boolean foundOrigin = false;
                for (BlockPos p : cells) {
                    if (p.equals(BlockPos.ORIGIN)) {
                        foundOrigin = true;
                        break;
                    }
                }
                assertTrue(foundOrigin, decl.name + " facing " + f + " must include origin (0,0,0)");
            }
        }
    }

    @Test
    @DisplayName("Verify Vault Door Footprint (55 cells)")
    public void testVaultDoorFootprint() {
        DoorDecl vault = DoorDecls.VAULT_DOOR;
        BlockPos[] cells = vault.getCells(Direction.NORTH);
        // 5x5 nominal (25) + rear rail & parking bay (30) = 55 cells
        assertEquals(55, cells.length, "Vault door must have exactly 55 cells");
    }

    @Test
    @DisplayName("Verify Transition Seal Footprint (624 cells)")
    public void testTransitionSealFootprint() {
        DoorDecl seal = DoorDecls.TRANSITION_SEAL;
        BlockPos[] cells = seal.getCells(Direction.NORTH);
        // 26 wide (-12 to 13) x 24 high (0 to 23) x 1 deep = 26 * 24 = 624 cells
        assertEquals(624, cells.length, "Transition seal must have exactly 624 cells");
    }

    @Test
    @DisplayName("Verify Silo Hatch Footprints (25 and 49 cells)")
    public void testSiloHatchFootprints() {
        DoorDecl small = DoorDecls.SILO_HATCH;
        BlockPos[] smallCells = small.getCells(Direction.NORTH);
        assertEquals(25, smallCells.length, "Small silo hatch must be 5x5 = 25 cells");

        DoorDecl large = DoorDecls.SILO_HATCH_LARGE;
        BlockPos[] largeCells = large.getCells(Direction.NORTH);
        assertEquals(49, largeCells.length, "Large silo hatch must be 7x7 = 49 cells");
    }

    @Test
    @DisplayName("Verify Cardinal Rotations Match Facings Math")
    public void testCardinalRotations() {
        DoorDecl decl = DoorDecls.SLIDING_BLAST_DOOR; // minX=-3, maxX=3, minY=0, maxY=3, minZ=0, maxZ=0 (7x4=28)
        assertEquals(28, decl.getCells(Direction.NORTH).length);

        for (Direction f : Direction.Type.HORIZONTAL) {
            BlockPos[] cells = decl.getCells(f);
            assertEquals(28, cells.length, "Must retain 28 cells for facing " + f);

            for (int x = -3; x <= 3; x++) {
                for (int y = 0; y <= 3; y++) {
                    int expectedX = (int) Math.round(Facings.worldX(x, 0, f));
                    int expectedZ = (int) Math.round(Facings.worldZ(x, 0, f));
                    BlockPos expectedPos = new BlockPos(expectedX, y, expectedZ);

                    boolean found = Arrays.stream(cells).anyMatch(p -> p.equals(expectedPos));
                    assertTrue(found, "Cell " + expectedPos + " must exist for facing " + f);
                }
            }
        }
    }
}
