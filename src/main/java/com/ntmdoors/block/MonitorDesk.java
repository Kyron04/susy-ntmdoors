package com.ntmdoors.block;

import com.ntmdoors.util.Facings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared layout for a small multi-block monitor desk. A desk has one block type per layer
 * (bottom and middle) and two or three columns. The clicked cell is the core bottom column:
 * the centre column for a three-wide desk and the left column for a two-wide desk.
 */
public final class MonitorDesk {

    private final int columns;
    private final BlockMonitorSetup.Column coreColumn;
    private final List<BlockMonitorDesk> blocks = new ArrayList<>();
    private BlockMonitorDesk core;

    public MonitorDesk(int columns) {
        if (columns != 2 && columns != 3) {
            throw new IllegalArgumentException("columns must be 2 or 3");
        }
        this.columns = columns;
        this.coreColumn = columns == 3
                ? BlockMonitorSetup.Column.CENTER
                : BlockMonitorSetup.Column.LEFT;
    }

    void add(BlockMonitorDesk block) {
        blocks.add(block);
        if (block.layer() == BlockMonitorSetup.Layer.BOTTOM) {
            core = block;
        }
    }

    public int columns() {
        return columns;
    }

    public BlockMonitorSetup.Column coreColumn() {
        return coreColumn;
    }

    public BlockMonitorDesk core() {
        return core;
    }

    public List<BlockMonitorDesk> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    /** World offset of one cell relative to the bottom core block, rotated for the facing. */
    public BlockPos offset(BlockMonitorDesk block, BlockMonitorSetup.Column column, Direction facing) {
        int dx = columns == 3
                ? column.ordinal() - 1
                : (column == BlockMonitorSetup.Column.LEFT ? 0 : 1);
        return new BlockPos(
                (int) Math.round(Facings.worldX(dx, 0, facing)),
                block.layer().ordinal(),
                (int) Math.round(Facings.worldZ(dx, 0, facing)));
    }
}
