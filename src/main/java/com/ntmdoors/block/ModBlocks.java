package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of security hardware blocks for Milestone M3.
 */
public final class ModBlocks {

    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    public static final KeypadBlock KEYPAD = reg("sliding_blast_door_keypad", new KeypadBlock());
    public static final KeycardScannerBlock KEYCARD_SCANNER = reg("keycard_scanner", new KeycardScannerBlock());
    public static final KeycardProgrammerBlock KEYCARD_PROGRAMMER = reg("keycard_programmer", new KeycardProgrammerBlock());
    public static final SafeBlock SAFE = reg("safe", new SafeBlock());
    public static final BiometricSafeBlock BIOMETRIC_SAFE = reg("biometric_safe", new BiometricSafeBlock());
    public static final RedstoneGateBlock REDSTONE_GATE = reg("redstone_gate", new RedstoneGateBlock());
    public static final SpeakerBlock SPEAKER = reg("speaker", new SpeakerBlock());
    public static final MotionSensorBlock MOTION_SENSOR = reg("motion_sensor", new MotionSensorBlock());

    // --- Monitor Desks (Milestone M5) ---
    // 6-Monitor Desk
    public static final BlockMonitorSetup MONITOR_SETUP = reg("monitor_setup",
            new BlockMonitorSetup("monitor_setup", BlockMonitorSetup.Layer.BOTTOM));
    public static final BlockMonitorSetup MONITOR_SETUP_MIDDLE = reg("monitor_setup_middle",
            new BlockMonitorSetup("monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE));
    public static final BlockMonitorSetup MONITOR_SETUP_TOP = reg("monitor_setup_top",
            new BlockMonitorSetup("monitor_setup_top", BlockMonitorSetup.Layer.TOP));

    // 3-Monitor Desk
    public static final MonitorDesk DESK_THREE = new MonitorDesk(3);
    public static final BlockMonitorDesk TRIPLE_MONITOR_SETUP = reg("triple_monitor_setup",
            new BlockMonitorDesk(DESK_THREE, "triple_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT));
    public static final BlockMonitorDesk TRIPLE_MONITOR_SETUP_MIDDLE = reg("triple_monitor_setup_middle",
            new BlockMonitorDesk(DESK_THREE, "triple_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT));

    // Dual-Monitor Desk (3-wide table, right-middle omitted)
    public static final MonitorDesk DESK_DUAL = new MonitorDesk(3);
    public static final BlockMonitorDesk DUAL_MONITOR_SETUP = reg("dual_monitor_setup",
            new BlockMonitorDesk(DESK_DUAL, "dual_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT));
    public static final BlockMonitorDesk DUAL_MONITOR_SETUP_MIDDLE = reg("dual_monitor_setup_middle",
            new BlockMonitorDesk(DESK_DUAL, "dual_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER));

    // 2x2 Dual-Monitor Desk (short table)
    public static final MonitorDesk DESK_TWO_BY_TWO = new MonitorDesk(2);
    public static final BlockMonitorDesk2 DUAL_MONITOR_SETUP_2X2 = reg("dual_monitor_setup_2x2",
            new BlockMonitorDesk2(DESK_TWO_BY_TWO, "dual_monitor_setup_2x2", BlockMonitorSetup.Layer.BOTTOM,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT));
    public static final BlockMonitorDesk2 DUAL_MONITOR_SETUP_2X2_MIDDLE = reg("dual_monitor_setup_2x2_middle",
            new BlockMonitorDesk2(DESK_TWO_BY_TWO, "dual_monitor_setup_2x2_middle", BlockMonitorSetup.Layer.MIDDLE,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT));

    // Single-Monitor Desk (short table, monitor on right column)
    public static final MonitorDesk DESK_SINGLE = new MonitorDesk(2);
    public static final BlockMonitorDesk2 SINGLE_MONITOR_SETUP = reg("single_monitor_setup",
            new BlockMonitorDesk2(DESK_SINGLE, "single_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT));
    public static final BlockMonitorDesk2 SINGLE_MONITOR_SETUP_MIDDLE = reg("single_monitor_setup_middle",
            new BlockMonitorDesk2(DESK_SINGLE, "single_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
                    BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT));

    private static <T extends Block> T reg(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    private static boolean registered = false;

    public static synchronized void registerModBlocks() {
        if (registered) return;
        registered = true;
        for (Map.Entry<String, Block> entry : BLOCKS.entrySet()) {
            Registry.register(Registries.BLOCK, Identifier.of(NTMDoors.MOD_ID, entry.getKey()), entry.getValue());
        }
    }

    private ModBlocks() {}
}
