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
