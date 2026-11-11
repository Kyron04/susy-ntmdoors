package com.ntmdoors.registry;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.List;

/**
 * Modern registry bridge exposing canonical constants like {@link #PROGRAMMER_CARDS}.
 */
public final class ModContent {

    public static final List<ItemRfidCard> PROGRAMMER_CARDS = ModItems.PROGRAMMER_CARDS;

    public static final Item BLANK_RFID_CARD = ModItems.BLANK_RFID_CARD;
    public static final Block KEYPAD = ModBlocks.KEYPAD;
    public static final Block KEYCARD_SCANNER = ModBlocks.KEYCARD_SCANNER;
    public static final Block KEYCARD_PROGRAMMER = ModBlocks.KEYCARD_PROGRAMMER;

    private ModContent() {}
}
