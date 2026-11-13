package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Crafting recipes for the non-door content. Relay keys, RFID card colours, the Creative and
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
