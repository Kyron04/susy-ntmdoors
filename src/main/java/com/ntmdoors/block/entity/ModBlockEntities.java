package com.ntmdoors.block.entity;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Block entity type registries for Milestone M3 security hardware.
 */
public final class ModBlockEntities {

    public static final BlockEntityType<KeypadBlockEntity> KEYPAD = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "sliding_blast_door_keypad"),
            FabricBlockEntityTypeBuilder.create(KeypadBlockEntity::new, ModBlocks.KEYPAD).build()
    );

    public static final BlockEntityType<KeycardScannerBlockEntity> KEYCARD_SCANNER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "keycard_scanner"),
            FabricBlockEntityTypeBuilder.create(KeycardScannerBlockEntity::new, ModBlocks.KEYCARD_SCANNER).build()
    );

    public static final BlockEntityType<KeycardProgrammerBlockEntity> KEYCARD_PROGRAMMER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "keycard_programmer"),
            FabricBlockEntityTypeBuilder.create(KeycardProgrammerBlockEntity::new, ModBlocks.KEYCARD_PROGRAMMER).build()
    );

    public static void registerModBlockEntities() {
        // Triggers static initialization
    }

    private ModBlockEntities() {}
}
