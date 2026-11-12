package com.ntmdoors.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;

/** Two-wide monitor desk cell; only left and right columns exist. */
public class BlockMonitorDesk2 extends BlockMonitorDesk {

    public static final EnumProperty<BlockMonitorSetup.Column> COLUMN_2 =
            EnumProperty.of("column", BlockMonitorSetup.Column.class,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);

    public BlockMonitorDesk2(MonitorDesk desk, String name, BlockMonitorSetup.Layer layer,
                             BlockMonitorSetup.Column... columns) {
        super(desk, name, layer, columns);
    }

    @Override
    public EnumProperty<BlockMonitorSetup.Column> columnProperty() {
        return COLUMN_2;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN_2);
    }
}
