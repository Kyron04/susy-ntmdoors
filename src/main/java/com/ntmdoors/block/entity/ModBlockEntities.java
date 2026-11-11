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

    public static final BlockEntityType<SafeBlockEntity> SAFE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "safe"),
            FabricBlockEntityTypeBuilder.create(SafeBlockEntity::new, ModBlocks.SAFE).build()
    );

    public static final BlockEntityType<BiometricSafeBlockEntity> BIOMETRIC_SAFE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "biometric_safe"),
            FabricBlockEntityTypeBuilder.create(BiometricSafeBlockEntity::new, ModBlocks.BIOMETRIC_SAFE).build()
    );

    public static final BlockEntityType<RedstoneGateBlockEntity> REDSTONE_GATE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "redstone_gate"),
            FabricBlockEntityTypeBuilder.create(RedstoneGateBlockEntity::new, ModBlocks.REDSTONE_GATE).build()
    );

    public static final BlockEntityType<SpeakerBlockEntity> SPEAKER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "speaker"),
            FabricBlockEntityTypeBuilder.create(SpeakerBlockEntity::new, ModBlocks.SPEAKER).build()
    );

    public static final BlockEntityType<MotionSensorBlockEntity> MOTION_SENSOR = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(NTMDoors.MOD_ID, "motion_sensor"),
            FabricBlockEntityTypeBuilder.create(MotionSensorBlockEntity::new, ModBlocks.MOTION_SENSOR).build()
    );

    public static void registerModBlockEntities() {
        // Triggers static initialization
    }

    private ModBlockEntities() {}
}
