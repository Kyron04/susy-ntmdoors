package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.item.ItemBlockKeypad;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ItemScrewdriver;
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
import java.util.List;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public class ModContent {

    public static final List<BlockDoorGeneric> NORMAL_DOORS = new ArrayList<>();
    public static final List<BlockDoorGeneric> LOCKED_DOORS = new ArrayList<>();
    /** Normal doors first, then their locked twins; used for registration and rendering setup. */
    public static final List<BlockDoorGeneric> DOORS = new ArrayList<>();
    public static final BlockDoorKeypad KEYPAD = new BlockDoorKeypad();
    public static final ItemScrewdriver SCREWDRIVER = new ItemScrewdriver();
    public static final ItemRfidCard BLANK_RFID_CARD = new ItemRfidCard("blank_rfid_card");
    public static final ItemRfidCard RED_RFID_CARD = new ItemRfidCard("red_rfid_card");
    public static final ItemRfidCard VIOLET_RFID_CARD = new ItemRfidCard("violet_rfid_card");
    public static final ItemRfidCard GREEN_RFID_CARD = new ItemRfidCard("green_rfid_card");
    public static final ItemRfidCard BLUE_RFID_CARD = new ItemRfidCard("blue_rfid_card");
    public static final ItemRfidCard BLACK_RFID_CARD = new ItemRfidCard("black_rfid_card");
    public static final ItemRfidCard ORANGE_RFID_CARD = new ItemRfidCard("orange_rfid_card");
    public static final ItemRfidCard YELLOW_RFID_CARD = new ItemRfidCard("yellow_rfid_card");

    /** Blank first, then the colour variants in creative-tab order. */
    public static final List<ItemRfidCard> RFID_CARDS = Collections.unmodifiableList(Arrays.asList(
            BLANK_RFID_CARD, RED_RFID_CARD, VIOLET_RFID_CARD, GREEN_RFID_CARD,
            BLUE_RFID_CARD, BLACK_RFID_CARD, ORANGE_RFID_CARD, YELLOW_RFID_CARD));

    static {
        for (DoorDecl d : DoorDecls.ALL) NORMAL_DOORS.add(new BlockDoorGeneric(d, false));
        for (DoorDecl d : DoorDecls.ALL) LOCKED_DOORS.add(new BlockDoorGeneric(d, true));
        DOORS.addAll(NORMAL_DOORS);
        DOORS.addAll(LOCKED_DOORS);
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(b);
        e.getRegistry().register(KEYPAD);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(new ItemDoorBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(new ItemBlockKeypad(KEYPAD).setRegistryName(KEYPAD.getRegistryName()));
        e.getRegistry().register(SCREWDRIVER);
        for (ItemRfidCard card : RFID_CARDS) e.getRegistry().register(card);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> e) {
        for (SoundEvent s : DoorDecl.ALL_SOUNDS) e.getRegistry().register(s);
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
