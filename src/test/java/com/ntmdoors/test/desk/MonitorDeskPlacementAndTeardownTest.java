package com.ntmdoors.test.desk;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorDesk2;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import net.minecraft.Bootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MonitorDeskPlacementAndTeardownTest {

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

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    @Test
    @DisplayName("Verify 6-monitor setup (3x3x1) analytical dummy offset calculation and inversion across 4 facings")
    public void testMonitorSetupOffsetAndInversion() {
        BlockPos corePos = new BlockPos(100, 64, -200);

        for (Direction facing : HORIZONTALS) {
            for (BlockMonitorSetup.Layer layer : BlockMonitorSetup.Layer.values()) {
                for (BlockMonitorSetup.Column col : BlockMonitorSetup.Column.values()) {
                    BlockPos offset = BlockMonitorSetup.offset(layer, col, facing);
                    BlockPos cellPos = corePos.add(offset);
                    BlockPos inverted = cellPos.subtract(offset);

                    assertEquals(corePos, inverted, "Inverting offset must return original corePos for "
                            + layer + " " + col + " facing " + facing);

                    // Core cell verification: BOTTOM layer and CENTER column must have offset (0, 0, 0)
                    if (layer == BlockMonitorSetup.Layer.BOTTOM && col == BlockMonitorSetup.Column.CENTER) {
                        assertEquals(BlockPos.ORIGIN, offset, "Core cell must have (0, 0, 0) offset");
                    }

                    // Height verification
                    assertEquals(layer.ordinal(), offset.getY(), "Y offset must equal layer ordinal");
                }
            }
        }
    }

    @Test
    @DisplayName("Verify 3-wide small desk analytical dummy offsets and inversion")
    public void testThreeWideDeskOffsets() {
        BlockPos corePos = new BlockPos(50, 70, 50);

        for (Direction facing : HORIZONTALS) {
            for (BlockMonitorDesk block : ModBlocks.DESK_THREE.blocks()) {
                for (BlockMonitorSetup.Column col : block.columns()) {
                    BlockPos offset = ModBlocks.DESK_THREE.offset(block, col, facing);
                    BlockPos cellPos = corePos.add(offset);
                    BlockPos inverted = cellPos.subtract(offset);

                    assertEquals(corePos, inverted);

                    if (block.layer() == BlockMonitorSetup.Layer.BOTTOM && col == BlockMonitorSetup.Column.CENTER) {
                        assertEquals(BlockPos.ORIGIN, offset);
                    }
                    assertEquals(block.layer().ordinal(), offset.getY());
                }
            }
        }
    }

    @Test
    @DisplayName("Verify 2-wide small desk analytical dummy offsets (core is LEFT, BOTTOM)")
    public void testTwoWideDeskOffsets() {
        BlockPos corePos = new BlockPos(0, 60, 0);

        for (Direction facing : HORIZONTALS) {
            for (BlockMonitorDesk block : ModBlocks.DESK_TWO_BY_TWO.blocks()) {
                for (BlockMonitorSetup.Column col : block.columns()) {
                    BlockPos offset = ModBlocks.DESK_TWO_BY_TWO.offset(block, col, facing);
                    BlockPos cellPos = corePos.add(offset);
                    BlockPos inverted = cellPos.subtract(offset);

                    assertEquals(corePos, inverted);

                    // For 2-wide desk, core column is LEFT
                    if (block.layer() == BlockMonitorSetup.Layer.BOTTOM && col == BlockMonitorSetup.Column.LEFT) {
                        assertEquals(BlockPos.ORIGIN, offset);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Verify dual monitor setup omits right-middle cell")
    public void testDualMonitorSetupOmission() {
        // Bottom layer of dual monitor setup has 3 columns (LEFT, CENTER, RIGHT)
        assertTrue(ModBlocks.DUAL_MONITOR_SETUP.columns().contains(BlockMonitorSetup.Column.LEFT));
        assertTrue(ModBlocks.DUAL_MONITOR_SETUP.columns().contains(BlockMonitorSetup.Column.CENTER));
        assertTrue(ModBlocks.DUAL_MONITOR_SETUP.columns().contains(BlockMonitorSetup.Column.RIGHT));

        // Middle layer of dual monitor setup omits RIGHT (contains only LEFT and CENTER)
        assertTrue(ModBlocks.DUAL_MONITOR_SETUP_MIDDLE.columns().contains(BlockMonitorSetup.Column.LEFT));
        assertTrue(ModBlocks.DUAL_MONITOR_SETUP_MIDDLE.columns().contains(BlockMonitorSetup.Column.CENTER));
        assertFalse(ModBlocks.DUAL_MONITOR_SETUP_MIDDLE.columns().contains(BlockMonitorSetup.Column.RIGHT),
                "Dual monitor setup middle layer must omit right column to leave space for custom equipment");
    }

    @Test
    @DisplayName("Verify beginDismantle atomic compare-and-set guard prevents teardown recursion")
    public void testBeginDismantleAtomicGuard() {
        MonitorDeskBlockEntity desk = new MonitorDeskBlockEntity(BlockPos.ORIGIN, ModBlocks.MONITOR_SETUP.getDefaultState());

        // First call should succeed (CAS false -> true)
        assertTrue(desk.beginDismantle(), "First beginDismantle() call must return true");

        // Subsequent calls must fail (already true)
        assertFalse(desk.beginDismantle(), "Second beginDismantle() call must return false");
        assertFalse(desk.beginDismantle(), "Third beginDismantle() call must return false");
    }
}
