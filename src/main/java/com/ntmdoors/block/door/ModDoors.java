package com.ntmdoors.block.door;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorPartBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.item.ModItemGroups;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Registry of all 22 multiblock doors (57 required canonical variants, 66 total)
 * and their corresponding BlockEntityTypes.
 */
public final class ModDoors {

    public static final Map<String, BlockDoorGeneric> DOORS = new LinkedHashMap<>();
    public static final Map<String, ItemDoorBlock> DOOR_ITEMS = new LinkedHashMap<>();
    public static final List<BlockDoorGeneric> ALL_DOORS = new ArrayList<>();

    // 1. Vault Door
    public static final BlockDoorGeneric VAULT_DOOR = reg(DoorDecls.VAULT_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric VAULT_DOOR_LOCKED = reg(DoorDecls.VAULT_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric VAULT_DOOR_SECURITY = reg(DoorDecls.VAULT_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 2. Sliding Blast Door
    public static final BlockDoorGeneric SLIDING_BLAST_DOOR = reg(DoorDecls.SLIDING_BLAST_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric SLIDING_BLAST_DOOR_LOCKED = reg(DoorDecls.SLIDING_BLAST_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric SLIDING_BLAST_DOOR_SECURITY = reg(DoorDecls.SLIDING_BLAST_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 3. QE Sliding Door
    public static final BlockDoorGeneric QE_SLIDING_DOOR = reg(DoorDecls.QE_SLIDING_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric QE_SLIDING_DOOR_LOCKED = reg(DoorDecls.QE_SLIDING_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric QE_SLIDING_DOOR_SECURITY = reg(DoorDecls.QE_SLIDING_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 4. QE Sliding Panel (1x2 column)
    public static final BlockDoorGeneric QE_SLIDING_PANEL = reg(DoorDecls.QE_SLIDING_PANEL, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric QE_SLIDING_PANEL_LOCKED = reg(DoorDecls.QE_SLIDING_PANEL, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric QE_SLIDING_PANEL_SECURITY = reg(DoorDecls.QE_SLIDING_PANEL, BlockDoorGeneric.Variant.SECURITY);

    // 5. Fire Door
    public static final BlockDoorGeneric FIRE_DOOR = reg(DoorDecls.FIRE_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric FIRE_DOOR_LOCKED = reg(DoorDecls.FIRE_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric FIRE_DOOR_SECURITY = reg(DoorDecls.FIRE_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 6. Secure Access Door
    public static final BlockDoorGeneric SECURE_ACCESS_DOOR = reg(DoorDecls.SECURE_ACCESS_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric SECURE_ACCESS_DOOR_LOCKED = reg(DoorDecls.SECURE_ACCESS_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric SECURE_ACCESS_DOOR_SECURITY = reg(DoorDecls.SECURE_ACCESS_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 7. Sliding Seal Door
    public static final BlockDoorGeneric SLIDING_SEAL_DOOR = reg(DoorDecls.SLIDING_SEAL_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric SLIDING_SEAL_DOOR_LOCKED = reg(DoorDecls.SLIDING_SEAL_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric SLIDING_SEAL_DOOR_SECURITY = reg(DoorDecls.SLIDING_SEAL_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 8. Round Airlock Door
    public static final BlockDoorGeneric ROUND_AIRLOCK_DOOR = reg(DoorDecls.ROUND_AIRLOCK_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric ROUND_AIRLOCK_DOOR_LOCKED = reg(DoorDecls.ROUND_AIRLOCK_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric ROUND_AIRLOCK_DOOR_SECURITY = reg(DoorDecls.ROUND_AIRLOCK_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 9. Silo Hatch
    public static final BlockDoorGeneric SILO_HATCH = reg(DoorDecls.SILO_HATCH, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric SILO_HATCH_LOCKED = reg(DoorDecls.SILO_HATCH, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric SILO_HATCH_SECURITY = reg(DoorDecls.SILO_HATCH, BlockDoorGeneric.Variant.SECURITY);

    // 10. Large Silo Hatch
    public static final BlockDoorGeneric SILO_HATCH_LARGE = reg(DoorDecls.SILO_HATCH_LARGE, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric SILO_HATCH_LARGE_LOCKED = reg(DoorDecls.SILO_HATCH_LARGE, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric SILO_HATCH_LARGE_SECURITY = reg(DoorDecls.SILO_HATCH_LARGE, BlockDoorGeneric.Variant.SECURITY);

    // 11. Cargo Door
    public static final BlockDoorGeneric CARGO_DOOR = reg(DoorDecls.CARGO_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric CARGO_DOOR_LOCKED = reg(DoorDecls.CARGO_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric CARGO_DOOR_SECURITY = reg(DoorDecls.CARGO_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 12. Large Vehicle Door
    public static final BlockDoorGeneric LARGE_VEHICLE_DOOR = reg(DoorDecls.LARGE_VEHICLE_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric LARGE_VEHICLE_DOOR_LOCKED = reg(DoorDecls.LARGE_VEHICLE_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric LARGE_VEHICLE_DOOR_SECURITY = reg(DoorDecls.LARGE_VEHICLE_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 13. QE Containment Door
    public static final BlockDoorGeneric QE_CONTAINMENT = reg(DoorDecls.QE_CONTAINMENT, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric QE_CONTAINMENT_LOCKED = reg(DoorDecls.QE_CONTAINMENT, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric QE_CONTAINMENT_SECURITY = reg(DoorDecls.QE_CONTAINMENT, BlockDoorGeneric.Variant.SECURITY);

    // 14. Roller Blast Door
    public static final BlockDoorGeneric BLAST_DOOR = reg(DoorDecls.BLAST_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric BLAST_DOOR_LOCKED = reg(DoorDecls.BLAST_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric BLAST_DOOR_SECURITY = reg(DoorDecls.BLAST_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 15. Water Door
    public static final BlockDoorGeneric WATER_DOOR = reg(DoorDecls.WATER_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric WATER_DOOR_LOCKED = reg(DoorDecls.WATER_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric WATER_DOOR_SECURITY = reg(DoorDecls.WATER_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 16. Transition Seal
    public static final BlockDoorGeneric TRANSITION_SEAL = reg(DoorDecls.TRANSITION_SEAL, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric TRANSITION_SEAL_LOCKED = reg(DoorDecls.TRANSITION_SEAL, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric TRANSITION_SEAL_SECURITY = reg(DoorDecls.TRANSITION_SEAL, BlockDoorGeneric.Variant.SECURITY);

    // 17. Blast Shutter
    public static final BlockDoorGeneric BLAST_SHUTTER = reg(DoorDecls.BLAST_SHUTTER, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric BLAST_SHUTTER_LOCKED = reg(DoorDecls.BLAST_SHUTTER, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric BLAST_SHUTTER_SECURITY = reg(DoorDecls.BLAST_SHUTTER, BlockDoorGeneric.Variant.SECURITY);

    // 18. Office Door 1 (Black Mesa Door)
    public static final BlockDoorGeneric BLACK_MESA_DOOR = reg(DoorDecls.BLACK_MESA_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric BLACK_MESA_DOOR_LOCKED = reg(DoorDecls.BLACK_MESA_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric BLACK_MESA_DOOR_SECURITY = reg(DoorDecls.BLACK_MESA_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // 19. Office Door 1 Double
    public static final BlockDoorGeneric OFFICE_DOOR_1_DOUBLE = reg(DoorDecls.OFFICE_DOOR_1_DOUBLE, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric OFFICE_DOOR_1_DOUBLE_LOCKED = reg(DoorDecls.OFFICE_DOOR_1_DOUBLE, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric OFFICE_DOOR_1_DOUBLE_SECURITY = reg(DoorDecls.OFFICE_DOOR_1_DOUBLE, BlockDoorGeneric.Variant.SECURITY);

    // 20. Office Door 2
    public static final BlockDoorGeneric OFFICE_DOOR_2 = reg(DoorDecls.OFFICE_DOOR_2, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric OFFICE_DOOR_2_LOCKED = reg(DoorDecls.OFFICE_DOOR_2, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric OFFICE_DOOR_2_SECURITY = reg(DoorDecls.OFFICE_DOOR_2, BlockDoorGeneric.Variant.SECURITY);

    // 21. Office Door 2 Double
    public static final BlockDoorGeneric OFFICE_DOOR_2_DOUBLE = reg(DoorDecls.OFFICE_DOOR_2_DOUBLE, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric OFFICE_DOOR_2_DOUBLE_LOCKED = reg(DoorDecls.OFFICE_DOOR_2_DOUBLE, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric OFFICE_DOOR_2_DOUBLE_SECURITY = reg(DoorDecls.OFFICE_DOOR_2_DOUBLE, BlockDoorGeneric.Variant.SECURITY);

    // 22. Test Door
    public static final BlockDoorGeneric TEST_DOOR = reg(DoorDecls.TEST_DOOR, BlockDoorGeneric.Variant.NORMAL);
    public static final BlockDoorGeneric TEST_DOOR_LOCKED = reg(DoorDecls.TEST_DOOR, BlockDoorGeneric.Variant.LOCKED);
    public static final BlockDoorGeneric TEST_DOOR_SECURITY = reg(DoorDecls.TEST_DOOR, BlockDoorGeneric.Variant.SECURITY);

    // Block Entity Types
    public static BlockEntityType<DoorBlockEntity> DOOR_BLOCK_ENTITY;
    public static BlockEntityType<DoorPartBlockEntity> DOOR_PART_BLOCK_ENTITY;

    private static BlockDoorGeneric reg(DoorDecl decl, BlockDoorGeneric.Variant variant) {
        String id = decl.name + variant.suffix;
        Block.Settings settings = Block.Settings.create()
                .hardness(decl.hardness)
                .resistance(decl.resistance)
                .nonOpaque();
        BlockDoorGeneric block = new BlockDoorGeneric(decl, variant, settings);
        ItemDoorBlock item = new ItemDoorBlock(block, new net.minecraft.item.Item.Settings());

        DOORS.put(id, block);
        DOOR_ITEMS.put(id, item);
        ALL_DOORS.add(block);
        return block;
    }

    private static boolean registered = false;

    public static synchronized void registerModDoors() {
        if (registered) return;
        registered = true;

        if (DOOR_BLOCK_ENTITY == null) {
            DOOR_BLOCK_ENTITY = Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(NTMDoors.MOD_ID, "door"),
                    FabricBlockEntityTypeBuilder.create(DoorBlockEntity::new, ALL_DOORS.toArray(new Block[0])).build(null)
            );
            DOOR_PART_BLOCK_ENTITY = Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(NTMDoors.MOD_ID, "door_part"),
                    FabricBlockEntityTypeBuilder.create(DoorPartBlockEntity::new, ALL_DOORS.toArray(new Block[0])).build(null)
            );
        }

        // Register all blocks and items
        for (Map.Entry<String, BlockDoorGeneric> entry : DOORS.entrySet()) {
            String id = entry.getKey();
            BlockDoorGeneric block = entry.getValue();
            ItemDoorBlock item = DOOR_ITEMS.get(id);

            Identifier blockId = Identifier.of(NTMDoors.MOD_ID, id);
            Registry.register(Registries.BLOCK, blockId, block);
            Registry.register(Registries.ITEM, blockId, item);
            ModItemGroups.addEntry(item);
        }

        // Set creative tab icon to Vault Door item
        ModItemGroups.setIconSupplier(() -> new ItemStack(VAULT_DOOR));

        // Preload OBJ bounding boxes for moving parts
        for (DoorDecl decl : DoorDecls.ALL) {
            decl.prepareGeometry();
        }
    }

    private ModDoors() {}
}
