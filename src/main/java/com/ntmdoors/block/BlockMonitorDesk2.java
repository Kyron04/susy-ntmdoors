package com.ntmdoors.block;

import net.minecraft.block.properties.PropertyEnum;

/** Two-wide monitor desk cell; only left and right columns exist. */
public class BlockMonitorDesk2 extends BlockMonitorDesk {

    private static final PropertyEnum<BlockMonitorSetup.Column> COLUMN_2 =
            PropertyEnum.create("column", BlockMonitorSetup.Column.class,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);

    public BlockMonitorDesk2(MonitorDesk desk, String name, BlockMonitorSetup.Layer layer,
                             BlockMonitorSetup.Column... columns) {
        super(desk, name, layer, columns);
    }

    @Override
    public PropertyEnum<BlockMonitorSetup.Column> columnProperty() {
        return COLUMN_2;
    }
}
