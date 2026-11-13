package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockBiometricSafe;
import com.ntmdoors.block.BlockDesk;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.block.BlockKeycardProgrammer;
import com.ntmdoors.block.BlockKeycardScanner;
import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorDesk2;
import com.ntmdoors.block.BlockMonitorOnly;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.BlockMotionSensor;
import com.ntmdoors.block.MonitorDesk;
import com.ntmdoors.block.BlockRedstoneGate;
import com.ntmdoors.block.BlockSafe;
import com.ntmdoors.block.BlockSpeaker;
import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.concrete.ConcreteRecipes;
import com.ntmdoors.concrete.ConcreteSet;
import com.ntmdoors.hlblocks.HLBlocksContent;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.item.ItemBiometricKeycard;
import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.item.ItemBiometricRelayKey;
import com.ntmdoors.item.ItemBlockKeycardProgrammer;
import com.ntmdoors.item.ItemBlockKeycardScanner;
import com.ntmdoors.item.ItemBlockKeypad;
import com.ntmdoors.item.ItemBlockRedstoneGate;
import com.ntmdoors.item.ItemBlockSafe;
import com.ntmdoors.item.ItemBlockSpatialDisrupter;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemCraftingPart;
import com.ntmdoors.item.ItemDesk;
import com.ntmdoors.item.ItemDeveloperKeycard;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemMonitorDesk;
import com.ntmdoors.item.ItemMonitorSetup;
import com.ntmdoors.item.ItemMotionRelayKey;
import com.ntmdoors.item.ItemNamedRfidCard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRedstoneMonocle;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ItemSafeBindingKey;
import com.ntmdoors.item.ItemScrewdriver;
import com.ntmdoors.item.ItemTerminalBackupKey;
import com.ntmdoors.item.ItemThermiteCharge;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.item.ItemUndefinedRelayKey;
import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.util.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public class ModContent {

    public static final List<BlockDoorGeneric> NORMAL_DOORS = new ArrayList<>();
    public static final List<BlockDoorGeneric> LOCKED_DOORS = new ArrayList<>();
    public static final List<BlockDoorGeneric> SECURITY_DOORS = new ArrayList<>();
    /** Normal doors first, then their locked and security twins; used for registration and rendering setup. */
    public static final List<BlockDoorGeneric> DOORS = new ArrayList<>();
    /** Doors that keep the existing registration order and therefore the existing numeric IDs. */
    public static final List<BlockDoorGeneric> EARLY_DOORS = new ArrayList<>();
    /** New doors registered after every other block/item so saved numeric IDs do not shift. */
    public static final List<BlockDoorGeneric> LATE_DOORS = new ArrayList<>();
    public static final BlockDoorKeypad KEYPAD = new BlockDoorKeypad();
    public static final BlockKeycardScanner KEYCARD_SCANNER = new BlockKeycardScanner();
    public static final BlockKeycardProgrammer KEYCARD_PROGRAMMER = new BlockKeycardProgrammer();
    public static final BlockSafe SAFE = new BlockSafe();
    public static final BlockBiometricSafe BIOMETRIC_SAFE = new BlockBiometricSafe();
    public static final BlockRedstoneGate REDSTONE_GATE = new BlockRedstoneGate();
    public static final BlockSpeaker SPEAKER = new BlockSpeaker();
    public static final BlockMotionSensor MOTION_SENSOR = new BlockMotionSensor();
    /** Bedrock-hard machine that converts hard UHPC to panels in a 16x16 chunk area. */
    public static final BlockSpatialDisrupter SPATIAL_DISRUPTER = new BlockSpatialDisrupter();
    /** Framed breaching charge that mounts on block faces and breaches doors. */
    public static final BlockThermiteCharge THERMITE_CHARGE = new BlockThermiteCharge();
    /** Bottom row, including the core cell and the item form of the desk. */
    public static final BlockMonitorSetup MONITOR_SETUP =
            new BlockMonitorSetup("monitor_setup", BlockMonitorSetup.Layer.BOTTOM);
    public static final BlockMonitorSetup MONITOR_MIDDLE =
            new BlockMonitorSetup("monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE);
    public static final BlockMonitorSetup MONITOR_TOP =
            new BlockMonitorSetup("monitor_setup_top", BlockMonitorSetup.Layer.TOP);
    /** Bottom, middle and top blocks, in placement order. */
    public static final List<BlockMonitorSetup> MONITOR_BLOCKS = Collections.unmodifiableList(Arrays.asList(
            MONITOR_SETUP, MONITOR_MIDDLE, MONITOR_TOP));

    /** Smaller monitor desks, built from the same sliced-cell approach. */
    public static final MonitorDesk TRIPLE_MONITOR_DESK = new MonitorDesk(3);
    public static final BlockMonitorDesk TRIPLE_MONITOR_SETUP = new BlockMonitorDesk(
            TRIPLE_MONITOR_DESK, "triple_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT);
    public static final BlockMonitorDesk TRIPLE_MONITOR_SETUP_MIDDLE = new BlockMonitorDesk(
            TRIPLE_MONITOR_DESK, "triple_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT);
    public static final List<BlockMonitorDesk> TRIPLE_MONITOR_BLOCKS = Collections.unmodifiableList(Arrays.asList(
            TRIPLE_MONITOR_SETUP, TRIPLE_MONITOR_SETUP_MIDDLE));

    public static final MonitorDesk DUAL_MONITOR_DESK = new MonitorDesk(3);
    public static final BlockMonitorDesk DUAL_MONITOR_SETUP = new BlockMonitorDesk(
            DUAL_MONITOR_DESK, "dual_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT);
    /** The open right-middle cell is not placed; the two monitors occupy the left and centre. */
    public static final BlockMonitorDesk DUAL_MONITOR_SETUP_MIDDLE = new BlockMonitorDesk(
            DUAL_MONITOR_DESK, "dual_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER);
    public static final List<BlockMonitorDesk> DUAL_MONITOR_BLOCKS = Collections.unmodifiableList(Arrays.asList(
            DUAL_MONITOR_SETUP, DUAL_MONITOR_SETUP_MIDDLE));

    public static final MonitorDesk DUAL_MONITOR_SHORT_DESK = new MonitorDesk(2);
    public static final BlockMonitorDesk2 DUAL_MONITOR_SETUP_2X2 = new BlockMonitorDesk2(
            DUAL_MONITOR_SHORT_DESK, "dual_monitor_setup_2x2", BlockMonitorSetup.Layer.BOTTOM,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);
    public static final BlockMonitorDesk2 DUAL_MONITOR_SETUP_2X2_MIDDLE = new BlockMonitorDesk2(
            DUAL_MONITOR_SHORT_DESK, "dual_monitor_setup_2x2_middle", BlockMonitorSetup.Layer.MIDDLE,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);
    public static final List<BlockMonitorDesk> DUAL_MONITOR_SHORT_BLOCKS = Collections.unmodifiableList(
            Arrays.<BlockMonitorDesk>asList(DUAL_MONITOR_SETUP_2X2, DUAL_MONITOR_SETUP_2X2_MIDDLE));

    public static final MonitorDesk SINGLE_MONITOR_DESK = new MonitorDesk(2);
    public static final BlockMonitorDesk2 SINGLE_MONITOR_SETUP = new BlockMonitorDesk2(
            SINGLE_MONITOR_DESK, "single_monitor_setup", BlockMonitorSetup.Layer.BOTTOM,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);
    public static final BlockMonitorDesk2 SINGLE_MONITOR_SETUP_MIDDLE = new BlockMonitorDesk2(
            SINGLE_MONITOR_DESK, "single_monitor_setup_middle", BlockMonitorSetup.Layer.MIDDLE,
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT);
    public static final List<BlockMonitorDesk> SINGLE_MONITOR_BLOCKS = Collections.unmodifiableList(
            Arrays.<BlockMonitorDesk>asList(SINGLE_MONITOR_SETUP, SINGLE_MONITOR_SETUP_MIDDLE));

    /** Decorative desk-only blocks: the monitor desks without monitors, terminals or tile entities. */
    public static final BlockDesk SINGLE_DESK = new BlockDesk("single_desk", 1);
    public static final BlockDesk DESK_1X2 = new BlockDesk("desk_1x2", 2);
    public static final BlockDesk DESK_1X3 = new BlockDesk("desk_1x3", 3);
    public static final List<BlockDesk> DESKS = Collections.unmodifiableList(Arrays.asList(
            SINGLE_DESK, DESK_1X2, DESK_1X3));

    /** Decorative monitor-only blocks: the monitor banks without desks, terminals or tile entities. */
    public static final BlockMonitorOnly SINGLE_MONITOR = new BlockMonitorOnly("single_monitor", 1);
    public static final BlockMonitorOnly MONITOR_1X2 = new BlockMonitorOnly("monitor_1x2", 2);
    public static final BlockMonitorOnly MONITOR_1X3 = new BlockMonitorOnly("monitor_1x3", 3);
    public static final List<BlockMonitorOnly> MONITORS = Collections.unmodifiableList(Arrays.asList(
            SINGLE_MONITOR, MONITOR_1X2, MONITOR_1X3));

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
            new ItemNamedRfidCard("keycard_blue_marking", "Sanitar's office.");
    public static final ItemRfidCard LABYRINTH_KEYCARD =
            new ItemNamedRfidCard("labyrinth_keycard", "Something is under Shoreline.");
    public static final ItemRfidCard ENGINE_ROOM_KEYCARD =
            new ItemNamedRfidCard("engine_room_keycard", "Let's break the ice.");
    public static final ItemRfidCard TERMINAL_KEYCARD =
            new ItemNamedRfidCard("terminal_keycard", "Only one way out of Tarkov.");
    public static final ItemRfidCard TERRAGROUP_LABS_KEYCARD =
            new ItemNamedRfidCard("terragroup_labs_access_keycard", "What are they hiding?");
    /** SCP clearance cards and the Googa joke card; fixed sprites, otherwise ordinary RFID cards. */
    public static final ItemRfidCard SCP_LEVEL_0_KEYCARD =
            new ItemNamedRfidCard("scp_level_0_keycard", "Level 0 clearance");
    public static final ItemRfidCard SCP_LEVEL_1_KEYCARD =
            new ItemNamedRfidCard("scp_level_1_keycard", "Level 1 clearance");
    public static final ItemRfidCard SCP_LEVEL_2_KEYCARD =
            new ItemNamedRfidCard("scp_level_2_keycard", "Level 2 clearance");
    public static final ItemRfidCard SCP_LEVEL_3_KEYCARD =
            new ItemNamedRfidCard("scp_level_3_keycard", "Level 3 clearance");
    public static final ItemRfidCard SCP_LEVEL_4_KEYCARD =
            new ItemNamedRfidCard("scp_level_4_keycard", "Level 4 clearance");
    public static final ItemRfidCard SCP_LEVEL_5_KEYCARD =
            new ItemNamedRfidCard("scp_level_5_keycard", "Level 5 clearance");
    public static final ItemRfidCard GOOGA_SECURITY_KEYCARD =
            new ItemNamedRfidCard(TextFormatting.GOLD, "googa_security_keycard", "Security is goog");
    /** Numbered access keycards (Level 1-12). */
    public static final ItemRfidCard LEVEL_1_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_1_access_keycard", "Level 1 access");
    public static final ItemRfidCard LEVEL_2_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_2_access_keycard", "Level 2 access");
    public static final ItemRfidCard LEVEL_3_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_3_access_keycard", "Level 3 access");
    public static final ItemRfidCard LEVEL_4_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_4_access_keycard", "Level 4 access");
    public static final ItemRfidCard LEVEL_5_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_5_access_keycard", "Level 5 access");
    public static final ItemRfidCard LEVEL_6_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_6_access_keycard", "Level 6 access");
    public static final ItemRfidCard LEVEL_7_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_7_access_keycard", "Level 7 access");
    public static final ItemRfidCard LEVEL_8_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_8_access_keycard", "Level 8 access");
    public static final ItemRfidCard LEVEL_9_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_9_access_keycard", "Level 9 access");
    public static final ItemRfidCard LEVEL_10_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_10_access_keycard", "Level 10 access");
    public static final ItemRfidCard LEVEL_11_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_11_access_keycard", "Level 11 access");
    public static final ItemRfidCard LEVEL_12_ACCESS_KEYCARD =
            new ItemNamedRfidCard("level_12_access_keycard", "Level 12 access");
    /** Privileged card produced by the programmer and accepted by scanners as an admin card. */
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
    /** Carried folder for written notes, with twenty editable pages. */
    public static final ItemDocumentsFolder DOCUMENTS_FOLDER = new ItemDocumentsFolder();
    /** Green creative key that links a safe to doors. */
    public static final ItemSafeBindingKey SAFE_BINDING_KEY = new ItemSafeBindingKey();
    /** Red creative key that links keypads, scanners, safes, speakers and sensors to a gate. */
    public static final ItemRedstoneRelayKey REDSTONE_RELAY_KEY = new ItemRedstoneRelayKey();
    /** Red diagnostic monocle that reveals wireless Redstone links while held. */
    public static final ItemRedstoneMonocle REDSTONE_MONOCLE = new ItemRedstoneMonocle();
    /** Blue creative key that opens the Motion Sensor console and defines its 5x5x5 area. */
    public static final ItemMotionRelayKey MOTION_RELAY_KEY = new ItemMotionRelayKey();
    /** Orange and grey key that binds devices to a Security Terminal net. */
    public static final ItemTerminalRelayKey TERMINAL_RELAY_KEY = new ItemTerminalRelayKey();
    /** Black and red USB key that carries terminal backup data. */
    public static final ItemTerminalBackupKey TERMINAL_BACKUP_KEY = new ItemTerminalBackupKey();
    /** Red weapons key that hard-locks explosives or links them to Redstone Gates. */
    public static final ItemWeaponsRelayKey WEAPONS_RELAY_KEY = new ItemWeaponsRelayKey();
    /** Keys the Undefined Relay Key can become, in the order the programming screen lists them. */
    public static final List<Item> UNDEFINED_KEY_TYPES = Collections.unmodifiableList(Arrays.asList(
            RELAY_KEY, BIOMETRIC_RELAY_KEY, REDSTONE_RELAY_KEY, MOTION_RELAY_KEY,
            TERMINAL_RELAY_KEY, WEAPONS_RELAY_KEY, SAFE_BINDING_KEY, TERMINAL_BACKUP_KEY));
    /** Creative-only programming key; no recipe is registered yet. */
    public static final ItemUndefinedRelayKey UNDEFINED_RELAY_KEY = new ItemUndefinedRelayKey();
    /** Plain crafting components for the new desk recipes. */
    public static final ItemCraftingPart ID_CHIP = new ItemCraftingPart("id_chip");
    public static final ItemCraftingPart MONITOR_SCREEN = new ItemCraftingPart("monitor_screen");
    public static final ItemCraftingPart UNCASED_USB = new ItemCraftingPart("uncased_usb");
    public static final ItemCraftingPart CIRCUIT_BOARD = new ItemCraftingPart("circuit_board");
    public static final ItemCraftingPart CARD_READER = new ItemCraftingPart("card_reader");
    public static final ItemCraftingPart RFID_CHIP = new ItemCraftingPart("rfid_chip");
    /** Pack-developer keycard: works like a normal card but shows Unknown for keys and biometric IDs. */
    public static final ItemDeveloperKeycard DEVELOPER_KEYCARD = new ItemDeveloperKeycard();

    /**
     * Every non-privileged, non-biometric RFID card the programmer's second page can produce:
     * the seven colours, the five Tarkov cards, the six SCP clearance cards, Googa and the
     * Level 1-12 access keycards. Operator, Creative, Biometric and the Developer Keycard are
     * deliberately excluded.
     */
    public static final List<ItemRfidCard> PROGRAMMER_CARDS = Collections.unmodifiableList(Arrays.asList(
            RED_RFID_CARD, VIOLET_RFID_CARD, GREEN_RFID_CARD, BLUE_RFID_CARD,
            BLACK_RFID_CARD, ORANGE_RFID_CARD, YELLOW_RFID_CARD,
            BLUE_MARKING_CARD, LABYRINTH_KEYCARD, ENGINE_ROOM_KEYCARD, TERMINAL_KEYCARD,
            TERRAGROUP_LABS_KEYCARD,
            SCP_LEVEL_0_KEYCARD, SCP_LEVEL_1_KEYCARD, SCP_LEVEL_2_KEYCARD,
            SCP_LEVEL_3_KEYCARD, SCP_LEVEL_4_KEYCARD, SCP_LEVEL_5_KEYCARD,
            GOOGA_SECURITY_KEYCARD,
            LEVEL_1_ACCESS_KEYCARD, LEVEL_2_ACCESS_KEYCARD, LEVEL_3_ACCESS_KEYCARD,
            LEVEL_4_ACCESS_KEYCARD, LEVEL_5_ACCESS_KEYCARD, LEVEL_6_ACCESS_KEYCARD,
            LEVEL_7_ACCESS_KEYCARD, LEVEL_8_ACCESS_KEYCARD, LEVEL_9_ACCESS_KEYCARD,
            LEVEL_10_ACCESS_KEYCARD, LEVEL_11_ACCESS_KEYCARD, LEVEL_12_ACCESS_KEYCARD));

    /** Blank first, then the colour variants and the themed cards in creative-tab order. */
    public static final List<ItemRfidCard> RFID_CARDS = Collections.unmodifiableList(Arrays.asList(
            BLANK_RFID_CARD, RED_RFID_CARD, VIOLET_RFID_CARD, GREEN_RFID_CARD,
            BLUE_RFID_CARD, BLACK_RFID_CARD, ORANGE_RFID_CARD, YELLOW_RFID_CARD,
            BLUE_MARKING_CARD, LABYRINTH_KEYCARD, ENGINE_ROOM_KEYCARD, TERMINAL_KEYCARD,
            TERRAGROUP_LABS_KEYCARD));

    /** New cards registered after every existing item, so saved numeric item IDs are untouched. */
    public static final List<ItemRfidCard> SCP_CARDS = Collections.unmodifiableList(Arrays.asList(
            SCP_LEVEL_0_KEYCARD, SCP_LEVEL_1_KEYCARD, SCP_LEVEL_2_KEYCARD,
            SCP_LEVEL_3_KEYCARD, SCP_LEVEL_4_KEYCARD, SCP_LEVEL_5_KEYCARD,
            GOOGA_SECURITY_KEYCARD));

    /** Level 1-12 access keycards; registered last to preserve saved numeric item IDs. */
    public static final List<ItemRfidCard> LEVEL_ACCESS_CARDS = Collections.unmodifiableList(Arrays.asList(
            LEVEL_1_ACCESS_KEYCARD, LEVEL_2_ACCESS_KEYCARD, LEVEL_3_ACCESS_KEYCARD,
            LEVEL_4_ACCESS_KEYCARD, LEVEL_5_ACCESS_KEYCARD, LEVEL_6_ACCESS_KEYCARD,
            LEVEL_7_ACCESS_KEYCARD, LEVEL_8_ACCESS_KEYCARD, LEVEL_9_ACCESS_KEYCARD,
            LEVEL_10_ACCESS_KEYCARD, LEVEL_11_ACCESS_KEYCARD, LEVEL_12_ACCESS_KEYCARD));

    static {
        for (DoorDecl d : DoorDecls.ALL) NORMAL_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.NORMAL));
        for (DoorDecl d : DoorDecls.ALL) LOCKED_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.LOCKED));
        for (DoorDecl d : DoorDecls.ALL) {
            SECURITY_DOORS.add(new BlockDoorGeneric(d, BlockDoorGeneric.Variant.SECURITY));
        }
        DOORS.addAll(NORMAL_DOORS);
        DOORS.addAll(LOCKED_DOORS);
        DOORS.addAll(SECURITY_DOORS);
        for (BlockDoorGeneric door : DOORS) {
            (DoorDecls.LATE.contains(door.decl) ? LATE_DOORS : EARLY_DOORS).add(door);
        }
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (BlockDoorGeneric b : EARLY_DOORS) e.getRegistry().register(b);
        e.getRegistry().register(KEYPAD);
        e.getRegistry().register(KEYCARD_SCANNER);
        e.getRegistry().register(KEYCARD_PROGRAMMER);
        e.getRegistry().register(SAFE);
        e.getRegistry().register(BIOMETRIC_SAFE);
        // Existing Redstone Gate and Speaker blocks keep their old numeric ids.
        e.getRegistry().register(REDSTONE_GATE);
        // Decorative blocks go last so previously saved numeric block IDs do not shift.
        e.getRegistry().register(SPEAKER);
        // Concrete and HL Blocks are new in 1.0.46 and follow every existing block.
        ConcreteSet.registerBlocks(e);
        HLBlocksContent.registerBlocks(e);
        // New blocks go last so previously saved numeric block IDs do not shift.
        e.getRegistry().register(MOTION_SENSOR);
        for (BlockMonitorSetup block : MONITOR_BLOCKS) e.getRegistry().register(block);
        // New monitor desks go last so previously saved numeric block IDs do not shift.
        for (BlockMonitorDesk block : TRIPLE_MONITOR_BLOCKS) e.getRegistry().register(block);
        for (BlockMonitorDesk block : DUAL_MONITOR_BLOCKS) e.getRegistry().register(block);
        for (BlockMonitorDesk block : DUAL_MONITOR_SHORT_BLOCKS) e.getRegistry().register(block);
        for (BlockMonitorDesk block : SINGLE_MONITOR_BLOCKS) e.getRegistry().register(block);
        // New blocks go last so previously saved numeric block IDs do not shift.
        e.getRegistry().register(SPATIAL_DISRUPTER);
        e.getRegistry().register(THERMITE_CHARGE);
        // The 3x3 office doors are newer still, so they take the next free numeric block IDs.
        for (BlockDoorGeneric b : LATE_DOORS) e.getRegistry().register(b);
        // Decorative desk-only blocks take the next free numeric block IDs.
        for (BlockDesk desk : DESKS) e.getRegistry().register(desk);
        // Decorative monitor-only blocks follow them.
        for (BlockMonitorOnly monitor : MONITORS) e.getRegistry().register(monitor);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (BlockDoorGeneric b : EARLY_DOORS) e.getRegistry().register(new ItemDoorBlock(b).setRegistryName(b.getRegistryName()));
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
        e.getRegistry().register(SAFE_BINDING_KEY);
        // New block items go last as well, so every previously saved numeric item ID is untouched.
        e.getRegistry().register(new ItemBlockSafe(SAFE,
                "A heavy door and keypad between you and your stuff.").setRegistryName(SAFE.getRegistryName()));
        e.getRegistry().register(new ItemBlockSafe(BIOMETRIC_SAFE,
                "A heavy door, scanner, and your own DNA",
                "between you and your stuff.")
                .setRegistryName(BIOMETRIC_SAFE.getRegistryName()));
        // Newest cards go after every existing item, so saved numeric item IDs do not shift.
        for (ItemRfidCard card : SCP_CARDS) e.getRegistry().register(card);
        // Existing Redstone Gate and Speaker items keep their old numeric ids.
        e.getRegistry().register(REDSTONE_RELAY_KEY);
        e.getRegistry().register(new ItemBlockRedstoneGate(REDSTONE_GATE)
                .setRegistryName(REDSTONE_GATE.getRegistryName()));
        e.getRegistry().register(new ItemBlock(SPEAKER)
                .setRegistryName(SPEAKER.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        // Concrete and HL Block items are new in 1.0.46 and follow every existing item.
        ConcreteSet.registerItems(e);
        HLBlocksContent.registerItems(e);
        // New block items go last so previously saved numeric item IDs do not shift.
        e.getRegistry().register(new ItemBlock(MOTION_SENSOR)
                .setRegistryName(MOTION_SENSOR.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        e.getRegistry().register(new ItemMonitorSetup(MONITOR_SETUP, MONITOR_BLOCKS)
                .setRegistryName(MONITOR_SETUP.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        // New relay keys go last so previously saved numeric item IDs do not shift.
        e.getRegistry().register(MOTION_RELAY_KEY);
        e.getRegistry().register(TERMINAL_RELAY_KEY);
        e.getRegistry().register(TERMINAL_BACKUP_KEY);
        e.getRegistry().register(WEAPONS_RELAY_KEY);
        // New monitor desk items go last for the same reason.
        e.getRegistry().register(new ItemMonitorDesk(TRIPLE_MONITOR_SETUP, TRIPLE_MONITOR_DESK)
                .setRegistryName(TRIPLE_MONITOR_SETUP.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        e.getRegistry().register(new ItemMonitorDesk(DUAL_MONITOR_SETUP, DUAL_MONITOR_DESK)
                .setRegistryName(DUAL_MONITOR_SETUP.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        e.getRegistry().register(new ItemMonitorDesk(DUAL_MONITOR_SETUP_2X2, DUAL_MONITOR_SHORT_DESK)
                .setRegistryName(DUAL_MONITOR_SETUP_2X2.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        e.getRegistry().register(new ItemMonitorDesk(SINGLE_MONITOR_SETUP, SINGLE_MONITOR_DESK)
                .setRegistryName(SINGLE_MONITOR_SETUP.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        e.getRegistry().register(new ItemBlockSpatialDisrupter(SPATIAL_DISRUPTER)
                .setRegistryName(SPATIAL_DISRUPTER.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        // Newest block item goes last so previously saved numeric item IDs do not shift.
        e.getRegistry().register(new ItemThermiteCharge(THERMITE_CHARGE)
                .setRegistryName(THERMITE_CHARGE.getRegistryName())
                .setCreativeTab(NTMDoors.TAB));
        // The diagnostic monocle is newer still, so it takes the next free numeric item ID.
        e.getRegistry().register(REDSTONE_MONOCLE);
        // Registered last to preserve every previously saved numeric item ID.
        e.getRegistry().register(DEVELOPER_KEYCARD);
        for (ItemRfidCard card : LEVEL_ACCESS_CARDS) e.getRegistry().register(card);
        // The 3x3 office door items are newer still, so they take the next free numeric item IDs.
        for (BlockDoorGeneric b : LATE_DOORS) {
            e.getRegistry().register(new ItemDoorBlock(b).setRegistryName(b.getRegistryName()));
        }
        // New programming item: register after every existing item so saved numeric IDs stay put.
        e.getRegistry().register(UNDEFINED_RELAY_KEY);
        // Crafting components and decorative desk items go last for the same reason.
        e.getRegistry().register(ID_CHIP);
        e.getRegistry().register(MONITOR_SCREEN);
        e.getRegistry().register(UNCASED_USB);
        e.getRegistry().register(CIRCUIT_BOARD);
        e.getRegistry().register(CARD_READER);
        e.getRegistry().register(RFID_CHIP);
        for (BlockDesk desk : DESKS) {
            e.getRegistry().register(new ItemDesk(desk).setRegistryName(desk.getRegistryName()));
        }
        for (BlockMonitorOnly monitor : MONITORS) {
            e.getRegistry().register(new ItemDesk(monitor).setRegistryName(monitor.getRegistryName()));
        }
    }

    /** Keeps existing worlds working after the SCP card registry names moved from 1-6 to 0-5. */
    @SubscribeEvent
    public static void remapMissingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getMappings()) {
            if (!NTMDoors.MODID.equals(mapping.key.getNamespace())) continue;
            switch (mapping.key.getPath()) {
                case "scp_level_1_keycard":
                    mapping.remap(SCP_LEVEL_0_KEYCARD);
                    break;
                case "scp_level_2_keycard":
                    mapping.remap(SCP_LEVEL_1_KEYCARD);
                    break;
                case "scp_level_3_keycard":
                    mapping.remap(SCP_LEVEL_2_KEYCARD);
                    break;
                case "scp_level_4_keycard":
                    mapping.remap(SCP_LEVEL_3_KEYCARD);
                    break;
                case "scp_level_5_keycard":
                    mapping.remap(SCP_LEVEL_4_KEYCARD);
                    break;
                case "scp_level_6_keycard":
                    mapping.remap(SCP_LEVEL_5_KEYCARD);
                    break;
                default:
                    break;
            }
        }
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

        // Non-door item and machine recipes.
        ModRecipes.register(e);

        // Concrete building recipes; the base blocks use half-scale crafting-table stand-ins for
        // CE's chemical-plant and crystallizer steps. See ConcreteRecipes for the substitution list.
        ConcreteRecipes.register(e);
    }
}
