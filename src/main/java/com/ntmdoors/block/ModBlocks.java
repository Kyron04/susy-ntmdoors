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

    private static <T extends Block> T reg(String name, T block) {
        BLOCKS.put(name, block);
        return block;
    }

    public static void registerModBlocks() {
        for (Map.Entry<String, Block> entry : BLOCKS.entrySet()) {
            Registry.register(Registries.BLOCK, Identifier.of(NTMDoors.MOD_ID, entry.getKey()), entry.getValue());
        }
    }

    private ModBlocks() {}
}
