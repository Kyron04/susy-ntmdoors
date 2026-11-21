package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Crafting recipes for the mod content. Relay keys, RFID card colours, the Creative and
 * Developer Keycards and the Spatial Disrupter deliberately have no recipes.
 */
public final class ModRecipes {

    private ModRecipes() {
    }

    public static void register(RegistryEvent.Register<IRecipe> e) {
        components(e);
        keysAndCards(e);
        toolsAndSecurity(e);
        monitorsAndDesks(e);
        utilities(e);
        officeDoors(e);
        industrialDoors(e);
        remainingDoors(e);
    }

    private static void components(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "id_chip", new ItemStack(ModContent.ID_CHIP, 2),
                "gRg", "RiR", "gRg",
                'g', Items.GOLD_NUGGET, 'R', Items.REDSTONE, 'i', Items.IRON_INGOT);
        shaped(e, "monitor_screen", new ItemStack(ModContent.MONITOR_SCREEN, 2),
                "ngn", "RdR", "ngn",
                'n', Items.IRON_NUGGET, 'g', Blocks.GLASS_PANE,
                'R', Items.REDSTONE, 'd', "dyeBlack");
        shaped(e, "uncased_usb", new ItemStack(ModContent.UNCASED_USB),
                " g ", " i ", " r ",
                'g', Items.GOLD_INGOT, 'i', Items.IRON_INGOT, 'r', Items.REDSTONE);
        shaped(e, "circuit_board", new ItemStack(ModContent.CIRCUIT_BOARD),
                "ggg", "ICI", "ggg",
                'g', Items.GOLD_NUGGET, 'I', Items.IRON_INGOT, 'C', ModContent.ID_CHIP);
        shaped(e, "card_reader", new ItemStack(ModContent.CARD_READER),
                " I ", "ICI", " R ",
                'I', Items.IRON_INGOT, 'C', ModContent.CIRCUIT_BOARD, 'R', Items.REDSTONE);
        shaped(e, "rfid_chip", new ItemStack(ModContent.RFID_CHIP, 2),
                " g ", "RCR", " g ",
                'g', Items.GOLD_NUGGET, 'R', Items.REDSTONE, 'C', ModContent.ID_CHIP);
    }

    private static void keysAndCards(RegistryEvent.Register<IRecipe> e) {
        shapeless(e, "undefined_relay_key", new ItemStack(ModContent.UNDEFINED_RELAY_KEY),
                ModContent.CIRCUIT_BOARD, ModContent.UNCASED_USB, ModContent.ID_CHIP);
        shaped(e, "keycard_holder", new ItemStack(ModContent.KEYCARD_HOLDER),
                "III", "ICI", "III",
                'I', Items.IRON_INGOT, 'C', Blocks.CHEST);
        shapeless(e, "blank_rfid_card", new ItemStack(ModContent.BLANK_RFID_CARD, 2),
                ModContent.RFID_CHIP, Items.PAPER, Items.PAPER, Items.IRON_INGOT);
        shapeless(e, "operator_keycard", new ItemStack(ModContent.OPERATOR_KEYCARD),
                ModContent.BLANK_RFID_CARD, ModContent.ID_CHIP, Items.GOLD_INGOT);
        shapeless(e, "biometric_keycard", new ItemStack(ModContent.BIOMETRIC_KEYCARD),
                ModContent.BLANK_RFID_CARD, Items.NAME_TAG, Items.REDSTONE);
        shapeless(e, "biometric_operator_keycard",
                new ItemStack(ModContent.BIOMETRIC_OPERATOR_KEYCARD),
                ModContent.OPERATOR_KEYCARD, Items.NAME_TAG);
        shaped(e, "documents_folder", new ItemStack(ModContent.DOCUMENTS_FOLDER),
                "LLL", "LBL", "LLL",
                'L', Items.LEATHER, 'B', Items.BOOK);
    }

    private static void toolsAndSecurity(RegistryEvent.Register<IRecipe> e) {
        shapeless(e, "safe_binding_key", new ItemStack(ModContent.SAFE_BINDING_KEY),
                ModContent.CIRCUIT_BOARD, ModContent.ID_CHIP, "dyeGreen");
        shapeless(e, "terminal_backup_key", new ItemStack(ModContent.TERMINAL_BACKUP_KEY),
                ModContent.UNCASED_USB, ModContent.CIRCUIT_BOARD, Items.REDSTONE, "dyeBlack");
        shaped(e, "redstone_gate", new ItemStack(ModContent.REDSTONE_GATE),
                "I I", "RCR", "I I",
                'I', Items.IRON_INGOT, 'R', Items.REDSTONE, 'C', ModContent.CIRCUIT_BOARD);
        shaped(e, "keypad", new ItemStack(ModContent.KEYPAD),
                "I I", "BCB", "I I",
                'I', Items.IRON_INGOT, 'B', Blocks.STONE_BUTTON, 'C', ModContent.CIRCUIT_BOARD);
        shaped(e, "keycard_scanner", new ItemStack(ModContent.KEYCARD_SCANNER),
                "I I", "GCG", "I I",
                'I', Items.IRON_INGOT, 'G', Blocks.GLASS_PANE, 'C', ModContent.CIRCUIT_BOARD);
        shaped(e, "keycard_programmer", new ItemStack(ModContent.KEYCARD_PROGRAMMER),
                "I I", "SCS", "I I",
                'I', Items.IRON_INGOT, 'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD);
        shaped(e, "safe", new ItemStack(ModContent.SAFE),
                "III", "ICI", "IBI",
                'I', Items.IRON_INGOT, 'C', Blocks.CHEST, 'B', ModContent.CIRCUIT_BOARD);
        shapeless(e, "biometric_safe", new ItemStack(ModContent.BIOMETRIC_SAFE),
                ModContent.SAFE, Items.NAME_TAG);
    }

    private static void monitorsAndDesks(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "speaker", new ItemStack(ModContent.SPEAKER),
                "I I", " N ", " C ",
                'I', Items.IRON_INGOT, 'N', Blocks.NOTEBLOCK, 'C', ModContent.CIRCUIT_BOARD);
        shaped(e, "motion_sensor", new ItemStack(ModContent.MOTION_SENSOR),
                "G G", " C ", " R ",
                'G', Blocks.GLASS_PANE, 'C', ModContent.CIRCUIT_BOARD, 'R', Items.REDSTONE);
        shaped(e, "monitor_setup", new ItemStack(ModContent.MONITOR_SETUP),
                "SSS", "SCS", "WWW",
                'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD, 'W', "plankWood");
        shaped(e, "triple_monitor_setup", new ItemStack(ModContent.TRIPLE_MONITOR_SETUP),
                "SSS", " C ", "WWW",
                'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD, 'W', "plankWood");
        shaped(e, "dual_monitor_setup", new ItemStack(ModContent.DUAL_MONITOR_SETUP),
                "SS ", " C ", "WWW",
                'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD, 'W', "plankWood");
        shaped(e, "single_monitor_setup", new ItemStack(ModContent.SINGLE_MONITOR_SETUP),
                " S ", " C ", "WWW",
                'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD, 'W', "plankWood");
        shaped(e, "single_desk", new ItemStack(ModContent.SINGLE_DESK),
                "WWW", "W W", "I I",
                'W', "plankWood", 'I', Items.IRON_INGOT);
        shaped(e, "single_monitor", new ItemStack(ModContent.SINGLE_MONITOR),
                " S ", " C ", " I ",
                'S', ModContent.MONITOR_SCREEN, 'C', ModContent.CIRCUIT_BOARD,
                'I', Items.IRON_INGOT);
        shapeless(e, "monitor_1x2", new ItemStack(ModContent.MONITOR_1X2),
                new ItemStack(ModContent.SINGLE_MONITOR), new ItemStack(ModContent.SINGLE_MONITOR));
        shapeless(e, "monitor_1x3", new ItemStack(ModContent.MONITOR_1X3),
                new ItemStack(ModContent.SINGLE_MONITOR), new ItemStack(ModContent.SINGLE_MONITOR),
                new ItemStack(ModContent.SINGLE_MONITOR));
        shapeless(e, "desk_1x2", new ItemStack(ModContent.DESK_1X2),
                ModContent.SINGLE_DESK, ModContent.SINGLE_DESK);
        shaped(e, "dual_monitor_setup_2x2", new ItemStack(ModContent.DUAL_MONITOR_SETUP_2X2),
                "SS ", " D ", " C ",
                'S', ModContent.MONITOR_SCREEN, 'D', ModContent.DESK_1X2,
                'C', ModContent.CIRCUIT_BOARD);
        shapeless(e, "desk_1x3", new ItemStack(ModContent.DESK_1X3),
                ModContent.SINGLE_DESK, ModContent.SINGLE_DESK, ModContent.SINGLE_DESK);
    }

    private static void utilities(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "thermite_charge", new ItemStack(ModContent.THERMITE_CHARGE),
                "I I", "RGR", "I I",
                'I', Items.IRON_INGOT, 'R', Items.REDSTONE, 'G', Items.GUNPOWDER);
        shaped(e, "redstone_monocle", new ItemStack(ModContent.REDSTONE_MONOCLE),
                " G ", "RIR", " G ",
                'G', Blocks.GLASS_PANE, 'R', Items.REDSTONE, 'I', Items.GOLD_INGOT);
    }

    /**
     * Office doors are built around a vanilla door core. The outer rows are the style frame and
     * the two module slots; the middle row carries one door for a single, two for a 2x2 double
     * and three for the 3x3 double. Locked doors replace both module slots with redstone and
     * security doors replace them with ID Chips.
     */
    private static void officeDoors(RegistryEvent.Register<IRecipe> e) {
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_1, "WDW",
                "plankWood", Items.IRON_DOOR);
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_1_DOUBLE, "DWD",
                "plankWood", Items.IRON_DOOR);
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_1_DOUBLE_3X3, "DDD",
                "plankWood", Items.IRON_DOOR);

        ItemStack redConcrete = new ItemStack(Blocks.CONCRETE, 1, 14);
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_2, "WDW",
                redConcrete, Items.IRON_DOOR);
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_2_DOUBLE, "DWD",
                redConcrete, Items.IRON_DOOR);
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_2_DOUBLE_3X3, "DDD",
                redConcrete, Items.IRON_DOOR);

        officeDoorSet(e, DoorDecls.OFFICE_DOOR_3, "WDW",
                "plankWood", "doorWood");
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_3_DOUBLE, "DWD",
                "plankWood", "doorWood");
        officeDoorSet(e, DoorDecls.OFFICE_DOOR_3_DOUBLE_3X3, "DDD",
                "plankWood", "doorWood");
    }

    private static void officeDoorSet(RegistryEvent.Register<IRecipe> e, DoorDecl decl, String middle,
                                      Object frame, Object door) {
        shaped(e, decl.name, doorStack(decl, BlockDoorGeneric.Variant.NORMAL),
                "W W", middle, "W W",
                'W', frame, 'D', door);
        shaped(e, decl.name + "_locked", doorStack(decl, BlockDoorGeneric.Variant.LOCKED),
                "WRW", middle, "WRW",
                'W', frame, 'D', door, 'R', Items.REDSTONE);
        shaped(e, decl.name + "_security", doorStack(decl, BlockDoorGeneric.Variant.SECURITY),
                "WCW", middle, "WCW",
                'W', frame, 'D', door, 'C', ModContent.ID_CHIP);
    }

    private static ItemStack doorStack(DoorDecl decl, BlockDoorGeneric.Variant variant) {
        for (BlockDoorGeneric door : ModContent.DOORS) {
            if (door.decl == decl && door.variant == variant) return new ItemStack(door);
        }
        throw new IllegalStateException("No door block for " + decl.name + " " + variant);
    }

    /**
     * The smaller industrial doors use the same iron-door core as the office doors, with iron
     * bars, ingots, glass, iron blocks and a piston for the moving parts. Locked and security
     * variants are upgrades from the finished normal door.
     */
    private static void industrialDoors(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "blast_shutter", doorStack(DoorDecls.BLAST_SHUTTER, BlockDoorGeneric.Variant.NORMAL),
                "BBB", "KDK", "BBB",
                'B', Blocks.IRON_BARS, 'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR);
        shaped(e, "water_door", doorStack(DoorDecls.WATER_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "BGB", "KDK", "BGB",
                'B', Blocks.IRON_BARS, 'G', Blocks.GLASS, 'K', Blocks.IRON_BLOCK,
                'D', Items.IRON_DOOR);
        shaped(e, "sliding_seal_door",
                doorStack(DoorDecls.SLIDING_SEAL_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "BDB", "IPI", "BBB",
                'B', Blocks.IRON_BARS, 'D', Items.IRON_DOOR,
                'I', Items.IRON_INGOT, 'P', Blocks.PISTON);
        shaped(e, "cargo_door", doorStack(DoorDecls.CARGO_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "BDB", "IPI", "BDB",
                'B', Blocks.IRON_BARS, 'D', Items.IRON_DOOR,
                'I', Items.IRON_INGOT, 'P', Blocks.PISTON);
        shaped(e, "qe_sliding_door",
                doorStack(DoorDecls.QE_SLIDING_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "GDG", "KPK", "GDG",
                'G', Blocks.GLASS, 'D', Items.IRON_DOOR,
                'K', Blocks.IRON_BLOCK, 'P', Blocks.PISTON);
        shaped(e, "qe_sliding_panel",
                doorStack(DoorDecls.QE_SLIDING_PANEL, BlockDoorGeneric.Variant.NORMAL),
                "GDG", "IPI", "GGG",
                'G', Blocks.GLASS, 'D', Items.IRON_DOOR,
                'I', Items.IRON_INGOT, 'P', Blocks.PISTON);

        doorUpgrades(e, DoorDecls.BLAST_SHUTTER);
        doorUpgrades(e, DoorDecls.WATER_DOOR);
        doorUpgrades(e, DoorDecls.SLIDING_SEAL_DOOR);
        doorUpgrades(e, DoorDecls.CARGO_DOOR);
        doorUpgrades(e, DoorDecls.QE_SLIDING_DOOR);
        doorUpgrades(e, DoorDecls.QE_SLIDING_PANEL);
    }

    private static void doorUpgrades(RegistryEvent.Register<IRecipe> e, DoorDecl decl) {
        shapeless(e, decl.name + "_locked", doorStack(decl, BlockDoorGeneric.Variant.LOCKED),
                doorStack(decl, BlockDoorGeneric.Variant.NORMAL), Items.REDSTONE, Items.REDSTONE);
        shapeless(e, decl.name + "_security", doorStack(decl, BlockDoorGeneric.Variant.SECURITY),
                doorStack(decl, BlockDoorGeneric.Variant.NORMAL), ModContent.ID_CHIP, ModContent.ID_CHIP);
    }

    /**
     * The remaining door families, including the large blast, vehicle, silo and transition doors.
     * They follow the same iron-door core as the smaller doors and use iron blocks for mass.
     */
    private static void remainingDoors(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "test_door", doorStack(DoorDecls.TEST_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "BGB", "GDG", "BGB",
                'B', Blocks.IRON_BARS, 'G', Blocks.GLASS, 'D', Items.IRON_DOOR);
        shaped(e, "fire_door", doorStack(DoorDecls.FIRE_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "IBI", "KDK", "IBI",
                'I', Items.IRON_INGOT, 'B', Blocks.IRON_BARS,
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR);
        shaped(e, "qe_containment",
                doorStack(DoorDecls.QE_CONTAINMENT, BlockDoorGeneric.Variant.NORMAL),
                "GDG", "KDK", "GPG",
                'G', Blocks.GLASS, 'D', Items.IRON_DOOR,
                'K', Blocks.IRON_BLOCK, 'P', Blocks.PISTON);
        shaped(e, "round_airlock_door",
                doorStack(DoorDecls.ROUND_AIRLOCK_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "BDB", "KPK", "BDB",
                'B', Blocks.IRON_BARS, 'D', Items.IRON_DOOR,
                'K', Blocks.IRON_BLOCK, 'P', Blocks.PISTON);
        shaped(e, "blast_door", doorStack(DoorDecls.BLAST_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "KDK", "BDB", "PDP",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR,
                'B', Blocks.IRON_BARS, 'P', Blocks.PISTON);
        shaped(e, "sliding_blast_door",
                doorStack(DoorDecls.SLIDING_BLAST_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "KDK", "IPI", "KDK",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR,
                'I', Items.IRON_INGOT, 'P', Blocks.PISTON);
        shaped(e, "silo_hatch", doorStack(DoorDecls.SILO_HATCH, BlockDoorGeneric.Variant.NORMAL),
                "BKB", "K K", "BKB",
                'B', Blocks.IRON_BARS, 'K', Blocks.IRON_BLOCK);
        shaped(e, "secure_access_door",
                doorStack(DoorDecls.SECURE_ACCESS_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "KDK", "BDB", "KDK",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR, 'B', Blocks.IRON_BARS);
        shaped(e, "large_vehicle_door",
                doorStack(DoorDecls.LARGE_VEHICLE_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "KDK", "PDP", "KDK",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR, 'P', Blocks.PISTON);
        shaped(e, "silo_hatch_large",
                doorStack(DoorDecls.SILO_HATCH_LARGE, BlockDoorGeneric.Variant.NORMAL),
                "KKK", "K K", "KKK",
                'K', Blocks.IRON_BLOCK);
        shaped(e, "vault_door", doorStack(DoorDecls.VAULT_DOOR, BlockDoorGeneric.Variant.NORMAL),
                "KDK", "DPD", "KDK",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR, 'P', Blocks.PISTON);
        shaped(e, "transition_seal",
                doorStack(DoorDecls.TRANSITION_SEAL, BlockDoorGeneric.Variant.NORMAL),
                "KKK", "DDD", "KKK",
                'K', Blocks.IRON_BLOCK, 'D', Items.IRON_DOOR);

        doorUpgrades(e, DoorDecls.TEST_DOOR);
        doorUpgrades(e, DoorDecls.FIRE_DOOR);
        doorUpgrades(e, DoorDecls.QE_CONTAINMENT);
        doorUpgrades(e, DoorDecls.ROUND_AIRLOCK_DOOR);
        doorUpgrades(e, DoorDecls.BLAST_DOOR);
        doorUpgrades(e, DoorDecls.SLIDING_BLAST_DOOR);
        doorUpgrades(e, DoorDecls.SILO_HATCH);
        doorUpgrades(e, DoorDecls.SECURE_ACCESS_DOOR);
        doorUpgrades(e, DoorDecls.LARGE_VEHICLE_DOOR);
        doorUpgrades(e, DoorDecls.SILO_HATCH_LARGE);
        doorUpgrades(e, DoorDecls.VAULT_DOOR);
        doorUpgrades(e, DoorDecls.TRANSITION_SEAL);
    }

    private static void shaped(RegistryEvent.Register<IRecipe> e, String name, ItemStack output,
                               Object... recipe) {
        ResourceLocation id = new ResourceLocation(NTMDoors.MODID, "crafting/" + name);
        ShapedOreRecipe shaped = new ShapedOreRecipe(id, output, recipe);
        shaped.setRegistryName(id);
        e.getRegistry().register(shaped);
    }

    private static void shapeless(RegistryEvent.Register<IRecipe> e, String name, ItemStack output,
                                  Object... recipe) {
        ResourceLocation id = new ResourceLocation(NTMDoors.MODID, "crafting/" + name);
        ShapelessOreRecipe shapeless = new ShapelessOreRecipe(id, output, recipe);
        shapeless.setRegistryName(id);
        e.getRegistry().register(shapeless);
    }
}
