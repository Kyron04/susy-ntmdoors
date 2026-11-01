package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.block.BlockBiometricSafe;
import com.ntmdoors.block.BlockKeycardProgrammer;
import com.ntmdoors.block.BlockKeycardScanner;
import com.ntmdoors.block.BlockSafe;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.item.ItemBlockKeycardProgrammer;
import com.ntmdoors.item.ItemBlockKeycardScanner;
import com.ntmdoors.item.ItemBlockKeypad;
import com.ntmdoors.item.ItemBlockSafe;
import com.ntmdoors.item.ItemBiometricKeycard;
import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.item.ItemBiometricRelayKey;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemNamedRfidCard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ItemScrewdriver;
import com.ntmdoors.util.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public class ModContent {

    public static final List<BlockDoorGeneric> NORMAL_DOORS = new ArrayList<>();
    public static final List<BlockDoorGeneric> LOCKED_DOORS = new ArrayList<>();
    public static final List<BlockDoorGeneric> SECURITY_DOORS = new ArrayList<>();
    /** Normal doors first, then their locked and security twins; used for registration and rendering setup. */
    public static final List<BlockDoorGeneric> DOORS = new ArrayList<>();
    /** The two silo hatches stay single-variant. */
    private static final Set<String> NO_SECURITY_VARIANT = new HashSet<>(Arrays.asList(
            "silo_hatch", "silo_hatch_large"));
    public static final BlockDoorKeypad KEYPAD = new BlockDoorKeypad();
    public static final BlockKeycardScanner KEYCARD_SCANNER = new BlockKeycardScanner();
    public static final BlockKeycardProgrammer KEYCARD_PROGRAMMER = new BlockKeycardProgrammer();
    public static final BlockSafe SAFE = new BlockSafe();
    public static final BlockBiometricSafe BIOMETRIC_SAFE = new BlockBiometricSafe();
    public static final ItemScrewdriver SCREWDRIVER = new ItemScrewdriver();
    public static final ItemRfidCard BLANK_RFID_CARD = new ItemRfidCard("blank_rfid_card", false);
    public static final ItemRfidCard RED_RFID_CARD = new ItemRfidCard("red_rfid_card");
    public static final ItemRfidCard VIOLET_RFID_CARD = new ItemRfidCard("violet_rfid_card");
    public static final ItemRfidCard GREEN_RFID_CARD = new ItemRfidCard("green_rfid_card");
    public static final ItemRfidCard BLUE_RFID_CARD = new ItemRfidCard("blue_rfid_card");
    public static final ItemRfidCard BLACK_RFID_CARD = new ItemRfidCard("black_rfid_card");
    public static final ItemRfidCard ORANGE_RFID_CARD = new ItemRfidCard("orange_rfid_card");
    public static final ItemRfidCard YELLOW_RFID_CARD = new ItemRfidCard("yellow_rfid_card");
    /** Themed cards with a fixed name, sprite and description; otherwise ordinary RFID cards. */
    public static final ItemRfidCard BLUE_MARKING_CARD =
            new ItemNamedRfidCard("keycard_blue_marking", "Samitar's office");
    public static final ItemRfidCard LABYRINTH_KEYCARD =
            new ItemNamedRfidCard("labyrinth_keycard", "Something is under Shoreline");
    public static final ItemRfidCard ENGINE_ROOM_KEYCARD =
            new ItemNamedRfidCard("engine_room_keycard", "let's break the ice");
    public static final ItemRfidCard TERMINAL_KEYCARD =
            new ItemNamedRfidCard("terminal_keycard", "Only one way out of Tarkov");
    public static final ItemRfidCard TERRAGROUP_LABS_KEYCARD =
            new ItemNamedRfidCard("terragroup_labs_access_keycard", "what are they hiding");
    /** Privileged card produced by the programmer; access rules are not defined yet. */
    public static final ItemOperatorKeycard OPERATOR_KEYCARD = new ItemOperatorKeycard();
    /** Debug card that bypasses every door restriction. */
    public static final ItemCreativeKeycard CREATIVE_KEYCARD = new ItemCreativeKeycard();
    public static final ItemRelayKey RELAY_KEY = new ItemRelayKey();
    /** Yellow card that records the player it first entered; not a recolourable colour variant. */
    public static final ItemBiometricKeycard BIOMETRIC_KEYCARD = new ItemBiometricKeycard();
    /** Operator Keycard with a recorded owner, shown with a blue name. */
    public static final ItemBiometricOperatorKeycard BIOMETRIC_OPERATOR_KEYCARD = new ItemBiometricOperatorKeycard();
    /** Relay Key that also records the owner, shown with a blue name. */
    public static final ItemBiometricRelayKey BIOMETRIC_RELAY_KEY = new ItemBiometricRelayKey();
    /** Carried case for up to twelve keycards, opened with a right-click. */
    public static final ItemKeycardHolder KEYCARD_HOLDER = new ItemKeycardHolder();
    /** Carried folder for written notes; page storage will be added later. */
    public static final ItemDocumentsFolder DOCUMENTS_FOLDER = new ItemDocumentsFolder();

    /** The seven cosmetic RFID colours, in the order the programmer's second page shows them. */
    public static final List<ItemRfidCard> COLOUR_CARDS = Collections.unmodifiableList(Arrays.asList(
            RED_RFID_CARD, VIOLET_RFID_CARD, GREEN_RFID_CARD, BLUE_RFID_CARD,
            BLACK_RFID_CARD, ORANGE_RFID_CARD, YELLOW_RFID_CARD));

    /** Blank first, then the colour variants and the themed cards in creative-tab order. */
    public static final List<ItemRfidCard> RFID_CARDS = Collections.unmodifiableList(Arrays.asList(
            BLANK_RFID_CARD, RED_RFID_CARD, VIOLET_RFID_CARD, GREEN_RFID_CARD,
            BLUE_RFID_CARD, BLACK_RFID_CARD, ORANGE_RFID_CARD, YELLOW_RFID_CARD,
            BLUE_MARKING_CARD, LABYRINTH_KEYCARD, ENGINE_ROOM_KEYCARD, TERMINAL_KEYCARD,
            TERRAGROUP_LABS_KEYCARD));

    static {
        for (DoorDecl d : DoorDecls.ALL) NORMAL_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.NORMAL));
        for (DoorDecl d : DoorDecls.ALL) LOCKED_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.LOCKED));
        for (DoorDecl d : DoorDecls.ALL) {
            if (!NO_SECURITY_VARIANT.contains(d.name)) {
                SECURITY_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.SECURITY));
            }
        }
        DOORS.addAll(NORMAL_DOORS);
        DOORS.addAll(LOCKED_DOORS);
        DOORS.addAll(SECURITY_DOORS);
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(b);
        e.getRegistry().register(KEYPAD);
        e.getRegistry().register(KEYCARD_SCANNER);
        e.getRegistry().register(KEYCARD_PROGRAMMER);
        e.getRegistry().register(SAFE);
        e.getRegistry().register(BIOMETRIC_SAFE);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(new ItemDoorBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(new ItemBlockKeypad(KEYPAD).setRegistryName(KEYPAD.getRegistryName()));
        e.getRegistry().register(new ItemBlockKeycardScanner(KEYCARD_SCANNER).setRegistryName(KEYCARD_SCANNER.getRegistryName()));
        e.getRegistry().register(new ItemBlockKeycardProgrammer(KEYCARD_PROGRAMMER).setRegistryName(KEYCARD_PROGRAMMER.getRegistryName()));
        e.getRegistry().register(SCREWDRIVER);
        for (ItemRfidCard card : RFID_CARDS) {
            if (!(card instanceof ItemNamedRfidCard)) e.getRegistry().register(card);
        }
        e.getRegistry().register(OPERATOR_KEYCARD);
        e.getRegistry().register(CREATIVE_KEYCARD);
        e.getRegistry().register(RELAY_KEY);
        e.getRegistry().register(BIOMETRIC_KEYCARD);
        e.getRegistry().register(BIOMETRIC_OPERATOR_KEYCARD);
        e.getRegistry().register(BIOMETRIC_RELAY_KEY);
        // New items go last: registering them among the old ones would shift the numeric item IDs
        // that existing worlds already saved for the cards above.
        for (ItemRfidCard card : RFID_CARDS) {
            if (card instanceof ItemNamedRfidCard) e.getRegistry().register(card);
        }
        // The holder is new too, so it follows the themed cards and leaves every saved item ID alone.
        e.getRegistry().register(KEYCARD_HOLDER);
        e.getRegistry().register(DOCUMENTS_FOLDER);
        // New block items go last as well, so every previously saved numeric item ID is untouched.
        e.getRegistry().register(new ItemBlockSafe(SAFE).setRegistryName(SAFE.getRegistryName()));
        e.getRegistry().register(new ItemBlockSafe(BIOMETRIC_SAFE).setRegistryName(BIOMETRIC_SAFE.getRegistryName()));
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> e) {
        for (SoundEvent s : DoorDecl.ALL_SOUNDS) e.getRegistry().register(s);
        for (SoundEvent s : ModSounds.ALL) e.getRegistry().register(s);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> e) {
        // screwdriver: "  I" / " I " / "S  "
        NonNullList<Ingredient> screw = NonNullList.withSize(9, Ingredient.EMPTY);
        screw.set(2, Ingredient.fromItems(Items.IRON_INGOT));
        screw.set(4, Ingredient.fromItems(Items.IRON_INGOT));
        screw.set(6, Ingredient.fromItems(Items.STICK));
        ShapedRecipes screwdriver = new ShapedRecipes(NTMDoors.MODID + ":screwdriver", 3, 3, screw,
                new ItemStack(SCREWDRIVER));
        screwdriver.setRegistryName(new ResourceLocation(NTMDoors.MODID, "screwdriver"));
        e.getRegistry().register(screwdriver);

        // Door crafting recipes are intentionally not included: the originals depend on NTM-only materials.
        // Add your own here (or via JSON in data/recipes), e.g. new ShapedRecipes(...) per door.
    }
}
