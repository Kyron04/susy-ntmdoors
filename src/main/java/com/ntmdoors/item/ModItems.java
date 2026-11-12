package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.ModBlocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of access cards, security tools, and security block items for Milestone M3.
 */
public final class ModItems {

    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    // --- Tools ---
    public static final ItemScrewdriver SCREWDRIVER = reg("screwdriver", new ItemScrewdriver());
    public static final ItemRelayKey RELAY_KEY = reg("relay_key", new ItemRelayKey());
    public static final ItemBiometricRelayKey BIOMETRIC_RELAY_KEY = reg("biometric_relay_key", new ItemBiometricRelayKey());
    public static final ItemSafeBindingKey SAFE_BINDING_KEY = reg("safe_binding_key", new ItemSafeBindingKey());
    public static final ItemMotionRelayKey MOTION_RELAY_KEY = reg("motion_relay_key", new ItemMotionRelayKey());
    public static final ItemRedstoneRelayKey REDSTONE_RELAY_KEY = reg("redstone_relay_key", new ItemRedstoneRelayKey());
    public static final ItemRedstoneMonocle REDSTONE_MONOCLE = reg("redstone_monocle", new ItemRedstoneMonocle());

    // --- 8 RFID Base & Color Cards ---
    public static final ItemRfidCard BLANK_RFID_CARD = reg("blank_rfid_card", new ItemRfidCard("blank_rfid_card", false));
    public static final ItemRfidCard RED_RFID_CARD = reg("red_rfid_card", new ItemRfidCard("red_rfid_card"));
    public static final ItemRfidCard VIOLET_RFID_CARD = reg("violet_rfid_card", new ItemRfidCard("violet_rfid_card"));
    public static final ItemRfidCard GREEN_RFID_CARD = reg("green_rfid_card", new ItemRfidCard("green_rfid_card"));
    public static final ItemRfidCard BLUE_RFID_CARD = reg("blue_rfid_card", new ItemRfidCard("blue_rfid_card"));
    public static final ItemRfidCard BLACK_RFID_CARD = reg("black_rfid_card", new ItemRfidCard("black_rfid_card"));
    public static final ItemRfidCard ORANGE_RFID_CARD = reg("orange_rfid_card", new ItemRfidCard("orange_rfid_card"));
    public static final ItemRfidCard YELLOW_RFID_CARD = reg("yellow_rfid_card", new ItemRfidCard("yellow_rfid_card"));

    // --- 5 Tarkov Themed Cards ---
    public static final ItemRfidCard BLUE_MARKING_CARD = reg("keycard_blue_marking",
            new ItemNamedRfidCard("keycard_blue_marking", "Sanitar's office."));
    public static final ItemRfidCard LABYRINTH_KEYCARD = reg("labyrinth_keycard",
            new ItemNamedRfidCard("labyrinth_keycard", "Something is under Shoreline."));
    public static final ItemRfidCard ENGINE_ROOM_KEYCARD = reg("engine_room_keycard",
            new ItemNamedRfidCard("engine_room_keycard", "Let's break the ice."));
    public static final ItemRfidCard TERMINAL_KEYCARD = reg("terminal_keycard",
            new ItemNamedRfidCard("terminal_keycard", "Only one way out of Tarkov."));
    public static final ItemRfidCard TERRAGROUP_LABS_KEYCARD = reg("terragroup_labs_access_keycard",
            new ItemNamedRfidCard("terragroup_labs_access_keycard", "What are they hiding?"));

    // --- 7 SCP Clearance & Joke Cards ---
    public static final ItemRfidCard SCP_LEVEL_0_KEYCARD = reg("scp_level_0_keycard",
            new ItemNamedRfidCard("scp_level_0_keycard", "Level 0 clearance"));
    public static final ItemRfidCard SCP_LEVEL_1_KEYCARD = reg("scp_level_1_keycard",
            new ItemNamedRfidCard("scp_level_1_keycard", "Level 1 clearance"));
    public static final ItemRfidCard SCP_LEVEL_2_KEYCARD = reg("scp_level_2_keycard",
            new ItemNamedRfidCard("scp_level_2_keycard", "Level 2 clearance"));
    public static final ItemRfidCard SCP_LEVEL_3_KEYCARD = reg("scp_level_3_keycard",
            new ItemNamedRfidCard("scp_level_3_keycard", "Level 3 clearance"));
    public static final ItemRfidCard SCP_LEVEL_4_KEYCARD = reg("scp_level_4_keycard",
            new ItemNamedRfidCard("scp_level_4_keycard", "Level 4 clearance"));
    public static final ItemRfidCard SCP_LEVEL_5_KEYCARD = reg("scp_level_5_keycard",
            new ItemNamedRfidCard("scp_level_5_keycard", "Level 5 clearance"));
    public static final ItemRfidCard GOOGA_SECURITY_KEYCARD = reg("googa_security_keycard",
            new ItemNamedRfidCard(Formatting.GOLD, "googa_security_keycard", "Security is goog"));

    // --- 12 Numbered Level Access Cards ---
    public static final ItemRfidCard LEVEL_1_ACCESS_KEYCARD = reg("level_1_access_keycard",
            new ItemNamedRfidCard("level_1_access_keycard", "Level 1 access"));
    public static final ItemRfidCard LEVEL_2_ACCESS_KEYCARD = reg("level_2_access_keycard",
            new ItemNamedRfidCard("level_2_access_keycard", "Level 2 access"));
    public static final ItemRfidCard LEVEL_3_ACCESS_KEYCARD = reg("level_3_access_keycard",
            new ItemNamedRfidCard("level_3_access_keycard", "Level 3 access"));
    public static final ItemRfidCard LEVEL_4_ACCESS_KEYCARD = reg("level_4_access_keycard",
            new ItemNamedRfidCard("level_4_access_keycard", "Level 4 access"));
    public static final ItemRfidCard LEVEL_5_ACCESS_KEYCARD = reg("level_5_access_keycard",
            new ItemNamedRfidCard("level_5_access_keycard", "Level 5 access"));
    public static final ItemRfidCard LEVEL_6_ACCESS_KEYCARD = reg("level_6_access_keycard",
            new ItemNamedRfidCard("level_6_access_keycard", "Level 6 access"));
    public static final ItemRfidCard LEVEL_7_ACCESS_KEYCARD = reg("level_7_access_keycard",
            new ItemNamedRfidCard("level_7_access_keycard", "Level 7 access"));
    public static final ItemRfidCard LEVEL_8_ACCESS_KEYCARD = reg("level_8_access_keycard",
            new ItemNamedRfidCard("level_8_access_keycard", "Level 8 access"));
    public static final ItemRfidCard LEVEL_9_ACCESS_KEYCARD = reg("level_9_access_keycard",
            new ItemNamedRfidCard("level_9_access_keycard", "Level 9 access"));
    public static final ItemRfidCard LEVEL_10_ACCESS_KEYCARD = reg("level_10_access_keycard",
            new ItemNamedRfidCard("level_10_access_keycard", "Level 10 access"));
    public static final ItemRfidCard LEVEL_11_ACCESS_KEYCARD = reg("level_11_access_keycard",
            new ItemNamedRfidCard("level_11_access_keycard", "Level 11 access"));
    public static final ItemRfidCard LEVEL_12_ACCESS_KEYCARD = reg("level_12_access_keycard",
            new ItemNamedRfidCard("level_12_access_keycard", "Level 12 access"));

    // --- 5 Admin / Special Keycards ---
    public static final ItemOperatorKeycard OPERATOR_KEYCARD = reg("operator_keycard", new ItemOperatorKeycard());
    public static final ItemBiometricKeycard BIOMETRIC_KEYCARD = reg("biometric_keycard", new ItemBiometricKeycard());
    public static final ItemBiometricOperatorKeycard BIOMETRIC_OPERATOR_KEYCARD = reg("biometric_operator_keycard", new ItemBiometricOperatorKeycard());
    public static final ItemCreativeKeycard CREATIVE_KEYCARD = reg("creative_keycard", new ItemCreativeKeycard());
    public static final ItemDeveloperKeycard DEVELOPER_KEYCARD = reg("developer_keycard", new ItemDeveloperKeycard());

    // Convenient Aliases
    public static final ItemOperatorKeycard CARD_OPERATOR = OPERATOR_KEYCARD;
    public static final ItemBiometricKeycard CARD_BIOMETRIC = BIOMETRIC_KEYCARD;
    public static final ItemBiometricOperatorKeycard CARD_BIOMETRIC_OPERATOR = BIOMETRIC_OPERATOR_KEYCARD;
    public static final ItemCreativeKeycard CARD_CREATIVE = CREATIVE_KEYCARD;
    public static final ItemDeveloperKeycard CARD_DEVELOPER = DEVELOPER_KEYCARD;

    public static final ItemRfidCard CARD_RFID_RED = RED_RFID_CARD;
    public static final ItemRfidCard CARD_RFID_BLUE = BLUE_RFID_CARD;
    public static final ItemRfidCard CARD_RFID_GREEN = GREEN_RFID_CARD;
    public static final ItemRfidCard CARD_RFID_YELLOW = YELLOW_RFID_CARD;
    public static final ItemRfidCard CARD_RFID_WHITE = BLANK_RFID_CARD;

    // --- Block Items ---
    public static final ItemBlockKeypad KEYPAD_ITEM = reg("sliding_blast_door_keypad", new ItemBlockKeypad(ModBlocks.KEYPAD));
    public static final ItemBlockKeycardScanner KEYCARD_SCANNER_ITEM = reg("keycard_scanner", new ItemBlockKeycardScanner(ModBlocks.KEYCARD_SCANNER));
    public static final ItemBlockKeycardProgrammer KEYCARD_PROGRAMMER_ITEM = reg("keycard_programmer", new ItemBlockKeycardProgrammer(ModBlocks.KEYCARD_PROGRAMMER));
    public static final ItemBlockSafe SAFE_ITEM = reg("safe", new ItemBlockSafe(ModBlocks.SAFE, new Item.Settings()));
    public static final ItemBlockSafe BIOMETRIC_SAFE_ITEM = reg("biometric_safe", new ItemBlockSafe(ModBlocks.BIOMETRIC_SAFE, "ntmdoors.desc.biometric_safe", new Item.Settings()));
    public static final ItemBlockRedstoneGate REDSTONE_GATE_ITEM = reg("redstone_gate", new ItemBlockRedstoneGate(ModBlocks.REDSTONE_GATE, new Item.Settings()));
    public static final net.minecraft.item.BlockItem SPEAKER_ITEM = reg("speaker", new net.minecraft.item.BlockItem(ModBlocks.SPEAKER, new Item.Settings()));
    public static final net.minecraft.item.BlockItem MOTION_SENSOR_ITEM = reg("motion_sensor", new net.minecraft.item.BlockItem(ModBlocks.MOTION_SENSOR, new Item.Settings()));

    // --- Monitor Desk Items (Milestone M5) ---
    public static final ItemMonitorSetup MONITOR_SETUP_ITEM = reg("monitor_setup",
            new ItemMonitorSetup(ModBlocks.MONITOR_SETUP,
                    List.of(ModBlocks.MONITOR_SETUP, ModBlocks.MONITOR_SETUP_MIDDLE, ModBlocks.MONITOR_SETUP_TOP),
                    new Item.Settings()));
    public static final ItemMonitorDesk TRIPLE_MONITOR_SETUP_ITEM = reg("triple_monitor_setup",
            new ItemMonitorDesk(ModBlocks.TRIPLE_MONITOR_SETUP, ModBlocks.DESK_THREE, new Item.Settings()));
    public static final ItemMonitorDesk DUAL_MONITOR_SETUP_ITEM = reg("dual_monitor_setup",
            new ItemMonitorDesk(ModBlocks.DUAL_MONITOR_SETUP, ModBlocks.DESK_DUAL, new Item.Settings()));
    public static final ItemMonitorDesk DUAL_MONITOR_SETUP_2X2_ITEM = reg("dual_monitor_setup_2x2",
            new ItemMonitorDesk(ModBlocks.DUAL_MONITOR_SETUP_2X2, ModBlocks.DESK_TWO_BY_TWO, new Item.Settings()));
    public static final ItemMonitorDesk SINGLE_MONITOR_SETUP_ITEM = reg("single_monitor_setup",
            new ItemMonitorDesk(ModBlocks.SINGLE_MONITOR_SETUP, ModBlocks.DESK_SINGLE, new Item.Settings()));

    public static final ItemTerminalBackupKey TERMINAL_BACKUP_KEY = reg("terminal_backup_key",
            new ItemTerminalBackupKey(new Item.Settings()));

    // --- 31 Palette Cards for Programmer Page 1 ---
    public static final List<ItemRfidCard> PROGRAMMER_CARDS;

    static {
        List<ItemRfidCard> list = new ArrayList<>();
        // 7 standard colors
        list.add(RED_RFID_CARD);
        list.add(VIOLET_RFID_CARD);
        list.add(GREEN_RFID_CARD);
        list.add(BLUE_RFID_CARD);
        list.add(BLACK_RFID_CARD);
        list.add(ORANGE_RFID_CARD);
        list.add(YELLOW_RFID_CARD);
        // 5 Tarkov cards
        list.add(BLUE_MARKING_CARD);
        list.add(LABYRINTH_KEYCARD);
        list.add(ENGINE_ROOM_KEYCARD);
        list.add(TERMINAL_KEYCARD);
        list.add(TERRAGROUP_LABS_KEYCARD);
        // 7 SCP & Joke cards
        list.add(SCP_LEVEL_0_KEYCARD);
        list.add(SCP_LEVEL_1_KEYCARD);
        list.add(SCP_LEVEL_2_KEYCARD);
        list.add(SCP_LEVEL_3_KEYCARD);
        list.add(SCP_LEVEL_4_KEYCARD);
        list.add(SCP_LEVEL_5_KEYCARD);
        list.add(GOOGA_SECURITY_KEYCARD);
        // 12 Level cards
        list.add(LEVEL_1_ACCESS_KEYCARD);
        list.add(LEVEL_2_ACCESS_KEYCARD);
        list.add(LEVEL_3_ACCESS_KEYCARD);
        list.add(LEVEL_4_ACCESS_KEYCARD);
        list.add(LEVEL_5_ACCESS_KEYCARD);
        list.add(LEVEL_6_ACCESS_KEYCARD);
        list.add(LEVEL_7_ACCESS_KEYCARD);
        list.add(LEVEL_8_ACCESS_KEYCARD);
        list.add(LEVEL_9_ACCESS_KEYCARD);
        list.add(LEVEL_10_ACCESS_KEYCARD);
        list.add(LEVEL_11_ACCESS_KEYCARD);
        list.add(LEVEL_12_ACCESS_KEYCARD);
        PROGRAMMER_CARDS = Collections.unmodifiableList(list);
    }

    private static <T extends Item> T reg(String name, T item) {
        ITEMS.put(name, item);
        return item;
    }

    private static boolean registered = false;

    public static synchronized void registerModItems() {
        if (registered) return;
        registered = true;
        for (Map.Entry<String, Item> entry : ITEMS.entrySet()) {
            Registry.register(Registries.ITEM, Identifier.of(NTMDoors.MOD_ID, entry.getKey()), entry.getValue());
            ModItemGroups.addEntry(entry.getValue());
        }
    }

    private ModItems() {}
}
